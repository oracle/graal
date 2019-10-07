package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;

public class LeafAssumptionSetterNode extends InlinedSetterNode {

    private final int curBCI;
    private final int opcode;

    protected LeafAssumptionSetterNode(Method inlinedMethod, int top, int opCode, int curBCI) {
        super(inlinedMethod, top, opCode, curBCI);
        this.curBCI = curBCI;
        this.opcode = opCode;
    }

    @Override
    public int execute(VirtualFrame frame) {
        BytecodesNode root = getBytecodesNode();
        if (inlinedMethod.leafAssumption()) {
            setFieldNode.setField(frame, root, top);
            return -slotCount + stackEffect;
        } else {
            return root.reQuickenInvoke(frame, top, curBCI, opcode, inlinedMethod);
        }
    }

}
