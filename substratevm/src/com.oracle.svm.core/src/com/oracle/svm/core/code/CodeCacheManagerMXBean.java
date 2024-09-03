/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;

import javax.management.MBeanNotificationInfo;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import sun.management.Util;

public final class CodeCacheManagerMXBean implements MemoryManagerMXBean, NotificationEmitter {
    public static final String CODE_CACHE_CODE_AND_DATA_POOL = "runtime code cache (code and data)";
    public static final String CODE_CACHE_NATIVE_METADATA_POOL = "runtime code cache (native metadata)";
    public static final String CODE_CACHE_MANAGER = "code cache";

    @Platforms(Platform.HOSTED_ONLY.class)
    public CodeCacheManagerMXBean() {
    }

    @Override
    public String getName() {
        return CODE_CACHE_MANAGER;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String[] getMemoryPoolNames() {
        return new String[]{CODE_CACHE_CODE_AND_DATA_POOL, CODE_CACHE_NATIVE_METADATA_POOL};
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.MEMORY_MANAGER_MXBEAN_DOMAIN_TYPE, getName());
    }

    @Override
    public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
    }

    @Override
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
    }

    @Override
    public void removeNotificationListener(NotificationListener listener) {
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[0];
    }
}
