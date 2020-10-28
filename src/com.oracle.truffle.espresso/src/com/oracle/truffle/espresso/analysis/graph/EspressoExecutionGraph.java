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

package com.oracle.truffle.espresso.analysis.graph;

import java.util.BitSet;

import com.oracle.truffle.espresso.meta.ExceptionHandler;

public final class EspressoExecutionGraph implements Graph<EspressoBlock> {

    private final ExceptionHandler[] handlers;
    private final int[] handlerToBlock;
    private final EspressoBlock[] blocks;

    public EspressoExecutionGraph(ExceptionHandler[] handlers, int[] handlerToBlock, EspressoBlock[] blocks) {
        this.handlers = handlers;
        this.handlerToBlock = handlerToBlock;
        this.blocks = blocks;
    }

    @Override
    public int totalBlocks() {
        return blocks.length;
    }

    @Override
    public EspressoBlock entryBlock() {
        return blocks[0];
    }

    @Override
    public EspressoBlock get(int blockID) {
        return blocks[blockID];
    }

    public ExceptionHandler getHandler(int handlerID) {
        return handlers[handlerID];
    }

    public int getHandlerBlock(int handlerID) {
        return handlerToBlock[handlerID];
    }

    @Override
    public String toString() {
        return visit(new GraphVisitor<String>() {
            StringBuilder str = new StringBuilder();
            int indent = 0;

            @Override
            protected String result() {
                return str.toString();
            }

            @Override
            protected void visitImpl(EspressoBlock block) {
                appendItem(block.toString());
            }

            private StringBuilder indent() {
                for (int i = 0; i < indent; i++) {
                    str.append("  ");
                }
                return str;
            }

            private void appendItem(String s) {
                indent();
                str.append(s).append('\n');
            }
        });
    }

    private <T> T visit(GraphVisitor<T> visitor) {
        for (EspressoBlock block : blocks) {
            visitor.visit(block);
        }
        return visitor.result();
    }

    private abstract class GraphVisitor<T> {
        private final BitSet visited = new BitSet(blocks.length);

        public void visit(EspressoBlock block) {
            int id = block.id();
            if (visited.get(id)) {
                return;
            }
            visited.set(id);
            visitImpl(block);
        }

        protected abstract void visitImpl(EspressoBlock block);

        protected abstract T result();
    }
}
