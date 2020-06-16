/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.hosted.c.NativeLibraries;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Utility class for Registration of static JNI libraries.
 */
@Platforms({InternalPlatform.PLATFORM_JNI.class})
class JNIRegistrationSupport {

    private final ConcurrentMap<String, Boolean> registeredLibraries = new ConcurrentHashMap<>();
    private NativeLibraries nativeLibraries = null;

    public static JNIRegistrationSupport singleton() {
        return ImageSingletons.lookup(JNIRegistrationSupport.class);
    }

    public void setNativeLibraries(NativeLibraries nativelibraries) {
        nativeLibraries = nativelibraries;
    }

    @SuppressWarnings({"unused", "rawtypes"})
    public void registerNativeLibrary(Providers providers, Plugins plugins, Class clazz, String methodname) {
        Registration systemRegistration = new Registration(plugins.getInvocationPlugins(), clazz);
        systemRegistration.register1(methodname, String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode libnameNode) {
                if (libnameNode.isConstant()) {
                    String libname = (String) SubstrateObjectConstant.asObject(libnameNode.asConstant());
                    if (libname != null && NativeLibrarySupport.singleton().isPreregisteredBuiltinLibrary(libname) && registeredLibraries.putIfAbsent(libname, Boolean.TRUE) != Boolean.TRUE) {
                        /*
                         * Support for automatic static linking of standard libraries. This works
                         * because all of the JDK uses System.loadLibrary or
                         * jdk.internal.loader.BootLoader with literal String arguments. If such a
                         * library is in our list of static standard libraries, add the library to
                         * the linker command.
                         */
                        nativeLibraries.addStaticJniLibrary(libname);
                    }
                }
                /*
                 * We never want to do any actual intrinsification, process the original invoke.
                 */
                return false;
            }
        });
    }
}
