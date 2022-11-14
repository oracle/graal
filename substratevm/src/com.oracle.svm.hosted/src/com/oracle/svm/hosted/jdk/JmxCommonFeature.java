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

import java.util.Arrays;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeSerialization;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
public class JmxCommonFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasJmxServerSupport() || VMInspectionOptions.hasJmxClientSupport();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        configureJNI();
        configureSerialization(access);
        configureReflection(access);
        configureProxy(access);
    }

    private static void configureProxy(BeforeAnalysisAccess access) {
        DynamicProxyRegistry dynamicProxySupport = ImageSingletons.lookup(DynamicProxyRegistry.class);

        dynamicProxySupport.addProxyClass(access.findClassByName("jdk.management.jfr.FlightRecorderMXBean"),
                        access.findClassByName("javax.management.NotificationEmitter"));

        dynamicProxySupport.addProxyClass(access.findClassByName("java.lang.management.RuntimeMXBean"));
        dynamicProxySupport.addProxyClass(access.findClassByName("java.lang.management.ClassLoadingMXBean"));
        dynamicProxySupport.addProxyClass(access.findClassByName("com.sun.management.ThreadMXBean"));
        dynamicProxySupport.addProxyClass(access.findClassByName("java.lang.management.ThreadMXBean"));
        dynamicProxySupport.addProxyClass(access.findClassByName("java.lang.management.GarbageCollectorMXBean"), access.findClassByName("javax.management.NotificationEmitter"));
        dynamicProxySupport.addProxyClass(access.findClassByName("com.sun.management.GarbageCollectorMXBean"), access.findClassByName("javax.management.NotificationEmitter"));
        dynamicProxySupport.addProxyClass(access.findClassByName("com.sun.management.OperatingSystemMXBean"));
        dynamicProxySupport.addProxyClass(access.findClassByName("java.lang.management.OperatingSystemMXBean"));
        dynamicProxySupport.addProxyClass(access.findClassByName("java.lang.management.MemoryManagerMXBean"), access.findClassByName("javax.management.NotificationEmitter"));
        dynamicProxySupport.addProxyClass(access.findClassByName("java.lang.management.BufferPoolMXBean"));
        dynamicProxySupport.addProxyClass(access.findClassByName("java.lang.management.MemoryPoolMXBean"));
        dynamicProxySupport.addProxyClass(access.findClassByName("java.lang.management.MemoryMXBean"), access.findClassByName("javax.management.NotificationEmitter"));
        dynamicProxySupport.addProxyClass(access.findClassByName("com.sun.management.UnixOperatingSystemMXBean"));
        dynamicProxySupport.addProxyClass(access.findClassByName("com.sun.management.java.lang.management.CompilationMXBean"));
    }

    private static void configureJNI() {
        JNIRuntimeAccess.register(Arrays.class);
        JNIRuntimeAccess.register(ReflectionUtil.lookupMethod(Arrays.class, "asList", Object[].class));
    }

    private static void configureSerialization(BeforeAnalysisAccess access) {
        String[] classes = new String[]{
                        "[B", "com.oracle.svm.core.jdk.UnsupportedFeatureError",
                        "java.io.IOException", "java.lang.Boolean", "java.lang.ClassCastException", "java.lang.Error",
                        "java.lang.Exception", "java.lang.IllegalArgumentException", "java.lang.IllegalStateException",
                        "java.lang.Integer", "[Ljava.lang.Integer;", "java.lang.Long", "java.lang.NoSuchMethodException",
                        "java.lang.Number", "[Ljava.lang.Object;", "java.lang.ReflectiveOperationException",
                        "java.lang.RuntimeException", "java.lang.SecurityException", "java.lang.StackTraceElement",
                        "[Ljava.lang.StackTraceElement;", "java.lang.String", "java.lang.Throwable",
                        "java.lang.UnsupportedOperationException",
                        "java.rmi.MarshalledObject", "[Ljava.rmi.MarshalledObject;", "java.rmi.RemoteException",
                        "java.rmi.ServerError", "java.rmi.dgc.Lease", "java.rmi.dgc.VMID", "java.rmi.server.ObjID",
                        "[Ljava.rmi.server.ObjID;", "java.rmi.server.RemoteObject", "java.rmi.server.RemoteStub",
                        "java.rmi.server.UID", "java.security.GeneralSecurityException", "java.util.ArrayList",
                        "java.util.Arrays$ArrayList", "java.util.Collections$EmptyList",
                        "java.util.Collections$UnmodifiableCollection", "java.util.Collections$UnmodifiableList",
                        "java.util.Collections$UnmodifiableRandomAccessList",
                        "java.util.EventObject", // Required for notifications
                        "java.util.HashMap", "java.util.HashSet",
                        "java.util.LinkedHashMap", "java.util.MissingResourceException", "java.util.TreeMap",
                        "java.util.Vector", "javax.management.Attribute", "javax.management.AttributeList",
                        "javax.management.AttributeNotFoundException",
                        "javax.management.AttributeChangeNotification", // Required for
                                                                        // notifications
                        "javax.management.InstanceNotFoundException",
                        "javax.management.JMException", "javax.management.MBeanAttributeInfo",
                        "[Ljavax.management.MBeanAttributeInfo;", "[Ljavax.management.MBeanConstructorInfo;",
                        "javax.management.MBeanFeatureInfo", "javax.management.MBeanInfo",
                        "javax.management.MBeanNotificationInfo", "[Ljavax.management.MBeanNotificationInfo;",
                        "javax.management.MBeanOperationInfo", "[Ljavax.management.MBeanOperationInfo;",
                        "javax.management.MBeanParameterInfo", "[Ljavax.management.MBeanParameterInfo;",
                        "javax.management.Notification", // Required for notifications
                        "javax.management.NotificationFilterSupport", "javax.management.ObjectName",
                        "[Ljavax.management.ObjectName;", "javax.management.OperationsException", "javax.management.ReflectionException",
                        "javax.management.openmbean.ArrayType", "javax.management.openmbean.CompositeDataSupport",
                        "[Ljavax.management.openmbean.CompositeData;", "javax.management.openmbean.CompositeType",
                        "javax.management.openmbean.OpenMBeanAttributeInfoSupport",
                        "javax.management.openmbean.OpenMBeanParameterInfoSupport", "javax.management.openmbean.OpenType",
                        "javax.management.openmbean.SimpleType", "javax.management.openmbean.TabularDataSupport",
                        "javax.management.openmbean.TabularType", "javax.management.remote.NotificationResult",
                        "javax.management.remote.TargetedNotification", // Required for
                                                                        // notifications
                        "[Ljavax.management.remote.TargetedNotification;", "javax.management.remote.rmi.RMIConnectionImpl_Stub",
                        "javax.management.remote.rmi.RMIServerImpl_Stub", "javax.management.RuntimeMBeanException",
                        "javax.rmi.ssl.SslRMIClientSocketFactory", // Required for SSL
                        "[Ljavax.security.auth.Subject;", "javax.security.auth.login.FailedLoginException",
                        "javax.security.auth.login.LoginException", "[J", "java.rmi.NoSuchObjectException",
                        "javax.management.JMRuntimeException", "javax.management.RuntimeErrorException"
        };
        for (String clazz : classes) {
            RuntimeSerialization.register(access.findClassByName(clazz));
        }
    }

    private static void configureReflection(BeforeAnalysisAccess access) {
        // Only JmxServerFeature, not JmxClientFeature, has registrations for platform MBeans
        String[] classes = new String[]{
                        "com.sun.crypto.provider.AESCipher$General", "com.sun.crypto.provider.ARCFOURCipher",
                        "com.sun.crypto.provider.ChaCha20Cipher$ChaCha20Poly1305", "com.sun.crypto.provider.DESCipher",
                        "com.sun.crypto.provider.DESedeCipher", "com.sun.crypto.provider.DHParameters",
                        "com.sun.crypto.provider.HmacCore$HmacSHA256",
                        "com.sun.management.GcInfo",
                        "com.sun.management.internal.OperatingSystemImpl",
                        "javax.management.remote.rmi.RMIConnection", "com.sun.management.VMOption",
                        "javax.management.remote.rmi.RMIConnectionImpl_Stub", "javax.management.remote.rmi.RMIServer",
                        "javax.management.remote.rmi.RMIServerImpl_Stub", "sun.rmi.registry.RegistryImpl_Stub",
                        "java.rmi.MarshalledObject", "java.rmi.Remote", "java.rmi.dgc.Lease", "java.rmi.dgc.VMID",
                        "java.rmi.registry.Registry", "java.rmi.server.ObjID", "java.rmi.server.RemoteObject",
                        "java.rmi.server.RemoteStub", "java.rmi.server.UID", "javax.management.openmbean.OpenType",
                        "java.lang.management.LockInfo", "java.lang.management.ManagementPermission",
                        "java.lang.management.MemoryUsage", "java.lang.management.MonitorInfo",
                        "java.lang.management.ThreadInfo", "java.security.SecureRandomParameters",
                        "javax.management.MBeanServerBuilder", "javax.management.NotificationBroadcaster",
                        "javax.management.NotificationEmitter", "javax.management.NotificationFilterSupport",
                        "javax.management.ObjectName", "jdk.management.jfr.ConfigurationInfo",
                        "jdk.management.jfr.EventTypeInfo",
                        "jdk.management.jfr.RecordingInfo", "jdk.management.jfr.SettingDescriptorInfo",
                        "sun.rmi.registry.RegistryImpl_Skel", "sun.rmi.transport.DGCImpl_Skel",
                        "sun.rmi.server.UnicastRef2", "sun.rmi.transport.DGCImpl", "sun.rmi.transport.DGCImpl_Skel",
                        "sun.rmi.transport.DGCImpl_Stub"
        };

        String[] methods = new String[]{
                        "com.sun.management.GcInfo",
                        "com.sun.management.VMOption",
                        "java.lang.management.MemoryUsage", "java.rmi.registry.Registry",
                        "javax.management.remote.rmi.RMIConnection", "javax.management.remote.rmi.RMIConnectionImpl_Stub",
                        "javax.management.remote.rmi.RMIServer", "javax.management.remote.rmi.RMIServerImpl_Stub",
                        "java.lang.management.MonitorInfo",
                        "java.lang.management.ThreadInfo", "jdk.management.jfr.ConfigurationInfo",
                        "jdk.management.jfr.EventTypeInfo", "jdk.management.jfr.RecordingInfo",
                        "jdk.management.jfr.SettingDescriptorInfo", "sun.rmi.registry.RegistryImpl_Stub",
                        "sun.rmi.server.UnicastRef2", "sun.rmi.transport.DGCImpl", "sun.rmi.transport.DGCImpl_Skel",
                        "sun.rmi.transport.DGCImpl_Stub"
        };

        String[] fields = new String[]{"com.sun.management.GcInfo"};

        String[] constructors = new String[]{
                        "com.sun.management.internal.GarbageCollectorExtImpl",
                        "com.sun.management.internal.OperatingSystemImpl", "java.lang.management.ManagementPermission",
                        "javax.management.MBeanServerBuilder", "javax.management.remote.rmi.RMIConnectionImpl_Stub",
                        "javax.management.remote.rmi.RMIServerImpl_Stub", "sun.rmi.transport.DGCImpl_Stub",
                        "sun.rmi.registry.RegistryImpl_Skel", "sun.rmi.registry.RegistryImpl_Stub",
                        "sun.rmi.server.UnicastRef2", "sun.rmi.transport.DGCImpl_Skel"
        };

        for (String clazz : classes) {
            RuntimeReflection.register(access.findClassByName(clazz));
        }
        for (String clazz : methods) {
            RuntimeReflection.register(access.findClassByName(clazz).getMethods());
        }
        for (String clazz : constructors) {
            RuntimeReflection.register(access.findClassByName(clazz).getConstructors());
        }
        for (String clazz : fields) {
            RuntimeReflection.register(access.findClassByName(clazz).getFields());
        }
    }
}
