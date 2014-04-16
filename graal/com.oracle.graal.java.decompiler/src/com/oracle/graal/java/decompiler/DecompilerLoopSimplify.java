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
package com.oracle.graal.java.decompiler;

import java.io.*;
import java.util.*;

import com.oracle.graal.cfg.*;
import com.oracle.graal.java.decompiler.block.*;
import com.oracle.graal.nodes.cfg.*;

public class DecompilerLoopSimplify {

    private final Decompiler decompiler;
    private final PrintStream infoStream;

    public DecompilerLoopSimplify(Decompiler decompiler, PrintStream infoStream) {
        this.decompiler = decompiler;
        this.infoStream = infoStream;
    }

    public List<DecompilerBlock> apply(List<Block> cfgBlocks) {
        List<DecompilerBlock> blocks = new ArrayList<>();

        while (!cfgBlocks.isEmpty()) {
            Block firstBlock = cfgBlocks.get(0);
            cfgBlocks.remove(0);
            if (firstBlock.isLoopHeader()) {
                DecompilerLoopBlock loopBlock = new DecompilerLoopBlock(firstBlock, decompiler, decompiler.getSchedule(), infoStream);
                Loop<Block> loop = firstBlock.getLoop();

                for (int i = 0; i < cfgBlocks.size(); i++) {
                    if (loop.blocks.contains(cfgBlocks.get(i)) && cfgBlocks.get(i) != firstBlock) {
                        loopBlock.addBodyBlock(cfgBlocks.get(i));
                    }
                }

                // Asserting:
                for (Block b : loopBlock.getBody()) {
                    if (!loop.blocks.contains(b)) {
                        throw new AssertionError();
                    }
                }
                for (Block b : loop.blocks) {
                    if (b != firstBlock && !loopBlock.getBody().contains(b)) {
                        throw new AssertionError();
                    }
                }

                for (Block b : loopBlock.getBody()) {
                    cfgBlocks.remove(b);
                }

                blocks.add(loopBlock);
                loopBlock.detectLoops();
            } else {
                DecompilerBasicBlock wrappedBlock = new DecompilerBasicBlock(firstBlock, decompiler, decompiler.getSchedule());
                blocks.add(wrappedBlock);
            }
        }
        return blocks;
    }
}
