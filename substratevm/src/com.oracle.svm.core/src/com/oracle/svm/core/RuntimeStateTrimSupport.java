/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import static com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import java.util.Objects;

import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import jdk.graal.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.RuntimeStateTrimConfig;
import org.graalvm.nativeimage.RuntimeStateTrimCallbackException;
import org.graalvm.nativeimage.RuntimeStateTrimCallbackException.Phase;
import org.graalvm.nativeimage.RuntimeStateTrimConfig.Mode;

import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.VMOperationInfos;

/**
 * Support for executing runtime-state trim at a safepoint.
 *
 * <p>
 * The optimization runs through a dedicated VM operation so that the runtime can apply the selected
 * {@link Mode mode}-driven actions while all other application
 * threads are suspended.
 */
@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = BuiltinTraits.AllAccess.class, layeredCallbacks = BuiltinTraits.SingleLayer.class, layeredInstallationKind = SingletonLayeredInstallationKind.InitialLayerOnly.class)
public final class RuntimeStateTrimSupport {
    private static final String MUST_NOT_MODIFY_JAVA_HEAP_STRUCTURE = "Must not modify the structure of the Java heap.";

    @Fold
    public static RuntimeStateTrimSupport singleton() {
        return ImageSingletons.lookup(RuntimeStateTrimSupport.class);
    }

    /**
     * Enqueues a safepoint VM operation that applies the runtime-state trim policy selected
     * by {@code config}.
     *
     * <p>
     * The operation invokes the configured callbacks around the optimization work and performs the
     * GC and heap cleanup actions implied by the selected mode.
     *
     * @param config the runtime-state trim configuration
     * @throws NullPointerException if {@code config} is null
     * @throws UnsupportedOperationException if the selected mode is not supported by the configured
     *             garbage collector
     */
    @SuppressWarnings("static-method")
    public void trimRuntimeState(RuntimeStateTrimConfig config) {
        Objects.requireNonNull(config, "Config must be non null");

        Mode mode = config.mode();
        Heap heap = Heap.getHeap();
        if (!heap.isRuntimeStateTrimSupported(mode)) {
            throw new UnsupportedOperationException("Runtime-state trim mode '" + mode + "' is not supported by the " + heap.getGC().getName() + ".");
        }

        RuntimeStateTrimOperation operation = new RuntimeStateTrimOperation(config);
        operation.enqueue();
        if (operation.exception != null) {
            throw rethrow(operation.exception);
        }
        int callbackFailure = operation.callbackFailure;
        if (callbackFailure != 0) {
            assert operation.callbackFailurePhase != null;
            throw new RuntimeStateTrimCallbackException(operation.callbackFailurePhase, callbackFailure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException rethrow(Throwable exception) throws E {
        throw (E) exception;
    }

    private static final class RuntimeStateTrimOperation extends JavaVMOperation {
        private final NoAllocationVerifier noAllocationVerifier;
        private final RuntimeStateTrimConfig config;
        private Throwable exception;
        private int callbackFailure;
        private Phase callbackFailurePhase;

        RuntimeStateTrimOperation(RuntimeStateTrimConfig config) {
            super(VMOperationInfos.get(RuntimeStateTrimOperation.class, "Runtime-state trim", SystemEffect.SAFEPOINT));
            this.noAllocationVerifier = NoAllocationVerifier.factory("RuntimeStateTrimOperation", false);
            this.config = config;
        }

        @Override
        @RestrictHeapAccess(access = NO_ALLOCATION, reason = MUST_NOT_MODIFY_JAVA_HEAP_STRUCTURE)
        protected void operate() {
            NoAllocationVerifier nav = noAllocationVerifier.open();
            try {
                callbackFailure = trim();
            } catch (Throwable t) {
                this.exception = t;
            } finally {
                nav.close();
            }
        }

        private int trim() {
            if (config.beforeRuntimeStateTrim().isNonNull()) {
                int status = config.beforeRuntimeStateTrim().invoke();
                if (status != 0) {
                    callbackFailurePhase = Phase.BEFORE;
                    return status;
                }
            }
            Heap.getHeap().trimRuntimeState(config.mode());
            if (config.afterRuntimeStateTrim().isNonNull()) {
                int status = config.afterRuntimeStateTrim().invoke();
                if (status != 0) {
                    callbackFailurePhase = Phase.AFTER;
                    return status;
                }
            }
            return 0;
        }

    }
}
