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


import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;


/**
 * Access <em>JMX</em> JSR 160 MBeans Server.
 * <ul>
 * <li>Get Mbeans attributes</li>
 * <li>Show Get result as Ant console log</li>
 * <li>Bind Get result as Ant properties</li>
 * </ul>
 * <p>
 * Examples: Set a Mbean Manager attribute maxActiveSessions. Set this attribute with fresh jmx connection without save
 * reference
 * </p>
 *
 * <pre>
 *   &lt;jmx:set
 *           host="127.0.0.1"
 *           port="9014"
 *           ref=""
 *           name="Catalina:type=Manager,context="/ClusterTest",host=localhost"
 *           attribute="maxActiveSessions"
 *           value="100"
 *           type="int"
 *           echo="false"&gt;
 *       /&gt;
 * </pre>
 * <p>
 * First call to a remote MBean server save the JMXConnection a reference <em>jmx.server</em>
 * </p>
 * These tasks require Ant 1.6 or later interface.
 *
 * @author Peter Rossbach
 *
 * @since 5.5.10
 */
public class JMXAccessorSetTask extends JMXAccessorTask {

    // ----------------------------------------------------- Instance Variables

    private String attribute;
    private String value;
    private String type;
    private boolean convert = false;

    // ------------------------------------------------------------- Properties

    /**
     * @return Returns the attribute.
     */
    public String getAttribute() {
        return attribute;
    }

    /**
     * @param attribute The attribute to set.
     */
    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    /**
     * @return Returns the value.
     */
    public String getValue() {
        return value;
    }

    /**
     * @param value The value to set.
     */
    public void setValue(String value) {
        this.value = value;
    }


    /**
     * @return Returns the type.
     */
    public String getType() {
        return type;
    }

    /**
     * @param valueType The type to set.
     */
    public void setType(String valueType) {
        this.type = valueType;
    }


    /**
     * @return Returns the convert.
     */
    public boolean isConvert() {
        return convert;
    }

    /**
     * @param convert The convert to set.
     */
    public void setConvert(boolean convert) {
        this.convert = convert;
    }
    // ------------------------------------------------------ protected Methods

    @Override
    public String jmxExecute(MBeanServerConnection jmxServerConnection) throws Exception {

        if (getName() == null) {
            throw new BuildException("Must specify a 'name'");
        }
        if ((attribute == null || value == null)) {
            throw new BuildException("Must specify a 'attribute' and 'value' for set");
        }
        return jmxSet(jmxServerConnection, getName());
    }

    /**
     * Set property value.
     *
     * @param jmxServerConnection Connection to the JMX server
     * @param name                The MBean name
     *
     * @return null (no error message to report other than exception)
     *
     * @throws Exception An error occurred
     */
    protected String jmxSet(MBeanServerConnection jmxServerConnection, String name) throws Exception {
        Object realValue;
        if (type != null) {
            realValue = convertStringToType(value, type);
        } else {
            if (isConvert()) {
                String mType = getMBeanAttributeType(jmxServerConnection, name, attribute);
                realValue = convertStringToType(value, mType);
            } else {
                realValue = value;
            }
        }
        jmxServerConnection.setAttribute(new ObjectName(name), new Attribute(attribute, realValue));
        return null;
    }


    /**
     * Get MBean Attribute from Mbean Server
     *
     * @param jmxServerConnection The JMX connection name
     * @param name                The MBean name
     * @param attribute           The attribute name
     *
     * @return The type of the attribute
     *
     * @throws Exception An error occurred
     */
    protected String getMBeanAttributeType(MBeanServerConnection jmxServerConnection, String name, String attribute)
            throws Exception {
        ObjectName oname = new ObjectName(name);
        String mattrType = null;
        MBeanInfo minfo = jmxServerConnection.getMBeanInfo(oname);
        MBeanAttributeInfo[] attrs = minfo.getAttributes();
        for (int i = 0; mattrType == null && i < attrs.length; i++) {
            if (attribute.equals(attrs[i].getName())) {
                mattrType = attrs[i].getType();
            }
        }
        return mattrType;
    }
}
