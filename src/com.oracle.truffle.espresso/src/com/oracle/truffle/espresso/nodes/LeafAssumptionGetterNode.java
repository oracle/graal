package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class LeafAssumptionGetterNode extends InlinedGetterNode {

    protected final int opCode;
    protected final int curBCI;

    protected LeafAssumptionGetterNode(Method inlinedMethod, int opCode, int curBCI) {
        super(inlinedMethod);
        this.opCode = opCode;
        this.curBCI = curBCI;
    }

    @Override
    public int invoke(VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        if (inlinedMethod.leafAssumption()) {
            StaticObject receiver = field.isStatic()
                            ? field.getDeclaringKlass().tryInitializeAndGetStatics()
                            : nullCheck(root.peekAndReleaseObject(frame, top - 1));
            int resultAt = inlinedMethod.isStatic() ? top : (top - 1);
            return (resultAt - top) + getFieldNode.getField(frame, root, receiver, resultAt);
        } else {
            return root.reQuickenInvoke(frame, top, curBCI, opCode, inlinedMethod);
        }
    }

}
