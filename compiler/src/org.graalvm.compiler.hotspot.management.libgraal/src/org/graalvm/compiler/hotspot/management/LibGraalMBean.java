/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.management;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ReflectionException;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

import org.graalvm.compiler.debug.TTY;
import org.graalvm.libgraal.LibGraalScope;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.util.OptionsEncoder;

/**
 * Encapsulates a handle to a {@link DynamicMBean} object in the libgraal heap. Implements the
 * {@link DynamicMBean} by delegating all operations to an object in the libgraal heap.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class LibGraalMBean implements DynamicMBean {

    private static final String COMPOSITE_TAG = ".composite";
    private static final String ARRAY_TAG = ".array";
    private static final Map<Class<?>, OpenType<?>> PRIMITIVE_TO_OPENTYPE;
    static {
        PRIMITIVE_TO_OPENTYPE = new HashMap<>();
        PRIMITIVE_TO_OPENTYPE.put(Void.class, SimpleType.VOID);
        PRIMITIVE_TO_OPENTYPE.put(Boolean.class, SimpleType.BOOLEAN);
        PRIMITIVE_TO_OPENTYPE.put(Byte.class, SimpleType.BYTE);
        PRIMITIVE_TO_OPENTYPE.put(Character.class, SimpleType.CHARACTER);
        PRIMITIVE_TO_OPENTYPE.put(Short.class, SimpleType.SHORT);
        PRIMITIVE_TO_OPENTYPE.put(Integer.class, SimpleType.INTEGER);
        PRIMITIVE_TO_OPENTYPE.put(Float.class, SimpleType.FLOAT);
        PRIMITIVE_TO_OPENTYPE.put(Long.class, SimpleType.LONG);
        PRIMITIVE_TO_OPENTYPE.put(Double.class, SimpleType.DOUBLE);
        PRIMITIVE_TO_OPENTYPE.put(String.class, SimpleType.STRING);
    }

    private final long isolate;
    private final long handle;

    LibGraalMBean(long isolate, long handle) {
        this.isolate = isolate;
        this.handle = handle;
    }

    /**
     * Obtain the value of a specific attribute of the Dynamic MBean by delegating to
     * {@link DynamicMBean} instance in the libgraal heap.
     *
     * @param attribute the name of the attribute to be retrieved
     */
    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        AttributeList attributes = getAttributes(new String[]{attribute});
        return attributes.isEmpty() ? null : ((Attribute) attributes.get(0)).getValue();
    }

    /**
     * Set the value of a specific attribute of the Dynamic MBean by delegating to
     * {@link DynamicMBean} instance in the libgraal heap.
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
     * {@link DynamicMBean} instance in the libgraal heap.
     *
     * @param attributes a list of the attributes to be retrieved
     */
    @Override
    public AttributeList getAttributes(String[] attributes) {
        try (LibGraalScope scope = new LibGraalScope(isolate)) {
            byte[] rawData = JMXToLibGraalCalls.getAttributes(scope.getIsolateThreadAddress(), handle, attributes);
            return rawToAttributeList(rawData);
        }
    }

    /**
     * Sets the values of several attributes of the Dynamic MBean by delegating to
     * {@link DynamicMBean} instance in the libgraal heap.
     *
     * @param attributes a list of attributes: The identification of the attributes to be set and
     *            the values they are to be set to.
     */
    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        try (LibGraalScope scope = new LibGraalScope(isolate)) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Object item : attributes) {
                Attribute attribute = (Attribute) item;
                map.put(attribute.getName(), attribute.getValue());
            }
            byte[] rawData = OptionsEncoder.encode(map);
            rawData = JMXToLibGraalCalls.setAttributes(scope.getIsolateThreadAddress(), handle, rawData);
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
        for (PushBackIterator<Map.Entry<String, Object>> it = new PushBackIterator<>(map.entrySet().iterator()); it.hasNext();) {
            Map.Entry<String, Object> e = it.next();
            String attrName = e.getKey();
            Object attrValue = e.getValue();
            try {
                if (isComposite(attrName)) {
                    attrValue = readComposite(attrName, (String) attrValue, it);
                    attrName = attrName(attrName, COMPOSITE_TAG);
                } else if (isArray(attrName)) {
                    attrValue = readArray(attrName, (String) attrValue, it);
                    attrName = attrName(attrName, ARRAY_TAG);
                }
            } catch (OpenDataException ex) {
                attrValue = null;
                TTY.printf("WARNING: Cannot read attribute %s due to %s", attrName, ex.getMessage());
            }
            res.add(new Attribute(attrName, attrValue));
        }
        return res;
    }

    /**
     * Check if the serialized attribute is a {@link CompositeData}.
     */
    private static boolean isComposite(String name) {
        return name.endsWith(COMPOSITE_TAG);
    }

    /**
     * Removes the tag from attribute name.
     */
    private static String attrName(String name, String tag) {
        return name.substring(0, name.length() - tag.length());
    }

    /**
     * Check if the serialized attribute is an array.
     */
    private static boolean isArray(String name) {
        return name.endsWith(ARRAY_TAG);
    }

    /**
     * Deserializes the {@link CompositeData}.
     */
    private static CompositeData readComposite(String scope, String typeName, PushBackIterator<Map.Entry<String, Object>> it) throws OpenDataException {
        String prefix = scope + '.';
        List<String> attrNames = new ArrayList<>();
        List<Object> attrValues = new ArrayList<>();
        List<OpenType<?>> attrTypes = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<String, Object> e = it.next();
            String attrName = e.getKey();
            if (!attrName.startsWith(prefix)) {
                it.pushBack(e);
                break;
            }
            Object attrValue = e.getValue();
            if (isComposite(attrName)) {
                attrValue = readComposite(attrName, (String) attrValue, it);
                attrName = attrName(attrName, COMPOSITE_TAG);
            } else if (isArray(attrName)) {
                attrValue = readArray(attrName, (String) attrValue, it);
                attrName = attrName(attrName, ARRAY_TAG);
            }
            attrName = attrName.substring(prefix.length());
            attrNames.add(attrName);
            attrValues.add(attrValue);
            attrTypes.add(getOpenType(attrValue));
        }
        String[] attrNamesArray = attrNames.toArray(new String[attrNames.size()]);
        CompositeType type = new CompositeType(typeName, typeName, attrNamesArray, attrNamesArray, attrTypes.toArray(new OpenType<?>[attrTypes.size()]));
        return new CompositeDataSupport(type, attrNamesArray, attrValues.toArray(new Object[attrValues.size()]));
    }

    private static Object[] readArray(String scope, String typeName, PushBackIterator<Map.Entry<String, Object>> it) throws OpenDataException {
        String prefix = scope + '.';
        List<Object> elements = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<String, Object> e = it.next();
            String attrName = e.getKey();
            if (!attrName.startsWith(prefix)) {
                it.pushBack(e);
                break;
            }
            Object attrValue = e.getValue();
            elements.add(attrValue);
        }
        Class<?> componentType = null;
        for (Map.Entry<Class<?>, OpenType<?>> e : PRIMITIVE_TO_OPENTYPE.entrySet()) {
            if (e.getValue().getTypeName().equals(typeName)) {
                componentType = e.getKey();
                break;
            }
        }
        if (componentType == null) {
            throw new OpenDataException("Only arrays of simple open types are suppored.");
        }
        return elements.toArray((Object[]) Array.newInstance(componentType, elements.size()));
    }

    /**
     * Returns an {@link OpenType} representing given value.
     */
    private static OpenType<?> getOpenType(Object value) throws OpenDataException {
        if (value instanceof CompositeData) {
            return ((CompositeData) value).getCompositeType();
        }
        if (value != null && value.getClass().isArray()) {
            return ArrayType.getArrayType(PRIMITIVE_TO_OPENTYPE.get(value.getClass().getComponentType()));
        }
        Class<?> clz = value == null ? String.class : value.getClass();
        OpenType<?> openType = PRIMITIVE_TO_OPENTYPE.get(clz);
        if (openType == null) {
            throw new IllegalArgumentException(clz.getName());
        }
        return openType;
    }

    /**
     * Invokes an action on the Dynamic MBean by delegating to {@link DynamicMBean} instance in the
     * libgraal heap.
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
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        try (LibGraalScope scope = new LibGraalScope(isolate)) {
            Map<String, Object> paramsMap = new LinkedHashMap<>();
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    paramsMap.put(Integer.toString(i), params[i]);
                }
            }
            byte[] rawData = OptionsEncoder.encode(paramsMap);
            rawData = JMXToLibGraalCalls.invoke(scope.getIsolateThreadAddress(), handle, actionName, rawData, signature);
            if (rawData == null) {
                throw new MBeanException(null);
            }
            AttributeList attributesList = rawToAttributeList(rawData);
            return attributesList.isEmpty() ? null : ((Attribute) attributesList.get(0)).getValue();
        }
    }

    /**
     * Provides the attributes and actions of the Dynamic MBean by delegating to
     * {@link DynamicMBean} instance in the libgraal heap.
     *
     */
    @Override
    public MBeanInfo getMBeanInfo() {
        try (LibGraalScope scope = new LibGraalScope(isolate)) {
            byte[] rawData = JMXToLibGraalCalls.getMBeanInfo(scope.getIsolateThreadAddress(), handle);
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
    public static final class PushBackIterator<T> implements Iterator<T> {

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
}
