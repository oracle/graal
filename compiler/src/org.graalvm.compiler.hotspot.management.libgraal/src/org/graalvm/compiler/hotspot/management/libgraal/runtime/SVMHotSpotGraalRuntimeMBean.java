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
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import org.graalvm.libgraal.LibGraalScope;
import org.graalvm.libgraal.OptionsEncoder;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@Platforms(Platform.HOSTED_ONLY.class)
public class SVMHotSpotGraalRuntimeMBean implements DynamicMBean {

    private final long handle;

    SVMHotSpotGraalRuntimeMBean(long handle) {
        this.handle = handle;
    }

    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        AttributeList attributes = getAttributes(new String[]{attribute});
        return ((Attribute) attributes.get(0)).getValue();
    }

    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        AttributeList list = new AttributeList();
        list.add(attribute);
        setAttributes(list);
    }

    @Override
    @SuppressWarnings("try")
    public AttributeList getAttributes(String[] attributes) {
        try (LibGraalScope scope = new LibGraalScope(HotSpotJVMCIRuntime.runtime())) {
            byte[] rawData = HotSpotToSVMCalls.getAttributes(LibGraalScope.getIsolateThread(), handle, attributes);
            return rawToAttributeList(rawData);
        }
    }

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

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        return null;
    }

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
            for (PushBackIterator<Map.Entry<String, Object>> it = new PushBackIterator<Map.Entry<String, Object>>(map.entrySet().iterator()); it.hasNext();) {
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
                }
            }
            Objects.requireNonNull(className, "ClassName must be non null.");
            Objects.requireNonNull(description, "Description must be non null.");
            return new MBeanInfo(className, description,
                            attributes.toArray(new MBeanAttributeInfo[attributes.size()]), null,
                            operations.toArray(new MBeanOperationInfo[operations.size()]), null);
        }
    }

    private static MBeanAttributeInfo createAttributeInfo(String attrName, PushBackIterator<Map.Entry<String, Object>> it) {
        String attrType = null;
        String attrDescription = null;
        boolean isReadable = false;
        boolean isWritable = false;
        boolean isIs = false;
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            String key = entry.getKey();
            if (!key.startsWith("attr." + attrName + ".")) {
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
}

final class PushBackIterator<T> implements Iterator<T> {

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
