package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class VmNativeNode extends NativeRootNode {

    public VmNativeNode(TruffleLanguage<?> language, TruffleObject boundNative, Meta.Method originalMethod) {
        super(language, boundNative, originalMethod);
    }

    public Object[] preprocessArgs(Object[] args) {
        JniEnv jniEnv = EspressoLanguage.getCurrentContext().getJNI();
        assert jniEnv.getNativePointer() != 0;

        args = super.preprocessArgs(args);

        Object[] argsWithEnv = getOriginalMethod().isStatic()
                ? prepend1(getOriginalMethod().getDeclaringClass().rawKlass().mirror(), args)
                : args;

        return argsWithEnv;
    }
}
