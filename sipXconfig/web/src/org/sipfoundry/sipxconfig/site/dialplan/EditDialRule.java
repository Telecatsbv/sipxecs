/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.site.dialplan;

import java.util.List;
import java.util.Map;

import org.apache.tapestry.annotations.InjectObject;
import org.apache.tapestry.annotations.Persist;
import org.apache.tapestry.callback.ICallback;
import org.apache.tapestry.callback.PageCallback;
import org.apache.tapestry.event.PageBeginRenderListener;
import org.apache.tapestry.event.PageEvent;
import org.apache.tapestry.form.IPropertySelectionModel;
import org.sipfoundry.sipxconfig.branch.BranchManager;
import org.sipfoundry.sipxconfig.components.NamedValuesSelectionModel;
import org.sipfoundry.sipxconfig.components.ObjectSelectionModel;
import org.sipfoundry.sipxconfig.components.SipxBasePage;
import org.sipfoundry.sipxconfig.device.ModelSource;
import org.sipfoundry.sipxconfig.dialplan.AutoAttendantManager;
import org.sipfoundry.sipxconfig.dialplan.DialPlanContext;
import org.sipfoundry.sipxconfig.dialplan.DialingRule;
import org.sipfoundry.sipxconfig.dialplan.DialingRuleType;
import org.sipfoundry.sipxconfig.dialplan.MediaServerFactory;
import org.sipfoundry.sipxconfig.forwarding.ForwardingContext;
import org.sipfoundry.sipxconfig.permission.PermissionManager;
import org.sipfoundry.sipxconfig.phone.PhoneModel;

/**
 * EditDialRule
 */
public abstract class EditDialRule extends SipxBasePage implements PageBeginRenderListener {

    public static final String CUSTOM = "dialplan/EditCustomDialRule";
    public static final String INTERNAL = "dialplan/EditInternalDialRule";
    public static final String ATTENDANT = "dialplan/EditAttendantDialRule";
    public static final String LOCAL = "dialplan/EditLocalDialRule";
    public static final String LONG_DISTANCE = "dialplan/EditLongDistanceDialRule";
    public static final String EMERGENCY = "dialplan/EditEmergencyDialRule";
    public static final String INTERNATIONAL = "dialplan/EditInternationalDialRule";
    public static final String SITE_TO_SITE = "dialplan/EditSiteToSiteDialRule";

    @InjectObject("spring:dialPlanContext")
    public abstract DialPlanContext getDialPlanContext();

    @InjectObject("spring:autoAttendantManager")
    public abstract AutoAttendantManager getAutoAttendantManager();

    @InjectObject("spring:forwardingContext")
    public abstract ForwardingContext getForwardingContext();

    @InjectObject("spring:permissionManager")
    public abstract PermissionManager getPermissionManager();

    @InjectObject("spring:mediaServerFactory")
    public abstract MediaServerFactory getMediaServerFactory();

    @InjectObject("spring:emergencyConfigurableModelSource")
    public abstract ModelSource<PhoneModel> getEmergencyConfigurableModelSource();

    @InjectObject("spring:branchManager")
    public abstract BranchManager getBranchManager();

    @Persist
    public abstract Integer getRuleId();

    public abstract void setRuleId(Integer ruleId);

    public abstract DialingRule getRule();

    public abstract void setRule(DialingRule rule);

    public abstract ICallback getCallback();

    public abstract void setCallback(ICallback callback);

    @Persist(value = "client")
    public abstract DialingRuleType getRuleType();

    public abstract void setRuleType(DialingRuleType dialingType);

    public abstract List getAvailableSchedules();

    public abstract void setAvailableSchedules(List schedules);

    public String getEmergencyConfigurableDeviceList() {
        StringBuilder sb = new StringBuilder();
        sb.append("<ul>");
        for (PhoneModel model : getEmergencyConfigurableModelSource().getModels()) {
            sb.append("<li>").append(model.getLabel()).append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    public IPropertySelectionModel getMediaServerTypeModel() {
        Map<String, String> types2Labels = getMediaServerFactory().getBeanIdsToLabels();
        return new NamedValuesSelectionModel(types2Labels);
    }

    public IPropertySelectionModel getLocationsModel() {
        ObjectSelectionModel model = new ObjectSelectionModel();
        model.setCollection(getBranchManager().getBranches());
        model.setLabelExpression("name");
        return model;
    }

    public void pageBeginRender(PageEvent event_) {
        // retrieve available schedules
        setAvailableSchedules(getForwardingContext().getAllGeneralSchedules());

        DialingRule rule = getRule();
        if (null != rule) {
            // FIXME: in custom rules: rule is persitent but rule type not...
            setRuleType(rule.getType());
            rule.setPermissionManager(getPermissionManager());
            return;
        }
        Integer id = getRuleId();
        if (null != id) {
            DialPlanContext manager = getDialPlanContext();
            rule = manager.getRule(id);
            setRuleType(rule.getType());
        } else {
            rule = getRuleType().create();
            rule.setPermissionManager(getPermissionManager());
        }
        setRule(rule);

        // Ignore the callback passed to us for now because we're navigating
        // to unexpected places. Always go to the EditFlexibleDialPlan plan.
        setCallback(new PageCallback(EditFlexibleDialPlan.PAGE));
    }
}
