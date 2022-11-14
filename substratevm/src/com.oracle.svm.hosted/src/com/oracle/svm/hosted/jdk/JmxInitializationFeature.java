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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;

@AutomaticallyRegisteredFeature
public class JmxInitializationFeature implements InternalFeature {
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

        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory$Mappings", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory$IdentityMapping", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.DescriptorCache", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.StandardMBeanIntrospector", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.MXBeanIntrospector", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver.MXBeanLookup", "JMX support");
        rci.initializeAtBuildTime("com.sun.jmx.remote.util.ClassLogger", "JMX support");
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
        rci.initializeAtRunTime("sun.rmi.transport.GC", "JMX support");
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
}
