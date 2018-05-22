package org.graalvm.compiler.truffle.pelang;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockDispatchNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangBasicBlockNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangDoubleSuccessorNode;
import org.graalvm.compiler.truffle.pelang.bcf.PELangSingleSuccessorNode;
import org.graalvm.compiler.truffle.pelang.expr.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangBlockNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangIfNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangReturnNode;
import org.graalvm.compiler.truffle.pelang.ncf.PELangWhileNode;

public class PELangBCFGenerator {

    private final Counter blockCounter = new Counter();
    private final Counter labelCounter = new Counter();

    private final List<PELangBasicBlockNode> basicBlocks = new ArrayList<>();
    private final Deque<Integer> labelStack = new ArrayDeque<>();
    private final List<Pair<PELangStatementNode, Mode>> delayedBlockBodies = new ArrayList<>();

    public PELangRootNode generate(PELangRootNode node) {
        // start in label mode with first label as PELangBasicBlockNode.NO_SUCCESSOR
        labelStack.push(labelCounter.decrementAndGet());
        generate(node.getBodyNode(), Mode.LABEL);
        labelStack.pop();

        PELangBasicBlockNode[] blockNodes = basicBlocks.stream().toArray(PELangBasicBlockNode[]::new);
        PELangBasicBlockDispatchNode dispatchNode = new PELangBasicBlockDispatchNode(blockNodes);
        return new PELangRootNode(dispatchNode, node.getFrameDescriptor());
    }

    private void generate(PELangStatementNode node, Mode mode) {
        if (node instanceof PELangBlockNode) {
            PELangBlockNode blockNode = (PELangBlockNode) node;
            generateBlock(blockNode, mode);
            generateDelayed();
        } else if (node instanceof PELangIfNode) {
            PELangIfNode ifNode = (PELangIfNode) node;
            generateBranch(ifNode, mode);
        } else if (node instanceof PELangWhileNode) {
            PELangWhileNode wihleNode = (PELangWhileNode) node;
            generateLoop(wihleNode, mode);
        } else if (node instanceof PELangExpressionNode | node instanceof PELangReturnNode) {
            generateSingle(node, mode);
        }
    }

    private void generateBlock(PELangBlockNode node, Mode mode) {
        PELangStatementNode[] bodyNodes = node.getBodyNodes();

        for (int i = 0; i < bodyNodes.length; i++) {
            PELangStatementNode bodyNode = bodyNodes[i];

            // use counter mode for all but last body node
            Mode bodyMode = (i == bodyNodes.length - 1) ? mode : Mode.COUNTER;

            if (bodyNode instanceof PELangIfNode) {
                PELangIfNode ifNode = (PELangIfNode) bodyNode;
                generateDelayed();
                generateBranch(ifNode, bodyMode);
            } else if (bodyNode instanceof PELangWhileNode) {
                PELangWhileNode whileNode = (PELangWhileNode) bodyNode;
                generateDelayed();
                generateLoop(whileNode, bodyMode);
            } else if (bodyNode instanceof PELangBlockNode) {
                PELangBlockNode innerBlock = (PELangBlockNode) bodyNode;
                generateBlock(innerBlock, bodyMode);
            } else {
                Pair<PELangStatementNode, Mode> pair = Pair.create(bodyNode, bodyMode);
                delayedBlockBodies.add(pair);
            }
        }
    }

    private void generateDelayed() {
        if (delayedBlockBodies.size() > 0) {
            PELangStatementNode bodyNode = null;
            Mode bodyMode = null;

            if (delayedBlockBodies.size() == 1) {
                Pair<PELangStatementNode, Mode> pair = delayedBlockBodies.get(0);
                bodyNode = pair.getLeft();
                bodyMode = pair.getRight();
            } else {
                PELangStatementNode[] bodyNodes = delayedBlockBodies.stream().map(Pair::getLeft).toArray(PELangStatementNode[]::new);
                bodyNode = new PELangBlockNode(bodyNodes);

                // use mode of last delayed tuple
                bodyMode = delayedBlockBodies.get(delayedBlockBodies.size() - 1).getRight();
            }
            delayedBlockBodies.clear();
            generateSingle(bodyNode, bodyMode);
        }
    }

    private void generateBranch(PELangIfNode node, Mode mode) {
        // create double successor with if condition
        PELangDoubleSuccessorNode basicBlock = new PELangDoubleSuccessorNode(node.getConditionNode());
        basicBlocks.add(basicBlock);
        blockCounter.increment();

        // save last block index to avoid iteration over all blocks when patching labels
        int lastIndex = basicBlocks.size() - 1;

        // set true successor to be next block index
        basicBlock.setTrueSuccessor(blockCounter.get());

        // push a new label for the branch end on the stack
        int branchEnd = labelCounter.incrementAndGet();
        labelStack.push(branchEnd);

        // generate then nodes in label mode with pushed branch end
        generate(node.getThenNode(), Mode.LABEL);

        // set false successor after size of then blocks is known
        basicBlock.setFalseSuccessor(blockCounter.get());

        // generate else nodes in label mode with pushed branch end
        generate(node.getElseNode(), Mode.LABEL);

        // pop branch end label from the stack
        labelStack.pop();

        // determine successor of the blocks to patch based on current mode
        int successor = (mode == Mode.COUNTER) ? blockCounter.get() : labelStack.peek();

        // patch blocks by given successor
        for (int i = lastIndex; i < basicBlocks.size(); i++) {
            PELangBasicBlockNode blockNode = basicBlocks.get(i);

            if (blockNode instanceof PELangSingleSuccessorNode) {
                PELangSingleSuccessorNode singleSuccessor = (PELangSingleSuccessorNode) blockNode;

                if (singleSuccessor.getSuccessor() == branchEnd) {
                    singleSuccessor.setSuccessor(successor);
                }
            } else if (blockNode instanceof PELangDoubleSuccessorNode) {
                PELangDoubleSuccessorNode doubleSuccessor = (PELangDoubleSuccessorNode) blockNode;

                // only false successor has to be patched in loops
                if (doubleSuccessor.getFalseSuccessor() == branchEnd) {
                    doubleSuccessor.setFalseSuccessor(successor);
                }
            }
        }
    }

    private void generateLoop(PELangWhileNode node, Mode mode) {
        // save current block index for jumps back to loop beginning
        int loopBegin = blockCounter.get();

        // create double successor with while condition
        PELangDoubleSuccessorNode basicBlock = new PELangDoubleSuccessorNode(node.getConditionNode());
        basicBlocks.add(basicBlock);
        blockCounter.increment();

        // set true successor to be next block index
        basicBlock.setTrueSuccessor(blockCounter.get());

        // push loop begin label on the stack
        labelStack.push(loopBegin);

        // generate body nodes in label mode with pushed loop begin
        generate(node.getBodyNode(), Mode.LABEL);

        // pop loop begin label from the stack
        labelStack.pop();

        // determine false successor after size of body nodes is known based on current mode
        int successor = (mode == Mode.COUNTER) ? blockCounter.get() : labelStack.peek();
        basicBlock.setFalseSuccessor(successor);
    }

    private void generateSingle(PELangStatementNode node, Mode mode) {
        PELangSingleSuccessorNode blockNode = new PELangSingleSuccessorNode(node);
        blockCounter.increment();

        // determine successor based on current mode
        int successor = (mode == Mode.COUNTER) ? blockCounter.get() : labelStack.peek();

        blockNode.setSuccessor(successor);
        basicBlocks.add(blockNode);
    }

    static final class Counter {

        private int counter = 0;

        int incrementAndGet() {
            return ++counter;
        }

        int decrementAndGet() {
            return --counter;
        }

        void increment() {
            counter++;
        }

        void decrement() {
            counter--;
        }

        int get() {
            return counter;
        }

    }

    static enum Mode {
        LABEL,
        COUNTER
    }

}
