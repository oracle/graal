/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.management.PlatformManagedObject;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.remote.JMXServiceURL;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jdk.management.ManagementAgentStartupHook;
import com.oracle.svm.core.jdk.management.ManagementSupport;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;

@AutomaticallyRegisteredFeature
public class JmxServerFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasJmxServerSupport();
    }

    private static void handleNativeLibraries(BeforeAnalysisAccess access) {
        // This is required for password authentication.
        // JMX checks the restrictions on the password file via a JNI native method.
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("management_agent");
        BeforeAnalysisAccessImpl beforeAnalysisAccess = (BeforeAnalysisAccessImpl) access;
        beforeAnalysisAccess.getNativeLibraries().addStaticJniLibrary("management_agent");
        // Resolve calls to jdk_internal_agent* as builtIn. For calls to native method
        // isAccessUserOnly0.
        PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("jdk_internal_agent");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        handleNativeLibraries(access);
        registerJMXAgentResources();
        configureReflection(access);
        configureProxy(access);
        RuntimeSupport.getRuntimeSupport().addStartupHook(new ManagementAgentStartupHook());
    }

    private static void registerJMXAgentResources() {
        ResourcesRegistry<ConfigurationCondition> resourcesRegistry = ResourcesRegistry.singleton();

        resourcesRegistry.addResourceBundles(ConfigurationCondition.alwaysTrue(),
                        "jdk.internal.agent.resources.agent");

        resourcesRegistry.addResourceBundles(ConfigurationCondition.alwaysTrue(),
                        "sun.security.util.Resources"); // required for password auth
    }

    private static void configureProxy(BeforeAnalysisAccess access) {
        DynamicProxyRegistry dynamicProxySupport = ImageSingletons.lookup(DynamicProxyRegistry.class);

        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("java.rmi.Remote"),
                        access.findClassByName("java.rmi.registry.Registry"));

        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("javax.management.remote.rmi.RMIServer"));
    }

    /**
     * This method configures reflection metadata only required by a JMX server.
     * <ul>
     * <li>Here we register all the custom MXBeans of Substrate VM. They will not be accounted for
     * by the native image tracing agent so a user may not know they need to register them.</li>
     * <li>We also register {@code com.sun.jmx.remote.protocol.rmi.ServerProvider} which can be
     * reflectively looked up on a code path starting from
     * {@link javax.management.remote.JMXConnectorServerFactory#newJMXConnectorServer(JMXServiceURL, Map, MBeanServer)}
     * </li>
     * </ul>
     */
    private static void configureReflection(BeforeAnalysisAccess access) {
        Set<PlatformManagedObject> platformManagedObjects = ManagementSupport.getSingleton().getPlatformManagedObjects();
        for (PlatformManagedObject p : platformManagedObjects) {

            // The platformManagedObjects list contains some PlatformManagedObjectSupplier objects
            // that are meant to help initialize some MXBeans at runtime. Skip them here.
            if (p instanceof ManagementSupport.PlatformManagedObjectSupplier) {
                continue;
            }
            Class<?> clazz = p.getClass();
            RuntimeReflection.register(clazz);
        }

        RuntimeReflection.register(access.findClassByName("com.sun.jmx.remote.protocol.rmi.ServerProvider"));
        RuntimeReflection.register(access.findClassByName("com.sun.jmx.remote.protocol.rmi.ServerProvider").getConstructors());
    }
}
