/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
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
    private static final TruffleObject mokapotLibrary = NativeLibrary.loadLibrary(System.getProperty("mokapot.library", "mokapot"));

    public static void touch() {}

    // Load native library nespresso.dll (Windows) or libnespresso.so (Unixes) at runtime.
    private static final TruffleObject nespressoLibrary = NativeLibrary.loadLibrary(System.getProperty("nespresso.library", "nespresso"));
    private static final TruffleObject javaLibrary = NativeLibrary.loadLibrary(System.getProperty("java.library", "java"));

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
            assert unwrapPointer(args[0]) == getNativePointer() : "Calling " + m + " from alien JniEnv";
            Object[] shiftedArgs = Arrays.copyOfRange(args, 1, args.length);

            Class<?>[] params = m.getParameterTypes();

            for (int i = 0; i < shiftedArgs.length; ++i) {
                // FIXME(peterssen): Espresso should accept interop null objects, since it doesn't
                // we must convert to Espresso null.
                // FIXME(peterssen): Also, do use proper nodes.
                if (shiftedArgs[i] instanceof TruffleObject) {
                    if (ForeignAccess.sendIsNull(Message.IS_NULL.createNode(), (TruffleObject) shiftedArgs[i])) {
                        shiftedArgs[i] = StaticObject.NULL;
                    }
                } else {
                    // TruffleNFI pass booleans as byte, do the proper conversion.
                    if (params[i] == boolean.class) {
                        shiftedArgs[i] = ((byte) shiftedArgs[i]) != 0;
                    }
                }
            }
            assert args.length - 1 == shiftedArgs.length;
            try {
                // Substitute raw pointer by proper `this` reference.
                // System.err.print("Call DEFINED method: " + m.getName() +
                // Arrays.toString(shiftedArgs));
                Object ret = m.invoke(JniEnv.this, shiftedArgs);
                // System.err.println(" -> " + ret);
                if (ret instanceof Boolean) {
                    return (boolean) ret ? (byte) 1 : (byte) 0;
                }

                if (ret == null && !m.getReturnType().isPrimitive()) {
                    throw EspressoError.shouldNotReachHere("Cannot return host null, only Espresso NULL");
                }
                return ret;
            } catch (Exception e) {
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
        return JniVersion.JNI_VERSION_ESPRESSO;
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
    public Object CallStaticObjectMethod(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return method.asStatic().invokeDirect(VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public boolean CallStaticBooleanMethod(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (boolean) method.asStatic().invokeDirect(VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public char CallStaticCharMethod(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (char) method.asStatic().invokeDirect(VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public byte CallStaticByteMethod(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (byte) method.asStatic().invokeDirect(VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public short CallStaticShortMethod(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (short) method.asStatic().invokeDirect(VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public int CallStaticIntMethod(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (int) method.asStatic().invoke(VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public float CallStaticFloatMethod(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (float) method.asStatic().invoke(VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public double CallStaticDoubleMethod(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (double) method.asStatic().invoke(VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public long CallStaticLongMethod(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (long) method.asStatic().invoke(VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public void CallStaticVoidMethod(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        method.asStatic().invoke(VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    // endregion CallStatic*Method

    // region Get*ArrayRegion

    @JniImpl
    public void GetBooleanArrayRegion(boolean[] array, int start, int len, long bufPtr) {
        ByteBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Byte);
        for (int i = 0; i < len; ++i) {
            buf.put(array[start + i] ? (byte) 1 : (byte) 0);
        }
    }

    @JniImpl
    public void GetCharArrayRegion(char[] array, int start, int len, long bufPtr) {
        CharBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Char).asCharBuffer();
        buf.put(array, start, len);
    }

    @JniImpl
    public void GetByteArrayRegion(byte[] array, int start, int len, long bufPtr) {
        ByteBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Byte);
        buf.put(array, start, len);
    }

    @JniImpl
    public void GetShortArrayRegion(short[] array, int start, int len, long bufPtr) {
        ShortBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Short).asShortBuffer();
        buf.put(array, start, len);
    }

    @JniImpl
    public void GetIntArrayRegion(int[] array, int start, int len, long bufPtr) {
        IntBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Int).asIntBuffer();
        buf.put(array, start, len);
    }

    @JniImpl
    public void GetFloatArrayRegion(float[] array, int start, int len, long bufPtr) {
        FloatBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Float).asFloatBuffer();
        buf.put(array, start, len);
    }

    @JniImpl
    public void GetDoubleArrayRegion(double[] array, int start, int len, long bufPtr) {
        DoubleBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Double).asDoubleBuffer();
        buf.put(array, start, len);
    }

    @JniImpl
    public void GetLongArrayRegion(long[] array, int start, int len, long bufPtr) {
        LongBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Long).asLongBuffer();
        buf.put(array, start, len);
    }

    // endregion Get*ArrayRegion

    // region Set*ArrayRegion

    @JniImpl
    public void SetBooleanArrayRegion(boolean[] array, int start, int len, long bufPtr) {
        ByteBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Byte);
        for (int i = 0; i < len; ++i) {
            array[start + i] = buf.get() != 0;
        }
    }

    @JniImpl
    public void SetCharArrayRegion(char[] array, int start, int len, long bufPtr) {
        CharBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Char).asCharBuffer();
        buf.get(array, start, len);
    }

    @JniImpl
    public void SetByteArrayRegion(byte[] array, int start, int len, long bufPtr) {
        ByteBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Byte);
        buf.get(array, start, len);
    }

    @JniImpl
    public void SetShortArrayRegion(short[] array, int start, int len, long bufPtr) {
        ShortBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Short).asShortBuffer();
        buf.get(array, start, len);
    }

    @JniImpl
    public void SetIntArrayRegion(int[] array, int start, int len, long bufPtr) {
        IntBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Int).asIntBuffer();
        buf.get(array, start, len);
    }

    @JniImpl
    public void SetFloatArrayRegion(float[] array, int start, int len, long bufPtr) {
        FloatBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Float).asFloatBuffer();
        buf.get(array, start, len);
    }

    @JniImpl
    public void SetDoubleArrayRegion(double[] array, int start, int len, long bufPtr) {
        DoubleBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Double).asDoubleBuffer();
        buf.get(array, start, len);
    }

    @JniImpl
    public void SetLongArrayRegion(long[] array, int start, int len, long bufPtr) {
        LongBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Long).asLongBuffer();
        buf.get(array, start, len);
    }

    // endregion Set*ArrayRegion

    // region New*Array

    @JniImpl
    Object NewBooleanArray(int len) {
        return InterpreterToVM.allocateNativeArray((byte) JavaKind.Boolean.getBasicType(), len);
    }

    @JniImpl
    Object NewByteArray(int len) {
        return InterpreterToVM.allocateNativeArray((byte) JavaKind.Byte.getBasicType(), len);
    }

    @JniImpl
    Object NewCharArray(int len) {
        return InterpreterToVM.allocateNativeArray((byte) JavaKind.Char.getBasicType(), len);
    }

    @JniImpl
    Object NewShortArray(int len) {
        return InterpreterToVM.allocateNativeArray((byte) JavaKind.Short.getBasicType(), len);
    }

    @JniImpl
    Object NewIntArray(int len) {
        return InterpreterToVM.allocateNativeArray((byte) JavaKind.Int.getBasicType(), len);
    }

    @JniImpl
    Object NewLongArray(int len) {
        return InterpreterToVM.allocateNativeArray((byte) JavaKind.Long.getBasicType(), len);
    }

    @JniImpl
    Object NewFloatArray(int len) {
        return InterpreterToVM.allocateNativeArray((byte) JavaKind.Float.getBasicType(), len);
    }

    @JniImpl
    Object NewDoubleArray(int len) {
        return InterpreterToVM.allocateNativeArray((byte) JavaKind.Double.getBasicType(), len);
    }

    // endregion New*Array

    @JniImpl
    public void SetStaticObjectField(StaticObjectClass clazz, long fieldHandle, Object value) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        assert field.isStatic();
        field.set(clazz.getMirror().getStatics(), value);
    }

    @JniImpl
    public Object GetObjectClass(Object obj) {
        return Target_java_lang_Object.getClass(obj);
    }

    @JniImpl
    public Object NewObject(StaticObjectClass clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        StaticObject instance = meta(((StaticObjectClass) clazz).getMirror()).allocateInstance();
        assert method.isConstructor();
        method.invokeDirect(instance, VarArgs.pop(varargsPtr, method.getParameterTypes()));
        return instance;
    }

    @JniImpl
    public StaticObject NewStringUTF(String str) {
        // FIXME(peterssen): This relies on TruffleNFI implicit char* -> String conversion that
        // uses host NewStringUTF.
        return EspressoLanguage.getCurrentContext().getMeta().toGuest(str);
    }

    @JniImpl
    public StaticObject FindClass(String name) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        StaticObject internalName = meta.toGuest(MetaUtil.toInternalName(name));
        return (StaticObject) meta.knownKlass(Class.class).staticMethod("forName", Class.class, String.class).invokeDirect(internalName);
    }

    @JniImpl
    public Object NewLocalRef(Object obj) {
        return obj;
    }

    @JniImpl
    public boolean ExceptionCheck() {
        StaticObject ex = JniThreadLocalPendingException.get();
        assert ex != StaticObject.NULL;
        return ex != null;
    }

    @JniImpl
    public long GetStringCritical(StaticObject str, long isCopyPtr) {
        if (isCopyPtr != 0L) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // always copy since pinning is not supported
        }
        final char[] stringChars = (char[]) meta(str).field("value").get();
        int len = stringChars.length;
        ByteBuffer criticalRegion = allocateDirect(len, JavaKind.Char); // direct byte buffer
                                                                        // (non-relocatable)
        long address = byteBufferAddress(criticalRegion);
        GetCharArrayRegion(stringChars, 0, len, address);
        return address;
    }

    private ByteBuffer allocateDirect(int capacity, JavaKind kind) {
        return allocateDirect(Math.multiplyExact(capacity, kind.getByteCount()));
    }

    private ByteBuffer allocateDirect(int capacity) {
        ByteBuffer bb = ByteBuffer.allocateDirect(capacity) //
                        .order(ByteOrder.nativeOrder());
        long address = byteBufferAddress(bb);
        nativeBuffers.put(address, bb);
        return bb;
    }

    @JniImpl
    public long GetStringUTFChars(StaticObject str, long isCopyPtr) {
        if (isCopyPtr != 0L) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // always copy since pinning is not supported
        }
        byte[] bytes = Utf8.asUTF(Meta.toHost(str));
        ByteBuffer region = allocateDirect(bytes.length);
        region.put(bytes);
        return byteBufferAddress(region);
    }

    @JniImpl
    public void ReleaseStringUTFChars(StaticObject str, long charsPtr) {
        assert nativeBuffers.containsKey(charsPtr);
        nativeBuffers.remove(charsPtr);
    }

    @JniImpl
    public void ReleaseStringCritical(StaticObject str, long criticalRegionPtr) {
        assert nativeBuffers.containsKey(criticalRegionPtr);
        nativeBuffers.remove(criticalRegionPtr);
    }

    @JniImpl
    public int EnsureLocalCapacity(int capacity) {
        return JNI_OK;
    }

    @JniImpl
    public void DeleteLocalRef(Object localRef) {
        // nop
    }

    @JniImpl
    public Object NewGlobalRef(Object obj) {
        return obj; // nop
    }

    @JniImpl
    public void DeleteGlobalRef(Object globalRef) {
        // nop
    }

    @JniImpl
    public int Throw(StaticObject ex) {
        assert EspressoLanguage.getCurrentContext().getMeta() //
                        .THROWABLE.isAssignableFrom(meta(ex.getKlass()));

        JniThreadLocalPendingException.set(ex);
        return JNI_OK;
    }

    @JniImpl
    public int ThrowNew(StaticObjectClass clazz, String message) {
        StaticObject ex = meta(clazz).getMeta().initEx(meta(clazz.getKlass()), message);
        JniThreadLocalPendingException.set(ex);
        return JNI_OK;
    }

    @JniImpl
    public StaticObject ExceptionOccurred() {
        StaticObject ex = JniThreadLocalPendingException.get();
        if (ex == null) {
            ex = StaticObject.NULL;
        }
        return ex;
    }

    @JniImpl
    public int MonitorEnter(Object obj) {
        EspressoLanguage.getCurrentContext().getVm().monitorEnter(obj);
        return JNI_OK;
    }

    @JniImpl
    public int MonitorExit(Object obj) {
        EspressoLanguage.getCurrentContext().getVm().monitorExit(obj);
        return JNI_OK;
    }

    @JniImpl
    public StaticObject NewObjectArray(int length, StaticObjectClass elementClass, Object initialElement) {
        assert !meta(elementClass.getMirror()).isPrimitive();
        StaticObjectArray arr = (StaticObjectArray) meta(elementClass.getMirror()).allocateArray(length);
        if (length > 0) {
            // Single store check
            EspressoLanguage.getCurrentContext().getVm().setArrayObject(initialElement, 0, arr);
            Arrays.fill(arr.getWrapped(), initialElement);
        }
        return arr;
    }

    @JniImpl
    public void SetObjectArrayElement(StaticObjectArray array, int index, Object value) {
        EspressoLanguage.getCurrentContext().getVm().setArrayObject(value, index, array);
    }

    private static ByteBuffer directByteBuffer(long address, long capacity, JavaKind kind) {
        return directByteBuffer(address, Math.multiplyExact(capacity, kind.getByteCount()));
    }

    private static Constructor<? extends ByteBuffer> constructor;
    private static Field address;

    static {
        try {
            Class<? extends ByteBuffer> clazz = (Class<? extends ByteBuffer>) Class.forName("java.nio.DirectByteBuffer");
            Class<? extends ByteBuffer> bufferClazz = (Class<? extends ByteBuffer>) Class.forName("java.nio.Buffer");
            constructor = clazz.getDeclaredConstructor(long.class, int.class);
            address = bufferClazz.getDeclaredField("address");
            address.setAccessible(true);
            constructor.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static ByteBuffer directByteBuffer(long address, long capacity) {
        ByteBuffer buffer = null;
        try {
            buffer = constructor.newInstance(address, Math.toIntExact(capacity));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    private static long byteBufferAddress(ByteBuffer byteBuffer) {
        try {
            return (long) address.get(byteBuffer);
        } catch (IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere();
        }
    }
}
