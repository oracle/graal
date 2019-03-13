package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

public class InvokeDynamicNode extends QuickNode {

    //TODO(garcia) Distinguish between constant and non-constant inDy's

    private final StaticObjectImpl callSite;
    private final StaticObjectImpl methodHandle;
    private final boolean isConstantCallSite;
    private final Method invoke;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final Symbol<Type>[] invokeSignature;
    private final StaticObjectImpl target;

    public InvokeDynamicNode(StaticObjectImpl dynamicInvoker, Meta meta) {
        if (meta.MethodHandle.isAssignableFrom(dynamicInvoker.getKlass())) {
            this.callSite = null;
            this.methodHandle = dynamicInvoker;
        } else {
            this.callSite = dynamicInvoker;
            this.methodHandle = null;
        }
        this.isConstantCallSite = callSite == null;
        if (isConstantCallSite) {
            StaticObjectImpl mtype =  (StaticObjectImpl)methodHandle.getField(meta.MHtype);
            String descriptor = Meta.toHostString((StaticObject) meta.toMethodDescriptorString.invokeDirect(mtype));
            Symbol<Symbol.Signature> sig = meta.getSignatures().lookupValidSignature(descriptor);
            this.invoke = meta.MethodHandle.lookupPolysigMethod(Name.invokeExact, sig);
            this.invokeSignature = invoke.getParsedSignature();
            this.target = null;
        } else {
            //TODO(garcia) CallSite's target can change. Caching ?
            this.target = (StaticObjectImpl)callSite.getField(meta.CStarget);
            StaticObjectImpl lform = (StaticObjectImpl) target.getField(meta.form);
            StaticObjectImpl memberName = (StaticObjectImpl) lform.getField(meta.vmentry);
            Method method = ((Method) memberName.getHiddenField("vmtarget"));
            this.invokeSignature = method.getParsedSignature();
            this.invoke = target.getKlass().lookupPolysigMethod(Name.invokeExact, method.getRawSignature());
        }
    }

    @Override
    public int invoke(final VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        Object[] args = root.peekArguments(frame, top, false, invokeSignature);
        Object result;
        if (isConstantCallSite) {
             result = invoke.invokeDirect(methodHandle, args);
        } else {
            // TODO(garcia) Cache
             result = invoke.invokeDirect(target, args);
        }
        int resultAt = top - Signatures.slotsForParameters(invokeSignature); // no receiver
        return (resultAt - top) + root.putKind(frame, resultAt, result, Signatures.returnKind(invokeSignature));
    }
}
