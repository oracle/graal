/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.truffle.compiler;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;

/**
 * A Graal compiler level listener for Truffle compilation events. This listener exposes events in
 * terms the intermediate representation and pipeline of the compiler. On the other hand, it does
 * not expose the details of Truffle {@code Node}s.
 */
public interface GraalCompilationListener {

    /**
     * Notifies this object when compilation of a call target failed.
     *
     * @param target the call target whose compilation failed
     * @param graph the graph representing {@code target}
     * @param t the reason for the failure
     */
    default void onCompilationFailed(CompilableTruffleAST target, StructuredGraph graph, Throwable t) {
    }

    /**
     * Notifies this object when compilation of a call target starts.
     *
     * @param target the call target whose compilation is starting
     */
    default void onCompilationStarted(CompilableTruffleAST target) {
    }

    /**
     * Notifies this object when Truffle partial evaluation of a call target completes.
     *
     * @param target the call target that was partially evaluated
     * @param graph the graph representing {@code target} produced by partial evaluation
     */
    default void onCompilationTruffleTierFinished(CompilableTruffleAST target, StructuredGraph graph) {
    }

    /**
     * Notifies this object when Graal compilation of a call target completes. Graal compilation
     * occurs between {@link #onCompilationTruffleTierFinished} and code installation.
     *
     * @param target the call target that was compiled
     * @param graph the graph representing {@code target}
     */
    default void onCompilationGraalTierFinished(CompilableTruffleAST target, StructuredGraph graph) {
    }

    /**
     * Notifies this object when code has been successfully compiled and installed for a call
     * target.
     *
     * @param target the call target that was compiled
     * @param graph the graph representing {@code target}
     * @param result the result of the successful compilation
     */
    default void onCompilationSuccess(CompilableTruffleAST target, StructuredGraph graph, CompilationResult result) {
    }

    /**
     * Invoked when the compiler is being shut down.
     */
    default void onShutdown() {
    }
}
