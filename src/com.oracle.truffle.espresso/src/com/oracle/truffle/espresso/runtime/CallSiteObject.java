package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.classfile.MethodHandleConstant;
import com.oracle.truffle.espresso.classfile.MethodHandleConstant.RefKind;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;

public final class CallSiteObject extends StaticObject {

    private final Method target;
    private Object[] args;
    private final Symbol<Type>[] signature;
    private final DirectCallNode callNode;
    private final RefKind kind;
    private final Boolean hasReceiver;
    private final int stackEffect;
    private final int endModif;

    public CallSiteObject(Klass klass, Method target, Symbol<Type>[] signature, Object[] args, MethodHandleConstant mh) {
        super(klass);
        this.target = target;
        this.signature = signature;
        this.args = args;
        this.callNode = DirectCallNode.create(target.getCallTarget());
        this.kind = mh.getRefKind();
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

    public int getStackEffect() {
        return this.stackEffect;
    }

    public int getEndModif() {
        return this.endModif;
    }
}
