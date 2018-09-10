package com.oracle.truffle.espresso.nodes;

import java.lang.reflect.Method;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.runtime.EspressoException;

public class IntrinsicReflectionRootNode extends RootNode {

    private final Method method;

    public IntrinsicReflectionRootNode(EspressoLanguage language, Method method) {
        super(language);
        this.method = method;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return callIntrinsic(frame.getArguments());
        } catch (EspressoException wrapped) {
            throw wrapped;
        } catch (Throwable throwable) {
            CompilerDirectives.transferToInterpreter();
            // Non-espresso exceptions cannot escape to the guest.
            throw new RuntimeException(throwable);
            // throw EspressoError.shouldNotReachHere();
        }
    }

    @CompilerDirectives.TruffleBoundary
    private Object callIntrinsic(Object... args) throws Throwable {
        return method.invoke(null, args);
    }
}
