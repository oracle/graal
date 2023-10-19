/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import java.lang.reflect.Field;

import jdk.compiler.graal.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.SubstrateControlFlowIntegrity;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
public class ContinuationsFeature implements InternalFeature {
    private boolean finishedRegistration = false;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        final int firstLoomPreviewVersion = 19;
        final int lastLoomPreviewVersion = 20;

        boolean supportLoom = false;
        if (JavaVersionUtil.JAVA_SPEC >= firstLoomPreviewVersion) {
            boolean haveLoom;
            if (JavaVersionUtil.JAVA_SPEC > lastLoomPreviewVersion) {
                haveLoom = true;
            } else {
                try {
                    haveLoom = (Boolean) Class.forName("jdk.internal.misc.PreviewFeatures")
                                    .getDeclaredMethod("isEnabled").invoke(null);
                } catch (ReflectiveOperationException e) {
                    throw VMError.shouldNotReachHere(e);
                }
                if (!haveLoom) {
                    // Defer: can get initialized and fail the image build despite substitution
                    RuntimeClassInitialization.initializeAtRunTime("jdk.internal.vm.Continuation");
                }
            }
            supportLoom = haveLoom && !DeoptimizationSupport.enabled() && !SubstrateOptions.useLLVMBackend() && !SubstrateControlFlowIntegrity.enabled();
        }

        /*
         * Note: missing support for Loom due to preview features being off, runtime compilation, or
         * the LLVM backend is reported at runtime to allow probing without failing the image build.
         */
        if (supportLoom) {
            LoomVirtualThreads vt = new LoomVirtualThreads();
            ImageSingletons.add(VirtualThreads.class, vt);
            ImageSingletons.add(LoomVirtualThreads.class, vt); // for simpler check in LoomSupport
        }
        finishedRegistration = true;
    }

    boolean hasFinishedRegistration() {
        return finishedRegistration;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (Continuation.isSupported()) {
            if (!ImageSingletons.contains(ContinuationSupport.class)) {
                ImageSingletons.add(ContinuationSupport.class, new ContinuationSupport());
            }

            Field ipField = ReflectionUtil.lookupField(StoredContinuation.class, "ip");
            access.registerAsAccessed(ipField);

            access.registerReachabilityHandler(a -> access.registerAsInHeap(StoredContinuation.class),
                            ReflectionUtil.lookupMethod(StoredContinuationAccess.class, "allocate", int.class));
        } else {
            access.registerReachabilityHandler(a -> abortIfUnsupported(), StoredContinuationAccess.class);
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (Continuation.isSupported()) {
            Field ipField = ReflectionUtil.lookupField(StoredContinuation.class, "ip");
            long offset = access.objectFieldOffset(ipField);
            ContinuationSupport.singleton().setIPOffset(offset);
        }
    }

    static void abortIfUnsupported() {
        VMError.guarantee(Continuation.isSupported(), "Virtual threads internals are reachable but support is not available or active.");
    }
}
