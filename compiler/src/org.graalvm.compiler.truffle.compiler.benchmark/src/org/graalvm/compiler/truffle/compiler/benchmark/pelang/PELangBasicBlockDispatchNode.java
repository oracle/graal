package org.graalvm.compiler.truffle.compiler.benchmark.pelang;

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
        int blockIndex = 0;

        while (blockIndex != PELangBasicBlockNode.NO_SUCCESSOR) {
            blockIndex = blockNodes[blockIndex].executeBlock(frame);
        }
        return blockIndex;
    }

}
