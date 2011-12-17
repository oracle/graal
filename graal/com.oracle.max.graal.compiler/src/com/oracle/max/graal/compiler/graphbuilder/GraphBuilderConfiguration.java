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
package com.oracle.max.graal.compiler.graphbuilder;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.phases.*;

public class GraphBuilderConfiguration {
    private final boolean useBranchPrediction;
    private final boolean eagerResolving;
    private final PhasePlan plan;

    public GraphBuilderConfiguration(boolean useBranchPrediction, boolean eagerResolving, PhasePlan plan) {
        this.useBranchPrediction = useBranchPrediction;
        this.eagerResolving = eagerResolving;
        this.plan = plan;
    }

    public boolean useBranchPrediction() {
        return useBranchPrediction;
    }

    public boolean eagerResolving() {
        return eagerResolving;
    }

    public PhasePlan plan() {
        return plan;
    }

    public static GraphBuilderConfiguration getDefault() {
        return getDefault(null);
    }

    public static GraphBuilderConfiguration getDefault(PhasePlan plan) {
        return new GraphBuilderConfiguration(GraalOptions.UseBranchPrediction, false, plan);
    }

    public static GraphBuilderConfiguration getDeoptFreeDefault() {
        return getDeoptFreeDefault(null);
    }

    public static GraphBuilderConfiguration getDeoptFreeDefault(PhasePlan plan) {
        return new GraphBuilderConfiguration(false, true, plan);
    }
}
