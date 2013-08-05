//
// Copyright (c) 2011 Telecats B.V. All rights reserved. Contributed to SIPfoundry and eZuce, Inc. under a Contributor Agreement.
// This library or application is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License (AGPL) as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
// This library or application is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License (AGPL) for more details.
//
//////////////////////////////////////////////////////////////////////////////
package org.sipfoundry.sipxconfig.hotdesking;

import org.springframework.context.ApplicationEvent;

/**
 * Application event for starting hotdesking process (updating user's lines) and
 * generating config for phones
 * 
 * @author aliaksandr
 * 
 */
public class HotdeskingApplicationEvent extends ApplicationEvent {
	private static final long serialVersionUID = -6492439393676401928L;

	public HotdeskingApplicationEvent(Object source) {
		super(source);
	}
}
