/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import java.io.ByteArrayInputStream;
import java.util.Map;

import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompiler;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.OptionsEncoder;
import org.graalvm.compiler.truffle.runtime.hotspot.AbstractHotSpotTruffleRuntime;

import com.oracle.truffle.api.TruffleRuntime;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.services.Services;

/**
 * A {@link TruffleRuntime} that uses libgraal for compilation.
 */
final class LibGraalTruffleRuntime extends AbstractHotSpotTruffleRuntime {

    /**
     * Gets the id for the SVM isolate thread associated with the current thread. This method
     * attaches the current thread to an SVM isolate thread first if necessary.
     *
     * @throws UnsatisfiedLinkError if libgraal is not {@linkplain #isAvailable() available}
     */
    static long getIsolateThreadId() {
        return CURRENT_ISOLATE_THREAD.get();
    }

    private static final ThreadLocal<Long> CURRENT_ISOLATE_THREAD = new ThreadLocal<Long>() {
        @Override
        protected Long initialValue() {
            if (initializationError != null) {
                throw initializationError;
            }
            long isolateThread = HotSpotToSVMCalls.attachThread(isolateId);
            return isolateThread;
        }
    };

    /**
     * Determines if libgraal can be linked.
     */
    static boolean isAvailable() {
        return isolateId != 0L;
    }

    static final long isolateId = initializeLibgraal();
    private static LinkageError initializationError;

    private final long handle;

    LibGraalTruffleRuntime() {
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        MetaAccessProvider metaAccess = runtime.getHostJVMCIBackend().getMetaAccess();
        HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) metaAccess.lookupJavaType(getClass());
        long classLoaderDelegate = runtime.translate(type);
        handle = HotSpotToSVMCalls.initializeRuntime(getIsolateThreadId(), this, classLoaderDelegate);
    }

    @Override
    public HotSpotTruffleCompiler newTruffleCompiler() {
        return new SVMHotSpotTruffleCompiler(HotSpotToSVMCalls.initializeCompiler(getIsolateThreadId(), handle));
    }

    @Override
    protected String initLazyCompilerConfigurationName() {
        return HotSpotToSVMCalls.getCompilerConfigurationFactoryName(getIsolateThreadId());
    }

    /**
     * Gets the isolate pointer for calls of libraal methods. This relies on SVM writes the
     * {@code Isolate*} to {@code JNIInvokeInterface_.reserved0}.
     *
     * @throws UnsatisfiedLinkError if libgraal is not available
     */
    private static long initializeLibgraal() {
        try {
            // Initialize JVMCI to ensure JVMCI opens its packages to
            // Graal otherwise the call to HotSpotJVMCIRuntime.runtime()
            // below will fail on JDK9+.
            Services.initializeJVMCI();

            long[] nativeInterface = HotSpotJVMCIRuntime.runtime().registerNativeMethods(HotSpotToSVMCalls.class);
            return nativeInterface[1];
        } catch (UnsatisfiedLinkError e) {
            initializationError = e;
            return 0L;
        } catch (NoSuchMethodError e) {
            // The signature of HotSpotJVMCIRuntime.registerNativeMethods changed
            // to support libgraal. JDKs that don't have full JVMCI support for
            // libgraal will have the old signature.
            initializationError = e;
            return 0L;
        }
    }

    @Override
    protected Map<String, Object> createInitialOptions() {
        byte[] serializedOptions = HotSpotToSVMCalls.getInitialOptions(getIsolateThreadId(), handle);
        return OptionsEncoder.decode(new ByteArrayInputStream(serializedOptions));
    }

    @Override
    public void log(String message) {
        HotSpotToSVMCalls.log(getIsolateThreadId(), message);
    }

    @Override
    public String getName() {
        String name = super.getName();
        return name + " compiler-native";
    }

    /**
     * Clears JNI GlobalReferences to HotSpot objects held by object on SVM heap. NOTE: This method
     * is called reflectively by Truffle tests.
     */
    @SuppressWarnings("unused")
    private static void cleanNativeReferences() {
        SVMObject.cleanHandles();
        HotSpotToSVMCalls.cleanReferences(getIsolateThreadId());
    }
}
