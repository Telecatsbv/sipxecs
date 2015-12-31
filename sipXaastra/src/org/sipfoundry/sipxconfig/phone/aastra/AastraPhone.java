/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxconfig.phone.aastra;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sipfoundry.provisioning.hot.HotProvisionable;
import org.sipfoundry.provisioning.hot.HotProvisioningManager;
import org.sipfoundry.sipxconfig.device.Device;
import org.sipfoundry.sipxconfig.device.Profile;
import org.sipfoundry.sipxconfig.device.ProfileContext;
import org.sipfoundry.sipxconfig.device.ProfileFilter;
import org.sipfoundry.sipxconfig.device.RestartException;
import org.sipfoundry.sipxconfig.phone.Line;
import org.sipfoundry.sipxconfig.phone.LineInfo;
import org.sipfoundry.sipxconfig.phone.Phone;
import org.sipfoundry.sipxconfig.phonebook.PhonebookEntry;
import org.sipfoundry.sipxconfig.phonebook.PhonebookManager;
import org.sipfoundry.sipxconfig.setting.SettingEntry;
import org.sipfoundry.sipxconfig.sip.SipService;
import org.sipfoundry.sipxconfig.speeddial.Button;
import org.sipfoundry.sipxconfig.speeddial.SpeedDial;

public class AastraPhone extends Phone implements HotProvisionable {
    private static final String LAST_REBOOT_REQUEST_TS_PREFIX = "# last reboot request at:";
    
    private static final Log LOG = LogFactory.getLog(AastraPhone.class);
    
    static final String REGISTRATION_PATH = "server/registrar_ip";
    static final String DISPLAY_NAME_PATH = "sip_id/screen_name";
    static final String PASSWORD_PATH = "sip_id/password";
    static final String USER_ID_PATH = "sip_id/user_name";
    static final String AUTHORIZATION_ID_PATH = "sip_id/auth_name";
    static final String PHONE_SIP_EXCEPTION = "&phone.sip.exception";
    static final String REGISTRATION_PORT_PATH = "server/registrar_port";
    static final String APPLICATION_XML = "application/xml";

    String m_phonebookFilename = "{0}-Directory.csv";
    private String m_xmlCleaningFilename = "aastra-cleaning.xml";
    private String m_xmlProvisioningFilename = "{0}.prov.xml";
    
    private String m_parentDir;
    
    public AastraPhone() {
    }

    @Override
    public void initialize() {
        AastraPhoneDefaults phoneDefaults = new AastraPhoneDefaults(getPhoneContext().getPhoneDefaults());
        addDefaultBeanSettingHandler(phoneDefaults);
        AastraPhonebookDefaults phonebookDefaults = new AastraPhonebookDefaults();
        addDefaultBeanSettingHandler(phonebookDefaults);
    }

    @Override
    public void initializeLine(Line line) {
        AastraLineDefaults lineDefaults = new AastraLineDefaults(getPhoneContext().getPhoneDefaults(), line);
        line.addDefaultBeanSettingHandler(lineDefaults);
    }

    @Override
    protected ProfileContext createContext() {
        SpeedDial speedDial = getPhoneContext().getSpeedDial(this);
        return new AastraProfileContext(this, speedDial, getModel().getProfileTemplate());
    }

    static class AastraProfileContext extends ProfileContext {
        private SpeedDial m_speeddial;

        AastraProfileContext(AastraPhone phone, SpeedDial speeddial, String profileTemplate) {
            super(phone, profileTemplate);
            m_speeddial = speeddial;
        }
	
	public Map<String, Object> getContext() {
            Map<String, Object> context = super.getContext();
            Phone phone = (Phone) getDevice();

            Collection<Button> speeddials = new ArrayList<Button>();
            if (m_speeddial != null) {
                Collection<Button> buttons = m_speeddial.getButtons();
                for (Button button : buttons) {
                    speeddials.add(button);
                }
            }
            context.put("speeddials", speeddials);
            context.put("timestamp", SimpleDateFormat.getDateTimeInstance().format(new Date()));
            return context;
        }

        /*
 * 	public Map<String, Object> getContext() {
            Map<String, Object> context = super.getContext();
            Phone phone = (Phone) getDevice();

            if (m_speeddial != null) {
                boolean hasBlf = false;
                Collection<Button> speeddials = new ArrayList<Button>();
                Collection<Button> buttons = m_speeddial.getButtons();
                for (Button button : buttons) {
                    speeddials.add(button);
                    if (button.isBlf()) {
                        hasBlf = true;
                        Line line = new Line();
                        line.setPhone(phone);
                        phone.initializeLine(line);
                        line.setSettingValue(DISPLAY_NAME_PATH, button.getLabel());
                        phone.addLine(line);
                    }
                }
                context.put("has_blf", hasBlf);
                context.put("speeddials", speeddials);
                context.put("speeddial", m_speeddial);
                context.put("timestamp", SimpleDateFormat.getDateTimeInstance().format(new Date()));
            }

            int speeddialOffset = 0;
            Collection lines = phone.getLines();
            if (lines != null) {
                speeddialOffset = lines.size();
            }
            context.put("speeddial_offset", speeddialOffset);

            return context;
        }*/
    }


    @Override
    protected void setLineInfo(Line line, LineInfo externalLine) {
        line.setSettingValue(DISPLAY_NAME_PATH, externalLine.getDisplayName());
        line.setSettingValue(USER_ID_PATH, externalLine.getUserId());
        line.setSettingValue(PASSWORD_PATH, externalLine.getPassword());
        line.setSettingValue(AUTHORIZATION_ID_PATH, externalLine.getUserId());

        line.setSettingValue(REGISTRATION_PATH, externalLine.getRegistrationServer());
        line.setSettingValue(REGISTRATION_PORT_PATH, externalLine.getRegistrationServerPort());
    }

    @Override
    protected LineInfo getLineInfo(Line line) {
        LineInfo lineInfo = new LineInfo();
        lineInfo.setUserId(line.getSettingValue(USER_ID_PATH));
        lineInfo.setDisplayName(line.getSettingValue(DISPLAY_NAME_PATH));
        lineInfo.setPassword(line.getSettingValue(PASSWORD_PATH));
        lineInfo.setRegistrationServer(line.getSettingValue(REGISTRATION_PATH));
        lineInfo.setRegistrationServerPort(line.getSettingValue(REGISTRATION_PORT_PATH));
        return lineInfo;
    }

    @Override
    public void restart() {
        updateRestartTimestamp();
        sendCheckSyncToMac();
    }
    
    /**
     * When config file not changed, aastra phone's won't restart. This is why we add
     * restartTimestamp to the config file.
     */
    private void updateRestartTimestamp() {
        File cfgFile = new File(getProfileDir(), getProfileFilename());
        File updatedCfgFile = new File(getProfileDir(), getProfileFilename() + ".restartTrigger");

        try {
            BufferedReader br = new BufferedReader(new FileReader(cfgFile));
            BufferedWriter bw = new BufferedWriter(new FileWriter(updatedCfgFile));
            bw.write(LAST_REBOOT_REQUEST_TS_PREFIX + SimpleDateFormat.getDateTimeInstance().format(new Date())+"\n");
            String line = br.readLine();
            while (line != null) {
                if (!line.startsWith(LAST_REBOOT_REQUEST_TS_PREFIX)) {
                    bw.write(line + "\n");
                }
                line = br.readLine();
            }
            bw.close();
            updatedCfgFile.renameTo(cfgFile);
        } catch (IOException ex) {
            LOG.warn("Couldn't add "+LAST_REBOOT_REQUEST_TS_PREFIX+" to config file:"+cfgFile.getAbsolutePath(),ex);
            updatedCfgFile.delete();
        }
    }

    @Override
    public String getProfileFilename() {
        return getSerialNumber().toUpperCase() + ".cfg";
    }

    @Override
    public Profile[] getProfileTypes() {
        List<Profile> profileTypes = new ArrayList<Profile>();
        profileTypes.add(new Profile(this));
        
        // phonebook profile
        PhonebookManager phonebookManager = getPhonebookManager();
        if (phonebookManager.getPhonebookManagementEnabled()) {
            profileTypes.add(new PhonebookProfile(getPhonebookFilename()));
        }

        // xml provisioning
        profileTypes.add(new XmlProvisionProfile(getXmlProvisioningFilename()));

        Profile[] result = profileTypes.toArray(new Profile[profileTypes.size()]);
        return result;
    }

    public ProfileContext getPhonebook() {
        Collection<PhonebookEntry> entries = getPhoneContext().getPhonebookEntries(this);
        return new AastraPhonebook(entries);
    }

    static class PhonebookProfile extends Profile {
        public PhonebookProfile(String name) {
            super(name, "text/csv");
        }

        protected ProfileFilter createFilter(Device device) {
            return null;
        }

        protected ProfileContext createContext(Device device) {
            AastraPhone phone = (AastraPhone) device;
            return phone.getPhonebook();
        }
    }

    public void setPhonebookFilename(String phonebookFilename) {
        m_phonebookFilename = phonebookFilename;
    }

    public String getXmlCleaningFilename() {
        return m_xmlCleaningFilename;
    }

    public String getParentDir() {
        return m_parentDir;
    }

    public void setParentDir(String parentDir) {
        this.m_parentDir = parentDir;
    }

    public String getPhonebookFilename() {
        return MessageFormat.format(m_phonebookFilename, getSerialNumber().toUpperCase());
    }

    public class AastraPhonebookDefaults {
        @SettingEntry(path = "preferences/dir/directory1")
        public String getFirstDirectoryName() {
            return getPhonebookFilename();
        }
    }
    
    public String getXmlProvisioningFilename() {
        return MessageFormat.format(m_xmlProvisioningFilename, getSerialNumber().toUpperCase());
    }

    @Override
    public void performHotProvisioning(HashMap<String, String> hotProvProps) {
        String instrAddr = getInstrumentAddrSpec();
        SipService m_sip = getSipService();
        LOG.info("AastraPhone: performHotProvisioning instrAddr:" + instrAddr);

        // check prov file is up-2-date, of wait for it to be updated
        long ageThreshold = 10 * 1000; // 10 sec
        long waitPeriod = 1000; // 1sec
        int maxRetry = 5; // 5 x 1sec = maxWait 15sec

        // perform up-2-date check
        File provXmlFile = new File(getParentDir(), getXmlProvisioningFilename());
        LOG.debug("AastraPhone: configFilePath:" + provXmlFile.getAbsolutePath());
        long age = (System.currentTimeMillis() - provXmlFile.lastModified());
        int retryAttempt = 0;
        while (age > ageThreshold && retryAttempt < maxRetry) {
            LOG.debug("AastraPhone: hotProvXmlFile outdated (older then:" + ageThreshold + ", wait:'" + waitPeriod
                    + "'ms for new file to become available and retry. Attempt:" + retryAttempt);
            retryAttempt++;
            synchronized (this) {
                try {
                    wait(waitPeriod);
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
            age = (System.currentTimeMillis() - provXmlFile.lastModified());
        }

        if (age > ageThreshold) {
            LOG.warn("AastraPhone: hotProvXmlFile outdated, older then:" + ageThreshold + ", age is:" + age
                    + ", file:" + provXmlFile.getAbsolutePath());
        }

        // empty redial list and missed calls
        File cleanXmlFile = new File(getParentDir(), getXmlCleaningFilename());
        String cleanFilePath = cleanXmlFile.getAbsolutePath();
        File cleanFile = new File(cleanFilePath);
        LOG.debug("Aastra: cleanFile: " + cleanFile.exists());
        StringBuffer cleanData = new StringBuffer();
        try {
            BufferedReader brc = new BufferedReader(new FileReader(cleanFilePath));
            String line = brc.readLine();
            while (line != null) {
                cleanData.append(line);
                line = brc.readLine();
            }
        } catch (IOException e1) {
            LOG.error("Aastra: Error reading hotProvision clean file:" + cleanFilePath + "," + e1.getMessage(), e1);
            return;
        }

        // send notify
        try {
            LOG.debug("AastraPhone: hotProvXmlFile age:" + age);
            LOG.info("AastraPhone: sending clean-data notify, instrAddr:"+instrAddr);
            getSipService().sendNotify(instrAddr, "aastra-xml", APPLICATION_XML, String.valueOf(cleanData).getBytes());
            LOG.info("AastraPhone: sending aastra-xml notify, instrAddr:"+instrAddr);
            getSipService().sendNotify(instrAddr, "aastra-xml", APPLICATION_XML, new byte[] {});
        } catch (RuntimeException ex) {
            String msg=ex.getMessage();
            ex.printStackTrace();
            throw new RestartException(PHONE_SIP_EXCEPTION);
        }
    }
    
    public class XmlProvisionProfile extends Profile {
        public XmlProvisionProfile(String name) {
            super(name, APPLICATION_XML);
        }

        protected ProfileFilter createFilter(Device device) {
            return null;
        }

        protected ProfileContext createContext(Device device) {
            SpeedDial speedDial = getPhoneContext().getSpeedDial((AastraPhone) device);
            return new AastraProfileContext((AastraPhone) device, speedDial, getModel().getProfileTemplate()
                    .replace(".cfg.", ".prov.xml."));
        }
    }
}
