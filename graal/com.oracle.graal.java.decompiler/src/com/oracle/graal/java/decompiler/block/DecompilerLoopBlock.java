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
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.schedule.*;

public class DecompilerLoopBlock extends DecompilerBlock {

    private final List<Block> body;
    private List<DecompilerBlock> decompilerBlockBody;
    private final PrintStream infoStream;

    public DecompilerLoopBlock(Block block, Decompiler decompiler, SchedulePhase schedule, PrintStream infoStream) {
        super(block, decompiler);
        this.infoStream = infoStream;
        this.body = new ArrayList<>();
        decompilerBlockBody = new ArrayList<>();
        decompilerBlockBody.add(new DecompilerBasicBlock(block, decompiler, schedule));
    }

    public void addBodyBlock(Block bodyBlock) {
        body.add(bodyBlock);
    }

    public List<Block> getBody() {
        return body;
    }

    public void detectLoops() {
        decompilerBlockBody.addAll(new DecompilerLoopSimplify(decompiler, infoStream).apply(body));
    }

    public void detectIfs() {
        decompilerBlockBody = new DecompilerIfSimplify(decompiler, infoStream).apply(decompilerBlockBody);
    }

    @Override
    public void printBlock(PrintStream stream, Block codeSuccessor) {
        stream.println(decompiler.getIdent() + block);
        stream.println(decompiler.getIdent() + "while {");
        decompiler.ident();
        for (int i = 0; i < decompilerBlockBody.size(); i++) {
            if (i < decompilerBlockBody.size() - 1) {
                decompilerBlockBody.get(i).printBlock(stream, decompilerBlockBody.get(i + 1).getBlock());
            } else {
                decompilerBlockBody.get(i).printBlock(stream, block);
            }
        }
        decompiler.undent();
        stream.println(decompiler.getIdent() + "}");
    }

    @Override
    public String toString() {
        return "LOOP " + block.toString();
    }

    @Override
    public int getSuccessorCount() {
        return block.getLoop().getExits().size();
    }

    @Override
    public boolean contains(Block b) {
        if (b == block) {
            return true;
        }
        for (Block bodyBlock : body) {
            if (bodyBlock == b) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<DecompilerBasicBlock> getAllBasicBlocks() {
        List<DecompilerBasicBlock> blocks = new ArrayList<>();
        for (DecompilerBlock b : decompilerBlockBody) {
            blocks.addAll(b.getAllBasicBlocks());
        }
        return blocks;
    }

}
