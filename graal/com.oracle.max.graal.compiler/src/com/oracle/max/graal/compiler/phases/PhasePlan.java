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
package com.oracle.max.graal.compiler.phases;

import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.nodes.*;

/**
 * Tells the compiler about additional phases that need to be executed during compilation.
 */
public class PhasePlan {
    /**
     * The compilation is split into the following sections:
     * ========================================================================
     * Period 1: High-level nodes. (Graph building)
     * ========================================================================
     * Runtime-specific lowering.
     * ========================================================================
     * Period 2: Mid-level nodes. (Memory dependence graph)
     * ========================================================================
     * Target-specific lowering, de-SSA.
     * ========================================================================
     * Period 3: Low-level nodes. (Register allocation, code generation)
     * ========================================================================
     *
     * A compiler extension phase can chose to run at the end of periods 1-3.
     */
    public static enum PhasePosition {
        AFTER_PARSING,
        HIGH_LEVEL,
        MID_LEVEL,
        LOW_LEVEL
    }

    public static final PhasePlan DEFAULT = new PhasePlan();

    @SuppressWarnings("unchecked")
    private final ArrayList<Phase>[] phases = new ArrayList[PhasePosition.values().length];

    private final Set<Class<? extends Phase>> disabledPhases = new HashSet<Class<? extends Phase>>();

    public void addPhase(PhasePosition pos, Phase phase) {
        if (phases[pos.ordinal()] == null) {
            phases[pos.ordinal()] = new ArrayList<Phase>();
        }
        phases[pos.ordinal()].add(phase);
    }

    public void runPhases(PhasePosition pos, StructuredGraph graph, GraalContext context) {
        if (phases[pos.ordinal()] != null) {
            for (Phase p : phases[pos.ordinal()]) {
                p.apply(graph, context);
            }
        }
    }

    public void disablePhase(Class<? extends Phase> clazz) {
        disabledPhases.add(clazz);
    }

    public boolean isPhaseDisabled(Class<? extends Phase> clazz) {
        return disabledPhases.contains(clazz);
    }
}
