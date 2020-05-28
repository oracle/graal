/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.management.libgraal;

import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

final class LibGraalMemoryPoolMBean implements DynamicMBean {

    static final String NAME = "java.lang:type=MemoryPool,name=Libgraal";

    private static final String ATTR_TYPE = "Type";
    private static final String ATTR_VALID = "Valid";
    private static final String ATTR_USAGE = "Usage";
    private static final String ATTR_PEAK_USAGE = "PeakUsage";
    private static final String ATTR_COLLECTION_USAGE = "CollectionUsage";
    private static final String ATTR_COLLECTION_USAGE_THRESHOLD_SUPPORTED = "CollectionUsageThresholdSupported";
    private static final String ATTR_COLLECTION_USAGE_THRESHOLD = "CollectionUsageThreshold";
    private static final String ATTR_COLLECTION_USAGE_THRESHOLD_COUNT = "CollectionUsageThresholdCount";
    private static final String ATTR_COLLECTION_USAGE_THRESHOLD_EXCEEDED = "CollectionUsageThresholdExceeded";
    private static final String ATTR_USAGE_THRESHOLD_SUPPORTED = "UsageThresholdSupported";
    private static final String ATTR_USAGE_THRESHOLD = "UsageThreshold";
    private static final String ATTR_USAGE_THRESHOLD_COUNT = "UsageThresholdCount";
    private static final String ATTR_USAGE_THRESHOLD_EXCEEDED = "UsageThresholdExceeded";

    private static final String ATTR_COMMITTED = "committed";
    private static final String ATTR_INIT = "init";
    private static final String ATTR_MAX = "max";
    private static final String ATTR_USED = "used";
    private static final String[] USAGE_TYPE_ATTRS = {ATTR_COMMITTED, ATTR_INIT, ATTR_MAX, ATTR_USED};
    private static final CompositeType USAGE_TYPE;
    static {
        try {
            OpenType<?>[] types = {SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG};
            USAGE_TYPE = new CompositeType(MemoryUsage.class.getName(), "Memory Usage", USAGE_TYPE_ATTRS, USAGE_TYPE_ATTRS, types);
        } catch (OpenDataException e) {
            // Should never happen
            throw new AssertionError("Cannot create memory usage type.", e);
        }
    }

    private final Runtime rt;
    private volatile long peekTotalMemory;
    private volatile long peekUsedMemory;

    LibGraalMemoryPoolMBean() {
        rt = Runtime.getRuntime();
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        List<MBeanAttributeInfo> attrs = new ArrayList<>();
        attrs.add(createAttributeInfo(ATTR_TYPE, String.class));
        attrs.add(createAttributeInfo(ATTR_VALID, Boolean.class));
        attrs.add(createAttributeInfo(ATTR_USAGE, CompositeData.class));
        attrs.add(createAttributeInfo(ATTR_PEAK_USAGE, CompositeData.class));
        attrs.add(createAttributeInfo(ATTR_COLLECTION_USAGE, CompositeData.class));
        attrs.add(createAttributeInfo(ATTR_COLLECTION_USAGE_THRESHOLD_SUPPORTED, Boolean.class));
        attrs.add(createAttributeInfo(ATTR_COLLECTION_USAGE_THRESHOLD, Long.class));
        attrs.add(createAttributeInfo(ATTR_COLLECTION_USAGE_THRESHOLD_COUNT, Long.class));
        attrs.add(createAttributeInfo(ATTR_COLLECTION_USAGE_THRESHOLD_EXCEEDED, Boolean.class));
        attrs.add(createAttributeInfo(ATTR_USAGE_THRESHOLD_SUPPORTED, Boolean.class));
        attrs.add(createAttributeInfo(ATTR_USAGE_THRESHOLD, Long.class));
        attrs.add(createAttributeInfo(ATTR_USAGE_THRESHOLD_COUNT, Long.class));
        attrs.add(createAttributeInfo(ATTR_USAGE_THRESHOLD_EXCEEDED, Boolean.class));

        return new MBeanInfo(
                        getClass().getName(),
                        "Libgraal Memory Pool",
                        attrs.toArray(new MBeanAttributeInfo[attrs.size()]),
                        new MBeanConstructorInfo[0],
                        new MBeanOperationInfo[0],
                        new MBeanNotificationInfo[0]);
    }

    private static MBeanAttributeInfo createAttributeInfo(String name, Class<?> type) {
        return new MBeanAttributeInfo(name, type.getName(), name, true, false, false);
    }

    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        switch (attribute) {
            case ATTR_TYPE:
                return "NON_HEAP";
            case ATTR_VALID:
                return true;
            case ATTR_USAGE:
                return getUsage();
            case ATTR_PEAK_USAGE:
                return getPeekUsage();
            case ATTR_COLLECTION_USAGE_THRESHOLD_SUPPORTED:
                return false;
            case ATTR_COLLECTION_USAGE_THRESHOLD_EXCEEDED:
                return false;
            case ATTR_COLLECTION_USAGE_THRESHOLD_COUNT:
                return 0L;
            case ATTR_COLLECTION_USAGE_THRESHOLD:
                return 0L;
            case ATTR_COLLECTION_USAGE:
                return null;
            case ATTR_USAGE_THRESHOLD_SUPPORTED:
                return false;
            case ATTR_USAGE_THRESHOLD_EXCEEDED:
                return false;
            case ATTR_USAGE_THRESHOLD_COUNT:
                return 0L;
            case ATTR_USAGE_THRESHOLD:
                return 0L;
            default:
                throw new AttributeNotFoundException(attribute);
        }
    }

    void update() {
        updateMemStat();
    }

    private CompositeData getUsage() {
        try {
            return new CompositeDataSupport(USAGE_TYPE, USAGE_TYPE_ATTRS, updateMemStat());
        } catch (OpenDataException e) {
            throw new RuntimeException(e);
        }
    }

    private CompositeData getPeekUsage() {
        try {
            Object[] data = updateMemStat();
            data[0] = peekTotalMemory;
            data[3] = peekUsedMemory;
            return new CompositeDataSupport(USAGE_TYPE, USAGE_TYPE_ATTRS, data);
        } catch (OpenDataException e) {
            throw new RuntimeException(e);
        }
    }

    private Object[] updateMemStat() {
        long maxMemory = rt.maxMemory();
        long totalMemory = rt.totalMemory();
        long freeMemory = rt.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        peekTotalMemory = Math.max(peekTotalMemory, totalMemory);
        peekUsedMemory = Math.max(peekUsedMemory, usedMemory);
        return new Object[]{totalMemory, 0L, maxMemory, usedMemory};
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        List<Attribute> result = new ArrayList<>();
        for (String attribute : attributes) {
            try {
                Object value = getAttribute(attribute);
                result.add(new Attribute(attribute, value));
            } catch (AttributeNotFoundException | MBeanException | ReflectionException e) {
            }
        }
        return new AttributeList(result);
    }

    @Override
    public void setAttribute(Attribute atrbt) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        throw new UnsupportedOperationException("Set attribute is not supported.");
    }

    @Override
    public AttributeList setAttributes(AttributeList al) {
        throw new UnsupportedOperationException("Set attribute is not supported.");
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        throw new UnsupportedOperationException("Operation invoke is not supported.");
    }
}
