/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.ant.jmx;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.catalina.ant.BaseRedirectorHelperTask;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

/**
 * Access <em>JMX</em> JSR 160 MBeans Server.
 * <ul>
 * <li>open more then one JSR 160 rmi connection</li>
 * <li>Get/Set Mbeans attributes</li>
 * <li>Call Mbean Operation with arguments</li>
 * <li>Argument values can be converted from string to int,long,float,double,boolean,ObjectName or InetAddress</li>
 * <li>Query Mbeans</li>
 * <li>Show Get, Call, Query result at Ant console log</li>
 * <li>Bind Get, Call, Query result at Ant properties</li>
 * </ul>
 * Examples: open server with reference and authorisation
 *
 * <pre>
 *
 *    &lt;jmxOpen
 *            host=&quot;127.0.0.1&quot;
 *            port=&quot;9014&quot;
 *            username=&quot;monitorRole&quot;
 *            password=&quot;mysecret&quot;
 *            ref=&quot;jmx.myserver&quot;
 *        /&gt;
 * </pre>
 *
 * All calls after opening with same refid reuse the connection.
 * <p>
 * First call to a remote MBeanserver save the JMXConnection a referenz <em>jmx.server</em>
 * </p>
 * All JMXAccessorXXXTask support the attribute <em>if</em> and <em>unless</em>. With <em>if</em> the task is only
 * execute when property exist and with <em>unless</em> when property not exists. <br>
 * <b>NOTE </b>: These tasks require Ant 1.6 or later interface.
 *
 * @author Peter Rossbach
 *
 * @since 5.5.10
 */
public class JMXAccessorTask extends BaseRedirectorHelperTask {

    public static final String JMX_SERVICE_PREFIX = "service:jmx:rmi:///jndi/rmi://";

    public static final String JMX_SERVICE_SUFFIX = "/jmxrmi";

    // ----------------------------------------------------- Instance Variables

    private String name = null;

    private String resultproperty;

    private String url = null;

    private String host = "localhost";

    private String port = "8050";

    private String password = null;

    private String username = null;

    private String ref = "jmx.server";

    private boolean echo = false;

    private boolean separatearrayresults = true;

    private String delimiter;

    private String unlessCondition;

    private String ifCondition;

    private final Properties properties = new Properties();

    // ------------------------------------------------------------- Properties

    /**
     * Get the name used at remote MbeanServer.
     *
     * @return the name used at remote MbeanServer
     */
    public String getName() {
        return this.name;
    }

    public void setName(String objectName) {
        this.name = objectName;
    }

    /**
     * @return Returns the resultproperty.
     */
    public String getResultproperty() {
        return resultproperty;
    }

    /**
     * @param propertyName The resultproperty to set.
     */
    public void setResultproperty(String propertyName) {
        this.resultproperty = propertyName;
    }

    /**
     * @return Returns the delimiter.
     */
    public String getDelimiter() {
        return delimiter;
    }

    /**
     * @param separator The delimiter to set.
     */
    public void setDelimiter(String separator) {
        this.delimiter = separator;
    }

    /**
     * @return Returns the echo.
     */
    public boolean isEcho() {
        return echo;
    }

    /**
     * @param echo The echo to set.
     */
    public void setEcho(boolean echo) {
        this.echo = echo;
    }

    /**
     * @return Returns the separatearrayresults.
     */
    public boolean isSeparatearrayresults() {
        return separatearrayresults;
    }

    /**
     * @param separateArrayResults The separatearrayresults to set.
     */
    public void setSeparatearrayresults(boolean separateArrayResults) {
        this.separatearrayresults = separateArrayResults;
    }

    /**
     * @return The login password for the <code>Manager</code> application.
     */
    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return The login username for the <code>JMX</code> MBeanServer.
     */
    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return The URL of the <code>JMX JSR 160</code> MBeanServer to be used.
     */
    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * @return The Host of the <code>JMX JSR 160</code> MBeanServer to be used.
     */
    public String getHost() {
        return this.host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @return The Port of the <code>JMX JSR 160</code> MBeanServer to be used.
     */
    public String getPort() {
        return this.port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    /**
     * @return Returns the useRef.
     */
    public boolean isUseRef() {
        return ref != null && !ref.isEmpty();
    }

    /**
     * @return Returns the ref.
     */
    public String getRef() {
        return ref;
    }

    /**
     * @param refId The ref to set.
     */
    public void setRef(String refId) {
        this.ref = refId;
    }

    /**
     * @return Returns the ifCondition.
     */
    public String getIf() {
        return ifCondition;
    }

    /**
     * Only execute if a property of the given name exists in the current project.
     *
     * @param c property name
     */
    public void setIf(String c) {
        ifCondition = c;
    }

    /**
     * @return Returns the unlessCondition.
     */
    public String getUnless() {
        return unlessCondition;
    }

    /**
     * Only execute if a property of the given name does not exist in the current project.
     *
     * @param c property name
     */
    public void setUnless(String c) {
        unlessCondition = c;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Execute the specified command. This logic only performs the common attribute validation required by all
     * subclasses; it does not perform any functional logic directly.
     *
     * @exception BuildException if a validation error occurs
     */
    @Override
    public void execute() throws BuildException {
        if (testIfCondition() && testUnlessCondition()) {
            try {
                String error;

                MBeanServerConnection jmxServerConnection = getJMXConnection();
                error = jmxExecute(jmxServerConnection);
                if (error != null && isFailOnError()) {
                    // exception should be thrown only if failOnError == true
                    // or error line will be logged twice
                    throw new BuildException(error);
                }
            } catch (Exception e) {
                if (isFailOnError()) {
                    throw new BuildException(e);
                } else {
                    handleErrorOutput(e.getMessage());
                }
            } finally {
                closeRedirector();
            }
        }
    }

    /**
     * Create a new JMX Connection with auth when username and password is set.
     *
     * @param url      URL to be used for the JMX connection (if specified, it is a complete URL so host and port will
     *                     not be used)
     * @param host     Host name of the JMX server
     * @param port     Port number for the JMX server
     * @param username User name for the connection
     * @param password Credentials corresponding to the specified user
     *
     * @throws MalformedURLException Invalid URL specified
     * @throws IOException           Other connection error
     *
     * @return the JMX connection
     */
    public static MBeanServerConnection createJMXConnection(String url, String host, String port, String username,
            String password) throws MalformedURLException, IOException {
        String urlForJMX;
        if (url != null) {
            urlForJMX = url;
        } else {
            urlForJMX = JMX_SERVICE_PREFIX + host + ":" + port + JMX_SERVICE_SUFFIX;
        }
        Map<String,String[]> environment = null;
        if (username != null && password != null) {
            String[] credentials = new String[2];
            credentials[0] = username;
            credentials[1] = password;
            environment = new HashMap<>();
            environment.put(JMXConnector.CREDENTIALS, credentials);
        }
        return JMXConnectorFactory.connect(new JMXServiceURL(urlForJMX), environment).getMBeanServerConnection();

    }

    /**
     * test the if condition
     *
     * @return true if there is no if condition, or the named property exists
     */
    protected boolean testIfCondition() {
        if (ifCondition == null || ifCondition.isEmpty()) {
            return true;
        }
        return getProperty(ifCondition) != null;
    }

    /**
     * test the unless condition
     *
     * @return true if there is no unless condition, or there is a named property but it doesn't exist
     */
    protected boolean testUnlessCondition() {
        if (unlessCondition == null || unlessCondition.isEmpty()) {
            return true;
        }
        return getProperty(unlessCondition) == null;
    }

    /**
     * Get Current Connection from <em>ref</em> parameter or create a new one!
     *
     * @param project  The Ant project
     * @param url      URL to be used for the JMX connection (if specified, it is a complete URL so host and port will
     *                     not be used)
     * @param host     Host name of the JMX server
     * @param port     Port number for the JMX server
     * @param username User name for the connection
     * @param password Credentials corresponding to the specified user
     * @param refId    The Id of the reference to retrieve in the project
     *
     * @throws MalformedURLException Invalid URL specified
     * @throws IOException           Other connection error
     *
     * @return the JMX connection
     */
    @SuppressWarnings("null")
    public static MBeanServerConnection accessJMXConnection(Project project, String url, String host, String port,
            String username, String password, String refId) throws MalformedURLException, IOException {
        MBeanServerConnection jmxServerConnection = null;
        boolean isRef = project != null && refId != null && !refId.isEmpty();
        if (isRef) {
            Object pref = project.getReference(refId);
            try {
                jmxServerConnection = (MBeanServerConnection) pref;
            } catch (ClassCastException cce) {
                project.log("wrong object reference " + refId + " - " + pref.getClass());
                return null;
            }
        }
        if (jmxServerConnection == null) {
            jmxServerConnection = createJMXConnection(url, host, port, username, password);
        }
        if (isRef && jmxServerConnection != null) {
            project.addReference(refId, jmxServerConnection);
        }
        return jmxServerConnection;
    }

    // ------------------------------------------------------ protected Methods

    /**
     * get JMXConnection
     *
     * @throws MalformedURLException Invalid URL specified
     * @throws IOException           Other connection error
     *
     * @return the JMX connection
     */
    protected MBeanServerConnection getJMXConnection() throws MalformedURLException, IOException {

        MBeanServerConnection jmxServerConnection = null;
        if (isUseRef()) {
            Object pref;
            if (getProject() != null) {
                pref = getProject().getReference(getRef());
                if (pref != null) {
                    try {
                        jmxServerConnection = (MBeanServerConnection) pref;
                    } catch (ClassCastException cce) {
                        getProject().log("Wrong object reference " + getRef() + " - " + pref.getClass());
                        return null;
                    }
                }
            }
            if (jmxServerConnection == null) {
                jmxServerConnection = accessJMXConnection(getProject(), getUrl(), getHost(), getPort(), getUsername(),
                        getPassword(), getRef());
            }
        } else {
            jmxServerConnection = accessJMXConnection(getProject(), getUrl(), getHost(), getPort(), getUsername(),
                    getPassword(), null);
        }
        return jmxServerConnection;
    }

    /**
     * Execute the specified command, based on the configured properties. The input stream will be closed upon
     * completion of this task, whether it was executed successfully or not.
     *
     * @param jmxServerConnection The JMX connection that should be used
     *
     * @return An error message string in some situations
     *
     * @exception Exception if an error occurs
     */
    public String jmxExecute(MBeanServerConnection jmxServerConnection) throws Exception {

        if ((jmxServerConnection == null)) {
            throw new BuildException("Must open a connection!");
        } else if (isEcho()) {
            handleOutput("JMX Connection ref=" + ref + " is open!");
        }
        return null;
    }

    /**
     * Convert string to datatype FIXME How we can transfer values from ant project reference store (ref)?
     *
     * @param value     The value
     * @param valueType The type
     *
     * @return The converted object
     */
    protected Object convertStringToType(String value, String valueType) {
        if ("java.lang.String".equals(valueType)) {
            return value;
        }

        Object convertValue = value;
        if ("java.lang.Integer".equals(valueType) || "int".equals(valueType)) {
            try {
                convertValue = Integer.valueOf(value);
            } catch (NumberFormatException ex) {
                if (isEcho()) {
                    handleErrorOutput("Unable to convert to integer:" + value);
                }
            }
        } else if ("java.lang.Long".equals(valueType) || "long".equals(valueType)) {
            try {
                convertValue = Long.valueOf(value);
            } catch (NumberFormatException ex) {
                if (isEcho()) {
                    handleErrorOutput("Unable to convert to long:" + value);
                }
            }
        } else if ("java.lang.Boolean".equals(valueType) || "boolean".equals(valueType)) {
            convertValue = Boolean.valueOf(value);
        } else if ("java.lang.Float".equals(valueType) || "float".equals(valueType)) {
            try {
                convertValue = Float.valueOf(value);
            } catch (NumberFormatException ex) {
                if (isEcho()) {
                    handleErrorOutput("Unable to convert to float:" + value);
                }
            }
        } else if ("java.lang.Double".equals(valueType) || "double".equals(valueType)) {
            try {
                convertValue = Double.valueOf(value);
            } catch (NumberFormatException ex) {
                if (isEcho()) {
                    handleErrorOutput("Unable to convert to double:" + value);
                }
            }
        } else if ("javax.management.ObjectName".equals(valueType) || "name".equals(valueType)) {
            try {
                convertValue = new ObjectName(value);
            } catch (MalformedObjectNameException e) {
                if (isEcho()) {
                    handleErrorOutput("Unable to convert to ObjectName:" + value);
                }
            }
        } else if ("java.net.InetAddress".equals(valueType)) {
            try {
                convertValue = InetAddress.getByName(value);
            } catch (UnknownHostException exc) {
                if (isEcho()) {
                    handleErrorOutput("Unable to resolve host name:" + value);
                }
            }
        }
        return convertValue;
    }

    /**
     * @param name   context of result
     * @param result The result
     */
    protected void echoResult(String name, Object result) {
        if (isEcho()) {
            if (result.getClass().isArray()) {
                for (int i = 0; i < Array.getLength(result); i++) {
                    handleOutput(name + "." + i + "=" + Array.get(result, i));
                }
            } else {
                handleOutput(name + "=" + result);
            }
        }
    }

    /**
     * create result as property with name from attribute resultproperty
     *
     * @param result The result
     *
     * @see #createProperty(String, Object)
     */
    protected void createProperty(Object result) {
        if (resultproperty != null) {
            createProperty(resultproperty, result);
        }
    }

    /**
     * create result as property with name from property prefix When result is an array and isSeparateArrayResults is
     * true, resultproperty used as prefix (<code>resultproperty.0-array.length</code> and store the result array length
     * at <code>resultproperty.length</code>. Other option is that you delimit your result with a delimiter
     * (java.util.StringTokenizer is used).
     *
     * @param propertyPrefix Prefix for the property
     * @param result         The result
     */
    protected void createProperty(String propertyPrefix, Object result) {
        if (propertyPrefix == null) {
            propertyPrefix = "";
        }
        if (result instanceof CompositeDataSupport data) {
            CompositeType compositeType = data.getCompositeType();
            Set<String> keys = compositeType.keySet();
            for (String key : keys) {
                Object value = data.get(key);
                OpenType<?> type = compositeType.getType(key);
                if (type instanceof SimpleType<?>) {
                    setProperty(propertyPrefix + "." + key, value);
                } else {
                    createProperty(propertyPrefix + "." + key, value);
                }
            }
        } else if (result instanceof TabularDataSupport data) {
            for (Object key : data.keySet()) {
                for (Object key1 : ((List<?>) key)) {
                    CompositeData valuedata = data.get(new Object[] { key1 });
                    Object value = valuedata.get("value");
                    OpenType<?> type = valuedata.getCompositeType().getType("value");
                    if (type instanceof SimpleType<?>) {
                        setProperty(propertyPrefix + "." + key1, value);
                    } else {
                        createProperty(propertyPrefix + "." + key1, value);
                    }
                }
            }
        } else if (result.getClass().isArray()) {
            if (isSeparatearrayresults()) {
                int size = 0;
                for (int i = 0; i < Array.getLength(result); i++) {
                    if (setProperty(propertyPrefix + "." + size, Array.get(result, i))) {
                        size++;
                    }
                }
                if (size > 0) {
                    setProperty(propertyPrefix + ".Length", Integer.toString(size));
                }
            }
        } else {
            String delim = getDelimiter();
            if (delim != null) {
                StringTokenizer tokenizer = new StringTokenizer(result.toString(), delim);
                int size = 0;
                while (tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken();
                    if (setProperty(propertyPrefix + "." + size, token)) {
                        size++;
                    }
                }
                if (size > 0) {
                    setProperty(propertyPrefix + ".Length", Integer.toString(size));
                }
            } else {
                setProperty(propertyPrefix, result.toString());
            }
        }
    }

    /**
     * Get Property
     *
     * @param property name
     *
     * @return The property value
     */
    public String getProperty(String property) {
        Project currentProject = getProject();
        if (currentProject != null) {
            return currentProject.getProperty(property);
        } else {
            return properties.getProperty(property);
        }
    }

    /**
     * @param property The property
     * @param value    The value
     *
     * @return True if successful
     */
    public boolean setProperty(String property, Object value) {
        if (property != null) {
            if (value == null) {
                value = "";
            }
            if (isEcho()) {
                handleOutput(property + "=" + value.toString());
            }
            Project currentProject = getProject();
            if (currentProject != null) {
                currentProject.setNewProperty(property, value.toString());
            } else {
                properties.setProperty(property, value.toString());
            }
            return true;
        }
        return false;
    }
}
