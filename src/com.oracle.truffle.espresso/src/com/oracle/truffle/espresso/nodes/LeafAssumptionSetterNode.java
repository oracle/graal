package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class LeafAssumptionSetterNode extends InlinedSetterNode {

    protected final int opCode;
    protected final int curBCI;

    protected LeafAssumptionSetterNode(Method inlinedMethod, int opCode, int curBCI) {
        super(inlinedMethod);
        this.opCode = opCode;
        this.curBCI = curBCI;
    }

    @Override
    public int invoke(VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        if (inlinedMethod.leafAssumption()) {
            setFieldNode.setField(frame, root, top);
            return -slotCount;
        } else {
            return root.reQuickenInvoke(frame, top, curBCI, opCode, inlinedMethod);
        }
    }

}
