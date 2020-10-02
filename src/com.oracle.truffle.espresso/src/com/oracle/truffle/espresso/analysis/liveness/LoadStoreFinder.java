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

import static com.oracle.truffle.espresso.bytecode.Bytecodes.isLoad;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.isStore;

import com.oracle.truffle.espresso.analysis.AnalysisProcessor;
import com.oracle.truffle.espresso.analysis.BlockIterator;
import com.oracle.truffle.espresso.analysis.BlockIteratorClosure;
import com.oracle.truffle.espresso.analysis.graph.Graph;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;

public class LoadStoreFinder extends BlockIteratorClosure {

    private final History[] blockHistory;

    @Override
    public BlockIterator.BlockProcessResult processBlock(LinkedBlock b, BytecodeStream bs, AnalysisProcessor processor) {
        History history = new History();
        int bci = b.start();
        while (bci <= b.end()) {
            int opcode = bs.currentBC(bci);
            if (isLoad(opcode)) {
                history.add(new Record(bci, bs.readLocalIndex(bci), TYPE.LOAD));
            }
            if (isStore(opcode)) {
                history.add(new Record(bci, bs.readLocalIndex(bci), TYPE.STORE));
            }
            bci = bs.nextBCI(bci);
        }
        blockHistory[b.id()] = history;
        return BlockIterator.BlockProcessResult.DONE;
    }

    public LoadStoreFinder(Graph<? extends LinkedBlock> graph) {
        this.blockHistory = new History[graph.totalBlocks()];
    }

    public History[] result() {
        return blockHistory;
    }

    enum TYPE {
        LOAD,
        STORE
    }

}
