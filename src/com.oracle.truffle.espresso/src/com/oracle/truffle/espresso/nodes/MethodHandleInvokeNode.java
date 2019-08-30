package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.JavaKind;

public class MethodHandleInvokeNode extends QuickNode {

    private final Method method;

    @CompilationFinal(dimensions = 1) //
    private final Symbol<Type>[] parsedSignature;

    @Child private DirectCallNode callNode;
    private final boolean hasReceiver;
    private final int argCount;
    private final int parameterCount;
    private final JavaKind rKind;

    public MethodHandleInvokeNode(Method method) {
        this.method = method;
        this.parsedSignature = method.getParsedSignature();
        this.hasReceiver = !method.isStatic();
        this.callNode = DirectCallNode.create(method.getCallTarget());
        this.argCount = method.getParameterCount() + (method.isStatic() ? 0 : 1) + (method.isMethodHandleInvokeIntrinsic() ? 1 : 0);
        this.parameterCount = method.getParameterCount();
        this.rKind = method.getReturnKind();
    }

    @Override
    public int invoke(VirtualFrame frame, int top) {
        BytecodeNode root = (BytecodeNode) getParent();
        Object[] args = new Object[argCount];
        if (hasReceiver) {
            args[0] = nullCheck(root.peekReceiver(frame, top, method));
        }
        root.peekAndReleaseBasicArgumentsWithArray(frame, top, parsedSignature, args, parameterCount, hasReceiver ? 1 : 0);
        Object result = unbasic(callNode.call(args), rKind);
        int resultAt = top - Signatures.slotsForParameters(method.getParsedSignature()) - (hasReceiver ? 1 : 0); // -receiver
        return (resultAt - top) + root.putKind(frame, resultAt, result, method.getReturnKind());
    }

    private static Object unbasic(Object obj, JavaKind kind) {
        switch (kind) {
            case Boolean:
                return ((int) obj != 0);
            case Byte:
                return ((byte) (int) obj);
            case Char:
                return ((char) (int) obj);
            case Short:
                return ((short) (int) obj);
            default:
                return obj;
        }
    }
}
