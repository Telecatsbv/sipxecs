//
// Copyright (c) 2011 Telecats B.V. All rights reserved. Contributed to SIPfoundry and eZuce, Inc. under a Contributor Agreement.
// This library or application is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License (AGPL) as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
// This library or application is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License (AGPL) for more details.
//
//////////////////////////////////////////////////////////////////////////////
package org.sipfoundry.sipxconfig.hotdesking;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sipfoundry.sipxconfig.admin.commserver.Location;
import org.sipfoundry.sipxconfig.common.SipxHibernateDaoSupport;
import org.sipfoundry.sipxconfig.freeswitch.FreeswitchAction;
import org.sipfoundry.sipxconfig.freeswitch.FreeswitchCondition;
import org.sipfoundry.sipxconfig.freeswitch.FreeswitchExtension;
import org.sipfoundry.sipxconfig.service.SipxServiceManager;
/**
 * Extension provider for hotdesking
 * @author aliaksandr
 *
 */
public class HotdeskingFreeswitchExtensionProviderImpl extends SipxHibernateDaoSupport<HotdeskingFreeswitchExtensionProvider> implements
		HotdeskingFreeswitchExtensionProvider {
	private SipxServiceManager m_sipxServiceManager;
	private SipxHotdeskingService m_hotdeskingService;
	private boolean m_enabled = true;
	private String m_socketEventPort;

	private void initProperties() {
		m_hotdeskingService = (SipxHotdeskingService) m_sipxServiceManager
				.getServiceByBeanId(SipxHotdeskingService.BEAN_ID);
		m_socketEventPort = m_hotdeskingService
				.getSettingValue(SipxHotdeskingService.EVENT_SOCKET_PORT);

	}

	public boolean isEnabled() {
		return m_enabled;
	}

	public void setEnabled(boolean m_enabled) {
		this.m_enabled = m_enabled;
	}

	@Override
	public List<? extends FreeswitchExtension> getFreeswitchExtensions(
			Location location) {
		if (!m_enabled) {
			return new ArrayList<FreeswitchExtension>();
		}
		initProperties();

		final List<FreeswitchExtension> exts = new ArrayList<FreeswitchExtension>();
		final FreeswitchExtension ext = new FreeswitchExtension() {
		};
		ext.setName("HTD");
		exts.add(ext);

		final Set<FreeswitchCondition> conditions = new HashSet<FreeswitchCondition>();
		final FreeswitchCondition hotdeskingCondition = new FreeswitchCondition();
		hotdeskingCondition.setField("destination_number");
		hotdeskingCondition.setExpression("^HTD$");
		conditions.add(hotdeskingCondition);
		ext.setConditions(conditions);

		final Set<FreeswitchAction> actions = new HashSet<FreeswitchAction>();
		final FreeswitchAction hotdeskingAction = new FreeswitchAction();
		actions.add(hotdeskingAction);
		hotdeskingCondition.setActions(actions);

		hotdeskingAction.setApplication("socket");
		final String address = location.getAddress();
		hotdeskingAction.setData(address + ":" + m_socketEventPort
				+ " async full");
		return exts;
	}

	public SipxServiceManager getSipxServiceManager() {
		return m_sipxServiceManager;
	}

	public void setSipxServiceManager(SipxServiceManager m_sipxServiceManager) {
		this.m_sipxServiceManager = m_sipxServiceManager;
	}

	public SipxHotdeskingService getHotdeskingService() {
		return m_hotdeskingService;
	}

	public void setHotdeskingService(SipxHotdeskingService m_hotdeskingService) {
		this.m_hotdeskingService = m_hotdeskingService;
	}

}
