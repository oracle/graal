/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.compiler.phases.*;

public class GraphBuilderConfiguration {

    public static enum ResolvePolicy {
        Default, EagerForSnippets, Eager,
    }

    private final ResolvePolicy resolving;
    private final PhasePlan plan;
    private ResolvedJavaType[] skippedExceptionTypes;

    public GraphBuilderConfiguration(ResolvePolicy resolving, PhasePlan plan) {
        this.resolving = resolving;
        this.plan = plan;
    }

    public void setSkippedExceptionTypes(ResolvedJavaType[] skippedExceptionTypes) {
        this.skippedExceptionTypes = skippedExceptionTypes;
    }

    public ResolvedJavaType[] getSkippedExceptionTypes() {
        return skippedExceptionTypes;
    }

    public boolean eagerResolvingForSnippets() {
        return (resolving == ResolvePolicy.EagerForSnippets || resolving == ResolvePolicy.Eager);
    }

    public boolean eagerResolving() {
        return (resolving == ResolvePolicy.Eager);
    }

    public PhasePlan plan() {
        return plan;
    }

    public static GraphBuilderConfiguration getDefault() {
        return getDefault(null);
    }

    public static GraphBuilderConfiguration getDefault(PhasePlan plan) {
        return new GraphBuilderConfiguration(ResolvePolicy.Default, plan);
    }

    public static GraphBuilderConfiguration getSnippetDefault() {
        return getSnippetDefault(null);
    }

    public static GraphBuilderConfiguration getSnippetDefault(PhasePlan plan) {
        return new GraphBuilderConfiguration(ResolvePolicy.EagerForSnippets, plan);
    }
}
