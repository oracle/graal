package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.bytecode.InterpreterToVM;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;

public class Utils {

    public static EspressoContext getContext() {
        EspressoContext context = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<EspressoContext>() {
            @Override
            public EspressoContext visitFrame(FrameInstance frameInstance) {
                RootCallTarget callTarget = (RootCallTarget) frameInstance.getCallTarget();
                RootNode rootNode = callTarget.getRootNode();
                if (rootNode instanceof EspressoRootNode) {
                    return ((EspressoRootNode) rootNode).getMethod().getContext();
                }
                return null;
            }
        });

        assert context != null;
        return context;
        // Native (intrinsics) callers are not supported.
        // throw EspressoError.unimplemented();
    }

    public static InterpreterToVM getVm() {
        return getContext().getVm();
    }

    public static Object maybeNull(Object obj) {
        return (obj == null) ? StaticObject.NULL : obj;
    }
}
