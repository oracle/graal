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
package com.sun.c1x.debug;

import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;

/**
 * Prints a listing for a {@linkplain BlockBegin block}.
 *
 * @author Doug Simon
 */
public class BlockPrinter implements BlockClosure {

    private final InstructionPrinter ip;
    private final boolean cfgOnly;
    private final boolean liveOnly;

    public BlockPrinter(IR ir, InstructionPrinter ip, boolean cfgOnly, boolean liveOnly) {
        this.ip = ip;
        this.cfgOnly = cfgOnly;
        this.liveOnly = liveOnly;
    }

    public void apply(BlockBegin block) {
        if (cfgOnly) {
            ip.printInstruction(block);
            ip.out().println();
        } else {
            printBlock(block, liveOnly);
        }
    }

    public void printBlock(BlockBegin block, boolean liveOnly) {
        ip.printInstruction(block);
        LogStream out = ip.out();
        out.println();
        printFrameState(block.stateBefore(), out);
        out.println();

        out.println("inlining depth " + block.stateBefore().scope().level);

        ip.printInstructionListingHeader();

        for (Instruction i = block.next(); i != null; i = i.next()) {
            if (!liveOnly || i.isLive()) {
                ip.printInstructionListing(i);
            }
        }
        out.println();

    }

    private static void printFrameState(FrameState newFrameState, LogStream out) {
        int startPosition = out.position();
        if (newFrameState.stackEmpty()) {
          out.print("empty stack");
        } else {
          out.print("stack [");
          int i = 0;
          while (i < newFrameState.stackSize()) {
            if (i > 0) {
                out.print(", ");
            }
            Value value = newFrameState.stackAt(i);
            out.print(i + ":" + Util.valueString(value));
            if (value == null) {
                i++;
            } else {
                i += value.kind.sizeInSlots();
                if (value instanceof Phi) {
                    Phi phi = (Phi) value;
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
                Value value = newFrameState.lockAt(i);
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
