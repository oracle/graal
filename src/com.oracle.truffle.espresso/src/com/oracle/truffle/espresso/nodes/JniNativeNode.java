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
import com.oracle.truffle.espresso.runtime.EspressoContext;

public class JniNativeNode extends RootNode {

    private final TruffleObject boundNative;
    @Child Node execute = Message.EXECUTE.createNode();

    public JniNativeNode(TruffleLanguage<?> language, TruffleObject boundNative) {
        super(language);
        this.boundNative = boundNative;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            // TODO(peterssen): Inject JNIEnv properly, without copying.
            // The frame.getArguments().length must match the arity of the native method, which is constant.
            // Having a constant length would help PEA to skip the copying.
            Object[] argsWithEnv = prependEnv(frame.getArguments());
            return ForeignAccess.sendExecute(execute, boundNative, argsWithEnv);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere();
        }
    }

    private static Object[] prependEnv(Object[] args) {
        Object[] argsWithEnv = new Object[args.length + 1];
        System.arraycopy(args, 0, argsWithEnv, 1, args.length);
        JniEnv jniEnv = EspressoLanguage.getCurrentContext().getJniEnv();
        assert jniEnv.getJniEnvPtr() != 0;
        argsWithEnv[0] = jniEnv.getJniEnvPtr();
        return argsWithEnv;
    }
}
