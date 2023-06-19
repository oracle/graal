/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.compiler;

import java.util.Collections;
import java.util.Map;

import jdk.vm.ci.meta.JavaConstant;

/**
 * A handle to a compilation task managed by the Truffle runtime.
 */
public interface TruffleCompilationTask {
    /**
     * Determines if this compilation has been cancelled.
     */
    boolean isCancelled();

    /**
     * Returns {@code true} if this is a last tier compilation.
     */
    boolean isLastTier();

    /**
     * Returns {@code true} if this is a first tier compilation.
     */
    default boolean isFirstTier() {
        return !isLastTier();
    }

    default int tier() {
        return isFirstTier() ? 1 : 2;
    }

    boolean hasNextTier();

    /**
     * If {@code node} represents an AST Node then return the nearest source information for it.
     * Otherwise simply return null.
     */
    // TODO GR-44222 move this to CompilableTruffleAST
    @SuppressWarnings("unused")
    default TruffleSourceLanguagePosition getPosition(JavaConstant node) {
        return null;
    }

    /**
     * Returns the debug properties of a truffle node constant or an empty map if there are no debug
     * properties.
     */
    // TODO GR-44222 move this to CompilableTruffleAST
    @SuppressWarnings("unused")
    default Map<String, Object> getDebugProperties(JavaConstant node) {
        return Collections.emptyMap();
    }

    /**
     * Records the given target to be dequeued from the compilation queue at the end of the current
     * compilation.
     */
    @SuppressWarnings("unused")
    default void addTargetToDequeue(TruffleCompilable target) {
        // not supported -> do nothing
    }

    /**
     * To be used from the compiler side. Sets how many calls in total are in the related
     * compilation unit, and how many of those were inlined.
     */
    @SuppressWarnings("unused")
    default void setCallCounts(int total, int inlined) {
        // not supported -> discard
    }

    /**
     * To be used from the compiler side.
     *
     * @param target register this target as inlined.
     */
    default void addInlinedTarget(TruffleCompilable target) {
        // not supported -> discard
    }

}
