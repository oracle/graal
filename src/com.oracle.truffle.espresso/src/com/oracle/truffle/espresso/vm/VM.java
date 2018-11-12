package com.oracle.truffle.espresso.vm;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.jni.Callback;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.jni.NativeLibrary;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class VM extends NativeEnv {

    private final TruffleObject initializeMokapotContext;
//    private final TruffleObject disposeMokapotContext;

    private final JniEnv jniEnv;

    private long vmPtr;

    // mokapot.dll (Windows) or libmokapot.so (Unixes) is the Espresso implementation of the VM
    // interface (libjvm)
    // Espresso loads all shared libraries in a private namespace (e.g. using dlmopen on Linux).
    // mokapot must be loaded strictly before any other library in the private namespace to
    // linking with HotSpot libjvm (or just linking errors), then libjava is loaded and further
    // system libraries, libzip ...
    private final TruffleObject mokapotLibrary = NativeLibrary.loadLibrary(System.getProperty("mokapot.library", "mokapot"));

    // libjava must be loaded after mokapot.
    private final TruffleObject javaLibrary = NativeLibrary.loadLibrary(System.getProperty("java.library", "java"));

    public TruffleObject getJavaLibrary() {
        return javaLibrary;
    }

    public TruffleObject getMokapotLibrary() {
        return mokapotLibrary;
    }

    private VM(JniEnv jniEnv) {
        this.jniEnv = jniEnv;
        try {
            initializeMokapotContext = NativeLibrary.lookupAndBind(mokapotLibrary,
                            "initializeMokapotContext", "(env, sint64, (string): pointer): sint64");
//
//            disposeMokapotContext = NativeLibrary.lookupAndBind(mokapotLibrary,
//                            "disposeMokapotContext",
//                            "(env, sint64): void");

            Callback lookupVmImplCallback = Callback.wrapInstanceMethod(this, "lookupVmImpl", String.class);
            this.vmPtr = (long) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), initializeMokapotContext, jniEnv.getNativePointer(), lookupVmImplCallback);
            assert this.vmPtr != 0;

        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException | UnknownIdentifierException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static Map<String, Method> buildVmMethods() {
        Map<String, Method> map = new HashMap<>();
        Method[] declaredMethods = JniEnv.class.getDeclaredMethods();
        for (Method method : declaredMethods) {
            VmImpl jniImpl = method.getAnnotation(VmImpl.class);
            if (jniImpl != null) {
                assert !map.containsKey(method.getName()) : "VmImpl for " + method + " already exists";
                map.put(method.getName(), method);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static final Map<String, Method> vmMethods = buildVmMethods();

    public static VM create(JniEnv jniEnv) {
        return new VM(jniEnv);
    }


    public static String vmNativeSignature(Method method) {
        StringBuilder sb = new StringBuilder("(");
        // Prepend JNIEnv* . The raw pointer will be substituted by the proper `this` reference.
        boolean first = true;
        for (Class<?> param : method.getParameterTypes()) {
            if (!first) {
                sb.append(", ");
            } else {
                first = true;
            }
            sb.append(classToType(param, false));
        }
        sb.append("): ").append(classToType(method.getReturnType(), true));
        return sb.toString();
    }

    public TruffleObject lookupVmImpl(String methodName) {
        Method m = vmMethods.get(methodName);
        try {
            // Dummy placeholder for unimplemented/unknown methods.
            if (m == null) {
                System.err.println("Fetching unknown/unimplemented VM method: " + methodName);
                return (TruffleObject) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), jniEnv.dupClosureRefAndCast("(pointer): void"),
                        new Callback(1, args -> {
                            System.err.println("Calling unimplemented VM method: " + methodName);
                            throw EspressoError.unimplemented("VM method: " + methodName);
                        }));
            }

            String signature = vmNativeSignature(m);
            Callback target = vmMethodWrapper(m);
            return (TruffleObject) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), jniEnv.dupClosureRefAndCast(signature), target);

        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    public Callback vmMethodWrapper(Method m) {
        return new Callback(m.getParameterCount() + 1, args -> {
            Class<?>[] params = m.getParameterTypes();

            for (int i = 0; i < args.length; ++i) {
                // FIXME(peterssen): Espresso should accept interop null objects, since it doesn't
                // we must convert to Espresso null.
                // FIXME(peterssen): Also, do use proper nodes.
                if (args[i] instanceof TruffleObject) {
                    if (ForeignAccess.sendIsNull(Message.IS_NULL.createNode(), (TruffleObject) args[i])) {
                        args[i] = StaticObject.NULL;
                    }
                } else {
                    // TruffleNFI pass booleans as byte, do the proper conversion.
                    if (params[i] == boolean.class) {
                        args[i] = ((byte) args[i]) != 0;
                    }
                }
            }
            assert args.length - 1 == args.length;
            try {
                // Substitute raw pointer by proper `this` reference.
// System.err.print("Call DEFINED method: " + m.getName() +
// Arrays.toString(shiftedArgs));
                Object ret = m.invoke(this, args);

                if (ret instanceof Boolean) {
                    return (boolean) ret ? (byte) 1 : (byte) 0;
                }

                if (ret == null && !m.getReturnType().isPrimitive()) {
                    throw EspressoError.shouldNotReachHere("Cannot return host null, only Espresso NULL");
                }

                if (ret == null && m.getReturnType() == void.class) {
                    // Cannot return host null to TruffleNFI.
                    ret = StaticObject.NULL;
                }

// System.err.println(" -> " + ret);

                return ret;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }


    @VmImpl
    public long JVM_CurrentTimeMillis(long jniEnv, StaticObject ignored) {
        return 666;
    }

    @VmImpl
    public long JVM_NanoTime(long jniEnv, StaticObject ignored) {
        return 12345;
    }

    /**
     * (Identity) hash code must be respected for wrappers.
     * The same object could be wrapped by two different instances of StaticObjectWrapper.
     * Wrappers are transparent, it's identity comes from the wrapped object.
     */
    @VmImpl
    public int JVM_IHashCode(long jniEnv, StaticObject object) {
        return System.identityHashCode(MetaUtil.unwrap(object));
    }
}
