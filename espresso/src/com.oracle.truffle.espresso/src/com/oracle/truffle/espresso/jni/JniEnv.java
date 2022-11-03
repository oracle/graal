/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Validation;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoProperties;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.EspressoInterop;
import com.oracle.truffle.espresso.substitutions.CallableFromNative;
import com.oracle.truffle.espresso.substitutions.GenerateNativeEnv;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.substitutions.Substitutions;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@GenerateNativeEnv(target = JniImpl.class)
public final class JniEnv extends NativeEnv {

    public static final int JNI_OK = 0; /* success */
    public static final int JNI_ERR = -1; /* unknown error */
    public static final int JNI_EDETACHED = -2;
    public static final int JNI_EVERSION = -3;
    public static final int JNI_COMMIT = 1;
    public static final int JNI_ABORT = 2;

    public static final int JVM_INTERFACE_VERSION_8 = 4;
    public static final int JVM_INTERFACE_VERSION_11 = 6;
    public static final int JNI_TRUE = 1;
    public static final int JNI_FALSE = 0;

    // enum jobjectRefType
    public static final int JNIInvalidRefType = 0;
    public static final int JNILocalRefType = 1;
    public static final int JNIGlobalRefType = 2;
    public static final int JNIWeakGlobalRefType = 3;

    // TODO(peterssen): Add user-configurable option.
    private static final int MAX_JNI_LOCAL_CAPACITY = 1 << 16;

    private final JNIHandles handles;

    private @Pointer TruffleObject jniEnvPtr;

    // Native library nespresso.dll (Windows) or libnespresso.so (Unixes) at runtime.
    private final TruffleObject nespressoLibrary;

    // Native methods in libenespresso.
    private final @Pointer TruffleObject initializeNativeContext;
    private final @Pointer TruffleObject disposeNativeContext;
    private final @Pointer TruffleObject popBoolean;
    private final @Pointer TruffleObject popByte;
    private final @Pointer TruffleObject popChar;
    private final @Pointer TruffleObject popShort;
    private final @Pointer TruffleObject popInt;
    private final @Pointer TruffleObject popFloat;
    private final @Pointer TruffleObject popDouble;
    private final @Pointer TruffleObject popLong;
    private final @Pointer TruffleObject popObject;

    private final @Pointer TruffleObject getSizeMax;

    private static final List<CallableFromNative.Factory> JNI_IMPL_FACTORIES = JniImplCollector.getInstances(CallableFromNative.Factory.class);

    @Override
    protected List<CallableFromNative.Factory> getCollector() {
        return JNI_IMPL_FACTORIES;
    }

    @Override
    protected JniEnv jni() {
        return this;
    }

    private final WeakHandles<Field> fieldIds = new WeakHandles<>();
    private final WeakHandles<Method> methodIds = new WeakHandles<>();

    // The maximum value supported by the native size_t e.g. SIZE_MAX.
    private long cachedSizeMax = 0;

    // Prevent cleaner threads from collecting in-use native buffers.
    private final Map<Long, ByteBuffer> nativeBuffers = new ConcurrentHashMap<>();

    public StaticObject getPendingException() {
        return getContext().getLanguage().getThreadLocalState().getPendingExceptionObject();
    }

    public EspressoException getPendingEspressoException() {
        return getContext().getLanguage().getThreadLocalState().getPendingException();
    }

    public void clearPendingException() {
        getContext().getLanguage().getThreadLocalState().clearPendingException();
    }

    public void setPendingException(StaticObject ex) {
        Meta meta = getMeta();
        assert StaticObject.notNull(ex) && meta.java_lang_Throwable.isAssignableFrom(ex.getKlass());
        setPendingException(EspressoException.wrap(ex, meta));
    }

    public void setPendingException(EspressoException ex) {
        getContext().getLanguage().getThreadLocalState().setPendingException(ex);
    }

    private class VarArgsImpl implements VarArgs {

        private final @Pointer TruffleObject nativePointer;

        VarArgsImpl(@Pointer TruffleObject nativePointer) {
            this.nativePointer = nativePointer;
        }

        @Override
        public boolean popBoolean() {
            try {
                return (boolean) getUncached().execute(popBoolean, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public byte popByte() {
            try {
                return (byte) getUncached().execute(popByte, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public char popChar() {
            try {
                return (char) getUncached().execute(popChar, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public short popShort() {
            try {
                return (short) getUncached().execute(popShort, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public int popInt() {
            try {
                return (int) getUncached().execute(popInt, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public float popFloat() {
            try {
                return (float) getUncached().execute(popFloat, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public double popDouble() {
            try {
                return (double) getUncached().execute(popDouble, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public long popLong() {
            try {
                return (long) getUncached().execute(popLong, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public Object popObject() {
            try {
                Object ret = getUncached().execute(popObject, nativePointer);
                @Handle(StaticObject.class)
                long handle = 0;
                if (getUncached().isPointer(ret)) {
                    /* due to GR-37169 it can be any pointer type, not just a long in nfi-llvm */
                    handle = getUncached().asPointer(ret);
                } else {
                    handle = (long) ret;
                }
                TruffleObject result = getHandles().get(Math.toIntExact(handle));
                if (result instanceof StaticObject) {
                    return result;
                } else {
                    if (getUncached().isNull(result)) {
                        // TODO(garcia) understand the weird stuff happening here.
                        // DaCapo batik gives us a NativePointer to 0 here. This is a workaround
                        // until I
                        // figure out just what is happening here.
                        return StaticObject.NULL;
                    } else {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw EspressoError.unimplemented("non null native pointer in JniEnv");
                    }
                }
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException | ClassCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    public Object[] popVarArgs(@Pointer TruffleObject varargsPtr, final Symbol<Type>[] signature) {
        VarArgs varargs = new VarArgsImpl(varargsPtr);
        int paramCount = Signatures.parameterCount(signature);
        Object[] args = new Object[paramCount];
        for (int i = 0; i < paramCount; ++i) {
            JavaKind kind = Signatures.parameterKind(signature, i);
            // @formatter:off
            switch (kind) {
                case Boolean : args[i] = varargs.popBoolean(); break;
                case Byte    : args[i] = varargs.popByte();    break;
                case Short   : args[i] = varargs.popShort();   break;
                case Char    : args[i] = varargs.popChar();    break;
                case Int     : args[i] = varargs.popInt();     break;
                case Float   : args[i] = varargs.popFloat();   break;
                case Long    : args[i] = varargs.popLong();    break;
                case Double  : args[i] = varargs.popDouble();  break;
                case Object  : args[i] = varargs.popObject();  break;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere("invalid parameter kind: " + kind);
            }
            // @formatter:on
        }
        return args;
    }

    private JniEnv(EspressoContext context) {
        super(context);
        EspressoProperties props = context.getVmProperties();
        Path espressoLibraryPath = props.espressoHome().resolve("lib");
        nespressoLibrary = getNativeAccess().loadLibrary(Collections.singletonList(espressoLibraryPath), "nespresso", true);
        initializeNativeContext = getNativeAccess().lookupAndBindSymbol(nespressoLibrary, "initializeNativeContext",
                        NativeSignature.create(NativeType.POINTER, NativeType.POINTER));
        disposeNativeContext = getNativeAccess().lookupAndBindSymbol(nespressoLibrary, "disposeNativeContext",
                        NativeSignature.create(NativeType.VOID, NativeType.POINTER, NativeType.POINTER));

        getSizeMax = getNativeAccess().lookupAndBindSymbol(nespressoLibrary, "get_SIZE_MAX", NativeSignature.create(NativeType.LONG));

        assert sizeMax() > Integer.MAX_VALUE : "size_t must be 64-bit wide";

        // Varargs native bindings.
        popBoolean = getNativeAccess().lookupAndBindSymbol(nespressoLibrary, "pop_boolean", NativeSignature.create(NativeType.BOOLEAN, NativeType.POINTER));
        popByte = getNativeAccess().lookupAndBindSymbol(nespressoLibrary, "pop_byte", NativeSignature.create(NativeType.BYTE, NativeType.POINTER));
        popChar = getNativeAccess().lookupAndBindSymbol(nespressoLibrary, "pop_char", NativeSignature.create(NativeType.CHAR, NativeType.POINTER));
        popShort = getNativeAccess().lookupAndBindSymbol(nespressoLibrary, "pop_short", NativeSignature.create(NativeType.SHORT, NativeType.POINTER));
        popInt = getNativeAccess().lookupAndBindSymbol(nespressoLibrary, "pop_int", NativeSignature.create(NativeType.INT, NativeType.POINTER));
        popFloat = getNativeAccess().lookupAndBindSymbol(nespressoLibrary, "pop_float", NativeSignature.create(NativeType.FLOAT, NativeType.POINTER));
        popDouble = getNativeAccess().lookupAndBindSymbol(nespressoLibrary, "pop_double", NativeSignature.create(NativeType.DOUBLE, NativeType.POINTER));
        popLong = getNativeAccess().lookupAndBindSymbol(nespressoLibrary, "pop_long", NativeSignature.create(NativeType.LONG, NativeType.POINTER));
        popObject = getNativeAccess().lookupAndBindSymbol(nespressoLibrary, "pop_object", NativeSignature.create(NativeType.OBJECT, NativeType.POINTER));

        this.jniEnvPtr = initializeAndGetEnv(initializeNativeContext);
        assert getUncached().isPointer(jniEnvPtr);

        this.handles = new JNIHandles();

        assert jniEnvPtr != null && !getUncached().isNull(jniEnvPtr);
    }

    @Override
    protected String getName() {
        return "JniEnv";
    }

    @Override
    public JNIHandles getHandles() {
        return handles;
    }

    @TruffleBoundary
    private ByteBuffer allocateDirect(int capacity, JavaKind kind) {
        return allocateDirect(Math.multiplyExact(capacity, kind.getByteCount()));
    }

    @TruffleBoundary
    private ByteBuffer allocateDirect(int capacity) {
        ByteBuffer bb = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
        long address = NativeUtils.byteBufferAddress(bb);
        nativeBuffers.put(address, bb);
        return bb;
    }

    public static JniEnv create(EspressoContext context) {
        return new JniEnv(context);
    }

    public @Pointer TruffleObject getNativePointer() {
        return jniEnvPtr;
    }

    public void dispose() {
        if (jniEnvPtr == null || getUncached().isNull(jniEnvPtr)) {
            return; // JniEnv disposed or uninitialized.
        }
        try {
            getUncached().execute(disposeNativeContext, jniEnvPtr, RawPointer.nullInstance());
            this.jniEnvPtr = null;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Cannot dispose Espresso native interface");
        }
        assert jniEnvPtr == null || getUncached().isNull(jniEnvPtr);
    }

    public long sizeMax() {
        long result = cachedSizeMax;
        if (result == 0) {
            try {
                result = (long) getUncached().execute(getSizeMax);
                if (result < 0) {
                    result = Long.MAX_VALUE;
                }
                cachedSizeMax = result;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }
        return result;
    }

    // Checkstyle: stop method name check

    // region Get*ID

    /**
     * <h3>jfieldID GetFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig);</h3>
     * <p>
     * Returns the field ID for an instance (nonstatic) field of a class. The field is specified by
     * its name and signature. The Get<type>Field and Set<type>Field families of accessor functions
     * use field IDs to retrieve object fields. GetFieldID() causes an uninitialized class to be
     * initialized. GetFieldID() cannot be used to obtain the length field of an array. Use
     * GetArrayLength() instead.
     *
     * @param clazz a Java class object.
     * @param namePtr the field name in a 0-terminated modified UTF-8 string.
     * @param typePtr the field signature in a 0-terminated modified UTF-8 string.
     * @return a field ID, or NULL if the operation fails.
     * @throws NoSuchFieldError: if the specified field cannot be found.
     * @throws ExceptionInInitializerError: if the class initializer fails due to an exception.
     * @throws OutOfMemoryError: if the system runs out of memory.
     */
    @JniImpl
    public @Handle(Field.class) long GetFieldID(@JavaType(Class.class) StaticObject clazz, @Pointer TruffleObject namePtr, @Pointer TruffleObject typePtr) {
        String name = NativeUtils.interopPointerToString(namePtr);
        String type = NativeUtils.interopPointerToString(typePtr);
        assert name != null && type != null;
        Klass klass = clazz.getMirrorKlass(getMeta());

        Field field = null;
        Symbol<Name> fieldName = getNames().lookup(name);
        if (fieldName != null) {
            Symbol<Type> fieldType = getTypes().lookup(type);
            if (fieldType != null) {
                // Lookup only if name and type are known symbols.
                klass.safeInitialize();
                field = klass.lookupField(fieldName, fieldType);
                assert field == null || field.getType().equals(fieldType);
            }
        }
        if (field == null || field.isStatic()) {
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchFieldError, name);
        }
        assert !field.isStatic();
        return fieldIds.handlify(field);
    }

    /**
     * <h3>jfieldID GetStaticFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig);
     * </h3>
     * <p>
     * Returns the field ID for a static field of a class. The field is specified by its name and
     * signature. The GetStatic<type>Field and SetStatic<type>Field families of accessor functions
     * use field IDs to retrieve static fields.
     * <p>
     * GetStaticFieldID() causes an uninitialized class to be initialized.
     *
     * @param clazz a Java class object.
     * @param namePtr the static field name in a 0-terminated modified UTF-8 string.
     * @param typePtr the field signature in a 0-terminated modified UTF-8 string.
     * @return a field ID, or NULL if the specified static field cannot be found.
     * @throws NoSuchFieldError if the specified static field cannot be found.
     * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
     * @throws OutOfMemoryError if the system runs out of memory.
     */
    @JniImpl
    public @Handle(Field.class) long GetStaticFieldID(@JavaType(Class.class) StaticObject clazz, @Pointer TruffleObject namePtr, @Pointer TruffleObject typePtr) {
        String name = NativeUtils.interopPointerToString(namePtr);
        String type = NativeUtils.interopPointerToString(typePtr);
        assert name != null && type != null;
        Field field = null;
        Symbol<Name> fieldName = getNames().lookup(name);
        if (fieldName != null) {
            Symbol<Type> fieldType = getTypes().lookup(type);
            if (fieldType != null) {
                Klass klass = clazz.getMirrorKlass(getMeta());
                klass.safeInitialize();
                // Lookup only if name and type are known symbols.
                field = klass.lookupField(fieldName, fieldType, Klass.LookupMode.STATIC_ONLY);
                assert field == null || field.getType().equals(fieldType);
            }
        }
        if (field == null || !field.isStatic()) {
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchFieldError, name);
        }
        return fieldIds.handlify(field);
    }

    /**
     * <h3>jmethodID GetMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig);</h3>
     * <p>
     * Returns the method ID for an instance (nonstatic) method of a class or interface. The method
     * may be defined in one of the clazz’s superclasses and inherited by clazz. The method is
     * determined by its name and signature.
     * <p>
     * GetMethodID() causes an uninitialized class to be initialized.
     * <p>
     * To obtain the method ID of a constructor, supply <init> as the method name and void (V) as
     * the return type.
     *
     * @param clazz a Java class object.
     * @param namePtr the method name in a 0-terminated modified UTF-8 string.
     * @param signaturePtr the method signature in 0-terminated modified UTF-8 string.
     * @return a method ID, or NULL if the specified method cannot be found.
     * @throws NoSuchMethodError if the specified method cannot be found.
     * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
     * @throws OutOfMemoryError if the system runs out of memory.
     */
    @JniImpl
    public @Handle(Method.class) long GetMethodID(@JavaType(Class.class) StaticObject clazz, @Pointer TruffleObject namePtr, @Pointer TruffleObject signaturePtr) {
        String name = NativeUtils.interopPointerToString(namePtr);
        String signature = NativeUtils.interopPointerToString(signaturePtr);
        assert name != null && signature != null;
        Method method = null;
        Symbol<Name> methodName = getNames().lookup(name);
        if (methodName != null) {
            Symbol<Signature> methodSignature = getSignatures().lookupValidSignature(signature);
            if (methodSignature != null) {
                Klass klass = clazz.getMirrorKlass(getMeta());
                klass.safeInitialize();
                // Lookup only if name and type are known symbols.
                method = klass.lookupMethod(methodName, methodSignature, klass);
            }
        }
        if (method == null || method.isStatic()) {
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError, name);
        }
        return methodIds.handlify(method);
    }

    /**
     * <h3>jmethodID GetStaticMethodID(JNIEnv *env, jclass clazz, const char *name, const char
     * *sig);</h3>
     * <p>
     * Returns the method ID for a static method of a class. The method is specified by its name and
     * signature.
     * <p>
     * GetStaticMethodID() causes an uninitialized class to be initialized.
     *
     * @param clazz a Java class object.
     * @param namePtr the static method name in a 0-terminated modified UTF-8 string.
     * @param signaturePtr the method signature in a 0-terminated modified UTF-8 string.
     * @return a method ID, or NULL if the operation fails.
     * @throws NoSuchMethodError if the specified static method cannot be found. *
     * @throws ExceptionInInitializerError if the class initializer fails due to an exception.
     * @throws OutOfMemoryError if the system runs out of memory.
     */
    @JniImpl
    public @Handle(Method.class) long GetStaticMethodID(@JavaType(Class.class) StaticObject clazz, @Pointer TruffleObject namePtr, @Pointer TruffleObject signaturePtr) {
        String name = NativeUtils.interopPointerToString(namePtr);
        String signature = NativeUtils.interopPointerToString(signaturePtr);
        assert name != null && signature != null;
        Method method = null;
        Symbol<Name> methodName = getNames().lookup(name);
        if (methodName != null) {
            Symbol<Signature> methodSignature = getSignatures().lookupValidSignature(signature);
            if (methodSignature != null) {
                // Throw a NoSuchMethodError exception if we have an instance of a
                // primitive java.lang.Class
                Klass klass = clazz.getMirrorKlass(getMeta());
                if (klass.isPrimitive()) {
                    Meta meta = getMeta();
                    throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError, name);
                }

                klass.safeInitialize();
                // Lookup only if name and type are known symbols.
                if (Name._clinit_.equals(methodName)) {
                    // Never search superclasses for static initializers.
                    method = klass.lookupDeclaredMethod(methodName, methodSignature);
                } else {
                    method = klass.lookupMethod(methodName, methodSignature);
                }
            }
        }
        if (method == null || !method.isStatic()) {
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError, name);
        }
        return methodIds.handlify(method);
    }

    // endregion Get*ID

    // region GetStatic*Field

    @JniImpl
    public @JavaType(Object.class) StaticObject GetStaticObjectField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return field.getAsObject(getMeta(), field.getDeclaringKlass().tryInitializeAndGetStatics());
    }

    @JniImpl
    public boolean GetStaticBooleanField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return field.getAsBoolean(getMeta(), field.getDeclaringKlass().tryInitializeAndGetStatics(), false);
    }

    @JniImpl
    public byte GetStaticByteField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return field.getAsByte(getMeta(), field.getDeclaringKlass().tryInitializeAndGetStatics(), false);
    }

    @JniImpl
    public char GetStaticCharField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return field.getAsChar(getMeta(), field.getDeclaringKlass().tryInitializeAndGetStatics(), false);
    }

    @JniImpl
    public short GetStaticShortField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return field.getAsShort(getMeta(), field.getDeclaringKlass().tryInitializeAndGetStatics(), false);
    }

    @JniImpl
    public int GetStaticIntField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return field.getAsInt(getMeta(), field.getDeclaringKlass().tryInitializeAndGetStatics(), false);
    }

    @JniImpl
    public long GetStaticLongField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return field.getAsLong(getMeta(), field.getDeclaringKlass().tryInitializeAndGetStatics(), false);
    }

    @JniImpl
    public float GetStaticFloatField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return field.getAsFloat(getMeta(), field.getDeclaringKlass().tryInitializeAndGetStatics(), false);
    }

    @JniImpl
    public double GetStaticDoubleField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return field.getAsDouble(getMeta(), field.getDeclaringKlass().tryInitializeAndGetStatics(), false);
    }

    // endregion GetStatic*Field

    // region Get*Field

    @JniImpl
    public @JavaType(Object.class) StaticObject GetObjectField(@JavaType(Object.class) StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return field.getAsObject(getMeta(), object);
    }

    @JniImpl
    public boolean GetBooleanField(@JavaType(Object.class) StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return field.getAsBoolean(getMeta(), object, false);
    }

    @JniImpl
    public byte GetByteField(@JavaType(Object.class) StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return field.getAsByte(getMeta(), object, false);
    }

    @JniImpl
    public char GetCharField(@JavaType(Object.class) StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return field.getAsChar(getMeta(), object, false);
    }

    @JniImpl
    public short GetShortField(@JavaType(Object.class) StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return field.getAsShort(getMeta(), object, false);
    }

    @JniImpl
    public int GetIntField(@JavaType(Object.class) StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return field.getAsInt(getMeta(), object, false);
    }

    @JniImpl
    public long GetLongField(@JavaType(Object.class) StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return field.getAsLong(getMeta(), object, false);
    }

    @JniImpl
    public float GetFloatField(@JavaType(Object.class) StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return field.getAsFloat(getMeta(), object, false);
    }

    @JniImpl
    public double GetDoubleField(@JavaType(Object.class) StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return field.getAsDouble(getMeta(), object, false);
    }

    // endregion Get*Field

    // region SetStatic*Field

    @JniImpl
    public void SetStaticObjectField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, @JavaType(Object.class) StaticObject val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticBooleanField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, boolean val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticByteField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, byte val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticCharField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, char val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticShortField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, short val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticIntField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, int val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticLongField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, long val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticFloatField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, float val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticDoubleField(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, double val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    // endregion SetStatic*Field

    // region Set*Field

    @JniImpl
    public void SetObjectField(@JavaType(Object.class) StaticObject obj, @Handle(Field.class) long fieldId, @JavaType(Object.class) StaticObject val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetBooleanField(@JavaType(Object.class) StaticObject obj, @Handle(Field.class) long fieldId, boolean val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetByteField(@JavaType(Object.class) StaticObject obj, @Handle(Field.class) long fieldId, byte val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetCharField(@JavaType(Object.class) StaticObject obj, @Handle(Field.class) long fieldId, char val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetShortField(@JavaType(Object.class) StaticObject obj, @Handle(Field.class) long fieldId, short val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetIntField(@JavaType(Object.class) StaticObject obj, @Handle(Field.class) long fieldId, int val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetLongField(@JavaType(Object.class) StaticObject obj, @Handle(Field.class) long fieldId, long val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetFloatField(@JavaType(Object.class) StaticObject obj, @Handle(Field.class) long fieldId, float val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetDoubleField(@JavaType(Object.class) StaticObject obj, @Handle(Field.class) long fieldId, double val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    // endregion Set*Field

    // region Call*Method

    private Object callVirtualMethodGeneric(StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        assert !receiver.getKlass().isInterface();
        Method resolutionSeed = methodIds.getObject(methodId);
        assert !resolutionSeed.isStatic();
        assert resolutionSeed.getDeclaringKlass().isAssignableFrom(receiver.getKlass());
        Object[] args = popVarArgs(varargsPtr, resolutionSeed.getParsedSignature());

        Method target;
        if (resolutionSeed.getDeclaringKlass().isInterface()) {
            if (!resolutionSeed.isPrivate() && !resolutionSeed.isStatic()) {
                target = ((ObjectKlass) receiver.getKlass()).itableLookup(resolutionSeed.getDeclaringKlass(), resolutionSeed.getITableIndex());
            } else {
                target = resolutionSeed;
            }
        } else {
            if (resolutionSeed.isConstructor()) {
                target = resolutionSeed;
            } else if (resolutionSeed.isVirtualCall()) {
                target = receiver.getKlass().vtableLookup(resolutionSeed.getVTableIndex());
            } else {
                target = resolutionSeed;
            }
        }

        assert target != null;
        assert target.getName() == resolutionSeed.getName() && resolutionSeed.getRawSignature() == target.getRawSignature();
        return target.invokeDirect(receiver, args);
    }

    @JniImpl
    public @JavaType(Object.class) StaticObject CallObjectMethodVarargs(@JavaType(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Object result = callVirtualMethodGeneric(receiver, methodId, varargsPtr);
        return getMeta().asObject(result);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public boolean CallBooleanMethodVarargs(@JavaType(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Object result = callVirtualMethodGeneric(receiver, methodId, varargsPtr);
        return getMeta().asBoolean(result, true);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public char CallCharMethodVarargs(@JavaType(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Object result = callVirtualMethodGeneric(receiver, methodId, varargsPtr);
        return getMeta().asChar(result, true);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public byte CallByteMethodVarargs(@JavaType(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Object result = callVirtualMethodGeneric(receiver, methodId, varargsPtr);
        return getMeta().asByte(result, true);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public short CallShortMethodVarargs(@JavaType(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Object result = callVirtualMethodGeneric(receiver, methodId, varargsPtr);
        return getMeta().asShort(result, true);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public int CallIntMethodVarargs(@JavaType(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Object result = callVirtualMethodGeneric(receiver, methodId, varargsPtr);
        return getMeta().asInt(result, true);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public float CallFloatMethodVarargs(@JavaType(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Object result = callVirtualMethodGeneric(receiver, methodId, varargsPtr);
        return getMeta().asFloat(result, true);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public double CallDoubleMethodVarargs(@JavaType(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Object result = callVirtualMethodGeneric(receiver, methodId, varargsPtr);
        return getMeta().asDouble(result, true);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public long CallLongMethodVarargs(@JavaType(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Object result = callVirtualMethodGeneric(receiver, methodId, varargsPtr);
        return getMeta().asLong(result, true);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public void CallVoidMethodVarargs(@JavaType(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Object result = callVirtualMethodGeneric(receiver, methodId, varargsPtr);
        assert result instanceof StaticObject && StaticObject.isNull((StaticObject) result) : "void methods must return StaticObject.NULL";
    }

    // endregion Call*Method

    // region CallNonvirtual*Method

    @JniImpl
    public @JavaType(Object.class) StaticObject CallNonvirtualObjectMethodVarargs(@JavaType(Object.class) StaticObject receiver, @JavaType(Class.class) StaticObject clazz,
                    @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asObject(result);
    }

    @JniImpl
    public boolean CallNonvirtualBooleanMethodVarargs(@JavaType(Object.class) StaticObject receiver, @JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asBoolean(result, true);
    }

    @JniImpl
    public char CallNonvirtualCharMethodVarargs(@JavaType(Object.class) StaticObject receiver, @JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asChar(result, true);
    }

    @JniImpl
    public byte CallNonvirtualByteMethodVarargs(@JavaType(Object.class) StaticObject receiver, @JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asByte(result, true);
    }

    @JniImpl
    public short CallNonvirtualShortMethodVarargs(@JavaType(Object.class) StaticObject receiver, @JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asShort(result, true);
    }

    @JniImpl
    public int CallNonvirtualIntMethodVarargs(@JavaType(Object.class) StaticObject receiver, @JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asInt(result, true);
    }

    @JniImpl
    public float CallNonvirtualFloatMethodVarargs(@JavaType(Object.class) StaticObject receiver, @JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asFloat(result, true);
    }

    @JniImpl
    public double CallNonvirtualDoubleMethodVarargs(@JavaType(Object.class) StaticObject receiver, @JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asDouble(result, true);
    }

    @JniImpl
    public long CallNonvirtualLongMethodVarargs(@JavaType(Object.class) StaticObject receiver, @JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asLong(result, true);
    }

    @JniImpl
    public void CallNonvirtualVoidMethodVarargs(@JavaType(Object.class) StaticObject receiver, @JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
        assert result instanceof StaticObject && StaticObject.isNull((StaticObject) result) : "void methods must return StaticObject.NULL";
    }

    // endregion CallNonvirtual*Method

    // region CallStatic*Method

    @JniImpl
    public @JavaType(Object.class) StaticObject CallStaticObjectMethodVarargs(@JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asObject(result);
    }

    @JniImpl
    public boolean CallStaticBooleanMethodVarargs(@JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asBoolean(result, true);
    }

    @JniImpl
    public char CallStaticCharMethodVarargs(@JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asChar(result, true);
    }

    @JniImpl
    public byte CallStaticByteMethodVarargs(@JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asByte(result, true);
    }

    @JniImpl
    public short CallStaticShortMethodVarargs(@JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asShort(result, true);
    }

    @JniImpl
    public int CallStaticIntMethodVarargs(@JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asInt(result, true);
    }

    @JniImpl
    public float CallStaticFloatMethodVarargs(@JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asFloat(result, true);
    }

    @JniImpl
    public double CallStaticDoubleMethodVarargs(@JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asDouble(result, true);
    }

    @JniImpl
    public long CallStaticLongMethodVarargs(@JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
        return getMeta().asLong(result, true);
    }

    @JniImpl
    public void CallStaticVoidMethodVarargs(@JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass(getMeta())) == method.getDeclaringKlass();
        Object result = method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
        assert result instanceof StaticObject && StaticObject.isNull((StaticObject) result) : "void methods must return StaticObject.NULL";
    }

    // endregion CallStatic*Method

    // region New*Array

    @JniImpl
    public @JavaType(boolean[].class) StaticObject NewBooleanArray(int len) {
        return getAllocator().createNewPrimitiveArray(getMeta(), (byte) JavaKind.Boolean.getBasicType(), len);
    }

    @JniImpl
    public @JavaType(byte[].class) StaticObject NewByteArray(int len) {
        return getAllocator().createNewPrimitiveArray(getMeta(), (byte) JavaKind.Byte.getBasicType(), len);
    }

    @JniImpl
    public @JavaType(char[].class) StaticObject NewCharArray(int len) {
        return getAllocator().createNewPrimitiveArray(getMeta(), (byte) JavaKind.Char.getBasicType(), len);
    }

    @JniImpl
    public @JavaType(short[].class) StaticObject NewShortArray(int len) {
        return getAllocator().createNewPrimitiveArray(getMeta(), (byte) JavaKind.Short.getBasicType(), len);
    }

    @JniImpl
    public @JavaType(int[].class) StaticObject NewIntArray(int len) {
        return getAllocator().createNewPrimitiveArray(getMeta(), (byte) JavaKind.Int.getBasicType(), len);
    }

    @JniImpl
    public @JavaType(long[].class) StaticObject NewLongArray(int len) {
        return getAllocator().createNewPrimitiveArray(getMeta(), (byte) JavaKind.Long.getBasicType(), len);
    }

    @JniImpl
    public @JavaType(float[].class) StaticObject NewFloatArray(int len) {
        return getAllocator().createNewPrimitiveArray(getMeta(), (byte) JavaKind.Float.getBasicType(), len);
    }

    @JniImpl
    public @JavaType(double[].class) StaticObject NewDoubleArray(int len) {
        return getAllocator().createNewPrimitiveArray(getMeta(), (byte) JavaKind.Double.getBasicType(), len);
    }

    @JniImpl
    public @JavaType(Object[].class) StaticObject NewObjectArray(int length, @JavaType(Class.class) StaticObject elementClass, @JavaType(Object.class) StaticObject initialElement,
                    @Inject EspressoLanguage language) {
        assert !elementClass.getMirrorKlass(getMeta()).isPrimitive();
        StaticObject arr = elementClass.getMirrorKlass(getMeta()).allocateReferenceArray(length);
        if (length > 0) {
            // Single store check
            getInterpreterToVM().setArrayObject(language, initialElement, 0, arr);
            Arrays.fill(arr.unwrap(language), initialElement);
        }
        return arr;
    }

    // endregion New*Array

    // region Get*ArrayRegion

    @JniImpl
    @TruffleBoundary
    public void GetBooleanArrayRegion(@JavaType(boolean[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        byte[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        ByteBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Byte);
        buf.put(contents, start, len);
    }

    private void boundsCheck(int start, int len, int arrayLength) {
        assert arrayLength >= 0;
        if (start < 0 || len < 0 || start + (long) len > arrayLength) {
            Meta meta = getMeta();
            throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    @JniImpl
    @TruffleBoundary
    public void GetCharArrayRegion(@JavaType(char[].class /* or byte[].class */) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        char[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        CharBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Char).asCharBuffer();
        buf.put(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void GetByteArrayRegion(@JavaType(byte[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        byte[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        ByteBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Byte);
        buf.put(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void GetShortArrayRegion(@JavaType(short[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        short[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        ShortBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Short).asShortBuffer();
        buf.put(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void GetIntArrayRegion(@JavaType(int[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        int[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        IntBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Int).asIntBuffer();
        buf.put(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void GetFloatArrayRegion(@JavaType(float[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        float[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        FloatBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Float).asFloatBuffer();
        buf.put(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void GetDoubleArrayRegion(@JavaType(double[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        double[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        DoubleBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Double).asDoubleBuffer();
        buf.put(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void GetLongArrayRegion(@JavaType(long[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        long[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        LongBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Long).asLongBuffer();
        buf.put(contents, start, len);
    }

    // endregion Get*ArrayRegion

    // region Set*ArrayRegion

    @JniImpl
    @TruffleBoundary
    public void SetBooleanArrayRegion(@JavaType(boolean[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        byte[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        ByteBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Byte);
        buf.get(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void SetCharArrayRegion(@JavaType(char[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        char[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        CharBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Char).asCharBuffer();
        buf.get(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void SetByteArrayRegion(@JavaType(byte[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        byte[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        ByteBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Byte);
        buf.get(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void SetShortArrayRegion(@JavaType(short[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        short[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        ShortBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Short).asShortBuffer();
        buf.get(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void SetIntArrayRegion(@JavaType(int[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        int[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        IntBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Int).asIntBuffer();
        buf.get(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void SetFloatArrayRegion(@JavaType(float[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        float[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        FloatBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Float).asFloatBuffer();
        buf.get(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void SetDoubleArrayRegion(@JavaType(double[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        double[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        DoubleBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Double).asDoubleBuffer();
        buf.get(contents, start, len);
    }

    @JniImpl
    @TruffleBoundary
    public void SetLongArrayRegion(@JavaType(long[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        long[] contents = array.unwrap(language);
        boundsCheck(start, len, contents.length);
        LongBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Long).asLongBuffer();
        buf.get(contents, start, len);
    }

    // endregion Set*ArrayRegion

    // region Strings

    /**
     * <h3>jsize GetStringLength(JNIEnv *env, jstring string);</h3>
     * <p>
     * Returns the length (the count of Unicode characters) of a Java string.
     *
     * @param string a Java string object.
     * @return the length of the Java string.
     */
    @JniImpl
    public int GetStringLength(@JavaType(String.class) StaticObject string) {
        if (StaticObject.isNull(string)) {
            return 0;
        }
        return (int) getMeta().java_lang_String_length.invokeDirect(string);
    }

    /**
     * <h3>jstring NewStringUTF(JNIEnv *env, const char *bytes);</h3>
     * <p>
     * Constructs a new java.lang.String object from an array of characters in modified UTF-8
     * encoding.
     *
     * @param bytesPtr pointer to a modified UTF-8 string.
     * @return a Java string object, or NULL if the string cannot be constructed.
     * @throws OutOfMemoryError if the system runs out of memory.
     */
    @JniImpl
    public @JavaType(String.class) StaticObject NewStringUTF(@Pointer TruffleObject bytesPtr) {
        String hostString = NativeUtils.fromUTF8Ptr(bytesPtr);
        return getMeta().toGuestString(hostString);
    }

    /**
     * <h3>const jchar * GetStringCritical(JNIEnv *env, jstring string, jboolean *isCopy);</h3>
     * <p>
     * The semantics of these two functions are similar to the existing Get/ReleaseStringChars
     * functions. If possible, the VM returns a pointer to string elements; otherwise, a copy is
     * made.
     *
     * <p>
     * However, there are significant restrictions on how these functions can be used. In a code
     * segment enclosed by Get/ReleaseStringCritical calls, the native code must not issue arbitrary
     * JNI calls, or cause the current thread to block.
     *
     * <p>
     * The restrictions on Get/ReleaseStringCritical are similar to those on
     * Get/ReleasePrimitiveArrayCritical.
     */
    @JniImpl
    @TruffleBoundary
    public @Pointer TruffleObject GetStringCritical(@JavaType(String.class) StaticObject str, @Pointer TruffleObject isCopyPtr, @Inject EspressoLanguage language, @Inject Meta meta) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = NativeUtils.directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // always copy since pinning is not supported
        }
        StaticObject stringChars;
        if (getJavaVersion().compactStringsEnabled()) {
            stringChars = (StaticObject) meta.java_lang_String_toCharArray.invokeDirect(str);
        } else {
            stringChars = meta.java_lang_String_value.getObject(str);
        }
        int len = stringChars.length(language);
        ByteBuffer criticalRegion = allocateDirect(len, JavaKind.Char); // direct byte buffer
        // (non-relocatable)
        @Pointer
        TruffleObject address = NativeUtils.byteBufferPointer(criticalRegion);
        GetCharArrayRegion(stringChars, 0, len, address, language);
        return address;
    }

    @JniImpl
    @TruffleBoundary
    public @Pointer TruffleObject GetStringUTFChars(@JavaType(String.class) StaticObject str, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = NativeUtils.directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // always copy since pinning is not supported
        }
        byte[] bytes = ModifiedUtf8.fromJavaString(getMeta().toHostString(str), true);
        ByteBuffer region = allocateDirect(bytes.length);
        region.put(bytes);
        return NativeUtils.byteBufferPointer(region);
    }

    /**
     * <h3>const jchar * GetStringChars(JNIEnv *env, jstring string, jboolean *isCopy);</h3>
     * <p>
     * Returns a pointer to the array of Unicode characters of the string. This pointer is valid
     * until ReleaseStringChars() is called.
     * <p>
     * If isCopy is not NULL, then *isCopy is set to JNI_TRUE if a copy is made; or it is set to
     * JNI_FALSE if no copy is made.
     *
     * @param string a Java string object.
     * @param isCopyPtr a pointer to a boolean. Returns a pointer to a Unicode string, or NULL if
     *            the operation fails.
     */
    @JniImpl
    @TruffleBoundary
    public @Pointer TruffleObject GetStringChars(@JavaType(String.class) StaticObject string, @Pointer TruffleObject isCopyPtr, @Inject EspressoLanguage language) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = NativeUtils.directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // always copy since pinning is not supported
        }
        char[] chars;
        if (getJavaVersion().compactStringsEnabled()) {
            StaticObject wrappedChars = (StaticObject) getMeta().java_lang_String_toCharArray.invokeDirect(string);
            chars = wrappedChars.unwrap(language);
        } else {
            chars = getMeta().java_lang_String_value.getObject(string).unwrap(language);
        }
        // Add one for zero termination.
        ByteBuffer bb = allocateDirect(chars.length + 1, JavaKind.Char);
        CharBuffer region = bb.asCharBuffer();
        region.put(chars);
        region.put((char) 0);
        return NativeUtils.byteBufferPointer(bb);
    }

    @TruffleBoundary
    public void releasePtr(@Pointer TruffleObject ptr) {
        long nativePtr = NativeUtils.interopAsPointer(ptr);
        assert nativeBuffers.containsKey(nativePtr);
        nativeBuffers.remove(nativePtr);
    }

    /**
     * <h3>void ReleaseStringChars(JNIEnv *env, jstring string, const jchar *chars);</h3>
     * <p>
     * Informs the VM that the native code no longer needs access to chars. The chars argument is a
     * pointer obtained from string using GetStringChars().
     *
     * @param string a Java string object.
     * @param charsPtr a pointer to a Unicode string.
     */
    @JniImpl
    public void ReleaseStringChars(@SuppressWarnings("unused") @JavaType(String.class) StaticObject string, @Pointer TruffleObject charsPtr) {
        releasePtr(charsPtr);
    }

    @JniImpl
    public void ReleaseStringUTFChars(@SuppressWarnings("unused") @JavaType(String.class) StaticObject str, @Pointer TruffleObject charsPtr) {
        releasePtr(charsPtr);
    }

    @JniImpl
    public void ReleaseStringCritical(@SuppressWarnings("unused") @JavaType(String.class) StaticObject str, @Pointer TruffleObject criticalRegionPtr) {
        releasePtr(criticalRegionPtr);
    }

    @JniImpl
    @TruffleBoundary
    public @JavaType(String.class) StaticObject NewString(@Pointer TruffleObject unicodePtr, int len, @Inject EspressoLanguage language, @Inject Meta meta) {
        // TODO(garcia) : works only for UTF16 encoded strings.
        final char[] array = new char[len];
        StaticObject value = StaticObject.wrap(array, meta);
        SetCharArrayRegion(value, 0, len, unicodePtr, language);
        return getMeta().toGuestString(new String(array));
    }

    /**
     * <h3>void GetStringRegion(JNIEnv *env, jstring str, jsize start, jsize len, jchar *buf);</h3>
     * <p>
     * Copies len number of Unicode characters beginning at offset start to the given buffer buf.
     * <p>
     * Throws StringIndexOutOfBoundsException on index overflow.
     */
    @JniImpl
    @TruffleBoundary
    public void GetStringRegion(@JavaType(String.class) StaticObject str, int start, int len, @Pointer TruffleObject bufPtr, @Inject EspressoLanguage language) {
        char[] chars;
        if (getJavaVersion().compactStringsEnabled()) {
            chars = getMeta().toHostString(str).toCharArray();
        } else {
            chars = getMeta().java_lang_String_value.getObject(str).unwrap(language);
        }
        if (start < 0 || start + (long) len > chars.length) {
            Meta meta = getMeta();
            throw meta.throwException(meta.java_lang_StringIndexOutOfBoundsException);
        }
        CharBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Char).asCharBuffer();
        buf.put(chars, start, len);
    }

    @JniImpl
    public int GetStringUTFLength(@JavaType(String.class) StaticObject string) {
        return ModifiedUtf8.utfLength(getMeta().toHostString(string));
    }

    @JniImpl
    @TruffleBoundary
    public void GetStringUTFRegion(@JavaType(String.class) StaticObject str, int start, int len, @Pointer TruffleObject bufPtr) {
        Meta meta = getMeta();
        String hostString = meta.toHostString(str);
        if (start < 0 || len < 0 || start > hostString.length() - len) {
            throw meta.throwException(meta.java_lang_StringIndexOutOfBoundsException);
        }
        // always 0-terminated.
        byte[] bytes = ModifiedUtf8.fromJavaString(hostString, start, len, true);
        ByteBuffer buf = NativeUtils.directByteBuffer(bufPtr, bytes.length, JavaKind.Byte);
        buf.put(bytes);
    }

    // endregion Strings

    // region Exception handling

    /**
     * <h3>jboolean ExceptionCheck(JNIEnv *env);</h3>
     * <p>
     * A convenience function to check for pending exceptions without creating a local reference to
     * the exception object.
     *
     * @return JNI_TRUE when there is a pending exception; otherwise, returns JNI_FALSE.
     */
    @JniImpl
    public boolean ExceptionCheck() {
        EspressoException ex = getPendingEspressoException();
        // ex != null => ex != NULL
        assert ex == null || StaticObject.notNull(ex.getGuestException());
        return ex != null;
    }

    /**
     * <h3>void ExceptionClear(JNIEnv *env);</h3>
     * <p>
     * Clears any exception that is currently being thrown. If no exception is currently being
     * thrown, this routine has no effect.
     */
    @JniImpl
    public void ExceptionClear() {
        clearPendingException();
    }

    /**
     * <h3>jint Throw(JNIEnv *env, jthrowable obj);</h3>
     * <p>
     * Causes a {@link java.lang.Throwable} object to be thrown.
     *
     * @param obj a {@link java.lang.Throwable} object.
     * @return 0 on success; a negative value on failure.
     */
    @JniImpl
    public static int Throw(@JavaType(Throwable.class) StaticObject obj, @Inject Meta meta) {
        assert meta.java_lang_Throwable.isAssignableFrom(obj.getKlass());
        // The TLS exception slot will be set by the JNI wrapper.
        // Throwing methods always return the default value, in this case 0 (success).
        throw meta.throwException(obj);
    }

    /**
     * <h3>jint ThrowNew(JNIEnv *env, jclass clazz, const char *message);</h3>
     * <p>
     * Constructs an exception object from the specified class with the message specified by message
     * and causes that exception to be thrown.
     *
     * @param clazz a subclass of java.lang.Throwable.
     * @param messagePtr the message used to construct the {@link java.lang.Throwable} object. The
     *            string is encoded in modified UTF-8.
     * @return 0 on success; a negative value on failure.
     * @throws EspressoException the newly constructed {@link java.lang.Throwable} object.
     */
    @JniImpl
    public int ThrowNew(@JavaType(Class.class) StaticObject clazz, @Pointer TruffleObject messagePtr, @Inject Meta meta) {
        String message = NativeUtils.interopPointerToString(messagePtr);
        // The TLS exception slot will be set by the JNI wrapper.
        // Throwing methods always return the default value, in this case 0 (success).
        throw meta.throwExceptionWithMessage((ObjectKlass) clazz.getMirrorKlass(getMeta()), message);
    }

    /**
     * <h3>jthrowable ExceptionOccurred(JNIEnv *env);</h3>
     * <p>
     * Determines if an exception is being thrown. The exception stays being thrown until either the
     * native code calls {@link #ExceptionClear}, or the Java code handles the exception.
     *
     * @return the exception object that is currently in the process of being thrown, or NULL if no
     *         exception is currently being thrown.
     */
    @JniImpl
    public @JavaType(Throwable.class) StaticObject ExceptionOccurred() {
        StaticObject ex = getPendingException();
        if (ex == null) {
            ex = StaticObject.NULL;
        }
        return ex;
    }

    /**
     * <h3>void ExceptionDescribe(JNIEnv *env);</h3>
     * <p>
     * Prints an exception and a backtrace of the stack to a system error-reporting channel, such as
     * stderr. This is a convenience routine provided for debugging.
     */
    @JniImpl
    public void ExceptionDescribe() {
        EspressoException ex = getPendingEspressoException();
        if (ex != null) {
            StaticObject guestException = ex.getGuestException();
            assert InterpreterToVM.instanceOf(guestException, getMeta().java_lang_Throwable);
            // Dynamic lookup.
            Method printStackTrace = guestException.getKlass().lookupMethod(Name.printStackTrace, Signature._void);
            printStackTrace.invokeDirect(guestException);
            // Restore exception cleared by invokeDirect.
            setPendingException(ex);
        }
    }

    /**
     * <h3>void FatalError(JNIEnv *env, const char *msg);</h3>
     * <p>
     * Raises a fatal error and does not expect the VM to recover. This function does not return.
     *
     * @param msgPtr an error message. The string is encoded in modified UTF-8.
     */
    @JniImpl
    @TruffleBoundary
    public void FatalError(@Pointer TruffleObject msgPtr, @Inject SubstitutionProfiler profiler) {
        String msg = NativeUtils.interopPointerToString(msgPtr);
        PrintWriter writer = new PrintWriter(getContext().err(), true);
        writer.println("FATAL ERROR in native method: " + msg);
        // TODO print stack trace
        getContext().truffleExit(profiler, 1);
        throw EspressoError.fatal(msg);
    }

    // endregion Exception handling

    // region Monitors

    @JniImpl
    public static int MonitorEnter(@JavaType(Object.class) StaticObject object, @Inject Meta meta) {
        InterpreterToVM.monitorEnter(object, meta);
        return JNI_OK;
    }

    @JniImpl
    public int MonitorExit(@JavaType(Object.class) StaticObject object, @Inject Meta meta) {
        try {
            InterpreterToVM.monitorExit(object, meta);
        } catch (EspressoException e) {
            assert InterpreterToVM.instanceOf(e.getGuestException(), getMeta().java_lang_IllegalMonitorStateException);
            setPendingException(e);
            return JNI_ERR;
        }
        return JNI_OK;
    }

    // endregion Monitors

    // region Get/SetObjectArrayElement

    /**
     * <h3>jobject GetObjectArrayElement(JNIEnv *env, jobjectArray array, jsize index);</h3>
     * <p>
     * Returns an element of an Object array.
     *
     * @param array a Java array.
     * @param index array index.
     * @return a Java object.
     * @throws ArrayIndexOutOfBoundsException if index does not specify a valid index in the array.
     */
    @JniImpl
    public @JavaType(Object.class) StaticObject GetObjectArrayElement(@JavaType(Object[].class) StaticObject array, int index, @Inject EspressoLanguage language) {
        return getInterpreterToVM().getArrayObject(language, index, array);
    }

    /**
     * <h3>void SetObjectArrayElement(JNIEnv *env, jobjectArray array, jsize index, jobject value);
     * </h3>
     * <p>
     * Sets an element of an Object array.
     *
     * @param array a Java array.
     * @param index array index.
     * @param value the new value.
     * @throws ArrayIndexOutOfBoundsException if index does not specify a valid index in the array.
     * @throws ArrayStoreException if the class of value is not a subclass of the element class of
     *             the array.
     */
    @JniImpl
    public void SetObjectArrayElement(@JavaType(Object[].class) StaticObject array, int index, @JavaType(Object.class) StaticObject value, @Inject EspressoLanguage language) {
        getInterpreterToVM().setArrayObject(language, value, index, array);
    }

    // endregion Get/SetObjectArrayElement

    // region Get*ArrayElements

    @JniImpl
    @TruffleBoundary
    public @Pointer TruffleObject GetBooleanArrayElements(@JavaType(boolean[].class) StaticObject array, @Pointer TruffleObject isCopyPtr, @Inject EspressoLanguage language) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = NativeUtils.directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        byte[] data = array.unwrap(language);
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Boolean);
        ByteBuffer elements = bytes;
        elements.put(data);
        return NativeUtils.byteBufferPointer(bytes);
    }

    @JniImpl
    @TruffleBoundary
    public @Pointer TruffleObject GetCharArrayElements(@JavaType(char[].class) StaticObject array, @Pointer TruffleObject isCopyPtr, @Inject EspressoLanguage language) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = NativeUtils.directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        char[] data = array.unwrap(language);
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Char);
        CharBuffer elements = bytes.asCharBuffer();
        elements.put(data);
        return NativeUtils.byteBufferPointer(bytes);
    }

    @JniImpl
    @TruffleBoundary
    public @Pointer TruffleObject GetByteArrayElements(@JavaType(byte[].class) StaticObject array, @Pointer TruffleObject isCopyPtr, @Inject EspressoLanguage language) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = NativeUtils.directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        byte[] data = array.unwrap(language);
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Byte);
        ByteBuffer elements = bytes;
        elements.put(data);
        return NativeUtils.byteBufferPointer(bytes);
    }

    @JniImpl
    @TruffleBoundary
    public @Pointer TruffleObject GetShortArrayElements(@JavaType(short[].class) StaticObject array, @Pointer TruffleObject isCopyPtr, @Inject EspressoLanguage language) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = NativeUtils.directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        short[] data = array.unwrap(language);
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Short);
        ShortBuffer elements = bytes.asShortBuffer();
        elements.put(data);
        return NativeUtils.byteBufferPointer(bytes);
    }

    @JniImpl
    @TruffleBoundary
    public @Pointer TruffleObject GetIntArrayElements(@JavaType(int[].class) StaticObject array, @Pointer TruffleObject isCopyPtr, @Inject EspressoLanguage language) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = NativeUtils.directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        int[] data = array.unwrap(language);
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Int);
        IntBuffer elements = bytes.asIntBuffer();
        elements.put(data);
        return NativeUtils.byteBufferPointer(bytes);
    }

    @JniImpl
    @TruffleBoundary
    public @Pointer TruffleObject GetFloatArrayElements(@JavaType(float[].class) StaticObject array, @Pointer TruffleObject isCopyPtr, @Inject EspressoLanguage language) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = NativeUtils.directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        float[] data = array.unwrap(language);
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Float);
        FloatBuffer elements = bytes.asFloatBuffer();
        elements.put(data);
        return NativeUtils.byteBufferPointer(bytes);
    }

    @JniImpl
    @TruffleBoundary
    public @Pointer TruffleObject GetDoubleArrayElements(@JavaType(double[].class) StaticObject array, @Pointer TruffleObject isCopyPtr, @Inject EspressoLanguage language) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = NativeUtils.directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        double[] data = array.unwrap(language);
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Double);
        DoubleBuffer elements = bytes.asDoubleBuffer();
        elements.put(data);
        return NativeUtils.byteBufferPointer(bytes);
    }

    @JniImpl
    @TruffleBoundary
    public @Pointer TruffleObject GetLongArrayElements(@JavaType(long[].class) StaticObject array, @Pointer TruffleObject isCopyPtr, @Inject EspressoLanguage language) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = NativeUtils.directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        long[] data = array.unwrap(language);
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Long);
        LongBuffer elements = bytes.asLongBuffer();
        elements.put(data);
        return NativeUtils.byteBufferPointer(bytes);
    }

    // endregion Get*ArrayElements

    // region Release*ArrayElements

    private void ReleasePrimitiveArrayElements(StaticObject object, @Pointer TruffleObject bufPtr, int mode, EspressoLanguage language) {
        if (mode == 0 || mode == JNI_COMMIT) { // Update array contents.
            StaticObject array = object;
            StaticObject clazz = GetObjectClass(array);
            JavaKind componentKind = ((ArrayKlass) clazz.getMirrorKlass(getMeta())).getComponentType().getJavaKind();
            assert componentKind.isPrimitive();
            int length = GetArrayLength(array, language);
            // @formatter:off
            switch (componentKind) {
                case Boolean : SetBooleanArrayRegion(array, 0, length, bufPtr, language); break;
                case Byte    : SetByteArrayRegion(array, 0, length, bufPtr, language);    break;
                case Short   : SetShortArrayRegion(array, 0, length, bufPtr, language);   break;
                case Char    : SetCharArrayRegion(array, 0, length, bufPtr, language);    break;
                case Int     : SetIntArrayRegion(array, 0, length, bufPtr, language);     break;
                case Float   : SetFloatArrayRegion(array, 0, length, bufPtr, language);   break;
                case Long    : SetLongArrayRegion(array, 0, length, bufPtr, language);    break;
                case Double  : SetDoubleArrayRegion(array, 0, length, bufPtr, language);  break;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
        }
        if (mode == 0 || mode == JNI_ABORT) { // Dispose copy.
            releasePtr(bufPtr);
        }
    }

    @JniImpl
    public void ReleaseBooleanArrayElements(@JavaType(boolean[].class) StaticObject object, @Pointer TruffleObject bufPtr, int mode, @Inject EspressoLanguage language) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Boolean;
        ReleasePrimitiveArrayElements(object, bufPtr, mode, language);
    }

    @JniImpl
    public void ReleaseByteArrayElements(@JavaType(byte[].class) StaticObject object, @Pointer TruffleObject bufPtr, int mode, @Inject EspressoLanguage language) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Byte;
        ReleasePrimitiveArrayElements(object, bufPtr, mode, language);
    }

    @JniImpl
    public void ReleaseCharArrayElements(@JavaType(char[].class) StaticObject object, @Pointer TruffleObject bufPtr, int mode, @Inject EspressoLanguage language) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Char;
        ReleasePrimitiveArrayElements(object, bufPtr, mode, language);
    }

    @JniImpl
    public void ReleaseShortArrayElements(@JavaType(short[].class) StaticObject object, @Pointer TruffleObject bufPtr, int mode, @Inject EspressoLanguage language) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Short;
        ReleasePrimitiveArrayElements(object, bufPtr, mode, language);
    }

    @JniImpl
    public void ReleaseIntArrayElements(@JavaType(int[].class) StaticObject object, @Pointer TruffleObject bufPtr, int mode, @Inject EspressoLanguage language) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Int;
        ReleasePrimitiveArrayElements(object, bufPtr, mode, language);
    }

    @JniImpl
    public void ReleaseLongArrayElements(@JavaType(long[].class) StaticObject object, @Pointer TruffleObject bufPtr, int mode, @Inject EspressoLanguage language) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Long;
        ReleasePrimitiveArrayElements(object, bufPtr, mode, language);
    }

    @JniImpl
    public void ReleaseFloatArrayElements(@JavaType(float[].class) StaticObject object, @Pointer TruffleObject bufPtr, int mode, @Inject EspressoLanguage language) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Float;
        ReleasePrimitiveArrayElements(object, bufPtr, mode, language);
    }

    @JniImpl
    public void ReleaseDoubleArrayElements(@JavaType(double[].class) StaticObject object, @Pointer TruffleObject bufPtr, int mode, @Inject EspressoLanguage language) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Double;
        ReleasePrimitiveArrayElements(object, bufPtr, mode, language);
    }

    // endregion Release*ArrayElements

    // region DirectBuffers

    /**
     * <h3>jobject NewDirectByteBuffer(JNIEnv* env, void* address, jlong capacity);</h3>
     * <p>
     * Allocates and returns a direct java.nio.ByteBuffer referring to the block of memory starting
     * at the memory address address and extending capacity bytes.
     * <p>
     * Native code that calls this function and returns the resulting byte-buffer object to
     * Java-level code should ensure that the buffer refers to a valid region of memory that is
     * accessible for reading and, if appropriate, writing. An attempt to access an invalid memory
     * location from Java code will either return an arbitrary value, have no visible effect, or
     * cause an unspecified exception to be thrown.
     *
     * @param addressPtr the starting address of the memory region (must not be NULL)
     * @param capacity the size in bytes of the memory region (must be positive)
     * @return a local reference to the newly-instantiated java.nio.ByteBuffer object. Returns NULL
     *         if an exception occurs, or if JNI access to direct buffers is not supported by this
     *         virtual machine.
     * @throws OutOfMemoryError if allocation of the ByteBuffer object fails
     */
    @JniImpl
    public @JavaType(internalName = "Ljava/nio/DirectByteBuffer;") StaticObject NewDirectByteBuffer(@Pointer TruffleObject addressPtr, long capacity) {
        Meta meta = getMeta();
        StaticObject instance = meta.java_nio_DirectByteBuffer.allocateInstance(getContext());
        long address = NativeUtils.interopAsPointer(addressPtr);
        meta.java_nio_DirectByteBuffer_init_long_int.invokeDirect(instance, address, (int) capacity);
        return instance;
    }

    /**
     * <h3>void* GetDirectBufferAddress(JNIEnv* env, jobject buf);</h3>
     * <p>
     * Fetches and returns the starting address of the memory region referenced by the given direct
     * {@link java.nio.Buffer}. This function allows native code to access the same memory region
     * that is accessible to Java code via the buffer object.
     *
     * @param buf a direct java.nio.Buffer object (must not be NULL)
     * @return the starting address of the memory region referenced by the buffer. Returns NULL if
     *         the memory region is undefined, if the given object is not a direct java.nio.Buffer,
     *         or if JNI access to direct buffers is not supported by this virtual machine.
     */
    @JniImpl
    public @Pointer TruffleObject GetDirectBufferAddress(@JavaType(java.nio.Buffer.class) StaticObject buf) {
        assert StaticObject.notNull(buf);
        // TODO(peterssen): Returns NULL if the memory region is undefined.
        // HotSpot check.
        assert StaticObject.notNull(buf);
        if (!InterpreterToVM.instanceOf(buf, getMeta().sun_nio_ch_DirectBuffer)) {
            return RawPointer.nullInstance();
        }
        // Check stated in the spec.
        if (StaticObject.notNull(buf) && !InterpreterToVM.instanceOf(buf, getMeta().java_nio_Buffer)) {
            return RawPointer.nullInstance();
        }
        return RawPointer.create((long) getMeta().java_nio_Buffer_address.get(buf));
    }

    /**
     * <h3>jlong GetDirectBufferCapacity(JNIEnv* env, jobject buf);</h3>
     * <p>
     * Fetches and returns the capacity of the memory region referenced by the given direct
     * {@link java.nio.Buffer}. The capacity is the number of elements that the memory region
     * contains.
     *
     * @param buf a direct java.nio.Buffer object (must not be NULL)
     * @return the capacity of the memory region associated with the buffer. Returns -1 if the given
     *         object is not a direct java.nio.Buffer, if the object is an unaligned view buffer and
     *         the processor architecture does not support unaligned access, or if JNI access to
     *         direct buffers is not supported by this virtual machine.
     */
    @JniImpl
    public long GetDirectBufferCapacity(@JavaType(java.nio.Buffer.class) StaticObject buf) {
        assert StaticObject.notNull(buf);
        // TODO(peterssen): Return -1 if the object is an unaligned view buffer and the processor
        // architecture does not support unaligned access.
        // HotSpot check.
        assert StaticObject.notNull(buf);
        if (!InterpreterToVM.instanceOf(buf, getMeta().sun_nio_ch_DirectBuffer)) {
            return -1L;
        }
        // Check stated in the spec.
        if (!InterpreterToVM.instanceOf(buf, getMeta().java_nio_Buffer)) {
            return -1L;
        }
        return (int) getMeta().java_nio_Buffer_capacity.get(buf);
    }

    // endregion DirectBuffers

    // region Register/Unregister natives

    @JniImpl
    @TruffleBoundary
    public int RegisterNative(@JavaType(Class.class) StaticObject clazz, @Pointer TruffleObject methodNamePtr, @Pointer TruffleObject methodSignaturePtr, @Pointer TruffleObject closure) {
        String methodName = NativeUtils.interopPointerToString(methodNamePtr);
        String methodSignature = NativeUtils.interopPointerToString(methodSignaturePtr);
        assert methodName != null && methodSignature != null;

        Symbol<Name> name = getNames().lookup(methodName);
        Symbol<Signature> signature = getSignatures().lookupValidSignature(methodSignature);

        Meta meta = getMeta();
        if (name == null || signature == null) {
            setPendingException(Meta.initException(meta.java_lang_NoSuchMethodError));
            return JNI_ERR;
        }

        Method targetMethod = clazz.getMirrorKlass(getMeta()).lookupDeclaredMethod(name, signature);
        if (targetMethod != null && targetMethod.isNative()) {
            targetMethod.unregisterNative();
            getSubstitutions().removeRuntimeSubstitution(targetMethod);
        } else {
            setPendingException(Meta.initException(meta.java_lang_NoSuchMethodError));
            return JNI_ERR;
        }

        Substitutions.EspressoRootNodeFactory factory = null;
        // Lookup known VM methods to shortcut native boudaries.
        factory = lookupKnownVmMethods(closure, targetMethod);

        if (factory == null) {
            NativeSignature ns = Method.buildJniNativeSignature(targetMethod.getParsedSignature());
            final TruffleObject boundNative = getNativeAccess().bindSymbol(closure, ns);
            factory = createJniRootNodeFactory(() -> EspressoRootNode.createNative(targetMethod.getMethodVersion(), boundNative), targetMethod);
        }

        Symbol<Type> classType = clazz.getMirrorKlass(getMeta()).getType();
        getSubstitutions().registerRuntimeSubstitution(classType, name, signature, factory, true);
        return JNI_OK;
    }

    private Substitutions.EspressoRootNodeFactory lookupKnownVmMethods(@Pointer TruffleObject closure, Method targetMethod) {
        try {
            long jvmMethodAddress = InteropLibrary.getUncached().asPointer(closure);
            CallableFromNative.Factory knownVmMethod = getVM().lookupKnownVmMethod(jvmMethodAddress);
            if (knownVmMethod != null) {
                if (!CallableFromNative.validParameterCount(knownVmMethod, targetMethod.getMethodVersion())) {
                    getLogger().warning("Implicit intrinsification of VM method does not have matching parameter counts:");
                    getLogger().warning("VM method " + knownVmMethod.methodName() + " has " + knownVmMethod.parameterCount() + " parameters,");
                    getLogger().warning(
                                    "Bound to " + (targetMethod.isStatic() ? "static" : "instance") + " method " + targetMethod.getNameAsString() + " which has " + targetMethod.getParameterCount() +
                                                    " parameters");
                    return null;
                }
                return createJniRootNodeFactory(() -> EspressoRootNode.createIntrinsifiedNative(targetMethod.getMethodVersion(), knownVmMethod, getVM()), targetMethod);
            }
        } catch (UnsupportedMessageException e) {
            // ignore
        }
        return null;
    }

    private static Substitutions.EspressoRootNodeFactory createJniRootNodeFactory(Supplier<EspressoRootNode> methodRootNodeSupplier, Method targetMethod) {
        return new Substitutions.EspressoRootNodeFactory() {
            @Override
            public EspressoRootNode createNodeIfValid(Method methodToSubstitute, boolean forceValid) {
                if (forceValid || methodToSubstitute == targetMethod) {
                    // Runtime substitutions apply only to the given method.
                    return methodRootNodeSupplier.get();
                }

                Substitutions.getLogger().warning(new Supplier<String>() {
                    @Override
                    public String get() {
                        StaticObject expectedLoader = targetMethod.getDeclaringKlass().getDefiningClassLoader();
                        StaticObject givenLoader = methodToSubstitute.getDeclaringKlass().getDefiningClassLoader();
                        return "Runtime substitution for " + targetMethod + " does not apply.\n" +
                                        "\tExpected class loader: " + EspressoInterop.toDisplayString(expectedLoader, false) + "\n" +
                                        "\tGiven class loader: " + EspressoInterop.toDisplayString(givenLoader, false) + "\n";
                    }
                });
                return null;
            }
        };
    }

    /**
     * <h3>jint UnregisterNatives(JNIEnv *env, jclass clazz);</h3>
     * <p>
     * Unregisters native methods of a class. The class goes back to the state before it was linked
     * or registered with its native method functions.
     * <p>
     * This function should not be used in normal native code. Instead, it provides special programs
     * a way to reload and relink native libraries.
     *
     * @param clazz a Java class object.
     *            <p>
     *            Returns 0 on success; returns a negative value on failure.
     */
    @JniImpl
    @TruffleBoundary
    public int UnregisterNatives(@JavaType(Class.class) StaticObject clazz) {
        Klass klass = clazz.getMirrorKlass(getMeta());
        for (Method m : klass.getDeclaredMethods()) {
            if (m.isNative()) {
                getSubstitutions().removeRuntimeSubstitution(m);
                m.unregisterNative();
            }
        }
        return JNI_OK;
    }

    // endregion Register/Unregister natives

    // region Reflection

    /**
     * <h3>jobject ToReflectedMethod(JNIEnv *env, jclass cls, jmethodID methodID, jboolean
     * isStatic);</h3>
     * <p>
     * Converts a method ID derived from cls to a java.lang.reflect.Method or
     * java.lang.reflect.Constructor object. isStatic must be set to JNI_TRUE if the method ID
     * refers to a static field, and JNI_FALSE otherwise.
     * <p>
     * Throws OutOfMemoryError and returns 0 if fails.
     */
    @JniImpl
    public @JavaType(java.lang.reflect.Executable.class) StaticObject ToReflectedMethod(@JavaType(Class.class) StaticObject unused, @Handle(Method.class) long methodId,
                    @SuppressWarnings("unused") boolean isStatic, @Inject EspressoLanguage language) {
        Method method = methodIds.getObject(methodId);
        assert method.getDeclaringKlass().isAssignableFrom(unused.getMirrorKlass(getMeta()));

        StaticObject methods = null;
        if (method.isConstructor()) {
            methods = getVM().JVM_GetClassDeclaredConstructors(method.getDeclaringKlass().mirror(), false);
        } else {
            methods = getVM().JVM_GetClassDeclaredMethods(method.getDeclaringKlass().mirror(), false);
        }

        for (StaticObject declMethod : methods.<StaticObject[]> unwrap(language)) {
            assert InterpreterToVM.instanceOf(declMethod, getMeta().java_lang_reflect_Executable);
            Method m = null;
            if (method.isConstructor()) {
                assert InterpreterToVM.instanceOf(declMethod, getMeta().java_lang_reflect_Constructor);
                m = (Method) getMeta().HIDDEN_CONSTRUCTOR_KEY.getHiddenObject(declMethod);
            } else {
                assert InterpreterToVM.instanceOf(declMethod, getMeta().java_lang_reflect_Method);
                m = (Method) getMeta().HIDDEN_METHOD_KEY.getHiddenObject(declMethod);
            }
            if (method == m) {
                return declMethod;
            }
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Method/constructor not found " + method);
    }

    /**
     * <h3>jobject ToReflectedField(JNIEnv *env, jclass cls, jfieldID fieldID, jboolean isStatic);
     * </h3>
     * <p>
     * Converts a field ID derived from cls to a java.lang.reflect.Field object. isStatic must be
     * set to JNI_TRUE if fieldID refers to a static field, and JNI_FALSE otherwise.
     * <p>
     * Throws OutOfMemoryError and returns 0 if fails.
     */
    @JniImpl
    public @JavaType(java.lang.reflect.Field.class) StaticObject ToReflectedField(@JavaType(Class.class) StaticObject unused, @Handle(Field.class) long fieldId,
                    @SuppressWarnings("unused") boolean isStatic, @Inject EspressoLanguage language) {
        Field field = fieldIds.getObject(fieldId);
        assert field.getDeclaringKlass().isAssignableFrom(unused.getMirrorKlass(getMeta()));
        StaticObject fields = getVM().JVM_GetClassDeclaredFields(field.getDeclaringKlass().mirror(), false);
        for (StaticObject declField : fields.<StaticObject[]> unwrap(language)) {
            assert InterpreterToVM.instanceOf(declField, getMeta().java_lang_reflect_Field);
            Field f = (Field) getMeta().HIDDEN_FIELD_KEY.getHiddenObject(declField);
            if (field == f) {
                return declField;
            }
        }

        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Field not found " + field);
    }

    /**
     * <h3>jfieldID FromReflectedField(JNIEnv *env, jobject field);</h3>
     * <p>
     * Converts a java.lang.reflect.Field to a field ID.
     */
    @JniImpl
    public @Handle(Field.class) long FromReflectedField(@JavaType(java.lang.reflect.Field.class) StaticObject field) {
        assert InterpreterToVM.instanceOf(field, getMeta().java_lang_reflect_Field);
        Field guestField = Field.getReflectiveFieldRoot(field, getMeta());
        guestField.getDeclaringKlass().initialize();
        return fieldIds.handlify(guestField);
    }

    /**
     * <h3>jmethodID FromReflectedMethod(JNIEnv *env, jobject method);</h3>
     * <p>
     * Converts a java.lang.reflect.Method or java.lang.reflect.Constructor object to a method ID.
     */
    @JniImpl
    public @Handle(Method.class) long FromReflectedMethod(@JavaType(java.lang.reflect.Executable.class) StaticObject method) {
        assert InterpreterToVM.instanceOf(method, getMeta().java_lang_reflect_Method) || InterpreterToVM.instanceOf(method, getMeta().java_lang_reflect_Constructor);
        Method guestMethod;
        if (InterpreterToVM.instanceOf(method, getMeta().java_lang_reflect_Method)) {
            guestMethod = Method.getHostReflectiveMethodRoot(method, getMeta());
        } else if (InterpreterToVM.instanceOf(method, getMeta().java_lang_reflect_Constructor)) {
            guestMethod = Method.getHostReflectiveConstructorRoot(method, getMeta());
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere();
        }
        guestMethod.getDeclaringKlass().initialize();
        return methodIds.handlify(guestMethod);
    }

    // endregion Reflection

    // region JNI handles

    /**
     * <h3>jobject NewLocalRef(JNIEnv *env, jobject ref);</h3>
     * <p>
     * Creates a new local reference that refers to the same object as ref. The given ref may be a
     * global or local reference. Returns NULL if ref refers to null.
     */
    @JniImpl
    public static @JavaType(Object.class) StaticObject NewLocalRef(@JavaType(Object.class) StaticObject ref) {
        // Local ref is allocated on return.
        return ref;
    }

    /**
     * <h3>jobject NewGlobalRef(JNIEnv *env, jobject obj);</h3>
     * <p>
     * Creates a new global reference to the object referred to by the obj argument. The
     * <b>handle</b> argument may be a global or local reference. Global references must be
     * explicitly disposed of by calling DeleteGlobalRef().
     *
     * @param handle a global or local reference.
     * @return a global reference, or NULL if the system runs out of memory.
     */
    @JniImpl
    public @Handle(StaticObject.class) long NewGlobalRef(@Handle(StaticObject.class) long handle) {
        return getHandles().createGlobal(getHandles().get(JNIHandles.toIntHandle(handle)));
    }

    /**
     * <h3>void DeleteGlobalRef(JNIEnv *env, jobject globalRef);</h3>
     * <p>
     * Deletes the global reference pointed to by globalRef.
     *
     * @param handle a global reference.
     */
    @JniImpl
    public void DeleteGlobalRef(@Handle(StaticObject.class) long handle) {
        getHandles().deleteGlobalRef(JNIHandles.toIntHandle(handle));
    }

    /**
     * <h3>void DeleteLocalRef(JNIEnv *env, jobject localRef);</h3>
     * <p>
     * Deletes the local reference pointed to by localRef.
     *
     * <p>
     * <b>Note:</b> JDK/JRE 1.1 provides the DeleteLocalRef function above so that programmers can
     * manually delete local references. For example, if native code iterates through a potentially
     * large array of objects and uses one element in each iteration, it is a good practice to
     * delete the local reference to the no-longer-used array element before a new local reference
     * is created in the next iteration.
     * <p>
     * As of JDK/JRE 1.2 an additional set of functions are provided for local reference lifetime
     * management. They are the four functions listed below.
     *
     * @param handle a local reference.
     */
    @JniImpl
    public void DeleteLocalRef(@Handle(StaticObject.class) long handle) {
        getHandles().deleteLocalRef(JNIHandles.toIntHandle(handle));
    }

    /**
     * <h3>jweak NewWeakGlobalRef(JNIEnv *env, jobject obj);</h3>
     * <p>
     * Creates a new weak global reference. Returns NULL if obj refers to null, or if the VM runs
     * out of memory. If the VM runs out of memory, an OutOfMemoryError will be thrown.
     */
    @JniImpl
    public @Handle(StaticObject.class) long NewWeakGlobalRef(@Handle(StaticObject.class) long handle) {
        return getHandles().createWeakGlobal(getHandles().get(JNIHandles.toIntHandle(handle)));
    }

    /**
     * <h3>void DeleteWeakGlobalRef(JNIEnv *env, jweak obj);</h3>
     * <p>
     * Delete the VM resources needed for the given weak global reference.
     */
    @JniImpl
    public void DeleteWeakGlobalRef(@Handle(StaticObject.class) long handle) {
        getHandles().deleteGlobalRef(JNIHandles.toIntHandle(handle));
    }

    /**
     * <h3>jint PushLocalFrame(JNIEnv *env, jint capacity);</h3>
     * <p>
     * Creates a new local reference frame, in which at least a given number of local references can
     * be created. Returns 0 on success, a negative number and a pending OutOfMemoryError on
     * failure.
     * <p>
     * Note that local references already created in previous local frames are still valid in the
     * current local frame.
     */
    @JniImpl
    public int PushLocalFrame(int capacity) {
        getHandles().pushFrame(capacity);
        return JNI_OK;
    }

    /**
     * <h3>jobject PopLocalFrame(JNIEnv *env, jobject result);</h3>
     * <p>
     * Pops off the current local reference frame, frees all the local references, and returns a
     * local reference in the previous local reference frame for the given result object.
     * <p>
     * Pass NULL as result if you do not need to return a reference to the previous frame.
     */
    @JniImpl
    public @JavaType(Object.class) StaticObject PopLocalFrame(@JavaType(Object.class) StaticObject object) {
        getHandles().popFrame();
        return object;
    }

    /**
     * <h3>jboolean IsSameObject(JNIEnv *env, jobject ref1, jobject ref2);</h3>
     * <p>
     * Tests whether two references refer to the same Java object.
     *
     * @param ref1 a Java object.
     * @param ref2 a Java object.
     * @return JNI_TRUE if ref1 and ref2 refer to the same Java object, or are both NULL; otherwise,
     *         returns JNI_FALSE.
     */
    @JniImpl
    @NoSafepoint
    public static boolean IsSameObject(@JavaType(Object.class) StaticObject ref1, @JavaType(Object.class) StaticObject ref2) {
        return ref1 == ref2;
    }

    /**
     * <h3>jobjectRefType GetObjectRefType(JNIEnv* env, jobject obj);</h3>
     * <p>
     * Returns the type of the object referred to by the obj argument. The argument obj can either
     * be a local, global or weak global reference.
     *
     * <ul>
     * <li>If the argument obj is a weak global reference type, the return will be
     * {@link #JNIWeakGlobalRefType}.
     *
     * <li>If the argument obj is a global reference type, the return value will be
     * {@link #JNIGlobalRefType}.
     *
     * <li>If the argument obj is a local reference type, the return will be
     * {@link #JNILocalRefType}.
     *
     * <li>If the obj argument is not a valid reference, the return value for this function will be
     * {@link #JNIInvalidRefType}.
     * </ul>
     * <p>
     * <p>
     * An invalid reference is a reference which is not a valid handle. That is, the obj pointer
     * address does not point to a location in memory which has been allocated from one of the Ref
     * creation functions or returned from a JNI function.
     * <p>
     * As such, NULL would be an invalid reference and GetObjectRefType(env,NULL) would return
     * JNIInvalidRefType.
     * <p>
     * On the other hand, a null reference, which is a reference that points to a null, would return
     * the type of reference that the null reference was originally created as.
     * <p>
     * GetObjectRefType cannot be used on deleted references.
     * <p>
     * Since references are typically implemented as pointers to memory data structures that can
     * potentially be reused by any of the reference allocation services in the VM, once deleted, it
     * is not specified what value the GetObjectRefType will return.
     *
     * @param handle a local, global or weak global reference.
     * @return one of the following enumerated values defined as a <b>jobjectRefType</b>:
     *         <ul>
     *         <li>{@link #JNIInvalidRefType} = 0
     *         <li>{@link #JNILocalRefType} = 1
     *         <li>{@link #JNIGlobalRefType} = 2
     *         <li>{@link #JNIWeakGlobalRefType} = 3
     *         </ul>
     */
    @JniImpl
    public /* C enum */ int GetObjectRefType(@Handle(StaticObject.class) long handle) {
        return getHandles().getObjectRefType(JNIHandles.toIntHandle(handle));
    }

    /**
     * <h3>jint EnsureLocalCapacity(JNIEnv *env, jint capacity);</h3>
     * <p>
     * Ensures that at least a given number of local references can be created in the current
     * thread. Returns 0 on success; otherwise returns a negative number and throws an
     * OutOfMemoryError.
     * <p>
     * Before it enters a native method, the VM automatically ensures that at least 16 local
     * references can be created.
     * <p>
     * For backward compatibility, the VM allocates local references beyond the ensured capacity.
     * (As a debugging support, the VM may give the user warnings that too many local references are
     * being created. In the JDK, the programmer can supply the -verbose:jni command line option to
     * turn on these messages.) The VM calls FatalError if no more local references can be created
     * beyond the ensured capacity.
     */
    @JniImpl
    @NoSafepoint
    public static int EnsureLocalCapacity(int capacity) {
        if (capacity >= 0 &&
                        ((MAX_JNI_LOCAL_CAPACITY <= 0) || (capacity <= MAX_JNI_LOCAL_CAPACITY))) {
            return JNI_OK;
        } else {
            return JNI_ERR;
        }
    }

    // endregion JNI handles

    /**
     * <h3>jint GetVersion(JNIEnv *env);</h3>
     * <p>
     * Returns the version of the native method interface.
     *
     * @return the major version number in the higher 16 bits and the minor version number in the
     *         lower 16 bits.
     *
     *         <p>
     *         codes</b>
     *         <ul>
     *         <li>#define JNI_EDETACHED (-2) // thread detached from the VM
     *         <li>#define JNI_EVERSION (-3) // JNI version error
     *         </ul>
     */
    @JniImpl
    @NoSafepoint
    public int GetVersion() {
        if (getJavaVersion().java8OrEarlier()) {
            return JniVersion.JNI_VERSION_ESPRESSO_8.version();
        } else {
            return JniVersion.JNI_VERSION_ESPRESSO_11.version();
        }
    }

    /**
     * <h3>jsize GetArrayLength(JNIEnv *env, jarray array);</h3>
     * <p>
     * Returns the number of elements in the array.
     *
     * @param array a Java array object.
     * @return the length of the array.
     */
    @JniImpl
    public static int GetArrayLength(@JavaType(Object.class) StaticObject array, @Inject EspressoLanguage language) {
        return InterpreterToVM.arrayLength(array, language);
    }

    @JniImpl
    public @Pointer TruffleObject GetPrimitiveArrayCritical(@JavaType(Object.class) StaticObject object, @Pointer TruffleObject isCopyPtr, @Inject EspressoLanguage language) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = NativeUtils.directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        StaticObject array = object;
        StaticObject clazz = GetObjectClass(array);
        JavaKind componentKind = ((ArrayKlass) clazz.getMirrorKlass(getMeta())).getComponentType().getJavaKind();
        assert componentKind.isPrimitive();
        int length = GetArrayLength(array, language);

        ByteBuffer region = allocateDirect(length, componentKind);
        @Pointer
        TruffleObject addressPtr = NativeUtils.byteBufferPointer(region);
        // @formatter:off
        switch (componentKind) {
            case Boolean : GetBooleanArrayRegion(array, 0, length, addressPtr, language); break;
            case Byte    : GetByteArrayRegion(array, 0, length, addressPtr, language);    break;
            case Short   : GetShortArrayRegion(array, 0, length, addressPtr, language);   break;
            case Char    : GetCharArrayRegion(array, 0, length, addressPtr, language);    break;
            case Int     : GetIntArrayRegion(array, 0, length, addressPtr, language);     break;
            case Float   : GetFloatArrayRegion(array, 0, length, addressPtr, language);   break;
            case Long    : GetLongArrayRegion(array, 0, length, addressPtr, language);    break;
            case Double  : GetDoubleArrayRegion(array, 0, length, addressPtr, language);  break;
            case Object  : // fall through
            case Void    : // fall through
            case Illegal : // fall through
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on

        return addressPtr;
    }

    @JniImpl
    public void ReleasePrimitiveArrayCritical(@JavaType(Object.class) StaticObject object, @Pointer TruffleObject carrayPtr, int mode, @Inject EspressoLanguage language) {
        if (mode == 0 || mode == JNI_COMMIT) { // Update array contents.
            StaticObject array = object;
            StaticObject clazz = GetObjectClass(array);
            JavaKind componentKind = ((ArrayKlass) clazz.getMirrorKlass(getMeta())).getComponentType().getJavaKind();
            assert componentKind.isPrimitive();
            int length = GetArrayLength(array, language);
            // @formatter:off
            switch (componentKind) {
                case Boolean : SetBooleanArrayRegion(array, 0, length, carrayPtr, language); break;
                case Byte    : SetByteArrayRegion(array, 0, length, carrayPtr, language);    break;
                case Short   : SetShortArrayRegion(array, 0, length, carrayPtr, language);   break;
                case Char    : SetCharArrayRegion(array, 0, length, carrayPtr, language);    break;
                case Int     : SetIntArrayRegion(array, 0, length, carrayPtr, language);     break;
                case Float   : SetFloatArrayRegion(array, 0, length, carrayPtr, language);   break;
                case Long    : SetLongArrayRegion(array, 0, length, carrayPtr, language);    break;
                case Double  : SetDoubleArrayRegion(array, 0, length, carrayPtr, language);  break;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
        }
        if (mode == 0 || mode == JNI_ABORT) { // Dispose copy.
            releasePtr(carrayPtr);
        }
    }

    /**
     * <h3>jclass GetObjectClass(JNIEnv *env, jobject obj);</h3>
     * <p>
     * Returns the class of an object.
     *
     * @param self a Java object (must not be NULL).
     */
    @JniImpl
    public static @JavaType(Class.class) StaticObject GetObjectClass(@JavaType(Object.class) StaticObject self) {
        return self.getKlass().mirror();
    }

    /**
     * <h3>jclass GetSuperclass(JNIEnv *env, jclass clazz);</h3>
     * <p>
     * If clazz represents any class other than the class Object, then this function returns the
     * object that represents the superclass of the class specified by clazz. If clazz specifies the
     * class Object, or clazz represents an interface, this function returns NULL.
     *
     * @param clazz a Java class object. Returns the superclass of the class represented by clazz,
     *            or NULL.
     */
    @JniImpl
    public @JavaType(Class.class) StaticObject GetSuperclass(@JavaType(Class.class) StaticObject clazz) {
        Klass klass = clazz.getMirrorKlass(getMeta());
        if (klass.isInterface() || klass.getSuperClass() == null) {
            /* also handles primitive classes */
            return StaticObject.NULL;
        }
        return klass.getSuperKlass().mirror();
    }

    @JniImpl
    public @JavaType(Object.class) StaticObject NewObjectVarargs(@JavaType(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isConstructor();
        Klass klass = clazz.getMirrorKlass(getMeta());
        if (klass.isInterface() || klass.isAbstract()) {
            Meta meta = getMeta();
            throw meta.throwException(meta.java_lang_InstantiationException);
        }
        klass.initialize();
        StaticObject instance;
        instance = klass.allocateInstance(getContext());
        method.invokeDirect(instance, popVarArgs(varargsPtr, method.getParsedSignature()));
        return instance;
    }

    /**
     * <h3>jclass FindClass(JNIEnv *env, const char *name);</h3>
     *
     * <p>
     * FindClass locates the class loader associated with the current native method; that is, the
     * class loader of the class that declared the native method. If the native method belongs to a
     * system class, no class loader will be involved. Otherwise, the proper class loader will be
     * invoked to load and link the named class. Since Java 2 SDK release 1.2, when FindClass is
     * called through the Invocation Interface, there is no current native method or its associated
     * class loader. In that case, the result of {@link ClassLoader#getSystemClassLoader} is used.
     * This is the class loader the virtual machine creates for applications, and is able to locate
     * classes listed in the java.class.path property. The name argument is a fully-qualified class
     * name or an array type signature .
     * <p>
     * For example, the fully-qualified class name for the {@code java.lang.String} class is:
     *
     * <pre>
     * "java/lang/String"}
     * </pre>
     *
     * <p>
     * The array type signature of the array class {@code java.lang.Object[]} is:
     *
     * <pre>
     * "[Ljava/lang/Object;"
     * </pre>
     *
     * @param namePtr a fully-qualified class name (that is, a package name, delimited by "/",
     *            followed by the class name). If the name begins with "[" (the array signature
     *            character), it returns an array class. The string is encoded in modified UTF-8.
     * @return Returns a class object from a fully-qualified name, or NULL if the class cannot be
     *         found.
     * @throws ClassFormatError if the class data does not specify a valid class.
     * @throws ClassCircularityError if a class or interface would be its own superclass or
     *             superinterface.
     * @throws NoClassDefFoundError if no definition for a requested class or interface can be
     *             found.
     * @throws OutOfMemoryError if the system runs out of memory.
     */
    @TruffleBoundary
    @JniImpl
    public @JavaType(Class.class) StaticObject FindClass(@Pointer TruffleObject namePtr, @Inject SubstitutionProfiler profiler) {
        String name = NativeUtils.interopPointerToString(namePtr);
        Meta meta = getMeta();
        if (name == null || (name.indexOf('.') > -1)) {
            profiler.profile(7);
            throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, name);
        }

        String internalName = name;
        if (!name.startsWith("[")) {
            // Force 'L' type.
            internalName = "L" + name + ";";
        }
        if (!Validation.validTypeDescriptor(ByteSequence.create(internalName), true)) {
            profiler.profile(6);
            throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, name);
        }

        StaticObject protectionDomain = StaticObject.NULL;
        StaticObject loader = StaticObject.NULL;

        StaticObject caller = getVM().JVM_GetCallerClass(0, profiler); // security stack walk
        if (StaticObject.notNull(caller)) {
            Klass callerKlass = caller.getMirrorKlass(getMeta());
            loader = callerKlass.getDefiningClassLoader();
            if (StaticObject.isNull(loader) && Type.java_lang_ClassLoader$NativeLibrary.equals(callerKlass.getType())) {
                StaticObject result = (StaticObject) getMeta().java_lang_ClassLoader$NativeLibrary_getFromClass.invokeDirect(null);
                loader = result.getMirrorKlass(getMeta()).getDefiningClassLoader();
                protectionDomain = getVM().JVM_GetProtectionDomain(result);
            }
        } else {
            loader = (StaticObject) getMeta().java_lang_ClassLoader_getSystemClassLoader.invokeDirect(null);
        }

        StaticObject guestClass = StaticObject.NULL;
        try {
            String dotName = name.replace('/', '.');
            guestClass = (StaticObject) getMeta().java_lang_Class_forName_String_boolean_ClassLoader.invokeDirect(null, meta.toGuestString(dotName), false, loader);
            EspressoError.guarantee(StaticObject.notNull(guestClass), "Class.forName returned null", dotName);
        } catch (EspressoException e) {
            profiler.profile(5);
            if (InterpreterToVM.instanceOf(e.getGuestException(), meta.java_lang_ClassNotFoundException)) {
                profiler.profile(4);
                throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, name);
            }
            throw e;
        }

        meta.HIDDEN_PROTECTION_DOMAIN.setHiddenObject(guestClass, protectionDomain);
        // FindClass should initialize the class.
        guestClass.getMirrorKlass(getMeta()).safeInitialize();

        return guestClass;
    }

    /**
     * Loads a class from a buffer of raw class data. The buffer containing the raw class data is
     * not referenced by the VM after the DefineClass call returns, and it may be discarded if
     * desired.
     *
     * @param namePtr the name of the class or interface to be defined. The string is encoded in
     *            modified UTF-8.
     * @param loader a class loader assigned to the defined class.
     * @param bufPtr buffer containing the .class file data.
     * @param bufLen buffer length.
     * @return Returns a Java class object or NULL if an error occurs.
     */
    @JniImpl
    public @JavaType(Class.class) StaticObject DefineClass(@Pointer TruffleObject namePtr,
                    @JavaType(ClassLoader.class) StaticObject loader,
                    @Pointer TruffleObject bufPtr, int bufLen) {
        // TODO(peterssen): Propagate errors and verifications, e.g. no class in the java package.
        return getVM().JVM_DefineClass(namePtr, loader, bufPtr, bufLen, StaticObject.NULL);
    }

    // JavaVM **vm);

    /**
     * <h3>jobject AllocObject(JNIEnv *env, jclass clazz);</h3>
     * <p>
     * Allocates a new Java object without invoking any of the constructors for the object. Returns
     * a reference to the object.
     * <p>
     * The clazz argument must not refer to an array class.
     *
     * @param clazz a Java class object.
     *            <p>
     *            Returns a Java object, or NULL if the object cannot be constructed.
     *            <p>
     *            Throws InstantiationException if the class is an interface or an abstract class.
     * @throws OutOfMemoryError if the system runs out of memory.
     */
    @JniImpl
    public @JavaType(Object.class) StaticObject AllocObject(@JavaType(Class.class) StaticObject clazz, @Inject Meta meta) {
        if (StaticObject.isNull(clazz)) {
            throw meta.throwException(getMeta().java_lang_InstantiationException);
        }
        Klass klass = clazz.getMirrorKlass(getMeta());
        return klass.allocateInstance(getContext());
    }

    /**
     * <h3>jboolean IsAssignableFrom(JNIEnv *env, jclass clazz1, jclass clazz2);</h3>
     * <p>
     * Determines whether an object of clazz1 can be safely cast to clazz2.
     *
     * @param clazz1 the first class argument.
     * @param clazz2 the second class argument.
     * @return Returns JNI_TRUE if either of the following is true:
     *         <ul>
     *         <li>The first and second class arguments refer to the same Java class.
     *         <li>The first class is a subclass of the second class.
     *         <li>The first class has the second class as one of its interfaces.
     *         </ul>
     */
    @JniImpl
    public boolean IsAssignableFrom(@JavaType(Class.class) StaticObject clazz1, @JavaType(Class.class) StaticObject clazz2) {
        Klass klass2 = clazz2.getMirrorKlass(getMeta());
        return klass2.isAssignableFrom(clazz1.getMirrorKlass(getMeta()));
    }

    /**
     * <h3>jboolean IsInstanceOf(JNIEnv *env, jobject obj, jclass clazz);</h3>
     * <p>
     * Tests whether an object is an instance of a class.
     *
     * @param obj a Java object.
     * @param clazz a Java class object.
     * @return Returns {@code JNI_TRUE} if obj can be cast to clazz; otherwise, returns
     *         {@code JNI_FALSE}. <b>A NULL object can be cast to any class.</b>
     */
    @JniImpl
    public boolean IsInstanceOf(@JavaType(Object.class) StaticObject obj, @JavaType(Class.class) StaticObject clazz) {
        if (StaticObject.isNull(obj)) {
            return true;
        }
        return InterpreterToVM.instanceOf(obj, clazz.getMirrorKlass(getMeta()));
    }

    /**
     * <h3>jobject GetModule(JNIEnv* env, jclass clazz);</h3>
     * <p>
     * Obtains the module object associated with a given class.
     *
     * @param clazz a Java class object.
     * @return the module object associated with the given class
     */
    @JniImpl
    public @JavaType(internalName = "Ljava/lang/Module;") StaticObject GetModule(@JavaType(Class.class) StaticObject clazz) {
        Meta meta = getMeta();
        if (StaticObject.isNull(clazz)) {
            throw meta.throwNullPointerException();
        }
        if (!meta.java_lang_Class.isAssignableFrom(clazz.getKlass())) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Invalid Class");
        }
        return clazz.getMirrorKlass(getMeta()).module().module();
    }

    // Checkstyle: resume method name check
}
