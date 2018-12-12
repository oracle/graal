package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.espresso.bytecode.OperandStack;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

public abstract class InvokeVirtualNode extends InvokeNode {

    final MethodInfo resolutionSeed;

    static final int INLINE_CACHE_SIZE_LIMIT = 5;

    protected abstract Object executeVirtual(OperandStack stack, Object receiver, Object[] arguments);

    @SuppressWarnings("unused")
    @Specialization(limit = "INLINE_CACHE_SIZE_LIMIT", guards = "receiver.getKlass() == cachedKlass")
    Object callVirtualDirect(OperandStack stack, StaticObjectImpl receiver, Object[] arguments,
                    @Cached("receiver.getKlass()") Klass cachedKlass,
                    @Cached("methodLookup(resolutionSeed, receiver)") MethodInfo resolvedMethod,
                    @Cached("create(resolvedMethod.getCallTarget())") DirectCallNode directCallNode) {
        return directCallNode.call(arguments);
    }

    @Specialization(replaces = "callVirtualDirect")
    Object callVirtualIndirect(OperandStack stack, Object receiver, Object[] arguments,
                    @Cached("create()") IndirectCallNode indirectCallNode) {
        // Brute virtual method resolution, walk the whole klass hierarchy.
        MethodInfo targetMethod = methodLookup(resolutionSeed, receiver);
        return indirectCallNode.call(targetMethod.getCallTarget(), arguments);
    }

    InvokeVirtualNode(MethodInfo resolutionSeed) {
        assert !resolutionSeed.isStatic();
        this.resolutionSeed = resolutionSeed;
    }

    // TODO(peterssen): Make this a node?
    private final Object nullCheck(Object value) {
        if (StaticObject.isNull(value)) {
            CompilerDirectives.transferToInterpreter();
            // TODO(peterssen): Profile whether null was hit or not.
            Meta meta = resolutionSeed.getDeclaringClass().getContext().getMeta();
            throw meta.throwEx(NullPointerException.class);
        }
        return value;
    }

    @TruffleBoundary
    static MethodInfo methodLookup(MethodInfo resolutionSeed, Object receiver) {
        Klass clazz = ((StaticObjectClass) resolutionSeed.getContext().getJNI().GetObjectClass(receiver)).getMirror();
        return clazz.findConcreteMethod(resolutionSeed.getName(), resolutionSeed.getSignature());
    }

    @Override
    public final void invoke(OperandStack stack) {
        // Method signature does not change.
        Object receiver = nullCheck(stack.peekReceiver(resolutionSeed));
        Object[] arguments = stack.popArguments(true, resolutionSeed.getSignature());
        Object result = executeVirtual(stack, receiver, arguments);
        stack.pushKind(result, resolutionSeed.getSignature().getReturnTypeDescriptor().toKind());
    }
}
