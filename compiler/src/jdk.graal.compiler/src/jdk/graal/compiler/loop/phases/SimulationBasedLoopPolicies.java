/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.loop.phases;

import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.spi.CoreProviders;

/** A loop policy that uses the simulation based peeling heuristic. */
public final class SimulationBasedLoopPolicies extends DefaultLoopPolicies {
    private final SimulationBasedLoopPeeling.LoopPeelingFactors peelingFactors;

    public SimulationBasedLoopPolicies(SimulationBasedLoopPeeling.LoopPeelingFactors peelingFactors) {
        this.peelingFactors = peelingFactors;
    }

    @Override
    public boolean shouldPeel(Loop loop, ControlFlowGraph cfg, CoreProviders providers, int peelingIteration) {
        return SimulationBasedLoopPeeling.shouldPeel(peelingFactors, loop, cfg, providers, peelingIteration);
    }
}
