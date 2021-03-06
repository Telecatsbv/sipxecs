/**
 * Copyright (c) 2014 eZuce, Inc. All rights reserved.
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
package org.sipfoundry.sipxconfig.rest;

import org.apache.commons.lang.StringUtils;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;

/**
 * This is meant to be periodically called by clients in order to keep their web session alive.
 */
public class KeepAliveResource extends UserResource {
    // avoid creating the same object again and again
    private static final StringRepresentation RESPONSE = new StringRepresentation(StringUtils.EMPTY);

    // GET
    @Override
    public Representation represent(Variant variant) throws ResourceException {
        return RESPONSE;
    }
}
