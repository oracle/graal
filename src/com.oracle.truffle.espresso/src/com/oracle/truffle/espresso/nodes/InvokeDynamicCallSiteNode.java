package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

// Non-constant call site. His target can change, but hopefully, the signature never changes.
public final class InvokeDynamicCallSiteNode extends QuickNode {

    private final Method target;
    private final StaticObject appendix;
    private final boolean hasAppendix;
    @Child private DirectCallNode callNode;

    private final Meta meta;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private Symbol<Symbol.Type>[] parsedSignature;

    InvokeDynamicCallSiteNode(StaticObjectImpl memberName, StaticObject appendix, Meta meta, Symbol<Type>[] parsedSignature) {
        this.target = (Method) memberName.getHiddenField("vmtarget");
        this.appendix = appendix;
        this.meta = meta;
        this.parsedSignature = parsedSignature;
        this.hasAppendix = appendix != StaticObject.NULL;
    }

    @Override
    public int invoke(final VirtualFrame frame, int top) {
        if (callNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            target.getDeclaringKlass().initialize();
            callNode = DirectCallNode.create(target.getCallTarget());
        }
        BytecodeNode root = (BytecodeNode) getParent();
        int argCount = Signatures.parameterCount(parsedSignature, false);
        Object[] args = root.peekArgumentsWithArray(frame, top, parsedSignature, new Object[argCount + (hasAppendix ? 1 : 0)], argCount);
        if (hasAppendix) {
            args[args.length - 1] = appendix;
        }
        Object result = callNode.call(args);
        int resultAt = top - Signatures.slotsForParameters(parsedSignature); // no receiver
        return (resultAt - top) + root.putKind(frame, resultAt, result, Signatures.returnKind(parsedSignature));
    }
}
