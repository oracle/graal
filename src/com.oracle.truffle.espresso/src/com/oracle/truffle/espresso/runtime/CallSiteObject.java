package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.classfile.MethodHandleConstant;
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
import com.oracle.truffle.espresso.vm.InterpreterToVM;

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

    public CallSiteObject(Klass klass, Method target, Symbol<Type>[] signature, Object[] args, MethodHandleConstant mh, StaticObject emulatedThis) {
        super(klass);
        this.target = target;
        this.signature = signature;
        this.args = args;
        this.callNode = DirectCallNode.create(target.getCallTarget());
        this.kind = mh.getRefKind();
        this.emulatedThis = emulatedThis;
        this.hasReceiver = !(kind == RefKind.INVOKESTATIC);
        int kindEffect;
        int endEffect;
        switch (kind) {
            case INVOKESPECIAL      :
                kindEffect = 1;
                endEffect = 0;
                break;
            case NEWINVOKESPECIAL   :
                kindEffect = 0;
                endEffect = 1;
                break;
            default:
                kindEffect = 0;
                endEffect = 0;
        }
        this.endModif = endEffect;
        this.stackEffect = args.length - kindEffect - 1; // Always pop CSO
    }

    public ForeignAccess getForeignAccess() {
        return StaticObjectMessageResolutionForeign.ACCESS;
    }

    @Override
    public boolean isCallSite() {return true;}

    @Override
    public final Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> methodSignature) {
        Method result = this.getKlass().lookupMethod(methodName, methodSignature);
        if (result.hasBytecodes()) {
            return result;
        } else {
            return target;
        }
    }

    public RefKind getKind() {
        return kind;
    }

    public Method getTarget() {
        return target;
    }

    public Symbol<Type>[] getSignature() {
        return signature;
    }

    public Object call(Object[] targetArgs) {
        return callNode.call(targetArgs);
    }

    public Object[] getArgs() {
        return this.args;
    }

    public Boolean hasReceiver() {
        return this.hasReceiver;
    }

    public int invoke(final VirtualFrame frame, int top, BytecodeNode root) {
        if (kind == MethodHandleConstant.RefKind.NEWINVOKESPECIAL) {
            Klass klass = target.getDeclaringKlass();
            klass.safeInitialize();
            root.putKind(frame, top - 1, InterpreterToVM.newObject(klass), JavaKind.Object);
        }
        Object[] targetArgs = root.peekArgumentsWithCSO(frame, top, hasReceiver, target.getParsedSignature(), this);
        Object result = call(targetArgs);
        JavaKind toPush = Signatures.returnKind(target.getParsedSignature());
        if (toPush != JavaKind.Void && toPush.isPrimitive()) {
            result = Meta.box(root.getMeta(), result);
            toPush = JavaKind.Object;
        }
        int resultAt = top - Signatures.slotsForParameters(target.getParsedSignature()) + stackEffect; // pop CSO
        int ret = (resultAt - top) + root.putKind(frame, resultAt, result, toPush);
        //return (resultAt - top) + root.putKind(frame, resultAt, result, method.getReturnKind()); //
        return ret + endModif;
    }

    public StaticObject getEmulatedThis() {
        return emulatedThis;
    }
}
