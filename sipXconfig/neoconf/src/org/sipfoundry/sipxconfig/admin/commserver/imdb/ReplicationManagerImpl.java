/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.admin.commserver.imdb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sipfoundry.sipxconfig.admin.ConfigurationFile;
import org.sipfoundry.sipxconfig.admin.commserver.Location;
import org.sipfoundry.sipxconfig.admin.commserver.LocationsManager;
import org.sipfoundry.sipxconfig.admin.logging.AuditLogContext;
import org.sipfoundry.sipxconfig.common.DataCollectionUtil;
import org.sipfoundry.sipxconfig.xmlrpc.ApiProvider;
import org.sipfoundry.sipxconfig.xmlrpc.XmlRpcRemoteException;
import org.springframework.beans.factory.annotation.Required;

public class ReplicationManagerImpl implements ReplicationManager {
    private static final int PERMISSIONS = 0644;
    private static final Log LOG = LogFactory.getLog(ReplicationManagerImpl.class);
    private static final String DATA_SET_START = "Start Replication of dataset %s with session id %s";
    private static final String DATA_SET_WRITE = "Writing %s records ending at %d with session id %s and status %s";
    private static final String FILE_START = "Start Replication of file %s with session id %s";
    private static final String FILE_WRITE_LAST = "Writing %d (last) chunk of %s with session id %s";
    private static final String FILE_WRITE_CHUNK = "Writing %d chunk of %s with session id %s";

    private static final String PARTIAL = "partial";
    private static final String FINAL = "final";
    private boolean m_enabled = true;

    private ApiProvider<FileApi> m_fileApiProvider;
    private ApiProvider<ImdbApi> m_imdbApiProvider;
    private LocationsManager m_locationsManager;
    private AuditLogContext m_auditLogContext;

    public void setFileApiProvider(ApiProvider<FileApi> fileApiProvider) {
        m_fileApiProvider = fileApiProvider;
    }

    public void setImdbApiProvider(ApiProvider<ImdbApi> imdbApiProvider) {
        m_imdbApiProvider = imdbApiProvider;
    }

    @Required
    public void setLocationsManager(LocationsManager locationsManager) {
        m_locationsManager = locationsManager;
    }

    @Required
    public void setAuditLogContext(AuditLogContext auditLogContext) {
        m_auditLogContext = auditLogContext;
    }

    /**
     * Sends IMDB table data to all locations
     *
     * It only returns one result, if there is a failure checking the log is the only way to
     * detect it. We could throw exceptions from here but it would mean that a single IO failure
     * dooms entire replication process.
     */
    public boolean replicateData(Location[] locations, DataSetGenerator generator) {
        if (!m_enabled) {
            return true;
        }
        boolean success = true;
        DataSet type = generator.getType();

        // we're interested only in registered locations
        List<Location> registeredLocations = new LinkedList<Location>();
        for (Location location : locations) {
            if (location.isRegistered()) {
                registeredLocations.add(location);
            }
        }

        // if any registered location, gather records to replicate
        if (registeredLocations.size() > 0) {
            List<Map<String, String>> records = generator.generate();
            for (Location location : registeredLocations) {
                try {
                    ImdbApi api = m_imdbApiProvider.getApi(location.getProcessMonitorUrl());
                    String sessionId = getSessionId();
                    LOG.info(String.format(DATA_SET_START, type.getName(), sessionId));
                    String status = PARTIAL;
                    int index = 0;
                    int recordsSize = records.size();
                    while (index < recordsSize) {
                        List<Map<String, String>> recordsToReplicate = DataCollectionUtil.getPage(records, index,
                                25000);
                        index = index + recordsToReplicate.size();
                        if (index == recordsSize) {
                            status = FINAL;
                        }
                        LOG.debug(String.format(DATA_SET_WRITE, type.getName(), index, sessionId, status));
                        success = api.replace(getHostname(), type.getName(),
                                recordsToReplicate.toArray(new Map[recordsToReplicate.size()]), status, sessionId);
                        if (!success) {
                            break;
                        }
                    }

                    if (success) {
                        m_auditLogContext.logReplication(type.getName(), location);
                    }
                } catch (XmlRpcRemoteException e) {
                    success = false;
                    LOG.error("Data replication failed: " + type.getName(), e);
                }
            }
        }
        return success;
    }

    /**
     * Encodes payload using Base64 and returns encoded data as string
     *
     * @param payload
     * @return string representing encoded data
     */
    private String encodeBase64(byte[] payload) {
        try {
            // Base64 encoded content is always limited to US-ASCII charset
            byte[] encodedPayload = Base64.encodeBase64(payload);
            return new String(encodedPayload, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean replicateFile(Location[] locations, ConfigurationFile file) {
        if (!m_enabled) {
            return true;
        }
        boolean success = false;
        for (int i = 0; i < locations.length; i++) {
            if (!locations[i].isRegistered()) {
                continue;
            }
            if (!file.isReplicable(locations[i])) {
                LOG.info("File " + file.getName() + " cannot be replicated on location: " + locations[i].getFqdn());
                success = true;
                continue;
            }
            try {
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                Writer writer = new OutputStreamWriter(outStream, "UTF-8");
                file.write(writer, locations[i]);
                writer.close();

                String sessionId = getSessionId();
                FileApi api = m_fileApiProvider.getApi(locations[i].getProcessMonitorUrl());
                LOG.info(String.format(FILE_START, file.getName(), sessionId));
                byte[] payloadBytes = outStream.toByteArray();
                int offset = 0;
                int buffer = 5000000;
                String content;
                while (offset < payloadBytes.length) {
                    byte[] outputBytes;

                    if (payloadBytes.length - offset < buffer) {
                        outputBytes = new byte[payloadBytes.length - offset];
                        System.arraycopy(payloadBytes, offset, outputBytes, 0, payloadBytes.length - offset);
                        content = encodeBase64(outputBytes);
                        LOG.debug(String.format(FILE_WRITE_LAST, outputBytes.length, file.getName(), sessionId));
                        success = api.replace(getHostname(), file.getPath(), PERMISSIONS, content, FINAL, sessionId);
                        break;
                    }

                    outputBytes = new byte[buffer];
                    System.arraycopy(payloadBytes, offset, outputBytes, 0, buffer);
                    offset += buffer;
                    content = encodeBase64(outputBytes);
                    success = api.replace(getHostname(), file.getPath(), PERMISSIONS, content, PARTIAL, sessionId);
                    LOG.debug(String.format(FILE_WRITE_CHUNK, buffer, file.getName(), sessionId));
                    if (!success) {
                        break;
                    }
                }

                if (success) {
                    m_auditLogContext.logReplication(file.getName(), locations[i]);
                }
            } catch (XmlRpcRemoteException e) {
                LOG.error("File replication failed: " + file.getName(), e);
            } catch (UnsupportedEncodingException e) {
                LOG.error("UTF-8 encoding should be always supported.");
                throw new RuntimeException(e);
            } catch (IOException e) {
                LOG.error("IOException for stream writer", e);
                throw new RuntimeException(e);
            }
        }
        return success;
    }

    protected String getSessionId() {
        return String.valueOf(RandomStringUtils.randomAlphanumeric(10)
                + String.valueOf(Calendar.getInstance().getTimeInMillis()));
    }

    public void setEnabled(boolean enabled) {
        m_enabled = enabled;
    }

    private String getHostname() {
        return m_locationsManager.getPrimaryLocation().getFqdn();
    }
}
