/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir;

import com.oracle.graal.asm.*;
import com.oracle.graal.nodes.cfg.*;

/**
 * LIR instructions such as JUMP and BRANCH need to reference their target {@link Block}. However,
 * direct references are not possible since the control flow graph (and therefore successors lists)
 * can be changed by optimizations - and fixing the instructions is error prone. Therefore, we only
 * reference of block B from block A only via the tuple (A, successor-index-of-B), i.e., indirectly
 * by storing the index into the successor list of A. Note that therefore it is not allowed to
 * reorder the successor list!
 */
public abstract class LabelRef {

    public abstract Label label();

    /**
     * Returns a new reference to a successor of the given block.
     * 
     * @param block The base block that contains the successor list.
     * @param suxIndex The index of the successor.
     * @return The newly created label reference.
     */
    public static LabelRef forSuccessor(final LIR lir, final Block block, final int suxIndex) {
        return new LabelRef() {

            @Override
            public Label label() {
                return ((StandardOp.LabelOp) lir.lir(block.getSuccessors().get(suxIndex)).get(0)).getLabel();
            }

            @Override
            public String toString() {
                return suxIndex < block.getSuccessorCount() ? block.getSuccessors().get(suxIndex).toString() : "?" + block + ":" + suxIndex + "?";
            }
        };
    }
}
