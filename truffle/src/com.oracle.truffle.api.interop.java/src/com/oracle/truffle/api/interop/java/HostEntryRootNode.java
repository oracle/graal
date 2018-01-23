package com.oracle.truffle.api.interop.java;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExecutableNode;

public abstract class HostEntryRootNode<T> extends ExecutableNode {

    HostEntryRootNode() {
        super(null);
    }

    protected abstract Class<? extends T> getReceiverType();

    @Override
    public final Object execute(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        Object languageContext = arguments[0];
        T receiver = getReceiverType().cast(arguments[1]);
        Object result;
        result = executeImpl(languageContext, receiver, arguments, 2);
        assert !(result instanceof TruffleObject);
        return result;
    }

    protected static final RuntimeException newNullPointerException(String message) {
        CompilerDirectives.transferToInterpreter();
        return JavaInterop.ACCESSOR.engine().newNullPointerException(message, null);
    }

    protected static final RuntimeException newUnsupportedOperationException(String message) {
        CompilerDirectives.transferToInterpreter();
        return JavaInterop.ACCESSOR.engine().newUnsupportedOperationException(message, null);
    }

    protected static final RuntimeException newClassCastException(String message) {
        CompilerDirectives.transferToInterpreter();
        return JavaInterop.ACCESSOR.engine().newClassCastException(message, null);
    }

    protected static final RuntimeException newIllegalArgumentException(String message) {
        CompilerDirectives.transferToInterpreter();
        return JavaInterop.ACCESSOR.engine().newIllegalArgumentException(message, null);
    }

    protected static final RuntimeException newArrayIndexOutOfBounds(String message) {
        CompilerDirectives.transferToInterpreter();
        return JavaInterop.ACCESSOR.engine().newArrayIndexOutOfBounds(message, null);
    }

    protected abstract Object executeImpl(Object languageContext, T receiver, Object[] args, int offset);

}
