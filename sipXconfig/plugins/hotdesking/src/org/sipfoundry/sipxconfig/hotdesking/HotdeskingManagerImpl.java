//
// Copyright (c) 2011 Telecats B.V. All rights reserved. Contributed to SIPfoundry and eZuce, Inc. under a Contributor Agreement.
// This library or application is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License (AGPL) as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
// This library or application is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License (AGPL) for more details.
//
//////////////////////////////////////////////////////////////////////////////
package org.sipfoundry.sipxconfig.hotdesking;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sipfoundry.sipxconfig.admin.AdminContext;
import org.sipfoundry.sipxconfig.admin.commserver.RegistrationContext;
import org.sipfoundry.sipxconfig.admin.commserver.imdb.RegistrationItem;
import org.sipfoundry.sipxconfig.admin.dialplan.DialingRule;
import org.sipfoundry.sipxconfig.admin.localization.LocalizationContext;
import org.sipfoundry.sipxconfig.common.ApplicationInitializedEvent;
import org.sipfoundry.sipxconfig.common.Closure;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.DaoUtils;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.device.HotProvisioningManager;
import org.sipfoundry.sipxconfig.device.ProfileManagerImpl;
import org.sipfoundry.sipxconfig.device.RestartManager;
import org.sipfoundry.sipxconfig.permission.PermissionName;
import org.sipfoundry.sipxconfig.phone.Line;
import org.sipfoundry.sipxconfig.phone.Phone;
import org.sipfoundry.sipxconfig.phone.PhoneContextImpl;
import org.sipfoundry.sipxconfig.service.LoggingEntity;
import org.sipfoundry.sipxconfig.service.SipxFreeswitchService;
import org.sipfoundry.sipxconfig.service.SipxServiceManager;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

/**
 * Hotdesking manager
 * 
 * @author aliaksandr
 * 
 */
public class HotdeskingManagerImpl implements HotdeskingManager, ApplicationListener<ApplicationEvent> {
    private static final String HOTDESKING_ENABLE_AUTO_LOGOFF_OTHER_PHONES = "hotdesking.enable_auto_logoff_other_phones";

    private static final Pattern EXTRACT_USER_RE = Pattern.compile("\\s*<?(?:sip:)?(.+?)[@;](.+)[:].+");

    private static final Pattern EXTRACT_FULL_USER_RE = Pattern
            .compile("\\s*(?:\"?\\s*([^\"<]+?)\\s*\"?)?\\s*<(?:sip:)?(.+?)[@;](.+)[:].+");

    /**
     * Logout user's id
     */
    public static final String LOGOFF_USER = "hotdesking.logoffUser";

    /**
     * Calling to hotdesking user
     */
    public static final String CALLING_USER = "callingUser";
    /**
     * Switch to
     */
    public static final String NEW_USER = "newUser";
    /**
     * Calling phone serial
     */
    public static final String CALLING_PHONE_SERIAL = "phoneSerial";
    /**
     * Just logout on calling phone
     */
    public static final String LOGOUT_ON_THIS_PHONE = "logoutOnThisPhone";
    /**
     * Logout everywhere (on all phones).
     */
    public static final String LOGOUT_EVERYWHERE = "logoutEverywhere";
    /**
     * Logout on all other phones and login only on calling phone
     */
    public static final String HOTDESKING_LOGOUT_EVERYWHERE = "hotdeskingWithEverywhereLogout";
    /**
     * Login on calling phone. Other phones with given user will stay online.
     */
    public static final String HOTDESKING_WITHOUT_LOGOUT = "hotdeskingWithoutLogout";

    private static final String SIP_CONTACT_HOST = "sipContactHost";
    
    private static final int RETRY_LOGOFF_USER_CREATION_DELAY = 30000; // 30 sec


    private static long DAY_IN_MILLIS = 24 * 60 * 60 * 1000;
    private static String FALLBACK_AUTO_LOGOFF_TIME = "00:00";

    // We are using log configuration from SipxHotdeskingService
    private static final Log LOG = LogFactory.getLog(HotdeskingManagerImpl.class);

    private SipxServiceManager sipxServiceManager;
    private CoreContext coreContext;
    private PhoneContextImpl phoneContext;
    private ProfileManagerImpl profileManager;
    private RestartManager restartManager;
    private HotProvisioningManager hotProvisioningManager;
    private LocalizationContext localizationContext;
    private RegistrationContext registrationContext;
    private AutoLogoffTask autoLogoffTask = new AutoLogoffTask();
    private AdminContext m_adminContext;

    /**
     * System folder
     */
    private String sysTempDir;
    /**
     * Timer for checking new tasks
     */
    private HotdeskingCheckTaskTimer timerTask;

    private Timer timer = new Timer();
    private Timer outoLogoffTimer = new Timer();

    /**
     * Hotdesking implementation requires that calls are forwarded to freeswitch service. Then freeswitch server will do
     * call to hotdesking service. We are adding the rule here.
     */
    @Override
    public List<DialingRule> getDialingRules() {
        final String hotdeskingPrefix = getHotdeskingPrefix();
        final String currentLanguage = localizationContext.getCurrentLanguage();
        LOG.debug("Hotdesking:getDialingRules currentLanguage:" + currentLanguage);
        final DialingRule rule = new HotdeskingRule(getSipxFreeswitchAddressAndPort(), hotdeskingPrefix,
                currentLanguage);
        return Collections.singletonList(rule);
    }

    /**
     * Lookup hotdesking prefix
     * 
     * @return
     */
    public String getHotdeskingPrefix() {
        SipxHotdeskingService sipxHotdeskingService = (SipxHotdeskingService) sipxServiceManager
                .getServiceByBeanId(SipxHotdeskingService.BEAN_ID);
        String prefix = sipxHotdeskingService.getHotdeskingPrefix();

        return prefix;
    }

    public String getLogoffUser() {
        SipxHotdeskingService sipxHotdeskingService = (SipxHotdeskingService) sipxServiceManager
                .getServiceByBeanId(SipxHotdeskingService.BEAN_ID);
        return sipxHotdeskingService.getLogoffUser();
    }

    /**
     * Resolve freeswitch port
     * 
     * @return
     */
    private String getSipxFreeswitchAddressAndPort() {
        SipxFreeswitchService service = getSipxFreeswitchService();
        String host;
        if (service.getAddresses().size() > 1) {
            // HACK: this assumes that one of the freeswitch instances runs on a
            // primary location
            // (but that neeeds to be true in order for MOH to work anyway)
            host = service.getLocationsManager().getPrimaryLocation().getAddress();
        } else {
            host = service.getAddress();
        }

        return host + ":" + service.getFreeswitchSipPort();
    }

    /**
     * Lookup freeswitch service
     * 
     * @return
     */
    private SipxFreeswitchService getSipxFreeswitchService() {
        return (SipxFreeswitchService) sipxServiceManager.getServiceByBeanId(SipxFreeswitchService.BEAN_ID);
    }
    /**
     * Enter point of configuration managment
     */
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
    	LOG.debug("Hotdesking received onApplicationEvent: " + event.getClass().getCanonicalName());
    	//Skip the initialization of sipxecs
        if (event instanceof ApplicationInitializedEvent && !m_adminContext.inInitializationPhase()) {
            LOG.info("Hotdesking received ApplicationInitializedEvent");
            applyConfiguration();

    		// schedule hotdesking check task
    		LOG.info("Starting timer for cheking hotdesking tasks for every 30sec.");
            final String path = sysTempDir + File.separatorChar + "hotdesking";
            timerTask.setPath2taskDir(path);
            timer.schedule(timerTask, 1000, 10000);

        } else if (event instanceof HotdeskingApplicationEvent) {
            LOG.info("Hotdesking: handling HotdeskingApplicationEvent");
            final HotdeskingApplicationEvent ev = (HotdeskingApplicationEvent) event;
            final Properties props = (Properties) ev.getSource();
            if (props == null) {
                LOG.info("Hotdesking received ApplicationInitializedEvent, but properties is null");
                return;
            } else {
                // Manage phones
                LOG.info("Hotdesking received ApplicationInitializedEvent, properties:"+props);
                managePhones(props);
            }
        }
    }

    public void applyConfiguration() {
        LOG.info("applyConfiguration");
        // create the special logoff user
        createSpecialLogoffUserIfNotExists(getLogoffUser());

        // schedule autolog of task
        try {
        	scheduleAutoLogoffTasks();
        } catch (Exception e) {
        	LOG.error("HotdeskingManagerImpl.startTasks scheduleAutoLogoffTasks threw exception: "
        			+ e.getMessage() + ". Auto logoff disabled, contact administrator!" );
        }
    }

    /**
     * Manage phones
     * 
     * @param props
     */
    private void managePhones(Properties props) {
        LOG.debug("Hotdesking: staring phone managment by given task's property file: " + props.toString());
        // Resolve given properties
        final String userId = props.getProperty(CALLING_USER);
        final String newUserIdstr = props.getProperty(NEW_USER);
        final String callingPhoneSerial = props.getProperty(CALLING_PHONE_SERIAL);
        final String logoffUserId = props.getProperty(LOGOFF_USER);
        final String autoLogoffString = props.getProperty(HOTDESKING_ENABLE_AUTO_LOGOFF_OTHER_PHONES);
        final String sipContactHost = props.getProperty(SIP_CONTACT_HOST);

        final int indexOf = userId.indexOf("@");
        String callingUserId = userId;
        if (indexOf > 0) {
            LOG.debug("Hotedesking: looks like given userId contains domain with '@'. Use only digits.");
            callingUserId = userId.substring(0, indexOf);
            LOG.debug("Hotedesking: calling callingUserId=" + callingUserId);
        }

        if (logoffUserId == null || StringUtils.isEmpty(logoffUserId)) {
            LOG.warn("Hotedesking: No logoffUser configured");
        }

        // debug output
        LOG.debug("Hotdesking: userId                            : " + userId);
        LOG.debug("Hotdesking: callingUserId                     : " + callingUserId);
        LOG.debug("Hotdesking: newUserIdstr                      : " + newUserIdstr);
        LOG.debug("Hotdesking: callingPhoneSerial                : " + callingPhoneSerial);
        LOG.debug("Hotdesking: logoffUserId                      : " + logoffUserId);
        LOG.debug("Hotdesking: autoLogoffString                  : " + autoLogoffString);
        LOG.debug("Hotdesking: sipContactHost                    : " + sipContactHost);

        boolean autoLogoff = Boolean.parseBoolean(autoLogoffString);

        boolean logoutOnThisPhone = "true".equalsIgnoreCase(props.getProperty(LOGOUT_ON_THIS_PHONE));
        boolean logoutWherewhere = "true".equalsIgnoreCase(props.getProperty(LOGOUT_EVERYWHERE));
        boolean hotdeskingWhithoutLogout = "true".equalsIgnoreCase(props.getProperty(HOTDESKING_WITHOUT_LOGOUT));
        boolean hotdeskingLogoutWherewhere = "true".equalsIgnoreCase(props.getProperty(HOTDESKING_LOGOUT_EVERYWHERE));

        // autoLogoff
        if (autoLogoff) {
            if (logoutOnThisPhone) {
                logoutOnThisPhone = false;
                logoutWherewhere = true;
            } else if (hotdeskingWhithoutLogout) {
                hotdeskingWhithoutLogout = false;
                hotdeskingLogoutWherewhere = true;
            }
        }

        // debug output
        LOG.debug("Hotdesking: autoLogoff                 : " + autoLogoff);
        LOG.debug("Hotdesking: logoutOnThisPhone          : " + logoutOnThisPhone);
        LOG.debug("Hotdesking: logoutWherewhere           : " + logoutWherewhere);
        LOG.debug("Hotdesking: hotdeskingLogoutWherewhere : " + hotdeskingLogoutWherewhere);
        LOG.debug("Hotdesking: hotdeskingWhithoutLogout   : " + hotdeskingWhithoutLogout);

        // find users
        User callingUser = null;
        User newUser = null;
        User logoffUser = null;

        try {
            callingUser = coreContext.loadUserByUserName(callingUserId);
            if (newUserIdstr != null) {
                newUser = coreContext.loadUserByUserName(newUserIdstr);
            }
            logoffUser = findLogoffUser(logoffUserId);
        } catch (Exception e) {
            LOG.error("Hotedesking: one of given userId is not exists: " + callingUserId + "; " + newUserIdstr + ";"
                    + logoffUserId, e);
            return;
        }

        final Integer callingId = callingUser.getId();
        Integer newUserId = -1;
        if (newUser != null) {
            newUserId = newUser.getId();
        }
        Phone callingPhone = null;
        Integer phoneIdBySerialNumber = null;
        LOG.debug("loading phone device for serial:"+callingPhoneSerial);
        try {
            phoneIdBySerialNumber = phoneContext.getPhoneIdBySerialNumber(callingPhoneSerial);
            LOG.debug("found phoneId:"+phoneIdBySerialNumber);
            callingPhone = phoneContext.loadPhone(phoneIdBySerialNumber);
        } catch (Exception e) {
            if (phoneIdBySerialNumber==null) {
                LOG.error("Hotedesking: Lookuping phone by serial (" + callingPhoneSerial + ") fail: " + e.getMessage(), e);
            } else if (callingPhone==null) {
                LOG.error("Error during constructing phone device by serial ("+callingPhoneSerial+"):"+e.getMessage(), e);
            } else {
                LOG.error("Unkonown error when loading phone device by serial ("+callingPhoneSerial+"):"+e.getMessage(), e);
            }
            
            if (LOG.isDebugEnabled()) {
                LOG.debug("=================================");
                LOG.debug("Known registrations:");
                LOG.debug("---------------------------------");
                List<RegistrationItem> registrations = registrationContext.getRegistrations();
                for(RegistrationItem reg : registrations) {
                    LOG.debug("- '"+reg.getInstrument()+"'");
                }
                LOG.debug("=================================");
                LOG.debug("");
                LOG.debug("=================================");
                LOG.debug("Known phones:");
                LOG.debug("---------------------------------");
                List<Phone> phones = phoneContext.loadPhones();
                for(Phone phone:phones) {
                    LOG.debug(phone.getId()+" - "+phone.getSerialNumber()+" - "+phone.getModelLabel());
                }
                LOG.debug("=================================");

            }
            return;
        }

        // debug output
        LOG.debug("Hotdesking: callingUser                : " + callingUser);
        LOG.debug("Hotdesking: newUser                    : " + newUser);
        LOG.debug("Hotdesking: logoffUser                 : " + logoffUser);
        LOG.debug("Hotdesking: callingPhone               : " + callingPhone);

        // Do management
        if (logoutOnThisPhone) {
            LOG.debug("Hotdesking: requested logout");
            // we need the logoutUser
            logout(callingPhone, callingId, logoffUser, sipContactHost, callingUser);

        } else if (logoutWherewhere) {
            LOG.debug("Hotdesking: requested logout on all phones");
            // explicite logoff callingPhone (this will logoff the calling phone also if it's accounts isn't in sync
            // with sipxecs phone account)
            logout(callingPhone, callingId, logoffUser, sipContactHost, callingUser);
            logoutEverywhere(callingUser, callingId, null, logoffUser);

        } else if (hotdeskingLogoutWherewhere) {
            LOG.debug("Hotdesking: requested login with new user and logout on other phones");
            setNewUserForPhone(callingPhone, callingId, newUser, sipContactHost, callingUser,
                    false);
            logoutEverywhere(newUser, newUserId, callingPhone.getId(), logoffUser);

        } else if (hotdeskingWhithoutLogout) {
            LOG.debug("Hotdesking: requested re-login");
            setNewUserForPhone(callingPhone, callingId, newUser, sipContactHost, callingUser,
                    false);

        } else {
            LOG.warn("Hotdesking: nothing todo");
        }
    }

    /**
     * Logout on all phones
     * 
     * @param callingUserId
     */
    private void logoutEverywhere(User callingUser, int callingUserId, Integer excludePhone, User logoffUser) {
        // get user phones
        LOG.debug("Hotdesking::logoutEverywhere get user phones (callingUserId):" + callingUserId);
        Collection<Phone> phonesByUserId = null;
        try {
            phonesByUserId = phoneContext.getPhonesByUserId(callingUserId);
        } catch (Exception e) {
            LOG.error("Hotedesking: Lookuping phone by calling ID fail: " + e.getMessage(), e);
            return;
        }

        // get phone addresses
        LOG.debug("Hotdesking::logoutEverywhere get phone addresses (callingUser):"
                + (callingUser != null ? callingUser.getUserName() : callingUser));
        HashMap<String, String> phoneAddressBySerial = new HashMap<String, String>();
        LOG.debug("Hotdesking::logoutEverywhere registrationContext:" + registrationContext);
        List<RegistrationItem> regsOfThisUser = registrationContext.getRegistrationsByUser(callingUser);
        for (RegistrationItem ri : regsOfThisUser) {
            phoneAddressBySerial.put(ri.getInstrument().toUpperCase(), extractAddress(ri.getContact()));
        }
        LOG.debug("Hotdesking: phoneAddressBySerial:" + phoneAddressBySerial);

        // logoff user phones
        LOG.debug("Hotdesking::logoutEverywhere  logoff user phones (phonesByUserId count):" + phonesByUserId.size());
        final Iterator<Phone> iterator = phonesByUserId.iterator();
        while (iterator.hasNext()) {
            final Phone phone = iterator.next();
            final Integer id = phone.getId();
            if (excludePhone == null || !id.equals(excludePhone)) {
                logout(phone, callingUserId, logoffUser,
                        phoneAddressBySerial.get(phone.getSerialNumber().toUpperCase()), 
                        callingUser);
            }
        }
    }

    /**
     * Set new user for given phone
     * 
     * @param phone
     * @param callingId
     * @param newUser
     */
    private void setNewUserForPhone(Phone phone, Integer callingId, User newUser, String sipContactHost,
            User callingUser, boolean logout) {
        final List<Line> lines = phone.getLines();
        LOG.debug("Hotdesking: setNewUserForPhone phone, newUser, callingId : " + phone.getSerialNumber() + ", "
                + newUser.getName() + "(" + newUser.getId() + "), " + callingId);
        final Integer phoneId = phone.getId();
        Date nowTS = new Date();

        if (lines == null || lines.isEmpty()) {
            LOG.debug("Hotdesking: there are no any line assigned to given phone");
            return;
        }

        // update line of user
        final Iterator<Line> linesIterator = lines.iterator();
        boolean userFoundOnLine = false;
        while (linesIterator.hasNext()) {
            final Line line = linesIterator.next();
            final User user = line.getUser();
            LOG.debug("Hotdesking: found line -> userId : " + user.getId());
            userFoundOnLine = true;
            updateLine(phone, newUser, sipContactHost, callingUser, logout, phoneId,
                    nowTS, line);
        }

        // fallback is because of some glitch user isn't on any line
        if (!userFoundOnLine && lines.size() > 0) {
            Line firstLine = lines.get(0);
            LOG.warn("Hotdesking: CallingUser not found on any of the phone's lines. Using fallback, apply hotdesking to first line. CallingUser is:"
                    + (callingUser != null ? callingUser.getUserName() : "null")
                    + ", user on first line:"
                    + firstLine.getUserName());

            updateLine(phone, newUser, sipContactHost, callingUser, logout, phoneId,
                    nowTS, firstLine);

        } else if (!userFoundOnLine) {
            LOG.warn("Hotdesking: CallingUser not found on any of the phone's lines. Hotdesking failed. CallingUser:"
                    + (callingUser != null ? callingUser.getUserName() : "null"));
        }
    }

    private void updateLine(Phone phone, User newUser, String sipContactHost, User callingUser,boolean logout, final Integer phoneId, Date nowTS, final Line line) {
        LOG.debug("Hotdesking: updating line by new given user: lineId=" + line.getId());
        // set new user to line
        line.setUser(newUser);

        // store line
        phoneContext.storeLine(line);
        LOG.debug("Hotdesking: generate new profile for phoneId=" + phoneId);

        // write new profiles
        profileManager.generateProfile(phoneId, false, nowTS);
        
        // provision (restart phone if xml provisioning isn't supported)
        // this only is needed when sipContextHost is known. If sipContextHost isn't set, phone is
        // probably offline and doesn't need to be provisioned
        if (!StringUtils.isEmpty(sipContactHost)) {
            if (phone.isHotProvisioningSupported()) {
                LOG.info("Hotdesking: SendXmlProvisionNotify");
                HashMap<String, String> hotProvProps = new HashMap<String, String>();
                hotProvProps.put(HotProvisioningManager.SIP_CONTACT_HOST_PROP, sipContactHost);
                hotProvisioningManager.hotProvision(phoneId, nowTS, hotProvProps);
            } else {
                LOG.info("Hotdesking: Restarting phone");
                restartManager.restart(phoneId, nowTS);
            }
        } else {
            LOG.debug("Hotdesking: Skipping provisioning, no contact host for (phoneId):" + phone.getId());
        }
    }

    /**
     * Logout on single phone
     * 
     * @param phone
     * @param callingId
     */
    private void logout(Phone phone, Integer callingId, User logoutUser, String sipContactHost, User callingUser) {
        setNewUserForPhone(phone, callingId, logoutUser, sipContactHost, callingUser, true);
    }

    /**
     * Lookuping special internal user that should be assigned to logouted phone.
     * 
     * @return
     */
    private User findLogoffUser(String logoffUserId) {
        User specialUser = null;
        
        if( coreContext.getAllUsersCount() == 0 )
        	throw new UserCreationException("Not creating hotdesking user while there are 0 users in the database.");
        
        try {
            specialUser = coreContext.loadUserByUserName(logoffUserId);
        } catch (Exception e) {
            LOG.warn(
                    "Hotdesking: loading special user for logout fail; may be he does not exists. Will try to create it."
                            + e.getMessage(), e);
        }

        return specialUser;
    }
    
    /**
     * IT MIGHT TAKE SOME TIME BEFORE SPECIAL HOTDESKING USER IS CREATED
     * <P/>
     *  NOTE: We dislike this code, but we were forced to do it this way because:<BR/>
     *  1 - superadmin user is never created if hotdesking user (~~in~HD) is already there<BR/>
     *  2 - superadmin creation is delayed until the superadmin password is set from web interface, we see no<BR/>
     *  logical way to hook our hotdesking user creation to this<BR/>
     * <P/>
     *  Note: See also: LoginPage.java for responsible code<BR/>
     * @param logoffUserId
     */
    private void createSpecialLogoffUserIfNotExists(final String logoffUserId) {
        int userCount = coreContext.getUsersCount();
        if (userCount == 0) {
            LOG.info("createSpecialLogoffUser: Delay creation of the special hotdesking user (default is this the '~~in~HD' user), we need to wait until superadmin user is created.");
            // reschedule
            timer.schedule(new HotdeskingUserCreationTask(), RETRY_LOGOFF_USER_CREATION_DELAY);
            
        } else {
            User specialUser = null;
    
            try {
                specialUser = coreContext.loadUserByUserName(logoffUserId);
            } catch (Exception e) {
                LOG.warn(
                        "Hotdesking: loading special user for logout fail; may be he does not exists. Will try to create it."
                                + e.getMessage(), e);
            }
    
            if (specialUser == null) {
                LOG.debug("createSpecialLogoffUser: creating new user " + logoffUserId);
                specialUser = coreContext.newUser();
                specialUser.setName(logoffUserId);
                specialUser.setFirstName(getHotdeskingPrefix() + " to login");
                specialUser.setUserName(specialUser.getUserName());
                specialUser.setPin("1234", coreContext.getAuthorizationRealm());
                specialUser.setSipPassword(RandomStringUtils.randomAlphanumeric(10));
                specialUser.setSettingTypedValue(PermissionName.HOTDESKING.getPath(), true);
                specialUser.setSettingTypedValue(PermissionName.AUTO_ATTENDANT_DIALING.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.EXCHANGE_VOICEMAIL.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.FREESWITH_VOICEMAIL.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.INTERNATIONAL_DIALING.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.LOCAL_DIALING.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.LONG_DISTANCE_DIALING.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.MOBILE.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.MUSIC_ON_HOLD.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.NINEHUNDERED_DIALING.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.PERSONAL_AUTO_ATTENDANT.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.RECORD_SYSTEM_PROMPTS.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.SUBSCRIBE_TO_PRESENCE.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.SUPERADMIN.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.TOLL_FREE_DIALING.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.TUI_CHANGE_PIN.getPath(), false);
                specialUser.setSettingTypedValue(PermissionName.VOICEMAIL.getPath(), false);
    
                coreContext.saveUser(specialUser);
                // } else {
                // Raymond Domingo 20120613 - disabled because some customers like
                // to edit the special logoff user's first name to customize for
                // there organization.
                // update description of special user when hotdeskingPrefix is
                // changed
                // if (!(getHotdeskingPrefix() +
                // " to login").equals(specialUser.getFirstName())) {
                // specialUser.setFirstName(getHotdeskingPrefix() + " to login");
                // coreContext.saveUser(specialUser);
                // }
            } else {
                LOG.warn("Hotdesking: skipping creation of special hotdesking user, it's already there!");
            }
        }
    }


    public String getSysTempDir() {
        return sysTempDir;
    }

    public void setSysTempDir(String sysTempDir) {
        this.sysTempDir = sysTempDir;
    }

    public HotdeskingCheckTaskTimer getTimerTask() {
        return timerTask;
    }

    public void setTimerTask(HotdeskingCheckTaskTimer timerTask) {
        this.timerTask = timerTask;
    }

    public CoreContext getCoreContext() {
        return coreContext;
    }

    public void setCoreContext(CoreContext coreContext) {
        this.coreContext = coreContext;
    }

    public PhoneContextImpl getPhoneContext() {
        return phoneContext;
    }

    public void setPhoneContext(PhoneContextImpl phoneContext) {
        this.phoneContext = phoneContext;
    }

    public ProfileManagerImpl getProfileManager() {
        return profileManager;
    }

    public void setProfileManager(ProfileManagerImpl profileManager) {
        this.profileManager = profileManager;
    }

    public RestartManager getRestartManager() {
        return restartManager;
    }

    public void setRestartManager(RestartManager restartManager) {
        this.restartManager = restartManager;
    }

    public HotProvisioningManager getHotProvisioningManager() {
        return hotProvisioningManager;
    }

    public void setHotProvisioningManager(HotProvisioningManager hotProvisioningManager) {
        this.hotProvisioningManager = hotProvisioningManager;
    }

    public LocalizationContext getLocalizationContext() {
        return localizationContext;
    }

    public void setLocalizationContext(LocalizationContext localizationContext) {
        this.localizationContext = localizationContext;
    }

    public RegistrationContext getRegistrationContext() {
        return registrationContext;
    }

    public void setRegistrationContext(RegistrationContext registrationContext) {
        this.registrationContext = registrationContext;
    }
    
	public void setAdminContext(AdminContext adminContext) {
		this.m_adminContext = adminContext;
	}

	/**
     * Extracts user name if available. Otherwise it returns the user id
     */
    private String extractAddress(String uri) {
        if (uri == null) {
            return null;
        }
        Matcher matcher = EXTRACT_FULL_USER_RE.matcher(uri);
        if (!matcher.matches()) {
            matcher = EXTRACT_USER_RE.matcher(uri);
            if (matcher.matches()) {
                return matcher.group(2);
            }
            return null;
        } else {
            return matcher.group(3);
        }
    }

    private Date calculateNextAutoLogoffTs(String autoLogoffTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        Calendar c1 = Calendar.getInstance();
        try {
            c1.setTime(sdf.parse(autoLogoffTime));
        } catch (ParseException e) {
            LOG.warn("Couldn't parse autoLogoffTime:" + autoLogoffTime, e);
            try {
                c1.setTime(sdf.parse(FALLBACK_AUTO_LOGOFF_TIME));
            } catch (ParseException e1) {
                LOG.warn("Couldn't parse fallback autoLogoffTime:" + FALLBACK_AUTO_LOGOFF_TIME, e1);
            }
        }

        Calendar c2 = Calendar.getInstance();
        c2.set(Calendar.HOUR_OF_DAY, c1.get(Calendar.HOUR_OF_DAY));
        c2.set(Calendar.MINUTE, c1.get(Calendar.MINUTE));

        if (c2.getTime().before(new Date())) {
            c2.setTimeInMillis(c2.getTimeInMillis() + DAY_IN_MILLIS);
        }

        Date tsNextAutoLogoff = c2.getTime();
        return tsNextAutoLogoff;
    }

    public SipxServiceManager getSipxServiceManager() {
        return sipxServiceManager;
    }

    public void setSipxServiceManager(SipxServiceManager sipxServiceManager) {
        this.sipxServiceManager = sipxServiceManager;
    }

    private void scheduleAutoLogoffTasks() {
        SipxHotdeskingService sipxHotdeskingService = (SipxHotdeskingService) sipxServiceManager.getServiceByBeanId(SipxHotdeskingService.BEAN_ID);
        String autoLogoffTime = sipxHotdeskingService.getAutoLogofftime();
        LOG.info("Going to schedule autologoffTask:"+autoLogoffTime);
        LOG.debug("AutoLogoffTask::autoLogoffTime:"+autoLogoffTime);
        Date nextAutoLogoffTS = calculateNextAutoLogoffTs(autoLogoffTime);
        LOG.debug("AutoLogoffTask::schedule autoLogoffTask for:" + nextAutoLogoffTS);
        // cancel previous task if needed
        try {
            if (autoLogoffTask != null) {
                autoLogoffTask.cancel();
            }
        } finally {
            // schedule new task, also if cancel of previous fails
            autoLogoffTask = new AutoLogoffTask();
            outoLogoffTimer.schedule(autoLogoffTask, nextAutoLogoffTS);
        }
    }

    private class AutoLogoffTask extends TimerTask {

        @Override
        public synchronized void run() {
            LOG.info("AutoLogoffTask::Running:"+new Date()+"...");
            try {
                SipxHotdeskingService sipxHotdeskingService = (SipxHotdeskingService) sipxServiceManager
                        .getServiceByBeanId(SipxHotdeskingService.BEAN_ID);
                final String logoffUserId = sipxHotdeskingService.getLogoffUser();
                final User logoffUser = findLogoffUser(logoffUserId);
                DaoUtils.forAllUsersDo(coreContext, new Closure<User>() {
                    @Override
                    public void execute(User user) {
                        user = coreContext.loadUser(user.getId());
                        if (user.getUserPermissionNames().contains(PermissionName.HOTDESKING_AUTO_LOGOFF.getName())) {
                            LOG.info("AutoLogoffTask::Auto logoff user (username,userId,logoffUserName,logoffUserId):"+user.getUserName()+","+user.getId()+","+(logoffUser!=null?logoffUser.getUserName():logoffUser)+","+logoffUserId);
                            logoutEverywhere(user, user.getId(), null, logoffUser);

                        } else {
                            LOG.info("AutoLogoffTask::Skip autologoff for user (no '"+PermissionName.HOTDESKING_AUTO_LOGOFF.getName()+"' permission):"+user.getUserName());
                        }
                    }
                });
            } finally {
                LOG.info("AutoLogoffTask::Running...done");
                try {
                    // wait at least one minute before rescheduling, or task might loop multiple times
                    wait(60000);
                } catch (InterruptedException e) {
                }
                scheduleAutoLogoffTasks();
            }
        }
    }

    private class HotdeskingUserCreationTask extends TimerTask {
        
        public HotdeskingUserCreationTask() {
            super();
        }

        @Override
        public void run() {
            // create logoffUser if needed
            try {
                LOG.info("HotdeskingUserCreationTask: (delayed) retry createSpecialLogoffUser");
                createSpecialLogoffUserIfNotExists(getLogoffUser());
            } catch (Exception e) {
                LOG.error("HotdeskingUserCreationTask: (delayed) retru createSpecialLogoffUser threw exception: " + e.getMessage()
                        + ". This is OK if in first-run");
            }
        }
    }
    
    class UserCreationException extends RuntimeException {
    	public UserCreationException(String message) {
    		super(message);
    	}
    }
}
