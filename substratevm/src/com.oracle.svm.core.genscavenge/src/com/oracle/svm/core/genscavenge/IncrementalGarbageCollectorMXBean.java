/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

//Checkstyle: stop
import java.lang.management.ManagementFactory;

import javax.management.MBeanNotificationInfo;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.TimeUtils;
import com.sun.management.GcInfo;

import sun.management.Util;
//Checkstyle: resume

public final class IncrementalGarbageCollectorMXBean implements com.sun.management.GarbageCollectorMXBean, NotificationEmitter {

    @Platforms(Platform.HOSTED_ONLY.class)
    public IncrementalGarbageCollectorMXBean() {
    }

    @Override
    public long getCollectionCount() {
        return HeapImpl.getHeapImpl().getGCImpl().getAccounting().getIncrementalCollectionCount();
    }

    @Override
    public long getCollectionTime() {
        long nanos = HeapImpl.getHeapImpl().getGCImpl().getAccounting().getIncrementalCollectionTotalNanos();
        return TimeUtils.roundNanosToMillis(nanos);
    }

    @Override
    public String[] getMemoryPoolNames() {
        /* Return a new array each time because arrays are not immutable. */
        return new String[]{"young generation space"};
    }

    @Override
    public String getName() {
        /* Changing this name will break assumptions we take in the object replacer. */
        return "young generation scavenger";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE, getName());
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

    @Override
    public GcInfo getLastGcInfo() {
        return null;
    }
}
