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
import org.graalvm.nativeimage.impl.ConfigurationCondition;

@AutomaticallyRegisteredFeature
public class JmxCommonFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasJmxServerSupport() || VMInspectionOptions.hasJmxClientSupport();
    }

    /**
     * This method adds JMX-specific initialization policies when JMX support is enabled.
     * <p>
     * Note that
     * {@link com.oracle.svm.core.jdk.management.ManagementFeature#duringSetup(org.graalvm.nativeimage.hosted.Feature.DuringSetupAccess)
     * ManagementFeature#duringSetup()} adds additional JMX-related initialization policies
     * unconditionally.
     */
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport rci = ImageSingletons.lookup(org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport.class);

        rci.initializeAtBuildTime("jdk.management.jfr.SettingDescriptorInfo", "JMX support");
        rci.initializeAtBuildTime("sun.rmi.runtime.Log$LogStreamLog", "JMX support");
        rci.initializeAtBuildTime("sun.rmi.runtime.Log", "JMX support");
        rci.initializeAtBuildTime("java.rmi.server.LogStream", "JMX support");
        rci.initializeAtBuildTime("sun.rmi.runtime.Log$LoggerLog", "JMX support");
        rci.initializeAtBuildTime("sun.rmi.server.Util", "JMX support");
        rci.initializeAtBuildTime("jdk.management.jfr.MBeanUtils", "JMX support");

        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.StandardMBeanIntrospector", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.MXBeanIntrospector", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.MXBeanLookup", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.remote.util.EnvHelp", "JMX support");
        rci.initializeAtBuildTime("java.rmi.server.RemoteObjectInvocationHandler", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.Introspector", "JMX support");
        rci.initializeAtBuildTime("java.beans.Introspector", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.JavaBeansAccessor", "JMX support");

        rci.initializeAtBuildTime("com.sun.jmx.remote.security.JMXPluggableAuthenticator", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.MBeanInstantiator", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.remote.internal.ArrayNotificationBuffer", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.remote.security.HashedPasswordManager", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.remote.security.JMXSubjectDomainCombiner", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.remote.internal.ServerCommunicatorAdmin", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.defaults.JmxProperties", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.remote.internal.ServerNotifForwarder", "JMX support");

        rci.initializeAtBuildTime("sun.rmi.runtime.Log$LoggerLogFactory", "JMX support");
        rci.initializeAtBuildTime("java.beans.Introspector$1", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.remote.internal.ArrayNotificationBuffer$BroadcasterQuery", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.WeakIdentityHashMap", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.MBeanIntrospector$PerInterfaceMap", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.MBeanIntrospector$MBeanInfoMap", "JMX support");
        rci.initializeAtBuildTime("sun.rmi.runtime.Log$InternalStreamHandler", "JMX support");
        rci.initializeAtBuildTime("java.rmi.server.RemoteObjectInvocationHandler$MethodToHash_Maps", "JMX support");

        rci.initializeAtRunTime("sun.rmi.transport.ConnectionInputStream", "JMX support");
        rci.initializeAtRunTime("java.rmi.MarshalledObject$MarshalledObjectInputStream", "JMX support");
        rci.initializeAtRunTime("sun.rmi.server.UnicastRef2", "JMX support");
        rci.initializeAtRunTime("sun.rmi.server.UnicastRef", "JMX support");
        rci.initializeAtRunTime("sun.rmi.server.MarshalInputStream", "JMX support");
        rci.initializeAtRunTime("sun.rmi.runtime.NewThreadAction", "JMX support");
        rci.initializeAtRunTime("com.sun.jmx.remote.security.FileLoginModule", "JMX support");
        rci.initializeAtRunTime("com.sun.jmx.remote.security.JMXPluggableAuthenticator$FileLoginConfig", "JMX support");
        rci.initializeAtRunTime("sun.rmi.transport.DGCImpl", "JMX support");
        rci.initializeAtRunTime("sun.rmi.transport.DGCAckHandler", "JMX support");
        rci.initializeAtRunTime("sun.rmi.transport.DGCClient", "JMX support");
        rci.initializeAtRunTime("sun.rmi.transport.ObjectTable", "JMX support");
        rci.initializeAtRunTime("sun.rmi.transport.tcp.TCPEndpoint", "JMX support");
        rci.initializeAtRunTime("sun.rmi.transport.tcp.TCPChannel", "JMX support");
        rci.initializeAtRunTime("sun.rmi.transport.tcp.TCPTransport", "JMX support");
        rci.initializeAtRunTime("sun.rmi.transport.Transport", "JMX support");
        rci.initializeAtRunTime("java.rmi.server.ObjID", "JMX support");
        rci.initializeAtRunTime("sun.rmi.server.UnicastServerRef", "JMX support");
        rci.initializeAtRunTime("java.rmi.server.UID", "JMX support");
        rci.initializeAtRunTime("sun.rmi.runtime.RuntimeUtil", "JMX support");
        rci.initializeAtRunTime("java.rmi.dgc.VMID", "JMX support");
        rci.initializeAtRunTime("java.rmi.server.RMIClassLoader", "JMX support");
        rci.initializeAtRunTime("sun.rmi.server.LoaderHandler", "JMX support");
        rci.initializeAtRunTime("java.rmi.server.RemoteServer", "JMX support");
        rci.initializeAtRunTime("sun.rmi.registry.RegistryImpl", "JMX support");
        rci.initializeAtRunTime("java.rmi.server.UnicastRemoteObject", "JMX support");
        rci.initializeAtRunTime("sun.rmi.server.UnicastServerRef2", "JMX support");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        configureJNI();
        configureSerialization(access);
        configureReflection(access);
        configureProxy(access);
    }

    /**
     * This method handles proxy registrations for PlatformMXBeans. We are able to do the
     * registrations for PlatformMXBeans because they are known and there are a finite number of
     * them. If a user wishes to register a custom standard MBean with the MBeanServer, they will
     * have to provide their own proxy configuration in a JSON file. This is documented in <a href=
     * "https://www.graalvm.org/dev/reference-manual/native-image/guides/build-and-run-native-executable-with-remote-jmx/">docs/reference-manual/native-image/guides/build-and-run-native-executable-with-remote-jmx.md</a>.
     * <p>
     * PlatformMXBeans require proxy configuration so that JMX client implementations can use
     * proxies to simplify the client's interaction with MBeans on the server (in a different
     * application). Using proxies makes the connection/sending/receiving of data transparent.
     * </p>
     *
     * <p>
     * Proxy registration also registers the methods of these MXBeans for reflection. This is
     * important because they are accessed in many places in the JMX infrastructure. For example:
     * <ul>
     * <li>{@code com.sun.jmx.remote.internal.rmi.ProxyRef#invoke(Remote, Method, Object[], long)}
     * </li>
     * <li>{@code com.sun.jmx.mbeanserver.MXBeanIntrospector}</li>
     * <li>{@code com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory}</li>
     * <li>{@code com.sun.jmx.mbeanserver.MXBeanProxy}</li>
     * <li>{@code javax.management.MBeanServerInvocationHandler#isLocal(Object, Method)}</li>
     * </ul>
     * </p>
     */
    private static void configureProxy(BeforeAnalysisAccess access) {
        DynamicProxyRegistry dynamicProxySupport = ImageSingletons.lookup(DynamicProxyRegistry.class);
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("com.sun.management.GarbageCollectorMXBean"),
                        access.findClassByName("javax.management.NotificationEmitter"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("com.sun.management.OperatingSystemMXBean"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("com.sun.management.ThreadMXBean"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("com.sun.management.UnixOperatingSystemMXBean"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("java.lang.management.BufferPoolMXBean"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("java.lang.management.ClassLoadingMXBean"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("java.lang.management.CompilationMXBean"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("java.lang.management.GarbageCollectorMXBean"),
                        access.findClassByName("javax.management.NotificationEmitter"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("java.lang.management.MemoryManagerMXBean"),
                        access.findClassByName("javax.management.NotificationEmitter"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("java.lang.management.MemoryManagerMXBean"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("java.lang.management.MemoryPoolMXBean"),
                        access.findClassByName("javax.management.NotificationEmitter"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("java.lang.management.MemoryMXBean"),
                        access.findClassByName("javax.management.NotificationEmitter"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("java.lang.management.OperatingSystemMXBean"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("java.lang.management.RuntimeMXBean"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("java.lang.management.ThreadMXBean"));
        dynamicProxySupport.addProxyClass(ConfigurationCondition.alwaysTrue(), access.findClassByName("jdk.management.jfr.FlightRecorderMXBean"),
                        access.findClassByName("javax.management.NotificationEmitter"));
    }

    private static void configureJNI() {
        JNIRuntimeAccess.register(Arrays.class);
        JNIRuntimeAccess.register(ReflectionUtil.lookupMethod(Arrays.class, "asList", Object[].class));
    }

    /**
     * This configuration is required to send data between JMX client and server.
     *
     * Only {@link javax.management.MXBean}s (which use {@link javax.management.openmbean.OpenType})
     * and standard MBeans are currently supported. To support {@link javax.management.MXBean}s
     * there must be metadata configuration for {@link javax.management.openmbean.OpenType}s. For
     * example:
     * <li>{@link javax.management.openmbean.SimpleType}</li>
     * <li>{@link javax.management.openmbean.TabularType}</li>
     * <li>{@link javax.management.openmbean.CompositeData}</li>
     * <li>{@link javax.management.openmbean.ArrayType}</li> These
     * {@link javax.management.openmbean.OpenType}s are reflectively accessed at multiple points in
     * the remote JMX infrastructure (See {@code sun.management.MappedMXBeanType},
     * {@code com.sun.jmx.mbeanserver.MXBeanMapping#makeOpenClass(Type, javax.management.openmbean.OpenType)})
     */
    private static void configureSerialization(BeforeAnalysisAccess access) {
        String[] classes = {
                        "[B", "com.oracle.svm.core.jdk.UnsupportedFeatureError",
                        "java.io.IOException", "java.lang.Boolean", "java.lang.ClassCastException", "java.lang.Error",
                        "java.lang.Exception", "java.lang.IllegalArgumentException", "java.lang.IllegalStateException",
                        "java.lang.Integer", "[Ljava.lang.Integer;", "java.lang.Long", "java.lang.NoSuchMethodException",
                        "java.lang.NullPointerException",
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

    /**
     * This method configures reflection metadata shared between both JMX client and server.
     * <ul>
     * <li>All <i>*Skel</i> and <i>*Stub</i> classes must be registered for reflection along with
     * their constructors. See {@code sun.rmi.server.Util} for an example.</li>
     *
     * <li>All methods of <i>*Info</i> classes with static <i>from</i> methods must be registered
     * for reflection. For example see:
     * {@code com.sun.management.GcInfo#from(javax.management.openmbean.CompositeData)} and
     * {@link java.lang.management.MemoryUsage#from(javax.management.openmbean.CompositeData)}. This
     * is because these classes have their methods reflectively accessed from their static
     * <i>from</i> methods. Remote JMX infrastructure uses the following pattern: the <i>*Info</i>
     * classes have a corresponding "CompositeData" class which is used to construct them using the
     * static <i>from</i>method. (ie. SomeInfo would correspond to <i>SomeInfoCompositeData extends
     * LazyCompositeData </i>).</li>
     *
     * <li>{@code javax.management.remote.rmi.RMIServer} requires registration of all its methods as
     * they are used from {@code javax.management.remote.rmi.RMIServerImpl_Stub}.</li>
     * <li>{@code javax.management.remote.rmi.RMIConnection} requires registration of all its
     * methods as they are used from
     * {@code javax.management.remote.rmi.RMIConnectionImpl_Stub}.</li>
     * </ul>
     */
    private static void configureReflection(BeforeAnalysisAccess access) {
        String[] classes = {
                        "com.sun.management.internal.OperatingSystemImpl",
                        "javax.management.remote.rmi.RMIConnectionImpl_Stub",
                        "javax.management.remote.rmi.RMIServerImpl_Stub", "sun.rmi.registry.RegistryImpl_Stub",
                        "java.rmi.server.RemoteStub",
                        "sun.rmi.registry.RegistryImpl_Skel", "sun.rmi.transport.DGCImpl_Skel",
                        "sun.rmi.server.UnicastRef2", "sun.rmi.transport.DGCImpl", "sun.rmi.transport.DGCImpl_Skel",
                        "sun.rmi.transport.DGCImpl_Stub"
        };

        String[] methods = {
                        "com.sun.management.GcInfo", "java.lang.management.LockInfo",
                        "java.lang.management.MemoryUsage", "java.lang.management.MonitorInfo",
                        "java.lang.management.MemoryNotificationInfo",
                        "javax.management.remote.rmi.RMIConnection",
                        "javax.management.remote.rmi.RMIServer",
                        "java.lang.management.ThreadInfo", "jdk.management.jfr.ConfigurationInfo",
                        "jdk.management.jfr.EventTypeInfo", "jdk.management.jfr.RecordingInfo",
                        "jdk.management.jfr.SettingDescriptorInfo"
        };

        String[] constructors = {
                        "javax.management.remote.rmi.RMIConnectionImpl_Stub",
                        "javax.management.remote.rmi.RMIServerImpl_Stub", "sun.rmi.transport.DGCImpl_Stub",
                        "sun.rmi.registry.RegistryImpl_Skel", "sun.rmi.registry.RegistryImpl_Stub",
                        "sun.rmi.transport.DGCImpl_Skel"
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
    }
}
