/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.hosted.jdk;

import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
public class JmxClientFeature extends JNIRegistrationUtil implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasJmxClientSupport();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            configureJNI();
            configureReflection(access);
        } catch (Exception e) {
            throw VMError.shouldNotReachHere("ManagementClientFeature configuration failed: " + e);
        }
    }

    private static void configureJNI() {
        JNIRuntimeAccess.register(Boolean.class);
        JNIRuntimeAccess.register(ReflectionUtil.lookupMethod(Boolean.class, "getBoolean", String.class));
    }

    /**
     * This method configures reflection metadata only required by a JMX client.
     * <ul>
     * <li>Register {@code com.sun.jmx.remote.protocol.rmi.ClientProvider} which can be reflectively
     * looked up on a code path starting from
     * {@code javax.management.remote.JMXConnectorFactory#newJMXConnector(JMXServiceURL, Map)}</li>
     * <li>Register {@code sun.rmi.server.UnicastRef2}, which can be reflectively accessed with
     * {@code sun.rmi.server.UnicastRef2#getRefClass(ObjectOutput)}.</li>
     * <li>Register {@code sun.rmi.server.UnicastRef}, which can be reflectively accessed with
     * {@code sun.rmi.server.UnicastRef#getRefClass(ObjectOutput)}.</li>
     * </ul>
     */
    private static void configureReflection(BeforeAnalysisAccess access) {
        RuntimeReflection.register(access.findClassByName("com.sun.jndi.url.rmi.rmiURLContextFactory"));
        RuntimeReflection.register(access.findClassByName("sun.rmi.server.UnicastRef"));

        RuntimeReflection.register(access.findClassByName("com.sun.jmx.remote.protocol.rmi.ClientProvider"));
        RuntimeReflection.register(access.findClassByName("com.sun.jndi.url.rmi.rmiURLContextFactory").getConstructors());
        RuntimeReflection.register(access.findClassByName("sun.rmi.server.UnicastRef").getConstructors());
        RuntimeReflection.register(access.findClassByName("sun.rmi.server.UnicastRef2").getConstructors());
        RuntimeReflection.register(access.findClassByName("com.sun.jmx.remote.protocol.rmi.ClientProvider").getConstructors());
    }
}
