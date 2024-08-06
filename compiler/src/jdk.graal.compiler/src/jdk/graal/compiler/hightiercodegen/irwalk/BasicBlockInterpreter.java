/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.graal.compiler.hightiercodegen.irwalk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;

import jdk.graal.compiler.hightiercodegen.CodeGenTool;
import jdk.graal.compiler.hightiercodegen.reconstruction.ReconstructionData;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Generates code by using the basic block interpreter to handle control flow. See
 * {@link #lowerFunction()} for an example.
 */
public class BasicBlockInterpreter extends IRWalker {
    /**
     * Name used for basic block interpreter dispatching.
     */
    protected static final String switchControl = "control";

    /**
     * Name used for caught exceptions.
     */
    private static final String excpname = "excp";

    /**
     * Map used for block interpreter dispatching during code generation. The integer is used for
     * the case identifier of the switch.
     */
    private final HashMap<HIRBlock, Integer> blockLookup;

    /**
     * Determines if next blocks with pred.count==1 in basic block interpreter should be inlined.
     */
    private static final boolean inlineSuccessorBlock = true;

    public BasicBlockInterpreter(CodeGenTool codeGenTool, ControlFlowGraph cfg, BlockMap<List<Node>> blockToNodeMap, NodeMap<HIRBlock> nodeToBlockMap, ReconstructionData reconstructionData) {
        super(codeGenTool, cfg, blockToNodeMap, nodeToBlockMap, reconstructionData);
        blockLookup = new HashMap<>();
        for (HIRBlock b : cfg.getBlocks()) {
            blockLookup.put(b, b.getId());
        }
    }

    /**
     * Generate code for an {@link InvokeWithExceptionNode}. Example:
     *
     * <pre>
     *     try {
     *         invoke(args);
     *         control = normalSuccessor;
     *         continue;
     *     } catch(excp) {
     *         exception_object_0 = excp;
     *         control = exceptionSuccessor;
     *         continue;
     *     }
     * </pre>
     */
    public final void genInvokeWithException(InvokeWithExceptionNode n) {
        codeGenTool.genTryBlock();

        lowerNode(n);
        int dispatchtarget = blockLookup.get(cfg.blockFor(n.next()));

        genDispatch(dispatchtarget);

        codeGenTool.genCatchBlockPrefix(excpname);

        ExceptionObjectNode eon = (ExceptionObjectNode) n.exceptionEdge();

        assert eon != null : "Exception object might never be null";

        String excpobjname = codeGenTool.getExceptionObjectId(eon);

        codeGenTool.genResolvedVarDeclPrefix(excpobjname);
        codeGenTool.getCodeBuffer().emitText(excpname);
        codeGenTool.genResolvedVarDeclPostfix(null);
        dispatchtarget = blockLookup.get(cfg.blockFor(n.exceptionEdge()));

        genDispatch(dispatchtarget);

        codeGenTool.genScopeEnd();
    }

    /**
     * Generates a switch that is not the switch of the basic block interpreter. For example:
     *
     * <pre>
     * switch (variable) {
     *     case 0:
     *         control = 10;
     *         continue;
     *     case 1:
     *         control = 11;
     *         continue;
     *     case 2:
     *         control = 12;
     *         continue;
     * }
     * </pre>
     */
    public final void genSwitch(IntegerSwitchNode switchNode) {
        ValueNode switchValue = switchNode.value();

        lowerForVerification(switchNode);

        // switch header
        codeGenTool.getCodeBuffer().emitSwitchHeaderLeft();
        codeGenTool.nodeLowerer().lowerValue(switchValue);
        codeGenTool.getCodeBuffer().emitSwitchHeaderRight();

        for (int i = 0; i < switchNode.keyCount(); i++) {
            // get the successor for guided dispatch
            ValueNode succ = switchNode.keySuccessor(i);

            Constant keyConstant = switchNode.keyAt(i);

            genSwitchCase(((PrimitiveConstant) keyConstant).asInt());

            genDispatch(blockLookup.get(cfg.blockFor(succ)));

            // Closes the scope opened by genSwitchCase
            codeGenTool.genScopeEnd();
        }

        if (switchNode.defaultSuccessor() != null) {
            codeGenTool.genSwitchDefaultCase();
            genDispatch(blockLookup.get(cfg.blockFor(switchNode.defaultSuccessor())));
            codeGenTool.genScopeEnd();
        }

        // switch end
        codeGenTool.genScopeEnd();
    }

    /**
     * Generates code for an {@link IfNode}. For example:
     *
     * <pre>
     * if (condition) {
     *     control = trueSuccessor;
     *     continue;
     * } else {
     *     control = falseSuccessor;
     *     continue;
     * }
     * </pre>
     */
    public final void genIf(IfNode n) {
        assert cfg.blockFor(n.trueSuccessor()) != null;
        assert null != cfg.blockFor(n.falseSuccessor());
        int trueBranchDispatch = blockLookup.get(cfg.blockFor(n.trueSuccessor()));
        int falseBranchDispatch = blockLookup.get(cfg.blockFor(n.falseSuccessor()));
        lowerForVerification(n);
        codeGenTool.genIfHeader(n.condition());

        genDispatch(trueBranchDispatch);

        codeGenTool.genElseHeader();

        genDispatch(falseBranchDispatch);

        codeGenTool.genScopeEnd();
    }

    /**
     * Generates the basic block interpreter. For example:
     *
     * <pre>
     *     control = 0;
     *     while(true) {
     *         switch(control) {
     *            case 0:
     *                  // basic block 0
     *                  ...
     *                  control = ..;
     *                  continue;
     *            case 1:
     *                  // basic block 1
     *                  ...
     *                  control = ...;
     *                  continue;
     *
     *            ...
     *
     *            default:
     *                  // should not reach here
     *         }
     *     }
     * </pre>
     */
    protected void lowerFunction() {
        List<HIRBlock> blocks = new ArrayList<>();
        Collections.addAll(blocks, cfg.getBlocks());

        // lower the dispatch variable
        genHeader(blockLookup.get(cfg.getStartBlock()));

        // loop
        codeGenTool.genWhileTrueHeader();

        // switch
        codeGenTool.genSwitchHeader(switchControl);

        while (!blocks.isEmpty()) {
            HIRBlock currentBlock = blocks.get(0);

            if (blockHistory.blockVisited(currentBlock)) {
                blocks.remove(currentBlock);
                continue;
            }

            genSwitchCase(blockLookup.get(currentBlock));

            currentBlock = lowerBlock(currentBlock, blocks);

            int gotoTarget = -1;
            if (currentBlock.getSuccessorCount() > 0) {
                Integer ic = blockLookup.get(currentBlock.getFirstSuccessor());
                assert ic != null;
                gotoTarget = ic;
            }
            genDispatch(gotoTarget);
            codeGenTool.genScopeEnd();

            // remember visited blocks
            blockHistory.visitBlock(currentBlock);
            blocks.remove(currentBlock);
        }

        codeGenTool.genSwitchDefaultCase();

        // default dispatch should never be called
        codeGenTool.genShouldNotReachHere("Default case of the basic block interpreter must not be reachable");
        codeGenTool.getCodeBuffer().emitInsEnd();

        codeGenTool.genLoopContinue();

        codeGenTool.genScopeEnd();
        codeGenTool.genScopeEnd();
        codeGenTool.genScopeEnd();
    }

    /**
     * Lower the given block.
     *
     * NOTE: this function will inline successor blocks if the option is set: every successor block
     * of the given block that has just one predecessor (so is not a merge target and cannot be a
     * jmp target of another block) will be inlined here
     *
     * @param b the block to be lowered
     * @param blocks the list of all blocks
     * @return the lowered block
     */
    protected HIRBlock lowerBlock(HIRBlock b, List<HIRBlock> blocks) {
        HIRBlock currentBlock = b;
        boolean inlinedBlock;
        do {
            inlinedBlock = false;
            for (Node n : blockToNodeMap.get(currentBlock)) {
                if (n instanceof IfNode) {
                    genIf((IfNode) n);
                    // dispatch was generated, must be used
                    return currentBlock;
                } else if (n instanceof IntegerSwitchNode) {
                    genSwitch((IntegerSwitchNode) n);
                    // dispatch was generated, must be used
                    return currentBlock;
                } else if (n instanceof InvokeWithExceptionNode) {
                    genInvokeWithException((InvokeWithExceptionNode) n);
                    // dispatch was generated, must be used
                    return currentBlock;
                } else {
                    lowerNode(n);
                }
            }
            if (inlineNextBlock(currentBlock)) {
                inlinedBlock = true;
                HIRBlock next = currentBlock.getFirstSuccessor();
                blocks.remove(currentBlock);
                blockHistory.visitBlock(currentBlock);
                currentBlock = next;
                codeGenTool.genComment("Inlined the basic block " + currentBlock);
            }
        } while (inlinedBlock);
        return currentBlock;
    }

    private static boolean inlineNextBlock(HIRBlock currentBlock) {
        return inlineSuccessorBlock && currentBlock.getSuccessorCount() > 0 && currentBlock.getFirstSuccessor().getPredecessorCount() == 1;
    }

    @Override
    protected boolean lowerNode(Node n) {
        // for verification purposes
        verifier.visitNode(n, codeGenTool);

        if (n instanceof LoopBeginNode) {
            codeGenTool.genComment("Omitted the LoopBeginNode " + n);
            return false;
        }

        if (n instanceof LoopExitNode) {
            codeGenTool.genComment("Omitted the LoopExitNode " + n);
            return false;
        } else if (n instanceof LoopEndNode) {
            codeGenTool.genComment("Loop End");
            lowerLoopEndResolver((LoopEndNode) n);
            return true;
        } else {
            return super.lowerNode(n);
        }
    }

    public void lowerForVerification(Node n) {
        verifier.visitNode(n, codeGenTool);
    }

    @Override
    protected void lower(DebugContext debugContext) {
        if (cfg.getBlocks().length > 1) {
            lowerFunction();
        } else {
            // no control flow -> skip generating the basic block interpreter
            lowerBlock(cfg.getStartBlock(), Arrays.asList(cfg.getBlocks()));
        }
    }

    /**
     * Generate a dispatch to the next basic block.
     *
     * @param target case id of the target basic block
     */
    private void genDispatch(int target) {
        codeGenTool.genResolvedVarAccess(switchControl);
        codeGenTool.genAssignment();
        codeGenTool.getCodeBuffer().emitIntLiteral(target);
        codeGenTool.genResolvedVarDeclPostfix(null);
        codeGenTool.genLoopContinue();
    }

    private void genSwitchCase(int caseVal) {
        codeGenTool.genSwitchCase(caseVal);
    }

    /**
     * Generates the dispatch variable.
     *
     * @param startCase switch case identifier of the {@link ControlFlowGraph#getStartBlock()}.
     */
    private void genHeader(int startCase) {
        codeGenTool.genResolvedVarDeclPrefix(switchControl);
        codeGenTool.getCodeBuffer().emitIntLiteral(startCase);
        codeGenTool.genResolvedVarDeclPostfix(null);
    }
}
