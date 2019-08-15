package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;

public class LeafAssumptionSetterNode extends InlinedSetterNode {

    private final int curBCI;
    private final int opcode;

    protected LeafAssumptionSetterNode(Method inlinedMethod, int opCode, int curBCI) {
        super(inlinedMethod, opCode);
        this.curBCI = curBCI;
        this.opcode = opCode;
    }

    @Override
    public int invoke(VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        if (inlinedMethod.leafAssumption()) {
            setFieldNode.setField(frame, root, top);
            return -slotCount + stackEffect;
        } else {
            return root.reQuickenInvoke(frame, top, curBCI, opcode, inlinedMethod);
        }
    }

}
