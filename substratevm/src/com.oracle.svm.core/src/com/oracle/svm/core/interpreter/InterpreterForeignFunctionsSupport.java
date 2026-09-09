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
package com.oracle.svm.core.interpreter;

import java.lang.invoke.MethodHandle;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.graal.code.PreparedSignature;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Runtime bridge for foreign downcalls whose method handles were created by runtime-loaded code.
 */
public interface InterpreterForeignFunctionsSupport {

    String ACCESS_REASON = "Accessed while preparing an interpreter foreign downcall.";

    /** Data pinned by an upcall trampoline and recovered after entering the isolate. */
    interface ForeignUpcallData {
        MethodHandle target();

        ForeignUpcallPlan plan();
    }

    /** Immutable ABI description consumed by the universal Crema upcall entry point. */
    record ForeignUpcallPlan(PreparedSignature signature, int[] preparedReturns) {
        public static final int MAX_RETURN_BUFFER_SIZE = 64;

        public boolean buffersReturn() {
            return preparedReturns != null;
        }
    }

    /**
     * Immutable runtime counterpart of the argument-list part of
     * {@code AbiUtils.adaptToNative}. It is initialized once when the native entry point is created.
     * Its prepared arguments are indexed like the original method-handle arguments; entries with
     * kind {@code void} are not passed to the native function and can encode special stub
     * locations.
     */
    record ForeignDowncallPlan(PreparedSignature signature, int[] preparedReturns, boolean skipsTransition) {

        @Override
        @Uninterruptible(reason = ACCESS_REASON)
        public PreparedSignature signature() {
            return signature;
        }

        @Override
        @Uninterruptible(reason = ACCESS_REASON)
        public int[] preparedReturns() {
            return preparedReturns;
        }

        @Override
        @Uninterruptible(reason = ACCESS_REASON)
        public boolean skipsTransition() {
            return skipsTransition;
        }

        @Uninterruptible(reason = ACCESS_REASON)
        public boolean needsReturnBuffer() {
            return preparedReturns != null;
        }

    }

    @Fold
    static boolean isAvailable() {
        return ImageSingletons.contains(InterpreterForeignFunctionsSupport.class);
    }

    @Fold
    static InterpreterForeignFunctionsSupport singleton() {
        return ImageSingletons.lookup(InterpreterForeignFunctionsSupport.class);
    }

    Object linkToNative(ForeignDowncallPlan plan, Object[] arguments, int captureMask);

    CFunctionPointer getUpcallStubPointer();

    void setUpcallStubPointer(CFunctionPointer pointer);
}
