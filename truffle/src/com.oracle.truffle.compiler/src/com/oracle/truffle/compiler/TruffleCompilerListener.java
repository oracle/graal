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

import java.util.function.Supplier;

/**
 * A listener for events related to the compilation of a {@link TruffleCompilable}. The events are
 * described only in terms of types that can be easily serialized or proxied across a heap boundary.
 */
public interface TruffleCompilerListener {

    /**
     * Summary information for a compiler graph.
     */
    interface GraphInfo {
        /**
         * Gets the number of nodes in the graph.
         */
        int getNodeCount();

        /**
         * Gets the set of nodes in the graph.
         *
         * @param simpleNames whether to return {@linkplain Class#getSimpleName() simple} names
         * @return list of type names for all the nodes in the graph
         */
        String[] getNodeTypes(boolean simpleNames);
    }

    /**
     * Summary information for the result of a compilation.
     */
    interface CompilationResultInfo {
        /**
         * Gets the size of the machine code generated.
         */
        int getTargetCodeSize();

        /**
         * Gets the total frame size of compiled code in bytes. This includes the return address
         * pushed onto the stack, if any.
         */
        int getTotalFrameSize();

        /**
         * Gets the number of {@code ExceptionHandler}s in the compiled code.
         */
        int getExceptionHandlersCount();

        /**
         * Gets the number of {@code Infopoint}s in the compiled code.
         */
        int getInfopointsCount();

        /**
         * Gets the infopoint reasons in the compiled code.
         */
        String[] getInfopoints();

        /**
         * Gets the number of {@code Mark}s in the compiled code.
         */
        int getMarksCount();

        /**
         * Gets the number of {@code DataPatch}es in the compiled code.
         */
        int getDataPatchesCount();
    }

    /**
     * Notifies this object when Graal IR compilation {@code compilable} completes. Graal
     * compilation occurs between {@link #onTruffleTierFinished} and code installation.
     *
     * @param compilable the call target that was compiled
     * @param graph the graph representing {@code compilable}. The {@code graph} object is only
     *            valid for the lifetime of a call to this method. Invoking any {@link GraphInfo}
     *            method on {@code graph} after this method returns will result in an
     *            {@link IllegalStateException}.
     */
    void onGraalTierFinished(TruffleCompilable compilable, GraphInfo graph);

    /**
     * Notifies this object when compilation of {@code compilable} has completed partial evaluation
     * and is about to perform compilation of the graph produced by partial evaluation.
     *
     * @param compilable the call target being compiled
     * @param task the compilation task
     * @param graph the graph representing {@code compilable}. The {@code graph} object is only
     *            valid for the lifetime of a call to this method. Invoking any {@link GraphInfo}
     *            method on {@code graph} after this method returns will result in an
     *            {@link IllegalStateException}.
     */
    void onTruffleTierFinished(TruffleCompilable compilable, TruffleCompilationTask task, GraphInfo graph);

    /**
     * Notifies this object when compilation of {@code compilable} succeeds.
     *
     * @param compilable the Truffle AST whose compilation succeeded
     * @param task the compilation task
     * @param graph the graph representing {@code compilable}. The {@code graph} object is only
     *            valid for the lifetime of a call to this method. Invoking any {@link GraphInfo}
     *            method on {@code graph} after this method returns will result in an
     *            {@link IllegalStateException}.
     * @param compilationResultInfo the result of a compilation. The {@code compilationResultInfo}
     *            object is only valid for the lifetime of a call to this method. Invoking any
     *            {@link CompilationResultInfo} method on {@code compilationResultInfo} after this
     *            method returns will result in an {@link IllegalStateException}.
     * @param tier Which compilation tier was the compilation
     */
    default void onSuccess(TruffleCompilable compilable, TruffleCompilationTask task, GraphInfo graph, CompilationResultInfo compilationResultInfo, int tier) {
    }

    /**
     * Notifies this object when compilation of {@code compilable} fails.
     *
     * @param compilable the Truffle AST whose compilation failed
     * @param reason the reason compilation failed
     * @param bailout specifies whether the failure was a bailout or an error in the compiler. A
     *            bailout means the compiler aborted the compilation based on some of property of
     *            {@code target} (e.g., too big). A non-bailout means an unexpected error in the
     *            compiler itself.
     * @param permanentBailout specifies if a bailout is due to a condition that probably won't
     *            change if the {@code target} is compiled again. This value is meaningless if
     *            {@code bailout == false}.
     * @param tier Which compilation tier was the compilation
     * @deprecated use
     *             {@link #onFailure(TruffleCompilable, String, boolean, boolean, int, Supplier)}
     */
    @Deprecated(since = "24.1")
    default void onFailure(TruffleCompilable compilable, String reason, boolean bailout, boolean permanentBailout, int tier) {
        onFailure(compilable, reason, bailout, permanentBailout, tier, null);
    }

    /**
     * Notifies this object when compilation of {@code compilable} fails.
     *
     * @param compilable the Truffle AST whose compilation failed
     * @param reason the reason compilation failed
     * @param bailout specifies whether the failure was a bailout or an error in the compiler. A
     *            bailout means the compiler aborted the compilation based on some of property of
     *            {@code target} (e.g., too big). A non-bailout means an unexpected error in the
     *            compiler itself.
     * @param permanentBailout specifies if a bailout is due to a condition that probably won't
     *            change if the {@code target} is compiled again. This value is meaningless if
     *            {@code bailout == false}.
     * @param tier Which compilation tier was the compilation
     * @param serializedException a serialized representation of the exception indicating the reason
     *            and stack trace for a compilation failure, or {@code null} in the case of a
     *            bailout or when the compiler does not provide a stack trace. See
     *            {@link TruffleCompilable#serializeException(Throwable)}.
     *
     */
    default void onFailure(TruffleCompilable compilable, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> serializedException) {
    }

    /**
     * Notifies this object when compilation of {@code compilable} is re-tried to diagnose a
     * compilation problem.
     *
     * @param compilable the Truffle AST which is going to be re-compiled.
     * @param task Which compilation task is in question.
     */
    default void onCompilationRetry(TruffleCompilable compilable, TruffleCompilationTask task) {
    }
}
