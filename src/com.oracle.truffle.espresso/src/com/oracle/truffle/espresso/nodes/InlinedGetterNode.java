package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.object.DebugCounter;

public class InlinedGetterNode extends QuickNode {

    private static final DebugCounter getterNodes = DebugCounter.create("getters: ");
    private static final DebugCounter leafGetterNodes = DebugCounter.create("leaf get: ");

    private static final int INSTANCE_GETTER_BCI = 1;
    private static final int STATIC_GETTER_BCI = 0;

    protected final Field field;
    protected final Method inlinedMethod;

    @Child ChildGetFieldNode getFieldNode;

    protected InlinedGetterNode(Method inlinedMethod) {
        this.inlinedMethod = inlinedMethod;
        this.field = getInlinedField(inlinedMethod);
        getFieldNode = ChildGetFieldNode.create(this.field);
    }

    public static InlinedGetterNode create(Method inlinedMethod, int opCode, int curBCI) {
        getterNodes.inc();
        if (inlinedMethod.isFinalFlagSet() || inlinedMethod.getDeclaringKlass().isFinalFlagSet()) {
            return new InlinedGetterNode(inlinedMethod);
        } else {
            leafGetterNodes.inc();
            return new LeafAssumptionGetterNode(inlinedMethod, opCode, curBCI);
        }
    }

    @Override
    public int invoke(VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        assert field.isStatic() == inlinedMethod.isStatic();
        StaticObject receiver = field.isStatic()
                        ? field.getDeclaringKlass().tryInitializeAndGetStatics()
                        : nullCheck(root.peekObject(frame, top - 1));
        int resultAt = inlinedMethod.isStatic() ? top : (top - 1);
        return (resultAt - top) + getFieldNode.getField(frame, root, receiver, resultAt);
    }

    private static Field getInlinedField(Method inlinedMethod) {
        BytecodeStream code = new BytecodeStream(inlinedMethod.getCode());
        if (inlinedMethod.isStatic()) {
            return inlinedMethod.getRuntimeConstantPool().resolvedFieldAt(inlinedMethod.getDeclaringKlass(), code.readCPI(STATIC_GETTER_BCI));
        } else {
            return inlinedMethod.getRuntimeConstantPool().resolvedFieldAt(inlinedMethod.getDeclaringKlass(), code.readCPI(INSTANCE_GETTER_BCI));
        }
    }
}
