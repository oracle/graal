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
package com.oracle.max.graal.compiler.lir;

import com.oracle.max.asm.*;

/**
 * LIR instructions such as JUMP and BRANCH need to reference their target {@link LIRBlock}. However,
 * direct references are not possible since the control flow graph (and therefore successors lists) can
 * be changed by optimizations - and fixing the instructions is error prone.
 * Therefore, we only reference of block B from block A only via the tuple (A, successor-index-of-B), i.e.,
 * indirectly by storing the index into the successor list of A.
 * Note that therefore it is not allowed to reorder the successor list!
 *
 * Labels of out-of-line stubs can be referenced directly, therefore it is also possible to construct a
 * LabelRef for a Label directly via {@link #forLabel}.
 */
public abstract class LabelRef {

    public abstract Label label();

    /**
     * Returns a new reference to a statically defined label.
     * @param label The label that is always returned.
     * @return The newly created label reference.
     */
    public static LabelRef forLabel(final Label label) {
       return new LabelRef() {
           @Override
           public Label label() {
               return label;
           }

           @Override
           public String toString() {
               return label.toString();
           }
       };
    }

    /**
     * Returns a new reference to a successor of the given block.
     * This allows to reference the given successor even when the successor list
     * is modified between the creation of the reference and the call to {@link #getLabel}.
     * @param block The base block that contains the successor list.
     * @param suxIndex The index of the successor.
     * @return The newly created label reference.
     */
    public static LabelRef forSuccessor(final LIRBlock block, final int suxIndex) {
        return new LabelRef() {
            @Override
            public Label label() {
                return block.suxAt(suxIndex).label();
            }

            @Override
            public String toString() {
                return suxIndex < block.numberOfSux() ? block.suxAt(suxIndex).toString() : "?" + block + ":" + suxIndex + "?";
            }
        };
    }
}
