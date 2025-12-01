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
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;

import com.oracle.svm.configure.ResourcesRegistry;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jdk.management.ManagementAgentStartupHook;
import com.oracle.svm.core.jdk.management.ManagementSupport;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.reflect.proxy.ProxyRegistry;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.dynamicaccess.JVMCIRuntimeReflection;

import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = Independent.class)
public class JmxServerFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild() && VMInspectionOptions.hasJmxServerSupport();
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
        configureReflection((BeforeAnalysisAccessImpl) access);
        configureProxy(access);
        RuntimeSupport.getRuntimeSupport().addStartupHook(new ManagementAgentStartupHook());
    }

    private static void registerJMXAgentResources() {
        ResourcesRegistry<AccessCondition> resourcesRegistry = ResourcesRegistry.singleton();

        resourcesRegistry.addResourceBundles(AccessCondition.unconditional(),
                        false, "jdk.internal.agent.resources.agent");

        resourcesRegistry.addResourceBundles(AccessCondition.unconditional(),
                        false, "sun.security.util.resources.security"); // required for password
                                                                        // auth
    }

    private static void configureProxy(BeforeAnalysisAccess access) {
        ProxyRegistry proxyRegistry = ImageSingletons.lookup(ProxyRegistry.class);

        proxyRegistry.registerProxy(AccessCondition.unconditional(), false, access.findClassByName("java.rmi.Remote"),
                        access.findClassByName("java.rmi.registry.Registry"));

        proxyRegistry.registerProxy(AccessCondition.unconditional(), false, access.findClassByName("javax.management.remote.rmi.RMIServer"));
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
    private static void configureReflection(BeforeAnalysisAccessImpl access) {
        Set<PlatformManagedObject> platformManagedObjects = ManagementSupport.getSingleton().getPlatformManagedObjects();
        for (PlatformManagedObject p : platformManagedObjects) {
            /*
             * The platformManagedObjects list contains some PlatformManagedObjectSupplier objects
             * that are meant to help initialize some MXBeans at runtime. Skip them here.
             */
            if (p instanceof ManagementSupport.PlatformManagedObjectSupplier) {
                continue;
            }
            JVMCIRuntimeReflection.register(access.getMetaAccess().lookupJavaType(p.getClass()));
        }

        ResolvedJavaType serviceProviderClass = access.findTypeByName("com.sun.jmx.remote.protocol.rmi.ServerProvider");
        JVMCIRuntimeReflection.register(serviceProviderClass);
        JVMCIRuntimeReflection.register(JVMCIReflectionUtil.getConstructors(serviceProviderClass));
    }
}
