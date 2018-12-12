package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.bytecode.OperandStack;
import com.oracle.truffle.espresso.impl.MethodInfo;

public final class InvokeStaticNode extends InvokeNode {
    protected final MethodInfo method;
    @Child private DirectCallNode directCallNode;

    public InvokeStaticNode(MethodInfo method) {
        assert method.isStatic();
        this.method = method;
        this.directCallNode = DirectCallNode.create(method.getCallTarget());
    }

    @Override
    public void invoke(OperandStack stack) {
        // TODO(peterssen): Constant fold this check.
        method.getDeclaringClass().initialize();
        Object[] arguments = stack.popArguments(false, method.getSignature());
        Object result = directCallNode.call(arguments);
        stack.pushKind(result, method.getSignature().getReturnTypeDescriptor().toKind());
    }
}
