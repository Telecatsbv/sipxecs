/*
 *
 * Copyright (c) 2011 Telecats B.V. All rights reserved.
 *
 *
 */
package org.sipfoundry.sipxconfig.phone.yealink;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.sipfoundry.sipxconfig.device.ProfileContext;
import org.sipfoundry.sipxconfig.speeddial.Button;

/**
 * Responsible for generating ipmid.cfg
 */
public class yealinkXmlConfiguration extends ProfileContext {
    
	public yealinkXmlConfiguration(YealinkPhone device, String profileTemplate) {
		super(device, profileTemplate);
	}
		
	@Override
	public Map<String, Object> getContext() {
		Map<String, Object> context = super.getContext();
		return context;
	}

	public Collection<Button> getSpeedDial() {
        YealinkPhone phone = (YealinkPhone) getDevice();
        if (null == phone.getSpeedDial()) {
            return Collections.emptyList();
        } else {
            return phone.getSpeedDial().getButtons();
        }
    }
}
