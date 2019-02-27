package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.nodes.DirectCallNode;
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
    private final Symbol<Type>[] signature;
    private final DirectCallNode callNode;
    private final RefKind kind;
    private final StaticObject emulatedThis;
    private final Boolean hasReceiver;
    private final int stackEffect;
    private final int endModif;

    @CompilerDirectives.TruffleBoundary
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
                endEffect = 1;
                break;
            default:
                kindEffect = 0;
                endEffect = 0;
        }
        this.endModif = endEffect;
        this.stackEffect = Signatures.parameterCount(signature, false) - kindEffect - 1; // Always
                                                                                         // pop CSO
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

    public final Object call(Object[] targetArgs) {
        return callNode.call(targetArgs);
    }

    public final void setArgs(Object[] args) {
        this.args = args;
    }

    public Object[] getArgs() {
        return this.args;
    }

    public final Boolean hasReceiver() {
        return this.hasReceiver;
    }

    public final int invoke(final VirtualFrame frame, int top, BytecodeNode root) {
        if (kind == RefKind.NEWINVOKESPECIAL) {
            Klass klass = target.getDeclaringKlass();
            klass.safeInitialize();
            root.putKind(frame, top - 1, klass.allocateInstance(), JavaKind.Object);
        }
        Object[] targetArgs = root.peekArgumentsWithCSO(frame, top, hasReceiver, target.getParsedSignature(), this);
        Object result = call(targetArgs);
        JavaKind toPush = Signatures.returnKind(target.getParsedSignature());
        if (toPush != JavaKind.Void && toPush.isPrimitive()) {
            result = Meta.box(root.getMeta(), result);
            toPush = JavaKind.Object;
        }
        int resultAt = top - Signatures.slotsForParameters(target.getParsedSignature()) + stackEffect; // pop
                                                                                                       // CSO
        int ret = (resultAt - top) + root.putKind(frame, resultAt, result, toPush);
        // return (resultAt - top) + root.putKind(frame, resultAt, result, method.getReturnKind());
        // //
        return ret + endModif;
    }

    public StaticObject getEmulatedThis() {
        return emulatedThis;
    }
}
