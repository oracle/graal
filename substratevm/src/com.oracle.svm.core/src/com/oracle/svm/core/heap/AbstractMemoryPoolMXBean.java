/*
 * Copyright (c) 2022, 2022, BELLSOFT. All rights reserved.
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;

import sun.management.Util;

public abstract class AbstractMemoryPoolMXBean extends MXBeanBase implements MemoryPoolMXBean {

    protected final String name;
    protected final String[] managerNames;
    protected final String objectName;

    protected AbstractMemoryPoolMXBean(String name, String... managerNames) {
        this.name = name;
        this.managerNames = managerNames.clone();
        this.objectName = ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE + ",name=" + name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String[] getMemoryManagerNames() {
        return managerNames.clone();
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(objectName);
    }

    @Override
    public MemoryType getType() {
        return MemoryType.HEAP;
    }

    @Override
    public boolean isValid() {
        return true;
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
    public long getUsageThreshold() {
        throw new UnsupportedOperationException("Usage threshold is not supported");
    }

    @Override
    public void setUsageThreshold(long l) {
        throw new UnsupportedOperationException("Usage threshold is not supported");
    }

    @Override
    public boolean isUsageThresholdExceeded() {
        throw new UnsupportedOperationException("Usage threshold is not supported");
    }

    @Override
    public long getUsageThresholdCount() {
        throw new UnsupportedOperationException("Usage threshold is not supported");
    }

    @Override
    public boolean isCollectionUsageThresholdSupported() {
        return false;
    }

    @Override
    public long getCollectionUsageThreshold() {
        throw new UnsupportedOperationException("Collection usage threshold is not supported");
    }

    @Override
    public void setCollectionUsageThreshold(long l) {
        throw new UnsupportedOperationException("Collection usage threshold is not supported");
    }

    @Override
    public boolean isCollectionUsageThresholdExceeded() {
        throw new UnsupportedOperationException("Collection usage threshold is not supported");
    }

    @Override
    public long getCollectionUsageThresholdCount() {
        throw new UnsupportedOperationException("Collection usage threshold is not supported");
    }
}