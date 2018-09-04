package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.bytecode.InterpreterToVM;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;

public class Utils {

    public static EspressoContext getContext() {
        return getCallerNode().getMethod().getDeclaringClass().getContext();
    }

    public static EspressoRootNode getCallerNode() {
        RootCallTarget callTarget = (RootCallTarget) Truffle.getRuntime().getCallerFrame().getCallTarget();
        RootNode rootNode = callTarget.getRootNode();
        if (rootNode instanceof EspressoRootNode) {
            return (EspressoRootNode) rootNode;
        }
        // Native (intrinsics) callers are not supported.
        throw EspressoError.unimplemented();
    }

    public static InterpreterToVM getVm() {
        return getCallerNode().getVm();
    }

    public static Object maybeNull(Object obj) {
        return (obj == null) ? StaticObject.NULL : obj;
    }
}
