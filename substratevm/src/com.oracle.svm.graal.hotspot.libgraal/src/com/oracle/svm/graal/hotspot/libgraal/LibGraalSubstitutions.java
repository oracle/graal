/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal;

import java.lang.ref.ReferenceQueue;
import java.util.Map;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.log.FunctionPointerLogHandler;
import com.oracle.svm.core.util.VMError;
import jdk.graal.compiler.serviceprovider.VMSupport;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.services.Services;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.impl.IsolateSupport;
import org.graalvm.word.WordFactory;

/**
 * Contains support code for the substitutions declared in this file.
 */
public final class LibGraalSubstitutions {

    static long jniEnvironmentOffset = Integer.MAX_VALUE;

    static long getJniEnvironmentOffset() {
        if (jniEnvironmentOffset == Integer.MAX_VALUE) {
            HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
            HotSpotVMConfigStore store = jvmciRuntime.getConfigStore();
            HotSpotVMConfigAccess config = new HotSpotVMConfigAccess(store);
            jniEnvironmentOffset = config.getFieldOffset("JavaThread::_jni_environment", Integer.class, "JNIEnv");
        }
        return jniEnvironmentOffset;
    }
}

@TargetClass(value = Services.class, onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_vm_ci_services_Services {
    // Checkstyle: stop
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true)//
    public static boolean IS_IN_NATIVE_IMAGE = true;
    // Checkstyle: resume

    /*
     * Ideally, the original field should be annotated with @NativeImageReinitialize. But that
     * requires a larger JVMCI change because the annotation is not visible in that project, so we
     * use this substitution instead.
     */
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Map<String, String> savedProperties;
}

@TargetClass(className = "jdk.vm.ci.hotspot.Cleaner", onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_vm_ci_hotspot_Cleaner {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, isFinal = true, declClass = ReferenceQueue.class)//
    private static ReferenceQueue<Object> queue;

    @Alias
    static native void clean();
}

@TargetClass(value = VMSupport.class, onlyWith = LibGraalFeature.IsEnabled.class)
final class Target_jdk_graal_compiler_serviceprovider_VMSupport {

    @Substitute
    public static long getIsolateAddress() {
        return CurrentIsolate.getIsolate().rawValue();
    }

    @Substitute
    public static long getIsolateID() {
        return ImageSingletons.lookup(IsolateSupport.class).getIsolateID();
    }

    /**
     * Performs the following actions around a libgraal compilation:
     * <ul>
     * <li>before: opens a JNIMethodScope to allow Graal compilations of Truffle host methods to
     * call methods on the TruffleCompilerRuntime.</li>
     * <li>after: closes the above JNIMethodScope</li>
     * <li>after: triggers GC weak reference processing as SVM does not use a separate thread for
     * this in libgraal</li>
     * </ul>
     */
    static class LibGraalCompilationRequestScope implements AutoCloseable {
        final JNIMethodScope scope;

        LibGraalCompilationRequestScope() {
            HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
            long offset = LibGraalSubstitutions.getJniEnvironmentOffset();
            long javaThreadAddr = jvmciRuntime.getCurrentJavaThread();
            JNI.JNIEnv env = (JNI.JNIEnv) WordFactory.unsigned(javaThreadAddr).add(WordFactory.unsigned(offset));
            // This scope is required to allow Graal compilations of host methods to call methods
            // on the TruffleCompilerRuntime. This is, for example, required to find out about
            // Truffle-specific method annotations.
            scope = LibGraalUtil.openScope("<called from VM>", env);
        }

        @Override
        public void close() {
            try {
                scope.close();
            } finally {
                /*
                 * libgraal doesn't use a dedicated reference handler thread, so we trigger the
                 * reference handling manually when a compilation finishes.
                 */
                LibGraalEntryPoints.doReferenceHandling();
            }
        }
    }

    @Substitute
    public static AutoCloseable getCompilationRequestScope() {
        return new LibGraalCompilationRequestScope();
    }

    @Substitute
    public static void fatalError(String message, int delayMS) {
        LogHandler handler = ImageSingletons.lookup(LogHandler.class);
        if (handler instanceof FunctionPointerLogHandler) {
            try {
                Thread.sleep(delayMS);
            } catch (InterruptedException e) {
                // ignore
            }
            VMError.shouldNotReachHere(message);
        }
    }

    @Substitute
    public static void startupLibGraal() {
        VMRuntime.initialize();
    }

    @Substitute
    public static void shutdownLibGraal() {
        VMRuntime.shutdown();
    }

    @Substitute
    public static void invokeShutdownCallback(String cbClassName, String cbMethodName) {
        long offset = LibGraalSubstitutions.getJniEnvironmentOffset();
        long javaThreadAddr = HotSpotJVMCIRuntime.runtime().getCurrentJavaThread();
        JNI.JNIEnv env = (JNI.JNIEnv) WordFactory.unsigned(javaThreadAddr).add(WordFactory.unsigned(offset));
        JNI.JClass cbClass = JNIUtil.findClass(env, JNIUtil.getSystemClassLoader(env),
                        JNIUtil.getBinaryName(cbClassName), true);
        JNI.JMethodID cbMethod = JNIUtil.findMethod(env, cbClass, true, cbMethodName, "()V");
        env.getFunctions().getCallStaticVoidMethodA().call(env, cbClass, cbMethod, StackValue.get(0));
        JNIExceptionWrapper.wrapAndThrowPendingJNIException(env);
    }

    @Substitute
    public static void notifyLowMemoryPoint(boolean hintFullGC, boolean forceFullGC) {
        if (forceFullGC) {
            Heap.getHeap().getGC().collectCompletely(GCCause.JavaLangSystemGC);
        } else {
            Heap.getHeap().getGC().collectionHint(hintFullGC);
        }
        LibGraalEntryPoints.doReferenceHandling();
    }
}
