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
package org.graalvm.compiler.hotspot.management;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * MemoryPoolBean providing aggregated memory usages for LibGraal isolates.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class AggregatedMemoryPoolBean implements MemoryPoolMXBean {

    private static final String[] MEMORY_MANAGER_NAMES = new String[0];

    private final ObjectName objectName;
    private final String name;
    private final MemoryType type;
    private final Map<ObjectName, DynamicMBean> delegates;

    AggregatedMemoryPoolBean(ObjectName aggregateBeanObjectName, DynamicMBean delegate, ObjectName delegateObjectName) {
        this.delegates = Collections.synchronizedMap(new HashMap<>());
        this.objectName = aggregateBeanObjectName;
        String typeName = safeReadAttribute(delegate, "Type", String.class);
        this.type = typeName != null ? MemoryType.valueOf(typeName) : MemoryType.NON_HEAP;
        this.name = String.format("Aggregated %s", delegate.getMBeanInfo().getDescription());
        this.delegates.put(delegateObjectName, delegate);
    }

    void addDelegate(DynamicMBean delegate, ObjectName delegateObjectName) {
        delegates.put(delegateObjectName, delegate);
    }

    void removeDelegate(ObjectName delegate) {
        delegates.remove(delegate);
    }

    @Override
    public ObjectName getObjectName() {
        return objectName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public MemoryType getType() {
        return type;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public MemoryUsage getUsage() {
        return collect("Usage");
    }

    @Override
    public MemoryUsage getPeakUsage() {
        return collect("PeakUsage");
    }

    private MemoryUsage collect(String attributeName) {
        long init = 0L;
        long used = 0L;
        long committed = 0L;
        long max = 0L;
        synchronized (delegates) {
            for (DynamicMBean delegate : delegates.values()) {
                CompositeData compositeData = safeReadAttribute(delegate, attributeName, CompositeData.class);
                if (compositeData != null) {
                    MemoryUsage isolateMemoryUsage = MemoryUsage.from(compositeData);
                    init += isolateMemoryUsage.getInit();
                    used += isolateMemoryUsage.getUsed();
                    committed += isolateMemoryUsage.getCommitted();
                    max += isolateMemoryUsage.getMax();
                }
            }
        }
        return new MemoryUsage(init, used, committed, max);
    }

    static <T> T safeReadAttribute(DynamicMBean mbean, String name, Class<T> attrType) {
        try {
            return attrType.cast(mbean.getAttribute(name));
        } catch (AttributeNotFoundException | MBeanException | ReflectionException e) {
            return null;
        }
    }

    @Override
    public String[] getMemoryManagerNames() {
        return MEMORY_MANAGER_NAMES;
    }

    @Override
    public void resetPeakUsage() {
    }

    @Override
    public MemoryUsage getCollectionUsage() {
        return null;
    }

    @Override
    public boolean isUsageThresholdSupported() {
        return false;
    }

    @Override
    public boolean isUsageThresholdExceeded() {
        throw new UnsupportedOperationException("UsageThreshold is not supported.");
    }

    @Override
    public long getUsageThresholdCount() {
        throw new UnsupportedOperationException("UsageThreshold is not supported.");
    }

    @Override
    public long getUsageThreshold() {
        throw new UnsupportedOperationException("UsageThreshold is not supported.");
    }

    @Override
    public void setUsageThreshold(long threshold) {
        throw new UnsupportedOperationException("UsageThreshold is not supported.");
    }

    @Override
    public boolean isCollectionUsageThresholdSupported() {
        return false;
    }

    @Override
    public boolean isCollectionUsageThresholdExceeded() {
        throw new UnsupportedOperationException("CollectionUsageThreshold is not supported.");
    }

    @Override
    public long getCollectionUsageThresholdCount() {
        throw new UnsupportedOperationException("CollectionUsageThreshold is not supported.");
    }

    @Override
    public long getCollectionUsageThreshold() {
        throw new UnsupportedOperationException("CollectionUsageThreshold is not supported.");
    }

    @Override
    public void setCollectionUsageThreshold(long threshold) {
        throw new UnsupportedOperationException("CollectionUsageThreshold is not supported.");
    }
}
