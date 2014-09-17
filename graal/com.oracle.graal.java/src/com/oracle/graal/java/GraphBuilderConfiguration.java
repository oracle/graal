/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;

public class GraphBuilderConfiguration {
    private static final ResolvedJavaType[] EMPTY = new ResolvedJavaType[]{};

    private final boolean eagerResolving;
    private final boolean omitAllExceptionEdges;
    private final ResolvedJavaType[] skippedExceptionTypes;
    private final DebugInfoMode debugInfoMode;

    public static enum DebugInfoMode {
        SafePointsOnly,
        /**
         * This mode inserts {@link SimpleInfopointNode}s in places where no safepoints would be
         * inserted: inlining boundaries, and line number switches.
         * <p>
         * In this mode the infopoint only have a location (method and bytecode index) and no
         * values.
         * <p>
         * This is useful to have better program counter to bci mapping and has no influence on the
         * generated code. However it can increase the amount of metadata and does not allow access
         * to accessing values at runtime.
         */
        Simple,
        /**
         * In this mode, {@link FullInfopointNode}s are generated in the same locations as in
         * {@link #Simple} mode but the infopoints have access to the runtime values.
         * <p>
         * This is relevant when code is to be generated for native, machine-code level debugging
         * but can have a limit the amount of optimization applied to the code.
         */
        Full,
    }

    protected GraphBuilderConfiguration(boolean eagerResolving, boolean omitAllExceptionEdges, DebugInfoMode debugInfoMode, ResolvedJavaType[] skippedExceptionTypes) {
        this.eagerResolving = eagerResolving;
        this.omitAllExceptionEdges = omitAllExceptionEdges;
        this.debugInfoMode = debugInfoMode;
        this.skippedExceptionTypes = skippedExceptionTypes;
    }

    public GraphBuilderConfiguration withSkippedExceptionTypes(ResolvedJavaType[] newSkippedExceptionTypes) {
        return new GraphBuilderConfiguration(eagerResolving, omitAllExceptionEdges, debugInfoMode, newSkippedExceptionTypes);
    }

    public GraphBuilderConfiguration withDebugInfoMode(DebugInfoMode newDebugInfoMode) {
        ResolvedJavaType[] newSkippedExceptionTypes = skippedExceptionTypes == EMPTY ? EMPTY : Arrays.copyOf(skippedExceptionTypes, skippedExceptionTypes.length);
        return new GraphBuilderConfiguration(eagerResolving, omitAllExceptionEdges, newDebugInfoMode, newSkippedExceptionTypes);
    }

    public ResolvedJavaType[] getSkippedExceptionTypes() {
        return skippedExceptionTypes;
    }

    public boolean eagerResolving() {
        return eagerResolving;
    }

    public boolean omitAllExceptionEdges() {
        return omitAllExceptionEdges;
    }

    public boolean insertNonSafepointDebugInfo() {
        return debugInfoMode.ordinal() >= DebugInfoMode.Simple.ordinal();
    }

    public boolean insertFullDebugInfo() {
        return debugInfoMode.ordinal() >= DebugInfoMode.Full.ordinal();
    }

    public static GraphBuilderConfiguration getDefault() {
        return new GraphBuilderConfiguration(false, false, DebugInfoMode.SafePointsOnly, EMPTY);
    }

    public static GraphBuilderConfiguration getEagerDefault() {
        return new GraphBuilderConfiguration(true, false, DebugInfoMode.SafePointsOnly, EMPTY);
    }

    public static GraphBuilderConfiguration getSnippetDefault() {
        return new GraphBuilderConfiguration(true, true, DebugInfoMode.SafePointsOnly, EMPTY);
    }

    public static GraphBuilderConfiguration getFullDebugDefault() {
        return new GraphBuilderConfiguration(true, false, DebugInfoMode.Full, EMPTY);
    }

    /**
     * Returns {@code true} if it is an error for a class/field/method resolution to fail. The
     * default is the same result as returned by {@link #eagerResolving()}. However, it may be
     * overridden to allow failure even when {@link #eagerResolving} is {@code true}.
     */
    public boolean unresolvedIsError() {
        return eagerResolving;
    }
}
