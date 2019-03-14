package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

// Non-constant call site. His target can change, but hopefully, the signature never changes.
public final class InvokeDynamicCallSiteNode extends QuickNode {

    private final StaticObjectImpl callSite;
    private final Meta meta;
    private final Method invoke;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private Symbol<Symbol.Type>[] parsedInvokeSignature;

    InvokeDynamicCallSiteNode(StaticObjectImpl dynamicInvoker, Meta meta, Symbol<Symbol.Signature> invokeSignature, Symbol<Type>[] parsedInvokeSignature) {
        this.callSite = dynamicInvoker;
        this.meta = meta;
        this.parsedInvokeSignature = parsedInvokeSignature;
        this.invoke = meta.MethodHandle.lookupPolysigMethod(Symbol.Name.invokeExact, invokeSignature);
    }

    @Override
    public int invoke(final VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        Object[] args = root.peekArguments(frame, top, false, parsedInvokeSignature);
        Object result = invoke.invokeDirect(callSite.getField(meta.CStarget), args);
        int resultAt = top - Signatures.slotsForParameters(parsedInvokeSignature); // no receiver
        return (resultAt - top) + root.putKind(frame, resultAt, result, Signatures.returnKind(parsedInvokeSignature));
    }
}
