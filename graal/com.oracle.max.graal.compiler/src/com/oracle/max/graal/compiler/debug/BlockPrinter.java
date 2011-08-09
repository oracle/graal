/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.debug;

import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.base.*;

/**
 * Prints a listing for a {@linkplain MergeNode block}.
 */
public class BlockPrinter implements BlockClosure {

    private final InstructionPrinter ip;
    private final boolean cfgOnly;

    public BlockPrinter(IR ir, InstructionPrinter ip, boolean cfgOnly) {
        this.ip = ip;
        this.cfgOnly = cfgOnly;
    }

    public void apply(Block block) {
        if (cfgOnly) {
            if (block.getInstructions().size() > 0) {
                ip.printInstruction((FixedWithNextNode) block.getInstructions().get(0));
            } else {
                ip.out().println("Empty block");
            }
            ip.out().println();
        } else {
            printBlock(block);
        }
    }

    public void printBlock(Block block) {
        LogStream out = ip.out();
        out.println();

        ip.printInstructionListingHeader();

        for (Node i : block.getInstructions()) {
            if (i instanceof FixedWithNextNode) {
                ip.printInstructionListing((FixedWithNextNode) i);
            }
        }
        out.println();

    }

    private static void printFrameState(FrameState newFrameState, LogStream out) {
        int startPosition = out.position();
        if (newFrameState.stackSize() == 0) {
          out.print("empty stack");
        } else {
          out.print("stack [");
          int i = 0;
          while (i < newFrameState.stackSize()) {
            if (i > 0) {
                out.print(", ");
            }
            ValueNode value = newFrameState.stackAt(i);
            out.print(i + ":" + Util.valueString(value));
            if (value == null) {
                i++;
            } else {
                i += value.kind.sizeInSlots();
                if (value instanceof PhiNode) {
                    PhiNode phi = (PhiNode) value;
                    if (phi.operand() != null) {
                        out.print(" ");
                        out.print(phi.operand().toString());
                    }
                }
            }
          }
          out.print(']');
        }
        if (newFrameState.locksSize() != 0) {
            // print out the lines on the line below this
            // one at the same indentation level.
            out.println();
            out.fillTo(startPosition, ' ');
            out.print("locks [");
            for (int i = 0; i < newFrameState.locksSize(); i++) {
                ValueNode value = newFrameState.lockAt(i);
                if (i > 0) {
                    out.print(", ");
                }
                out.print(i + ":");
                if (value == null) {
                    // synchronized methods push null on the lock stack
                    out.print("this");
                } else {
                    out.print(Util.valueString(value));
                }
            }
            out.print("]");
        }
    }
}
