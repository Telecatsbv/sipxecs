//
// Copyright (c) 2011 Telecats B.V. All rights reserved. Contributed to SIPfoundry and eZuce, Inc. under a Contributor Agreement.
// This library or application is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License (AGPL) as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
// This library or application is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License (AGPL) for more details.
//
//////////////////////////////////////////////////////////////////////////////
package org.sipfoundry.sipxconfig.hotdesking;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sipfoundry.sipxconfig.admin.NameInUseException;
import org.sipfoundry.sipxconfig.service.LoggingEntity;
import org.sipfoundry.sipxconfig.service.SipxService;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
/**
 * Hotdesking sipx service
 * @author aliaksandr
 *
 */
public class SipxHotdeskingService extends SipxService implements LoggingEntity, ApplicationContextAware {
	public static final String BEAN_ID = "sipxHotdeskingService";
	public static final String LOG_SETTING = "hotdesking/log.level";
	public static final String HOTDESKING_PREFIX = "hotdesking/SIP_HOTDESKING_PREFIX";
	public static final String EVENT_SOCKET_PORT = "hotdesking/freeswitch.eventSocketPort";
	private static final String AUTO_LOGOFF_TIME = "hotdesking/AUTO_LOGOFF_TIME";
    
	private static final Log LOG = LogFactory
			.getLog(SipxHotdeskingService.class);
    private static final String LOGOFF_USER = "hotdesking/LOGOFF_USER";

	private String m_docDir;
	private String m_hotdeskingPrefix;
	private String m_eventSocketPort;
	//private boolean is_enabled;
    private ApplicationContext applicationContext;
    private HotdeskingManagerImpl hotdeskingManager;

	/**
	 * Validates the data in this service and throws a UserException if there is
	 * a problem
	 **/
	@Override
	public void validate() {
		final String extension = getSettingValue(HOTDESKING_PREFIX);
		if (extension == null) {
			final String hotdesking = "SipxHotdeskingService::validate() exstention is not valid: "
					+ extension;
			LOG.info(hotdesking);
			throw new NameInUseException(hotdesking, extension);
		}
	}

	@Override
	public String getLogSetting() {
		return LOG_SETTING;
	}

	@Override
	public void setLogLevel(String logLevel) {
		super.setLogLevel(logLevel);
	}

	@Override
	public String getLogLevel() {
		return super.getLogLevel();
	}

	@Override
	public String getLabelKey() {
		return super.getLabelKey();
	}

	@Override
	public void onConfigChange() {
		m_hotdeskingPrefix = getSettingValue(HOTDESKING_PREFIX);
		LOG.info(String.format(
				"SipxHotdeskingService::onConfigChange(): set prefix",
				m_hotdeskingPrefix));
		hotdeskingManager.applyConfiguration();
	}

	public String getHotdeskingPrefix() {
		String prefix = getSettingValue(HOTDESKING_PREFIX);
		return prefix;
	}

	public String getEventSocketPort() {
		m_eventSocketPort = getSettingValue(EVENT_SOCKET_PORT);
		return m_eventSocketPort;
	}

	public void setEventSocketPort(String port) {
		m_eventSocketPort = port;
		setSettingValue(EVENT_SOCKET_PORT, m_eventSocketPort);
	}

	public void setHotdeskingPrefix(String hotdeskingprefix) {
		m_hotdeskingPrefix = hotdeskingprefix;
		setSettingValue(HOTDESKING_PREFIX, hotdeskingprefix);
	}

	public String getDocDir() {
		return m_docDir;
	}

	public void setDocDir(String m_docDir) {
		this.m_docDir = m_docDir;
	}

    public String getAutoLogofftime() {
        return getSettingValue(AUTO_LOGOFF_TIME);
    }
    
    public String getLogoffUser() {
        return getSettingValue(LOGOFF_USER);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext=applicationContext;
    }

	public HotdeskingManagerImpl getHotdeskingManager() {
		return hotdeskingManager;
	}

	public void setHotdeskingManager(HotdeskingManagerImpl hotdeskingManager) {
		this.hotdeskingManager = hotdeskingManager;
	}
    
    

}
