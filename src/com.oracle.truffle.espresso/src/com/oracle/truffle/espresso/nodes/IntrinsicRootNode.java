package com.oracle.truffle.espresso.nodes;

import java.lang.invoke.MethodHandle;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;

public class IntrinsicRootNode extends RootNode {

    private final MethodHandle handle;

    public IntrinsicRootNode(EspressoLanguage language, MethodHandle handle) {
        super(language);
        this.handle = handle;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return handle.invokeWithArguments(frame.getArguments());
        } catch (EspressoException wrapped) {
            throw wrapped;
        } catch (Throwable throwable) {
            // Non-espresso exceptions cannot escape to the guest.
            throw new RuntimeException(throwable);
            // throw EspressoError.shouldNotReachHere();
        }
    }
}
