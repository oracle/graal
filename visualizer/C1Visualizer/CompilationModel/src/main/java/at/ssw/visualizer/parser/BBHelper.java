/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.parser;

import java.util.ArrayList;
import java.util.List;
import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.IRInstruction;
import at.ssw.visualizer.model.cfg.State;
import at.ssw.visualizer.modelimpl.cfg.BasicBlockImpl;

/**
 *
 * @author Christian Wimmer
 */
public class BBHelper {

    protected String name;
    protected int fromBci;
    protected int toBci;
    protected String[] predecessors;
    protected String[] successors;
    protected String[] xhandlers;
    protected String[] flags;
    protected String dominator;
    protected int loopIndex;
    protected int loopDepth;
    protected int firstLirId;
    protected int lastLirId;
    protected double probability = Double.NaN;
    protected List<State> states = new ArrayList<State>();
    protected List<IRInstruction> hirInstructions = new ArrayList<IRInstruction>();
    protected List<IRInstruction> lirOperations = new ArrayList<IRInstruction>();
    protected BasicBlockImpl basicBlock = new BasicBlockImpl();
    protected List<BasicBlock> defPredecessorsList = new ArrayList<BasicBlock>();
    protected List<BasicBlock> calcPredecessorsList = new ArrayList<BasicBlock>();
    protected List<BasicBlock> successorsList = new ArrayList<BasicBlock>();
    protected List<BasicBlock> xhandlersList = new ArrayList<BasicBlock>();
}
