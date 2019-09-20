/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common;

/**
 * A listener for events related to the compilation of a {@link CompilableTruffleAST}. The events
 * are described only in terms of types that can be easily serialized or proxied across a heap
 * boundary.
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
    void onGraalTierFinished(CompilableTruffleAST compilable, GraphInfo graph);

    /**
     * Notifies this object when compilation of {@code compilable} has completed partial evaluation
     * and is about to perform compilation of the graph produced by partial evaluation.
     *
     * @param compilable the call target being compiled
     * @param inliningPlan the inlining plan used during partial evaluation
     * @param graph the graph representing {@code compilable}. The {@code graph} object is only
     *            valid for the lifetime of a call to this method. Invoking any {@link GraphInfo}
     *            method on {@code graph} after this method returns will result in an
     *            {@link IllegalStateException}.
     */
    void onTruffleTierFinished(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graph);

    /**
     * Notifies this object when compilation of {@code compilable} succeeds.
     *
     * @param compilable the Truffle AST whose compilation succeeded
     * @param inliningPlan the inlining plan used during partial evaluation
     * @param graph the graph representing {@code compilable}. The {@code graph} object is only
     *            valid for the lifetime of a call to this method. Invoking any {@link GraphInfo}
     *            method on {@code graph} after this method returns will result in an
     *            {@link IllegalStateException}.
     * @param compilationResultInfo the result of a compilation. The {@code compilationResultInfo}
     *            object is only valid for the lifetime of a call to this method. Invoking any
     *            {@link CompilationResultInfo} method on {@code compilationResultInfo} after this
     *            method returns will result in an {@link IllegalStateException}.
     */
    void onSuccess(CompilableTruffleAST compilable, TruffleInliningPlan inliningPlan, GraphInfo graph, CompilationResultInfo compilationResultInfo);

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
     */
    void onFailure(CompilableTruffleAST compilable, String reason, boolean bailout, boolean permanentBailout);
}
