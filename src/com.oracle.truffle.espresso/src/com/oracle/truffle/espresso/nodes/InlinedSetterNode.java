package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.object.DebugCounter;

public class InlinedSetterNode extends QuickNode {

    private static final DebugCounter setterNodes = DebugCounter.create("setters: ");
    private static final DebugCounter leafSetterNodes = DebugCounter.create("leaf set: ");

    private static final int INSTANCE_SETTER_BCI = 2;
    private static final int STATIC_SETTER_BCI = 1;

    final Field field;
    final Method inlinedMethod;
    protected final int stackEffect;
    final int slotCount;

    @Child AbstractSetFieldNode setFieldNode;

    InlinedSetterNode(Method inlinedMethod, int opcode) {
        this.inlinedMethod = inlinedMethod;
        this.field = getInlinedField(inlinedMethod);
        this.slotCount = field.getKind().getSlotCount();
        this.stackEffect = Bytecodes.stackEffectOf(opcode);
        setFieldNode = AbstractSetFieldNode.create(this.field);
        assert field.isStatic() == inlinedMethod.isStatic();
    }

    public static InlinedSetterNode create(Method inlinedMethod, int opCode, int curBCI) {
        setterNodes.inc();
        if (inlinedMethod.isFinalFlagSet() || inlinedMethod.getDeclaringKlass().isFinalFlagSet()) {
            return new InlinedSetterNode(inlinedMethod, opCode);
        } else {
            leafSetterNodes.inc();
            return new LeafAssumptionSetterNode(inlinedMethod, opCode, curBCI);
        }
    }

    @Override
    public int invoke(VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        setFieldNode.setField(frame, root, top);
        return -slotCount + stackEffect;
    }

    private static Field getInlinedField(Method inlinedMethod) {
        BytecodeStream code = new BytecodeStream(inlinedMethod.getCode());
        if (inlinedMethod.isStatic()) {
            return inlinedMethod.getRuntimeConstantPool().resolvedFieldAt(inlinedMethod.getDeclaringKlass(), code.readCPI(STATIC_SETTER_BCI));
        } else {
            return inlinedMethod.getRuntimeConstantPool().resolvedFieldAt(inlinedMethod.getDeclaringKlass(), code.readCPI(INSTANCE_SETTER_BCI));
        }
    }
}
