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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;

public class GraphBuilderConfiguration {

    private final boolean eagerResolving;
    private final boolean omitAllExceptionEdges;
    private ResolvedJavaType[] skippedExceptionTypes;

    /**
     * When the graph builder is in eager infopoint mode, it inserts {@link InfopointNode}s in
     * places where no safepoints would be inserted: inlining boundaries, and line number switches.
     * This is relevant when code is to be generated for native, machine-code level debugging.
     */
    private boolean eagerInfopointMode;

    protected GraphBuilderConfiguration(boolean eagerResolving, boolean omitAllExceptionEdges, boolean eagerInfopointMode) {
        this.eagerResolving = eagerResolving;
        this.omitAllExceptionEdges = omitAllExceptionEdges;
        this.eagerInfopointMode = eagerInfopointMode;
    }

    public void setSkippedExceptionTypes(ResolvedJavaType[] skippedExceptionTypes) {
        this.skippedExceptionTypes = skippedExceptionTypes;
    }

    public void setEagerInfopointMode(boolean eagerInfopointMode) {
        this.eagerInfopointMode = eagerInfopointMode;
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

    public boolean eagerInfopointMode() {
        return eagerInfopointMode;
    }

    public static GraphBuilderConfiguration getDefault() {
        return new GraphBuilderConfiguration(false, false, false);
    }

    public static GraphBuilderConfiguration getInfopointDefault() {
        return new GraphBuilderConfiguration(false, false, true);
    }

    public static GraphBuilderConfiguration getEagerDefault() {
        return new GraphBuilderConfiguration(true, false, false);
    }

    public static GraphBuilderConfiguration getSnippetDefault() {
        return new GraphBuilderConfiguration(true, true, false);
    }

    public static GraphBuilderConfiguration getEagerInfopointDefault() {
        return new GraphBuilderConfiguration(true, false, true);
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
