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

public class DecompilerIfBlock extends DecompilerBlock {

    private List<DecompilerBlock> thenBranch;
    private List<DecompilerBlock> elseBranch;
    private final DecompilerBasicBlock head;
    private final PrintStream infoStream;

    public DecompilerIfBlock(Block block, Decompiler decompiler, List<DecompilerBlock> thenBranch, List<DecompilerBlock> elseBranch, PrintStream infoStream) {
        super(block, decompiler);
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
        this.infoStream = infoStream;
        this.head = new DecompilerBasicBlock(block, decompiler, decompiler.getSchedule());

        if (!(thenBranch.isEmpty() == false && head.getBlock().getSuccessors().contains(thenBranch.get(0).getBlock()) || (elseBranch.isEmpty() == false && head.getBlock().getSuccessors().contains(
                        elseBranch.get(0).getBlock())))) {
            // first block of then / else MUST be a successor of the head!
            throw new AssertionError(decompiler.contexInformation);
        }
    }

    @Override
    public void printBlock(PrintStream stream, Block codeSuccessor) {
        List<DecompilerSyntaxLine> lines = head.getCode();
        for (int i = 0; i < lines.size() - 1; i++) {
            if (lines.get(i) != null) {
                String line = lines.get(i).getAsString();
                stream.println(decompiler.getIdent() + line);
            }
        }
        DecompilerIfLine ifLine = (DecompilerIfLine) lines.get(lines.size() - 1);
        if (!thenBranch.isEmpty() && block.getSuccessors().contains(thenBranch.get(0).getBlock())) {
            if (elseBranch.isEmpty()) {
                // while break:
                stream.println(decompiler.getIdent() + ifLine.getIfNegStatement());
                decompiler.ident();
                stream.println(decompiler.getIdent() + "BREAK TO " + block.getSuccessors().get(1));
                decompiler.undent();
                for (int i = 0; i < thenBranch.size(); i++) {
                    if (i < thenBranch.size() - 1) {
                        thenBranch.get(i).printBlock(stream, thenBranch.get(i + 1).getBlock());
                    } else {
                        thenBranch.get(i).printBlock(stream, codeSuccessor);
                    }
                }
            } else {
                stream.println(decompiler.getIdent() + ifLine.getIfStatement() + " {");
                decompiler.ident();
                for (int i = 0; i < thenBranch.size(); i++) {
                    if (i < thenBranch.size() - 1) {
                        thenBranch.get(i).printBlock(stream, thenBranch.get(i + 1).getBlock());
                    } else {
                        thenBranch.get(i).printBlock(stream, codeSuccessor);
                    }
                }
                decompiler.undent();
                stream.println(decompiler.getIdent() + "} ELSE {");
                decompiler.ident();
                for (int i = 0; i < elseBranch.size(); i++) {
                    if (i < elseBranch.size() - 1) {
                        elseBranch.get(i).printBlock(stream, elseBranch.get(i + 1).getBlock());
                    } else {
                        elseBranch.get(i).printBlock(stream, codeSuccessor);
                    }
                }
                decompiler.undent();
                stream.println(decompiler.getIdent() + "}");
            }
        } else if (!elseBranch.isEmpty() && block.getSuccessors().contains(elseBranch.get(0).getBlock())) {
            if (thenBranch.isEmpty()) {
                // while break:
                stream.println(decompiler.getIdent() + ifLine.getIfStatement());
                decompiler.ident();
                stream.println(decompiler.getIdent() + "BREAK TO " + block.getSuccessors().get(0));
                decompiler.undent();
                for (int i = 0; i < elseBranch.size(); i++) {
                    if (i < elseBranch.size() - 1) {
                        elseBranch.get(i).printBlock(stream, elseBranch.get(i + 1).getBlock());
                    } else {
                        elseBranch.get(i).printBlock(stream, codeSuccessor);
                    }
                }
            } else {
                stream.println(decompiler.getIdent() + ifLine.getIfNegStatement() + " {");
                decompiler.ident();
                for (int i = 0; i < elseBranch.size(); i++) {
                    if (i < elseBranch.size() - 1) {
                        elseBranch.get(i).printBlock(stream, elseBranch.get(i + 1).getBlock());
                    } else {
                        elseBranch.get(i).printBlock(stream, codeSuccessor);
                    }
                }
                decompiler.undent();
                stream.println(decompiler.getIdent() + "} ELSE {");
                decompiler.ident();

                for (int i = 0; i < thenBranch.size(); i++) {
                    if (i < thenBranch.size() - 1) {
                        thenBranch.get(i).printBlock(stream, thenBranch.get(i + 1).getBlock());
                    } else {
                        thenBranch.get(i).printBlock(stream, codeSuccessor);
                    }
                }

                decompiler.undent();
                stream.println(decompiler.getIdent() + "}");
            }
        } else {
            throw new AssertionError();
        }
    }

    public void detectIfs() {
        thenBranch = new DecompilerIfSimplify(decompiler, infoStream).apply(thenBranch);
        elseBranch = new DecompilerIfSimplify(decompiler, infoStream).apply(elseBranch);
    }

    @Override
    public int getSuccessorCount() {
        return 1;
    }

    @Override
    public String toString() {
        return "IF" + block.toString();
    }

    @Override
    public boolean contains(Block b) {
        if (b == block) {
            return true;
        }
        for (DecompilerBlock i : thenBranch) {
            if (i.block == b) {
                return true;
            }
        }
        for (DecompilerBlock i : elseBranch) {
            if (i.block == b) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<DecompilerBasicBlock> getAllBasicBlocks() {
        List<DecompilerBasicBlock> blocks = new ArrayList<>();
        blocks.add(head);
        for (DecompilerBlock b : thenBranch) {
            blocks.addAll(b.getAllBasicBlocks());
        }
        for (DecompilerBlock b : elseBranch) {
            blocks.addAll(b.getAllBasicBlocks());
        }
        return blocks;
    }

}
