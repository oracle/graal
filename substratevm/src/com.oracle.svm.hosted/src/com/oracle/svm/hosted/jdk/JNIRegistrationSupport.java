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

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/** Registration of native JDK libraries. */
@Platforms(InternalPlatform.PLATFORM_JNI.class)
@AutomaticFeature
class JNIRegistrationSupport implements GraalFeature {

    private final ConcurrentMap<String, Boolean> registeredLibraries = new ConcurrentHashMap<>();
    private NativeLibraries nativeLibraries = null;

    public static JNIRegistrationSupport singleton() {
        return ImageSingletons.lookup(JNIRegistrationSupport.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        nativeLibraries = ((BeforeAnalysisAccessImpl) access).getNativeLibraries();
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, Plugins plugins, ParsingReason reason) {
        registerLoadLibraryPlugin(plugins, System.class);
    }

    void registerLoadLibraryPlugin(Plugins plugins, Class<?> clazz) {
        Registration r = new Registration(plugins.getInvocationPlugins(), clazz);
        r.register1("loadLibrary", String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode libnameNode) {
                /*
                 * Support for automatic discovery of standard JDK libraries. This works because all
                 * of the JDK uses System.loadLibrary or jdk.internal.loader.BootLoader with literal
                 * String arguments.
                 */
                if (libnameNode.isConstant()) {
                    String libname = (String) SubstrateObjectConstant.asObject(libnameNode.asConstant());
                    if (libname != null && registeredLibraries.putIfAbsent(libname, Boolean.TRUE) != Boolean.TRUE) {
                        /*
                         * If a library is in our list of static standard libraries, add the library
                         * to the linker command.
                         */
                        if (NativeLibrarySupport.singleton().isPreregisteredBuiltinLibrary(libname)) {
                            nativeLibraries.addStaticJniLibrary(libname);
                        }
                    }
                }
                /* We never want to do any actual intrinsification, process the original invoke. */
                return false;
            }
        });
    }
}
