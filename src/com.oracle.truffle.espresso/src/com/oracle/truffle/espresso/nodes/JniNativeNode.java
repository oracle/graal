package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public class JniNativeNode extends RootNode {

    private final TruffleObject boundNative;
    private final Meta.Method originalMethod;
    @Child Node execute = Message.EXECUTE.createNode();

    public JniNativeNode(TruffleLanguage<?> language, TruffleObject boundNative, Meta.Method originalMethod) {
        super(language);
        this.boundNative = boundNative;
        this.originalMethod = originalMethod;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            // TODO(peterssen): Inject JNIEnv properly, without copying.
            // The frame.getArguments().length must match the arity of the native method, which is constant.
            // Having a constant length would help PEA to skip the copying.

            JniEnv jniEnv = EspressoLanguage.getCurrentContext().getJniEnv();
            assert jniEnv.getJniEnvPtr() != 0;

            Object[] argsWithEnv = originalMethod.isStatic()
                    ? prepend2(jniEnv.getJniEnvPtr(), originalMethod.getDeclaringClass().rawKlass().mirror(), frame.getArguments())
                    : prepend1(jniEnv.getJniEnvPtr(), frame.getArguments());

            return ForeignAccess.sendExecute(execute, boundNative, argsWithEnv);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere();
        }
    }

    private static Object[] prepend1(Object first, Object... args) {
        Object[] newArgs = new Object[args.length + 1];
        System.arraycopy(args, 0, newArgs, 1, args.length);
        newArgs[0] = first;
        return newArgs;
    }

    private static Object[] prepend2(Object first, Object second, Object... args) {
        Object[] newArgs = new Object[args.length + 2];
        System.arraycopy(args, 0, newArgs, 2, args.length);
        newArgs[0] = first;
        newArgs[1] = second;
        return newArgs;
    }
}
