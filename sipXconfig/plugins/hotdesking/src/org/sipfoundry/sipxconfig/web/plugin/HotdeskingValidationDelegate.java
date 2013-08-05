//
// Copyright (c) 2011 Telecats B.V. All rights reserved. Contributed to SIPfoundry and eZuce, Inc. under a Contributor Agreement.
// This library or application is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License (AGPL) as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
// This library or application is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License (AGPL) for more details.
//
//////////////////////////////////////////////////////////////////////////////
package org.sipfoundry.sipxconfig.web.plugin;

import org.apache.commons.lang.StringUtils;
import org.apache.hivemind.Messages;
import org.apache.tapestry.IMarkupWriter;
import org.apache.tapestry.IRequestCycle;
import org.apache.tapestry.form.IFormComponent;
import org.apache.tapestry.valid.IValidator;
import org.apache.tapestry.valid.ValidationDelegate;
import org.apache.tapestry.valid.ValidatorException;
import org.sipfoundry.sipxconfig.admin.tapestry.ISipxValidationDelegate;
import org.sipfoundry.sipxconfig.common.UserException;

/**
 * SipXhotdesking version of the validator. It does not decorate labels.
 */
public class HotdeskingValidationDelegate extends ValidationDelegate implements ISipxValidationDelegate{
	private static final long serialVersionUID = -4399992967206630297L;

	/**
     * This value is defined in sipXconfig/web/context/css/sipxconfig.css
     */
    private static final String ERROR_CLASS = "user-error";

    private final String m_suffix;
    private final boolean m_decorateLabels;
    private String m_success;

    public HotdeskingValidationDelegate() {
        this("*", false);
    }

    public HotdeskingValidationDelegate(String suffix, boolean decorateLabels) {
        m_decorateLabels = decorateLabels;
        m_suffix = suffix;
    }

    @Override
    public void writeLabelPrefix(IFormComponent component, IMarkupWriter writer, IRequestCycle cycle) {
        if (m_decorateLabels) {
            super.writeLabelPrefix(component, writer, cycle);
        }
    }

    @Override
    public void writeLabelSuffix(IFormComponent component, IMarkupWriter writer, IRequestCycle cycle) {
        if (m_decorateLabels) {
            super.writeLabelSuffix(component, writer, cycle);
        }
    }

    @Override
    public void writeSuffix(IMarkupWriter writer, IRequestCycle cycle_, IFormComponent component_,
            IValidator validator_) {
        if (isInError()) {
            writer.printRaw("&nbsp;");
            writer.begin("span");
            writer.attribute("class", ERROR_CLASS);
            writer.print(m_suffix);
            writer.end();
        }
    }

    @Override
    public void clear() {
        super.clear();
        m_success = null;
    }

    @Override
    public void clearErrors() {
        super.clearErrors();
        m_success = null;
    }

    public void recordSuccess(String success) {
        m_success = success;
    }

    public String getSuccess() {
        return m_success;
    }

    public boolean getHasSuccess() {
        return !getHasErrors() && StringUtils.isNotBlank(m_success);
    }

    public void record(UserException e, Messages messages) {
        String msg = getFormattedMsg(e, messages);
        ValidatorException ve = new ValidatorException(msg);
        record(ve);
    }

    /**
     * Prepares message to be recorded as validation exception
     *
     * It will also check for localization for parameters. If a localization will be found for a
     * parameter it will be translated otherwise not.
     *
     * @param e user exception
     * @param messages optional reference to message store
     * @return user visible message
     */
    private String getFormattedMsg(UserException e, Messages messages) {
        return e.getMessage();       
    }
}
