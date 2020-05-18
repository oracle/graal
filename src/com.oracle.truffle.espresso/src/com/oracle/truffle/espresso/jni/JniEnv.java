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
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.Utils;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Validation;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.NativeMethodNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoProperties;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.GuestCall;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.substitutions.InjectProfile;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.substitutions.Substitutions;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Class;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;

public final class JniEnv extends NativeEnv implements ContextAccess {

    private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, JniEnv.class);
    private final InteropLibrary uncached = InteropLibrary.getFactory().getUncached();

    protected InteropLibrary getUncached() {
        return uncached;
    }

    protected TruffleLogger getLogger() {
        return logger;
    }

    public static final int JNI_OK = 0; /* success */
    public static final int JNI_ERR = -1; /* unknown error */
    public static final int JNI_COMMIT = 1;
    public static final int JNI_ABORT = 2;

    public static final int JVM_INTERFACE_VERSION = 4;
    public static final int JNI_TRUE = 1;
    public static final int JNI_FALSE = 0;

    // enum jobjectRefType
    public static final int JNIInvalidRefType = 0;
    public static final int JNILocalRefType = 1;
    public static final int JNIGlobalRefType = 2;
    public static final int JNIWeakGlobalRefType = 3;

    // TODO(peterssen): Add user-configurable option.
    private static final int MAX_JNI_LOCAL_CAPACITY = 1 << 16;

    private final EspressoContext context;

    private final JNIHandles handles;

    private @Pointer TruffleObject jniEnvPtr;

    // Native library nespresso.dll (Windows) or libnespresso.so (Unixes) at runtime.
    private final TruffleObject nespressoLibrary;

    // Native methods in libenespresso.
    private final @Pointer TruffleObject initializeNativeContext;
    private final @Pointer TruffleObject disposeNativeContext;
    private final @Pointer TruffleObject dupClosureRef;
    private final @Pointer TruffleObject popBoolean;
    private final @Pointer TruffleObject popByte;
    private final @Pointer TruffleObject popChar;
    private final @Pointer TruffleObject popShort;
    private final @Pointer TruffleObject popInt;
    private final @Pointer TruffleObject popFloat;
    private final @Pointer TruffleObject popDouble;
    private final @Pointer TruffleObject popLong;
    private final @Pointer TruffleObject popObject;

    private static final Map<String, JniSubstitutor.Factory> jniMethods = buildJniMethods();

    private final WeakHandles<Field> fieldIds = new WeakHandles<>();
    private final WeakHandles<Method> methodIds = new WeakHandles<>();

    Method getMethod(long handle) {
        return methodIds.getObject(handle);
    }

    // Prevent cleaner threads from collecting in-use native buffers.
    private final Map<Long, ByteBuffer> nativeBuffers = new ConcurrentHashMap<>();

    private final JniThreadLocalPendingException threadLocalPendingException = new JniThreadLocalPendingException();

    public JniThreadLocalPendingException getThreadLocalPendingException() {
        return threadLocalPendingException;
    }

    @TruffleBoundary
    public StaticObject getPendingException() {
        return threadLocalPendingException.get();
    }

    @TruffleBoundary
    public void clearPendingException() {
        threadLocalPendingException.clear();
    }

    @TruffleBoundary
    public void setPendingException(StaticObject ex) {
        assert StaticObject.notNull(ex) && getMeta().java_lang_Throwable.isAssignableFrom(ex.getKlass());
        threadLocalPendingException.set(ex);
    }

    public Callback jniMethodWrapper(JniSubstitutor.Factory factory) {
        return new Callback(factory.getParameterCount() + 1, new Callback.Function() {
            @CompilerDirectives.CompilationFinal private JniSubstitutor subst = null;

            @Override
            public Object call(Object... args) {
                assert interopAsPointer((TruffleObject) args[0]) == interopAsPointer(JniEnv.this.getNativePointer()) : "Calling " + factory + " from alien JniEnv";
                try {
                    if (subst == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        subst = factory.create(getMeta());
                    }
                    return subst.invoke(JniEnv.this, args);
                } catch (EspressoException | StackOverflowError | OutOfMemoryError e) {
                    // This will most likely SOE again. Nothing we can do about that
                    // unfortunately.
                    EspressoException wrappedError = (e instanceof EspressoException)
                                    ? (EspressoException) e
                                    : (e instanceof StackOverflowError)
                                                    ? getContext().getStackOverflow()
                                                    : getContext().getOutOfMemory();
                    setPendingException(wrappedError.getExceptionObject());
                    return defaultValue(factory.returnType());
                }
            }
        });
    }

    private static final int LOOKUP_JNI_IMPL_PARAMETER_COUNT = 1;

    @TruffleBoundary
    public TruffleObject lookupJniImpl(String methodName) {
        JniSubstitutor.Factory m = jniMethods.get(methodName);
        try {
            // Dummy placeholder for unimplemented/unknown methods.
            if (m == null) {
                getLogger().log(Level.FINER, "Fetching unknown/unimplemented JNI method: {0}", methodName);
                return (TruffleObject) InteropLibrary.getFactory().getUncached().execute(dupClosureRefAndCast("(pointer): void"),
                                new Callback(1, new Callback.Function() {
                                    @Override
                                    public Object call(Object... args) {
                                        CompilerDirectives.transferToInterpreter();
                                        getLogger().log(Level.SEVERE, "Calling unimplemented JNI method: {0}", methodName);
                                        throw EspressoError.unimplemented("JNI method: " + methodName);
                                    }
                                }));
            }

            String signature = m.jniNativeSignature();
            Callback target = jniMethodWrapper(m);
            return (TruffleObject) InteropLibrary.getFactory().getUncached().execute(dupClosureRefAndCast(signature), target);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static boolean containsMethod(String methodName) {
        return jniMethods.containsKey(methodName);
    }

    private class VarArgsImpl implements VarArgs {

        private final @Pointer TruffleObject nativePointer;

        VarArgsImpl(@Pointer TruffleObject nativePointer) {
            this.nativePointer = nativePointer;
        }

        @Override
        public boolean popBoolean() {
            try {
                return ((byte) InteropLibrary.getFactory().getUncached().execute(popBoolean, nativePointer)) != 0;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public byte popByte() {
            try {
                return (byte) InteropLibrary.getFactory().getUncached().execute(popByte, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public char popChar() {
            try {
                return (char) (short) InteropLibrary.getFactory().getUncached().execute(popChar, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public short popShort() {
            try {
                return (short) InteropLibrary.getFactory().getUncached().execute(popShort, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public int popInt() {
            try {
                return (int) InteropLibrary.getFactory().getUncached().execute(popInt, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public float popFloat() {
            try {
                return (float) InteropLibrary.getFactory().getUncached().execute(popFloat, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public double popDouble() {
            try {
                return (Double) InteropLibrary.getFactory().getUncached().execute(popDouble, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public long popLong() {
            try {
                return (long) InteropLibrary.getFactory().getUncached().execute(popLong, nativePointer);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public Object popObject() {
            try {
                @Handle(StaticObject.class)
                long handle = (long) InteropLibrary.getFactory().getUncached().execute(popObject, nativePointer);
                TruffleObject result = getHandles().get(Math.toIntExact(handle));
                if (result instanceof StaticObject) {
                    return result;
                } else {
                    if (InteropLibrary.getFactory().getUncached().isNull(result)) {
                        // TODO(garcia) understand the weird stuff happening here.
                        // DaCapo batik gives us a NativePointer to 0 here. This is a workaround
                        // until I
                        // figure out just what is happening here.
                        return StaticObject.NULL;
                    } else {
                        throw EspressoError.unimplemented("non null native pointer in JniEnv");
                    }
                }
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            } catch (ClassCastException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    public Object[] popVarArgs(@Pointer TruffleObject varargsPtr, final Symbol<Type>[] signature) {
        VarArgs varargs = new VarArgsImpl(varargsPtr);
        int paramCount = Signatures.parameterCount(signature, false);
        Object[] args = new Object[paramCount];
        for (int i = 0; i < paramCount; ++i) {
            JavaKind kind = Signatures.parameterKind(signature, i);
            // @formatter:off
            switch (kind) {
                case Boolean : args[i] = varargs.popBoolean();   break;
                case Byte    : args[i] = varargs.popByte();      break;
                case Short   : args[i] = varargs.popShort();     break;
                case Char    : args[i] = varargs.popChar();      break;
                case Int     : args[i] = varargs.popInt();       break;
                case Float   : args[i] = varargs.popFloat();     break;
                case Long    : args[i] = varargs.popLong();      break;
                case Double  : args[i] = varargs.popDouble();    break;
                case Object  : args[i] = varargs.popObject();    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere("invalid parameter kind: " + kind);
            }
            // @formatter:on
        }
        return args;
    }

    public Object[] popVarArgsWithReceiver(long varargsPtr, StaticObject receiver, final Symbol<Type>[] signature) {
        VarArgs varargs = new VarArgsImpl(varargsPtr);
        int paramCount = Signatures.parameterCount(signature, false) + 1;
        Object[] args = new Object[paramCount];
        args[0] = receiver;
        for (int i = 1; i < paramCount; ++i) {
            JavaKind kind = Signatures.parameterKind(signature, i);
            // @formatter:off
            // Checkstyle: stop
            switch (kind) {
                case Boolean : args[i] = varargs.popBoolean();   break;
                case Byte    : args[i] = varargs.popByte();      break;
                case Short   : args[i] = varargs.popShort();     break;
                case Char    : args[i] = varargs.popChar();      break;
                case Int     : args[i] = varargs.popInt();       break;
                case Float   : args[i] = varargs.popFloat();     break;
                case Long    : args[i] = varargs.popLong();      break;
                case Double  : args[i] = varargs.popDouble();    break;
                case Object  : args[i] = varargs.popObject();    break;
                default:
                    throw EspressoError.shouldNotReachHere("invalid parameter kind: " + kind);
            }
            // @formatter:on
            // Checkstyle: resume
        }
        return args;
    }

    private JniEnv(EspressoContext context) {
        EspressoProperties props = context.getVmProperties();
        this.context = context;
        try {
            nespressoLibrary = loadLibraryInternal(Collections.singletonList(props.espressoLibraryPath()), "nespresso");
            dupClosureRef = NativeLibrary.lookup(nespressoLibrary, "dupClosureRef");
            initializeNativeContext = NativeLibrary.lookupAndBind(nespressoLibrary,
                            "initializeNativeContext", "(env, (pointer): pointer): pointer");
            disposeNativeContext = NativeLibrary.lookupAndBind(nespressoLibrary, "disposeNativeContext",
                            "(env, pointer): void");

            // Varargs native bindings.
            popBoolean = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_boolean", "(pointer): sint8");
            popByte = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_byte", "(pointer): sint8");
            popChar = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_char", "(pointer): sint16");
            popShort = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_short", "(pointer): sint16");
            popInt = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_int", "(pointer): sint32");
            popFloat = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_float", "(pointer): float");
            popDouble = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_double", "(pointer): double");
            popLong = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_long", "(pointer): sint64");
            popObject = NativeLibrary.lookupAndBind(nespressoLibrary, "pop_object", "(pointer): sint64");

            Callback lookupJniImplCallback = new Callback(LOOKUP_JNI_IMPL_PARAMETER_COUNT, new Callback.Function() {
                @Override
                public Object call(Object... args) {
                    try {
                        String name = interopPointerToString((TruffleObject) args[0]);
                        return JniEnv.this.lookupJniImpl(name);
                    } catch (ClassCastException e) {
                        throw EspressoError.shouldNotReachHere(e);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw EspressoError.shouldNotReachHere(e);
                    }
                }
            });
            this.jniEnvPtr = (TruffleObject) getUncached().execute(initializeNativeContext, lookupJniImplCallback);
            assert getUncached().isPointer(jniEnvPtr);

            this.handles = new JNIHandles();

            assert jniEnvPtr != null && !getUncached().isNull(jniEnvPtr);
        } catch (UnsupportedMessageException | ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
            throw EspressoError.shouldNotReachHere("Cannot initialize Espresso native interface");
        }
    }

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
        long address = byteBufferAddress(bb);
        nativeBuffers.put(address, bb);
        return bb;
    }

    private static String nfiSignature(final Symbol<Type>[] signature, boolean isJni) {

        int argCount = Signatures.parameterCount(signature, false);
        StringBuilder sb = new StringBuilder("(");

        boolean first = true;
        if (isJni) {
            sb.append(NativeSimpleType.POINTER); // JNIEnv*
            sb.append(",");
            sb.append(Utils.kindToType(JavaKind.Object)); // Receiver or class (for static
            // methods).
            first = false;
        }
        for (int i = 0; i < argCount; ++i) {
            JavaKind kind = Signatures.parameterKind(signature, i);
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }
            sb.append(Utils.kindToType(kind));
        }

        sb.append("): ").append(Utils.kindToType(Signatures.returnKind(signature)));
        return sb.toString();
    }

    private static Map<String, JniSubstitutor.Factory> buildJniMethods() {
        Map<String, JniSubstitutor.Factory> map = new HashMap<>();
        for (JniSubstitutor.Factory method : JniCollector.getCollector()) {
            assert !map.containsKey(method.methodName()) : "JniImpl for " + method.methodName() + " already exists";
            map.put(method.methodName(), method);
        }
        return Collections.unmodifiableMap(map);
    }

    public TruffleObject dupClosureRefAndCast(String signature) {
        // TODO(peterssen): Cache binding per signature.
        return NativeLibrary.bind(dupClosureRef, "(env, " + signature + ")" + ": pointer");
    }

    public static JniEnv create(EspressoContext context) {
        return new JniEnv(context);
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    public @Pointer TruffleObject getNativePointer() {
        return jniEnvPtr;
    }

    public void dispose() {
        assert jniEnvPtr != null : "JNIEnv already disposed";
        try {
            InteropLibrary.getFactory().getUncached().execute(disposeNativeContext, jniEnvPtr);
            threadLocalPendingException.dispose();
            this.jniEnvPtr = null;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot initialize Espresso native interface");
        }
        assert jniEnvPtr == null;
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
    public @Handle(Field.class) long GetFieldID(@Host(Class.class) StaticObject clazz, @Pointer TruffleObject namePtr, @Pointer TruffleObject typePtr) {
        String name = interopPointerToString(namePtr);
        String type = interopPointerToString(typePtr);
        assert name != null && type != null;
        Klass klass = clazz.getMirrorKlass();

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
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_NoSuchFieldError, name);
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
    public @Handle(Field.class) long GetStaticFieldID(@Host(Class.class) StaticObject clazz, @Pointer TruffleObject namePtr, @Pointer TruffleObject typePtr) {
        String name = interopPointerToString(namePtr);
        String type = interopPointerToString(typePtr);
        assert name != null && type != null;
        Field field = null;
        Symbol<Name> fieldName = getNames().lookup(name);
        if (fieldName != null) {
            Symbol<Type> fieldType = getTypes().lookup(type);
            if (fieldType != null) {
                Klass klass = clazz.getMirrorKlass();
                klass.safeInitialize();
                // Lookup only if name and type are known symbols.
                field = klass.lookupField(fieldName, fieldType, true);
                assert field == null || field.getType().equals(fieldType);
            }
        }
        if (field == null || !field.isStatic()) {
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_NoSuchFieldError, name);
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
    public @Handle(Method.class) long GetMethodID(@Host(Class.class) StaticObject clazz, @Pointer TruffleObject namePtr, @Pointer TruffleObject signaturePtr) {
        String name = interopPointerToString(namePtr);
        String signature = interopPointerToString(signaturePtr);
        assert name != null && signature != null;
        Method method = null;
        Symbol<Name> methodName = getNames().lookup(name);
        if (methodName != null) {
            Symbol<Signature> methodSignature = getSignatures().lookupValidSignature(signature);
            if (methodSignature != null) {
                Klass klass = clazz.getMirrorKlass();
                klass.safeInitialize();
                // Lookup only if name and type are known symbols.
                method = klass.lookupMethod(methodName, methodSignature, klass);
            }
        }
        if (method == null || method.isStatic()) {
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_NoSuchMethodError, name);
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
    public @Handle(Method.class) long GetStaticMethodID(@Host(Class.class) StaticObject clazz, @Pointer TruffleObject namePtr, @Pointer TruffleObject signaturePtr) {
        String name = interopPointerToString(namePtr);
        String signature = interopPointerToString(signaturePtr);
        assert name != null && signature != null;
        Method method = null;
        Symbol<Name> methodName = getNames().lookup(name);
        if (methodName != null) {
            Symbol<Signature> methodSignature = getSignatures().lookupValidSignature(signature);
            if (methodSignature != null) {
                // Throw a NoSuchMethodError exception if we have an instance of a
                // primitive java.lang.Class
                Klass klass = clazz.getMirrorKlass();
                if (klass.isPrimitive()) {
                    throw Meta.throwExceptionWithMessage(getMeta().java_lang_NoSuchMethodError, String.format("static %s.%s%s", klass, name, signature));
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
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_NoSuchMethodError, name);
        }
        return methodIds.handlify(method);
    }

    // endregion Get*ID

    // region GetStatic*Field

    @JniImpl
    public @Host(Object.class) StaticObject GetStaticObjectField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return (StaticObject) field.get(field.getDeclaringKlass().tryInitializeAndGetStatics());
    }

    @JniImpl
    public boolean GetStaticBooleanField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return (boolean) field.get(field.getDeclaringKlass().tryInitializeAndGetStatics());
    }

    @JniImpl
    public byte GetStaticByteField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return (byte) field.get(field.getDeclaringKlass().tryInitializeAndGetStatics());
    }

    @JniImpl
    public char GetStaticCharField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return (char) field.get(field.getDeclaringKlass().tryInitializeAndGetStatics());
    }

    @JniImpl
    public short GetStaticShortField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return (short) field.get(field.getDeclaringKlass().tryInitializeAndGetStatics());
    }

    @JniImpl
    public int GetStaticIntField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return (int) field.get(field.getDeclaringKlass().tryInitializeAndGetStatics());
    }

    @JniImpl
    public long GetStaticLongField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return (long) field.get(field.getDeclaringKlass().tryInitializeAndGetStatics());
    }

    @JniImpl
    public float GetStaticFloatField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return (float) field.get(field.getDeclaringKlass().tryInitializeAndGetStatics());
    }

    @JniImpl
    public double GetStaticDoubleField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        return (double) field.get(field.getDeclaringKlass().tryInitializeAndGetStatics());
    }

    // endregion GetStatic*Field

    // region Get*Field

    @JniImpl
    public @Host(Object.class) StaticObject GetObjectField(StaticObject obj, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return (StaticObject) field.get(obj);
    }

    @JniImpl
    public boolean GetBooleanField(StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return (boolean) field.get(object);
    }

    @JniImpl
    public byte GetByteField(StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return (byte) field.get(object);
    }

    @JniImpl
    public char GetCharField(StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return (char) field.get(object);
    }

    @JniImpl
    public short GetShortField(StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return (short) field.get(object);
    }

    @JniImpl
    public int GetIntField(StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return (int) field.get(object);
    }

    @JniImpl
    public long GetLongField(StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return (long) field.get(object);
    }

    @JniImpl
    public float GetFloatField(StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return (float) field.get(object);
    }

    @JniImpl
    public double GetDoubleField(StaticObject object, @Handle(Field.class) long fieldId) {
        Field field = fieldIds.getObject(fieldId);
        return (double) field.get(object);
    }

    // endregion Get*Field

    // region SetStatic*Field

    @JniImpl
    public void SetStaticObjectField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, @Host(Object.class) StaticObject val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticBooleanField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, boolean val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticByteField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, byte val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticCharField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, char val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticShortField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, short val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticIntField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, int val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticLongField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, long val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticFloatField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, float val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    @JniImpl
    public void SetStaticDoubleField(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, double val) {
        Field field = fieldIds.getObject(fieldId);
        assert field.isStatic();
        field.set(field.getDeclaringKlass().tryInitializeAndGetStatics(), val);
    }

    // endregion SetStatic*Field

    // region Set*Field

    @JniImpl
    public void SetObjectField(StaticObject obj, @Handle(Field.class) long fieldId, @Host(Object.class) StaticObject val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetBooleanField(StaticObject obj, @Handle(Field.class) long fieldId, boolean val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetByteField(StaticObject obj, @Handle(Field.class) long fieldId, byte val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetCharField(StaticObject obj, @Handle(Field.class) long fieldId, char val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetShortField(StaticObject obj, @Handle(Field.class) long fieldId, short val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetIntField(StaticObject obj, @Handle(Field.class) long fieldId, int val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetLongField(StaticObject obj, @Handle(Field.class) long fieldId, long val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetFloatField(StaticObject obj, @Handle(Field.class) long fieldId, float val) {
        Field field = fieldIds.getObject(fieldId);
        field.set(obj, val);
    }

    @JniImpl
    public void SetDoubleField(StaticObject obj, @Handle(Field.class) long fieldId, double val) {
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
            target = ((ObjectKlass) receiver.getKlass()).itableLookup(resolutionSeed.getDeclaringKlass(), resolutionSeed.getITableIndex());
        } else {
            if (resolutionSeed.isConstructor()) {
                target = resolutionSeed;
            } else {
                target = receiver.getKlass().vtableLookup(resolutionSeed.getVTableIndex());
            }
        }

        assert target != null;
        assert target.getName() == resolutionSeed.getName() && resolutionSeed.getRawSignature() == target.getRawSignature();
        return target.invokeDirect(receiver, args);
    }

    @JniImpl
    public @Host(Object.class) StaticObject CallObjectMethodVarargs(@Host(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        return (StaticObject) callVirtualMethodGeneric(receiver, methodId, varargsPtr);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public boolean CallBooleanMethodVarargs(@Host(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        return (boolean) callVirtualMethodGeneric(receiver, methodId, varargsPtr);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public char CallCharMethodVarargs(@Host(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        return (char) callVirtualMethodGeneric(receiver, methodId, varargsPtr);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public byte CallByteMethodVarargs(@Host(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        return (byte) callVirtualMethodGeneric(receiver, methodId, varargsPtr);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public short CallShortMethodVarargs(@Host(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        return (short) callVirtualMethodGeneric(receiver, methodId, varargsPtr);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public int CallIntMethodVarargs(@Host(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        return (int) callVirtualMethodGeneric(receiver, methodId, varargsPtr);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public float CallFloatMethodVarargs(@Host(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        return (float) callVirtualMethodGeneric(receiver, methodId, varargsPtr);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public double CallDoubleMethodVarargs(@Host(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        return (double) callVirtualMethodGeneric(receiver, methodId, varargsPtr);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public long CallLongMethodVarargs(@Host(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        return (long) callVirtualMethodGeneric(receiver, methodId, varargsPtr);
    }

    @SuppressWarnings("unused")
    @JniImpl
    public void CallVoidMethodVarargs(@Host(Object.class) StaticObject receiver, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        callVirtualMethodGeneric(receiver, methodId, varargsPtr);
    }

    // endregion Call*Method

    // region CallNonvirtual*Method

    @JniImpl
    public @Host(Object.class) StaticObject CallNonvirtualObjectMethodVarargs(@Host(Object.class) StaticObject receiver, @Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (StaticObject) method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public boolean CallNonvirtualBooleanMethodVarargs(@Host(Object.class) StaticObject receiver, @Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (boolean) method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public char CallNonvirtualCharMethodVarargs(@Host(Object.class) StaticObject receiver, @Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (char) method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public byte CallNonvirtualByteMethodVarargs(@Host(Object.class) StaticObject receiver, @Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (byte) method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public short CallNonvirtualShortMethodVarargs(@Host(Object.class) StaticObject receiver, @Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (short) method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public int CallNonvirtualIntMethodVarargs(@Host(Object.class) StaticObject receiver, @Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (int) method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public float CallNonvirtualFloatMethodVarargs(@Host(Object.class) StaticObject receiver, @Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (float) method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public double CallNonvirtualDoubleMethodVarargs(@Host(Object.class) StaticObject receiver, @Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (double) method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public long CallNonvirtualLongMethodVarargs(@Host(Object.class) StaticObject receiver, @Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (long) method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public void CallNonvirtualVoidMethodVarargs(@Host(Object.class) StaticObject receiver, @Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public @Host(Object.class) StaticObject CallNonvirtualtualObjectMethodVarargs(@Host(Object.class) StaticObject receiver, @Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId,
                    @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert !method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (StaticObject) method.invokeDirect(receiver, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    // endregion CallNonvirtual*Method

    // region CallStatic*Method

    @JniImpl
    public @Host(Object.class) StaticObject CallStaticObjectMethodVarargs(@Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (StaticObject) method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public boolean CallStaticBooleanMethodVarargs(@Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (boolean) method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public char CallStaticCharMethodVarargs(@Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (char) method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public byte CallStaticByteMethodVarargs(@Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (byte) method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public short CallStaticShortMethodVarargs(@Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (short) method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public int CallStaticIntMethodVarargs(@Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (int) method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public float CallStaticFloatMethodVarargs(@Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (float) method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public double CallStaticDoubleMethodVarargs(@Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (double) method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public long CallStaticLongMethodVarargs(@Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        return (long) method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    @JniImpl
    public void CallStaticVoidMethodVarargs(@Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isStatic();
        assert (clazz.getMirrorKlass()) == method.getDeclaringKlass();
        method.invokeDirect(null, popVarArgs(varargsPtr, method.getParsedSignature()));
    }

    // endregion CallStatic*Method

    // region New*Array

    @JniImpl
    public static @Host(boolean[].class) StaticObject NewBooleanArray(int len) {
        return InterpreterToVM.allocatePrimitiveArray((byte) JavaKind.Boolean.getBasicType(), len);
    }

    @JniImpl
    public static @Host(byte[].class) StaticObject NewByteArray(int len) {
        return InterpreterToVM.allocatePrimitiveArray((byte) JavaKind.Byte.getBasicType(), len);
    }

    @JniImpl
    public static @Host(char[].class) StaticObject NewCharArray(int len) {
        return InterpreterToVM.allocatePrimitiveArray((byte) JavaKind.Char.getBasicType(), len);
    }

    @JniImpl
    public static @Host(short[].class) StaticObject NewShortArray(int len) {
        return InterpreterToVM.allocatePrimitiveArray((byte) JavaKind.Short.getBasicType(), len);
    }

    @JniImpl
    public static @Host(int[].class) StaticObject NewIntArray(int len) {
        return InterpreterToVM.allocatePrimitiveArray((byte) JavaKind.Int.getBasicType(), len);
    }

    @JniImpl
    public static @Host(long[].class) StaticObject NewLongArray(int len) {
        return InterpreterToVM.allocatePrimitiveArray((byte) JavaKind.Long.getBasicType(), len);
    }

    @JniImpl
    public static @Host(float[].class) StaticObject NewFloatArray(int len) {
        return InterpreterToVM.allocatePrimitiveArray((byte) JavaKind.Float.getBasicType(), len);
    }

    @JniImpl
    public static @Host(double[].class) StaticObject NewDoubleArray(int len) {
        return InterpreterToVM.allocatePrimitiveArray((byte) JavaKind.Double.getBasicType(), len);
    }

    @JniImpl
    public @Host(Object[].class) StaticObject NewObjectArray(int length, @Host(Class.class) StaticObject elementClass, @Host(Object.class) StaticObject initialElement) {
        assert !elementClass.getMirrorKlass().isPrimitive();
        StaticObject arr = elementClass.getMirrorKlass().allocateReferenceArray(length);
        if (length > 0) {
            // Single store check
            getInterpreterToVM().setArrayObject(initialElement, 0, arr);
            Arrays.fill(arr.unwrap(), initialElement);
        }
        return arr;
    }

    // endregion New*Array

    // region Get*ArrayRegion

    @JniImpl
    public void GetBooleanArrayRegion(@Host(boolean[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        boolean[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        ByteBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Byte);
        for (int i = 0; i < len; ++i) {
            buf.put(contents[start + i] ? (byte) 1 : (byte) 0);
        }
    }

    private void boundsCheck(int start, int len, int arrayLength) {
        assert arrayLength >= 0;
        if (start < 0 || len < 0 || start + (long) len > arrayLength) {
            throw Meta.throwException(getMeta().java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    @JniImpl
    public void GetCharArrayRegion(@Host(char[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        char[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        CharBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Char).asCharBuffer();
        buf.put(contents, start, len);
    }

    @JniImpl
    public void GetByteArrayRegion(@Host(byte[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        byte[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        ByteBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Byte);
        buf.put(contents, start, len);
    }

    @JniImpl
    public void GetShortArrayRegion(@Host(short[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        short[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        ShortBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Short).asShortBuffer();
        buf.put(contents, start, len);
    }

    @JniImpl
    public void GetIntArrayRegion(@Host(int[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        int[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        IntBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Int).asIntBuffer();
        buf.put(contents, start, len);
    }

    @JniImpl
    public void GetFloatArrayRegion(@Host(float[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        float[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        FloatBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Float).asFloatBuffer();
        buf.put(contents, start, len);
    }

    @JniImpl
    public void GetDoubleArrayRegion(@Host(double[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        double[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        DoubleBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Double).asDoubleBuffer();
        buf.put(contents, start, len);
    }

    @JniImpl
    public void GetLongArrayRegion(@Host(long[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        long[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        LongBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Long).asLongBuffer();
        buf.put(contents, start, len);
    }

    // endregion Get*ArrayRegion

    // region Set*ArrayRegion

    @JniImpl
    public void SetBooleanArrayRegion(@Host(boolean[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        boolean[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        ByteBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Byte);
        for (int i = 0; i < len; ++i) {
            contents[start + i] = buf.get() != 0;
        }
    }

    @JniImpl
    public void SetCharArrayRegion(@Host(char[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        char[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        CharBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Char).asCharBuffer();
        buf.get(contents, start, len);
    }

    @JniImpl
    public void SetByteArrayRegion(@Host(byte[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        byte[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        ByteBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Byte);
        buf.get(contents, start, len);
    }

    @JniImpl
    public void SetShortArrayRegion(@Host(short[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        short[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        ShortBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Short).asShortBuffer();
        buf.get(contents, start, len);
    }

    @JniImpl
    public void SetIntArrayRegion(@Host(int[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        int[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        IntBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Int).asIntBuffer();
        buf.get(contents, start, len);
    }

    @JniImpl
    public void SetFloatArrayRegion(@Host(float[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        float[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        FloatBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Float).asFloatBuffer();
        buf.get(contents, start, len);
    }

    @JniImpl
    public void SetDoubleArrayRegion(@Host(double[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        double[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        DoubleBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Double).asDoubleBuffer();
        buf.get(contents, start, len);
    }

    @JniImpl
    public void SetLongArrayRegion(@Host(long[].class) StaticObject array, int start, int len, @Pointer TruffleObject bufPtr) {
        long[] contents = array.unwrap();
        boundsCheck(start, len, contents.length);
        LongBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Long).asLongBuffer();
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
    @TruffleBoundary
    public int GetStringLength(@Host(String.class) StaticObject string) {
        if (StaticObject.isNull(string)) {
            return 0;
        }
        return (int) getMeta().java_lang_String_length.invokeDirect(string);
    }

    /**
     * <h3>jstring NewStringUTF(JNIEnv *env, const char *bytes);</h3>
     *
     * Constructs a new java.lang.String object from an array of characters in modified UTF-8
     * encoding.
     *
     * @param bytesPtr pointer to a modified UTF-8 string.
     *
     * @return a Java string object, or NULL if the string cannot be constructed.
     *
     * @throws OutOfMemoryError if the system runs out of memory.
     */
    @JniImpl
    public @Host(String.class) StaticObject NewStringUTF(@Pointer TruffleObject bytesPtr) {
        String hostString = fromUTF8Ptr(bytesPtr);
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
    public @Pointer TruffleObject GetStringCritical(@Host(String.class) StaticObject str, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // always copy since pinning is not supported
        }
        final StaticObject stringChars = (StaticObject) getMeta().java_lang_String_value.get(str);
        int len = stringChars.length();
        ByteBuffer criticalRegion = allocateDirect(len, JavaKind.Char); // direct byte buffer
        // (non-relocatable)
        @Pointer
        TruffleObject address = byteBufferPointer(criticalRegion);
        GetCharArrayRegion(stringChars, 0, len, address);
        return address;
    }

    @JniImpl
    public @Pointer TruffleObject GetStringUTFChars(@Host(String.class) StaticObject str, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // always copy since pinning is not supported
        }
        byte[] bytes = ModifiedUtf8.asUtf(Meta.toHostString(str), true);
        ByteBuffer region = allocateDirect(bytes.length);
        region.put(bytes);
        return byteBufferPointer(region);
    }

    /**
     * <h3>const jchar * GetStringChars(JNIEnv *env, jstring string, jboolean *isCopy);</h3>
     *
     * Returns a pointer to the array of Unicode characters of the string. This pointer is valid
     * until ReleaseStringChars() is called.
     *
     * If isCopy is not NULL, then *isCopy is set to JNI_TRUE if a copy is made; or it is set to
     * JNI_FALSE if no copy is made.
     *
     * @param string a Java string object.
     * @param isCopyPtr a pointer to a boolean. Returns a pointer to a Unicode string, or NULL if
     *            the operation fails.
     */
    @JniImpl
    public @Pointer TruffleObject GetStringChars(@Host(String.class) StaticObject string, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // always copy since pinning is not supported
        }
        char[] chars = ((StaticObject) getMeta().java_lang_String_value.get(string)).unwrap();
        // Add one for zero termination.
        ByteBuffer bb = allocateDirect(chars.length + 1, JavaKind.Char);
        CharBuffer region = bb.asCharBuffer();
        region.put(chars);
        region.put((char) 0);
        return byteBufferPointer(bb);
    }

    /**
     * <h3>void ReleaseStringChars(JNIEnv *env, jstring string, const jchar *chars);</h3>
     *
     * Informs the VM that the native code no longer needs access to chars. The chars argument is a
     * pointer obtained from string using GetStringChars().
     *
     * @param string a Java string object.
     * @param charsPtr a pointer to a Unicode string.
     */
    @JniImpl
    public void ReleaseStringChars(@SuppressWarnings("unused") @Host(String.class) StaticObject string, @Pointer TruffleObject charsPtr) {
        long nativePtr = interopAsPointer(charsPtr);
        assert nativeBuffers.containsKey(nativePtr);
        nativeBuffers.remove(nativePtr);
    }

    @JniImpl
    public void ReleaseStringUTFChars(@SuppressWarnings("unused") @Host(String.class) StaticObject str, @Pointer TruffleObject charsPtr) {
        long nativePtr = interopAsPointer(charsPtr);
        assert nativeBuffers.containsKey(nativePtr);
        nativeBuffers.remove(nativePtr);
    }

    @JniImpl
    public void ReleaseStringCritical(@SuppressWarnings("unused") @Host(String.class) StaticObject str, @Pointer TruffleObject criticalRegionPtr) {
        long nativePtr = interopAsPointer(criticalRegionPtr);
        assert nativeBuffers.containsKey(nativePtr);
        nativeBuffers.remove(nativePtr);
    }

    @JniImpl
    public @Host(String.class) StaticObject NewString(@Pointer TruffleObject unicodePtr, int len) {
        StaticObject value = StaticObject.wrap(new char[len], getMeta());
        SetCharArrayRegion(value, 0, len, unicodePtr);
        StaticObject guestString = getMeta().java_lang_String.allocateInstance();
        getMeta().java_lang_String_value.set(guestString, value);
        return guestString;
    }

    /**
     * <h3>void GetStringRegion(JNIEnv *env, jstring str, jsize start, jsize len, jchar *buf);</h3>
     *
     * Copies len number of Unicode characters beginning at offset start to the given buffer buf.
     *
     * Throws StringIndexOutOfBoundsException on index overflow.
     */
    @JniImpl
    public void GetStringRegion(@Host(String.class) StaticObject str, int start, int len, @Pointer TruffleObject bufPtr) {
        char[] chars = ((StaticObject) getMeta().java_lang_String_value.get(str)).unwrap();
        if (start < 0 || start + (long) len > chars.length) {
            throw Meta.throwException(getMeta().java_lang_StringIndexOutOfBoundsException);
        }
        CharBuffer buf = directByteBuffer(bufPtr, len, JavaKind.Char).asCharBuffer();
        buf.put(chars, start, len);
    }

    @JniImpl
    public static int GetStringUTFLength(@Host(String.class) StaticObject string) {
        return ModifiedUtf8.utfLength(Meta.toHostString(string));
    }

    @JniImpl
    public void GetStringUTFRegion(@Host(String.class) StaticObject str, int start, int len, @Pointer TruffleObject bufPtr) {
        int length = ModifiedUtf8.utfLength(Meta.toHostString(str));
        if (start < 0 || start + (long) len > length) {
            throw Meta.throwException(getMeta().java_lang_StringIndexOutOfBoundsException);
        }
        byte[] bytes = ModifiedUtf8.asUtf(Meta.toHostString(str), start, len, true); // always 0
        // terminated.
        ByteBuffer buf = directByteBuffer(bufPtr, bytes.length, JavaKind.Byte);
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
    @TruffleBoundary
    public boolean ExceptionCheck() {
        StaticObject ex = threadLocalPendingException.get();
        assert ex == null || StaticObject.notNull(ex); // ex != null => ex != NULL
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
    public int Throw(@Host(Throwable.class) StaticObject obj) {
        assert getMeta().java_lang_Throwable.isAssignableFrom(obj.getKlass());
        // The TLS exception slot will be set by the JNI wrapper.
        // Throwing methods always return the default value, in this case 0 (success).
        throw Meta.throwException(obj);
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
    public static int ThrowNew(@Host(Class.class) StaticObject clazz, @Pointer TruffleObject messagePtr) {
        String message = interopPointerToString(messagePtr);
        // The TLS exception slot will be set by the JNI wrapper.
        // Throwing methods always return the default value, in this case 0 (success).
        throw Meta.throwExceptionWithMessage((ObjectKlass) clazz.getMirrorKlass(), message);
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
    @TruffleBoundary
    public @Host(Throwable.class) StaticObject ExceptionOccurred() {
        StaticObject ex = threadLocalPendingException.get();
        if (ex == null) {
            ex = StaticObject.NULL;
        }
        return ex;
    }

    /**
     * <h3>void ExceptionDescribe(JNIEnv *env);</h3>
     *
     * Prints an exception and a backtrace of the stack to a system error-reporting channel, such as
     * stderr. This is a convenience routine provided for debugging.
     */
    @JniImpl
    public void ExceptionDescribe() {
        StaticObject ex = getPendingException();
        if (ex != null) {
            assert InterpreterToVM.instanceOf(ex, getMeta().java_lang_Throwable);
            // Dynamic lookup.
            Method printStackTrace = ex.getKlass().lookupMethod(Name.printStackTrace, Signature._void);
            printStackTrace.invokeDirect(ex);
            // Restore exception cleared by invokeDirect.
            setPendingException(ex);
        }
    }

    /**
     * <h3>void FatalError(JNIEnv *env, const char *msg);</h3>
     *
     * Raises a fatal error and does not expect the VM to recover. This function does not return.
     *
     * @param msgPtr an error message. The string is encoded in modified UTF-8.
     */
    @JniImpl
    public static void FatalError(@Pointer TruffleObject msgPtr) {
        String msg = interopPointerToString(msgPtr);
        throw new EspressoError(msg);
    }

    // endregion Exception handling

    // region Monitors

    @JniImpl
    public static int MonitorEnter(@Host(Object.class) StaticObject object) {
        InterpreterToVM.monitorEnter(object);
        return JNI_OK;
    }

    @JniImpl
    public int MonitorExit(@Host(Object.class) StaticObject object) {
        try {
            InterpreterToVM.monitorExit(object);
        } catch (EspressoException e) {
            assert InterpreterToVM.instanceOf(e.getExceptionObject(), getMeta().java_lang_IllegalMonitorStateException);
            setPendingException(e.getExceptionObject());
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
    public @Host(Object.class) StaticObject GetObjectArrayElement(StaticObject array, int index) {
        return getInterpreterToVM().getArrayObject(index, array);
    }

    /**
     * <h3>void SetObjectArrayElement(JNIEnv *env, jobjectArray array, jsize index, jobject value);
     * </h3>
     *
     * Sets an element of an Object array.
     *
     * @param array a Java array.
     *
     * @param index array index.
     *
     * @param value the new value.
     * @throws ArrayIndexOutOfBoundsException if index does not specify a valid index in the array.
     * @throws ArrayStoreException if the class of value is not a subclass of the element class of
     *             the array.
     */
    @JniImpl
    public void SetObjectArrayElement(StaticObject array, int index, @Host(Object.class) StaticObject value) {
        getInterpreterToVM().setArrayObject(value, index, array);
    }

    // endregion Get/SetObjectArrayElement

    // region Get*ArrayElements

    @JniImpl
    public @Pointer TruffleObject GetBooleanArrayElements(@Host(boolean[].class) StaticObject array, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        boolean[] data = array.unwrap();
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Boolean);
        for (int i = 0; i < data.length; ++i) {
            bytes.put(data[i] ? (byte) 1 : (byte) 0);
        }
        return byteBufferPointer(bytes);
    }

    @JniImpl
    public @Pointer TruffleObject GetCharArrayElements(@Host(char[].class) StaticObject array, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        char[] data = array.unwrap();
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Char);
        CharBuffer elements = bytes.asCharBuffer();
        elements.put(data);
        return byteBufferPointer(bytes);
    }

    @JniImpl
    public @Pointer TruffleObject GetByteArrayElements(@Host(byte[].class) StaticObject array, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        byte[] data = array.unwrap();
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Byte);
        ByteBuffer elements = bytes;
        elements.put(data);
        return byteBufferPointer(bytes);
    }

    @JniImpl
    public @Pointer TruffleObject GetShortArrayElements(@Host(short[].class) StaticObject array, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        short[] data = array.unwrap();
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Short);
        ShortBuffer elements = bytes.asShortBuffer();
        elements.put(data);
        return byteBufferPointer(bytes);
    }

    @JniImpl
    public @Pointer TruffleObject GetIntArrayElements(@Host(int[].class) StaticObject array, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        int[] data = array.unwrap();
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Int);
        IntBuffer elements = bytes.asIntBuffer();
        elements.put(data);
        return byteBufferPointer(bytes);
    }

    @JniImpl
    public @Pointer TruffleObject GetFloatArrayElements(@Host(float[].class) StaticObject array, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        float[] data = array.unwrap();
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Float);
        FloatBuffer elements = bytes.asFloatBuffer();
        elements.put(data);
        return byteBufferPointer(bytes);
    }

    @JniImpl
    public @Pointer TruffleObject GetDoubleArrayElements(@Host(double[].class) StaticObject array, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        double[] data = array.unwrap();
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Double);
        DoubleBuffer elements = bytes.asDoubleBuffer();
        elements.put(data);
        return byteBufferPointer(bytes);
    }

    @JniImpl
    public @Pointer TruffleObject GetLongArrayElements(@Host(long[].class) StaticObject array, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        long[] data = array.unwrap();
        ByteBuffer bytes = allocateDirect(data.length, JavaKind.Long);
        LongBuffer elements = bytes.asLongBuffer();
        elements.put(data);
        return byteBufferPointer(bytes);
    }

    // endregion Get*ArrayElements

    // region Release*ArrayElements

    private void ReleasePrimitiveArrayElements(StaticObject object, @Pointer TruffleObject bufPtr, int mode) {
        if (mode == 0 || mode == JNI_COMMIT) { // Update array contents.
            StaticObject array = object;
            StaticObject clazz = GetObjectClass(array);
            JavaKind componentKind = ((ArrayKlass) clazz.getMirrorKlass()).getComponentType().getJavaKind();
            assert componentKind.isPrimitive();
            int length = GetArrayLength(array);
            // @formatter:off
            switch (componentKind) {
                case Boolean : SetBooleanArrayRegion(array, 0, length, bufPtr);  break;
                case Byte    : SetByteArrayRegion(array, 0, length, bufPtr);     break;
                case Short   : SetShortArrayRegion(array, 0, length, bufPtr);    break;
                case Char    : SetCharArrayRegion(array, 0, length, bufPtr);     break;
                case Int     : SetIntArrayRegion(array, 0, length, bufPtr);      break;
                case Float   : SetFloatArrayRegion(array, 0, length, bufPtr);    break;
                case Long    : SetLongArrayRegion(array, 0, length, bufPtr);     break;
                case Double  : SetDoubleArrayRegion(array, 0, length, bufPtr);   break;
                default      : throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
        }
        if (mode == 0 || mode == JNI_ABORT) { // Dispose copy.
            long nativePtr = interopAsPointer(bufPtr);
            assert nativeBuffers.containsKey(nativePtr);
            nativeBuffers.remove(nativePtr);
        }
    }

    @JniImpl
    public void ReleaseBooleanArrayElements(StaticObject object, @Pointer TruffleObject bufPtr, int mode) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Boolean;
        ReleasePrimitiveArrayElements(object, bufPtr, mode);
    }

    @JniImpl
    public void ReleaseByteArrayElements(StaticObject object, @Pointer TruffleObject bufPtr, int mode) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Byte;
        ReleasePrimitiveArrayElements(object, bufPtr, mode);
    }

    @JniImpl
    public void ReleaseCharArrayElements(StaticObject object, @Pointer TruffleObject bufPtr, int mode) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Char;
        ReleasePrimitiveArrayElements(object, bufPtr, mode);
    }

    @JniImpl
    public void ReleaseShortArrayElements(StaticObject object, @Pointer TruffleObject bufPtr, int mode) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Short;
        ReleasePrimitiveArrayElements(object, bufPtr, mode);
    }

    @JniImpl
    public void ReleaseIntArrayElements(StaticObject object, @Pointer TruffleObject bufPtr, int mode) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Int;
        ReleasePrimitiveArrayElements(object, bufPtr, mode);
    }

    @JniImpl
    public void ReleaseLongArrayElements(StaticObject object, @Pointer TruffleObject bufPtr, int mode) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Long;
        ReleasePrimitiveArrayElements(object, bufPtr, mode);
    }

    @JniImpl
    public void ReleaseFloatArrayElements(StaticObject object, @Pointer TruffleObject bufPtr, int mode) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Float;
        ReleasePrimitiveArrayElements(object, bufPtr, mode);
    }

    @JniImpl
    public void ReleaseDoubleArrayElements(StaticObject object, @Pointer TruffleObject bufPtr, int mode) {
        assert ((ArrayKlass) object.getKlass()).getComponentType().getJavaKind() == JavaKind.Double;
        ReleasePrimitiveArrayElements(object, bufPtr, mode);
    }

    // endregion Release*ArrayElements

    // region DirectBuffers

    /**
     * <h3>jobject NewDirectByteBuffer(JNIEnv* env, void* address, jlong capacity);</h3>
     *
     * Allocates and returns a direct java.nio.ByteBuffer referring to the block of memory starting
     * at the memory address address and extending capacity bytes.
     *
     * Native code that calls this function and returns the resulting byte-buffer object to
     * Java-level code should ensure that the buffer refers to a valid region of memory that is
     * accessible for reading and, if appropriate, writing. An attempt to access an invalid memory
     * location from Java code will either return an arbitrary value, have no visible effect, or
     * cause an unspecified exception to be thrown.
     *
     * @param addressPtr the starting address of the memory region (must not be NULL)
     *
     * @param capacity the size in bytes of the memory region (must be positive)
     *
     * @return a local reference to the newly-instantiated java.nio.ByteBuffer object. Returns NULL
     *         if an exception occurs, or if JNI access to direct buffers is not supported by this
     *         virtual machine.
     * @throws OutOfMemoryError if allocation of the ByteBuffer object fails
     */
    @JniImpl
    public @Host(typeName = "Ljava/nio/DirectByteBuffer;") StaticObject NewDirectByteBuffer(@Pointer TruffleObject addressPtr, long capacity) {
        Meta meta = getMeta();
        StaticObject instance = meta.java_nio_DirectByteBuffer.allocateInstance();
        long address = interopAsPointer(addressPtr);
        meta.java_nio_DirectByteBuffer_init_long_int.invokeDirect(instance, address, (int) capacity);
        return instance;
    }

    /**
     * <h3>void* GetDirectBufferAddress(JNIEnv* env, jobject buf);</h3>
     *
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
    public @Pointer TruffleObject GetDirectBufferAddress(@Host(java.nio.Buffer.class) StaticObject buf) {
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
     *
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
    public long GetDirectBufferCapacity(@Host(java.nio.Buffer.class) StaticObject buf) {
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
    public int RegisterNative(@Host(Class.class) StaticObject clazz, @Pointer TruffleObject methodNamePtr, @Pointer TruffleObject methodSignaturePtr, @Pointer TruffleObject closure) {
        String methodName = interopPointerToString(methodNamePtr);
        String methodSignature = interopPointerToString(methodSignaturePtr);
        assert methodName != null && methodSignature != null;

        Symbol<Name> name = getNames().lookup(methodName);
        Symbol<Signature> signature = getSignatures().lookupValidSignature(methodSignature);

        Meta meta = getMeta();
        if (name == null || signature == null) {
            setPendingException(Meta.initException(meta.java_lang_NoSuchMethodError));
            return JNI_ERR;
        }

        Method m = clazz.getMirrorKlass().lookupDeclaredMethod(name, signature);
        if (m != null && m.isNative()) {
            m.unregisterNative();
            getSubstitutions().removeRuntimeSubstitution(m);
        } else {
            setPendingException(Meta.initException(meta.java_lang_NoSuchMethodError));
            return JNI_ERR;
        }

        final TruffleObject boundNative = NativeLibrary.bind(closure, nfiSignature(getSignatures().parsed(signature), true));
        Substitutions.EspressoRootNodeFactory factory = new Substitutions.EspressoRootNodeFactory() {
            @Override
            public EspressoRootNode spawnNode(Method method) {
                return EspressoRootNode.create(null, new NativeMethodNode(boundNative, method.getMethodVersion(), true));
            }
        };
        Symbol<Type> classType = clazz.getMirrorKlass().getType();
        getSubstitutions().registerRuntimeSubstitution(classType, name, signature, factory, true);
        return JNI_OK;
    }

    /**
     * <h3>jint UnregisterNatives(JNIEnv *env, jclass clazz);</h3>
     *
     * Unregisters native methods of a class. The class goes back to the state before it was linked
     * or registered with its native method functions.
     *
     * This function should not be used in normal native code. Instead, it provides special programs
     * a way to reload and relink native libraries.
     *
     * @param clazz a Java class object.
     *
     *            Returns 0 on success; returns a negative value on failure.
     */
    @JniImpl
    public int UnregisterNatives(@Host(Class.class) StaticObject clazz) {
        Klass klass = clazz.getMirrorKlass();
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
     *
     * Converts a method ID derived from cls to a java.lang.reflect.Method or
     * java.lang.reflect.Constructor object. isStatic must be set to JNI_TRUE if the method ID
     * refers to a static field, and JNI_FALSE otherwise.
     *
     * Throws OutOfMemoryError and returns 0 if fails.
     */
    @JniImpl
    public @Host(java.lang.reflect.Executable.class) StaticObject ToReflectedMethod(@Host(Class.class) StaticObject unused, @Handle(Method.class) long methodId,
                    @SuppressWarnings("unused") boolean isStatic) {
        Method method = methodIds.getObject(methodId);
        assert method.getDeclaringKlass().isAssignableFrom(unused.getMirrorKlass());

        StaticObject methods = null;
        if (method.isConstructor()) {
            methods = Target_java_lang_Class.getDeclaredConstructors0(method.getDeclaringKlass().mirror(), false, getMeta());
        } else {
            methods = Target_java_lang_Class.getDeclaredMethods0(method.getDeclaringKlass().mirror(), false, getMeta());
        }

        for (StaticObject declMethod : methods.<StaticObject[]> unwrap()) {
            assert InterpreterToVM.instanceOf(declMethod, getMeta().java_lang_reflect_Executable);
            Method m = null;
            if (method.isConstructor()) {
                assert InterpreterToVM.instanceOf(declMethod, getMeta().java_lang_reflect_Constructor);
                m = (Method) declMethod.getHiddenField(getMeta().HIDDEN_CONSTRUCTOR_KEY);
            } else {
                assert InterpreterToVM.instanceOf(declMethod, getMeta().java_lang_reflect_Method);
                m = (Method) declMethod.getHiddenField(getMeta().HIDDEN_METHOD_KEY);
            }
            if (method == m) {
                return declMethod;
            }
        }

        throw EspressoError.shouldNotReachHere("Method/constructor not found " + method);
    }

    /**
     * <h3>jobject ToReflectedField(JNIEnv *env, jclass cls, jfieldID fieldID, jboolean isStatic);
     * </h3>
     *
     * Converts a field ID derived from cls to a java.lang.reflect.Field object. isStatic must be
     * set to JNI_TRUE if fieldID refers to a static field, and JNI_FALSE otherwise.
     *
     * Throws OutOfMemoryError and returns 0 if fails.
     */
    @JniImpl
    public @Host(java.lang.reflect.Field.class) StaticObject ToReflectedField(@Host(Class.class) StaticObject unused, @Handle(Field.class) long fieldId, @SuppressWarnings("unused") boolean isStatic) {
        Field field = fieldIds.getObject(fieldId);
        assert field.getDeclaringKlass().isAssignableFrom(unused.getMirrorKlass());
        StaticObject fields = Target_java_lang_Class.getDeclaredFields0(field.getDeclaringKlass().mirror(), false, getMeta());
        for (StaticObject declField : fields.<StaticObject[]> unwrap()) {
            assert InterpreterToVM.instanceOf(declField, getMeta().java_lang_reflect_Field);
            Field f = (Field) declField.getHiddenField(getMeta().HIDDEN_FIELD_KEY);
            if (field == f) {
                return declField;
            }
        }

        throw EspressoError.shouldNotReachHere("Field not found " + field);
    }

    /**
     * <h3>jfieldID FromReflectedField(JNIEnv *env, jobject field);</h3>
     *
     * Converts a java.lang.reflect.Field to a field ID.
     */
    @JniImpl
    public @Handle(Field.class) long FromReflectedField(@Host(java.lang.reflect.Field.class) StaticObject field) {
        assert InterpreterToVM.instanceOf(field, getMeta().java_lang_reflect_Field);
        Field guestField = Field.getReflectiveFieldRoot(field, getMeta());
        guestField.getDeclaringKlass().initialize();
        return fieldIds.handlify(guestField);
    }

    /**
     * <h3>jmethodID FromReflectedMethod(JNIEnv *env, jobject method);</h3>
     *
     * Converts a java.lang.reflect.Method or java.lang.reflect.Constructor object to a method ID.
     */
    @JniImpl
    public @Handle(Method.class) long FromReflectedMethod(@Host(java.lang.reflect.Executable.class) StaticObject method) {
        assert InterpreterToVM.instanceOf(method, getMeta().java_lang_reflect_Method) || InterpreterToVM.instanceOf(method, getMeta().java_lang_reflect_Constructor);
        Method guestMethod;
        if (InterpreterToVM.instanceOf(method, getMeta().java_lang_reflect_Method)) {
            guestMethod = Method.getHostReflectiveMethodRoot(method, getMeta());
        } else if (InterpreterToVM.instanceOf(method, getMeta().java_lang_reflect_Constructor)) {
            guestMethod = Method.getHostReflectiveConstructorRoot(method, getMeta());
        } else {
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
    public static @Host(Object.class) StaticObject NewLocalRef(@Host(Object.class) StaticObject ref) {
        // Local ref is allocated on return.
        return ref;
    }

    /**
     * <h3>jobject NewGlobalRef(JNIEnv *env, jobject obj);</h3>
     *
     * Creates a new global reference to the object referred to by the obj argument. The
     * <b>handle</b> argument may be a global or local reference. Global references must be
     * explicitly disposed of by calling DeleteGlobalRef().
     *
     * @param handle a global or local reference.
     * @return a global reference, or NULL if the system runs out of memory.
     */
    @JniImpl
    public @Handle(StaticObject.class) long NewGlobalRef(@Handle(StaticObject.class) long handle) {
        return getHandles().createGlobal(getHandles().get(Math.toIntExact(handle)));
    }

    /**
     * <h3>void DeleteGlobalRef(JNIEnv *env, jobject globalRef);</h3>
     *
     * Deletes the global reference pointed to by globalRef.
     *
     * @param handle a global reference.
     */
    @JniImpl
    public void DeleteGlobalRef(@Handle(StaticObject.class) long handle) {
        getHandles().deleteGlobalRef(Math.toIntExact(handle));
    }

    /**
     * <h3>void DeleteLocalRef(JNIEnv *env, jobject localRef);</h3>
     *
     * Deletes the local reference pointed to by localRef.
     *
     * <p>
     * <b>Note:</b> JDK/JRE 1.1 provides the DeleteLocalRef function above so that programmers can
     * manually delete local references. For example, if native code iterates through a potentially
     * large array of objects and uses one element in each iteration, it is a good practice to
     * delete the local reference to the no-longer-used array element before a new local reference
     * is created in the next iteration.
     *
     * As of JDK/JRE 1.2 an additional set of functions are provided for local reference lifetime
     * management. They are the four functions listed below.
     *
     * @param handle a local reference.
     */
    @JniImpl
    public void DeleteLocalRef(@Handle(StaticObject.class) long handle) {
        getHandles().deleteLocalRef(Math.toIntExact(handle));
    }

    /**
     * <h3>jweak NewWeakGlobalRef(JNIEnv *env, jobject obj);</h3>
     *
     * Creates a new weak global reference. Returns NULL if obj refers to null, or if the VM runs
     * out of memory. If the VM runs out of memory, an OutOfMemoryError will be thrown.
     */
    @JniImpl
    public @Handle(StaticObject.class) long NewWeakGlobalRef(@Handle(StaticObject.class) long handle) {
        return getHandles().createWeakGlobal(getHandles().get(Math.toIntExact(handle)));
    }

    /**
     * <h3>void DeleteWeakGlobalRef(JNIEnv *env, jweak obj);</h3>
     *
     * Delete the VM resources needed for the given weak global reference.
     */
    @JniImpl
    public void DeleteWeakGlobalRef(@Handle(StaticObject.class) long handle) {
        getHandles().deleteGlobalRef(Math.toIntExact(handle));
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
    public @Host(Object.class) StaticObject PopLocalFrame(@Host(Object.class) StaticObject object) {
        getHandles().popFrame();
        return object;
    }

    /**
     * <h3>jboolean IsSameObject(JNIEnv *env, jobject ref1, jobject ref2);</h3>
     *
     * Tests whether two references refer to the same Java object.
     *
     * @param ref1 a Java object.
     * @param ref2 a Java object.
     *
     * @return JNI_TRUE if ref1 and ref2 refer to the same Java object, or are both NULL; otherwise,
     *         returns JNI_FALSE.
     */
    @JniImpl
    public static boolean IsSameObject(@Host(Object.class) StaticObject ref1, @Host(Object.class) StaticObject ref2) {
        return ref1 == ref2;
    }

    /**
     * <h3>jobjectRefType GetObjectRefType(JNIEnv* env, jobject obj);</h3>
     *
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
     *
     *
     * An invalid reference is a reference which is not a valid handle. That is, the obj pointer
     * address does not point to a location in memory which has been allocated from one of the Ref
     * creation functions or returned from a JNI function.
     *
     * As such, NULL would be an invalid reference and GetObjectRefType(env,NULL) would return
     * JNIInvalidRefType.
     *
     * On the other hand, a null reference, which is a reference that points to a null, would return
     * the type of reference that the null reference was originally created as.
     *
     * GetObjectRefType cannot be used on deleted references.
     *
     * Since references are typically implemented as pointers to memory data structures that can
     * potentially be reused by any of the reference allocation services in the VM, once deleted, it
     * is not specified what value the GetObjectRefType will return.
     *
     * @param handle a local, global or weak global reference.
     *
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
        return getHandles().getObjectRefType(Math.toIntExact(handle));
    }

    /**
     * <h3>jint EnsureLocalCapacity(JNIEnv *env, jint capacity);</h3>
     *
     * Ensures that at least a given number of local references can be created in the current
     * thread. Returns 0 on success; otherwise returns a negative number and throws an
     * OutOfMemoryError.
     *
     * Before it enters a native method, the VM automatically ensures that at least 16 local
     * references can be created.
     *
     * For backward compatibility, the VM allocates local references beyond the ensured capacity.
     * (As a debugging support, the VM may give the user warnings that too many local references are
     * being created. In the JDK, the programmer can supply the -verbose:jni command line option to
     * turn on these messages.) The VM calls FatalError if no more local references can be created
     * beyond the ensured capacity.
     */
    @JniImpl
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
     *         <b>Error codes</b>
     *         <ul>
     *         <li>#define JNI_EDETACHED (-2) // thread detached from the VM
     *         <li>#define JNI_EVERSION (-3) // JNI version error
     *         </ul>
     */
    @JniImpl
    public static int GetVersion() {
        return JniVersion.JNI_VERSION_ESPRESSO;
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
    public static int GetArrayLength(@Host(Object.class) StaticObject array) {
        return InterpreterToVM.arrayLength(array);
    }

    @JniImpl
    public @Pointer TruffleObject GetPrimitiveArrayCritical(StaticObject object, @Pointer TruffleObject isCopyPtr) {
        if (!getUncached().isNull(isCopyPtr)) {
            ByteBuffer isCopyBuf = directByteBuffer(isCopyPtr, 1);
            isCopyBuf.put((byte) 1); // Always copy since pinning is not supported.
        }
        StaticObject array = object;
        StaticObject clazz = GetObjectClass(array);
        JavaKind componentKind = ((ArrayKlass) clazz.getMirrorKlass()).getComponentType().getJavaKind();
        assert componentKind.isPrimitive();
        int length = GetArrayLength(array);

        ByteBuffer region = allocateDirect(length, componentKind);
        @Pointer
        TruffleObject addressPtr = byteBufferPointer(region);
        // @formatter:off
        switch (componentKind) {
            case Boolean : GetBooleanArrayRegion(array, 0, length, addressPtr);  break;
            case Byte    : GetByteArrayRegion(array, 0, length, addressPtr);     break;
            case Short   : GetShortArrayRegion(array, 0, length, addressPtr);    break;
            case Char    : GetCharArrayRegion(array, 0, length, addressPtr);     break;
            case Int     : GetIntArrayRegion(array, 0, length, addressPtr);      break;
            case Float   : GetFloatArrayRegion(array, 0, length, addressPtr);    break;
            case Long    : GetLongArrayRegion(array, 0, length, addressPtr);     break;
            case Double  : GetDoubleArrayRegion(array, 0, length, addressPtr);   break;
            case Object  : // fall through
            case Void    : // fall through
            case Illegal : // fall through
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on

        return addressPtr;
    }

    @JniImpl
    public void ReleasePrimitiveArrayCritical(@Host(Object.class) StaticObject object, @Pointer TruffleObject carrayPtr, int mode) {
        if (mode == 0 || mode == JNI_COMMIT) { // Update array contents.
            StaticObject array = object;
            StaticObject clazz = GetObjectClass(array);
            JavaKind componentKind = ((ArrayKlass) clazz.getMirrorKlass()).getComponentType().getJavaKind();
            assert componentKind.isPrimitive();
            int length = GetArrayLength(array);
            // @formatter:off
            switch (componentKind) {
                case Boolean : SetBooleanArrayRegion(array, 0, length, carrayPtr);   break;
                case Byte    : SetByteArrayRegion(array, 0, length, carrayPtr);      break;
                case Short   : SetShortArrayRegion(array, 0, length, carrayPtr);     break;
                case Char    : SetCharArrayRegion(array, 0, length, carrayPtr);      break;
                case Int     : SetIntArrayRegion(array, 0, length, carrayPtr);       break;
                case Float   : SetFloatArrayRegion(array, 0, length, carrayPtr);     break;
                case Long    : SetLongArrayRegion(array, 0, length, carrayPtr);      break;
                case Double  : SetDoubleArrayRegion(array, 0, length, carrayPtr);    break;
                default      : throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
        }
        if (mode == 0 || mode == JNI_ABORT) { // Dispose copy.
            long nativePtr = interopAsPointer(carrayPtr);
            assert nativeBuffers.containsKey(nativePtr);
            nativeBuffers.remove(nativePtr);
        }
    }

    /**
     * <h3>jclass GetObjectClass(JNIEnv *env, jobject obj);</h3>
     *
     * Returns the class of an object.
     *
     * @param self a Java object (must not be NULL).
     */
    @JniImpl
    public static @Host(Class.class) StaticObject GetObjectClass(@Host(Object.class) StaticObject self) {
        return self.getKlass().mirror();
    }

    /**
     * <h3>jclass GetSuperclass(JNIEnv *env, jclass clazz);</h3>
     *
     * If clazz represents any class other than the class Object, then this function returns the
     * object that represents the superclass of the class specified by clazz. If clazz specifies the
     * class Object, or clazz represents an interface, this function returns NULL.
     *
     * @param clazz a Java class object. Returns the superclass of the class represented by clazz,
     *            or NULL.
     */
    @JniImpl
    public static @Host(Class.class) StaticObject GetSuperclass(@Host(Class.class) StaticObject clazz) {
        Klass klass = clazz.getMirrorKlass();
        if (klass.isInterface() || klass.isJavaLangObject()) {
            return StaticObject.NULL;
        }
        return klass.getSuperKlass().mirror();
    }

    @JniImpl
    public @Host(Object.class) StaticObject NewObjectVarargs(@Host(Class.class) StaticObject clazz, @Handle(Method.class) long methodId, @Pointer TruffleObject varargsPtr) {
        Method method = methodIds.getObject(methodId);
        assert method.isConstructor();
        Klass klass = clazz.getMirrorKlass();
        if (klass.isInterface() || klass.isAbstract()) {
            throw Meta.throwException(getMeta().java_lang_InstantiationException);
        }
        klass.initialize();
        StaticObject instance;
        if (CompilerDirectives.isPartialEvaluationConstant(klass)) {
            instance = klass.allocateInstance();
        } else {
            instance = allocateBoundary(klass);
        }
        method.invokeDirect(instance, popVarArgs(varargsPtr, method.getParsedSignature()));
        return instance;
    }

    @TruffleBoundary
    public static StaticObject allocateBoundary(Klass klass) {
        return klass.allocateInstance();
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
    @JniImpl
    public @Host(Class.class) StaticObject FindClass(@Pointer TruffleObject namePtr,
        @GuestCall DirectCallNode java_lang_ClassLoader_getSystemClassLoader, @GuestCall DirectCallNode java_lang_ClassLoader$NativeLibrary_getFromClass,
        @GuestCall DirectCallNode java_lang_Class_forName_String_boolean_ClassLoader,
        @InjectProfile SubstitutionProfiler profiler) {
        String name = interopPointerToString(namePtr);
        Meta meta = getMeta();
        if (name == null || name.contains(".")) {
            profiler.profile(7);
            throw Meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, name);
        }

        String internalName = name;
        if (!name.startsWith("[")) {
            // Force 'L' type.
            internalName = "L" + name + ";";
        }
        if (!Validation.validTypeDescriptor(ByteSequence.create(internalName), true)) {
            profiler.profile(6);
            throw Meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, name);
        }

        StaticObject protectionDomain = StaticObject.NULL;
        StaticObject loader = StaticObject.NULL;

        StaticObject caller = getVM().JVM_GetCallerClass(0, profiler); // security stack walk
        if (StaticObject.notNull(caller)) {
            Klass callerKlass = caller.getMirrorKlass();
            loader = callerKlass.getDefiningClassLoader();
            if (StaticObject.isNull(loader) && Type.java_lang_ClassLoader$NativeLibrary.equals(callerKlass.getType())) {
                StaticObject result = (StaticObject) java_lang_ClassLoader$NativeLibrary_getFromClass.call();
                loader = result.getMirrorKlass().getDefiningClassLoader();
                protectionDomain = Target_java_lang_Class.getProtectionDomain0(result, getMeta());
            }
        } else {
            loader = (StaticObject) java_lang_ClassLoader_getSystemClassLoader.call();
        }

        StaticObject guestClass = StaticObject.NULL;
        try {
            String dotName = name.replace('/', '.');
            guestClass = (StaticObject) java_lang_Class_forName_String_boolean_ClassLoader.call(meta.toGuestString(dotName), false, loader);
            EspressoError.guarantee(StaticObject.notNull(guestClass), "Class.forName returned null");
        } catch (EspressoException e) {
            profiler.profile(5);
            if (InterpreterToVM.instanceOf(e.getExceptionObject(), meta.java_lang_ClassNotFoundException)) {
                profiler.profile(4);
                throw Meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, name);
            }
            throw e;
        }

        guestClass.setHiddenField(meta.HIDDEN_PROTECTION_DOMAIN, protectionDomain);
        guestClass.getMirrorKlass().safeInitialize();

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
    public @Host(Class.class) StaticObject DefineClass(@Pointer TruffleObject namePtr, @Host(ClassLoader.class) StaticObject loader, @Pointer TruffleObject bufPtr, int bufLen,
                    @InjectProfile SubstitutionProfiler profiler) {
        // TODO(peterssen): Propagate errors and verifications, e.g. no class in the java package.
        return getVM().JVM_DefineClass(namePtr, loader, bufPtr, bufLen, StaticObject.NULL, profiler);
    }

    // JavaVM **vm);

    @JniImpl
    public int GetJavaVM(@Pointer TruffleObject vmPtr) {
        ByteBuffer buf = directByteBuffer(vmPtr, 1, JavaKind.Long); // 64 bits pointer
        buf.putLong(interopAsPointer(getVM().getJavaVM()));
        return JNI_OK;
    }

    /**
     * <h3>jobject AllocObject(JNIEnv *env, jclass clazz);</h3>
     *
     * Allocates a new Java object without invoking any of the constructors for the object. Returns
     * a reference to the object.
     *
     * The clazz argument must not refer to an array class.
     *
     * @param clazz a Java class object.
     *
     *            Returns a Java object, or NULL if the object cannot be constructed.
     *
     *            Throws InstantiationException if the class is an interface or an abstract class.
     * @throws OutOfMemoryError if the system runs out of memory.
     */
    @JniImpl
    public @Host(Object.class) StaticObject AllocObject(@Host(Class.class) StaticObject clazz) {
        if (StaticObject.isNull(clazz)) {
            throw Meta.throwException(getMeta().java_lang_InstantiationException);
        }
        Klass klass = clazz.getMirrorKlass();
        return klass.allocateInstance();
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
    public static boolean IsAssignableFrom(@Host(Class.class) StaticObject clazz1, @Host(Class.class) StaticObject clazz2) {
        Klass klass2 = clazz2.getMirrorKlass();
        return klass2.isAssignableFrom(clazz1.getMirrorKlass());
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
    public static boolean IsInstanceOf(@Host(Object.class) StaticObject obj, @Host(Class.class) StaticObject clazz) {
        if (StaticObject.isNull(obj)) {
            return true;
        }
        return InterpreterToVM.instanceOf(obj, clazz.getMirrorKlass());
    }

    // Checkstyle: resume method name check
}
