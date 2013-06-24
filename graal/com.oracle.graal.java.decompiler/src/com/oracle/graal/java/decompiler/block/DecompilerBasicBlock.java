/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java.decompiler.block;

import java.io.*;
import java.util.*;

import com.oracle.graal.java.decompiler.*;
import com.oracle.graal.java.decompiler.lines.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.schedule.*;

public class DecompilerBasicBlock extends DecompilerBlock {

    public DecompilerBasicBlock(Block block, Decompiler decompiler, SchedulePhase schedule) {
        super(block, decompiler);
        initCode(schedule);
    }

    @Override
    public void printBlock(PrintStream stream, Block codeSuccessor) {
        stream.println(decompiler.getIdent() + block);
        for (DecompilerSyntaxLine l : code) {
            if (l != null) {
                String line = l.getAsString();
                stream.println(decompiler.getIdent() + line);
            }
        }
        if (!(block.getSuccessorCount() == 0 || (block.getSuccessorCount() == 1 && block.getFirstSuccessor() == codeSuccessor))) {
            stream.println(decompiler.getIdent() + "GOTO " + Arrays.toString(block.getSuccessors().toArray()));
        }
    }

    @Override
    public String toString() {
        return block.toString();
    }

    @Override
    public int getSuccessorCount() {
        return block.getSuccessorCount();
    }

    public List<Block> getSuccessors() {
        return block.getSuccessors();
    }

    @Override
    public boolean contains(Block b) {
        return b == block;
    }

    public List<DecompilerSyntaxLine> getCode() {
        return code;
    }

    @Override
    public List<DecompilerBasicBlock> getAllBasicBlocks() {
        return Arrays.asList(new DecompilerBasicBlock[]{this});
    }
}
