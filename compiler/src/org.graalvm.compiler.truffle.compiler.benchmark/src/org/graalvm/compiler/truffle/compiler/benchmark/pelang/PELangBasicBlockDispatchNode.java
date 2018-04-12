package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

import org.graalvm.compiler.truffle.compiler.benchmark.pelang.PELangBasicBlockNode.Execution;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;

public class PELangBasicBlockDispatchNode extends PELangExpressionNode {

    @Children private final PELangBasicBlockNode[] blockNodes;

    public PELangBasicBlockDispatchNode(PELangBasicBlockNode[] blockNodes) {
        this.blockNodes = blockNodes;
    }

    @Override
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    public Object executeGeneric(VirtualFrame frame) {
        if (blockNodes.length == 0) {
            return PELangNull.Instance;
        }

        Object result = PELangNull.Instance;
        int blockIndex = 0;

        while (blockIndex != PELangBasicBlockNode.NO_SUCCESSOR) {
            Execution execution = blockNodes[blockIndex].executeBlock(frame);

            result = execution.getResult();
            blockIndex = execution.getSuccessor();
        }

        return result;
    }

}
