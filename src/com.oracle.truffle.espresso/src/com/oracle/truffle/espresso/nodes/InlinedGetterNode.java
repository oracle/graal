package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class InlinedGetterNode extends QuickNode {

    protected final Field field;
    protected final JavaKind kind;
    protected final Method inlinedMethod;
    protected final Klass methodKlass;
    protected final int opCode;
    protected final int curBCI;

    protected InlinedGetterNode(Method inlinedMethod, int opCode, int curBCI) {
        this.inlinedMethod = inlinedMethod;
        this.opCode = opCode;
        this.field = getInlinedField(inlinedMethod);
        this.kind = field.getKind();
        this.methodKlass = inlinedMethod.getDeclaringKlass();
        this.curBCI = curBCI;
    }

    public static InlinedGetterNode create(Method inlinedMethod, int opCode, int curBCI) {
        if (inlinedMethod.isFinalFlagSet() || inlinedMethod.getDeclaringKlass().isFinalFlagSet()) {
            return new InlinedGetterNode(inlinedMethod, opCode, curBCI);
        } else {
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
        Object result = getField(receiver, this.field, kind);
        return (resultAt - top) + root.putKind(frame, resultAt, result, kind);
    }

    protected static Object getField(StaticObject receiver, Field f, JavaKind k) {
        // @formatter:off
        switch (k) {
            case Boolean: return receiver.getBooleanField(f);
            case Byte:    return receiver.getByteField(f);
            case Short:   return receiver.getShortField(f);
            case Char:    return receiver.getCharField(f);
            case Int:     return receiver.getIntField(f);
            case Float:   return receiver.getFloatField(f);
            case Long:    return receiver.getLongField(f);
            case Double:  return receiver.getDoubleField(f);
            case Object:  return receiver.getField(f);
            default:
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    private static Field getInlinedField(Method inlinedMethod) {
        BytecodeStream code = new BytecodeStream(inlinedMethod.getCode());
        if (inlinedMethod.isStatic()) {
            return inlinedMethod.getRuntimeConstantPool().resolvedFieldAt(inlinedMethod.getDeclaringKlass(), code.readCPI(0));
        } else {
            return inlinedMethod.getRuntimeConstantPool().resolvedFieldAt(inlinedMethod.getDeclaringKlass(), code.readCPI(1));
        }
    }
}
