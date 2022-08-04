/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.features;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.standalone.MethodConfigReader;
import com.oracle.graal.pointsto.standalone.StandalonePointsToAnalysis;
import com.oracle.graal.pointsto.standalone.features.StandaloneAnalysisFeatureImpl.BeforeAnalysisAccessImpl;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.hosted.Feature;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VMInitItemsFeature implements Feature {
    public static class Options {
        // @formatter:off
        // see {@link #registerVMInvokedMethodsAsRoot(BeforeAnalysisAccessImpl, BigBang)}
        @Option(help = "Register system level vm invoked methods as root for system level analysis in standalone " +
                       "pointsto analysis, " +
                       "in default only basic level methods registered as root for application level analysis",
                       type = OptionType.User)
        public static final OptionKey<Boolean> IncludingSystemInited = new OptionKey<>(false);
        // @formatter:on
    }

    // see {@link #registerVMInvokedMethodsAsRoot(BeforeAnalysisAccessImpl, BigBang)}
    private static final String[] BASIC_LEVEL_VM_INVOKE_METHODS = {
                    "java.lang.ClassLoader.addClass",
                    "java.lang.ClassLoader.checkPackageAccess",
                    "java.lang.ClassLoader.findNative",
                    "java.lang.ClassLoader.getPlatformClassLoader",
                    "java.lang.ClassLoader.getSystemClassLoader",
                    "java.lang.ClassLoader.loadClass",
                    "java.lang.System.initPhase1",
                    "java.lang.System.initPhase3",
                    "java.lang.ThreadGroup.add",
                    "java.util.Properties.getProperty",
                    "java.util.Properties.put"
    };
    // see {@link #registerVMInvokedMethodsAsRoot(BeforeAnalysisAccessImpl, BigBang)}
    private static final String[] SYSTEM_LEVEL_VM_INVOKE_METHODS = {
                    "java.lang.Shutdown.shutdown",
                    "java.lang.System.getProperty",
                    "java.lang.System.initPhase2",
                    "java.lang.Thread.exit",
                    "sun.launcher.LauncherHelper.checkAndLoadMain",
                    "sun.launcher.LauncherHelper.getApplicationClass",
                    "sun.launcher.LauncherHelper.makePlatformString"
    };

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        StandalonePointsToAnalysis bigbang = (StandalonePointsToAnalysis) access.getBigBang();
        registerVMInitFieldsAsRoot(bigbang);
        registerVMInvokedMethodsAsRoot(access, bigbang);
    }

    private static void registerVMInitFieldsAsRoot(StandalonePointsToAnalysis bigbang) {
        bigbang.addRootStaticField(System.class, "out").registerAsInHeap("VM initiated field.");
        bigbang.addRootStaticField(System.class, "err").registerAsInHeap("VM initiated field.");
        bigbang.addRootField(PrintStream.class, "textOut").registerAsInHeap("VM initiated field.");
        bigbang.addRootField(PrintStream.class, "charOut").registerAsInHeap("VM initiated field.");
    }

    /*
     * Some JDK methods, which are invoked only by VM, need to be registered as root methods before
     * static analysis. Those methods could be devided into two levels according to runtime
     * dependent relationship, basic level methods (listed in {@link
     * #BASIC_LEVEL_VM_INVOKE_METHODS}) and system level methods (listed in {@link
     * #SYSTEM_LEVEL_VM_INVOKE_METHODS}). If the analysis concentrates on application, only basic
     * level methods are needed because they contains all of methods depended by application. And if
     * the analysis concentrate on the full system, except for basic level methods, system level
     * methods are also needed to make sure the comprehensiveness of analysis.
     */
    private static void registerVMInvokedMethodsAsRoot(BeforeAnalysisAccessImpl access, BigBang bigbang) {
        List<String> finalVMInvokedMethodList = new ArrayList<>();
        finalVMInvokedMethodList.addAll(Arrays.asList(BASIC_LEVEL_VM_INVOKE_METHODS));
        if (Options.IncludingSystemInited.getValue(bigbang.getOptions())) {
            finalVMInvokedMethodList.addAll(Arrays.asList(SYSTEM_LEVEL_VM_INVOKE_METHODS));
        }
        ClassLoader classLoader = access.getApplicationClassLoader();
        MethodConfigReader.forMethodList(bigbang.getDebug(), finalVMInvokedMethodList, bigbang, classLoader, m -> bigbang.addRootMethod(m, true));
    }
}
