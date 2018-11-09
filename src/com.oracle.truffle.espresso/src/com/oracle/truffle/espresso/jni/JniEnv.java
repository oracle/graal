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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Object;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.nfi.types.NativeSimpleType;

public class JniEnv {

    public static void touch() {
    }

    private static final int JNI_OK = 0; /* success */
    private static final int JNI_ERR = -1; /* unknown error */
    private static final int JNI_COMMIT = 1;
    private static final int JNI_ABORT = 2;

    // mokapot.dll (Windows) or libmokapot.so (Unixes) is the Espresso implementation of the VM
    // interface (libjvm)
    // Espresso loads all shared libraries in a private namespace (e.g. using dlmopen on Linux).
    // mokapot must be loaded strictly before any other library in the private namespace to
    // linking with HotSpot libjvm (or just linking errors), then libjava is loaded and further
    // system libraries, libzip ...
    private static final TruffleObject mokapotLibrary = NativeLibrary.loadLibrary(System.getProperty("mokapot.library", "mokapot"));

    // Load native library nespresso.dll (Windows) or libnespresso.so (Unixes) at runtime.
    public static final TruffleObject nespressoLibrary = NativeLibrary.loadLibrary(System.getProperty("nespresso.library", "nespresso"));

    public static final TruffleObject javaLibrary = NativeLibrary.loadLibrary(System.getProperty("java.library", "java"));

    private static final TruffleObject initializeNativeContext;
    private static final TruffleObject setMokapotEspressoJniEnv;
    private static final TruffleObject disposeNativeContext;
    static {
        try {
            initializeNativeContext = NativeLibrary.lookupAndBind(nespressoLibrary,
                            "initializeNativeContext", "(env, (string): pointer): pointer");

            setMokapotEspressoJniEnv = NativeLibrary.lookupAndBind(mokapotLibrary,
                            "Mokapot_SetJNIEnv", "(pointer): void");

            disposeNativeContext = NativeLibrary.lookupAndBind(nespressoLibrary, "disposeNativeContext",
                            "(env, pointer): void");
        } catch (UnknownIdentifierException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static final TruffleObject dupClosureRef = NativeLibrary.lookup(nespressoLibrary, "dupClosureRef");

    private static final Map<Class<?>, NativeSimpleType> classToNative = buildClassToNative();
    private static final Map<String, Method> jniMethods = buildJniMethods();

    private long jniEnvPtr;

    private final Handles<FieldInfo> fieldIds = new Handles<>();
    private final Handles<MethodInfo> methodIds = new Handles<>();

    // Prevent cleaner threads from collecting in-use native buffers.
    private final Map<Long, ByteBuffer> nativeBuffers = new ConcurrentHashMap<>();

    private JniEnv() {
        try {
            Callback lookupJniImplCallback = Callback.wrapInstanceMethod(this, "lookupJniImpl", String.class);
            this.jniEnvPtr = unwrapPointer(ForeignAccess.sendExecute(Message.EXECUTE.createNode(), initializeNativeContext, lookupJniImplCallback));
            assert this.jniEnvPtr != 0;
            ForeignAccess.sendExecute(Message.EXECUTE.createNode(), setMokapotEspressoJniEnv, this.jniEnvPtr);
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

    private static NativeSimpleType kindToType(Class<?> clazz, boolean javaToNative) {
        return classToNative.getOrDefault(clazz, javaToNative ? NativeSimpleType.OBJECT_OR_NULL : NativeSimpleType.OBJECT);
    }

    private static String nativeSignature(Method method) {
        StringBuilder sb = new StringBuilder("(");
        // Prepend JNIEnv* . The raw pointer will be substituted by the proper `this` reference.
        sb.append(NativeSimpleType.POINTER);
        for (Class<?> param : method.getParameterTypes()) {
            sb.append(", ").append(kindToType(param, false));
        }
        sb.append("): ").append(kindToType(method.getReturnType(), true));
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
                // System.err.println("Fetching unknown/unimplemented JNI method: " + methodName);
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
                //System.err.print("Call DEFINED method: " + m.getName() +
                //                Arrays.toString(shiftedArgs));
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
    public int GetStringLength(StaticObject str) {
        return (int) meta(str).method("length", int.class).invokeDirect();
    }

    // region Get*ID

    @JniImpl
    public long GetFieldID(StaticObjectClass clazz, String name, String signature) {
        clazz.getMirror().initialize();
        Meta.Field field = meta((clazz).getMirror()).field(name);
        return fieldIds.handlify(field.rawField());
    }

    @JniImpl
    public long GetStaticFieldID(StaticObjectClass clazz, String name, String signature) {
        clazz.getMirror().initialize();
        return fieldIds.handlify(meta((clazz).getMirror()).staticField(name).getField().rawField());
    }

    @JniImpl
    public long GetMethodID(StaticObjectClass clazz, String name, String signature) {
        clazz.getMirror().initialize();
        Meta.Method[] methods = meta(clazz.getMirror()).methods(true);
        for (Meta.Method m : methods) {
            if (m.getName().equals(name) && m.rawMethod().getSignature().toString().equals(signature)) {
                return methodIds.handlify(m.rawMethod());
            }
        }
        throw new RuntimeException("Method " + name + " not found");
    }

    @JniImpl
    public long GetStaticMethodID(StaticObjectClass clazz, String name, String signature) {
        clazz.getMirror().initialize();
        Meta.Method[] methods = meta(clazz.getMirror()).methods(false);
        for (Meta.Method m : methods) {
            if (m.getName().equals(name) && m.rawMethod().getSignature().toString().equals(signature)) {
                return methodIds.handlify(m.rawMethod());
            }
        }
        throw new RuntimeException("Method " + name + " not found");
    }

    // endregion Get*ID

    // region Get*Field

    @JniImpl
    public Object GetObjectField(StaticObject obj, long fieldHandle) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        return field.get(obj);
    }

    @JniImpl
    public boolean GetBooleanField(StaticObject object, long fieldHandle) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        return (boolean) field.get(object);
    }

    @JniImpl
    public byte GetByteField(StaticObject object, long fieldHandle) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        return (byte) field.get(object);
    }

    @JniImpl
    public char GetCharField(StaticObject object, long fieldHandle) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        return (char) field.get(object);
    }

    @JniImpl
    public short GetShortField(StaticObject object, long fieldHandle) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        return (short) field.get(object);
    }

    @JniImpl
    public int GetIntField(StaticObject object, long fieldHandle) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        return (int) field.get(object);
    }

    @JniImpl
    public long GetLongField(StaticObject object, long fieldHandle) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        return (long) field.get(object);
    }

    @JniImpl
    public float GetFloatField(StaticObject object, long fieldHandle) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        return (float) field.get(object);
    }

    @JniImpl
    public double GetDoubleField(StaticObject object, long fieldHandle) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        return (double) field.get(object);
    }

    // endregion Get*Field

    // region Set*Field

    @JniImpl
    public void SetObjectField(StaticObject obj, long fieldHandle, Object val) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        field.set(obj, val);
    }

    @JniImpl
    public void SetBooleanField(StaticObject obj, long fieldHandle, boolean val) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        field.set(obj, val);
    }

    @JniImpl
    public void SetByteField(StaticObject obj, long fieldHandle, byte val) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        field.set(obj, val);
    }

    @JniImpl
    public void SetCharField(StaticObject obj, long fieldHandle, char val) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        field.set(obj, val);
    }

    @JniImpl
    public void SetShortField(StaticObject obj, long fieldHandle, short val) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        field.set(obj, val);
    }

    @JniImpl
    public void SetIntField(StaticObject obj, long fieldHandle, int val) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        field.set(obj, val);
    }

    @JniImpl
    public void SetLongField(StaticObject obj, long fieldHandle, long val) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        field.set(obj, val);
    }

    @JniImpl
    public void SetFloatField(StaticObject obj, long fieldHandle, float val) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        field.set(obj, val);
    }

    @JniImpl
    public void SetDoubleField(StaticObject obj, long fieldHandle, double val) {
        Meta.Field field = meta(fieldIds.getObject(fieldHandle));
        field.set(obj, val);
    }

    // endregion Set*Field

    // region Call*Method

    @JniImpl
    public Object CallObjectMethod(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return method.invokeDirect(receiver, VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public boolean CallBooleanMethod(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public char CallCharMethod(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public byte CallByteMethod(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public short CallShortMethod(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public int CallIntMethod(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public float CallFloatMethod(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public double CallDoubleMethod(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public long CallLongMethod(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public void CallVoidMethod(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    // endregion Call*Method

    // region CallNonvirtual*Method

    @JniImpl
    public Object CallNonvirtualObjectMethod(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return method.invokeDirect(receiver, VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public boolean CallNonvirtualBooleanMethod(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (boolean) method.invoke(receiver, VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public char CallNonvirtualCharMethod(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (char) method.invoke(receiver, VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public byte CallNonvirtualByteMethod(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (byte) method.invoke(receiver, VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public short CallNonvirtualShortMethod(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (short) method.invoke(receiver, VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public int CallNonvirtualIntMethod(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (int) method.invoke(receiver, VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public float CallNonvirtualFloatMethod(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (float) method.invoke(receiver, VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public double CallNonvirtualDoubleMethod(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (double) method.invoke(receiver, VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public long CallNonvirtualLongMethod(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (long) method.invoke(receiver, VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public void CallNonvirtualVoidMethod(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        method.invoke(receiver, VarArgs.pop(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public Object CallNonvirtualtualObjectMethod(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return method.invoke(receiver, VarArgs.pop(varargsPtr, method.getParameterTypes()));
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

    @JniImpl
    public long GetPrimitiveArrayCritical(Object array, long isCopyPtr) {
        if (isCopyPtr != 0L) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        StaticObjectClass clazz = (StaticObjectClass) GetObjectClass(array);
        JavaKind componentKind = clazz.getMirror().getComponentType().getJavaKind();
        assert componentKind.isPrimitive();
        int length = GetArrayLength(array);

        ByteBuffer region = allocateDirect(length, componentKind);
        long address = byteBufferAddress(region);
        // @formatter:off
        // Checkstyle: stop
        switch (componentKind) {
            case Boolean: GetBooleanArrayRegion((boolean[]) array, 0, length, address); break;
            case Byte:    GetByteArrayRegion((byte[]) array, 0, length, address);       break;
            case Short:   GetShortArrayRegion((short[]) array, 0, length, address);     break;
            case Char:    GetCharArrayRegion((char[]) array, 0, length, address);       break;
            case Int:     GetIntArrayRegion((int[]) array, 0, length, address);         break;
            case Float:   GetFloatArrayRegion((float[]) array, 0, length, address);     break;
            case Long:    GetLongArrayRegion((long[]) array, 0, length, address);       break;
            case Double:  GetDoubleArrayRegion((double[]) array, 0, length, address);   break;
            case Object:  // fall through
            case Void:    // fall through
            case Illegal:
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume

        return address;
    }

    @JniImpl
    public void ReleasePrimitiveArrayCritical(Object array, long carrayPtr, int mode) {
        if (mode == 0 || mode == JNI_COMMIT) { // Update array contents.
            StaticObjectClass clazz = (StaticObjectClass) GetObjectClass(array);
            JavaKind componentKind = clazz.getMirror().getComponentType().getJavaKind();
            assert componentKind.isPrimitive();
            int length = GetArrayLength(array);
            // @formatter:off
            // Checkstyle: stop
            switch (componentKind) {
                case Boolean: SetBooleanArrayRegion((boolean[]) array, 0, length, carrayPtr); break;
                case Byte:    SetByteArrayRegion((byte[]) array, 0, length, carrayPtr);       break;
                case Short:   SetShortArrayRegion((short[]) array, 0, length, carrayPtr);     break;
                case Char:    SetCharArrayRegion((char[]) array, 0, length, carrayPtr);       break;
                case Int:     SetIntArrayRegion((int[]) array, 0, length, carrayPtr);         break;
                case Float:   SetFloatArrayRegion((float[]) array, 0, length, carrayPtr);     break;
                case Long:    SetLongArrayRegion((long[]) array, 0, length, carrayPtr);       break;
                case Double:  SetDoubleArrayRegion((double[]) array, 0, length, carrayPtr);   break;
                case Object:  // fall through
                case Void:    // fall through
                case Illegal:
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
            // Checkstyle: resume
        }

        if (mode == 0 || mode == JNI_ABORT) { // Dispose copy.
            assert nativeBuffers.containsKey(carrayPtr);
            nativeBuffers.remove(carrayPtr);
        }
    }

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

    @JniImpl
    public StaticObject NewString(long unicodePtr, int len) {
        char[] value = new char[len];
        SetCharArrayRegion(value, 0, len, unicodePtr);
        return EspressoLanguage.getCurrentContext().getMeta() //
                        .STRING.metaNew() //
                                        .fields(Meta.Field.set("value", value)) //
                                        .getInstance();
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
