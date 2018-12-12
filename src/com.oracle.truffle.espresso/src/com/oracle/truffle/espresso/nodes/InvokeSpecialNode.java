package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.bytecode.OperandStack;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class InvokeSpecialNode extends InvokeNode {
    protected final MethodInfo method;
    @Child private DirectCallNode directCallNode;

    public InvokeSpecialNode(MethodInfo method) {
        this.method = method;
        this.directCallNode = DirectCallNode.create(method.getCallTarget());
    }

    private Object nullCheck(Object value) {
        if (StaticObject.isNull(value)) {
            CompilerDirectives.transferToInterpreter();
            // TODO(peterssen): Profile whether null was hit or not.
            Meta meta = method.getDeclaringClass().getContext().getMeta();
            throw meta.throwEx(NullPointerException.class);
        }
        return value;
    }

    @Override
    public void invoke(OperandStack stack) {
        // TODO(peterssen): Constant fold this check.
        nullCheck(stack.peekReceiver(method));
        Object[] arguments = stack.popArguments(true, method.getSignature());
        Object result = directCallNode.call(arguments);
        stack.pushKind(result, method.getSignature().getReturnTypeDescriptor().toKind());
    }
}
