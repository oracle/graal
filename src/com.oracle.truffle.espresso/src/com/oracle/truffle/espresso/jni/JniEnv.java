package com.oracle.truffle.espresso.jni;

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Object;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.nfi.types.NativeSimpleType;

public class JniEnv {

    // Load native library nespresso.dll (Windows) or libnespresso.so (Unixes) at runtime.
    private static final TruffleObject nespressoLibrary = NativeLibrary.loadLibrary(System.getProperty("nespresso.library", "nespresso"));

    private static final TruffleObject initializeNativeContext = NativeLibrary.lookupAndBind(nespressoLibrary,
                    "initializeNativeContext", "(env, (string): pointer): pointer");

    private static final TruffleObject dupClosureRef = NativeLibrary.lookup(nespressoLibrary, "dupClosureRef");

    private static final TruffleObject disposeNativeContext = NativeLibrary.lookupAndBind(nespressoLibrary, "disposeNativeContext",
                    "(env, pointer): void");

    private static final Map<Class<?>, NativeSimpleType> classToNative = buildClassToNative();
    private static final Map<String, Method> jniMethods = buildJniMethods();

    private long jniEnvPtr;

    private JniEnv() {
        try {
            Callback lookupJniImplCallback = Callback.wrapInstanceMethod(this, "lookupJniImpl", String.class);
            this.jniEnvPtr = unwrapPointer(ForeignAccess.sendExecute(Message.EXECUTE.createNode(), initializeNativeContext, lookupJniImplCallback));
            assert this.jniEnvPtr != 0;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot initialize Espresso native interface");
        }
    }

    private static Map<Class<?>, NativeSimpleType> buildClassToNative() {
        Map<Class<?>, NativeSimpleType> map = new HashMap<>();
        map.put(boolean.class, NativeSimpleType.UINT8);
        map.put(byte.class, NativeSimpleType.SINT8);
        map.put(short.class, NativeSimpleType.SINT16);
        map.put(char.class, NativeSimpleType.UINT16);
        map.put(int.class, NativeSimpleType.SINT32);
        map.put(float.class, NativeSimpleType.FLOAT);
        map.put(long.class, NativeSimpleType.SINT64);
        map.put(double.class, NativeSimpleType.DOUBLE);
        map.put(void.class, NativeSimpleType.VOID);
        map.put(String.class, NativeSimpleType.STRING);
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, Method> buildJniMethods() {
        Map<String, Method> map = new HashMap<>();
        Method[] declaredMethods = JniEnv.class.getDeclaredMethods();
        for (Method method : declaredMethods) {
            JniImpl jniImpl = method.getAnnotation(JniImpl.class);
            if (jniImpl != null) {
                assert !map.containsKey(method.getName()) : "JniImpl for " + method + " already exists";
                map.put(method.getName(), method);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static long unwrapPointer(Object nativePointer) {
        try {
            return ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), (TruffleObject) nativePointer);
        } catch (UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    private static TruffleObject dupClosureRefAndCast(String signature) {
        // TODO(peterssen): Cache binding per signature.
        return NativeLibrary.bind(dupClosureRef, "(env, " + signature + ")" + ": pointer");
    }

    private static NativeSimpleType kindToType(Class<?> clazz) {
        return classToNative.getOrDefault(clazz, NativeSimpleType.OBJECT);
    }

    private static String nativeSignature(Method method) {
        StringBuilder sb = new StringBuilder("(");
        // Prepend JNIEnv* . The raw pointer will be substituted by the proper `this` reference.
        sb.append(NativeSimpleType.POINTER);
        for (Class<?> param : method.getParameterTypes()) {
            sb.append(", ").append(kindToType(param));
        }
        sb.append("): ").append(kindToType(method.getReturnType()));
        return sb.toString();
    }

    public static JniEnv create() {
        return new JniEnv();
    }

    TruffleObject lookupJniImpl(String methodName) {
        Method m = jniMethods.get(methodName);
        try {
            // Dummy placeholder for unimplemented/unknown methods.
            if (m == null) {
                System.err.println("Fetching unknown/unimplemented JNI method: " + methodName);
                return (TruffleObject) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), dupClosureRefAndCast("(pointer): void"),
                                new Callback(1, args -> {
                                    System.err.println("Calling unimplemented JNI method: " + methodName);
                                    throw EspressoError.unimplemented("JNI method: " + methodName);
                                }));
            }

            String signature = nativeSignature(m);
            Callback target = jniMethodWrapper(m);
            return (TruffleObject) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), dupClosureRefAndCast(signature), target);

        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    private Callback jniMethodWrapper(Method m) {
        return new Callback(m.getParameterCount() + 1, args -> {
            assert unwrapPointer(args[0]) == getNativePointer() : "Calling " + m + "from alien JniEnv";
            Object[] shiftedArgs = Arrays.copyOfRange(args, 1, args.length);
            assert args.length - 1 == shiftedArgs.length;
            try {
                // Substitute raw pointer by proper `this` reference.
                return m.invoke(JniEnv.this, shiftedArgs);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public long getNativePointer() {
        return jniEnvPtr;
    }

    public void dispose() {
        assert jniEnvPtr == 0L : "JNIEnv already disposed";
        try {
            ForeignAccess.sendExecute(Message.EXECUTE.createNode(), disposeNativeContext, jniEnvPtr);
            jniEnvPtr = 0L;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot initialize Espresso native interface");
        }
        assert jniEnvPtr == 0L;
    }

    @JniImpl
    public int GetVersion() {
        return JniVersion.JNI_VERSION_1_8;
    }

    @JniImpl
    public int GetArrayLength(Object arr) {
        return EspressoLanguage.getCurrentContext().getVm().arrayLength(arr);
    }

    @JniImpl
    public Meta.Field GetFieldID(StaticObjectClass clazz, String name, String signature) {
        return meta((clazz).getMirror()).field(name);
    }

    @JniImpl
    public Meta.Method GetMethodID(StaticObjectClass clazz, String name, String signature) {
        Meta.Method[] methods = meta(clazz.getMirror()).methods(true);
        for (Meta.Method m : methods) {
            if (m.getName().equals(name) && m.rawMethod().getSignature().toString().equals(signature)) {
                return m;
            }
        }
        throw new RuntimeException("Method " + name + " not found");
    }

    @JniImpl
    public Meta.Method GetStaticMethodID(StaticObjectClass clazz, String name, String signature) {
        Meta.Method[] methods = meta(clazz.getMirror()).methods(false);
        for (Meta.Method m : methods) {
            if (m.getName().equals(name) && m.rawMethod().getSignature().toString().equals(signature)) {
                return m;
            }
        }
        throw new RuntimeException("Method " + name + " not found");
    }

    @JniImpl
    public Object GetObjectClass(Object obj) {
        return Target_java_lang_Object.getClass(obj);
    }

    // region Get*Field

    @JniImpl
    public Object GetObjectField(StaticObject obj, Meta.Field fieldID) {
        return fieldID.get(obj);
    }

    @JniImpl
    public boolean GetBooleanField(StaticObject object, Meta.Field field) {
        return (boolean) field.get(object);
    }

    @JniImpl
    public byte GetByteField(StaticObject object, Meta.Field field) {
        return (byte) field.get(object);
    }

    @JniImpl
    public char GetCharField(StaticObject object, Meta.Field field) {
        return (char) field.get(object);
    }

    @JniImpl
    public short GetShortField(StaticObject object, Meta.Field field) {
        return (short) field.get(object);
    }

    @JniImpl
    public int GetIntField(StaticObject object, Meta.Field field) {
        return (int) field.get(object);
    }

    @JniImpl
    public long GetLongField(StaticObject object, Meta.Field field) {
        return (long) field.get(object);
    }

    @JniImpl
    public float GetFloatField(StaticObject object, Meta.Field field) {
        return (float) field.get(object);
    }

    @JniImpl
    public double GetDoubleField(StaticObject object, Meta.Field field) {
        return (double) field.get(object);
    }

    // endregion Get*Field

    // region Set*Field

    @JniImpl
    public void SetObjectField(StaticObject obj, Meta.Field fieldID, Object val) {
        fieldID.set(obj, val);
    }

    @JniImpl
    public void SetBooleanField(StaticObject obj, Meta.Field fieldID, boolean val) {
        fieldID.set(obj, val);
    }

    @JniImpl
    public void SetByteField(StaticObject obj, Meta.Field fieldID, byte val) {
        fieldID.set(obj, val);
    }

    @JniImpl
    public void SetCharField(StaticObject obj, Meta.Field fieldID, char val) {
        fieldID.set(obj, val);
    }

    @JniImpl
    public void SetShortField(StaticObject obj, Meta.Field fieldID, short val) {
        fieldID.set(obj, val);
    }

    @JniImpl
    public void SetIntField(StaticObject obj, Meta.Field fieldID, int val) {
        fieldID.set(obj, val);
    }

    @JniImpl
    public void SetLongField(StaticObject obj, Meta.Field fieldID, long val) {
        fieldID.set(obj, val);
    }

    @JniImpl
    public void SetFloatField(StaticObject obj, Meta.Field fieldID, float val) {
        fieldID.set(obj, val);
    }

    @JniImpl
    public void SetDoubleField(StaticObject obj, Meta.Field fieldID, double val) {
        fieldID.set(obj, val);
    }

    // endregion Set*Field

    // region Call*Method

    @JniImpl
    public Object CallObjectMethod(Object receiver, Meta.Method methodID, long varargsPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public boolean CallBooleanMethod(Object receiver, Meta.Method methodID, long varargsPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public char CallCharMethod(Object receiver, Meta.Method methodID, long varargsPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public byte CallByteMethod(Object receiver, Meta.Method methodID, long varargsPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public short CallShortMethod(Object receiver, Meta.Method methodID, long varargsPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public int CallIntMethod(Object receiver, Meta.Method methodID, long varargsPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public float CallFloatMethod(Object receiver, Meta.Method methodID, long varargsPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public double CallDoubleMethod(Object receiver, Meta.Method methodID, long varargsPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public long CallLongMethod(Object receiver, Meta.Method methodID, long varargsPtr) {
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public void CallVoidMethod(Object receiver, Meta.Method methodID, long varargsPtr) {
        throw EspressoError.unimplemented();
    }

    // endregion Call*Method

    // region CallNonvirtual*Method

    @JniImpl
    public Object CallNonvirtualObjectMethod(Object receiver, Object clazz, Meta.Method methodID, long varargsPtr) {
        return methodID.invoke(receiver, VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public boolean CallNonvirtualBooleanMethod(Object receiver, Object clazz, Meta.Method methodID, long varargsPtr) {
        return (boolean) methodID.invoke(receiver, VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public char CallNonvirtualCharMethod(Object receiver, Object clazz, Meta.Method methodID, long varargsPtr) {
        return (char) methodID.invoke(receiver, VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public byte CallNonvirtualByteMethod(Object receiver, Object clazz, Meta.Method methodID, long varargsPtr) {
        return (byte) methodID.invoke(receiver, VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public short CallNonvirtualShortMethod(Object receiver, Object clazz, Meta.Method methodID, long varargsPtr) {
        return (short) methodID.invoke(receiver, VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public int CallNonvirtualIntMethod(Object receiver, Object clazz, Meta.Method methodID, long varargsPtr) {
        return (int) methodID.invoke(receiver, VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public float CallNonvirtualFloatMethod(Object receiver, Object clazz, Meta.Method methodID, long varargsPtr) {
        return (float) methodID.invoke(receiver, VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public double CallNonvirtualDoubleMethod(Object receiver, Object clazz, Meta.Method methodID, long varargsPtr) {
        return (double) methodID.invoke(receiver, VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public long CallNonvirtualLongMethod(Object receiver, Object clazz, Meta.Method methodID, long varargsPtr) {
        return (long) methodID.invoke(receiver, VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public void CallNonvirtualVoidMethod(Object receiver, Object clazz, Meta.Method methodID, long varargsPtr) {
        methodID.invoke(receiver, VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public Object CallNonvirtualtualObjectMethod(Object receiver, Object clazz, Meta.Method methodID, long varargsPtr) {
        return methodID.invoke(receiver, VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    // endregion CallNonvirtual*Method

    // region CallStatic*Method

    @JniImpl
    public Object CallStaticObjectMethod(Object clazz, Meta.Method methodID, long varargsPtr) {
        return methodID.asStatic().invoke(VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public boolean CallStaticBooleanMethod(Object clazz, Meta.Method methodID, long varargsPtr) {
        return (boolean) methodID.asStatic().invoke(VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public char CallStaticCharMethod(Object clazz, Meta.Method methodID, long varargsPtr) {
        return (char) methodID.asStatic().invoke(VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public byte CallStaticByteMethod(Object clazz, Meta.Method methodID, long varargsPtr) {
        return (byte) methodID.asStatic().invoke(VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public short CallStaticShortMethod(Object clazz, Meta.Method methodID, long varargsPtr) {
        return (short) methodID.asStatic().invoke(VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public int CallStaticIntMethod(Object clazz, Meta.Method methodID, long varargsPtr) {
        return (int) methodID.asStatic().invoke(VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public float CallStaticFloatMethod(Object clazz, Meta.Method methodID, long varargsPtr) {
        return (float) methodID.asStatic().invoke(VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public double CallStaticDoubleMethod(Object clazz, Meta.Method methodID, long varargsPtr) {
        return (double) methodID.asStatic().invoke(VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public long CallStaticLongMethod(Object clazz, Meta.Method methodID, long varargsPtr) {
        return (long) methodID.asStatic().invoke(VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public void CallStaticVoidMethod(Object clazz, Meta.Method methodID, long varargsPtr) {
        methodID.asStatic().invoke(VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    @JniImpl
    public Object CallNonvirtualObjectMethod(Object clazz, Meta.Method methodID, long varargsPtr) {
        return methodID.asStatic().invoke(VarArgs.pop(varargsPtr, methodID.getParameterTypes()));
    }

    // endregion CallStatic*Method
}
