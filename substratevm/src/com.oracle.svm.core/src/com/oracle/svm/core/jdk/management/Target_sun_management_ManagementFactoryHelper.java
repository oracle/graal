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
package com.oracle.svm.core.jdk.management;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.PlatformLoggingMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK11OrLater;

/**
 * See {@link ManagementSupport} for documentation of the overall approach.
 * 
 * In the JDK, the methods substituted in this class perform the lazy allocation of the beans. Since
 * we have all beans eagerly allocated at image build time, we can just return the already allocated
 * objects.
 */
@TargetClass(className = "sun.management.ManagementFactoryHelper")
@SuppressWarnings("unused")
final class Target_sun_management_ManagementFactoryHelper {

    @Substitute
    private static ClassLoadingMXBean getClassLoadingMXBean() {
        return ManagementSupport.getSingleton().getPlatformMXBean(ClassLoadingMXBean.class);
    }

    @Substitute
    private static MemoryMXBean getMemoryMXBean() {
        return ManagementSupport.getSingleton().getPlatformMXBean(MemoryMXBean.class);
    }

    @Substitute
    private static ThreadMXBean getThreadMXBean() {
        return ManagementSupport.getSingleton().getPlatformMXBean(ThreadMXBean.class);
    }

    @Substitute
    private static RuntimeMXBean getRuntimeMXBean() {
        return ManagementSupport.getSingleton().getPlatformMXBean(RuntimeMXBean.class);
    }

    @Substitute
    private static CompilationMXBean getCompilationMXBean() {
        return ManagementSupport.getSingleton().getPlatformMXBean(CompilationMXBean.class);
    }

    @Substitute
    private static OperatingSystemMXBean getOperatingSystemMXBean() {
        return ManagementSupport.getSingleton().getPlatformMXBean(OperatingSystemMXBean.class);
    }

    @Substitute
    private static List<MemoryPoolMXBean> getMemoryPoolMXBeans() {
        return ManagementSupport.getSingleton().getPlatformMXBeans(MemoryPoolMXBean.class);
    }

    @Substitute
    private static List<MemoryManagerMXBean> getMemoryManagerMXBeans() {
        return ManagementSupport.getSingleton().getPlatformMXBeans(MemoryManagerMXBean.class);
    }

    @Substitute
    private static List<GarbageCollectorMXBean> getGarbageCollectorMXBeans() {
        return ManagementSupport.getSingleton().getPlatformMXBeans(GarbageCollectorMXBean.class);
    }

    @Substitute
    private static PlatformLoggingMXBean getPlatformLoggingMXBean() {
        return null;
    }

    @TargetElement(onlyWith = JDK11OrLater.class)
    @Substitute
    private static boolean isPlatformLoggingMXBeanAvailable() {
        return false;
    }
}
