package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class LeafAssumptionGetterNode extends InlinedGetterNode {

    public LeafAssumptionGetterNode(Method inlinedMethod, int opCode, int curBCI) {
        super(inlinedMethod, opCode, curBCI);
    }

    @Override
    public int invoke(VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        assert field.isStatic() == inlinedMethod.isStatic();
        if (inlinedMethod.leafAssumption()) {
            StaticObject receiver = field.isStatic()
                            ? field.getDeclaringKlass().tryInitializeAndGetStatics()
                            : nullCheck(root.peekObject(frame, top - 1));
            int resultAt = inlinedMethod.isStatic() ? top : (top - 1);
            Object result = getField(receiver, this.field, kind);
            return (resultAt - top) + root.putKind(frame, resultAt, result, kind);
        } else {
            return root.reQuickenInvoke(frame, top, curBCI, opCode, inlinedMethod);
        }
    }

}
