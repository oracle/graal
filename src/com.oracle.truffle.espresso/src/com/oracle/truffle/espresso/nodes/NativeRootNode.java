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
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.util.Arrays;

public abstract class NativeRootNode extends RootNode implements LinkedNode {

    private final TruffleObject boundNative;
    private Meta.Method originalMethod;
    @Node.Child Node execute = Message.EXECUTE.createNode();

    public NativeRootNode(TruffleLanguage<?> language, TruffleObject boundNative) {
        this(language, boundNative, null);
    }

    public NativeRootNode(TruffleLanguage<?> language, TruffleObject boundNative, Meta.Method originalMethod) {
        super(language);
        this.boundNative = boundNative;
        this.originalMethod = originalMethod;
    }

    public Object[] preprocessArgs(Object[] args) {
        Meta.Klass[] params = getOriginalMethod().getParameterTypes();
        // TODO(peterssen): Static method does not get the clazz in the arguments,
        int argIndex = getOriginalMethod().isStatic() ? 0 : 1;
        for (int i = 0; i < params.length; ++i) {
            if (args[argIndex] instanceof Boolean) {
                if (params[i].kind() == JavaKind.Boolean) {
                    args[argIndex] = (boolean) args[argIndex] ? (byte) 1 : (byte) 0;
                }
            }
            ++argIndex;
        }
        return args;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            // TODO(peterssen): Inject JNIEnv properly, without copying.
            // The frame.getArguments().length must match the arity of the native method, which is
            // constant.
            // Having a constant length would help PEA to skip the copying.
            Object[] argsWithEnv = preprocessArgs(frame.getArguments());
            System.err.println("Calling native " + originalMethod.getName() + Arrays.toString(argsWithEnv));
            Object result = ForeignAccess.sendExecute(execute, boundNative, argsWithEnv);
            return processResult(result);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        } catch (VirtualMachineError | EspressoException allowed) {
            throw allowed;
        } catch (Exception e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public Object processResult(Object result) {
        JniEnv jniEnv = EspressoLanguage.getCurrentContext().getJNI();
        assert jniEnv.getNativePointer() != 0;

        // JNI exception handling.
        StaticObject ex = jniEnv.getThreadLocalPendingException().get();
        if (ex != null) {
            jniEnv.getThreadLocalPendingException().clear();
            throw new EspressoException(ex);
        }

        switch (getOriginalMethod().getReturnType().kind()) {
            case Boolean:
            case Byte:
                result = (int) (byte) result;
                break;
            case Char:
                result = (int) (char) result;
                break;
            case Short:
                result = (int) (short) result;
                break;
            case Object:
                if (result instanceof TruffleObject) {
                    if (ForeignAccess.sendIsNull(Message.IS_NULL.createNode(), (TruffleObject) result)) {
                        result = StaticObject.NULL;
                    }
                }
                break;
        }
        // System.err.println("Return native " + originalMethod.getName() + " -> " + result);
        return result;
    }

    protected static Object[] prepend1(Object first, Object... args) {
        Object[] newArgs = new Object[args.length + 1];
        System.arraycopy(args, 0, newArgs, 1, args.length);
        newArgs[0] = first;
        return newArgs;
    }

    protected static Object[] prepend2(Object first, Object second, Object... args) {
        Object[] newArgs = new Object[args.length + 2];
        System.arraycopy(args, 0, newArgs, 2, args.length);
        newArgs[0] = first;
        newArgs[1] = second;
        return newArgs;
    }

    @Override
    public Meta.Method getOriginalMethod() {
        return originalMethod;
    }

    public void setOriginalMethod(Meta.Method originalMethod) {
        this.originalMethod = originalMethod;
    }

    public TruffleObject getBoundNative() {
        return boundNative;
    }
}
