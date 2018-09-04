package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;

@EspressoIntrinsics
public class Target_sun_reflect_Reflection {
    @Intrinsic
    public static @Type(Class.class) StaticObject getCallerClass() {
        // TODO(peterssen):
        final int[] depth = new int[]{0};
        CallTarget caller = Truffle.getRuntime().iterateFrames(
                        frameInstance -> (depth[0]++ <= 1) ? null : frameInstance.getCallTarget());

        RootCallTarget callTarget = (RootCallTarget) caller;
        RootNode rootNode = callTarget.getRootNode();
        if (rootNode instanceof EspressoRootNode) {
            return ((EspressoRootNode) rootNode).getMethod().getDeclaringClass().mirror();
        }

        throw EspressoError.shouldNotReachHere();
    }

    @Intrinsic
    public static int getClassAccessFlags(@Type(Class.class) StaticObjectClass clazz) {
        // TODO(peterssen): Investigate access vs. modifiers.
        return clazz.getMirror().getModifiers();
    }
}
