/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.cfg;

import jdk.graal.compiler.nodes.StructuredGraph;

public class ControlFlowGraphBuilder {
    private final StructuredGraph structuredGraph;
    private boolean backendBlocks;
    private boolean connectBlocks;
    private boolean computeFrequency;
    private boolean computeLoops;
    private boolean computeDominators;
    private boolean computePostdominators;

    public ControlFlowGraphBuilder(StructuredGraph structuredGraph) {
        this.structuredGraph = structuredGraph;
    }

    public ControlFlowGraphBuilder backendBlocks(boolean backendBlocks) {
        this.backendBlocks = backendBlocks;
        return this;
    }

    public ControlFlowGraphBuilder connectBlocks(boolean connectBlocks) {
        this.connectBlocks = connectBlocks;
        return this;
    }

    public ControlFlowGraphBuilder computeFrequency(boolean computeFrequency) {
        this.computeFrequency = computeFrequency;
        return this;
    }

    public ControlFlowGraphBuilder computeLoops(boolean computeLoops) {
        this.computeLoops = computeLoops;
        return this;
    }

    public ControlFlowGraphBuilder computeDominators(boolean computeDominators) {
        this.computeDominators = computeDominators;
        return this;
    }

    public ControlFlowGraphBuilder computePostdominators(boolean computePostdominators) {
        this.computePostdominators = computePostdominators;
        return this;
    }

    public ControlFlowGraph build() {
        return ControlFlowGraph.compute(structuredGraph, backendBlocks, connectBlocks, computeFrequency, computeLoops, computeDominators, computePostdominators);
    }
}
