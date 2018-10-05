package com.oracle.truffle.espresso.jni;

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Object;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.nfi.types.NativeSimpleType;

public class JniEnv {

    // Load native library nespresso.dll (Windows) or libnespresso.so (Unixes)
    // at runtime
    private final static TruffleObject nespressoLibrary = loadLibrary(System.getProperty("nespresso.library", "nespresso"));

    private final static TruffleObject createJniEnv = lookupAndBind(nespressoLibrary, "createJniEnv",
                    "(env, (string): pointer): pointer");

    private final static TruffleObject dupClosureRef = lookup(nespressoLibrary, "dupClosureRef");

    private final static TruffleObject disposeJniEnv = lookupAndBind(nespressoLibrary, "disposeJniEnv",
                    "(env): void");

    private static TruffleObject dupClosureRefAndCast(String signature) {
        return bind(dupClosureRef, "(env, " + signature + ")" + ": pointer");
    }

    private long jniEnvPtr;

    final Method[] methods = this.getClass().getDeclaredMethods();

    static Map<Class<?>, NativeSimpleType> classToNative;
    static {
        classToNative = new HashMap<>();
        classToNative.put(boolean.class, NativeSimpleType.UINT8);
        classToNative.put(byte.class, NativeSimpleType.SINT8);
        classToNative.put(short.class, NativeSimpleType.SINT16);
        classToNative.put(char.class, NativeSimpleType.UINT16);
        classToNative.put(int.class, NativeSimpleType.SINT32);
        classToNative.put(float.class, NativeSimpleType.FLOAT);
        classToNative.put(long.class, NativeSimpleType.SINT64);
        classToNative.put(double.class, NativeSimpleType.DOUBLE);
        classToNative.put(void.class, NativeSimpleType.VOID);
        classToNative.put(String.class, NativeSimpleType.STRING);
    }

    private static NativeSimpleType kindToType(Class<?> clazz) {
        return classToNative.getOrDefault(clazz, NativeSimpleType.OBJECT);
    }

    private static String nativeSignature(Method method) {
        StringBuilder sb = new StringBuilder("(").append(NativeSimpleType.POINTER); // Prepend
                                                                                    // JNIEnv.
        for (Class<?> param : method.getParameterTypes()) {
            sb.append(", ").append(kindToType(param));
        }
        sb.append("): ").append(kindToType(method.getReturnType()));
        return sb.toString();
    }

    private JniEnv() {
        try {
            TruffleObject ptr = (TruffleObject) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), createJniEnv,
                            new Callback(1, lookupArgs -> {
                                String name = (String) lookupArgs[0];
                                try {
                                    for (Method m : methods) {
                                        if (m.getAnnotation(JniMethod.class) != null) {
                                            if (m.getName().equals(name)) {
                                                return ForeignAccess.sendExecute(Message.EXECUTE.createNode(),
                                                                dupClosureRefAndCast(nativeSignature(m)),
                                                                new Callback(m.getParameterCount() + 1, args -> {
                                                                    Object[] shiftedArgs = new Object[m.getParameterCount()];
                                                                    assert args.length - 1 == shiftedArgs.length;
                                                                    System.arraycopy(args, 1, shiftedArgs, 0, shiftedArgs.length);
                                                                    try {
                                                                        return m.invoke(JniEnv.this, shiftedArgs);
                                                                    } catch (IllegalAccessException | InvocationTargetException e) {
                                                                        throw new RuntimeException(e);
                                                                    }
                                                                }));
                                            }
                                        }
                                    }

                                    final String methodName = name;

                                    return ForeignAccess.sendExecute(Message.EXECUTE.createNode(),
                                                    dupClosureRefAndCast("(pointer): void"),
                                                    new Callback(1, args -> {
                                                        System.err.println("Called JNI method: " + methodName);
                                                        throw EspressoError.unimplemented("JNI method: " + methodName);
                                                    }));

                                    // throw EspressoError.unimplemented("JNI method: " + name);
                                } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
                                    throw EspressoError.shouldNotReachHere();
                                }
                            }));

            this.jniEnvPtr = (long) ForeignAccess.sendUnbox(Message.UNBOX.createNode(), ptr);
            assert this.jniEnvPtr != 0;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot initialize Espresso native interface");
        }
    }

    private static TruffleObject lookup(TruffleObject library, String method) {
        try {
            return (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), library, method);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot find " + method);
        }
    }

    private static TruffleObject bind(TruffleObject symbol, String signature) {
        try {
            return (TruffleObject) ForeignAccess.sendInvoke(Message.INVOKE.createNode(), symbol, "bind", signature);
        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot bind 'createJniEnv'");
        }
    }

    private static TruffleObject lookupAndBind(TruffleObject library, String method, String signature) {
        try {
            TruffleObject symbol = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), library, method);
            return (TruffleObject) ForeignAccess.sendInvoke(Message.INVOKE.createNode(), symbol, "bind", signature);
        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot bind 'createJniEnv'");
        }
    }

    public static JniEnv create() {
        return new JniEnv();
    }

    private static TruffleObject loadLibrary(String lib) {
        Source source = Source.newBuilder("nfi", String.format("load(RTLD_LAZY) '%s'", lib), "loadLibrary").build();
        CallTarget target = EspressoLanguage.getCurrentContext().getEnv().parse(source);
        return (TruffleObject) target.call();
    }

    public long getJniEnvPtr() {
        return jniEnvPtr;
    }

    public void dispose() {
        assert jniEnvPtr == 0L : "JNIEnv already disposed";
        try {
            ForeignAccess.sendExecute(Message.EXECUTE.createNode(), disposeJniEnv, jniEnvPtr);
            jniEnvPtr = 0L;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot initialize Espresso native interface");
        }
        assert jniEnvPtr == 0L;
    }

    @JniMethod
    public int GetVersion() {
        return 0x00001001;
    }

    @JniMethod
    public int GetArrayLength(Object arr) {
        return EspressoLanguage.getCurrentContext().getVm().arrayLength(arr);
    }

    @JniMethod
    public Meta.Field GetFieldID(StaticObjectClass clazz, String name, String signature) {
        return meta((clazz).getMirror()).field(name);
    }

    @JniMethod
    public Meta.Method GetMethodID(StaticObjectClass clazz, String name, String signature) {
        Meta.Method[] methods = meta(clazz.getMirror()).methods(true);
        for (Meta.Method m : methods) {
            if (m.getName().equals(name) && m.rawMethod().getSignature().toString().equals(signature)) {
                return m;
            }
        }
        throw new RuntimeException("Method " + name + " not found");
    }

    @JniMethod
    public Object GetObjectField(StaticObject obj, Meta.Field fieldID) {
        return fieldID.get(obj);
    }

    @JniMethod
    public StaticObject GetObjectClass(Object obj) {
        return Target_java_lang_Object.getClass(obj);
    }

    @JniMethod
    public boolean GetBooleanField(StaticObject object, Meta.Field field) {
        return (boolean) field.get(object);
    }

    @JniMethod
    public byte GetByteField(StaticObject object, Meta.Field field) {
        return (byte) field.get(object);
    }

    @JniMethod
    public char GetCharField(StaticObject object, Meta.Field field) {
        return (char) field.get(object);
    }

    @JniMethod
    public short GetShortField(StaticObject object, Meta.Field field) {
        return (short) field.get(object);
    }

    @JniMethod
    public int GetIntField(StaticObject object, Meta.Field field) {
        return (int) field.get(object);
    }

    @JniMethod
    public long GetLongField(StaticObject object, Meta.Field field) {
        return (long) field.get(object);
    }

    @JniMethod
    public float GetFloatField(StaticObject object, Meta.Field field) {
        return (float) field.get(object);
    }

    @JniMethod
    public double GetDoubleField(StaticObject object, Meta.Field field) {
        return (double) field.get(object);
    }

    @JniMethod
    public void SetObjectField(StaticObject obj, Meta.Field fieldID, Object val) {
        fieldID.set(obj, val);
    }

    @JniMethod
    public void SetBooleanField(StaticObject obj, Meta.Field fieldID, boolean val) {
        fieldID.set(obj, val);
    }

    @JniMethod
    public void SetByteField(StaticObject obj, Meta.Field fieldID, byte val) {
        fieldID.set(obj, val);
    }

    @JniMethod
    public void SetCharField(StaticObject obj, Meta.Field fieldID, char val) {
        fieldID.set(obj, val);
    }

    @JniMethod
    public void SetShortField(StaticObject obj, Meta.Field fieldID, short val) {
        fieldID.set(obj, val);
    }

    @JniMethod
    public void SetIntField(StaticObject obj, Meta.Field fieldID, int val) {
        fieldID.set(obj, val);
    }

    @JniMethod
    public void SetLongField(StaticObject obj, Meta.Field fieldID, long val) {
        fieldID.set(obj, val);
    }

    @JniMethod
    public void SetFloatField(StaticObject obj, Meta.Field fieldID, float val) {
        fieldID.set(obj, val);
    }

    @JniMethod
    public void SetDoubleField(StaticObject obj, Meta.Field fieldID, double val) {
        fieldID.set(obj, val);
    }

    @JniMethod
    public int EnsureLocalCapacity(int capacity) {
        // TODO(peterssen): Do check via TruffleNFI.
        return 0;
    }

    @JniMethod
    public StaticObject FindClass(String name) {
        return StaticObject.NULL;
    }

    @JniMethod
    public double CallStaticDoubleMethodV(StaticObject clazz, Meta.Method methodID, Object[] args) {
        return 0.0;
    }

    @JniMethod
    public float CallStaticFloatMethodV(StaticObject clazz, Meta.Method methodID, Object[] args) {
        return 0.0f;
    }

    @JniMethod
    public long CallStaticLongMethodV(StaticObject clazz, Meta.Method methodID, Object[] args) {
        return 0L;
    }

    @JniMethod
    public int CallStaticIntMethodV(StaticObject clazz, Meta.Method methodID, Object[] args) {
        return 0;
    }

    @JniMethod
    public byte CallStaticByteMethodV(StaticObject clazz, Meta.Method methodID, Object[] args) {
        return 0;
    }

    @JniMethod
    public short CallStaticShortMethodV(StaticObject clazz, Meta.Method methodID, Object[] args) {
        return 0;
    }

    @JniMethod
    public char CallStaticCharMethodV(StaticObject clazz, Meta.Method methodID, Object[] args) {
        return 0;
    }

    @JniMethod
    public boolean CallStaticBooleanMethodV(StaticObject clazz, Meta.Method methodID, Object[] args) {
        return false;
    }

    @JniMethod
    public Object CallStaticObjectMethodV(StaticObject clazz, Meta.Method methodID, Object[] args) {
        return StaticObject.NULL;
    }
}
