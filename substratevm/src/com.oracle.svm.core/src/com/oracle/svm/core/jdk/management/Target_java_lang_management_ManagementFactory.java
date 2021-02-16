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

import java.lang.management.PlatformManagedObject;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/** See {@link ManagementSupport} for documentation. */
@TargetClass(java.lang.management.ManagementFactory.class)
@SuppressWarnings("unused")
final class Target_java_lang_management_ManagementFactory {

    @Substitute
    private static MBeanServer getPlatformMBeanServer() {
        return ManagementSupport.getSingleton().getPlatformMBeanServer();
    }

    @Substitute
    private static Set<Class<?>> getPlatformManagementInterfaces() {
        return ManagementSupport.getSingleton().getPlatformManagementInterfaces();
    }

    @Substitute
    private static <T extends PlatformManagedObject> T getPlatformMXBean(Class<T> mxbeanInterface) {
        return ManagementSupport.getSingleton().getPlatformMXBean(mxbeanInterface);
    }

    @Substitute
    static <T extends PlatformManagedObject> List<T> getPlatformMXBeans(Class<T> mxbeanInterface) {
        return ManagementSupport.getSingleton().getPlatformMXBeans(mxbeanInterface);
    }

    /*
     * Connections to remote MBean servers are not yet supported.
     */

    @Substitute
    private static <T extends PlatformManagedObject> T getPlatformMXBean(MBeanServerConnection connection, Class<T> mxbeanInterface) throws java.io.IOException {
        return null;
    }

    @Substitute
    private static <T extends PlatformManagedObject> List<T> getPlatformMXBeans(MBeanServerConnection connection, Class<T> mxbeanInterface) throws java.io.IOException {
        return Collections.emptyList();
    }

    @Substitute
    private static <T> T newPlatformMXBeanProxy(MBeanServerConnection connection, String mxbeanName, Class<T> mxbeanInterface) throws java.io.IOException {
        return null;
    }
}
