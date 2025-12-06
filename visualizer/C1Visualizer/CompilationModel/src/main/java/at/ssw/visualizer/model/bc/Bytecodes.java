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
package at.ssw.visualizer.model.bc;

import at.ssw.visualizer.model.cfg.ControlFlowGraph;

/**
 * This class holds the bytecode of a method and provides severel methods
 * accessing the details.
 *
 * @author Alexander Reder
 * @author Christian Wimmer
 */
public interface Bytecodes {
    /**
     * Back-link to the control flow graph where the bytecodes were loaded from.
     */
    public ControlFlowGraph getControlFlowGraph();

    /**
     * Called before the first call of getBytecodes() or getEpilogue(). Can be called multiple times.
     */
    public void parseBytecodes();
    
    /**
     * The bytecodes of the method in the given bytecode range.
     *
     * @param   fromBCI starting BCI (including this bci)
     * @param   toBCI   ending BCI (not including this bci)
     * @return          string representation of the bytecodes
     */
    public String getBytecodes(int fromBCI, int toBCI);
    
    public String getEpilogue();
}
