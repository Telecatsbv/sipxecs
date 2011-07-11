/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.admin.commserver.imdb;

import java.util.List;
import java.util.Map;

import org.sipfoundry.sipxconfig.IntegrationTestCase;
import org.sipfoundry.sipxconfig.common.CoreContext;

public class AliasesTestIntegration extends IntegrationTestCase {

    private Aliases m_aliases;
    private CoreContext m_context;

    public void testAddAliases() throws Exception {
        loadDataSetXml("domain/DomainSeed.xml");
        loadDataSet("common/UserSearchSeed.xml");
        m_aliases.setAliasProvider(m_context);
        List<Map<String, String>> aliases = ((DataSetGenerator) m_aliases).generate();
        assertEquals(10, aliases.size());
        Map<String, String> alias1001 = aliases.get(0);
        assertEquals("1@example.org", alias1001.get("identity"));
        assertEquals("sip:userseed1@example.org", alias1001.get("contact"));
        assertEquals("alias", alias1001.get("relation"));

        Map<String, String> alias1002 = aliases.get(1);
        assertEquals("2@example.org", alias1002.get("identity"));
        assertEquals("sip:userseed2@example.org", alias1002.get("contact"));
        assertEquals("alias", alias1002.get("relation"));

        Map<String, String> alias1002String = aliases.get(2);
        assertEquals("two@example.org", alias1002String.get("identity"));
        assertEquals("sip:userseed2@example.org", alias1002String.get("contact"));
        assertEquals("alias", alias1002String.get("relation"));

        Map<String, String> alias1005 = aliases.get(5);
        assertEquals("5@example.org", alias1005.get("identity"));
        assertEquals("\"seed5\"<sip:userseed5@example.org>", alias1005.get("contact"));
        assertEquals("alias", alias1005.get("relation"));
    }

    public void setAliasDataSet(Aliases aliases) {
        m_aliases = aliases;
    }

    public void setCoreContext(CoreContext context) {
        m_context = context;
    }

}
