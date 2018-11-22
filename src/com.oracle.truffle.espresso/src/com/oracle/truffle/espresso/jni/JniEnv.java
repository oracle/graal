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
import static com.oracle.truffle.espresso.meta.Meta.toHost;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.Utils;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.intrinsics.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.nodes.VmNativeNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.types.SignatureDescriptor;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.nfi.types.NativeSimpleType;

public class JniEnv extends NativeEnv {

    public static final int JNI_OK = 0; /* success */
    public static final int JNI_ERR = -1; /* unknown error */
    public static final int JNI_COMMIT = 1;
    public static final int JNI_ABORT = 2;


    public static final int JVM_INTERFACE_VERSION = 4;
    public static final int JNI_TRUE = 1;
    public static final int JNI_FALSE = 0;

    private long jniEnvPtr;

    // Load native library nespresso.dll (Windows) or libnespresso.so (Unixes) at runtime.
    private final TruffleObject nespressoLibrary = NativeLibrary.loadLibrary(System.getProperty("nespresso.library", "nespresso"));

    private final TruffleObject initializeNativeContext;
    private final TruffleObject disposeNativeContext;
    private final TruffleObject dupClosureRef;

    private final TruffleObject popBoolean;
    private final TruffleObject popByte;
    private final TruffleObject popChar;
    private final TruffleObject popShort;
    private final TruffleObject popInt;
    private final TruffleObject popFloat;
    private final TruffleObject popDouble;
    private final TruffleObject popLong;
    private final TruffleObject popObject;

    private static final Map<String, Method> jniMethods = buildJniMethods();

    private final WeakHandles<FieldInfo> fieldIds = new WeakHandles<>();
    private final WeakHandles<MethodInfo> methodIds = new WeakHandles<>();

    // Prevent cleaner threads from collecting in-use native buffers.
    private final Map<Long, ByteBuffer> nativeBuffers = new ConcurrentHashMap<>();

    private final JniThreadLocalPendingException threadLocalPendingException = new JniThreadLocalPendingException();

    public JniThreadLocalPendingException getThreadLocalPendingException() {
        return threadLocalPendingException;
    }

    public Callback jniMethodWrapper(Method m) {
        return new Callback(m.getParameterCount() + 1, args -> {
            assert (long) args[0] == getNativePointer() : "Calling " + m + " from alien JniEnv";
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
                Object ret = m.invoke(this, shiftedArgs);

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
            } catch (InvocationTargetException e) {
                Throwable targetEx = e.getTargetException();
                if (targetEx instanceof EspressoException) {
                    getThreadLocalPendingException().set(((EspressoException) targetEx).getException());
                    return defaultValue(m.getReturnType());
                } else if (targetEx instanceof RuntimeException) {
                    throw (RuntimeException) targetEx;
                }
                // FIXME(peterssen): Handle VME exceptions back to guest.
                throw EspressoError.shouldNotReachHere(targetEx);
            } catch (IllegalAccessException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        });
    }


    public static String jniNativeSignature(Method method) {
        StringBuilder sb = new StringBuilder("(");
        // Prepend JNIEnv* . The raw pointer will be substituted by the proper `this` reference.
        sb.append(NativeSimpleType.SINT64);
        for (Parameter param : method.getParameters()) {
            sb.append(", ");

            // Override NFI type.
            NFIType nfiType = param.getAnnotatedType().getAnnotation(NFIType.class);
            if (nfiType != null) {
                sb.append(NativeSimpleType.valueOf(nfiType.value().toUpperCase()));
            } else {
                sb.append(classToType(param.getType(), false));
            }
        }
        sb.append("): ").append(classToType(method.getReturnType(), true));
        return sb.toString();
    }

    public TruffleObject lookupJniImpl(String methodName) {
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

            String signature = jniNativeSignature(m);
            Callback target = jniMethodWrapper(m);
            return (TruffleObject) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), dupClosureRefAndCast(signature), target);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean containsMethod(String methodName) {
        return jniMethods.containsKey(methodName);
    }

    private class VarArgsImpl implements VarArgs {

        @Node.Child Node execute = Message.EXECUTE.createNode();
        private final long nativePointer;

        public VarArgsImpl(long nativePointer) {
            this.nativePointer = nativePointer;
        }

        @Override
        public boolean popBoolean() {
            try {
                return ((byte) ForeignAccess.sendExecute(execute, popBoolean, nativePointer)) != 0;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public byte popByte() {
            try {
                return (byte) ForeignAccess.sendExecute(execute, popByte, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public char popChar() {
            try {
                return (char) ForeignAccess.sendExecute(execute, popChar, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public short popShort() {
            try {
                return (short) ForeignAccess.sendExecute(execute, popShort, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int popInt() {
            try {
                return (int) ForeignAccess.sendExecute(execute, popInt, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public float popFloat() {
            try {
                return (float) ForeignAccess.sendExecute(execute, popFloat, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public double popDouble() {
            try {
                return (Double) ForeignAccess.sendExecute(execute, popDouble, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long popLong() {
            try {
                return (long) ForeignAccess.sendExecute(execute, popLong, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Object popObject() {
            try {
                return ForeignAccess.sendExecute(execute, popObject, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Object[] popVarArgs(long varargsPtr, Meta.Klass[] parameterTypes) {
        VarArgs varargs = new VarArgsImpl(varargsPtr);
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; ++i) {
            JavaKind kind = parameterTypes[i].rawKlass().getJavaKind();
            switch (kind) {
                case Boolean:
                    args[i] = varargs.popBoolean();
                    break;
                case Byte:
                    args[i] = varargs.popByte();
                    break;
                case Short:
                    args[i] = varargs.popShort();
                    break;
                case Char:
                    args[i] = varargs.popChar();
                    break;
                case Int:
                    args[i] = varargs.popInt();
                    break;
                case Float:
                    args[i] = varargs.popFloat();
                    break;
                case Long:
                    args[i] = varargs.popLong();
                    break;
                case Double:
                    args[i] = varargs.popDouble();
                    break;
                case Object:
                    args[i] = varargs.popObject();
                    break;
                case Void:
                case Illegal:
                    throw EspressoError.shouldNotReachHere();
            }
        }
        return args;
    }

    public VarArgs varargs(long nativePointer) {
        return new VarArgsImpl(nativePointer);
    }

    private JniEnv() {
        try {
            dupClosureRef = NativeLibrary.lookup(nespressoLibrary, "dupClosureRef");

            initializeNativeContext = NativeLibrary.lookupAndBind(nespressoLibrary,
                            "initializeNativeContext", "(env, (string): pointer): sint64");

            disposeNativeContext = NativeLibrary.lookupAndBind(nespressoLibrary, "disposeNativeContext",
                            "(env, sint64): void");

            // Vararg()ds
            popBoolean = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_boolean", "(sint64): uint8");
            popByte = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_byte", "(sint64): uint8");
            popChar = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_char", "(sint64): uint16");
            popShort = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_short", "(sint64): sint16");
            popInt = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_int", "(sint64): sint32");
            popFloat = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_float", "(sint64): float");
            popDouble = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_double", "(sint64): double");
            popLong = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_long", "(sint64): sint64");
            popObject = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_object", "(sint64): object");

            Callback lookupJniImplCallback = Callback.wrapInstanceMethod(this, "lookupJniImpl", String.class);
            this.jniEnvPtr = (long) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), initializeNativeContext, lookupJniImplCallback);
            assert this.jniEnvPtr != 0;
        } catch (UnsupportedMessageException | ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
            throw EspressoError.shouldNotReachHere("Cannot initialize Espresso native interface");
        }
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

    public TruffleObject dupClosureRefAndCast(String signature) {
        // TODO(peterssen): Cache binding per signature.
        return NativeLibrary.bind(dupClosureRef, "(env, " + signature + ")" + ": pointer");
    }

    public static JniEnv create() {
        return new JniEnv();
    }

    public long getNativePointer() {
        return jniEnvPtr;
    }

    public void dispose() {
        assert jniEnvPtr != 0L : "JNIEnv already disposed";
        try {
            ForeignAccess.sendExecute(Message.EXECUTE.createNode(), disposeNativeContext, jniEnvPtr);
            threadLocalPendingException.dispose();
            this.jniEnvPtr = 0L;
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
        return EspressoLanguage.getCurrentContext().getInterpreterToVM().arrayLength(arr);
    }

    @JniImpl
    public int GetStringLength(@Type(String.class) StaticObject str) {
        if (str == StaticObject.NULL) {
            return 0;
        }
        return (int) meta(str).method("length", int.class).invokeDirect();
    }

    // region Get*ID

    @JniImpl
    public long GetFieldID(StaticObjectClass clazz, String name, String signature) {
        clazz.getMirror().initialize();
        Meta.Field field = meta((clazz).getMirror()).declaredField(name);
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
    public Object CallObjectMethodVarargs(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        // FIXME(peterssen): This is virtual dispatch. Re-resolve the method.
        return method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public boolean CallBooleanMethodVarargs(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public char CallCharMethodVarargs(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public byte CallByteMethodVarargs(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public short CallShortMethodVarargs(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public int CallIntMethodVarargs(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public float CallFloatMethodVarargs(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public double CallDoubleMethodVarargs(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public long CallLongMethodVarargs(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    @JniImpl
    public void CallVoidMethodVarargs(Object receiver, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        throw EspressoError.unimplemented();
    }

    // endregion Call*Method

    // region CallNonvirtual*Method

    @JniImpl
    public Object CallNonvirtualObjectMethodVarargs(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public boolean CallNonvirtualBooleanMethodVarargs(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (boolean) method.invoke(receiver, popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public char CallNonvirtualCharMethodVarargs(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (char) method.invoke(receiver, popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public byte CallNonvirtualByteMethodVarargs(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (byte) method.invoke(receiver, popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public short CallNonvirtualShortMethodVarargs(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (short) method.invoke(receiver, popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public int CallNonvirtualIntMethodVarargs(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (int) method.invoke(receiver, popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public float CallNonvirtualFloatMethodVarargs(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (float) method.invoke(receiver, popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public double CallNonvirtualDoubleMethodVarargs(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (double) method.invoke(receiver, popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public long CallNonvirtualLongMethodVarargs(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (long) method.invoke(receiver, popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public void CallNonvirtualVoidMethodVarargs(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        method.invoke(receiver, popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public Object CallNonvirtualtualObjectMethodVarargs(Object receiver, Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return method.invoke(receiver, popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    // endregion CallNonvirtual*Method

    // region CallStatic*Method

    @JniImpl
    public Object CallStaticObjectMethodVarargs(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return method.asStatic().invokeDirect(popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public boolean CallStaticBooleanMethodVarargs(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (boolean) method.asStatic().invokeDirect(popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public char CallStaticCharMethodVarargs(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (char) method.asStatic().invokeDirect(popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public byte CallStaticByteMethodVarargs(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (byte) method.asStatic().invokeDirect(popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public short CallStaticShortMethodVarargs(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (short) method.asStatic().invokeDirect(popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public int CallStaticIntMethodVarargs(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (int) method.asStatic().invoke(popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public float CallStaticFloatMethodVarargs(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (float) method.asStatic().invoke(popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public double CallStaticDoubleMethodVarargs(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (double) method.asStatic().invoke(popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public long CallStaticLongMethodVarargs(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        return (long) method.asStatic().invoke(popVarArgs(varargsPtr, method.getParameterTypes()));
    }

    @JniImpl
    public void CallStaticVoidMethodVarargs(Object clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        method.asStatic().invoke(popVarArgs(varargsPtr, method.getParameterTypes()));
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
            case Boolean:
                GetBooleanArrayRegion((boolean[]) array, 0, length, address);
                break;
            case Byte:
                GetByteArrayRegion((byte[]) array, 0, length, address);
                break;
            case Short:
                GetShortArrayRegion((short[]) array, 0, length, address);
                break;
            case Char:
                GetCharArrayRegion((char[]) array, 0, length, address);
                break;
            case Int:
                GetIntArrayRegion((int[]) array, 0, length, address);
                break;
            case Float:
                GetFloatArrayRegion((float[]) array, 0, length, address);
                break;
            case Long:
                GetLongArrayRegion((long[]) array, 0, length, address);
                break;
            case Double:
                GetDoubleArrayRegion((double[]) array, 0, length, address);
                break;
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
                case Boolean:
                    SetBooleanArrayRegion((boolean[]) array, 0, length, carrayPtr);
                    break;
                case Byte:
                    SetByteArrayRegion((byte[]) array, 0, length, carrayPtr);
                    break;
                case Short:
                    SetShortArrayRegion((short[]) array, 0, length, carrayPtr);
                    break;
                case Char:
                    SetCharArrayRegion((char[]) array, 0, length, carrayPtr);
                    break;
                case Int:
                    SetIntArrayRegion((int[]) array, 0, length, carrayPtr);
                    break;
                case Float:
                    SetFloatArrayRegion((float[]) array, 0, length, carrayPtr);
                    break;
                case Long:
                    SetLongArrayRegion((long[]) array, 0, length, carrayPtr);
                    break;
                case Double:
                    SetDoubleArrayRegion((double[]) array, 0, length, carrayPtr);
                    break;
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
    public Object GetObjectClass(Object self) {
        if (self instanceof StaticObject) {
            return ((StaticObject) self).getKlass().mirror();
        }
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        if (self instanceof int[]) {
            return meta.INT.array().rawKlass().mirror();
        } else if (self instanceof byte[]) {
            return meta.BYTE.array().rawKlass().mirror();
        } else if (self instanceof boolean[]) {
            return meta.BOOLEAN.array().rawKlass().mirror();
        } else if (self instanceof long[]) {
            return meta.LONG.array().rawKlass().mirror();
        } else if (self instanceof float[]) {
            return meta.FLOAT.array().rawKlass().mirror();
        } else if (self instanceof double[]) {
            return meta.DOUBLE.array().rawKlass().mirror();
        } else if (self instanceof char[]) {
            return meta.CHAR.array().rawKlass().mirror();
        } else if (self instanceof short[]) {
            return meta.SHORT.array().rawKlass().mirror();
        }
        throw EspressoError.shouldNotReachHere(".getClass failed. Non-espresso object: " + self);
    }

    @JniImpl
    public Object NewObjectVarargs(StaticObjectClass clazz, long methodHandle, long varargsPtr) {
        Meta.Method method = meta(methodIds.getObject(methodHandle));
        StaticObject instance = meta(clazz.getMirror()).allocateInstance();
        assert method.isConstructor();
        method.invokeDirect(instance, popVarArgs(varargsPtr, method.getParameterTypes()));
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
        // Local ref is allocated by host JNI on return.
        return obj;
    }

    @JniImpl
    public boolean ExceptionCheck() {
        StaticObject ex = threadLocalPendingException.get();
        assert ex != StaticObject.NULL;
        return ex != null;
    }

    @JniImpl
    public long GetStringCritical(@Type(String.class) StaticObject str, long isCopyPtr) {
        if (isCopyPtr != 0L) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // always copy since pinning is not supported
        }
        final char[] stringChars = (char[]) meta(str).declaredField("value").get();
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
    public long GetStringUTFChars(@Type(String.class) StaticObject str, long isCopyPtr) {
        if (isCopyPtr != 0L) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // always copy since pinning is not supported
        }
        byte[] bytes = Utf8.asUTF(Meta.toHost(str), true);
        ByteBuffer region = allocateDirect(bytes.length);
        region.put(bytes);
        return byteBufferAddress(region);
    }

    @JniImpl
    public void ReleaseStringUTFChars(@Type(String.class) StaticObject str, long charsPtr) {
        assert nativeBuffers.containsKey(charsPtr);
        nativeBuffers.remove(charsPtr);
    }

    @JniImpl
    public void ReleaseStringCritical(@Type(String.class) StaticObject str, long criticalRegionPtr) {
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
    public int Throw(StaticObject ex) {
        assert EspressoLanguage.getCurrentContext().getMeta() //
                        .THROWABLE.isAssignableFrom(meta(ex.getKlass()));

        threadLocalPendingException.set(ex);
        return JNI_OK;
    }

    @JniImpl
    public int ThrowNew(@Type(Class.class) StaticObjectClass clazz, String message) {
        StaticObject ex = meta(clazz).getMeta().initEx(meta(clazz.getKlass()), message);
        threadLocalPendingException.set(ex);
        return JNI_OK;
    }

    @JniImpl
    public StaticObject ExceptionOccurred() {
        StaticObject ex = threadLocalPendingException.get();
        if (ex == null) {
            ex = StaticObject.NULL;
        }
        return ex;
    }

    @JniImpl
    public int MonitorEnter(Object obj) {
        EspressoLanguage.getCurrentContext().getInterpreterToVM().monitorEnter(obj);
        return JNI_OK;
    }

    @JniImpl
    public int MonitorExit(Object obj) {
        EspressoLanguage.getCurrentContext().getInterpreterToVM().monitorExit(obj);
        return JNI_OK;
    }

    @JniImpl
    public StaticObject NewObjectArray(int length, StaticObjectClass elementClass, Object initialElement) {
        assert !meta(elementClass.getMirror()).isPrimitive();
        StaticObjectArray arr = (StaticObjectArray) meta(elementClass.getMirror()).allocateArray(length);
        if (length > 0) {
            // Single store check
            EspressoLanguage.getCurrentContext().getInterpreterToVM().setArrayObject(initialElement, 0, arr);
            Arrays.fill(arr.getWrapped(), initialElement);
        }
        return arr;
    }

    @JniImpl
    public void SetObjectArrayElement(StaticObjectArray array, int index, Object value) {
        EspressoLanguage.getCurrentContext().getInterpreterToVM().setArrayObject(value, index, array);
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

    @JniImpl
    public void GetStringRegion(@Type(String.class) StaticObject str, int start, int len, long bufPtr) {
        final char[] chars = (char[]) meta(str).declaredField("value").get();
        CharBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Char).asCharBuffer();
        buf.put(chars, start, len);
    }

    private String nfiSignature(String signature, boolean isJni) {
        SignatureDescriptor descriptor = EspressoLanguage.getCurrentContext().getSignatureDescriptors().make(signature);
        int argCount = descriptor.getParameterCount(false);
        StringBuilder sb = new StringBuilder("(");

        boolean first = true;
        if (isJni) {
            sb.append(NativeSimpleType.POINTER); // JNIEnv*
            sb.append(",");
            sb.append(Utils.kindToType(JavaKind.Object, false)); // Receiver or class (for static
                                                                 // methods).
            first = false;
        }
        for (int i = 0; i < argCount; ++i) {
            JavaKind kind = descriptor.getParameterKind(i);
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }
            sb.append(Utils.kindToType(kind, false));
        }

        sb.append("): ").append(Utils.kindToType(descriptor.resultKind(), false));
        return sb.toString();
    }

    @JniImpl
    public int RegisterNative(StaticObject clazz, String name, String signature, @NFIType("POINTER") TruffleObject closure) {
        String className = meta(((StaticObjectClass) clazz).getMirror()).getInternalName();
        TruffleObject boundNative = NativeLibrary.bind(closure, nfiSignature(signature, true));
        RootNode nativeNode = new VmNativeNode(EspressoLanguage.getCurrentContext().getLanguage(), boundNative, true, null);
        EspressoLanguage.getCurrentContext().getInterpreterToVM().registerIntrinsic(className, name, signature, nativeNode, false);
        return JNI_OK;
    }

    @JniImpl
    public int GetStringUTFLength(@Type(String.class) StaticObject string) {
        return Utf8.UTFLength(Meta.toHost(string));
    }

    @JniImpl
    public void GetStringUTFRegion(@Type(String.class) StaticObject str, int start, int len, long bufPtr) {
        byte[] bytes = Utf8.asUTF(toHost(str), start, len, true); // always 0 terminated.
        ByteBuffer buf = directByteBuffer(bufPtr, bytes.length, JavaKind.Byte);
        buf.put(bytes);
    }

    /**
     * Loads a class from a buffer of raw class data. The buffer containing the raw class data is not
     * referenced by the VM after the DefineClass call returns, and it may be discarded if desired.
     *
     * @param name the name of the class or interface to be defined. The string is encoded in modified UTF-8.
     * @param loader a class loader assigned to the defined class.
     * @param bufPtr buffer containing the .class file data.
     * @param bufLen buffer length.
     *
     * @return Returns a Java class object or NULL if an error occurs.
     */
    @JniImpl
    public @Type(Class.class) StaticObject DefineClass(String name, Object loader, long bufPtr, int bufLen) {
        // TODO(peterssen): Propagete errors and verifications, e.g. no class win the java package.
        return EspressoLanguage.getCurrentContext().getVM().JVM_DefineClass(name, loader, bufPtr, bufLen, StaticObject.NULL);
    }

    // JavaVM **vm);

    @JniImpl
    public int GetJavaVM(long vmPtr) {
        ByteBuffer buf = directByteBuffer(vmPtr, 1, JavaKind.Long); // 64 bits pointer
        buf.putLong(EspressoLanguage.getCurrentContext().getVM().getJavaVM());
        return JNI_OK;
    }
}
