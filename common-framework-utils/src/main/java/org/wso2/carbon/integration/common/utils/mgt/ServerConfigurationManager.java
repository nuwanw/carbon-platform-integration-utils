/*
*Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*WSO2 Inc. licenses this file to you under the Apache License,
*Version 2.0 (the "License"); you may not use this file except
*in compliance with the License.
*You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
*Unless required by applicable law or agreed to in writing,
*software distributed under the License is distributed on an
*"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*KIND, either express or implied.  See the License for the
*specific language governing permissions and limitations
*under the License.
*/
package org.wso2.carbon.integration.common.utils.mgt;

import org.apache.axis2.AxisFault;
import org.wso2.carbon.authenticator.stub.LoginAuthenticationExceptionException;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.engine.frameworkutils.CodeCoverageUtils;
import org.wso2.carbon.automation.test.utils.common.FileManager;
import org.wso2.carbon.integration.common.admin.client.ServerAdminClient;
import org.wso2.carbon.integration.common.extensions.carbonserver.ClientConnectionUtil;
import org.wso2.carbon.utils.ServerConstants;

import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.rmi.RemoteException;

/**
 * This class can be used to configure server by  replacing axis2.xml or carbon.xml
 */
public class ServerConfigurationManager {
    //    private final String CARBON_XML = "carbon.xml";
    private static final long TIME_OUT = 240000;
    private boolean isFileBackUp = false;
    private File originalConfig;
    private File backUpConfig;
    private int port;
    private String hostname;
    private String backEndUrl;
    private AutomationContext autoCtx;
    private String sessionCookie;

    /**
     * Create a  ServerConfigurationManager
     *
     * @throws AxisFault
     * @throws MalformedURLException - if backend url is invalid
     */
    public ServerConfigurationManager(String productGroup, TestUserMode userMode)
            throws RemoteException, MalformedURLException, XPathExpressionException, LoginAuthenticationExceptionException {
        autoCtx = new AutomationContext(productGroup, userMode);
        sessionCookie = autoCtx.login();
        URL serverUrl = new URL(autoCtx.getContextUrls().getServiceUrl());
        this.backEndUrl = autoCtx.getContextUrls().getBackEndUrl();
        port = serverUrl.getPort();
        hostname = serverUrl.getHost();
    }

    /**
     * backup the current server configuration file
     *
     * @param fileName
     */
    private void backupConfiguration(String fileName) {
        //restore backup configuration
        String carbonHome = System.getProperty(ServerConstants.CARBON_HOME);
        String confDir = carbonHome + File.separator + "repository" + File.separator + "conf"
                + File.separator;
        String AXIS2_XML = "axis2";
        if (fileName.contains(AXIS2_XML)) {
            confDir = confDir + "axis2" + File.separator;
        }
        originalConfig = new File(confDir + fileName);
        backUpConfig = new File(confDir + fileName + ".backup");
        originalConfig.renameTo(backUpConfig);
        isFileBackUp = true;
    }

    /**
     * Backup a file residing in a cabron server.
     *
     * @param file file residing in server to backup.
     */
    private void backupConfiguration(File file) {
        //restore backup configuration
        originalConfig = file;
        backUpConfig = new File(file.getAbsolutePath() + File.separator + file.getName() + ".backup");
        originalConfig.renameTo(backUpConfig);
        isFileBackUp = true;
    }

    /**
     * Apply configuration from source file to a target file without restarting.
     *
     * @param sourceFile Source file to copy.
     * @param targetFile Target file that is to be backed up and replaced.
     * @param backup     boolean value, set this to true if you want to backup the original file.
     * @throws Exception
     */
    public void applyConfigurationWithoutRestart(File sourceFile, File targetFile, boolean backup) throws Exception {
        // Using inputstreams to copy bytes instead of Readers that copy chars. Otherwise thigns like JKS files get corrupted during copy.
        FileChannel source = null;
        FileChannel destination = null;
        if (backup) {
            backupConfiguration(targetFile);
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(originalConfig).getChannel();
        } else {
            if (!targetFile.exists()) {
                targetFile.createNewFile();
            }
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(targetFile).getChannel();
        }
        destination.transferFrom(source, 0, source.size());
        if (source != null) {
            source.close();
        }
        if (destination != null) {
            destination.close();
        }
    }

    /**
     * restore to a last configuration and restart the server
     *
     * @throws Exception
     */
    public void restoreToLastConfiguration() throws Exception {
        if (isFileBackUp) {
            backUpConfig.renameTo(originalConfig);
            isFileBackUp = false;
            restartGracefully();
        }
    }

    /**
     * apply configuration file and restart server to take effect the configuration
     *
     * @param newConfig
     * @throws Exception
     */
    public void applyConfiguration(File newConfig) throws Exception {
        //to backup existing configuration
        backupConfiguration(newConfig.getName());
        FileReader in = new FileReader(newConfig);
        FileWriter out = new FileWriter(originalConfig);
        int c;
        while ((c = in.read()) != -1) {
            out.write(c);
        }
        in.close();
        out.close();
        restartGracefully();
    }

    /**
     * apply configuration file and restart server to take effect the configuration
     *
     * @param newConfig
     * @throws Exception
     */
    public void applyConfigurationWithoutRestart(File newConfig) throws Exception {
        //to backup existing configuration
        backupConfiguration(newConfig.getName());
        FileReader in = new FileReader(newConfig);
        FileWriter out = new FileWriter(originalConfig);
        int c;
        while ((c = in.read()) != -1) {
            out.write(c);
        }
        in.close();
        out.close();
    }

    /**
     * Methods to replace configuration files in products.
     *
     * @param sourceFile - configuration file to be copied for your local machine or carbon server it self.
     * @param targetFile - configuration file in carbon server. e.g - path to axis2.xml in config directory
     * @throws Exception - if file IO error
     */
    public void applyConfiguration(File sourceFile, File targetFile) throws Exception {
        //to backup existing configuration
        backupConfiguration(targetFile.getName());
        FileReader in = new FileReader(sourceFile);
        FileWriter out = new FileWriter(originalConfig);
        int c;
        while ((c = in.read()) != -1) {
            out.write(c);
        }
        in.close();
        out.close();
        restartGracefully();
    }

    /**
     * Restart Server Gracefully  from admin user
     *
     * @throws Exception
     */
    public void restartGracefully() throws Exception {
        //todo use ServerUtils class restart
        ServerAdminClient serverAdmin = new ServerAdminClient(backEndUrl, sessionCookie);
        serverAdmin.restartGracefully();
        CodeCoverageUtils.renameCoverageDataFile(System.getProperty(ServerConstants.CARBON_HOME));
        Thread.sleep(20000); //forceful wait until emma dump coverage data file.
        ClientConnectionUtil.waitForPort(port, TIME_OUT, true, hostname);
        ClientConnectionUtil.waitForLogin(String.valueOf(port), autoCtx.getSuperTenant().getDomain(),
                autoCtx.getSuperTenant().getTenantAdmin().getUserName(), autoCtx.getSuperTenant().getTenantAdmin().getPassword());
    }

    /**
     * Restart server gracefully from current user session
     *
     * @param sessionCookie
     * @throws Exception
     */
    public void restartGracefully(String sessionCookie) throws Exception {
        //todo use ServerUtils class restart
        ServerAdminClient serverAdmin = new ServerAdminClient(backEndUrl, sessionCookie);
        serverAdmin.restartGracefully();
        CodeCoverageUtils.renameCoverageDataFile(System.getProperty(ServerConstants.CARBON_HOME));
        Thread.sleep(20000); //forceful wait until emma dump coverage data file.
        ClientConnectionUtil.waitForPort(port, TIME_OUT, true, hostname);
        ClientConnectionUtil.waitForLogin(String.valueOf(port), autoCtx.getSuperTenant().getDomain(),
                autoCtx.getSuperTenant().getTenantAdmin().getUserName(), autoCtx.getSuperTenant().getTenantAdmin().getPassword());
    }

    /**
     * Copy Jar file to server component/lib
     *
     * @param jar
     * @throws IOException
     * @throws URISyntaxException
     */
    public void copyToComponentLib(File jar) throws IOException, URISyntaxException {
        String carbonHome = System.getProperty(ServerConstants.CARBON_HOME);
        String lib = carbonHome + File.separator + "repository" + File.separator + "components" + File.separator
                + "lib";
        FileManager.copyJarFile(jar, lib);
    }

    /**
     * @param fileName
     * @throws IOException
     * @throws URISyntaxException
     */
    public void removeFromComponentLib(String fileName) throws IOException, URISyntaxException {
        String carbonHome = System.getProperty(ServerConstants.CARBON_HOME);
        String filePath = carbonHome + File.separator + "repository" + File.separator + "components" + File.separator
                + "lib" + File.separator + fileName;
        FileManager.deleteFile(filePath);
//      removing osgi bundle from dropins; OSGI bundle versioning starts with _1.0.0
        fileName = fileName.replace("-", "_");
        fileName = fileName.replace(".jar", "_1.0.0.jar");
        removeFromComponentDropins(fileName);
    }

    /**
     * /**
     * Copy Jar file to server component/dropins
     *
     * @param jar
     * @throws IOException
     * @throws URISyntaxException
     */
    public void copyToComponentDropins(File jar) throws IOException, URISyntaxException {
        String carbonHome = System.getProperty(ServerConstants.CARBON_HOME);
        String lib = carbonHome + File.separator + "repository" + File.separator + "components" + File.separator
                + "dropins";
        FileManager.copyJarFile(jar, lib);
    }

    /**
     * @param fileName
     * @throws IOException
     * @throws URISyntaxException
     */
    public void removeFromComponentDropins(String fileName) throws IOException, URISyntaxException {
        String carbonHome = System.getProperty(ServerConstants.CARBON_HOME);
        String filePath = carbonHome + File.separator + "repository" + File.separator + "components" + File.separator
                + "dropins" + File.separator + fileName;
        FileManager.deleteFile(filePath);
    }
}

