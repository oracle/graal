/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.analysis.liveness;

import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ALOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ASTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DLOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.DSTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FLOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.FSTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.IINC;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ILOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.ISTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LLOAD_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE_0;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE_1;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE_2;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LSTORE_3;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.RET;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.isLoad;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.isStore;

import com.oracle.truffle.espresso.analysis.graph.Graph;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.impl.Method;

/**
 * Examines each block's opodes to find all relevant history for Liveness analysis (ie: finds LOADs,
 * STOREs and IINCs).
 */
public final class LoadStoreFinder {

    private final Graph<? extends LinkedBlock> graph;
    private final History[] blockHistory;
    private final BytecodeStream bs;
    private final boolean doNotClearThis;

    public LoadStoreFinder(Graph<? extends LinkedBlock> graph, Method method, boolean doNotClearThis) {
        this.graph = graph;
        this.blockHistory = new History[graph.totalBlocks()];
        this.bs = new BytecodeStream(method.getOriginalCode());
        this.doNotClearThis = doNotClearThis;
    }

    public void analyze() {
        for (int i = 0; i < graph.totalBlocks(); i++) {
            processBlock(graph.get(i));
        }
    }

    public void processBlock(LinkedBlock b) {
        History history = new History();
        int bci = b.start();
        while (bci <= b.end()) {
            int opcode = bs.currentBC(bci);
            Record record = null;
            boolean needsTwoLocals = false;
            if (opcode == IINC) {
                record = new Record(bci, findLocalIndex(bs, bci, opcode), TYPE.IINC);
            } else if (isLoad(opcode) || opcode == RET) {
                record = new Record(bci, findLocalIndex(bs, bci, opcode), TYPE.LOAD);
                needsTwoLocals = Bytecodes.stackEffectOf(opcode) == 2;
            } else if (isStore(opcode)) {
                record = new Record(bci, findLocalIndex(bs, bci, opcode), TYPE.STORE);
                needsTwoLocals = Bytecodes.stackEffectOf(opcode) == -2;
            } else if (doNotClearThis && (Bytecodes.isControlSink(opcode))) {
                // Ensures 'this' is alive across the entire method.
                record = new Record(bci, 0, TYPE.LOAD);
            }
            if (record != null) {
                history.add(record);
                if (needsTwoLocals) {
                    history.add(record.second());
                }
            }
            bci = bs.nextBCI(bci);
        }
        blockHistory[b.id()] = history;
    }

    private static int findLocalIndex(BytecodeStream bs, int bci, int opcode) {
        switch (opcode) {
            case ILOAD_0: // fall through
            case ILOAD_1: // fall through
            case ILOAD_2: // fall through
            case ILOAD_3:
                return opcode - ILOAD_0;
            case LLOAD_0: // fall through
            case LLOAD_1: // fall through
            case LLOAD_2: // fall through
            case LLOAD_3:
                return opcode - LLOAD_0;
            case FLOAD_0: // fall through
            case FLOAD_1: // fall through
            case FLOAD_2: // fall through
            case FLOAD_3:
                return opcode - FLOAD_0;
            case DLOAD_0: // fall through
            case DLOAD_1: // fall through
            case DLOAD_2: // fall through
            case DLOAD_3:
                return opcode - DLOAD_0;
            case ALOAD_0: // fall through
            case ALOAD_1: // fall through
            case ALOAD_2: // fall through
            case ALOAD_3:
                return opcode - ALOAD_0;
            case ISTORE_0: // fall through
            case ISTORE_1: // fall through
            case ISTORE_2: // fall through
            case ISTORE_3:
                return opcode - ISTORE_0;
            case LSTORE_0: // fall through
            case LSTORE_1: // fall through
            case LSTORE_2: // fall through
            case LSTORE_3:
                return opcode - LSTORE_0;
            case FSTORE_0: // fall through
            case FSTORE_1: // fall through
            case FSTORE_2: // fall through
            case FSTORE_3:
                return opcode - FSTORE_0;
            case DSTORE_0: // fall through
            case DSTORE_1: // fall through
            case DSTORE_2: // fall through
            case DSTORE_3:
                return opcode - DSTORE_0;
            case ASTORE_0: // fall through
            case ASTORE_1: // fall through
            case ASTORE_2: // fall through
            case ASTORE_3:
                return opcode - ASTORE_0;
            default:
                return bs.readLocalIndex(bci);
        }
    }

    public History[] result() {
        return blockHistory;
    }

    enum TYPE {
        LOAD,
        STORE,
        IINC,
    }

}
