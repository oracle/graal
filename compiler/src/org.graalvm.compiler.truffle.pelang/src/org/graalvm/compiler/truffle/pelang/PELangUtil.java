/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.pelang;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockDispatchNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangDoubleSuccessorNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangSingleSuccessorNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangBlockNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangIfNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangReturnNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangWhileNode;

public class PELangUtil {

    public static PELangRootNode toBCF(PELangRootNode rootNode) {
        List<PELangBasicBlockNode> basicBlocks = new ArrayList<>();
        BlockCounter counter = new BlockCounter();
        toBCF(rootNode.getBodyNode(), basicBlocks, counter);

        // fix successor indices which are out-of-bounds
        for (PELangBasicBlockNode basicBlock : basicBlocks) {
            if (basicBlock instanceof PELangSingleSuccessorNode) {
                PELangSingleSuccessorNode singleSuccessor = (PELangSingleSuccessorNode) basicBlock;

                if (singleSuccessor.getSuccessor() >= basicBlocks.size()) {
                    singleSuccessor.setSuccessor(PELangBasicBlockNode.NO_SUCCESSOR);
                }
            } else if (basicBlock instanceof PELangDoubleSuccessorNode) {
                PELangDoubleSuccessorNode doubleSuccessor = (PELangDoubleSuccessorNode) basicBlock;

                if (doubleSuccessor.getTrueSuccessor() >= basicBlocks.size()) {
                    doubleSuccessor.setTrueSuccessor(PELangBasicBlockNode.NO_SUCCESSOR);
                }
                if (doubleSuccessor.getFalseSuccessor() >= basicBlocks.size()) {
                    doubleSuccessor.setFalseSuccessor(PELangBasicBlockNode.NO_SUCCESSOR);
                }
            }
        }
        PELangBasicBlockDispatchNode dispatchNode = new PELangBasicBlockDispatchNode(basicBlocks.stream().toArray(PELangBasicBlockNode[]::new));
        return new PELangRootNode(dispatchNode, rootNode.getFrameDescriptor());
    }

    private static void toBCF(PELangStatementNode statementNode, List<PELangBasicBlockNode> basicBlocks, BlockCounter counter) {
        if (statementNode instanceof PELangBasicBlockDispatchNode) {
            throw new IllegalArgumentException("can't convert AST to BCF if it already contains a dispatch node");
        } else if (statementNode instanceof PELangExpressionNode | statementNode instanceof PELangReturnNode) {
            basicBlocks.add(new PELangSingleSuccessorNode(statementNode, counter.incrementAndGet()));
        } else if (statementNode instanceof PELangIfNode) {
            PELangIfNode ifNode = (PELangIfNode) statementNode;
            PELangDoubleSuccessorNode basicBlock = new PELangDoubleSuccessorNode(ifNode.getConditionNode());

            // set true successor to be next block
            basicBlock.setTrueSuccessor(counter.incrementAndGet());

            List<PELangBasicBlockNode> thenBasicBlocks = new ArrayList<>();
            toBCF(ifNode.getThenNode(), thenBasicBlocks, counter);

            // set false successor after size of thenBasicBlocks is known
            basicBlock.setFalseSuccessor(counter.get());

            List<PELangBasicBlockNode> elseBasicBlocks = new ArrayList<>();
            toBCF(ifNode.getElseNode(), elseBasicBlocks, counter);

            // patch successor of last basic block in then branch to skip the else blocks
            if (thenBasicBlocks.size() > 0) {
                PELangBasicBlockNode lastThenBasicBlock = thenBasicBlocks.get(thenBasicBlocks.size() - 1);

                if (lastThenBasicBlock instanceof PELangSingleSuccessorNode) {
                    PELangSingleSuccessorNode lastSingleSuccessor = (PELangSingleSuccessorNode) lastThenBasicBlock;
                    lastSingleSuccessor.setSuccessor(counter.get());
                } else if (lastThenBasicBlock instanceof PELangDoubleSuccessorNode) {
                    PELangDoubleSuccessorNode lastDoubleSuccessor = (PELangDoubleSuccessorNode) lastThenBasicBlock;

                    // patch false successor because loop end should skip else blocks
                    lastDoubleSuccessor.setFalseSuccessor(counter.get());
                }
            }
            basicBlocks.add(basicBlock);
            basicBlocks.addAll(thenBasicBlocks);
            basicBlocks.addAll(elseBasicBlocks);
        } else if (statementNode instanceof PELangWhileNode) {
            PELangWhileNode whileNode = (PELangWhileNode) statementNode;

            // save current counter for later patching of jump back
            int blockIndex = counter.get();
            PELangDoubleSuccessorNode basicBlock = new PELangDoubleSuccessorNode(whileNode.getConditionNode());

            // set true successor to be next block
            basicBlock.setTrueSuccessor(counter.incrementAndGet());

            List<PELangBasicBlockNode> bodyBasicBlocks = new ArrayList<>();
            toBCF(whileNode.getBodyNode(), bodyBasicBlocks, counter);

            // set false successor after size of bodyBasicBlocks is known
            basicBlock.setFalseSuccessor(counter.get());

            // patch jump-back in last body block
            if (bodyBasicBlocks.size() > 0) {
                PELangBasicBlockNode lastBodyBasicBlock = bodyBasicBlocks.get(bodyBasicBlocks.size() - 1);

                if (lastBodyBasicBlock instanceof PELangSingleSuccessorNode) {
                    PELangSingleSuccessorNode lastSingleSuccessor = (PELangSingleSuccessorNode) lastBodyBasicBlock;
                    lastSingleSuccessor.setSuccessor(blockIndex);
                } else if (lastBodyBasicBlock instanceof PELangDoubleSuccessorNode) {
                    PELangDoubleSuccessorNode lastDoubleSuccessor = (PELangDoubleSuccessorNode) lastBodyBasicBlock;

                    // patch false successor because inner loop end should jump back
                    lastDoubleSuccessor.setFalseSuccessor(blockIndex);
                }
            }
            basicBlocks.add(basicBlock);
            basicBlocks.addAll(bodyBasicBlocks);
        } else if (statementNode instanceof PELangBlockNode) {
            PELangBlockNode blockNode = (PELangBlockNode) statementNode;
            List<PELangStatementNode> collectedBodyNodes = new ArrayList<>();
            toBCF(blockNode, basicBlocks, counter, collectedBodyNodes);
        }
    }

    private static void toBCF(PELangBlockNode blockNode, List<PELangBasicBlockNode> basicBlocks, BlockCounter counter, List<PELangStatementNode> collectedBodyNodes) {
        for (PELangStatementNode bodyNode : blockNode.getBodyNodes()) {
            if (bodyNode instanceof PELangIfNode || bodyNode instanceof PELangWhileNode) {
                toBCF(basicBlocks, counter, collectedBodyNodes);
                toBCF(bodyNode, basicBlocks, counter);
            } else if (bodyNode instanceof PELangBlockNode) {
                PELangBlockNode innerBlockNode = (PELangBlockNode) bodyNode;
                toBCF(innerBlockNode, basicBlocks, counter, collectedBodyNodes);
                collectedBodyNodes.clear();
            } else {
                collectedBodyNodes.add(bodyNode);
            }
        }
        toBCF(basicBlocks, counter, collectedBodyNodes);
    }

    private static void toBCF(List<PELangBasicBlockNode> basicBlocks, BlockCounter counter, List<PELangStatementNode> collectedBodyNodes) {
        if (collectedBodyNodes.size() > 0) {
            PELangStatementNode bodyNode = (collectedBodyNodes.size() == 1) ? collectedBodyNodes.get(0)
                            : new PELangBlockNode(collectedBodyNodes.stream().toArray(PELangStatementNode[]::new));
            collectedBodyNodes.clear();
            PELangSingleSuccessorNode collectedBlock = new PELangSingleSuccessorNode(bodyNode, counter.incrementAndGet());
            basicBlocks.add(collectedBlock);
        }
    }

    static final class BlockCounter {
        private int counter = 0;

        int incrementAndGet() {
            return ++counter;
        }

        int get() {
            return counter;
        }
    }
}
