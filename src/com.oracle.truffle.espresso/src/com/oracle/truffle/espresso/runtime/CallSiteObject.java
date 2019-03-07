package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.classfile.MethodHandleConstant.RefKind;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;

public final class CallSiteObject extends StaticObject {

    private final Method target;
    private Object[] args;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final Symbol<Type>[] signature;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final Symbol<Type>[] targetParsedSig;
    private final DirectCallNode callNode;
    private final RefKind kind;
    private final StaticObject emulatedThis;
    private final Boolean hasReceiver;
    private final int stackEffect;
    private final int endModif;
    private final Klass newKlass;
    private final int parameterSlots;
    private final JavaKind toPush;
    private final int capturedArgs;




    //@TruffleBoundary
    public CallSiteObject(Klass klass, Method target, Symbol<Type>[] signature, RefKind mh, StaticObject emulatedThis) {
        super(klass);
        this.target = target;
        this.signature = signature;
        this.callNode = DirectCallNode.create(target.getCallTarget());
        this.kind = mh;
        this.emulatedThis = emulatedThis;
        this.hasReceiver = !(kind == RefKind.INVOKESTATIC);
        int kindEffect;
        int endEffect;
        switch (kind) {
            case INVOKESPECIAL:
                kindEffect = 1;
                endEffect = 0;
                break;
            case NEWINVOKESPECIAL:
                kindEffect = 0;
                endEffect = 1; // Don't pop the created new object (= having dup-ed it)
                break;
            default:
                kindEffect = 0;
                endEffect = 0;
        }
        this.endModif = endEffect;
        this.stackEffect = Signatures.parameterCount(signature, false) - kindEffect - 1; // Always pop CSO
        this.newKlass = target.getDeclaringKlass();
        this.targetParsedSig = target.getParsedSignature();
        this.parameterSlots = Signatures.slotsForParameters(target.getParsedSignature());
        this.toPush = Signatures.returnKind(targetParsedSig);
        this.capturedArgs = Signatures.parameterCount(signature, false);
    }

    public ForeignAccess getForeignAccess() {
        return StaticObjectMessageResolutionForeign.ACCESS;
    }

    @Override
    public boolean isCallSite() {
        return true;
    }

    @Override
    public final Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> methodSignature) {
        Method result = this.getKlass().lookupMethod(methodName, methodSignature);
        if (result.hasBytecodes()) {
            return result;
        } else {
            return target;
        }
    }

    public final RefKind getKind() {
        return kind;
    }

    public final Method getTarget() {
        return target;
    }

    public final Symbol<Type>[] getSignature() {
        return signature;
    }



    public final void setArgs(Object[] args) {
        this.args = args;
    }

    public final Object[] getArgs() {
        return this.args;
    }

    public final Boolean hasReceiver() {
        return this.hasReceiver;
    }

    public final Symbol<Type>[] getTargetParsedSig() {
        return targetParsedSig;
    }

    public final Object call(Object[] targetArgs) {
        return callNode.call(targetArgs);
    }

    public final int invoke(final VirtualFrame frame, int top, BytecodeNode root) {
        if (kind == RefKind.NEWINVOKESPECIAL) {
            //Runnable cNew = () -> {root.putKind(frame, top - 1, createNew(), JavaKind.Object);};
            //CompilerDirectives.interpreterOnly(cNew);
            root.putKind(frame, top - 1, createNew(), JavaKind.Object);
        }
        Object[] targetArgs = root.peekArgumentsWithCSO(frame, top, hasReceiver, this);
        Object result = call(targetArgs);

        JavaKind resKind = toPush;
        if (toPush.isPrimitive() && toPush != JavaKind.Void) {
            result = Meta.box(root.getMeta(), result);
            resKind = JavaKind.Object;
        }

        int resultAt = top - parameterSlots + stackEffect; // pop CSO
        int ret = (resultAt - top) + root.putKind(frame, resultAt, result, resKind);
        return ret + endModif;
    }

    @CompilerDirectives.TruffleBoundary
    private StaticObject createNew() {
        return newKlass.allocateInstance();

    }

    public int getCapturedArgs() {
        return capturedArgs;
    }

    public final StaticObject getEmulatedThis() {
        return emulatedThis;
    }
}
