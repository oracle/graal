/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.libgraal.truffle;

import java.util.function.Supplier;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilerListener.CompilationResultInfo;
import com.oracle.truffle.compiler.TruffleCompilerListener.GraphInfo;
import com.oracle.truffle.compiler.TruffleCompilerOptionDescriptor;

import jdk.graal.compiler.hotspot.CompilationContext;
import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.graal.compiler.truffle.TruffleCompilerOptions;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilationSupport;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilerImpl;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Guest Graal entry points for Truffle to libgraal calls.
 */
public final class GraalEntryPoints {

    private GraalEntryPoints() {
    }

    // HotSpot to libgraal entry points

    public static void initializeIsolate() {
        TruffleLibGraalShutdownHook.registerShutdownHook();
    }

    public static boolean registerRuntime(long truffleRuntimeWeakRef) {
        return LibGraalTruffleHostEnvironmentLookup.registerRuntime(truffleRuntimeWeakRef);
    }

    public static Object initializeRuntime(Object hsHandle, long hsClassLoaderDelegate) {
        return new HSTruffleCompilerRuntime(hsHandle, hsClassLoaderDelegate);
    }

    public static Object newCompiler(Object truffleCompilerRuntime) {
        /*
         * Unlike `LibGraalTruffleHostEnvironment`, Truffle libgraal entry points use the global
         * compilation context by default, so we don't need to call
         * `HotSpotGraalServices.enterGlobalCompilationContext()` before creating
         * `TruffleCompilerImpl`. The `doCompile` method enters a local compilation context through
         * its own call to `HotSpotGraalServices.openLocalCompilationContext`.
         */
        return HotSpotTruffleCompilerImpl.create((HSTruffleCompilerRuntime) truffleCompilerRuntime, null);
    }

    public static void initializeCompiler(Object compiler, Object compilableHsHandle, boolean firstInitialization) {
        HotSpotTruffleCompilerImpl truffleCompiler = (HotSpotTruffleCompilerImpl) compiler;
        TruffleCompilable compilable = new HSTruffleCompilable(compilableHsHandle);
        truffleCompiler.initialize(compilable, firstInitialization);
    }

    public static String getCompilerConfigurationFactoryName() {
        return HotSpotTruffleCompilationSupport.getLazyCompilerConfigurationName();
    }

    @SuppressWarnings("try")
    public static void doCompile(Object compiler, Object taskHsHandle, Object compilableHsHandle, Object listenerHsHandle) {
        HotSpotTruffleCompilerImpl truffleCompiler = (HotSpotTruffleCompilerImpl) compiler;
        HSTruffleCompilationTask task = taskHsHandle == null ? null : new HSTruffleCompilationTask(taskHsHandle);
        HSTruffleCompilerListener listener = listenerHsHandle == null ? null : new HSTruffleCompilerListener(listenerHsHandle);
        HSTruffleCompilable compilable = new HSTruffleCompilable(compilableHsHandle);
        try (CompilationContext hotSpotObjectConstantScope = HotSpotGraalServices.openLocalCompilationContext(compilable)) {
            truffleCompiler.doCompile(task, compilable, listener);
        }
    }

    public static void shutdown(Object compiler) {
        ((HotSpotTruffleCompilerImpl) compiler).shutdown();
    }

    public static void installTruffleCallBoundaryMethod(Object compiler, long methodHandle) {
        HotSpotTruffleCompilerImpl truffleCompiler = (HotSpotTruffleCompilerImpl) compiler;
        truffleCompiler.installTruffleCallBoundaryMethod(HotSpotJVMCIRuntime.runtime().unhand(ResolvedJavaMethod.class, methodHandle), null);
    }

    public static void installTruffleReservedOopMethod(Object compiler, long methodHandle) {
        HotSpotTruffleCompilerImpl truffleCompiler = (HotSpotTruffleCompilerImpl) compiler;
        truffleCompiler.installTruffleReservedOopMethod(HotSpotJVMCIRuntime.runtime().unhand(ResolvedJavaMethod.class, methodHandle), null);
    }

    public static int pendingTransferToInterpreterOffset(Object compiler, Object compilableHsHandle) {
        HotSpotTruffleCompilerImpl truffleCompiler = (HotSpotTruffleCompilerImpl) compiler;
        TruffleCompilable compilable = new HSTruffleCompilable(compilableHsHandle);
        return truffleCompiler.pendingTransferToInterpreterOffset(compilable);
    }

    @SuppressWarnings("unchecked")
    public static String getSuppliedString(Object stringSupplier) {
        return ((Supplier<String>) stringSupplier).get();
    }

    public static int getNodeCount(Object graphInfo) {
        return ((GraphInfo) graphInfo).getNodeCount();
    }

    public static String[] getNodeTypes(Object graphInfo, boolean simpleNames) {
        return ((GraphInfo) graphInfo).getNodeTypes(simpleNames);
    }

    public static long getCompilationId(Object compilationResultInfo) {
        return ((CompilationResultInfo) compilationResultInfo).getCompilationId();
    }

    public static int getTargetCodeSize(Object compilationResultInfo) {
        return ((CompilationResultInfo) compilationResultInfo).getTargetCodeSize();
    }

    public static int getTotalFrameSize(Object compilationResultInfo) {
        return ((CompilationResultInfo) compilationResultInfo).getTotalFrameSize();
    }

    public static int getExceptionHandlersCount(Object compilationResultInfo) {
        return ((CompilationResultInfo) compilationResultInfo).getExceptionHandlersCount();
    }

    public static int getInfopointsCount(Object compilationResultInfo) {
        return ((CompilationResultInfo) compilationResultInfo).getInfopointsCount();
    }

    public static String[] getInfopoints(Object compilationResultInfo) {
        return ((CompilationResultInfo) compilationResultInfo).getInfopoints();
    }

    public static int getMarksCount(Object compilationResultInfo) {
        return ((CompilationResultInfo) compilationResultInfo).getMarksCount();
    }

    public static int getDataPatchesCount(Object compilationResultInfo) {
        return ((CompilationResultInfo) compilationResultInfo).getDataPatchesCount();
    }

    public static Object[] listCompilerOptions() {
        TruffleCompilerOptionDescriptor[] options = TruffleCompilerOptions.listOptions();
        Object[] result = new Object[options.length];
        for (int i = 0; i < options.length; i++) {
            TruffleCompilerOptionDescriptor option = options[i];
            result[i] = NativeImageHostCalls.createTruffleCompilerOptionDescriptor(option.name(), option.type().ordinal(), option.deprecated(), option.help(), option.deprecationMessage());
        }
        return result;
    }

    public static boolean compilerOptionExists(String optionName) {
        return TruffleCompilerOptions.optionExists(optionName);
    }

    public static String validateCompilerOption(String optionName, String optionValue) {
        return TruffleCompilerOptions.validateOption(optionName, optionValue);
    }

    public static void purgePartialEvaluationCaches(Object compiler) {
        ((HotSpotTruffleCompilerImpl) compiler).purgePartialEvaluationCaches();
    }

    public static String getCompilerVersion() {
        return HSTruffleCompilerRuntime.COMPILER_VERSION;
    }

    public static long getCurrentJavaThread() {
        return HotSpotJVMCIRuntime.runtime().getCurrentJavaThread();
    }

    public static int getLastJavaPCOffset() {
        HotSpotVMConfigAccess configAccess = new HotSpotVMConfigAccess(HotSpotJVMCIRuntime.runtime().getConfigStore());
        int anchor = configAccess.getFieldOffset("JavaThread::_anchor", Integer.class, "JavaFrameAnchor");
        int lastJavaPc = configAccess.getFieldOffset("JavaFrameAnchor::_last_Java_pc", Integer.class, "address");
        return anchor + lastJavaPc;
    }
}
