package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

public final class InvokeDynamicConstantNode extends QuickNode {

    private final StaticObjectImpl methodHandle;
    private final Method invoke;
    @CompilationFinal(dimensions = 1) Symbol<Type>[] parsedInvokeSignature;

    InvokeDynamicConstantNode(StaticObjectImpl dynamicInvoker, Meta meta, Symbol<Symbol.Signature> invokeSignature, Symbol<Type>[] parsedInvokeSignature) {
        this.methodHandle = dynamicInvoker;
        // StaticObjectImpl mtype = (StaticObjectImpl)methodHandle.getField(meta.MHtype);
        // String descriptor = Meta.toHostString((StaticObject)
        // meta.toMethodDescriptorString.invokeDirect(mtype));
        // Symbol<Symbol.Signature> sig = meta.getSignatures().lookupValidSignature(descriptor);
        this.invoke = meta.MethodHandle.lookupPolysigMethod(Symbol.Name.invokeExact, invokeSignature);
        this.parsedInvokeSignature = parsedInvokeSignature;
    }

    @Override
    public int invoke(final VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        Object[] args = root.peekArguments(frame, top, false, parsedInvokeSignature);
        Object result = invoke.invokeDirect(methodHandle, args);
        int resultAt = top - Signatures.slotsForParameters(parsedInvokeSignature); // no receiver
        return (resultAt - top) + root.putKind(frame, resultAt, result, Signatures.returnKind(parsedInvokeSignature));
    }
}
