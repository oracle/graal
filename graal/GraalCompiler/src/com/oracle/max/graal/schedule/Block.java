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
package com.oracle.max.graal.schedule;

import java.util.*;

import com.sun.c1x.debug.*;
import com.sun.c1x.ir.*;


public class Block {

    private int blockID;
    private final List<Block> successors = new ArrayList<Block>();
    private final List<Block> predecessors = new ArrayList<Block>();
    private final List<Instruction> instructions = new ArrayList<Instruction>();
    private boolean exceptionEntry;

    public List<Block> getSuccessors() {
        return Collections.unmodifiableList(successors);
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    public List<Block> getPredecessors() {
        return Collections.unmodifiableList(predecessors);
    }

    public Block(int blockID) {
        this.blockID = blockID;
    }

    public void addSuccessor(Block other) {
        successors.add(other);
        other.predecessors.add(this);
    }

    public int blockID() {
        return blockID;
    }

    public void setExceptionEntry(boolean b) {
        exceptionEntry = b;
    }

    public boolean isExceptionEntry() {
        return exceptionEntry;
    }

    @Override
    public String toString() {
        return "B" + blockID;
    }

    public void removeExceptionSuccessors() {
        for (int i = 0; i < successors.size(); ++i) {
            TTY.println("checking succ");
            if (successors.get(i).isExceptionEntry()) {
                TTY.println("removing successor " + i);
                successors.remove(i);
                i--;
            }
        }

    }
}
