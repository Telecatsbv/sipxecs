/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxconfig.phone.aastra;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import org.sipfoundry.sipxconfig.address.Address;
import org.sipfoundry.sipxconfig.address.AddressManager;
import org.sipfoundry.sipxconfig.address.AddressProvider;
import org.sipfoundry.sipxconfig.address.AddressType;
import org.sipfoundry.sipxconfig.commserver.Location;
import org.sipfoundry.sipxconfig.feature.FeatureManager;
import org.sipfoundry.sipxconfig.feature.LocationFeature;
import org.sipfoundry.sipxconfig.firewall.DefaultFirewallRule;
import org.sipfoundry.sipxconfig.firewall.FirewallManager;
import org.sipfoundry.sipxconfig.firewall.FirewallProvider;
import org.sipfoundry.sipxconfig.firewall.FirewallRule;

public class AastraPhoneFirewallRule implements AddressProvider, FirewallProvider {

    private FeatureManager m_featureManager;
    
    public static final AddressType AASTRA_HOTPROVISIONING_PORT = new AddressType("aastraHotProvisioning");
    private static final Collection<AddressType> ADDRESS_TYPES = Arrays.asList(new AddressType[] {
        AASTRA_HOTPROVISIONING_PORT
    });
    
    

    public FeatureManager getFeatureManager() {
        return m_featureManager;
    }

    public void setFeatureManager(FeatureManager featureManager) {
        m_featureManager = featureManager;
    }

    @Override
    public Collection<DefaultFirewallRule> getFirewallRules(FirewallManager manager) {
        return DefaultFirewallRule.rules(ADDRESS_TYPES, FirewallRule.SystemId.PUBLIC);
    }

    @Override
    public Collection<Address> getAvailableAddresses(AddressManager manager, AddressType type, Location requester) {
        if (!ADDRESS_TYPES.contains(type)) {
            return null;
        }

        Set<LocationFeature> enabledLocationFeatures = m_featureManager.getEnabledLocationFeatures();
        LocationFeature hotLoc = null;
        for (LocationFeature lf : enabledLocationFeatures) {
            if (lf.getId().toLowerCase().equals("hotdesking")) {
                hotLoc = lf;
            }
        }

        Collection<Address> addresses = new ArrayList<Address>();
        if (hotLoc != null) {
            Collection<Location> locations = m_featureManager.getLocationsForEnabledFeature(hotLoc);

            for (Location location : locations) {
                if (type.equals(AASTRA_HOTPROVISIONING_PORT)) {
                    addresses.add(new Address(AASTRA_HOTPROVISIONING_PORT, location.getAddress(), 8090));
                }
            }
        }

        return addresses;
    }
}
