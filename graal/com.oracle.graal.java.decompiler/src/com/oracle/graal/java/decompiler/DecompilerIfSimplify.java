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

import com.oracle.graal.java.decompiler.block.*;
import com.oracle.graal.java.decompiler.lines.*;
import com.oracle.graal.nodes.cfg.*;

public class DecompilerIfSimplify {

    private final Decompiler decompiler;
    private final PrintStream infoStream;

    public DecompilerIfSimplify(Decompiler decompiler, PrintStream infoStream) {
        this.decompiler = decompiler;
        this.infoStream = infoStream;
    }

    public List<DecompilerBlock> apply(List<DecompilerBlock> decompilerBlocks) {
        return detectIfs(decompilerBlocks);
    }

    private List<DecompilerBlock> detectIfs(List<DecompilerBlock> decompilerBlocks) {
        List<DecompilerBlock> blocks = new ArrayList<>();
        while (!decompilerBlocks.isEmpty()) {
            if (decompilerBlocks.get(0) instanceof DecompilerBasicBlock) {
                DecompilerBasicBlock block = (DecompilerBasicBlock) decompilerBlocks.remove(0);
                if (block.getSuccessorCount() <= 1) {
                    blocks.add(block);
                } else if (block.getSuccessorCount() == 2 && block.getCode().get(block.getCode().size() - 1) instanceof DecompilerIfLine) {
                    Block firstThenBlock = block.getBlock().getSuccessors().get(0);
                    Block firstElseBlock = block.getBlock().getSuccessors().get(1);
                    List<DecompilerBlock> thenBlocks = getReachableDecompilerBlocks(firstThenBlock, decompilerBlocks);
                    List<DecompilerBlock> elseBlocks = getReachableDecompilerBlocks(firstElseBlock, decompilerBlocks);
                    removeIntersection(thenBlocks, elseBlocks);
                    if (thenBlocks.size() == 0 && elseBlocks.size() == 0) {
                        blocks.add(block);
                    } else {
                        for (DecompilerBlock b : thenBlocks) {
                            decompilerBlocks.remove(b);
                        }
                        for (DecompilerBlock b : elseBlocks) {
                            decompilerBlocks.remove(b);
                        }
                        // TODO(mg)
                        // thenBlocks and elseBlocks can be both empty --> causes an AssertionError
                        DecompilerIfBlock ifBlock = new DecompilerIfBlock(block.getBlock(), decompiler, thenBlocks, elseBlocks, infoStream);
                        if (thenBlocks.contains(block.getBlock()) || elseBlocks.contains(block.getBlock())) {
                            throw new AssertionError();
                        }
                        blocks.add(ifBlock);
                        ifBlock.detectIfs();
                    }
                } else {
                    blocks.add(block);
                }
            } else {
                if (decompilerBlocks.get(0) instanceof DecompilerLoopBlock) {
                    DecompilerLoopBlock loop = (DecompilerLoopBlock) decompilerBlocks.get(0);
                    loop.detectIfs();
                }
                blocks.add(decompilerBlocks.remove(0));
            }
        }

        return blocks;
    }

    private static List<DecompilerBlock> getReachableDecompilerBlocks(Block b, List<DecompilerBlock> decompilerBlocks) {
        List<DecompilerBlock> result = new ArrayList<>();
        for (DecompilerBlock block : decompilerBlocks) {
            if (isReachable(b, block.getBlock(), new ArrayList<Block>())) {
                result.add(block);
            }
        }
        return result;
    }

    private static boolean isReachable(Block from, Block to, ArrayList<Block> visited) {
        if (from == to) {
            return true;
        }
        for (Block b : from.getSuccessors()) {
            if (b != from && visited.contains(b) == false && b.getId() > from.getId()) {
                visited.add(b);
                if (isReachable(b, to, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void removeIntersection(List<DecompilerBlock> list1, List<DecompilerBlock> list2) {
        List<DecompilerBlock> list1Copy = new ArrayList<>(list1);
        List<DecompilerBlock> list2Copy = new ArrayList<>(list2);

        list1.removeAll(list2Copy);
        list2.removeAll(list1Copy);
    }
}
