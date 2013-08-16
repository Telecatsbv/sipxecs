//
// Copyright (c) 2011 Telecats B.V. All rights reserved. Contributed to SIPfoundry and eZuce, Inc. under a Contributor Agreement.
// This library or application is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License (AGPL) as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
// This library or application is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License (AGPL) for more details.
//
//////////////////////////////////////////////////////////////////////////////
package org.sipfoundry.sipxconfig.hotdesking;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Timer for checking hotdesking tasks
 * 
 * @author aliaksandr
 * 
 */
public class HotdeskingCheckTaskTimer extends TimerTask implements
		ApplicationContextAware {
	private static final Log LOG = LogFactory
			.getLog(HotdeskingCheckTaskTimer.class);

	private ApplicationContext applicationContext;

	/**
	 * Path to task's files
	 */
	private String path2taskDir;

	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public void run() {
		try {
			final File f = new File(path2taskDir);
			if (!f.exists() || !f.canWrite() || !f.isDirectory()) {
				LOG.error("Hotdesking timer doesnot have write access to task directory: "
						+ path2taskDir);
				return;
			}

			final File[] listFiles = f.listFiles();
			int length = listFiles.length;
			for (int i = 0; i < length; i++) {
				final Properties properties = new Properties();
				final File file = listFiles[i];
				try {
					try {
						properties.load(new FileInputStream(file));
					} catch (Exception e) {
						LOG.error(
								"Hotdesking timer cannot load task property file: "
										+ file.getAbsolutePath() + " "
										+ e.getMessage(), e);
						continue;
					}
	                                LOG.debug("hotdesking properties loaded"+file.getAbsolutePath()+":\n"+properties);
	
					final HotdeskingApplicationEvent event = new HotdeskingApplicationEvent(
							properties);
					applicationContext.publishEvent(event);
				} finally {
				// cleanup
				boolean delete = file.delete();
				if (!delete) {
					LOG.error("Hotdesking timer cannot delete task file: "
							+ file.getAbsolutePath());
				}
				}
			}
		} catch (Throwable t) {
			LOG.error("Error executing HotdeskingTask:" + t.getMessage(), t);
		}
	}

	/**
	 * Set path to folder with task's files
	 * 
	 * @param path2taskDir
	 */
	public void setPath2taskDir(String path2taskDir) {
		this.path2taskDir = path2taskDir;
	}

}
