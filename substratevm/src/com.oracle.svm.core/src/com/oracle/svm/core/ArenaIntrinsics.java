/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.nodes.foreign.MemoryArenaValidInScopeNode;

import jdk.internal.foreign.MemorySessionImpl;

/**
 * Intrinsification happening in SubstrateGraphBuilderPlugins when the method calls to these callees
 * are parsed.
 */
public class ArenaIntrinsics {

    /**
     * Checks if the provided memory session is valid within its current scope.
     *
     * This method serves as a compiler intrinsic to ensure that dominated code accessing memory
     * arenas/sessions adheres to the semantics of shared arenas. It allows the compiler to
     * guarantee proper checks for closed shared arenas in concurrent access scenarios.
     *
     * To utilize this method effectively, follow the recommended pattern:
     *
     * <pre>
     * if (checkValidArenaInScope(session, base, offset) != 0) {
     *     checkArena();
     * }
     * </pre>
     *
     * The returned numeric value indicates whether the associated intrinsic
     * {@link MemoryArenaValidInScopeNode} is present in the graph. A non-zero value signifies its
     * presence, whereas zero denotes its absence. Note that the actual values hold no significance
     * and should not be interpreted directly. Instead, they facilitate modeling data dependencies
     * during compilation in IR. Once the optimizer completes processing scoped memory accesses, the
     * value is replaced with a constant zero, enabling easy cleanup and folding of dependent
     * control flow.
     *
     * If the arena scope is removed from the graph, the compiler sets the arena value to a constant
     * zero, resulting in the removal of dominated code.
     *
     * @param session the memory session to validate
     * @param base the base object associated with the memory session
     * @param offset the offset within the base object
     * @return a numeric value indicating the validity of the memory session within its scope
     */
    @SuppressWarnings("unused")
    public static long checkArenaValidInScope(MemorySessionImpl session, Object base, long offset) {
        // will be intrinsifed to have an exception branch
        return 0;
    }

}
