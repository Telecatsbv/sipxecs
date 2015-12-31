/**
 *
 *
 * Copyright (c) 2012 eZuce, Inc. All rights reserved.
 * Contributed to SIPfoundry under a Contributor Agreement
 *
 * This software is free software; you can redistribute it and/or modify it under
 * the terms of the Affero General Public License (AGPL) as published by the
 * Free Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 */
package org.sipfoundry.sipxconfig.site.user_portal;

import org.apache.tapestry.BaseComponent;
import org.apache.tapestry.IPage;
import org.apache.tapestry.annotations.ComponentClass;
import org.apache.tapestry.annotations.InjectObject;
import org.apache.tapestry.annotations.InjectState;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.components.AdminNavigation;
import org.sipfoundry.sipxconfig.feature.FeatureManager;
import org.sipfoundry.sipxconfig.ivr.Ivr;
import org.sipfoundry.sipxconfig.permission.Permission;
import org.sipfoundry.sipxconfig.permission.PermissionManager;
import org.sipfoundry.sipxconfig.site.UserSession;

@ComponentClass(allowBody = false, allowInformalParameters = false)
public abstract class UserPortalNavigation extends BaseComponent {

    @InjectState(value = "presenceServer")
    public abstract UserSession getPresenceServer();

    @InjectState(value = "userSession")
    public abstract UserSession getUserSession();

    @InjectObject(value = "spring:featureManager")
    public abstract FeatureManager getFeatureManager();
    
    @InjectObject(value = "spring:permissionManager")
    public abstract PermissionManager getPermissionManager();
    
    @InjectObject(value = "spring:coreContext")
    public abstract CoreContext getCoreContext();

    public boolean isVoicemailEnabled() {
        return (getFeatureManager().isFeatureEnabled(Ivr.FEATURE) ? true : false);
    }
    
    public boolean isFeatureEnabled(String feature) {
    	return AdminNavigation.getEnabledFeatures(getFeatureManager()).contains(feature);
    }
    
    public boolean hasPermission(String permissionLabel) {
    	Permission permission = getPermissionManager().getPermissionByLabel(permissionLabel);
    	if(permission == null)
    		return false;
    	User user = getUserSession().getUser(getCoreContext());
    	return user.hasPermission(permission);
    }
    
    public IPage editPluginPage(String pluginPage, Integer userId) {
    	IPage hookPage = getPage().getRequestCycle().getPage(pluginPage);
    	if(hookPage instanceof UserBasePage) {
    		UserBasePage userHookPage = (UserBasePage)hookPage;
    		userHookPage.setUserId(userId);
    	}
    	return hookPage;
    }
    
}
