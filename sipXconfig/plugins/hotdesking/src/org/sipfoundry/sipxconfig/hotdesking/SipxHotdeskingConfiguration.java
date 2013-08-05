//
// Copyright (c) 2011 Telecats B.V. All rights reserved. Contributed to SIPfoundry and eZuce, Inc. under a Contributor Agreement.
// This library or application is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License (AGPL) as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
// This library or application is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License (AGPL) for more details.
//
//////////////////////////////////////////////////////////////////////////////
package org.sipfoundry.sipxconfig.hotdesking;

import org.apache.velocity.VelocityContext;
import org.sipfoundry.sipxconfig.admin.commserver.Location;
import org.sipfoundry.sipxconfig.service.SipxProxyService;
import org.sipfoundry.sipxconfig.service.SipxServiceConfiguration;

/**
 * Hotdesking service sipx configuration
 * 
 * @author aliaksandr
 * 
 */
public class SipxHotdeskingConfiguration extends SipxServiceConfiguration {
    @Override
    protected VelocityContext setupContext(Location location) {
        VelocityContext context = super.setupContext(location);
        context.put("service", getService(SipxHotdeskingService.BEAN_ID));
        context.put("sipxServiceManager", getSipxServiceManager());
        return context;
    }

    @Override
    public boolean isReplicable(Location location) {
        // Needs to replicate to the servers that run a proxy
        return getSipxServiceManager().isServiceInstalled(location.getId(), SipxProxyService.BEAN_ID);
    }
}
