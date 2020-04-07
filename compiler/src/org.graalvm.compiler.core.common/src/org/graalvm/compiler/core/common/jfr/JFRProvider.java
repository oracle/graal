/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.jfr;

import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.function.Consumer;

import org.graalvm.compiler.core.common.CompilationIdentifier;

/**
 * Service-provider class for logging compiler related events.
 */
public interface JFRProvider {

    /**
     * Register compiler phases.
     *
     * @param phaseNames initial list of compiler phase names
     */
    void registerCompilerPhases(String[] phaseNames);

    /**
     * Opens a compiler phase scope.
     *
     * @param nesting the nesting level of the phase, starting at 1
     * @return an object whose {@link CompilerPhaseEvent#close()} method will use {@code writer} to
     *         send details of the phase to JFR
     */
    CompilerPhaseEvent openCompilerPhase(Consumer<CompilerPhaseEvent> writer, int nesting);

    /**
     * Scope for a compiler phase event. The {@link #close()} method must be called to leave the
     * scope when the compiler phase completes.
     */
    interface CompilerPhaseEvent extends AutoCloseable {

        /**
         * Write the event to JFR.
         *
         * @param phase compiler phase name
         * @param compileId current compilation unit id
         */
        void write(String phase, CompilationIdentifier compileId);

        @Override
        void close();

    }

    /**
     * Notifies JFR about an inlining event.
     *
     * @param compileId current computation unit id
     * @param caller caller method
     * @param callee callee method
     * @param succeeded true if inlining succeeded
     * @param message extra information on inlining
     * @param bci byte code index of invoke
     */
    void notifyInlining(CompilationIdentifier compileId, ResolvedJavaMethod caller, ResolvedJavaMethod callee, boolean succeeded, String message, int bci);

}
