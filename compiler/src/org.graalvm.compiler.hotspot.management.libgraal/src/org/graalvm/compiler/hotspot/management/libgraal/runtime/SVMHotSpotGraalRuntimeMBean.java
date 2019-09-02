/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.hotspot.management.libgraal.runtime;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.HotSpotGraalManagementRegistration;
import org.graalvm.compiler.hotspot.management.HotSpotGraalRuntimeMBean;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.LibGraalScope;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.util.OptionsEncoder;

/**
 * Encapsulates a handle to a {@link HotSpotGraalRuntimeMBean} object in the SVM heap. Implements
 * the {@link DynamicMBean} by delegating all operations to an object in the SVM heap.
 */
@Platforms(Platform.HOSTED_ONLY.class)
class SVMHotSpotGraalRuntimeMBean implements DynamicMBean {

    private static volatile Factory factory;

    private final long handle;

    SVMHotSpotGraalRuntimeMBean(long handle) {
        this.handle = handle;
    }

    /**
     * Obtain the value of a specific attribute of the Dynamic MBean by delegating to
     * {@link HotSpotGraalRuntimeMBean} instance in the SVM heap.
     *
     * @param attribute the name of the attribute to be retrieved
     */
    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        AttributeList attributes = getAttributes(new String[]{attribute});
        return ((Attribute) attributes.get(0)).getValue();
    }

    /**
     * Set the value of a specific attribute of the Dynamic MBean by delegating to
     * {@link HotSpotGraalRuntimeMBean} instance in the SVM heap.
     *
     * @param attribute the identification of the attribute to be set and the value it is to be set
     *            to
     */
    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        AttributeList list = new AttributeList();
        list.add(attribute);
        setAttributes(list);
    }

    /**
     * Get the values of several attributes of the Dynamic MBean by delegating to
     * {@link HotSpotGraalRuntimeMBean} instance in the SVM heap.
     *
     * @param attributes a list of the attributes to be retrieved
     */
    @Override
    @SuppressWarnings("try")
    public AttributeList getAttributes(String[] attributes) {
        try (LibGraalScope scope = new LibGraalScope(HotSpotJVMCIRuntime.runtime())) {
            byte[] rawData = HotSpotToSVMCalls.getAttributes(LibGraalScope.getIsolateThread(), handle, attributes);
            return rawToAttributeList(rawData);
        }
    }

    /**
     * Sets the values of several attributes of the Dynamic MBean by delegating to
     * {@link HotSpotGraalRuntimeMBean} instance in the SVM heap.
     *
     * @param attributes a list of attributes: The identification of the attributes to be set and
     *            the values they are to be set to.
     */
    @Override
    @SuppressWarnings("try")
    public AttributeList setAttributes(AttributeList attributes) {
        try (LibGraalScope scope = new LibGraalScope(HotSpotJVMCIRuntime.runtime())) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Object item : attributes) {
                Attribute attribute = (Attribute) item;
                map.put(attribute.getName(), attribute.getValue());
            }
            byte[] rawData = OptionsEncoder.encode(map);
            rawData = HotSpotToSVMCalls.setAttributes(LibGraalScope.getIsolateThread(), handle, rawData);
            return rawToAttributeList(rawData);
        }
    }

    /**
     * Decodes an {@link AttributeList} encoded to {@code byte} array using {@link OptionsEncoder}.
     *
     * @param rawData the encoded attribute list.
     */
    private static AttributeList rawToAttributeList(byte[] rawData) {
        AttributeList res = new AttributeList();
        Map<String, Object> map = OptionsEncoder.decode(rawData);
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String attrName = e.getKey();
            Object attrValue = e.getValue();
            res.add(new Attribute(attrName, attrValue));
        }
        return res;
    }

    /**
     * Invokes an action on the Dynamic MBean by delegating to {@link HotSpotGraalRuntimeMBean}
     * instance in the SVM heap.
     *
     * @param actionName the name of the action to be invoked
     * @param params an array containing the parameters to be set when the action is invoked
     * @param signature an array containing the signature of the action. The class objects will be
     *            loaded through the same class loader as the one used for loading the MBean on
     *            which the action is invoked
     *
     * @return The object returned by the action, which represents the result of invoking the action
     *         on the MBean specified.
     *
     */
    @Override
    @SuppressWarnings("try")
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        try (LibGraalScope scope = new LibGraalScope(HotSpotJVMCIRuntime.runtime())) {
            Map<String, Object> paramsMap = new LinkedHashMap<>();
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    paramsMap.put(Integer.toString(i), params[i]);
                }
            }
            byte[] rawData = OptionsEncoder.encode(paramsMap);
            rawData = HotSpotToSVMCalls.invoke(LibGraalScope.getIsolateThread(), handle, actionName, rawData, signature);
            if (rawData == null) {
                throw new MBeanException(null);
            }
            AttributeList attributesList = rawToAttributeList(rawData);
            return attributesList.isEmpty() ? null : ((Attribute) attributesList.get(0)).getValue();
        }
    }

    /**
     * Provides the attributes and actions of the Dynamic MBean by delegating to
     * {@link HotSpotGraalRuntimeMBean} instance in the SVM heap.
     *
     */
    @Override
    @SuppressWarnings("try")
    public MBeanInfo getMBeanInfo() {
        try (LibGraalScope scope = new LibGraalScope(HotSpotJVMCIRuntime.runtime())) {
            byte[] rawData = HotSpotToSVMCalls.getMBeanInfo(LibGraalScope.getIsolateThread(), handle);
            Map<String, Object> map = OptionsEncoder.decode(rawData);
            String className = null;
            String description = null;
            List<MBeanAttributeInfo> attributes = new ArrayList<>();
            List<MBeanOperationInfo> operations = new ArrayList<>();
            for (PushBackIterator<Map.Entry<String, Object>> it = new PushBackIterator<>(map.entrySet().iterator()); it.hasNext();) {
                Map.Entry<String, ?> entry = it.next();
                String key = entry.getKey();
                if (key.equals("bean.class")) {
                    className = (String) entry.getValue();
                } else if (key.equals("bean.description")) {
                    description = (String) entry.getValue();
                } else if (key.startsWith("attr.")) {
                    String attrName = (String) entry.getValue();
                    if (!key.equals("attr." + attrName + ".name")) {
                        throw new IllegalStateException("Invalid order of attribute properties");
                    }
                    MBeanAttributeInfo attr = createAttributeInfo(attrName, it);
                    attributes.add(attr);
                } else if (key.startsWith("op.")) {
                    int opId = (Integer) entry.getValue();
                    if (!key.equals("op." + opId + ".id")) {
                        throw new IllegalStateException("Invalid order of operation properties");
                    }
                    MBeanOperationInfo op = createOperationInfo(opId, it);
                    operations.add(op);
                }
            }
            Objects.requireNonNull(className, "ClassName must be non null.");
            Objects.requireNonNull(description, "Description must be non null.");
            return new MBeanInfo(className, description,
                            attributes.toArray(new MBeanAttributeInfo[attributes.size()]), null,
                            operations.toArray(new MBeanOperationInfo[operations.size()]), null);
        }
    }

    /**
     * Returns a factory thread registering the {@link SVMHotSpotGraalRuntimeMBean} instances into
     * {@link MBeanServer}. If the factory thread does not exist it's created and started.
     *
     * @return the started factory thread instance.
     */
    static Factory getFactory() {
        Factory res = factory;
        if (res == null) {
            synchronized (SVMHotSpotGraalRuntimeMBean.class) {
                res = factory;
                if (res == null) {
                    res = new Factory();
                    res.start();
                    factory = res;
                }
            }
        }
        return res;
    }

    /**
     * Parses {@link MBeanAttributeInfo} from iterator of MBean properties.
     *
     * @param attrName the current attribute name
     * @param it the attribute properties {@link Iterator}
     * @return the parsed {@link MBeanAttributeInfo}
     */
    private static MBeanAttributeInfo createAttributeInfo(String attrName, PushBackIterator<Map.Entry<String, Object>> it) {
        String attrType = null;
        String attrDescription = null;
        boolean isReadable = false;
        boolean isWritable = false;
        boolean isIs = false;
        String prefix = "attr." + attrName + ".";
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            String key = entry.getKey();
            if (!key.startsWith(prefix)) {
                it.pushBack(entry);
                break;
            }
            String propertyName = key.substring(key.lastIndexOf('.') + 1);
            switch (propertyName) {
                case "type":
                    attrType = (String) entry.getValue();
                    break;
                case "description":
                    attrDescription = (String) entry.getValue();
                    break;
                case "r":
                    isReadable = (Boolean) entry.getValue();
                    break;
                case "w":
                    isWritable = (Boolean) entry.getValue();
                    break;
                case "i":
                    isIs = (Boolean) entry.getValue();
                    break;
                default:
                    throw new IllegalStateException("Unkown attribute property: " + propertyName);
            }
        }
        if (attrType == null) {
            throw new IllegalStateException("Attribute type must be given.");
        }
        return new MBeanAttributeInfo(attrName, attrType, attrDescription, isReadable, isWritable, isIs);
    }

    /**
     * Parses {@link MBeanOperationInfo} from iterator of MBean properties.
     *
     * @param opId unique id of an operation. Each operation has an unique id as operation name is
     *            not an unique identifier due to overloads.
     * @param it the attribute properties {@link Iterator}
     * @return the parsed {@link MBeanOperationInfo}
     */
    private static MBeanOperationInfo createOperationInfo(int opId, PushBackIterator<Map.Entry<String, Object>> it) {
        String opName = null;
        String opType = null;
        String opDescription = null;
        int opImpact = 0;
        List<MBeanParameterInfo> params = new ArrayList<>();
        String prefix = "op." + opId + ".";
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            String key = entry.getKey();
            if (!key.startsWith(prefix)) {
                it.pushBack(entry);
                break;
            }
            int nextDotIndex = key.indexOf('.', prefix.length());
            nextDotIndex = nextDotIndex < 0 ? key.length() : nextDotIndex;
            String propertyName = key.substring(prefix.length(), nextDotIndex);
            switch (propertyName) {
                case "name":
                    opName = (String) entry.getValue();
                    break;
                case "type":
                    opType = (String) entry.getValue();
                    break;
                case "description":
                    opDescription = (String) entry.getValue();
                    break;
                case "i":
                    opImpact = (Integer) entry.getValue();
                    break;
                case "arg":
                    String paramName = (String) entry.getValue();
                    if (!key.equals(prefix + "arg." + paramName + ".name")) {
                        throw new IllegalStateException("Invalid order of parameter properties");
                    }
                    MBeanParameterInfo param = createParameterInfo(prefix, paramName, it);
                    params.add(param);
                    break;
                default:
                    throw new IllegalStateException("Unkown attribute property: " + propertyName);
            }
        }
        if (opName == null) {
            throw new IllegalStateException("Operation name must be given.");
        }
        if (opType == null) {
            throw new IllegalStateException("Operation return type must be given.");
        }
        return new MBeanOperationInfo(opName, opDescription,
                        params.toArray(new MBeanParameterInfo[params.size()]),
                        opType, opImpact);
    }

    /**
     * Parses {@link MBeanAttributeInfo} from iterator of MBean properties.
     *
     * @param owner the operation attribute scope
     * @param paramName the name of the parameter
     * @param it the attribute properties {@link Iterator}
     * @return the parsed {@link MBeanParameterInfo}
     */
    private static MBeanParameterInfo createParameterInfo(String owner, String paramName, PushBackIterator<Map.Entry<String, Object>> it) {
        String paramType = null;
        String paramDescription = null;
        String prefix = owner + "arg." + paramName + ".";
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            String key = entry.getKey();
            if (!key.startsWith(prefix)) {
                it.pushBack(entry);
                break;
            }
            String propertyName = key.substring(key.lastIndexOf('.') + 1);
            switch (propertyName) {
                case "type":
                    paramType = (String) entry.getValue();
                    break;
                case "description":
                    paramDescription = (String) entry.getValue();
                    break;
                default:
                    throw new IllegalStateException("Unkown parameter property: " + propertyName);
            }
        }
        if (paramType == null) {
            throw new IllegalStateException("Parameter type must be given.");
        }
        return new MBeanParameterInfo(paramName, paramType, paramDescription);
    }

    /**
     * An iterator allowing pushing back a single look ahead item.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    private static final class PushBackIterator<T> implements Iterator<T> {

        private final Iterator<T> delegate;
        private T pushBack;

        PushBackIterator(Iterator<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return pushBack != null || delegate.hasNext();
        }

        @Override
        public T next() {
            if (pushBack != null) {
                T res = pushBack;
                pushBack = null;
                return res;
            } else {
                return delegate.next();
            }
        }

        void pushBack(T e) {
            if (pushBack != null) {
                throw new IllegalStateException("Push back element already exists.");
            }
            pushBack = e;
        }
    }

    /**
     * A factory thread creating the {@link SVMHotSpotGraalRuntimeMBean} instances for
     * {@link HotSpotGraalRuntimeMBean}s in SVM heap and registering them to {@link MBeanServer}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    static final class Factory extends Thread {

        private static final int POLL_INTERVAL_MS = 2000;

        private MBeanServer platformMBeanServer;
        private boolean dirty;

        private Factory() {
            super("HotSpotGraalManagement Bean Registration");
            this.setPriority(Thread.MIN_PRIORITY);
            this.setDaemon(true);
            LibGraal.registerNativeMethods(runtime(), HotSpotToSVMCalls.class);
        }

        /**
         * Main loop waiting for {@link HotSpotGraalRuntimeMBean} creation in SVM heap. When a new
         * {@link HotSpotGraalRuntimeMBean} is created in the SVM heap this thread creates a new
         * {@link SVMHotSpotGraalRuntimeMBean} encapsulation the {@link HotSpotGraalRuntimeMBean}
         * and registers it to {@link MBeanServer}.
         */
        @Override
        public void run() {
            while (true) {
                try {
                    synchronized (this) {
                        // Wait until there are deferred registrations to process
                        while (!dirty) {
                            wait();
                        }
                        try {
                            dirty = !poll();
                        } catch (SecurityException | UnsatisfiedLinkError | NoClassDefFoundError | UnsupportedOperationException e) {
                            // Without permission to find or create the MBeanServer,
                            // we cannot process any Graal mbeans.
                            // Various other errors can occur in the ManagementFactory (JDK-8076557)
                            break;
                        }
                    }
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    // Be verbose about unexpected interruption and then continue
                    e.printStackTrace(TTY.out);
                }
            }
        }

        /**
         * Called by {@link HotSpotGraalManagementRegistration} in SVM heap to notify the factory
         * thread about new {@link HotSpotGraalRuntimeMBean}s.
         */
        synchronized void signal() {
            dirty = true;
            notify();
        }

        /**
         * In case of successful {@link MBeanServer} initialization creates
         * {@link SVMHotSpotGraalRuntimeMBean} for pending {@link HotSpotGraalRuntimeMBean}s and
         * registers them.
         *
         * @return {@code true} if {@link SVMHotSpotGraalRuntimeMBean}s were successfuly registered,
         *         {@code false} when {@link MBeanServer} is not yet available and {@code poll}
         *         should be retried.
         * @throws SecurityException can be thrown by {@link MBeanServer}
         * @throws UnsatisfiedLinkError can be thrown by {@link MBeanServer}
         * @throws NoClassDefFoundError can be thrown by {@link MBeanServer}
         * @throws UnsupportedOperationException can be thrown by {@link MBeanServer}
         */
        private boolean poll() {
            assert Thread.holdsLock(this);
            if (platformMBeanServer == null) {
                ArrayList<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
                if (!servers.isEmpty()) {
                    platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
                    return process();
                }
            } else {
                return process();
            }
            return false;
        }

        /**
         * Creates {@link SVMHotSpotGraalRuntimeMBean} for pending {@link HotSpotGraalRuntimeMBean}s
         * and registers them {@link MBeanServer}.
         *
         * @return {@code true}
         * @throws SecurityException can be thrown by {@link MBeanServer}
         * @throws UnsatisfiedLinkError can be thrown by {@link MBeanServer}
         * @throws NoClassDefFoundError can be thrown by {@link MBeanServer}
         * @throws UnsupportedOperationException can be thrown by {@link MBeanServer}
         */
        @SuppressWarnings("try")
        private boolean process() {
            try (LibGraalScope scope = new LibGraalScope(HotSpotJVMCIRuntime.runtime())) {
                long[] svmRegistrations = HotSpotToSVMCalls.pollRegistrations(LibGraalScope.getIsolateThread());
                if (svmRegistrations.length > 0) {
                    for (long svmRegistration : svmRegistrations) {
                        try {
                            SVMHotSpotGraalRuntimeMBean bean = new SVMHotSpotGraalRuntimeMBean(svmRegistration);
                            String name = HotSpotToSVMCalls.getRegistrationName(LibGraalScope.getIsolateThread(), svmRegistration);
                            platformMBeanServer.registerMBean(bean, new ObjectName("org.graalvm.compiler.hotspot:type=" + name));
                        } catch (MalformedObjectNameException | InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException e) {
                            e.printStackTrace(TTY.out);
                        }
                    }
                    HotSpotToSVMCalls.finishRegistration(LibGraalScope.getIsolateThread(), svmRegistrations);
                }
            }
            return true;
        }
    }
}
