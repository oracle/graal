/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vm;

import static com.oracle.truffle.espresso.classfile.Constants.ACC_ABSTRACT;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_CALLER_SENSITIVE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINAL;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_LAMBDA_FORM_COMPILED;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PUBLIC;
import static com.oracle.truffle.espresso.jni.JniEnv.JNI_EDETACHED;
import static com.oracle.truffle.espresso.jni.JniEnv.JNI_ERR;
import static com.oracle.truffle.espresso.jni.JniEnv.JNI_EVERSION;
import static com.oracle.truffle.espresso.jni.JniEnv.JNI_OK;
import static com.oracle.truffle.espresso.meta.EspressoError.cat;
import static com.oracle.truffle.espresso.runtime.Classpath.JAVA_BASE;
import static com.oracle.truffle.espresso.runtime.EspressoContext.DEFAULT_STACK_SIZE;

import java.io.File;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Array;
import java.lang.reflect.Parameter;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlContext;
import java.security.ProtectionDomain;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Level;

import com.oracle.truffle.espresso._native.RawPointer;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso._native.NativeSignature;
import com.oracle.truffle.espresso._native.NativeType;
import com.oracle.truffle.espresso._native.Pointer;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.MethodParametersAttribute;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.Validation;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.EntryTable;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ModuleTable;
import com.oracle.truffle.espresso.impl.ModuleTable.ModuleEntry;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PackageTable;
import com.oracle.truffle.espresso.impl.PackageTable.PackageEntry;
import com.oracle.truffle.espresso.jni.Callback;
import com.oracle.truffle.espresso.jni.JNIHandles;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.jni.JniImpl;
import com.oracle.truffle.espresso.jni.JniVersion;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.jni.NativeLibrary;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNodeGen;
import com.oracle.truffle.espresso.runtime.Classpath;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
import com.oracle.truffle.espresso.runtime.EspressoProperties;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.GuestCall;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.substitutions.InjectMeta;
import com.oracle.truffle.espresso.substitutions.InjectProfile;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.substitutions.SuppressFBWarnings;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Class;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_System;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread.State;

/**
 * Espresso implementation of the VM interface (libjvm).
 * <p>
 * Adding a new VM method requires doing a few things in package
 * com.oracle.truffle.espresso.mokapot:
 * <p>
 * - adding it in include/mokapot.h
 * <p>
 * - implementing it in src/mokapot.c
 * <p>
 * - registering it in mapfile-vers
 * <p>
 * - for new VM methods (/ex: upgrading from java 8 to 11), updating include/jvm.h
 */
public final class VM extends NativeEnv implements ContextAccess {

    private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, VM.class);
    private final InteropLibrary uncached = InteropLibrary.getFactory().getUncached();

    private final @Pointer TruffleObject disposeMokapotContext;
    private final @Pointer TruffleObject initializeManagementContext;
    private final @Pointer TruffleObject disposeManagementContext;
    private final @Pointer TruffleObject getJavaVM;
    private final @Pointer TruffleObject mokapotAttachThread;
    private final @Pointer TruffleObject getPackageAt;

    private final long rtldDefaultValue;
    private final boolean safeRTLDDefaultLookup;

    private final JniEnv jniEnv;

    private @Pointer TruffleObject managementPtr;
    private @Pointer TruffleObject mokapotEnvPtr;

    // libjava must be loaded after mokapot.
    private final @Pointer TruffleObject javaLibrary;

    private static String stringify(List<Path> paths) {
        StringJoiner joiner = new StringJoiner(File.pathSeparator);
        for (Path p : paths) {
            joiner.add(p.toString());
        }
        return joiner.toString();
    }

    protected TruffleLogger getLogger() {
        return logger;
    }

    protected InteropLibrary getUncached() {
        return uncached;
    }

    public void attachThread(Thread hostThread) {
        assert hostThread == Thread.currentThread();
        try {
            getUncached().execute(mokapotAttachThread, mokapotEnvPtr);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("mokapotAttachThread failed", e);
        }
    }

    public static final class GlobalFrameIDs {
        private static final AtomicLong id = new AtomicLong();

        public static long getID() {
            return id.incrementAndGet();
        }
    }

    private Callback lookupVmImplCallback = new Callback(LOOKUP_VM_IMPL_PARAMETER_COUNT, new Callback.Function() {
        @Override
        public Object call(Object... args) {
            try {
                String name = interopPointerToString((TruffleObject) args[0]);
                return VM.this.lookupVmImpl(name);
            } catch (ClassCastException e) {
                throw EspressoError.shouldNotReachHere(e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    });

    public JNIHandles getHandles() {
        return jniEnv.getHandles();
    }

    public @Pointer TruffleObject getJavaLibrary() {
        return javaLibrary;
    }

    private @Pointer TruffleObject loadJavaLibrary(List<Path> bootLibraryPath) {
        // Comment from HotSpot:
        // Try to load verify dll first. In 1.3 java dll depends on it and is not
        // always able to find it when the loading executable is outside the JDK.
        // In order to keep working with 1.2 we ignore any loading errors.
        /* verifyLibrary = */ getNativeAccess().loadLibrary(bootLibraryPath, "verify", false);
        TruffleObject libJava = getNativeAccess().loadLibrary(bootLibraryPath, "java", true);

        if (getJavaVersion().java9OrLater()) {
            return libJava;
        }

        // The JNI_OnLoad handling is normally done by method load in
        // java.lang.ClassLoader$NativeLibrary, but the VM loads the base library
        // explicitly so we have to check for JNI_OnLoad as well
        // libjava is initialized after libjvm (Espresso VM native context).

        // TODO(peterssen): Use JVM_FindLibraryEntry.
        TruffleObject jniOnLoad = getNativeAccess().lookupAndBindSymbol(libJava, "JNI_OnLoad", NativeType.INT, NativeType.POINTER, NativeType.POINTER);
        if (jniOnLoad != null) {
            try {
                getUncached().execute(jniOnLoad, mokapotEnvPtr, RawPointer.nullInstance());
            } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        return libJava;
    }

    private VM(JniEnv jniEnv) {
        this.jniEnv = jniEnv;
        try {
            EspressoProperties props = getContext().getVmProperties();

            // Load Espresso's libjvm:
            /*
             * jvm.dll (Windows) or libjvm.so (Unixes) is the Espresso implementation of the VM
             * interface (libjvm). Espresso loads all shared libraries in a private namespace (e.g.
             * using dlmopen on Linux). Espresso's libjvm must be loaded strictly before any other
             * library in the private namespace to avoid linking with HotSpot libjvm, then libjava
             * is loaded and further system libraries, libzip, libnet, libnio ...
             */
            @Pointer
            TruffleObject mokapotLibrary = getNativeAccess().loadLibrary(props.jvmLibraryPath(), "jvm", true);
            assert mokapotLibrary != null;

            @Pointer
            TruffleObject initializeMokapotContext = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "initializeMokapotContext", NativeType.POINTER, NativeType.POINTER, NativeType.POINTER);

            disposeMokapotContext = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "disposeMokapotContext",
                            NativeType.VOID, NativeType.POINTER, NativeType.POINTER);

            if (jniEnv.getContext().EnableManagement) {
                initializeManagementContext = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                                "initializeManagementContext", NativeType.POINTER, NativeType.POINTER, NativeType.INT);

                disposeManagementContext = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                                "disposeManagementContext",
                                NativeType.VOID, NativeType.POINTER, NativeType.INT, NativeType.POINTER);
            } else {
                initializeManagementContext = null;
                disposeManagementContext = null;
            }

            getJavaVM = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "getJavaVM",
                            NativeType.POINTER, NativeType.POINTER);

            mokapotAttachThread = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "mokapotAttachThread",
                            NativeType.VOID, NativeType.POINTER);

            @Pointer
            TruffleObject mokapotGetRTLDDefault = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "mokapotGetRTLD_DEFAULT",
                            NativeType.POINTER);

            getPackageAt = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "getPackageAt",
                            NativeType.POINTER, NativeType.POINTER, NativeType.INT);
            OptionValues options = EspressoLanguage.getCurrentContext().getEnv().getOptions();
            this.safeRTLDDefaultLookup = !options.get(EspressoOptions.UseTruffleNFIIsolatedNamespace);

            // void* fetch_by_name(char* function_name)
            @Pointer
            TruffleObject lookupVmImplNativeCallback = getNativeAccess().createNativeClosure(lookupVmImplCallback, NativeType.POINTER, NativeType.POINTER);

            this.mokapotEnvPtr = (TruffleObject) getUncached().execute(initializeMokapotContext, jniEnv.getNativePointer(), lookupVmImplNativeCallback);
            this.rtldDefaultValue = getUncached().asPointer(getUncached().execute(mokapotGetRTLDDefault));
            assert getUncached().isPointer(this.mokapotEnvPtr);
            assert !getUncached().isNull(this.mokapotEnvPtr);

            javaLibrary = loadJavaLibrary(props.bootLibraryPath());

        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        if (getJavaVersion().java9OrLater()) {
            stackWalk = new StackWalk();
        } else {
            stackWalk = null;
        }
    }

    @Override
    public EspressoContext getContext() {
        return jniEnv.getContext();
    }

    public @Pointer TruffleObject getJavaVM() {
        try {
            @Pointer
            TruffleObject ptr = (TruffleObject) getUncached().execute(getJavaVM, mokapotEnvPtr);
            assert getUncached().isPointer(ptr);
            return ptr;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("getJavaVM failed", e);
        }
    }

    private static Map<String, VMSubstitutor.Factory> buildVmMethods() {
        Map<String, VMSubstitutor.Factory> map = new HashMap<>();
        for (VMSubstitutor.Factory method : VMCollector.getCollector()) {
            assert !map.containsKey(method.methodName()) : "VmImpl for " + method + " already exists";
            map.put(method.methodName(), method);
        }
        return Collections.unmodifiableMap(map);
    }

    private static final Map<String, VMSubstitutor.Factory> vmMethods = buildVmMethods();

    public static VM create(JniEnv jniEnv) {
        return new VM(jniEnv);
    }

    private static final int JVM_CALLER_DEPTH = -1;

    public static int jvmCallerDepth() {
        return JVM_CALLER_DEPTH;
    }

    public static final int LOOKUP_VM_IMPL_PARAMETER_COUNT = 1;

    @TruffleBoundary
    public TruffleObject lookupVmImpl(String methodName) throws UnsupportedMessageException {
        VMSubstitutor.Factory m = vmMethods.get(methodName);
        // Dummy placeholder for unimplemented/unknown methods.
        if (m == null) {
            getLogger().log(Level.FINER, "Fetching unknown/unimplemented VM method: {0}", methodName);
            @Pointer
            TruffleObject errorClosure = getNativeAccess().createNativeClosure(
                            new Callback(1, new Callback.Function() {
                                @Override
                                public Object call(Object... args) {
                                    CompilerDirectives.transferToInterpreter();
                                    getLogger().log(Level.SEVERE, "Calling unimplemented VM method: {0}", methodName);
                                    throw EspressoError.unimplemented("VM method: " + methodName);
                                }
                            }), NativeType.VOID);
            nativeClosures.add(errorClosure);
            return errorClosure;
        }

        NativeSignature signature = m.jniNativeSignature();
        Callback target = vmMethodWrapper(m);
        TruffleObject nativeClosure = getNativeAccess().createNativeClosure(target, signature.getReturnType(), signature.getParameterTypes());
        nativeClosures.add(nativeClosure);
        return nativeClosure;
    }

    // Checkstyle: stop method name check

    // region VM methods

    @VmImpl
    @JniImpl
    // SVM windows has System.currentTimeMillis() BlackListed.
    @TruffleBoundary(allowInlining = true)
    public static long JVM_CurrentTimeMillis(@SuppressWarnings("unused") StaticObject ignored) {
        return System.currentTimeMillis();
    }

    @VmImpl
    @JniImpl
    public static long JVM_NanoTime(@SuppressWarnings("unused") StaticObject ignored) {
        return System.nanoTime();
    }

    @TruffleBoundary(allowInlining = true)
    @VmImpl
    @JniImpl
    public static int JVM_IHashCode(@Host(Object.class) StaticObject object) {
        // On SVM + Windows, the System.identityHashCode substitution triggers the blacklisted
        // methods (System.currentTimeMillis?) check.
        return System.identityHashCode(MetaUtil.maybeUnwrapNull(object));
    }

    @VmImpl
    @JniImpl
    public static void JVM_ArrayCopy(@SuppressWarnings("unused") @Host(Class/* <System> */.class) StaticObject ignored,
                    @Host(Object.class) StaticObject src, int srcPos, @Host(Object.class) StaticObject dest, int destPos, int length,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profile) {
        Target_java_lang_System.arraycopy(src, srcPos, dest, destPos, length, meta, profile);
    }

    private static Object readForeignArrayElement(StaticObject array, int index, InteropLibrary interop,
                    Meta meta, SubstitutionProfiler profiler, char exceptionBranch) {
        try {
            return interop.readArrayElement(array.rawForeignObject(), index);
        } catch (UnsupportedMessageException e) {
            profiler.profile(exceptionBranch);
            throw Meta.throwExceptionWithMessage(meta.getMeta().java_lang_ClassCastException, "The foreign object is not a readable array");
        } catch (InvalidArrayIndexException e) {
            profiler.profile(exceptionBranch);
            throw Meta.throwExceptionWithMessage(meta.java_lang_CloneNotSupportedException, "Foreign array length changed during clone");
        }
    }

    private static StaticObject cloneForeignArray(StaticObject array, Meta meta, InteropLibrary interop, ToEspressoNode toEspressoNode, SubstitutionProfiler profiler, char exceptionBranch) {
        assert array.isForeignObject();
        assert array.isArray();
        int length;
        try {
            long longLength = interop.getArraySize(array.rawForeignObject());
            if (longLength > Integer.MAX_VALUE) {
                profiler.profile(exceptionBranch);
                throw Meta.throwExceptionWithMessage(meta.java_lang_CloneNotSupportedException, "Cannot clone a foreign array whose length does not fit in int");
            }
            if (longLength < 0) {
                profiler.profile(exceptionBranch);
                throw Meta.throwExceptionWithMessage(meta.java_lang_NegativeArraySizeException, "Cannot clone a foreign array with negative length");
            }
            length = (int) longLength;
        } catch (UnsupportedMessageException e) {
            profiler.profile(exceptionBranch);
            throw Meta.throwExceptionWithMessage(meta.java_lang_CloneNotSupportedException, "Cannot clone a non-array foreign object as an array");
        }

        ArrayKlass arrayKlass = (ArrayKlass) array.getKlass();
        Klass componentType = arrayKlass.getComponentType();
        if (componentType.isPrimitive()) {
            try {
                switch (componentType.getJavaKind()) {
                    case Boolean:
                        boolean[] booleanArray = new boolean[length];
                        for (int i = 0; i < length; ++i) {
                            Object foreignElement = readForeignArrayElement(array, i, interop, meta, profiler, exceptionBranch);
                            booleanArray[i] = (boolean) toEspressoNode.execute(foreignElement, componentType);
                        }
                        return StaticObject.createArray(arrayKlass, booleanArray);
                    case Byte:
                        byte[] byteArray = new byte[length];
                        for (int i = 0; i < length; ++i) {
                            Object foreignElement = readForeignArrayElement(array, i, interop, meta, profiler, exceptionBranch);
                            byteArray[i] = (byte) toEspressoNode.execute(foreignElement, componentType);
                        }
                        return StaticObject.createArray(arrayKlass, byteArray);
                    case Short:
                        short[] shortArray = new short[length];
                        for (int i = 0; i < length; ++i) {
                            Object foreignElement = readForeignArrayElement(array, i, interop, meta, profiler, exceptionBranch);
                            shortArray[i] = (short) toEspressoNode.execute(foreignElement, componentType);
                        }
                        return StaticObject.createArray(arrayKlass, shortArray);
                    case Char:
                        char[] charArray = new char[length];
                        for (int i = 0; i < length; ++i) {
                            Object foreignElement = readForeignArrayElement(array, i, interop, meta, profiler, exceptionBranch);
                            charArray[i] = (char) toEspressoNode.execute(foreignElement, componentType);
                        }
                        return StaticObject.createArray(arrayKlass, charArray);
                    case Int:
                        int[] intArray = new int[length];
                        for (int i = 0; i < length; ++i) {
                            Object foreignElement = readForeignArrayElement(array, i, interop, meta, profiler, exceptionBranch);
                            intArray[i] = (int) toEspressoNode.execute(foreignElement, componentType);
                        }
                        return StaticObject.createArray(arrayKlass, intArray);
                    case Float:
                        float[] floatArray = new float[length];
                        for (int i = 0; i < length; ++i) {
                            Object foreignElement = readForeignArrayElement(array, i, interop, meta, profiler, exceptionBranch);
                            floatArray[i] = (float) toEspressoNode.execute(foreignElement, componentType);
                        }
                        return StaticObject.createArray(arrayKlass, floatArray);
                    case Long:
                        long[] longArray = new long[length];
                        for (int i = 0; i < length; ++i) {
                            Object foreignElement = readForeignArrayElement(array, i, interop, meta, profiler, exceptionBranch);
                            longArray[i] = (long) toEspressoNode.execute(foreignElement, componentType);
                        }
                        return StaticObject.createArray(arrayKlass, longArray);
                    case Double:
                        double[] doubleArray = new double[length];
                        for (int i = 0; i < length; ++i) {
                            Object foreignElement = readForeignArrayElement(array, i, interop, meta, profiler, exceptionBranch);
                            doubleArray[i] = (double) toEspressoNode.execute(foreignElement, componentType);
                        }
                        return StaticObject.createArray(arrayKlass, doubleArray);
                    case Object:
                    case Void:
                    case ReturnAddress:
                    case Illegal:
                        CompilerDirectives.transferToInterpreter();
                        throw EspressoError.shouldNotReachHere("Unexpected primitive kind: " + componentType.getJavaKind());
                }

            } catch (UnsupportedTypeException e) {
                profiler.profile(exceptionBranch);
                throw Meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast an element of a foreign array to the declared component type");
            }
        }
        StaticObject[] newArray = new StaticObject[length];
        for (int i = 0; i < length; ++i) {
            Object foreignElement = readForeignArrayElement(array, i, interop, meta, profiler, exceptionBranch);

            try {
                newArray[i] = (StaticObject) toEspressoNode.execute(foreignElement, componentType);
            } catch (UnsupportedTypeException e) {
                profiler.profile(exceptionBranch);
                throw Meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast an element of a foreign array to the declared component type");
            }
        }
        return StaticObject.createArray(arrayKlass, newArray);
    }

    @VmImpl
    @JniImpl
    public static @Host(Object.class) StaticObject JVM_Clone(@Host(Object.class) StaticObject self,
                    @GuestCall(target = "java_lang_ref_Finalizer_register") DirectCallNode finalizerRegister,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        assert StaticObject.notNull(self);
        char exceptionBranch = 3;
        if (self.isArray()) {
            // Arrays are always cloneable.
            if (self.isForeignObject()) {
                return cloneForeignArray(self, meta, InteropLibrary.getUncached(self.rawForeignObject()), ToEspressoNodeGen.getUncached(), profiler, exceptionBranch);
            }
            return self.copy();
        }

        if (self.isForeignObject()) {
            profiler.profile(exceptionBranch);
            throw Meta.throwExceptionWithMessage(meta.java_lang_CloneNotSupportedException, "Clone not supported for non-array foreign objects");
        }

        if (!meta.java_lang_Cloneable.isAssignableFrom(self.getKlass())) {
            profiler.profile(0);
            throw Meta.throwException(meta.java_lang_CloneNotSupportedException);
        }

        if (InterpreterToVM.instanceOf(self, meta.java_lang_ref_Reference)) {
            // HotSpot 8202260: The semantics of cloning a Reference object is not clearly defined.
            // In addition, it is questionable whether it should be supported due to its tight
            // interaction with garbage collector.
            //
            // The reachability state of a Reference object may change during GC reference
            // processing. The referent may have been cleared when it reaches its reachability
            // state. On the other hand, it may be enqueued or pending for enqueuing. Cloning a
            // Reference object with a referent that is unreachable but not yet cleared might mean
            // to resurrect the referent. A cloned enqueued Reference object will never be enqueued.
            //
            // A Reference object cannot be meaningfully cloned.

            // Non-strong references are not cloneable.
            if (InterpreterToVM.instanceOf(self, meta.java_lang_ref_WeakReference) //
                            || InterpreterToVM.instanceOf(self, meta.java_lang_ref_SoftReference) //
                            || InterpreterToVM.instanceOf(self, meta.java_lang_ref_FinalReference) //
                            || InterpreterToVM.instanceOf(self, meta.java_lang_ref_PhantomReference)) {
                profiler.profile(1);
                throw Meta.throwExceptionWithMessage(meta.java_lang_CloneNotSupportedException, self.getKlass().getName().toString());
            }
        }

        final StaticObject clone = self.copy();

        // If the original object is finalizable, so is the copy.
        assert self.getKlass() instanceof ObjectKlass;
        if (((ObjectKlass) self.getKlass()).hasFinalizer()) {
            profiler.profile(2);
            finalizerRegister.call(clone);
        }

        return clone;
    }

    public Callback vmMethodWrapper(VMSubstitutor.Factory m) {
        int extraArg = (m.isJni()) ? 1 : 0;
        return new Callback(m.parameterCount() + extraArg, new Callback.Function() {
            @CompilerDirectives.CompilationFinal private VMSubstitutor subst = null;

            @Override
            public Object call(Object... args) {
                boolean isJni = m.isJni();
                try {
                    if (subst == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        subst = m.create(getMeta());
                    }
                    return subst.invoke(VM.this, args);
                } catch (EspressoException | StackOverflowError | OutOfMemoryError e) {
                    if (isJni) {
                        // This will most likely SOE again. Nothing we can do about that
                        // unfortunately.
                        EspressoException wrappedError = (e instanceof EspressoException)
                                        ? (EspressoException) e
                                        : (e instanceof StackOverflowError)
                                                        ? getContext().getStackOverflow()
                                                        : getContext().getOutOfMemory();
                        jniEnv.getThreadLocalPendingException().set(wrappedError.getExceptionObject());
                        return defaultValue(m.returnType());
                    }
                    throw e;
                }
            }
        });
    }

    @VmImpl
    @JniImpl
    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, .notifyAll is just forwarded from the guest.")
    public void JVM_MonitorNotifyAll(@Host(Object.class) StaticObject self, @InjectProfile SubstitutionProfiler profiler) {
        try {
            InterpreterToVM.monitorNotifyAll(self.getLock());
        } catch (IllegalMonitorStateException e) {
            profiler.profile(0);
            throw Meta.throwException(getMeta().java_lang_IllegalMonitorStateException);
        }
    }

    @VmImpl
    @JniImpl
    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, .notify is just forwarded from the guest.")
    public void JVM_MonitorNotify(@Host(Object.class) StaticObject self, @InjectProfile SubstitutionProfiler profiler) {
        try {
            InterpreterToVM.monitorNotify(self.getLock());
        } catch (IllegalMonitorStateException e) {
            profiler.profile(0);
            throw Meta.throwException(getMeta().java_lang_IllegalMonitorStateException);
        }
    }

    @VmImpl
    @JniImpl
    @TruffleBoundary
    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, .wait is just forwarded from the guest.")
    public void JVM_MonitorWait(@Host(Object.class) StaticObject self, long timeout, @InjectProfile SubstitutionProfiler profiler) {

        EspressoContext context = getContext();
        StaticObject currentThread = context.getCurrentThread();
        try {
            Target_java_lang_Thread.fromRunnable(currentThread, getMeta(), (timeout > 0 ? State.TIMED_WAITING : State.WAITING));
            if (context.EnableManagement) {
                // Locks bookkeeping.
                currentThread.setHiddenField(getMeta().HIDDEN_THREAD_BLOCKED_OBJECT, self);
                Target_java_lang_Thread.incrementThreadCounter(currentThread, getMeta().HIDDEN_THREAD_WAITED_COUNT);
            }
            context.getJDWPListener().monitorWait(self, timeout);
            boolean timedOut = !InterpreterToVM.monitorWait(self.getLock(), timeout);
            context.getJDWPListener().monitorWaited(self, timedOut);
        } catch (InterruptedException e) {
            profiler.profile(0);
            Target_java_lang_Thread.setInterrupt(currentThread, false);
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_InterruptedException, e.getMessage());
        } catch (IllegalMonitorStateException e) {
            profiler.profile(1);
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalMonitorStateException, e.getMessage());
        } catch (IllegalArgumentException e) {
            profiler.profile(2);
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, e.getMessage());
        } finally {
            if (context.EnableManagement) {
                currentThread.setHiddenField(getMeta().HIDDEN_THREAD_BLOCKED_OBJECT, null);
            }
            Target_java_lang_Thread.toRunnable(currentThread, getMeta(), State.RUNNABLE);
        }
    }

    @VmImpl
    public static boolean JVM_IsNaN(double d) {
        return Double.isNaN(d);
    }

    @VmImpl
    @TruffleBoundary
    public static boolean JVM_SupportsCX8() {
        try {
            Class<?> klass = Class.forName("java.util.concurrent.atomic.AtomicLong");
            java.lang.reflect.Field field = klass.getDeclaredField("VM_SUPPORTS_LONG_CAS");
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @VmImpl
    @JniImpl
    @TruffleBoundary
    // TODO(peterssen): @Type annotaion only for readability purposes.
    public @Host(String.class) StaticObject JVM_InternString(@Host(String.class) StaticObject self) {
        return getInterpreterToVM().intern(self);
    }

    // endregion VM methods

    // region JNI Invocation Interface
    @VmImpl
    public int DestroyJavaVM() {
        int result = DetachCurrentThread();
        try {
            EspressoContext context = getContext();
            context.destroyVM(!context.ExitHost);
        } catch (EspressoExitException exit) {
            // expected
        }
        return result;
    }

    /*
    @formatter:off
    struct JavaVMAttachArgs {
        0      |     4     jint version;
     XXX  4-byte hole
        8      |     8     char *name;
       16      |     8     jobject group;
    }
    total size (bytes):   24
    @formatter:on
     */

    @VmImpl
    @TruffleBoundary
    public int AttachCurrentThread(@Pointer TruffleObject vmPtr_, @Pointer TruffleObject penvPtr, @Pointer TruffleObject argsPtr) {
        assert interopAsPointer(getJavaVM()) == interopAsPointer(vmPtr_);
        return attachCurrentThread(penvPtr, argsPtr, false);
    }

    private int attachCurrentThread(@SuppressWarnings("unused") @Pointer TruffleObject penvPtr, @Pointer TruffleObject argsPtr, boolean daemon) {
        LongBuffer buf = directByteBuffer(argsPtr, 8, JavaKind.Long).asLongBuffer();
        int version = (int) buf.get(0);
        @SuppressWarnings("unused")
        long namePtr = buf.get(1);
        long groupHandle = buf.get(2);
        StaticObject group = null;
        String name = null;
        if (JniVersion.isSupported(version, getContext().getJavaVersion())) {
            group = getHandles().get(Math.toIntExact(groupHandle));
            name = fromUTF8Ptr(namePtr);
        } else {
            getLogger().warning(String.format("AttachCurrentThread with unsupported JavaVMAttachArgs version: 0x%08x", version));
        }
        StaticObject thread = getContext().createThread(Thread.currentThread(), group, name);
        if (daemon) {
            getMeta().java_lang_Thread_daemon.set(thread, true);
        }
        return JNI_OK;
    }

    @VmImpl
    @TruffleBoundary
    public int DetachCurrentThread() {
        EspressoContext context = getContext();
        StaticObject currentThread = context.getCurrentThread();
        if (currentThread == null) {
            return JNI_OK;
        }
        getLogger().fine(() -> {
            Meta meta = getMeta();
            String guestName = Target_java_lang_Thread.getThreadName(meta, currentThread);
            return "DetachCurrentThread: " + guestName;
        });
        // HotSpot will wait forever if the current VM this thread was attached to has exited
        // Should we reproduce this behaviour?

        Method lastJavaMethod = Truffle.getRuntime().iterateFrames(
                        new FrameInstanceVisitor<Method>() {
                            @Override
                            public Method visitFrame(FrameInstance frameInstance) {
                                Method method = getMethodFromFrame(frameInstance);
                                if (method != null && method.getContext() == context) {
                                    return method;
                                }
                                return null;
                            }
                        });
        if (lastJavaMethod != null) {
            // this thread is executing
            return JNI_ERR;
        }
        StaticObject pendingException = jniEnv.getPendingException();
        jniEnv.clearPendingException();

        try {
            Meta meta = context.getMeta();
            if (pendingException != null) {
                try {
                    meta.java_lang_Thread_dispatchUncaughtException.invokeDirect(currentThread, pendingException);
                } catch (EspressoException e) {
                    String exception = e.getExceptionObject().getKlass().getExternalName();
                    String threadName = Target_java_lang_Thread.getThreadName(meta, currentThread);
                    context.getLogger().warning(String.format("Exception: %s thrown from the UncaughtExceptionHandler in thread \"%s\"", exception, threadName));
                } catch (EspressoExitException e) {
                    // ignore
                }
            }

            Target_java_lang_Thread.terminate(currentThread, meta);
        } catch (EspressoExitException e) {
            // ignore
        }

        return JNI_OK;
    }

    /**
     * <h3>jint GetEnv(JavaVM *vm, void **env, jint version);</h3>
     *
     * @param vmPtr_ The virtual machine instance from which the interface will be retrieved.
     * @param envPtr pointer to the location where the JNI interface pointer for the current thread
     *            will be placed.
     * @param version The requested JNI version.
     *
     * @return If the current thread is not attached to the VM, sets *env to NULL, and returns
     *         JNI_EDETACHED. If the specified version is not supported, sets *env to NULL, and
     *         returns JNI_EVERSION. Otherwise, sets *env to the appropriate interface, and returns
     *         JNI_OK.
     */
    @VmImpl
    @TruffleBoundary
    public int GetEnv(@Pointer TruffleObject vmPtr_, @Pointer TruffleObject envPtr, int version) {
        assert interopAsPointer(getJavaVM()) == interopAsPointer(vmPtr_);
        StaticObject currentThread = getContext().getGuestThreadFromHost(Thread.currentThread());
        if (currentThread == null) {
            return JNI_EDETACHED;
        }
        if (JniVersion.isSupported(version, getContext().getJavaVersion())) {
            LongBuffer buf = directByteBuffer(envPtr, 1, JavaKind.Long).asLongBuffer();
            buf.put(interopAsPointer(jniEnv.getNativePointer()));
            return JNI_OK;
        }
        return JNI_EVERSION;
    }

    @VmImpl
    @TruffleBoundary
    public int AttachCurrentThreadAsDaemon(@Pointer TruffleObject vmPtr_, @Pointer TruffleObject penvPtr, @Pointer TruffleObject argsPtr) {
        assert interopAsPointer(getJavaVM()) == interopAsPointer(vmPtr_);
        return attachCurrentThread(penvPtr, argsPtr, true);
    }

    // endregion JNI Invocation Interface

    public static class StackElement {
        /**
         * @see StackTraceElement#isNativeMethod()
         */
        public static int NATIVE_BCI = -2;
        /**
         * @see StackTraceElement#toString()
         */
        public static int UNKNOWN_BCI = -1;

        private final Method m;
        private final int bci;

        public StackElement(Method m, int bci) {
            this.m = m;
            this.bci = bci;
        }

        public Method getMethod() {
            return m;
        }

        public int getBCI() {
            return bci;
        }
    }

    public static class StackTrace {
        public static final StackTrace EMPTY_STACK_TRACE = new StackTrace(0);

        public StackElement[] trace;
        public int size;
        public int capacity;

        public StackTrace() {
            this(DEFAULT_STACK_SIZE);
        }

        private StackTrace(int size) {
            this.trace = new StackElement[size];
            this.capacity = size;
            this.size = 0;
        }

        public void add(StackElement e) {
            if (size < capacity) {
                trace[size++] = e;
            } else {
                trace = Arrays.copyOf(trace, capacity <<= 1);
                trace[size++] = e;
            }
        }
    }

    @VmImpl
    @JniImpl
    public @Host(Throwable.class) StaticObject JVM_FillInStackTrace(@Host(Throwable.class) StaticObject self, @SuppressWarnings("unused") int dummy) {
        return InterpreterToVM.fillInStackTrace(self, false, getMeta());
    }

    @VmImpl
    @JniImpl
    public int JVM_GetStackTraceDepth(@Host(Throwable.class) StaticObject self) {
        Meta meta = getMeta();
        StackTrace frames = EspressoException.getFrames(self, meta);
        if (frames == null) {
            return 0;
        }
        return frames.size;
    }

    @VmImpl
    @JniImpl
    public @Host(StackTraceElement.class) StaticObject JVM_GetStackTraceElement(@Host(Throwable.class) StaticObject self, int index,
                    @GuestCall(target = "java_lang_StackTraceElement_init") DirectCallNode stackTraceElementInit,
                    @InjectProfile SubstitutionProfiler profiler) {
        Meta meta = getMeta();
        if (index < 0) {
            profiler.profile(0);
            throw Meta.throwException(meta.java_lang_IndexOutOfBoundsException);
        }
        StaticObject ste = meta.java_lang_StackTraceElement.allocateInstance();
        StackTrace frames = EspressoException.getFrames(self, meta);
        if (frames == null || index >= frames.size) {
            profiler.profile(1);
            throw Meta.throwException(meta.java_lang_IndexOutOfBoundsException);
        }
        StackElement stackElement = frames.trace[index];
        Method method = stackElement.getMethod();
        if (method == null) {
            return StaticObject.NULL;
        }
        int bci = stackElement.getBCI();

        stackTraceElementInit.call(
                        /* this */ ste,
                        /* declaringClass */ meta.toGuestString(MetaUtil.internalNameToJava(method.getDeclaringKlass().getType().toString(), true, true)),
                        /* methodName */ meta.toGuestString(method.getName()),
                        /* fileName */ meta.toGuestString(method.getSourceFile()),
                        /* lineNumber */ method.bciToLineNumber(bci));

        return ste;
    }

    private static void checkTag(ConstantPool pool, int index, ConstantPool.Tag expected, Meta meta, SubstitutionProfiler profiler) {
        ConstantPool.Tag target = pool.tagAt(index);
        if (target != expected) {
            profiler.profile(0);
            throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Wrong type at constant pool index");
        }
    }

    // region ConstantPool

    @VmImpl
    @JniImpl
    public static int JVM_ConstantPoolGetSize(@SuppressWarnings("unused") StaticObject unused, StaticObject jcpool) {
        return jcpool.getMirrorKlass().getConstantPool().length();
    }

    @VmImpl
    @JniImpl
    public static @Host(Class.class) StaticObject JVM_ConstantPoolGetClassAt(@SuppressWarnings("unused") StaticObject unused, @Host(Object.class) StaticObject jcpool, int index,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.CLASS, meta, profiler);
        return ((RuntimeConstantPool) jcpool.getMirrorKlass().getConstantPool()).resolvedKlassAt(null, index).mirror();
    }

    @VmImpl
    @JniImpl
    public static double JVM_ConstantPoolGetDoubleAt(@SuppressWarnings("unused") StaticObject unused, @Host(Object.class) StaticObject jcpool, int index,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.DOUBLE, meta, profiler);
        return jcpool.getMirrorKlass().getConstantPool().doubleAt(index);
    }

    @VmImpl
    @JniImpl
    public static float JVM_ConstantPoolGetFloatAt(@SuppressWarnings("unused") StaticObject unused, @Host(Object.class) StaticObject jcpool, int index,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.FLOAT, meta, profiler);
        return jcpool.getMirrorKlass().getConstantPool().floatAt(index);
    }

    @VmImpl
    @JniImpl
    public static @Host(String.class) StaticObject JVM_ConstantPoolGetStringAt(@SuppressWarnings("unused") StaticObject unused, @Host(Object.class) StaticObject jcpool, int index,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.STRING, meta, profiler);
        return ((RuntimeConstantPool) jcpool.getMirrorKlass().getConstantPool()).resolvedStringAt(index);
    }

    @VmImpl
    @JniImpl
    public @Host(String.class) StaticObject JVM_ConstantPoolGetUTF8At(@SuppressWarnings("unused") StaticObject unused, StaticObject jcpool, int index,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.UTF8, meta, profiler);
        return getMeta().toGuestString(jcpool.getMirrorKlass().getConstantPool().symbolAt(index).toString());
    }

    @VmImpl
    @JniImpl
    public static int JVM_ConstantPoolGetIntAt(@SuppressWarnings("unused") StaticObject unused, StaticObject jcpool, int index,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.INTEGER, meta, profiler);
        return jcpool.getMirrorKlass().getConstantPool().intAt(index);
    }

    @VmImpl
    @JniImpl
    public static long JVM_ConstantPoolGetLongAt(@SuppressWarnings("unused") StaticObject unused, StaticObject jcpool, int index,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.LONG, meta, profiler);
        return jcpool.getMirrorKlass().getConstantPool().longAt(index);
    }

    // endregion ConstantPool

    @VmImpl
    @JniImpl
    @TruffleBoundary
    public @Host(Class.class) StaticObject JVM_DefineClass(@Pointer TruffleObject namePtr, @Host(ClassLoader.class) StaticObject loader, @Pointer TruffleObject bufPtr, int len,
                    @Host(ProtectionDomain.class) StaticObject pd, @InjectProfile SubstitutionProfiler profiler) {
        String name = interopPointerToString(namePtr);
        ByteBuffer buf = JniEnv.directByteBuffer(bufPtr, len, JavaKind.Byte);
        final byte[] bytes = new byte[len];
        buf.get(bytes);

        Symbol<Type> type = null;
        if (name != null) {
            String internalName = name;
            if (!name.startsWith("[")) {
                // Force 'L' type.
                internalName = "L" + name + ";";
            }
            if (!Validation.validTypeDescriptor(ByteSequence.create(internalName), false)) {
                profiler.profile(0);
                throw Meta.throwExceptionWithMessage(getMeta().java_lang_NoClassDefFoundError, name);
            }
            type = getTypes().fromClassGetName(internalName);
        }

        StaticObject clazz = getContext().getRegistries().defineKlass(type, bytes, loader).mirror();
        assert clazz != null;
        assert pd != null;
        clazz.setHiddenField(getMeta().HIDDEN_PROTECTION_DOMAIN, pd);
        return clazz;
    }

    @VmImpl
    @JniImpl
    public @Host(Class.class) StaticObject JVM_DefineClassWithSource(@Pointer TruffleObject namePtr, @Host(ClassLoader.class) StaticObject loader, @Pointer TruffleObject bufPtr, int len,
                    @Host(ProtectionDomain.class) StaticObject pd, @SuppressWarnings("unused") @Pointer TruffleObject source, @InjectProfile SubstitutionProfiler profiler) {
        // FIXME(peterssen): Source is ignored.
        return JVM_DefineClass(namePtr, loader, bufPtr, len, pd, profiler);
    }

    @VmImpl
    @JniImpl
    public @Host(Class.class) StaticObject JVM_FindLoadedClass(@Host(ClassLoader.class) StaticObject loader, @Host(String.class) StaticObject name) {
        Symbol<Type> type = getTypes().fromClassGetName(getMeta().toHostString(name));
        // HotSpot skips reflection (DelegatingClassLoader) class loaders.
        Klass klass = getRegistries().findLoadedClass(type, nonReflectionClassLoader(loader));
        if (klass == null) {
            return StaticObject.NULL;
        }
        return klass.mirror();
    }

    private final ConcurrentHashMap<Long, @Pointer TruffleObject> handle2Lib = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, @Pointer TruffleObject> handle2Sym = new ConcurrentHashMap<>();

    // region Library support

    @VmImpl
    @TruffleBoundary
    public @Pointer TruffleObject JVM_LoadLibrary(@Pointer TruffleObject namePtr) {
        String name = interopPointerToString(namePtr);
        getLogger().fine(String.format("JVM_LoadLibrary: '%s'", name));
        try {
            @Pointer
            TruffleObject lib = getNativeAccess().loadLibrary(Paths.get(name));
            if (lib == null) {
                throw Meta.throwExceptionWithMessage(getMeta().java_lang_UnsatisfiedLinkError, name);
            }
            java.lang.reflect.Field f = lib.getClass().getDeclaredField("handle");
            f.setAccessible(true);
            long handle = (long) f.get(lib);
            getLogger().fine(String.format("JVM_LoadLibrary: Successfully loaded '%s' with handle %x", name, handle));
            handle2Lib.put(handle, lib);
            return RawPointer.create(handle);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @VmImpl
    @TruffleBoundary
    public void JVM_UnloadLibrary(@SuppressWarnings("unused") @Pointer TruffleObject handle) {
        // TODO(peterssen): Do unload the library.
        getLogger().severe(String.format("JVM_UnloadLibrary: %x was not unloaded!", interopAsPointer(handle)));
    }

    @VmImpl
    @TruffleBoundary
    @SuppressFBWarnings(value = "AT_OPERATION_SEQUENCE_ON_CONCURRENT_ABSTRACTION", justification = "benign race")
    public @Pointer TruffleObject JVM_FindLibraryEntry(@Pointer TruffleObject libraryPtr, @Pointer TruffleObject namePtr) {
        String name = interopPointerToString(namePtr);
        long nativePtr = interopAsPointer(libraryPtr);
        TruffleObject library = handle2Lib.get(nativePtr);
        if (library == null) {
            if (nativePtr == rtldDefaultValue) {
                if (!safeRTLDDefaultLookup) {
                    getLogger().warning("JVM_FindLibraryEntry from default/global namespace is not supported in TruffleNFIIsolatedNamespace mode: " + name);
                    return RawPointer.nullInstance();
                }
                library = NativeLibrary.loadDefaultLibrary();
                handle2Lib.put(nativePtr, library);
            } else {
                getLogger().warning("JVM_FindLibraryEntry with unknown handle (" + libraryPtr + " / " + Long.toHexString(nativePtr) + "): " + name);
                return RawPointer.nullInstance();
            }
        }
        try {
            TruffleObject function = getNativeAccess().lookupSymbol(library, name);
            if (function == null) {
                return RawPointer.nullInstance(); // not found
            }
            long handle = getUncached().asPointer(function);
            handle2Sym.put(handle, function);
            return function;
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    // endregion Library support
    @VmImpl
    public boolean JVM_IsSupportedJNIVersion(int version) {
        return JniVersion.isSupported(version, getJavaVersion());
    }

    @VmImpl
    public int JVM_GetInterfaceVersion() {
        if (getJavaVersion().java8OrEarlier()) {
            return JniEnv.JVM_INTERFACE_VERSION_8;
        } else {
            return JniEnv.JVM_INTERFACE_VERSION_11;
        }
    }

    public void dispose() {
        assert !getUncached().isNull(mokapotEnvPtr) : "Mokapot already disposed";
        try {

            if (getContext().EnableManagement) {
                if (managementPtr != null) {
                    getUncached().execute(disposeManagementContext, managementPtr, managementVersion, RawPointer.nullInstance());
                    this.managementPtr = null;
                    this.managementVersion = 0;
                }
            } else {
                assert managementPtr == null;
            }

            getUncached().execute(disposeMokapotContext, mokapotEnvPtr, RawPointer.nullInstance());
            this.mokapotEnvPtr = RawPointer.nullInstance();
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot dispose Espresso libjvm (mokapot).");
        }
        assert getUncached().isNull(mokapotEnvPtr);
    }

    @VmImpl
    @TruffleBoundary(allowInlining = true)
    public static long JVM_TotalMemory() {
        // TODO(peterssen): What to report here?
        return Runtime.getRuntime().totalMemory();
    }

    @VmImpl
    @TruffleBoundary(allowInlining = true)
    public static long JVM_MaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    @VmImpl
    @TruffleBoundary(allowInlining = true)
    public static void JVM_GC() {
        System.gc();
    }

    @VmImpl
    public void JVM_Halt(int code) {
        getContext().doExit(code);
    }

    @VmImpl
    public void JVM_Exit(int code) {
        getContext().doExit(code);
        // System.exit(code);
        // Unlike Halt, runs finalizers
    }

    @VmImpl
    @JniImpl
    @TruffleBoundary
    public @Host(Properties.class) StaticObject JVM_InitProperties(@Host(Properties.class) StaticObject properties) {
        Method setProperty = properties.getKlass().lookupMethod(Name.setProperty, Signature.Object_String_String);

        OptionValues options = getContext().getEnv().getOptions();

        // Set user-defined system properties.
        for (Map.Entry<String, String> entry : options.get(EspressoOptions.Properties).entrySet()) {
            setProperty.invokeWithConversions(properties, entry.getKey(), entry.getValue());
        }

        EspressoProperties props = getContext().getVmProperties();

        // Espresso uses VM properties, to ensure consistency the user-defined properties (that may
        // differ in some cases) are overwritten.
        setProperty.invokeWithConversions(properties, "java.class.path", stringify(props.classpath()));
        setProperty.invokeWithConversions(properties, "java.home", props.javaHome().toString());
        setProperty.invokeWithConversions(properties, "sun.boot.class.path", stringify(props.bootClasspath()));
        setProperty.invokeWithConversions(properties, "java.library.path", stringify(props.javaLibraryPath()));
        setProperty.invokeWithConversions(properties, "sun.boot.library.path", stringify(props.bootLibraryPath()));
        setProperty.invokeWithConversions(properties, "java.ext.dirs", stringify(props.extDirs()));

        // Modules properties.
        if (getJavaVersion().modulesEnabled()) {
            setPropertyIfExists(properties, setProperty, "jdk.module.main", getModuleMain(options));
            setPropertyIfExists(properties, setProperty, "jdk.module.path", stringify(options.get(EspressoOptions.ModulePath)));
            setNumberedProperty(setProperty, properties, "jdk.module.addreads", options.get(EspressoOptions.AddReads));
            setNumberedProperty(setProperty, properties, "jdk.module.addexports", options.get(EspressoOptions.AddExports));
            setNumberedProperty(setProperty, properties, "jdk.module.addopens", options.get(EspressoOptions.AddOpens));
            setNumberedProperty(setProperty, properties, "jdk.module.addmods", options.get(EspressoOptions.AddModules));
        }

        // Applications expect different formats e.g. 1.8 vs. 11
        String specVersion = getJavaVersion().java8OrEarlier()
                        ? "1." + getJavaVersion()
                        : getJavaVersion().toString();

        // Set VM information.
        setProperty.invokeWithConversions(properties, "java.vm.specification.version", specVersion);
        setProperty.invokeWithConversions(properties, "java.vm.specification.name", EspressoLanguage.VM_SPECIFICATION_NAME);
        setProperty.invokeWithConversions(properties, "java.vm.specification.vendor", EspressoLanguage.VM_SPECIFICATION_VENDOR);
        setProperty.invokeWithConversions(properties, "java.vm.version", specVersion + "-" + EspressoLanguage.VM_VERSION);
        setProperty.invokeWithConversions(properties, "java.vm.name", EspressoLanguage.VM_NAME);
        setProperty.invokeWithConversions(properties, "java.vm.vendor", EspressoLanguage.VM_VENDOR);
        setProperty.invokeWithConversions(properties, "java.vm.info", EspressoLanguage.VM_INFO);

        setProperty.invokeWithConversions(properties, "sun.nio.MaxDirectMemorySize", Long.toString(options.get(EspressoOptions.MaxDirectMemorySize)));

        return properties;
    }

    public static void setPropertyIfExists(@Host(Properties.class) StaticObject properties, Method setProperty, String propertyName, String value) {
        if (value != null && value.length() > 0) {
            setProperty.invokeWithConversions(properties, propertyName, value);
        }
    }

    private static String getModuleMain(OptionValues options) {
        String module = options.get(EspressoOptions.Module);
        if (module.length() > 0) {
            int slash = module.indexOf('/');
            if (slash != -1) {
                module = module.substring(0, slash);
            }
        }
        return module;
    }

    private static void setNumberedProperty(Method setProperty, StaticObject properties, String property, List<String> values) {
        int count = 0;
        for (String value : values) {
            setProperty.invokeWithConversions(properties, property + "." + count++, value);
        }
    }

    @VmImpl
    @JniImpl
    public int JVM_GetArrayLength(@Host(Object.class) StaticObject array, @InjectProfile SubstitutionProfiler profiler) {
        try {
            return Array.getLength(MetaUtil.unwrapArrayOrNull(array));
        } catch (IllegalArgumentException e) {
            profiler.profile(0);
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, e.getMessage());
        } catch (NullPointerException e) {
            profiler.profile(1);
            throw getMeta().throwNullPointerException();
        }
    }

    @SuppressWarnings("unused")
    @VmImpl
    @JniImpl
    public static boolean JVM_DesiredAssertionStatus(@Host(Class.class) StaticObject unused, @Host(Class.class) StaticObject cls) {
        // TODO(peterssen): Assertions are always disabled, use the VM arguments.
        return false;
    }

    /**
     * Returns the caller frame, 'depth' levels up. If securityStackWalk is true, some Espresso
     * frames are skipped according to {@link #isIgnoredBySecurityStackWalk}.
     */
    @TruffleBoundary
    private static FrameInstance getCallerFrame(int depth, boolean securityStackWalk, Meta meta) {
        if (depth == JVM_CALLER_DEPTH) {
            return getCallerFrame(1, securityStackWalk, meta);
        }
        assert depth >= 0;

        // Ignores non-Espresso frames.
        //
        // The call stack at this point looks something like this:
        //
        // [0] [ current frame e.g. AccessController.doPrivileged, Reflection.getCallerClass ]
        // [.] [ (skipped intermediate frames) ]
        // ...
        // [n] [ caller ]
        FrameInstance callerFrame = Truffle.getRuntime().iterateFrames(
                        new FrameInstanceVisitor<FrameInstance>() {
                            private int n;

                            @Override
                            public FrameInstance visitFrame(FrameInstance frameInstance) {
                                Method m = getMethodFromFrame(frameInstance);
                                if (m != null) {
                                    if (!securityStackWalk || !isIgnoredBySecurityStackWalk(m, meta)) {
                                        if (n == depth) {
                                            return frameInstance;
                                        }
                                        ++n;
                                    }
                                }
                                return null;
                            }
                        });

        if (callerFrame != null) {
            return callerFrame;
        }

        throw EspressoError.shouldNotReachHere(String.format("Caller frame not found at depth %d", depth));
    }

    @TruffleBoundary
    public static EspressoRootNode getEspressoRootFromFrame(FrameInstance frameInstance) {
        if (frameInstance.getCallTarget() instanceof RootCallTarget) {
            RootCallTarget callTarget = (RootCallTarget) frameInstance.getCallTarget();
            RootNode rootNode = callTarget.getRootNode();
            if (rootNode instanceof EspressoRootNode) {
                return ((EspressoRootNode) rootNode);
            }
        }
        return null;
    }

    @TruffleBoundary
    public static Method getMethodFromFrame(FrameInstance frameInstance) {
        // TODO this should take a context as argument and only return the method if the context
        // matches
        EspressoRootNode root = getEspressoRootFromFrame(frameInstance);
        if (root != null) {
            return root.getMethod();
        }
        return null;
    }

    @VmImpl
    @JniImpl
    public @Host(Class.class) StaticObject JVM_GetCallerClass(int depth,
                    @InjectProfile SubstitutionProfiler profiler) {
        // HotSpot comment:
        // Pre-JDK 8 and early builds of JDK 8 don't have a CallerSensitive annotation; or
        // sun.reflect.Reflection.getCallerClass with a depth parameter is provided
        // temporarily for existing code to use until a replacement API is defined.
        if (depth != JVM_CALLER_DEPTH) {
            FrameInstance callerFrame = getCallerFrame(depth, true, getMeta());
            if (callerFrame != null) {
                Method callerMethod = getMethodFromFrame(callerFrame);
                if (callerMethod != null) {
                    return callerMethod.getDeclaringKlass().mirror();
                }
            }
            // Not found.
            return StaticObject.NULL;
        }

        // Getting the class of the caller frame.
        //
        // The call stack at this point looks something like this:
        //
        // [0] [ @CallerSensitive public sun.reflect.Reflection.getCallerClass ]
        // [1] [ @CallerSensitive API.method ]
        // [.] [ (skipped intermediate frames) ]
        // [n] [ caller ]
        Meta meta = getMeta();
        StaticObject[] exception = new StaticObject[]{null};
        Method callerMethod = Truffle.getRuntime().iterateFrames(
                        new FrameInstanceVisitor<Method>() {
                            private int depth = 0;

                            @SuppressWarnings("fallthrough")
                            @Override
                            public Method visitFrame(FrameInstance frameInstance) {
                                Method method = getMethodFromFrame(frameInstance);
                                if (method != null) {
                                    switch (depth) {
                                        case 0:
                                            // This must only be called from
                                            // Reflection.getCallerClass.
                                            if (method != meta.sun_reflect_Reflection_getCallerClass) {
                                                exception[0] = Meta.initExceptionWithMessage(meta.java_lang_InternalError, "JVM_GetCallerClass must only be called from Reflection.getCallerClass");
                                                return /* ignore */ method;
                                            }
                                            // fall-through
                                        case 1:
                                            // Frame 0 and 1 must be caller sensitive.
                                            if ((method.getModifiers() & ACC_CALLER_SENSITIVE) == 0) {
                                                exception[0] = Meta.initExceptionWithMessage(meta.java_lang_InternalError, "CallerSensitive annotation expected at frame " + depth);
                                                return /* ignore */ method;
                                            }
                                            break;
                                        default:
                                            if (!isIgnoredBySecurityStackWalk(method, meta)) {
                                                return method;
                                            }
                                    }
                                    ++depth;
                                }
                                return null;
                            }
                        });

        // InternalError was recorded.
        StaticObject internalError = exception[0];
        if (internalError != null) {
            profiler.profile(0);
            assert InterpreterToVM.instanceOf(internalError, meta.java_lang_InternalError);
            throw Meta.throwException(internalError);
        }

        if (callerMethod == null) {
            return StaticObject.NULL;
        }

        return callerMethod.getDeclaringKlass().mirror();
    }

    @VmImpl
    @JniImpl
    public @Host(Class[].class) StaticObject JVM_GetClassContext() {
        // TODO(garcia) This must only be called from SecurityManager.getClassContext
        ArrayList<StaticObject> result = new ArrayList<>();
        Truffle.getRuntime().iterateFrames(
                        new FrameInstanceVisitor<Object>() {
                            @Override
                            public Object visitFrame(FrameInstance frameInstance) {
                                Method m = getMethodFromFrame(frameInstance);
                                if (m != null && !isIgnoredBySecurityStackWalk(m, getMeta()) && !m.isNative()) {
                                    result.add(m.getDeclaringKlass().mirror());
                                }
                                return null;
                            }
                        });
        return StaticObject.createArray(getMeta().java_lang_Class_array, result.toArray(StaticObject.EMPTY_ARRAY));
    }

    private static boolean isIgnoredBySecurityStackWalk(Method m, Meta meta) {
        Klass holderKlass = m.getDeclaringKlass();
        if (holderKlass == meta.java_lang_reflect_Method && m.getName() == Name.invoke) {
            return true;
        }
        if (meta.sun_reflect_MethodAccessorImpl.isAssignableFrom(holderKlass)) {
            return true;
        }
        if (MethodHandleIntrinsics.isMethodHandleIntrinsic(m) || (m.getModifiers() & ACC_LAMBDA_FORM_COMPILED) != 0) {
            return true;
        }
        return false;
    }

    private boolean isAuthorized(StaticObject context, Klass klass) {
        if (!StaticObject.isNull(getMeta().java_lang_System.getStatics().getField(getMeta().java_lang_System_securityManager))) {
            if (getMeta().java_security_ProtectionDomain_impliesCreateAccessControlContext == null) {
                return true;
            }
            if ((boolean) getMeta().java_security_AccessControlContext_isAuthorized.get(context)) {
                return true;
            }
            StaticObject pd = Target_java_lang_Class.getProtectionDomain0(klass.mirror(), getMeta());
            if (pd != StaticObject.NULL) {
                return (boolean) getMeta().java_security_ProtectionDomain_impliesCreateAccessControlContext.invokeDirect(pd);
            }
        }
        return true;
    }

    private @Host(AccessControlContext.class) StaticObject createACC(@Host(ProtectionDomain[].class) StaticObject context,
                    boolean isPriviledged,
                    @Host(AccessControlContext.class) StaticObject priviledgedContext) {
        Klass accKlass = getMeta().java_security_AccessControlContext;
        StaticObject acc = accKlass.allocateInstance();
        acc.setField(getMeta().java_security_AccessControlContext_context, context);
        acc.setField(getMeta().java_security_AccessControlContext_privilegedContext, priviledgedContext);
        acc.setBooleanField(getMeta().java_security_AccessControlContext_isPrivileged, isPriviledged);
        if (getMeta().java_security_AccessControlContext_isAuthorized != null) {
            acc.setBooleanField(getMeta().java_security_AccessControlContext_isAuthorized, true);
        }
        return acc;
    }

    private @Host(AccessControlContext.class) StaticObject createDummyACC() {
        Klass pdKlass = getMeta().java_security_ProtectionDomain;
        StaticObject pd = pdKlass.allocateInstance();
        getMeta().java_security_ProtectionDomain_init_CodeSource_PermissionCollection.invokeDirect(pd, StaticObject.NULL, StaticObject.NULL);
        StaticObject context = StaticObject.wrap(new StaticObject[]{pd}, getMeta());
        return createACC(context, false, StaticObject.NULL);
    }

    static private class PrivilegedStack {
        public static Supplier<PrivilegedStack> supplier = new Supplier<PrivilegedStack>() {
            @Override
            public PrivilegedStack get() {
                return new PrivilegedStack();
            }
        };

        private Element top;

        public void push(FrameInstance frame, StaticObject context, Klass klass) {
            top = new Element(frame, context, klass, top);
        }

        public void pop() {
            assert top != null : "poping empty privileged stack !";
            top = top.next;
        }

        public boolean compare(FrameInstance frame) {
            return top != null && top.compare(frame);
        }

        public StaticObject peekContext() {
            assert top != null;
            return top.context;
        }

        public StaticObject classLoader() {
            assert top != null;
            return top.klass.getDefiningClassLoader();
        }

        static private class Element {
            long frameID;
            StaticObject context;
            Klass klass;
            Element next;

            public Element(FrameInstance frame, StaticObject context, Klass klass, Element next) {
                this.frameID = getFrameId(frame);
                this.context = context;
                this.klass = klass;
                this.next = next;
            }

            public boolean compare(FrameInstance other) {
                EspressoRootNode rootNode = getEspressoRootFromFrame(other);
                if (rootNode != null) {
                    Frame readOnlyFrame = other.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                    long frameIdOrZero = rootNode.readFrameIdOrZero(readOnlyFrame);
                    return frameIdOrZero != 0 && frameIdOrZero == frameID;
                }
                return false;
            }

            private static long getFrameId(FrameInstance frame) {
                EspressoRootNode rootNode = getEspressoRootFromFrame(frame);
                Frame readOnlyFrame = frame.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                return rootNode.readFrameIdOrZero(readOnlyFrame);
            }
        }
    }

    private final ThreadLocal<PrivilegedStack> privilegedStackThreadLocal = ThreadLocal.withInitial(PrivilegedStack.supplier);

    @VmImpl
    @JniImpl
    @SuppressWarnings("unused")
    public @Host(Object.class) StaticObject JVM_DoPrivileged(@Host(Class.class) StaticObject cls,
                    @Host(typeName = "PrivilegedAction OR PrivilegedActionException") StaticObject action,
                    @Host(AccessControlContext.class) StaticObject context,
                    boolean wrapException,
                    @GuestCall(target = "java_security_PrivilegedActionException_init_Exception") DirectCallNode privilegedActionExceptionInit,
                    @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(action)) {
            profiler.profile(0);
            throw getMeta().throwNullPointerException();
        }
        FrameInstance callerFrame = getCallerFrame(1, false, getMeta());
        assert callerFrame != null : "No caller ?";
        Klass caller = getMethodFromFrame(callerFrame).getDeclaringKlass();
        StaticObject acc = context;
        if (!StaticObject.isNull(context)) {
            if (!isAuthorized(context, caller)) {
                acc = createDummyACC();
            }
        }
        Method run = action.getKlass().lookupMethod(Name.run, Signature.Object);
        if (run == null || !run.isPublic() || run.isStatic()) {
            profiler.profile(1);
            throw Meta.throwException(getMeta().java_lang_InternalError);
        }

        // Prepare the privileged stack
        PrivilegedStack stack = getPrivilegedStack();
        stack.push(callerFrame, acc, caller);

        // Execute the action.
        StaticObject result;
        try {
            result = (StaticObject) run.invokeDirect(action);
        } catch (EspressoException e) {
            profiler.profile(2);
            if (getMeta().java_lang_Exception.isAssignableFrom(e.getExceptionObject().getKlass()) &&
                            !getMeta().java_lang_RuntimeException.isAssignableFrom(e.getExceptionObject().getKlass())) {
                profiler.profile(3);
                StaticObject wrapper = getMeta().java_security_PrivilegedActionException.allocateInstance();
                privilegedActionExceptionInit.call(wrapper, e.getExceptionObject());
                throw Meta.throwException(wrapper);
            }
            profiler.profile(4);
            throw e;
        } finally {
            stack.pop();
        }
        return result;
    }

    @VmImpl
    @JniImpl
    @SuppressWarnings("unused")
    public @Host(Object.class) StaticObject JVM_GetStackAccessControlContext(@Host(Class.class) StaticObject cls) {
        ArrayList<StaticObject> domains = new ArrayList<>();
        final PrivilegedStack stack = getPrivilegedStack();
        final boolean[] isPrivileged = new boolean[]{false};

        StaticObject context = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<StaticObject>() {
            StaticObject prevDomain = StaticObject.NULL;

            @Override
            public StaticObject visitFrame(FrameInstance frameInstance) {
                Method m = getMethodFromFrame(frameInstance);
                if (m != null) {
                    if (stack.compare(frameInstance)) {
                        isPrivileged[0] = true;
                    }
                    StaticObject domain = Target_java_lang_Class.getProtectionDomain0(m.getDeclaringKlass().mirror(), getMeta());
                    if (domain != prevDomain && domain != StaticObject.NULL) {
                        domains.add(domain);
                        prevDomain = domain;
                    }
                    if (isPrivileged[0]) {
                        return stack.peekContext();
                    }
                }
                return null;
            }
        });

        if (domains.isEmpty()) {
            if (isPrivileged[0] && StaticObject.isNull(context)) {
                return StaticObject.NULL;
            }
            return createACC(StaticObject.NULL, isPrivileged[0], context == null ? StaticObject.NULL : context);
        }

        StaticObject guestContext = StaticObject.createArray(getMeta().java_security_ProtectionDomain.array(), domains.toArray(StaticObject.EMPTY_ARRAY));
        return createACC(guestContext, isPrivileged[0], context == null ? StaticObject.NULL : context);
    }

    @VmImpl
    @JniImpl
    @SuppressWarnings("unused")
    public @Host(Object.class) StaticObject JVM_GetInheritedAccessControlContext(@Host(Class.class) StaticObject cls) {
        return getContext().getCurrentThread().getField(getMeta().java_lang_Thread_inheritedAccessControlContext);
    }

    @VmImpl
    @JniImpl
    public @Host(Object.class) StaticObject JVM_LatestUserDefinedLoader(@InjectMeta Meta meta) {
        StaticObject result = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<StaticObject>() {
            @Override
            public StaticObject visitFrame(FrameInstance frameInstance) {
                Method m = getMethodFromFrame(frameInstance);
                if (m != null) {
                    Klass holder = m.getDeclaringKlass();
                    // vfst.skip_reflection_related_frames(); // Only needed for 1.4 reflection
                    if (meta.sun_reflect_MethodAccessorImpl.isAssignableFrom(holder) || meta.sun_reflect_ConstructorAccessorImpl.isAssignableFrom(holder)) {
                        return null;
                    }

                    StaticObject loader = holder.getDefiningClassLoader();
                    // if (loader != NULL && !SystemDictionary::is_ext_class_loader(loader))
                    if (getJavaVersion().java8OrEarlier()) {
                        if (StaticObject.notNull(loader) && !Type.sun_misc_Launcher$ExtClassLoader.equals(loader.getKlass().getType())) {
                            return loader;
                        }
                    } else {
                        if (StaticObject.notNull(loader) && !Type.jdk_internal_loader_ClassLoaders$PlatformClassLoader.equals(loader.getKlass().getType())) {
                            return loader;
                        }
                    }
                }
                return null;
            }
        });

        return result == null ? StaticObject.NULL : result;
    }

    @VmImpl
    @JniImpl
    public static int JVM_GetClassAccessFlags(@Host(Class.class) StaticObject clazz) {
        Klass klass = clazz.getMirrorKlass();
        if (klass.isPrimitive()) {
            final int primitiveFlags = ACC_ABSTRACT | ACC_FINAL | ACC_PUBLIC;
            assert klass.getModifiers() == primitiveFlags;
            return klass.getModifiers();
        }
        return klass.getModifiers() & Constants.JVM_ACC_WRITTEN_FLAGS;
    }

    @VmImpl
    @JniImpl
    public static int JVM_GetClassModifiers(@Host(Class.class) StaticObject clazz) {
        Klass klass = clazz.getMirrorKlass();
        if (klass.isPrimitive()) {
            final int primitiveModifiers = ACC_ABSTRACT | ACC_FINAL | ACC_PUBLIC;
            assert klass.getClassModifiers() == primitiveModifiers;
            return klass.getClassModifiers();
        }
        return klass.getClassModifiers();
    }

    @VmImpl
    @JniImpl
    public @Host(Class.class) StaticObject JVM_FindClassFromBootLoader(@Pointer TruffleObject namePtr) {
        String name = interopPointerToString(namePtr);
        if (name == null) {
            return StaticObject.NULL;
        }

        String internalName = name;
        if (!name.startsWith("[")) {
            // Force 'L' type.
            internalName = "L" + name + ";";
        }

        if (!Validation.validTypeDescriptor(ByteSequence.create(internalName), false)) {
            return StaticObject.NULL;
        }

        Symbol<Type> type = getTypes().fromClassGetName(internalName);
        if (Types.isPrimitive(type)) {
            return StaticObject.NULL;
        }
        Klass klass = getMeta().resolveSymbolOrNull(type, StaticObject.NULL, StaticObject.NULL);
        if (klass == null) {
            return StaticObject.NULL;
        }

        return klass.mirror();
    }

    public @Pointer TruffleObject getFunction(long handle) {
        return handle2Sym.get(handle);
    }

    /**
     * Returns the value of the indexed component in the specified array object. The value is
     * automatically wrapped in an object if it has a primitive type.
     *
     * @param array the array
     * @param index the index
     * @throws NullPointerException If the specified object is null
     * @throws IllegalArgumentException If the specified object is not an array
     * @throws ArrayIndexOutOfBoundsException If the specified {@code index} argument is negative,
     *             or if it is greater than or equal to the length of the specified array
     * @return the (possibly wrapped) value of the indexed component in the specified array
     */
    @VmImpl
    @JniImpl
    public @Host(Object.class) StaticObject JVM_GetArrayElement(@Host(Object.class) StaticObject array, int index, @InjectProfile SubstitutionProfiler profiler) {
        Meta meta = getMeta();
        if (StaticObject.isNull(array)) {
            profiler.profile(7);
            throw meta.throwNullPointerException();
        }
        if (array.isArray()) {
            profiler.profile(6);
            return getInterpreterToVM().getArrayObject(index, array);
        }
        if (!array.getClass().isArray()) {
            profiler.profile(5);
            throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Argument is not an array");
        }
        assert array.getClass().isArray() && array.getClass().getComponentType().isPrimitive();
        if (index < 0 || index >= JVM_GetArrayLength(array, profiler)) {
            profiler.profile(4);
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, "index");
        }
        Object elem = Array.get(array, index);
        return guestBox(elem);
    }

    private static @Host(java.lang.reflect.Method.class) StaticObject getGuestReflectiveMethodRoot(@Host(java.lang.reflect.Method.class) StaticObject seed, Meta meta) {
        assert InterpreterToVM.instanceOf(seed, meta.java_lang_reflect_Method);
        StaticObject curMethod = seed;
        Method target = null;
        while (target == null) {
            target = (Method) curMethod.getHiddenField(meta.HIDDEN_METHOD_KEY);
            if (target == null) {
                curMethod = (StaticObject) meta.java_lang_reflect_Method_root.get(curMethod);
            }
        }
        return curMethod;
    }

    private static @Host(java.lang.reflect.Field.class) StaticObject getGuestReflectiveFieldRoot(@Host(java.lang.reflect.Field.class) StaticObject seed, Meta meta) {
        assert InterpreterToVM.instanceOf(seed, meta.java_lang_reflect_Field);
        StaticObject curField = seed;
        Field target = null;
        while (target == null) {
            target = (Field) curField.getHiddenField(meta.HIDDEN_FIELD_KEY);
            if (target == null) {
                curField = (StaticObject) meta.java_lang_reflect_Field_root.get(curField);
            }
        }
        return curField;
    }

    private static @Host(java.lang.reflect.Constructor.class) StaticObject getGuestReflectiveConstructorRoot(@Host(java.lang.reflect.Constructor.class) StaticObject seed, Meta meta) {
        assert InterpreterToVM.instanceOf(seed, meta.java_lang_reflect_Constructor);
        StaticObject curConstructor = seed;
        Method target = null;
        while (target == null) {
            target = (Method) curConstructor.getHiddenField(meta.HIDDEN_CONSTRUCTOR_KEY);
            if (target == null) {
                curConstructor = (StaticObject) meta.java_lang_reflect_Constructor_root.get(curConstructor);
            }
        }
        return curConstructor;
    }

    @VmImpl
    @JniImpl
    public @Host(Parameter[].class) StaticObject JVM_GetMethodParameters(@Host(Object.class) StaticObject executable, @InjectProfile SubstitutionProfiler profiler) {
        assert getMeta().java_lang_reflect_Executable.isAssignableFrom(executable.getKlass());
        StaticObject parameterTypes = (StaticObject) executable.getKlass().lookupMethod(Name.getParameterTypes, Signature.Class_array).invokeDirect(executable);
        int numParams = parameterTypes.length();
        if (numParams == 0) {
            return StaticObject.NULL;
        }

        Method method;
        if (getMeta().java_lang_reflect_Method.isAssignableFrom(executable.getKlass())) {
            method = Method.getHostReflectiveMethodRoot(executable, getMeta());
        } else if (getMeta().java_lang_reflect_Constructor.isAssignableFrom(executable.getKlass())) {
            method = Method.getHostReflectiveConstructorRoot(executable, getMeta());
        } else {
            throw EspressoError.shouldNotReachHere();
        }

        MethodParametersAttribute methodParameters = (MethodParametersAttribute) method.getAttribute(Name.MethodParameters);

        if (methodParameters == null) {
            return StaticObject.NULL;
        }
        // Verify first.
        /*
         * If number of entries in ParametersAttribute is inconsistent with actual parameters from
         * the signature, it will be caught in guest java code.
         */
        int cpLength = method.getConstantPool().length();
        for (MethodParametersAttribute.Entry entry : methodParameters.getEntries()) {
            int nameIndex = entry.getNameIndex();
            if (nameIndex < 0 || nameIndex >= cpLength) {
                profiler.profile(0);
                throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "Constant pool index out of bounds");
            }
            if (nameIndex != 0 && method.getConstantPool().tagAt(nameIndex) != ConstantPool.Tag.UTF8) {
                profiler.profile(1);
                throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "Wrong type at constant pool index");
            }
        }

        // TODO(peterssen): Cache guest j.l.reflect.Parameter constructor.
        // Calling the constructor is just for validation, manually setting the fields would
        // be faster.
        Method parameterInit = getMeta().java_lang_reflect_Parameter.lookupDeclaredMethod(Name._init_, getSignatures().makeRaw(Type._void,
                        /* name */ Type.java_lang_String,
                        /* modifiers */ Type._int,
                        /* executable */ Type.java_lang_reflect_Executable,
                        /* index */ Type._int));

        // Use attribute's number of parameters.
        return getMeta().java_lang_reflect_Parameter.allocateReferenceArray(methodParameters.getEntries().length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int index) {
                MethodParametersAttribute.Entry entry = methodParameters.getEntries()[index];
                StaticObject instance = getMeta().java_lang_reflect_Parameter.allocateInstance();
                // For a 0 index, give an empty name.
                StaticObject guestName;
                if (entry.getNameIndex() != 0) {
                    guestName = getMeta().toGuestString(method.getConstantPool().symbolAt(entry.getNameIndex(), "parameter name").toString());
                } else {
                    guestName = getJavaVersion().java9OrLater() ? StaticObject.NULL : getMeta().toGuestString("");
                }
                parameterInit.invokeDirect(/* this */ instance,
                                /* name */ guestName,
                                /* modifiers */ entry.getAccessFlags(),
                                /* executable */ executable,
                                /* index */ index);
                return instance;
            }
        });
    }

    @VmImpl
    @JniImpl
    public @Host(byte[].class) StaticObject JVM_GetMethodTypeAnnotations(@Host(java.lang.reflect.Executable.class) StaticObject guestReflectionMethod) {
        // guestReflectionMethod can be either a Method or a Constructor.
        if (InterpreterToVM.instanceOf(guestReflectionMethod, getMeta().java_lang_reflect_Method)) {
            StaticObject methodRoot = getGuestReflectiveMethodRoot(guestReflectionMethod, getMeta());
            assert methodRoot != null;
            return (StaticObject) methodRoot.getHiddenField(getMeta().HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        } else if (InterpreterToVM.instanceOf(guestReflectionMethod, getMeta().java_lang_reflect_Constructor)) {
            StaticObject constructorRoot = getGuestReflectiveConstructorRoot(guestReflectionMethod, getMeta());
            assert constructorRoot != null;
            return (StaticObject) constructorRoot.getHiddenField(getMeta().HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        } else {
            throw EspressoError.shouldNotReachHere();
        }
    }

    @VmImpl
    @JniImpl
    public @Host(byte[].class) StaticObject JVM_GetFieldTypeAnnotations(@Host(java.lang.reflect.Field.class) StaticObject guestReflectionField) {
        assert InterpreterToVM.instanceOf(guestReflectionField, getMeta().java_lang_reflect_Field);
        StaticObject fieldRoot = getGuestReflectiveFieldRoot(guestReflectionField, getMeta());
        assert fieldRoot != null;
        return (StaticObject) fieldRoot.getHiddenField(getMeta().HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
    }

    private StaticObject guestBox(Object elem) {
        if (elem instanceof Integer) {
            return (StaticObject) getMeta().java_lang_Integer_valueOf.invokeDirect(null, (int) elem);
        }
        if (elem instanceof Boolean) {
            return (StaticObject) getMeta().java_lang_Boolean_valueOf.invokeDirect(null, (boolean) elem);
        }
        if (elem instanceof Byte) {
            return (StaticObject) getMeta().java_lang_Byte_valueOf.invokeDirect(null, (byte) elem);
        }
        if (elem instanceof Character) {
            return (StaticObject) getMeta().java_lang_Character_valueOf.invokeDirect(null, (char) elem);
        }
        if (elem instanceof Short) {
            return (StaticObject) getMeta().java_lang_Short_valueOf.invokeDirect(null, (short) elem);
        }
        if (elem instanceof Float) {
            return (StaticObject) getMeta().java_lang_Float_valueOf.invokeDirect(null, (float) elem);
        }
        if (elem instanceof Double) {
            return (StaticObject) getMeta().java_lang_Double_valueOf.invokeDirect(null, (double) elem);
        }
        if (elem instanceof Long) {
            return (StaticObject) getMeta().java_lang_Long_valueOf.invokeDirect(null, (long) elem);
        }

        throw EspressoError.shouldNotReachHere("Not a boxed type ", elem);
    }

    @VmImpl
    @JniImpl
    public @Host(String.class) StaticObject JVM_GetSystemPackage(@Host(String.class) StaticObject name) {
        String hostPkgName = getMeta().toHostString(name);
        if (hostPkgName.endsWith("/")) {
            hostPkgName = hostPkgName.substring(0, hostPkgName.length() - 1);
        }
        String fileName = getRegistries().getBootClassRegistry().getPackagePath(hostPkgName);
        return getMeta().toGuestString(fileName);
    }

    @VmImpl
    @JniImpl
    public @Host(String[].class) StaticObject JVM_GetSystemPackages() {
        String[] packages = getRegistries().getBootClassRegistry().getPackages();
        StaticObject[] array = new StaticObject[packages.length];
        for (int i = 0; i < packages.length; i++) {
            array[i] = getMeta().toGuestString(packages[i]);
        }
        return StaticObject.createArray(getMeta().java_lang_String.getArrayClass(), array);
    }

    @VmImpl
    @TruffleBoundary(allowInlining = true)
    public static long JVM_FreeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    /**
     * Espresso only supports basic -ea and -esa options. Complex per-class/package filters are
     * unsupported.
     */
    @VmImpl
    @JniImpl
    @TruffleBoundary
    public @Host(typeName = "Ljava/lang/AssertionStatusDirectives;") StaticObject JVM_AssertionStatusDirectives(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused) {
        Meta meta = getMeta();
        StaticObject instance = meta.java_lang_AssertionStatusDirectives.allocateInstance();
        meta.java_lang_AssertionStatusDirectives.lookupMethod(Name._init_, Signature._void).invokeDirect(instance);
        meta.java_lang_AssertionStatusDirectives_classes.set(instance, meta.java_lang_String.allocateReferenceArray(0));
        meta.java_lang_AssertionStatusDirectives_classEnabled.set(instance, meta._boolean.allocateReferenceArray(0));
        meta.java_lang_AssertionStatusDirectives_packages.set(instance, meta.java_lang_String.allocateReferenceArray(0));
        meta.java_lang_AssertionStatusDirectives_packageEnabled.set(instance, meta._boolean.allocateReferenceArray(0));
        boolean ea = getContext().getEnv().getOptions().get(EspressoOptions.EnableAssertions);
        meta.java_lang_AssertionStatusDirectives_deflt.set(instance, ea);
        return instance;
    }

    @VmImpl
    @TruffleBoundary(allowInlining = true)
    public static int JVM_ActiveProcessorCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    @JniImpl
    @VmImpl
    public @Host(Class.class) StaticObject JVM_CurrentLoadedClass() {
        PrivilegedStack stack = getPrivilegedStack();
        StaticObject mirrorKlass = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<StaticObject>() {
            @Override
            public StaticObject visitFrame(FrameInstance frameInstance) {
                Method m = getMethodFromFrame(frameInstance);
                if (m != null) {
                    if (isTrustedFrame(frameInstance, stack)) {
                        return StaticObject.NULL;
                    }
                    if (!m.isNative()) {
                        ObjectKlass klass = m.getDeclaringKlass();
                        StaticObject loader = klass.getDefiningClassLoader();
                        if (StaticObject.notNull(loader) && !isTrustedLoader(loader)) {
                            return klass.mirror();
                        }
                    }
                }
                return null;
            }
        });
        return mirrorKlass == null ? StaticObject.NULL : mirrorKlass;
    }

    @TruffleBoundary
    public PrivilegedStack getPrivilegedStack() {
        return privilegedStackThreadLocal.get();
    }

    @JniImpl
    @VmImpl
    public @Host(Class.class) StaticObject JVM_CurrentClassLoader() {
        @Host(Class.class)
        StaticObject loadedClass = JVM_CurrentLoadedClass();
        return StaticObject.isNull(loadedClass) ? StaticObject.NULL : loadedClass.getMirrorKlass().getDefiningClassLoader();
    }

    @JniImpl
    @VmImpl
    public int JVM_ClassLoaderDepth() {
        PrivilegedStack stack = getPrivilegedStack();
        Integer res = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Integer>() {
            int depth = 0;

            @Override
            public Integer visitFrame(FrameInstance frameInstance) {
                Method m = getMethodFromFrame(frameInstance);
                if (m != null) {
                    if (isTrustedFrame(frameInstance, stack)) {
                        return -1;
                    }
                    if (!m.isNative()) {
                        ObjectKlass klass = m.getDeclaringKlass();
                        StaticObject loader = klass.getDefiningClassLoader();
                        if (StaticObject.notNull(loader) && !isTrustedLoader(loader)) {
                            return depth;
                        }
                        depth++;
                    }
                }
                return null;
            }
        });
        return res == null ? -1 : res;
    }

    @JniImpl
    @VmImpl
    public int JVM_ClassDepth(@Host(String.class) StaticObject name) {
        Symbol<Name> className = getContext().getNames().lookup(getMeta().toHostString(name).replace('.', '/'));
        if (className == null) {
            return -1;
        }
        Integer res = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Integer>() {
            int depth = 0;

            @Override
            public Integer visitFrame(FrameInstance frameInstance) {
                Method m = getMethodFromFrame(frameInstance);
                if (m != null) {
                    if (className.equals(m.getDeclaringKlass().getName())) {
                        return depth;
                    }
                    depth++;
                }
                return null;
            }
        });
        return res == null ? -1 : res;
    }

    // region Invocation API

    @VmImpl
    @TruffleBoundary
    public int JNI_GetCreatedJavaVMs(@Pointer TruffleObject vmBufPtr, int bufLen, @Pointer TruffleObject numVMsPtr) {
        if (bufLen > 0) {
            getContext().getJNI().GetJavaVM(vmBufPtr);
            if (!getUncached().isNull(numVMsPtr)) {
                IntBuffer numVMsBuf = directByteBuffer(numVMsPtr, 1, JavaKind.Int).asIntBuffer();
                numVMsBuf.put(1);
            }
        }
        return JNI_OK;
    }

    // endregion Invocation API

    private boolean isTrustedFrame(FrameInstance frameInstance, PrivilegedStack stack) {
        if (stack.compare(frameInstance)) {
            StaticObject loader = stack.classLoader();
            if (StaticObject.isNull(loader)) {
                return true;
            }
            if (isTrustedLoader(loader)) {
                return true;
            }
        }
        return false;
    }

    private StaticObject nonReflectionClassLoader(StaticObject loader) {
        if (StaticObject.notNull(loader)) {
            Meta meta = getMeta();
            if (meta.sun_reflect_DelegatingClassLoader.isAssignableFrom(loader.getKlass())) {
                return loader.getField(meta.java_lang_ClassLoader_parent);
            }
        }
        return loader;
    }

    private boolean isTrustedLoader(StaticObject loader) {
        StaticObject nonDelLoader = nonReflectionClassLoader(loader);
        StaticObject systemLoader = (StaticObject) getMeta().java_lang_ClassLoader_getSystemClassLoader.invokeDirect(null);
        while (StaticObject.notNull(systemLoader)) {
            if (systemLoader == nonDelLoader) {
                return true;
            }
            systemLoader = systemLoader.getField(getMeta().java_lang_ClassLoader_parent);
        }
        return false;
    }

    @JniImpl
    @VmImpl
    public @Host(Thread[].class) StaticObject JVM_GetAllThreads(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused) {
        final StaticObject[] threads = getContext().getActiveThreads();
        return getMeta().java_lang_Thread.allocateReferenceArray(threads.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int index) {
                return threads[index];
            }
        });
    }

    // region Management

    // Partial/incomplete implementation disclaimer!
    //
    // This is a partial implementation of the {@link java.lang.management} APIs. Some APIs go
    // beyond Espresso reach e.g. GC stats. Espresso could implement the hard bits by just
    // forwarding to the host implementation, but this approach is not feasible:
    // - In some cases it's not possible to gather stats per-context e.g. host GC stats are VM-wide.
    // - SubstrateVM implements a bare-minimum subset of the management APIs.
    //
    // Some implementations below are just partially correct due to limitations of Espresso itself
    // e.g. dumping stacktraces for all threads.

    // @formatter:off
    // enum jmmLongAttribute
    public static final int JMM_CLASS_LOADED_COUNT             = 1;    /* Total number of loaded classes */
    public static final int JMM_CLASS_UNLOADED_COUNT           = 2;    /* Total number of unloaded classes */
    public static final int JMM_THREAD_TOTAL_COUNT             = 3;    /* Total number of threads that have been started */
    public static final int JMM_THREAD_LIVE_COUNT              = 4;    /* Current number of live threads */
    public static final int JMM_THREAD_PEAK_COUNT              = 5;    /* Peak number of live threads */
    public static final int JMM_THREAD_DAEMON_COUNT            = 6;    /* Current number of daemon threads */
    public static final int JMM_JVM_INIT_DONE_TIME_MS          = 7;    /* Time when the JVM finished initialization */
    public static final int JMM_COMPILE_TOTAL_TIME_MS          = 8;    /* Total accumulated time spent in compilation */
    public static final int JMM_GC_TIME_MS                     = 9;    /* Total accumulated time spent in collection */
    public static final int JMM_GC_COUNT                       = 10;   /* Total number of collections */
    public static final int JMM_JVM_UPTIME_MS                  = 11;   /* The JVM uptime in milliseconds */
    public static final int JMM_INTERNAL_ATTRIBUTE_INDEX       = 100;
    public static final int JMM_CLASS_LOADED_BYTES             = 101;  /* Number of bytes loaded instance classes */
    public static final int JMM_CLASS_UNLOADED_BYTES           = 102;  /* Number of bytes unloaded instance classes */
    public static final int JMM_TOTAL_CLASSLOAD_TIME_MS        = 103;  /* Accumulated VM class loader time (TraceClassLoadingTime) */
    public static final int JMM_VM_GLOBAL_COUNT                = 104;  /* Number of VM internal flags */
    public static final int JMM_SAFEPOINT_COUNT                = 105;  /* Total number of safepoints */
    public static final int JMM_TOTAL_SAFEPOINTSYNC_TIME_MS    = 106;  /* Accumulated time spent getting to safepoints */
    public static final int JMM_TOTAL_STOPPED_TIME_MS          = 107;  /* Accumulated time spent at safepoints */
    public static final int JMM_TOTAL_APP_TIME_MS              = 108;  /* Accumulated time spent in Java application */
    public static final int JMM_VM_THREAD_COUNT                = 109;  /* Current number of VM internal threads */
    public static final int JMM_CLASS_INIT_TOTAL_COUNT         = 110;  /* Number of classes for which initializers were run */
    public static final int JMM_CLASS_INIT_TOTAL_TIME_MS       = 111;  /* Accumulated time spent in class initializers */
    public static final int JMM_METHOD_DATA_SIZE_BYTES         = 112;  /* Size of method data in memory */
    public static final int JMM_CLASS_VERIFY_TOTAL_TIME_MS     = 113;  /* Accumulated time spent in class verifier */
    public static final int JMM_SHARED_CLASS_LOADED_COUNT      = 114;  /* Number of shared classes loaded */
    public static final int JMM_SHARED_CLASS_UNLOADED_COUNT    = 115;  /* Number of shared classes unloaded */
    public static final int JMM_SHARED_CLASS_LOADED_BYTES      = 116;  /* Number of bytes loaded shared classes */
    public static final int JMM_SHARED_CLASS_UNLOADED_BYTES    = 117;  /* Number of bytes unloaded shared classes */
    public static final int JMM_OS_ATTRIBUTE_INDEX             = 200;
    public static final int JMM_OS_PROCESS_ID                  = 201;  /* Process id of the JVM */
    public static final int JMM_OS_MEM_TOTAL_PHYSICAL_BYTES    = 202;  /* Physical memory size */
    public static final int JMM_GC_EXT_ATTRIBUTE_INFO_SIZE     = 401;  /* the size of the GC specific attributes for a given GC memory manager */
    // @formatter:on

    // enum jmmBoolAttribute
    public static final int JMM_VERBOSE_GC = 21;
    public static final int JMM_VERBOSE_CLASS = 22;
    public static final int JMM_THREAD_CONTENTION_MONITORING = 23;
    public static final int JMM_THREAD_CPU_TIME = 24;
    public static final int JMM_THREAD_ALLOCATED_MEMORY = 25;

    // enum
    public static final int JMM_VERSION_1 = 0x20010000;
    public static final int JMM_VERSION_1_0 = 0x20010000;
    public static final int JMM_VERSION_1_1 = 0x20010100; // JDK 6
    public static final int JMM_VERSION_1_2 = 0x20010200; // JDK 7
    public static final int JMM_VERSION_1_2_1 = 0x20010201; // JDK 7 GA
    public static final int JMM_VERSION_1_2_2 = 0x20010202;
    public static final int JMM_VERSION_1_2_3 = 0x20010203;
    public static final int JMM_VERSION_2 = 0x20020000; // JDK 10
    public static final int JMM_VERSION_3 = 0x20030000; // JDK 11.7

    @CompilerDirectives.CompilationFinal //
    private int managementVersion;

    /**
     * Procedure to support a new management version in Espresso:
     * <ul>
     * <li>Add the new version to support in this method.</li>
     * <li>Add the version to the version enum in <code>jmm_common.h</code> in the mokapot include
     * directory.</li>
     * <li>Create and update accordingly with the new changes (most certainly a new function)
     * <code>jmm_.h</code> and <code>management_.c</code> in the mokapot include and source
     * directory</li>
     * <li>Add to <code>management.h</code> the new <code>initializeManagementContext_</code> and
     * <code>disposeManagementContext_</code> functions.</li>
     * <li>Update <code>management.c</code> to select these new method depending on the requested
     * version</li>
     * <li>Ideally implement the method in this class.</li>
     * </ul>
     */
    private static boolean isSupportedManagementVersion(int version) {
        return version == JMM_VERSION_1 || version == JMM_VERSION_2 || version == JMM_VERSION_3;
    }

    @VmImpl
    public synchronized @Pointer TruffleObject JVM_GetManagement(int version) {
        if (!isSupportedManagementVersion(version)) {
            return RawPointer.nullInstance();
        }
        EspressoContext context = getContext();
        if (!context.EnableManagement) {
            getLogger().severe("JVM_GetManagement: Experimental support for java.lang.management native APIs is disabled.\n" +
                            "Use '--java.EnableManagement=true' to enable experimental support for j.l.management native APIs.");
            return RawPointer.nullInstance();
        }
        if (managementPtr == null) {
            try {
                // void* fetch_by_name(char* function_name)
                @Pointer
                TruffleObject lookupVmImplNativeCallback = getNativeAccess().createNativeClosure(lookupVmImplCallback, NativeType.POINTER, NativeType.POINTER);
                managementPtr = (TruffleObject) getUncached().execute(initializeManagementContext, lookupVmImplNativeCallback, version);
                managementVersion = version;
                assert getUncached().isPointer(managementPtr);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
            assert managementPtr != null && !getUncached().isNull(managementPtr);
        } else if (version != managementVersion) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            context.getLogger().warning("Asking for a different management version that previously requested.\n" +
                            "Previously requested: " + managementVersion + ", currently requested: " + version);
            return RawPointer.nullInstance();
        }
        return managementPtr;
    }

    @JniImpl
    @VmImpl
    public int GetVersion() {
        if (managementVersion <= JMM_VERSION_1_2_3) {
            return JMM_VERSION_1_2_3;
        } else {
            return managementVersion;
        }
    }

    @JniImpl
    @VmImpl
    public int GetOptionalSupport(@Pointer TruffleObject /* jmmOptionalSupport **/ supportPtr) {
        if (!getUncached().isNull(supportPtr)) {
            ByteBuffer supportBuf = directByteBuffer(supportPtr, 8);
            supportBuf.putInt(0); // nothing optional is supported
            return 0;
        }
        return -1;
    }

    private static void validateThreadIdArray(Meta meta, @Host(long[].class) StaticObject threadIds, SubstitutionProfiler profiler) {
        assert threadIds.isArray();
        int numThreads = threadIds.length();
        for (int i = 0; i < numThreads; ++i) {
            long tid = threadIds.<long[]> unwrap()[i];
            if (tid <= 0) {
                profiler.profile(3);
                throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Invalid thread ID entry");
            }
        }
    }

    private static void validateThreadInfoArray(Meta meta, @Host(ThreadInfo[].class) StaticObject infoArray, SubstitutionProfiler profiler) {
        // check if the element of infoArray is of type ThreadInfo class
        Klass infoArrayKlass = infoArray.getKlass();
        if (infoArray.isArray()) {
            Klass component = ((ArrayKlass) infoArrayKlass).getComponentType();
            if (!meta.java_lang_management_ThreadInfo.equals(component)) {
                profiler.profile(4);
                throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "infoArray element type is not ThreadInfo class");
            }
        }
    }

    @JniImpl
    @VmImpl
    public int GetThreadInfo(@Host(long[].class) StaticObject ids, int maxDepth, @Host(Object[].class) StaticObject infoArray, @InjectProfile SubstitutionProfiler profiler) {
        Meta meta = getMeta();
        if (StaticObject.isNull(ids) || StaticObject.isNull(infoArray)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }

        if (maxDepth < -1) {
            profiler.profile(1);
            throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Invalid maxDepth");
        }

        validateThreadIdArray(meta, ids, profiler);
        validateThreadInfoArray(meta, infoArray, profiler);

        if (ids.length() != infoArray.length()) {
            profiler.profile(2);
            throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "The length of the given ThreadInfo array does not match the length of the given array of thread IDs");
        }

        Method init = meta.java_lang_management_ThreadInfo.lookupDeclaredMethod(Name._init_, getSignatures().makeRaw(
                        /* returns */Type._void,
                        /* t */ Type.java_lang_Thread,
                        /* state */ Type._int,
                        /* lockObj */ Type.java_lang_Object,
                        /* lockOwner */Type.java_lang_Thread,
                        /* blockedCount */Type._long,
                        /* blockedTime */Type._long,
                        /* waitedCount */Type._long,
                        /* waitedTime */Type._long,
                        /* StackTraceElement[] */ Type.java_lang_StackTraceElement_array));

        StaticObject[] activeThreads = getContext().getActiveThreads();
        StaticObject currentThread = getContext().getCurrentThread();
        for (int i = 0; i < ids.length(); ++i) {
            long id = getInterpreterToVM().getArrayLong(i, ids);
            StaticObject thread = StaticObject.NULL;

            for (int j = 0; j < activeThreads.length; ++j) {
                if (Target_java_lang_Thread.getThreadId(meta, activeThreads[j]) == id) {
                    thread = activeThreads[j];
                    break;
                }
            }

            if (StaticObject.isNull(thread)) {
                getInterpreterToVM().setArrayObject(StaticObject.NULL, i, infoArray);
            } else {

                int threadStatus = thread.getIntField(meta.java_lang_Thread_threadStatus);
                StaticObject lockObj = StaticObject.NULL;
                StaticObject lockOwner = StaticObject.NULL;
                int mask = State.BLOCKED.value | State.WAITING.value | State.TIMED_WAITING.value;
                if ((threadStatus & mask) != 0) {
                    lockObj = (StaticObject) thread.getHiddenField(meta.HIDDEN_THREAD_BLOCKED_OBJECT);
                    if (lockObj == null) {
                        lockObj = StaticObject.NULL;
                    }
                    Thread hostOwner = StaticObject.isNull(lockObj)
                                    ? null
                                    : lockObj.getLock().getOwnerThread();
                    if (hostOwner != null && hostOwner.isAlive()) {
                        lockOwner = getContext().getGuestThreadFromHost(hostOwner);
                        if (lockOwner == null) {
                            lockOwner = StaticObject.NULL;
                        }
                    }
                }

                long blockedCount = Target_java_lang_Thread.getThreadCounter(thread, meta.HIDDEN_THREAD_BLOCKED_COUNT);
                long waitedCount = Target_java_lang_Thread.getThreadCounter(thread, meta.HIDDEN_THREAD_WAITED_COUNT);

                StaticObject stackTrace;
                if (maxDepth != 0 && thread == currentThread) {
                    stackTrace = (StaticObject) getMeta().java_lang_Throwable_getStackTrace.invokeDirect(Meta.initException(meta.java_lang_Throwable));
                    if (stackTrace.length() > maxDepth && maxDepth != -1) {
                        StaticObject[] unwrapped = stackTrace.unwrap();
                        unwrapped = Arrays.copyOf(unwrapped, maxDepth);
                        stackTrace = StaticObject.wrap(meta.java_lang_StackTraceElement.getArrayClass(), unwrapped);
                    }
                } else {
                    stackTrace = meta.java_lang_StackTraceElement.allocateReferenceArray(0);
                }

                StaticObject threadInfo = meta.java_lang_management_ThreadInfo.allocateInstance();
                init.invokeDirect( /* this */ threadInfo,
                                /* t */ thread,
                                /* state */ threadStatus,
                                /* lockObj */ lockObj,
                                /* lockOwner */ lockOwner,
                                /* blockedCount */ blockedCount,
                                /* blockedTime */ -1L,
                                /* waitedCount */ waitedCount,
                                /* waitedTime */ -1L,
                                /* StackTraceElement[] */ stackTrace);
                getInterpreterToVM().setArrayObject(threadInfo, i, infoArray);
            }
        }

        return 0; // always 0
    }

    @JniImpl
    @VmImpl
    public @Host(String[].class) StaticObject GetInputArgumentArray() {
        return getMeta().java_lang_String.allocateReferenceArray(0);
    }

    @JniImpl
    @VmImpl
    public @Host(String[].class) StaticObject GetInputArguments() {
        return GetInputArgumentArray();
    }

    @JniImpl
    @VmImpl
    public @Host(Object[].class) StaticObject GetMemoryPools(@SuppressWarnings("unused") @Host(Object.class) StaticObject unused,
                    @GuestCall(target = "sun_management_ManagementFactory_createMemoryPool") DirectCallNode createMemoryPool) {
        Klass memoryPoolMXBean = getMeta().resolveSymbolOrFail(Type.java_lang_management_MemoryPoolMXBean, StaticObject.NULL, StaticObject.NULL);
        return memoryPoolMXBean.allocateReferenceArray(1, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int value) {
                // (String name, boolean isHeap, long uThreshold, long gcThreshold)
                return (StaticObject) createMemoryPool.call(
                                /* String name */ getMeta().toGuestString("foo"),
                                /* boolean isHeap */ true,
                                /* long uThreshold */ -1L,
                                /* long gcThreshold */ 0L);
            }
        });
    }

    @JniImpl
    @VmImpl
    public @Host(Object[].class) StaticObject GetMemoryManagers(@SuppressWarnings("unused") @Host(Object.class) StaticObject pool,
                    @GuestCall(target = "sun_management_ManagementFactory_createMemoryManager") DirectCallNode createMemoryManager) {
        Klass memoryManagerMXBean = getMeta().resolveSymbolOrFail(Type.java_lang_management_MemoryManagerMXBean, StaticObject.NULL, StaticObject.NULL);
        return memoryManagerMXBean.allocateReferenceArray(1, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int value) {
                // (String name, String type)
                return (StaticObject) createMemoryManager.call(
                                /* String name */ getMeta().toGuestString("foo"));
            }
        });
    }

    @JniImpl
    @VmImpl
    public @Host(Object.class) StaticObject GetMemoryPoolUsage(@Host(Object.class) StaticObject pool) {
        if (StaticObject.isNull(pool)) {
            return StaticObject.NULL;
        }
        Method init = getMeta().java_lang_management_MemoryUsage.lookupDeclaredMethod(Symbol.Name._init_, getSignatures().makeRaw(Type._void, Type._long, Type._long, Type._long, Type._long));
        StaticObject instance = getMeta().java_lang_management_MemoryUsage.allocateInstance();
        init.invokeDirect(instance, 0L, 0L, 0L, 0L);
        return instance;
    }

    @JniImpl
    @VmImpl
    public @Host(Object.class) StaticObject GetPeakMemoryPoolUsage(@Host(Object.class) StaticObject pool) {
        if (StaticObject.isNull(pool)) {
            return StaticObject.NULL;
        }
        Method init = getMeta().java_lang_management_MemoryUsage.lookupDeclaredMethod(Symbol.Name._init_, getSignatures().makeRaw(Type._void, Type._long, Type._long, Type._long, Type._long));
        StaticObject instance = getMeta().java_lang_management_MemoryUsage.allocateInstance();
        init.invokeDirect(instance, 0L, 0L, 0L, 0L);
        return instance;
    }

    @JniImpl
    @VmImpl
    public @Host(Object.class) StaticObject GetMemoryUsage(@SuppressWarnings("unused") boolean heap) {
        Method init = getMeta().java_lang_management_MemoryUsage.lookupDeclaredMethod(Symbol.Name._init_, getSignatures().makeRaw(Type._void, Type._long, Type._long, Type._long, Type._long));
        StaticObject instance = getMeta().java_lang_management_MemoryUsage.allocateInstance();
        init.invokeDirect(instance, 0L, 0L, 0L, 0L);
        return instance;
    }

    @JniImpl
    @VmImpl
    @TruffleBoundary // Lots of SVM + Windows blacklisted methods.
    public long GetLongAttribute(@SuppressWarnings("unused") @Host(Object.class) StaticObject obj,
                    /* jmmLongAttribute */ int att) {
        switch (att) {
            case JMM_JVM_INIT_DONE_TIME_MS:
                return TimeUnit.NANOSECONDS.toMillis(getContext().initDoneTimeNanos);
            case JMM_CLASS_LOADED_COUNT:
                return getRegistries().getLoadedClassesCount();
            case JMM_CLASS_UNLOADED_COUNT:
                return 0L;
            case JMM_JVM_UPTIME_MS:
                long elapsedNanos = System.nanoTime() - getContext().initDoneTimeNanos;
                return TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
            case JMM_OS_PROCESS_ID:
                String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
                String[] parts = processName.split("@");
                return Long.parseLong(parts[0]);
            case JMM_THREAD_DAEMON_COUNT:
                int daemonCount = 0;
                for (StaticObject t : getContext().getActiveThreads()) {
                    if ((boolean) getMeta().java_lang_Thread_daemon.get(t)) {
                        ++daemonCount;
                    }
                }
                return daemonCount;

            case JMM_THREAD_PEAK_COUNT:
                return getContext().getPeakThreadCount();
            case JMM_THREAD_LIVE_COUNT:
                return getContext().getActiveThreads().length;
            case JMM_THREAD_TOTAL_COUNT:
                return getContext().getCreatedThreadCount();
        }
        throw EspressoError.unimplemented("GetLongAttribute " + att);
    }

    private boolean JMM_VERBOSE_GC_state = false;
    private boolean JMM_VERBOSE_CLASS_state = false;
    private boolean JMM_THREAD_CONTENTION_MONITORING_state = false;
    private boolean JMM_THREAD_CPU_TIME_state = false;
    private boolean JMM_THREAD_ALLOCATED_MEMORY_state = false;

    @JniImpl
    @VmImpl
    public boolean GetBoolAttribute(/* jmmBoolAttribute */ int att) {
        switch (att) {
            case JMM_VERBOSE_GC:
                return JMM_VERBOSE_GC_state;
            case JMM_VERBOSE_CLASS:
                return JMM_VERBOSE_CLASS_state;
            case JMM_THREAD_CONTENTION_MONITORING:
                return JMM_THREAD_CONTENTION_MONITORING_state;
            case JMM_THREAD_CPU_TIME:
                return JMM_THREAD_CPU_TIME_state;
            case JMM_THREAD_ALLOCATED_MEMORY:
                return JMM_THREAD_ALLOCATED_MEMORY_state;
        }
        throw EspressoError.unimplemented("GetBoolAttribute ", att);
    }

    @JniImpl
    @VmImpl
    public boolean SetBoolAttribute(/* jmmBoolAttribute */ int att, boolean flag) {
        switch (att) {
            case JMM_VERBOSE_GC:
                return JMM_VERBOSE_GC_state = flag;
            case JMM_VERBOSE_CLASS:
                return JMM_VERBOSE_CLASS_state = flag;
            case JMM_THREAD_CONTENTION_MONITORING:
                return JMM_THREAD_CONTENTION_MONITORING_state = flag;
            case JMM_THREAD_CPU_TIME:
                return JMM_THREAD_CPU_TIME_state = flag;
            case JMM_THREAD_ALLOCATED_MEMORY:
                return JMM_THREAD_ALLOCATED_MEMORY_state = flag;
        }
        throw EspressoError.unimplemented("SetBoolAttribute ", att);
    }

    @JniImpl
    @VmImpl
    public int GetVMGlobals(@Host(Object[].class) StaticObject names, /* jmmVMGlobal* */ @Pointer TruffleObject globalsPtr, @SuppressWarnings("unused") int count,
                    @InjectProfile SubstitutionProfiler profiler) {
        Meta meta = getMeta();
        if (getUncached().isNull(globalsPtr)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        if (StaticObject.notNull(names)) {
            if (!names.getKlass().equals(meta.java_lang_String.array())) {
                profiler.profile(1);
                throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Array element type is not String class");
            }

            StaticObject[] entries = names.unwrap();
            for (StaticObject entry : entries) {
                if (StaticObject.isNull(entry)) {
                    profiler.profile(2);
                    throw meta.throwNullPointerException();
                }
                getLogger().fine("GetVMGlobals: " + meta.toHostString(entry));
            }
        }
        return 0;
    }

    @VmImpl
    @JniImpl
    @SuppressWarnings("unused")
    public @Host(ThreadInfo[].class) StaticObject DumpThreads(@Host(long[].class) StaticObject ids, boolean lockedMonitors, boolean lockedSynchronizers,
                    @InjectProfile SubstitutionProfiler profiler) {
        StaticObject threadIds = ids;
        if (StaticObject.isNull(threadIds)) {
            StaticObject[] activeThreads = getContext().getActiveThreads();
            threadIds = InterpreterToVM.allocatePrimitiveArray((byte) JavaKind.Long.getBasicType(), activeThreads.length, getMeta());
            for (int j = 0; j < activeThreads.length; ++j) {
                long tid = Target_java_lang_Thread.getThreadId(getMeta(), activeThreads[j]);
                getInterpreterToVM().setArrayLong(tid, j, threadIds);
            }
        }
        StaticObject result = getMeta().java_lang_management_ThreadInfo.allocateReferenceArray(threadIds.length());
        if (GetThreadInfo(threadIds, 0, result, profiler) != JNI_OK) {
            return StaticObject.NULL;
        }
        return result;
    }

    @VmImpl
    @JniImpl
    public long GetOneThreadAllocatedMemory(
                    long threadId) {
        StaticObject[] activeThreads = getContext().getActiveThreads();

        StaticObject thread = StaticObject.NULL;

        for (int j = 0; j < activeThreads.length; ++j) {
            if (Target_java_lang_Thread.getThreadId(getMeta(), activeThreads[j]) == threadId) {
                thread = activeThreads[j];
                break;
            }
        }
        if (StaticObject.isNull(thread)) {
            return -1L;
        } else {
            return 0L;
        }
    }

    @VmImpl
    @JniImpl
    public void GetThreadAllocatedMemory(
                    @Host(long[].class) StaticObject ids,
                    @Host(long[].class) StaticObject sizeArray,
                    @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(ids) || StaticObject.isNull(sizeArray)) {
            profiler.profile(0);
            throw Meta.throwException(getMeta().java_lang_NullPointerException);
        }
        validateThreadIdArray(getMeta(), ids, profiler);
        if (ids.length() != sizeArray.length()) {
            profiler.profile(1);
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "The length of the given long array does not match the length of the given array of thread IDs");
        }
        StaticObject[] activeThreads = getContext().getActiveThreads();

        for (int i = 0; i < ids.length(); i++) {
            long id = getInterpreterToVM().getArrayLong(i, ids);
            StaticObject thread = StaticObject.NULL;

            for (int j = 0; j < activeThreads.length; ++j) {
                if (Target_java_lang_Thread.getThreadId(getMeta(), activeThreads[j]) == id) {
                    thread = activeThreads[j];
                    break;
                }
            }
            if (StaticObject.isNull(thread)) {
                getInterpreterToVM().setArrayLong(-1L, i, sizeArray);
            } else {
                getInterpreterToVM().setArrayLong(0L, i, sizeArray);
            }
        }
    }

    // endregion Management

    // region Modules

    @VmImpl
    @JniImpl
    @TruffleBoundary
    public void JVM_AddModuleExports(@Host(typeName = "Ljava/lang/Module") StaticObject from_module,
                    @Pointer TruffleObject pkgName,
                    @Host(typeName = "Ljava/lang/Module") StaticObject to_module,
                    @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(to_module)) {
            profiler.profile(6);
            throw getMeta().throwNullPointerException();
        }
        ModulesHelperVM.addModuleExports(from_module, pkgName, to_module, getMeta(), getUncached(), profiler);
    }

    @VmImpl
    @JniImpl
    @TruffleBoundary
    public void JVM_AddModuleExportsToAllUnnamed(@Host(typeName = "Ljava/lang/Module") StaticObject from_module, @Pointer TruffleObject pkgName,
                    @InjectProfile SubstitutionProfiler profiler) {
        if (getUncached().isNull(pkgName)) {
            profiler.profile(0);
            throw getMeta().throwNullPointerException();
        }
        ModuleEntry fromModuleEntry = ModulesHelperVM.extractFromModuleEntry(from_module, getMeta(), profiler);
        if (fromModuleEntry.isNamed()) { // No-op for unnamed module.
            PackageEntry packageEntry = ModulesHelperVM.extractPackageEntry(pkgName, fromModuleEntry, getMeta(), profiler);
            packageEntry.setExportedAllUnnamed();
        }
    }

    @VmImpl
    @JniImpl
    @TruffleBoundary
    public void JVM_AddModuleExportsToAll(@Host(typeName = "Ljava/lang/Module") StaticObject from_module, @Pointer TruffleObject pkgName,
                    @InjectProfile SubstitutionProfiler profiler) {
        ModulesHelperVM.addModuleExports(from_module, pkgName, StaticObject.NULL, getMeta(), getUncached(), profiler);
    }

    @VmImpl
    @JniImpl
    @TruffleBoundary
    public void JVM_AddReadsModule(@Host(typeName = "Ljava/lang/Module") StaticObject from_module, @Host(typeName = "Ljava/lang/Module") StaticObject source_module,
                    @InjectProfile SubstitutionProfiler profiler) {
        ModuleEntry fromEntry = ModulesHelperVM.extractFromModuleEntry(from_module, getMeta(), profiler);
        ModuleEntry toEntry = ModulesHelperVM.extractToModuleEntry(source_module, getMeta(), profiler);
        if (fromEntry != toEntry && fromEntry.isNamed()) {
            fromEntry.addReads(toEntry);
        }
    }

    private static final String MODULES = "modules";

    @VmImpl
    @JniImpl
    @TruffleBoundary
    public void JVM_DefineModule(@Host(typeName = "Ljava/lang/Module") StaticObject module,
                    boolean is_open,
                    @SuppressWarnings("unused") @Host(String.class) StaticObject version,
                    @SuppressWarnings("unused") @Host(String.class) StaticObject location,
                    @Pointer TruffleObject pkgs,
                    int num_package,
                    @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(module)) {
            profiler.profile(0);
            throw getMeta().throwNullPointerException();
        }
        if (num_package < 0) {
            profiler.profile(1);
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "num_package must be >= 0");
        }
        if (getUncached().isNull(pkgs) && num_package > 0) {
            profiler.profile(2);
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "num_packages must be 0 if packages is null");
        }
        if (!getMeta().java_lang_Module.isAssignableFrom(module.getKlass())) {
            profiler.profile(3);
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "module is not an instance of java.lang.Module");
        }

        StaticObject guestName = module.getField(getMeta().java_lang_Module_name);
        if (StaticObject.isNull(guestName)) {
            profiler.profile(4);
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "modue name cannot be null");
        }

        String hostName = getMeta().toHostString(guestName);
        if (hostName.equals(JAVA_BASE)) {
            profiler.profile(5);
            defineJavaBaseModule(module, pkgs, num_package, profiler);
            return;
        }
        profiler.profile(6);
        defineModule(module, hostName, is_open, pkgs, num_package, profiler);
    }

    @SuppressWarnings("try")
    private void defineModule(StaticObject module,
                    String moduleName,
                    boolean is_open,
                    TruffleObject pkgs,
                    int num_package,
                    SubstitutionProfiler profiler) {
        StaticObject loader = module.getField(getMeta().java_lang_Module_loader);
        if (loader != nonReflectionClassLoader(loader)) {
            profiler.profile(15);
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "Class loader is an invalid delegating class loader");
        }

        // Prepare variables
        ClassRegistry registry = getRegistries().getClassRegistry(loader);
        assert registry != null;
        PackageTable packageTable = registry.packages();
        ModuleTable moduleTable = registry.modules();
        assert moduleTable != null && packageTable != null;
        boolean loaderIsBootOrPlatform = ClassRegistry.loaderIsBootOrPlatform(loader, getMeta());

        ArrayList<Symbol<Name>> pkgSymbols = new ArrayList<>();
        String[] packages = extractNativePackages(pkgs, num_package, profiler);
        try (EntryTable.BlockLock block = packageTable.write()) {
            for (String str : packages) {
                // Extract the package symbols. Also checks for duplicates.
                if (!loaderIsBootOrPlatform && (str.equals("java") || str.startsWith("java/"))) {
                    // Only modules defined to either the boot or platform class loader, can define
                    // a "java/" package.
                    profiler.profile(14);
                    throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException,
                                    cat("Class loader (", loader.getKlass().getType(), ") tried to define prohibited package name: ", str));
                }
                Symbol<Name> symbol = getNames().getOrCreate(str);
                if (packageTable.lookup(symbol) != null) {
                    profiler.profile(13);
                    throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException,
                                    cat("Package ", str, " is already defined."));
                }
                pkgSymbols.add(symbol);
            }
            Symbol<Name> moduleSymbol = getNames().getOrCreate(moduleName);
            // Try define module
            ModuleEntry moduleEntry = moduleTable.createAndAddEntry(moduleSymbol, registry, is_open, module);
            if (moduleEntry == null) {
                // Module already defined
                profiler.profile(12);
                throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException,
                                cat("Module ", moduleName, " is already defined"));
            }
            // Register packages
            for (Symbol<Name> pkgSymbol : pkgSymbols) {
                PackageEntry pkgEntry = packageTable.createAndAddEntry(pkgSymbol, moduleEntry);
                assert pkgEntry != null; // should have been checked before
            }
            // Link guest module to its host representation
            module.setField(getMeta().HIDDEN_MODULE_ENTRY, moduleEntry);
        }
        if (StaticObject.isNull(loader) && getContext().getVmProperties().bootClassPathType().isExplodedModule()) {
            profiler.profile(11);
            // If we have an exploded build, and the module is defined to the bootloader, prepend a
            // class path entry for this module.
            prependModuleClasspath(moduleName);
        }
    }

    void prependModuleClasspath(String moduleName) {
        Path path = getContext().getVmProperties().javaHome().resolve(MODULES).resolve(moduleName);
        Classpath.Entry newEntry = Classpath.createEntry(path.toString());
        if (newEntry.isDirectory()) {
            getContext().getBootClasspath().prepend(newEntry);
            // TODO: prepend path to VM properties' bootClasspath
        }
    }

    private String[] extractNativePackages(TruffleObject pkgs, int numPackages, SubstitutionProfiler profiler) {
        String[] packages = new String[numPackages];
        try {
            for (int i = 0; i < numPackages; i++) {
                String pkg = interopPointerToString((TruffleObject) getUncached().execute(getPackageAt, pkgs, i));
                if (!Validation.validBinaryName(pkg)) {
                    profiler.profile(7);
                    throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException,
                                    cat("Invalid package name: ", pkg));
                }
                packages[i] = pkg;
            }
        } catch (UnsupportedMessageException | ArityException | UnsupportedTypeException e) {
            throw EspressoError.shouldNotReachHere();
        }
        return packages;
    }

    @SuppressWarnings("try")
    private void defineJavaBaseModule(StaticObject module, TruffleObject pkgs, int numPackages, SubstitutionProfiler profiler) {
        String[] packages = extractNativePackages(pkgs, numPackages, profiler);
        StaticObject loader = module.getField(getMeta().java_lang_Module_loader);
        if (!StaticObject.isNull(loader)) {
            profiler.profile(10);
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException,
                            "Class loader must be the bootclass loader");
        }
        PackageTable pkgTable = getRegistries().getBootClassRegistry().packages();
        ModuleEntry javaBaseEntry = getRegistries().getJavaBaseModule();
        try (EntryTable.BlockLock block = pkgTable.write()) {
            if (getRegistries().javaBaseDefined()) {
                profiler.profile(9);
                throw Meta.throwException(getMeta().java_lang_InternalError);
            }
            for (String pkg : packages) {
                Symbol<Name> pkgName = getNames().getOrCreate(pkg);
                if (pkgTable.lookup(pkgName) == null) {
                    pkgTable.createAndAddEntry(pkgName, javaBaseEntry);
                }
            }
            javaBaseEntry.setModule(module);
            module.setHiddenField(getMeta().HIDDEN_MODULE_ENTRY, javaBaseEntry);
            getRegistries().processFixupList(module);
        }
    }

    @VmImpl
    @JniImpl
    @TruffleBoundary
    public void JVM_SetBootLoaderUnnamedModule(@Host(typeName = "Ljava/lang/Module") StaticObject module) {
        if (StaticObject.isNull(module)) {
            throw getMeta().throwNullPointerException();
        }
        if (!getMeta().java_lang_Module.isAssignableFrom(module.getKlass())) {
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "module is not an instance of java.lang.module");
        }
        if (!StaticObject.isNull(module.getField(getMeta().java_lang_Module_name))) {
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "boot loader unnamed module has a name");
        }
        if (!StaticObject.isNull(module.getField(getMeta().java_lang_Module_loader))) {
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "Class loader must be the boot class loader");
        }
        ModuleEntry bootUnnamed = getRegistries().getBootClassRegistry().getUnnamedModule();
        bootUnnamed.setModule(module);
        module.setHiddenField(getMeta().HIDDEN_MODULE_ENTRY, bootUnnamed);
    }

    // endregion Modules

    // region java11

    @VmImpl
    @JniImpl
    public static boolean JVM_AreNestMates(@Host(Class.class) StaticObject current, @Host(Class.class) StaticObject member) {
        return current.getMirrorKlass().nest() == member.getMirrorKlass().nest();
    }

    @VmImpl
    @JniImpl
    public static @Host(Class.class) StaticObject JVM_GetNestHost(@Host(Class.class) StaticObject current) {
        return current.getMirrorKlass().nest().mirror();
    }

    @VmImpl
    @JniImpl
    public @Host(Class[].class) StaticObject JVM_GetNestMembers(@Host(Class.class) StaticObject current) {
        Klass k = current.getMirrorKlass();
        Klass[] nestMembers = k.getNestMembers();
        StaticObject[] array = new StaticObject[nestMembers.length];
        for (int i = 0; i < nestMembers.length; i++) {
            array[i] = nestMembers[i].mirror();
        }
        return StaticObject.createArray(getMeta().java_lang_Class_array, array);
    }

    @VmImpl
    @JniImpl
    public StaticObject JVM_GetAndClearReferencePendingList() {
        return getContext().getAndClearReferencePendingList();
    }

    @VmImpl
    @JniImpl
    public void JVM_WaitForReferencePendingList() {
        getContext().waitForReferencePendingList();
    }

    @VmImpl
    @JniImpl
    public boolean JVM_HasReferencePendingList() {
        return getContext().hasReferencePendingList();
    }

    @VmImpl
    @JniImpl
    @SuppressWarnings("unused")
    public void JVM_InitializeFromArchive(@Host(Class.class) StaticObject cls) {
        /*
         * Used to reduce boot time of certain initializations through CDS (/ex: module
         * initialization). Currently unsupported.
         */
    }

    @VmImpl
    @JniImpl
    public void JVM_BeforeHalt() {
        /*
         * currently nop
         */
    }

    /**
     * Return the temporary directory that the VM uses for the attach and perf data files.
     *
     * It is important that this directory is well-known and the same for all VM instances. It
     * cannot be affected by configuration variables such as java.io.tmpdir.
     */
    @VmImpl
    @JniImpl
    @TruffleBoundary
    public @Host(String.class) StaticObject JVM_GetTemporaryDirectory() {
        // TODO: use host VMSupport.getVMTemporaryDirectory(). Not implemented by SVM.
        // host application temporary directory
        return getMeta().toGuestString(System.getProperty("java.io.tmpdir"));
    }

    private static final long ONE_BILLION = 1_000_000_000;
    private static final long MAX_DIFF = 0x0100000000L;

    @VmImpl
    @JniImpl
    @SuppressWarnings("unused")
    @TruffleBoundary
    /**
     * Instant.now() uses System.currentTimeMillis() on a host Java 8. This might produce some loss
     * of precision.
     */
    public static long JVM_GetNanoTimeAdjustment(@Host(Class.class) StaticObject ignored, long offset) {
        // Instant.now() uses System.currentTimeMillis() on a host Java 8. This might produce some
        // loss of precision.
        Instant now = Instant.now();
        long secs = now.getEpochSecond();
        long nanos = now.getNano();
        long diff = secs - offset;
        if (diff > MAX_DIFF || diff < -MAX_DIFF) {
            return -1;
        }
        // Test above also guards against overflow.
        return (diff * ONE_BILLION) + nanos;
    }

    @VmImpl
    @JniImpl
    @TruffleBoundary
    public static long JVM_MaxObjectInspectionAge() {
        // TODO: somehow use GC.maxObjectInspectionAge() (not supported by SVM);
        return 0;
    }

    @VmImpl
    @JniImpl
    @SuppressWarnings("unused")
    public void JVM_InitStackTraceElement(@Host(StackTraceElement.class) StaticObject element, @Host(typeName = "Ljava/lang/StackFrameInfo;") StaticObject info,
                    @GuestCall(target = "java_lang_Class_getName") DirectCallNode classGetName) {
        if (StaticObject.isNull(element) || StaticObject.isNull(info)) {
            throw Meta.throwException(getMeta().java_lang_NullPointerException);
        }
        StaticObject mname = info.getField(getMeta().java_lang_StackFrameInfo_memberName);
        if (StaticObject.isNull(mname)) {
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_InternalError, "uninitialized StackFrameInfo !");
        }
        StaticObject clazz = mname.getField(getMeta().java_lang_invoke_MemberName_clazz);
        Method m = (Method) mname.getHiddenField(getMeta().HIDDEN_VMTARGET);
        if (m == null) {
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_InternalError, "uninitialized StackFrameInfo !");
        }
        int bci = info.getIntField(getMeta().java_lang_StackFrameInfo_bci);
        fillInElement(element, new VM.StackElement(m, bci), classGetName);
    }

    @VmImpl
    @JniImpl
    public void JVM_InitStackTraceElementArray(@Host(StackTraceElement[].class) StaticObject elements, @Host(Throwable.class) StaticObject throwable,
                    @GuestCall(target = "java_lang_Class_getName") DirectCallNode classGetName,
                    @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(elements) || StaticObject.isNull(throwable)) {
            profiler.profile(0);
            throw getMeta().throwNullPointerException();
        }
        assert elements.isArray();
        VM.StackTrace stackTrace = (VM.StackTrace) throwable.getHiddenField(getMeta().HIDDEN_FRAMES);
        if (elements.length() != stackTrace.size) {
            profiler.profile(1);
            throw Meta.throwException(getMeta().java_lang_IndexOutOfBoundsException);
        }
        for (int i = 0; i < stackTrace.size; i++) {
            if (StaticObject.isNull(elements.get(i))) {
                profiler.profile(2);
                throw getMeta().throwNullPointerException();
            }
            fillInElement(elements.get(i), stackTrace.trace[i], classGetName);
        }
    }

    private void fillInElement(@Host(StackTraceElement.class) StaticObject ste, VM.StackElement element,
                    DirectCallNode classGetName) {
        Method m = element.getMethod();
        Klass k = m.getDeclaringKlass();
        StaticObject guestClass = k.mirror();
        StaticObject loader = k.getDefiningClassLoader();
        ModuleEntry module = k.module();

        // Fill in class name
        ste.setField(getMeta().java_lang_StackTraceElement_declaringClass, classGetName.call(guestClass));
        ste.setField(getMeta().java_lang_StackTraceElement_declaringClassObject, guestClass);

        // Fill in loader name
        if (!StaticObject.isNull(loader)) {
            StaticObject loaderName = loader.getField(getMeta().java_lang_ClassLoader_name);
            if (!StaticObject.isNull(loader)) {
                ste.setField(getMeta().java_lang_StackTraceElement_classLoaderName, loaderName);
            }
        }

        // Fill in method name
        Symbol<Name> mname = m.getName();
        ste.setField(getMeta().java_lang_StackTraceElement_methodName, getMeta().toGuestString(mname));

        // Fill in module
        if (module.isNamed()) {
            ste.setField(getMeta().java_lang_StackTraceElement_moduleName, getMeta().toGuestString(module.getName()));
            // TODO: module version
        }

        // Fill in source information
        ste.setField(getMeta().java_lang_StackTraceElement_fileName, getMeta().toGuestString(m.getSourceFile()));
        ste.setIntField(getMeta().java_lang_StackTraceElement_lineNumber, m.bciToLineNumber(element.getBCI()));
    }

    private final StackWalk stackWalk;

    private void checkStackWalkArguments(int batchSize, int startIndex, @Host(Object[].class) StaticObject frames) {
        if (StaticObject.isNull(frames)) {
            throw Meta.throwException(getMeta().java_lang_NullPointerException);
        }
        assert frames.isArray();
        int limit = startIndex + batchSize;
        if (frames.length() < limit) {
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "Not enough space in buffers");
        }
    }

    @VmImpl
    @JniImpl
    @TruffleBoundary
    public @Host(Object.class) StaticObject JVM_CallStackWalk(
                    @Host(typeName = "Ljava/lang/StackStreamFactory;") StaticObject stackStream, long mode, int skipframes,
                    int batchSize, int startIndex,
                    @Host(Object[].class) StaticObject frames) {
        checkStackWalkArguments(batchSize, startIndex, frames);
        return stackWalk.fetchFirstBatch(stackStream, mode, skipframes, batchSize, startIndex, frames, getMeta());
    }

    @VmImpl
    @JniImpl
    @TruffleBoundary
    public int JVM_MoreStackWalk(
                    @Host(typeName = "Ljava/lang/StackStreamFactory;") StaticObject stackStream, long mode, long anchor,
                    int batchSize, int startIndex,
                    @Host(Object[].class) StaticObject frames) {
        checkStackWalkArguments(batchSize, startIndex, frames);
        return stackWalk.fetchNextBatch(stackStream, mode, anchor, batchSize, startIndex, frames, getMeta());
    }
    // Checkstyle: resume method name check
}
