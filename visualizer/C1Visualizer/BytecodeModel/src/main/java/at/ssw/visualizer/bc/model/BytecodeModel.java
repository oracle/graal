/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.bc.model;

import at.ssw.visualizer.model.bc.Bytecodes;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;

/**
 * Entry point for the public bytecodes data model.
 *
 * @author Christian Wimmer
 */
public interface BytecodeModel {
    /**
     * Checks if bytecodes can be available for a control flow graph, i.e. if
     * the control flow graph is either from the early bytecode parsing phase
     * of the compiler, or no method inlining was performed.
     * 
     * This does not imply that {link #getBytecodes()} returns true for this
     * control flow graph. It is possible that the class is not in the
     * classpath specified in the visualizer options.
     *
     * @return  returns true if a BlockListBuilder node is selected
     *  or a After Generation of HIR node is selected and there is
     *  only one BlockListerBuilder node, false otherwise.
     */
    public boolean hasBytecodes(ControlFlowGraph cfg);

    /**
     * Gets the bytecodes for a control flow graph.
     *
     * @return  the bytecodes, or null if not available.
     */
    public Bytecodes getBytecodes(ControlFlowGraph cfg);

    /**
     * Returns a human-readable message that explains why no bytecodes are
     * available for the specified control flow graph.
     */
    public String noBytecodesMsg(ControlFlowGraph cfg);
}
