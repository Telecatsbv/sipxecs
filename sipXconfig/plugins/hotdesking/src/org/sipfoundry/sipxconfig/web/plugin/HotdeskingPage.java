//
// Copyright (c) 2011 Telecats B.V. All rights reserved. Contributed to SIPfoundry and eZuce, Inc. under a Contributor Agreement.
// This library or application is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License (AGPL) as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
// This library or application is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License (AGPL) for more details.
//
//////////////////////////////////////////////////////////////////////////////
package org.sipfoundry.sipxconfig.web.plugin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tapestry.IBeanProvider;
import org.apache.tapestry.IComponent;
import org.apache.tapestry.annotations.Bean;
import org.apache.tapestry.annotations.InjectObject;
import org.apache.tapestry.event.PageBeginRenderListener;
import org.apache.tapestry.event.PageEvent;
import org.apache.tapestry.html.BasePage;
import org.apache.tapestry.valid.IValidationDelegate;
import org.sipfoundry.sipxconfig.admin.dialplan.DialPlanActivationManager;
import org.sipfoundry.sipxconfig.hotdesking.SipxHotdeskingService;
import org.sipfoundry.sipxconfig.service.ServiceConfigurator;
import org.sipfoundry.sipxconfig.service.SipxService;
import org.sipfoundry.sipxconfig.service.SipxServiceManager;
import org.sipfoundry.sipxconfig.setting.Setting;
/**
 * Hotdesking web UI administration page
 * @author aliaksandr
 *
 */
public abstract class HotdeskingPage extends BasePage implements
		PageBeginRenderListener {

	public static final Log LOG = LogFactory.getLog(HotdeskingPage.class);

	/**
	 * Standard name for form validation delegate
	 */
	public static final String VALIDATOR = "hotdeskingValidator";

	@InjectObject("spring:sipxServiceManager")
	public abstract SipxServiceManager getSipxServiceManager();

	@InjectObject("spring:serviceConfigurator")
	public abstract ServiceConfigurator getServiceConfigurator();

	@InjectObject(value = "spring:dialPlanActivationManager")
	public abstract DialPlanActivationManager getDialPlanActivationManager();

	public abstract SipxService getSipxService();
	
	@Bean
	public abstract HotdeskingValidationDelegate getHotdeskingValidator();

	public abstract void setSipxService(SipxService service);

	public void pageBeginRender(PageEvent event) {
		LOG.info("ENTERED HotdeskingPage::pageBeginRender()");

		if (getSipxService() == null) {
			SipxService service = getSipxServiceManager().getServiceByBeanId(
					SipxHotdeskingService.BEAN_ID);
			setSipxService(service);
			LOG.info(String.format(" setted sipXService: %s ", service));
		}
	}

	public Setting getHotdeskingConfigSettings() {
		return getSipxService().getSettings();
	}

	public void apply() {
		if (!isValid(this)) {
			return;
		}
		SipxService service = getSipxService();

		// hotdesking service will validate extension and aliases
		service.validate();

		getSipxServiceManager().storeService(service);
		getServiceConfigurator().replicateServiceConfig(service);

		getDialPlanActivationManager().replicateDialPlan(true);
		recordSuccess(this, getMessages().getMessage("starPrefix.updated"));
	}

	public String getBorderTitle() {
		final String borderTitle = getPage().getMessages().getMessage(
				"hotdesking.title");
		return borderTitle;
	}

    private boolean isValid(IComponent page) {
        IValidationDelegate validator = getValidator(page);
        if (validator == null) {
            return false;
        }
        return !validator.getHasErrors();
    }
    
    private IValidationDelegate getValidator(IComponent page) {
        for (IComponent c = page; c != null; c = c.getContainer()) {
            IBeanProvider beans = c.getBeans();
            if (beans.canProvideBean(VALIDATOR)) {
                return (IValidationDelegate) c.getBeans().getBean(VALIDATOR);
            }
        }
        return null;
    }
    
    private void recordSuccess(IComponent page, String msg) {
       IValidationDelegate delegate = getValidator(page);
        if (delegate instanceof HotdeskingValidationDelegate) {
            HotdeskingValidationDelegate validator = (HotdeskingValidationDelegate) delegate;
            validator.recordSuccess(msg);
        }
    }
}
