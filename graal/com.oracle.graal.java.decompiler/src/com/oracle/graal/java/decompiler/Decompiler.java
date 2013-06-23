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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.schedule.*;

public class Decompiler {

    private List<DecompilerBlock> blocks = new ArrayList<>();
    private List<Block> cfgBlocks = new ArrayList<>();
    private SchedulePhase schedule;
    private ControlFlowGraph cfg;

    private final PrintStream stream;
    private final PrintStream infoStream;

    private static final String IDENT = "  ";
    private String curIdent = "";

    public final String contexInformation;

    public Decompiler(StructuredGraph graph, SchedulePhase schedulePhase, PrintStream stream, PrintStream infoPrintStream, String contextInformation) {
        this.stream = stream;
        this.infoStream = infoPrintStream;
        this.contexInformation = contextInformation;
        schedule = schedulePhase;
        if (schedule == null) {
            try {
                schedule = new SchedulePhase();
                schedule.apply(graph);
            } catch (Throwable t) {
            }
        }

        cfg = schedule.getCFG();
    }

    public void decompile() {

        for (Block b : getCfg().getBlocks()) {
            cfgBlocks.add(b);
        }

        for (int i = 0; i < getCfg().getBlocks().length - 1; i++) {
            if (cfg.getBlocks()[i].getId() >= cfg.getBlocks()[i + 1].getId()) {
                throw new AssertionError();
            }
        }

        blocks = new DecompilerLoopSimplify(this, infoStream).apply(cfgBlocks);
        blocks = new DecompilerIfSimplify(this, infoStream).apply(blocks);

        DecompilerPhiRemover.apply(cfg, blocks);

        printDebugOutput();
    }

    private void printDebugOutput() {
        for (int i = 0; i < blocks.size(); i++) {
            if (i < blocks.size() - 1) {
                blocks.get(i).printBlock(stream, blocks.get(i + 1).getBlock());
            } else {
                blocks.get(i).printBlock(stream, null);
            }
        }
    }

    public List<DecompilerBlock> getBlocks() {
        return blocks;
    }

    public void ident() {
        curIdent += IDENT;
    }

    public void undent() {
        curIdent = curIdent.substring(0, curIdent.length() - IDENT.length());
    }

    public String getIdent() {
        return curIdent;
    }

    public List<Block> getCfgBlocks() {
        return cfgBlocks;
    }

    public SchedulePhase getSchedule() {
        return schedule;
    }

    public ControlFlowGraph getCfg() {
        return cfg;
    }

}
