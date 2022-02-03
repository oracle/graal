/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.ACCESS_VM_ANNOTATIONS;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.HIDDEN_CLASS;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.NESTMATE_CLASS;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.STRONG_LOADER_LINK;

import java.io.File;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlContext;
import java.security.ProtectionDomain;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

import org.graalvm.collections.EconomicMap;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.attributes.InnerClassesAttribute;
import com.oracle.truffle.espresso.classfile.attributes.MethodParametersAttribute;
import com.oracle.truffle.espresso.classfile.attributes.PermittedSubclassesAttribute;
import com.oracle.truffle.espresso.classfile.attributes.RecordAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.NameAndTypeConstant;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.Validation;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
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
import com.oracle.truffle.espresso.impl.SuppressFBWarnings;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.jni.JniVersion;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.jvmti.JVMTI;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNodeGen;
import com.oracle.truffle.espresso.overlay.ReferenceSupport;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.Classpath;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
import com.oracle.truffle.espresso.runtime.EspressoProperties;
import com.oracle.truffle.espresso.runtime.JavaVersion;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.CallableFromNative;
import com.oracle.truffle.espresso.substitutions.EspressoReference;
import com.oracle.truffle.espresso.substitutions.GenerateNativeEnv;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_System;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_ref_Reference;
import com.oracle.truffle.espresso.threads.State;
import com.oracle.truffle.espresso.vm.structs.JavaVMAttachArgs;
import com.oracle.truffle.espresso.vm.structs.JdkVersionInfo;
import com.oracle.truffle.espresso.vm.structs.Structs;
import com.oracle.truffle.espresso.vm.structs.StructsAccess;

import sun.misc.Unsafe;

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
@GenerateNativeEnv(target = VmImpl.class, reachableForAutoSubstitution = true)
public final class VM extends NativeEnv implements ContextAccess {

    private final @Pointer TruffleObject disposeMokapotContext;

    private final @Pointer TruffleObject getJavaVM;
    private final @Pointer TruffleObject mokapotAttachThread;
    private final @Pointer TruffleObject getPackageAt;

    private final long rtldDefaultValue;
    private final long processHandleValue;

    private final JavaVersion javaVersion;

    private final Structs structs;

    private final JniEnv jniEnv;
    private final Management management;
    private final JVMTI jvmti;

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

    public void attachThread(Thread hostThread) {
        if (hostThread != Thread.currentThread()) {
            getLogger().warning("unimplemented: attachThread for non-current thread: " + hostThread);
            return;
        }
        assert hostThread == Thread.currentThread();
        try {
            getUncached().execute(mokapotAttachThread, mokapotEnvPtr);
            // Initialize external threads e.g. ctype TLS data must be initialized for threads
            // created outside the isolated namespace using the nfi-dlmopen backend.
            getNativeAccess().prepareThread();
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

    @Override
    public JavaVersion getJavaVersion() {
        return javaVersion;
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
        return getNativeAccess().loadLibrary(bootLibraryPath, "java", true);
    }

    private void libJavaOnLoad(TruffleObject libJava) {
        if (getJavaVersion().java8OrEarlier()) {
            // The JNI_OnLoad handling is normally done by method load in
            // java.lang.ClassLoader$NativeLibrary, but the VM loads the base library
            // explicitly so we have to check for JNI_OnLoad as well
            // libjava is initialized after libjvm (Espresso VM native context).

            // TODO(peterssen): Use JVM_FindLibraryEntry.
            TruffleObject jniOnLoad = getNativeAccess().lookupAndBindSymbol(libJava, "JNI_OnLoad", NativeSignature.create(NativeType.INT, NativeType.POINTER, NativeType.POINTER));
            if (jniOnLoad != null) {
                try {
                    getUncached().execute(jniOnLoad, mokapotEnvPtr, RawPointer.nullInstance());
                } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
                    throw EspressoError.shouldNotReachHere(e);
                }
            }
        }
    }

    private JavaVersion findJavaVersion(TruffleObject libJava) {
        // void JDK_GetVersionInfo0(jdk_version_info* info, size_t info_size);
        TruffleObject jdkGetVersionInfo = getNativeAccess().lookupAndBindSymbol(libJava, "JDK_GetVersionInfo0", NativeSignature.create(NativeType.VOID, NativeType.POINTER, NativeType.LONG));
        if (jdkGetVersionInfo != null) {
            JdkVersionInfo.JdkVersionInfoWrapper wrapper = getStructs().jdkVersionInfo.allocate(getNativeAccess(), jni());
            try {
                getUncached().execute(jdkGetVersionInfo, wrapper.pointer(), getStructs().jdkVersionInfo.structSize());
            } catch (UnsupportedTypeException | UnsupportedMessageException | ArityException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
            int versionInfo = wrapper.jdkVersion();
            wrapper.free(getNativeAccess());

            int major = (versionInfo & 0xFF000000) >> 24;
            if (major == 1) {
                // Version 1.X
                int minor = (versionInfo & 0x00FF0000) >> 16;
                return new JavaVersion(minor);
            } else {
                // Version X.Y
                return new JavaVersion(major);
            }
        } else {
            // JDK 14+
            return new JavaVersion(JavaVersion.LATEST_SUPPORTED);
        }
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
            TruffleObject initializeMokapotContext = getNativeAccess().lookupAndBindSymbol(mokapotLibrary, "initializeMokapotContext",
                            NativeSignature.create(NativeType.POINTER, NativeType.POINTER, NativeType.POINTER));

            disposeMokapotContext = getNativeAccess().lookupAndBindSymbol(mokapotLibrary, "disposeMokapotContext",
                            NativeSignature.create(NativeType.VOID, NativeType.POINTER, NativeType.POINTER));

            if (jniEnv.getContext().EnableManagement) {
                management = new Management(getContext(), mokapotLibrary);
            } else {
                management = null;
            }

            structs = StructsAccess.getStructs(getContext(), mokapotLibrary);

            jvmti = new JVMTI(getContext(), mokapotLibrary);

            getJavaVM = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "getJavaVM",
                            NativeSignature.create(NativeType.POINTER, NativeType.POINTER));

            mokapotAttachThread = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "mokapotAttachThread",
                            NativeSignature.create(NativeType.VOID, NativeType.POINTER));

            @Pointer
            TruffleObject mokapotGetRTLDDefault = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "mokapotGetRTLD_DEFAULT",
                            NativeSignature.create(NativeType.POINTER));
            @Pointer
            TruffleObject mokapotGetProcessHandle = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "mokapotGetProcessHandle",
                            NativeSignature.create(NativeType.POINTER));

            getPackageAt = getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "getPackageAt",
                            NativeSignature.create(NativeType.POINTER, NativeType.POINTER, NativeType.INT));
            this.mokapotEnvPtr = initializeAndGetEnv(true, initializeMokapotContext, jniEnv.getNativePointer());
            this.rtldDefaultValue = getUncached().asPointer(getUncached().execute(mokapotGetRTLDDefault));
            this.processHandleValue = getUncached().asPointer(getUncached().execute(mokapotGetProcessHandle));
            getLogger().finest(() -> String.format("Got RTLD_DEFAULT=0x%016x and ProcessHandle=0x%016x", rtldDefaultValue, processHandleValue));
            assert getUncached().isPointer(this.mokapotEnvPtr);
            assert !getUncached().isNull(this.mokapotEnvPtr);

            javaLibrary = loadJavaLibrary(props.bootLibraryPath());
            javaVersion = findJavaVersion(javaLibrary);
            libJavaOnLoad(javaLibrary);

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

    public Structs getStructs() {
        return structs;
    }

    public JVMTI getJvmti() {
        return jvmti;
    }

    private static final List<CallableFromNative.Factory> VM_IMPL_FACTORIES = VmImplCollector.getInstances(CallableFromNative.Factory.class);
    private static final int VM_LOOKUP_CALLBACK_ARGS = 2;

    /**
     * Maps native function pointers to node factories for VM methods.
     */
    private EconomicMap<Long, CallableFromNative.Factory> knownVmMethods = EconomicMap.create();

    @Override
    protected List<CallableFromNative.Factory> getCollector() {
        return VM_IMPL_FACTORIES;
    }

    @Override
    protected int lookupCallBackArgsCount() {
        return VM_LOOKUP_CALLBACK_ARGS;
    }

    @Override
    protected NativeSignature lookupCallbackSignature() {
        return NativeSignature.create(NativeType.POINTER, NativeType.POINTER, NativeType.POINTER);
    }

    /**
     * Registers this known VM method's function pointer. Later native method bindings can perform a
     * lookup when trying to bind to a function pointer, and if a match happens, this is a known VM
     * method, and we can link directly to it thus bypassing native calls.
     *
     * @param name The name of the VM method, previously extracted from {@code args[0]}.
     * @param factory The node factory of the requested VM method.
     * @param args A length {@linkplain #lookupCallBackArgsCount() 2} arguments array: At position 0
     *            is a native pointer to the name of the method. At position 1 is the address of the
     *            {@code JVM_*} symbol exported by {@code mokapot}.
     */
    @Override
    @TruffleBoundary
    protected void processCallBackResult(String name, CallableFromNative.Factory factory, Object... args) {
        assert args.length == lookupCallBackArgsCount();
        try {
            InteropLibrary uncached = InteropLibrary.getUncached();
            Object ptr = args[1];
            if (factory != null && !uncached.isNull(ptr) && uncached.isPointer(ptr)) {
                long jvmMethodAddress = uncached.asPointer(ptr);
                knownVmMethods.put(jvmMethodAddress, factory);
            }
        } catch (UnsupportedMessageException e) {
            /* Ignore */
        }
    }

    @TruffleBoundary
    public CallableFromNative.Factory lookupKnownVmMethod(long functionPointer) {
        return knownVmMethods.get(functionPointer);
    }

    public static VM create(JniEnv jniEnv) {
        return new VM(jniEnv);
    }

    public void dispose() {
        assert !getUncached().isNull(mokapotEnvPtr) : "Mokapot already disposed";
        try {
            if (management != null) {
                assert getContext().EnableManagement;
                management.dispose();
            }
            if (jvmti != null) {
                jvmti.dispose();
            }
            getUncached().execute(disposeMokapotContext, mokapotEnvPtr, RawPointer.nullInstance());
            this.mokapotEnvPtr = RawPointer.nullInstance();
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot dispose Espresso libjvm (mokapot).");
        }
        assert getUncached().isNull(mokapotEnvPtr);
    }

    private StaticObject nonReflectionClassLoader(StaticObject loader) {
        if (StaticObject.notNull(loader)) {
            Meta meta = getMeta();
            if (meta.sun_reflect_DelegatingClassLoader.isAssignableFrom(loader.getKlass())) {
                return meta.java_lang_ClassLoader_parent.getObject(loader);
            }
        }
        return loader;
    }

    // Checkstyle: stop method name check

    // region system

    @VmImpl(isJni = true)
    // SVM windows has System.currentTimeMillis() blocked for PE.
    @TruffleBoundary(allowInlining = true)
    public static long JVM_CurrentTimeMillis(
                    @SuppressWarnings("unused") @JavaType(Class/* <System> */.class) StaticObject ignored) {
        return System.currentTimeMillis();
    }

    @VmImpl(isJni = true)
    public static long JVM_NanoTime(@SuppressWarnings("unused") @JavaType(Class/* <System> */.class) StaticObject ignored) {
        return System.nanoTime();
    }

    @TruffleBoundary(allowInlining = true)
    @VmImpl(isJni = true)
    public static int JVM_IHashCode(@JavaType(Object.class) StaticObject object) {
        /*
         * On SVM + Windows, the System.identityHashCode substitution calls methods blocked for PE
         * (System.currentTimeMillis?).
         */
        return System.identityHashCode(MetaUtil.maybeUnwrapNull(object));
    }

    @VmImpl(isJni = true)
    public static void JVM_ArrayCopy(@SuppressWarnings("unused") @JavaType(Class/* <System> */.class) StaticObject ignored,
                    @JavaType(Object.class) StaticObject src, int srcPos, @JavaType(Object.class) StaticObject dest, int destPos, int length,
                    @Inject Meta meta, @Inject SubstitutionProfiler profile) {
        Target_java_lang_System.arraycopy(src, srcPos, dest, destPos, length, meta, profile);
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

    @VmImpl(isJni = true)
    public @JavaType(String.class) StaticObject JVM_GetSystemPackage(@JavaType(String.class) StaticObject name) {
        String hostPkgName = getMeta().toHostString(name);
        if (hostPkgName.endsWith("/")) {
            hostPkgName = hostPkgName.substring(0, hostPkgName.length() - 1);
        }
        String fileName = getRegistries().getBootClassRegistry().getPackagePath(hostPkgName);
        return getMeta().toGuestString(fileName);
    }

    @VmImpl(isJni = true)
    public @JavaType(String[].class) StaticObject JVM_GetSystemPackages() {
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

    @VmImpl
    @TruffleBoundary(allowInlining = true)
    public static int JVM_ActiveProcessorCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    @VmImpl
    public static boolean JVM_IsNaN(double d) {
        return Double.isNaN(d);
    }

    @VmImpl
    @TruffleBoundary
    public static boolean JVM_SupportsCX8() {
        try {
            java.lang.reflect.Field field = AtomicLong.class.getDeclaredField("VM_SUPPORTS_LONG_CAS");
            Unsafe unsafe = UnsafeAccess.get();
            return unsafe.getBoolean(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field));
        } catch (NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @VmImpl(isJni = true)
    public @JavaType(String.class) StaticObject JVM_InternString(@JavaType(String.class) StaticObject self) {
        return getInterpreterToVM().intern(self);
    }

    // endregion system

    // region objects

    private static Object readForeignArrayElement(StaticObject array, int index, InteropLibrary interop,
                    Meta meta, SubstitutionProfiler profiler, char exceptionBranch) {
        try {
            return interop.readArrayElement(array.rawForeignObject(), index);
        } catch (UnsupportedMessageException e) {
            profiler.profile(exceptionBranch);
            throw meta.throwExceptionWithMessage(meta.getMeta().java_lang_ClassCastException, "The foreign object is not a readable array");
        } catch (InvalidArrayIndexException e) {
            profiler.profile(exceptionBranch);
            throw meta.throwExceptionWithMessage(meta.java_lang_CloneNotSupportedException, "Foreign array length changed during clone");
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
                throw meta.throwExceptionWithMessage(meta.java_lang_CloneNotSupportedException, "Cannot clone a foreign array whose length does not fit in int");
            }
            if (longLength < 0) {
                profiler.profile(exceptionBranch);
                throw meta.throwExceptionWithMessage(meta.java_lang_NegativeArraySizeException, "Cannot clone a foreign array with negative length");
            }
            length = (int) longLength;
        } catch (UnsupportedMessageException e) {
            profiler.profile(exceptionBranch);
            throw meta.throwExceptionWithMessage(meta.java_lang_CloneNotSupportedException, "Cannot clone a non-array foreign object as an array");
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
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast an element of a foreign array to the declared component type");
            }
        }
        StaticObject[] newArray = new StaticObject[length];
        for (int i = 0; i < length; ++i) {
            Object foreignElement = readForeignArrayElement(array, i, interop, meta, profiler, exceptionBranch);

            try {
                newArray[i] = (StaticObject) toEspressoNode.execute(foreignElement, componentType);
            } catch (UnsupportedTypeException e) {
                profiler.profile(exceptionBranch);
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Cannot cast an element of a foreign array to the declared component type");
            }
        }
        return StaticObject.createArray(arrayKlass, newArray);
    }

    @VmImpl(isJni = true)
    public static @JavaType(Object.class) StaticObject JVM_Clone(@JavaType(Object.class) StaticObject self,
                    @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
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
            throw meta.throwExceptionWithMessage(meta.java_lang_CloneNotSupportedException, "Clone not supported for non-array foreign objects");
        }

        if (!meta.java_lang_Cloneable.isAssignableFrom(self.getKlass())) {
            profiler.profile(0);
            throw meta.throwException(meta.java_lang_CloneNotSupportedException);
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
                throw meta.throwExceptionWithMessage(meta.java_lang_CloneNotSupportedException, self.getKlass().getName().toString());
            }
        }

        final StaticObject clone = self.copy();

        // If the original object is finalizable, so is the copy.
        assert self.getKlass() instanceof ObjectKlass;
        if (((ObjectKlass) self.getKlass()).hasFinalizer()) {
            profiler.profile(2);
            meta.java_lang_ref_Finalizer_register.invokeDirect(null, clone);
        }

        return clone;
    }

    @VmImpl(isJni = true)
    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, .notifyAll is just forwarded from the guest.")
    public void JVM_MonitorNotifyAll(@JavaType(Object.class) StaticObject self, @Inject SubstitutionProfiler profiler) {
        try {
            InterpreterToVM.monitorNotifyAll(self.getLock());
        } catch (IllegalMonitorStateException e) {
            profiler.profile(0);
            Meta meta = getMeta();
            throw meta.throwException(meta.java_lang_IllegalMonitorStateException);
        }
    }

    @VmImpl(isJni = true)
    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, .notify is just forwarded from the guest.")
    public void JVM_MonitorNotify(@JavaType(Object.class) StaticObject self, @Inject SubstitutionProfiler profiler) {
        try {
            InterpreterToVM.monitorNotify(self.getLock());
        } catch (IllegalMonitorStateException e) {
            profiler.profile(0);
            Meta meta = getMeta();
            throw meta.throwException(meta.java_lang_IllegalMonitorStateException);
        }
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, .wait is just forwarded from the guest.")
    public void JVM_MonitorWait(@JavaType(Object.class) StaticObject self, long timeout,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {

        EspressoContext context = getContext();
        StaticObject currentThread = context.getCurrentThread();
        try {
            meta.getThreadAccess().fromRunnable(currentThread, (timeout > 0 ? State.TIMED_WAITING : State.WAITING));
            if (context.EnableManagement) {
                // Locks bookkeeping.
                meta.HIDDEN_THREAD_BLOCKED_OBJECT.setHiddenObject(currentThread, self);
                Target_java_lang_Thread.incrementThreadCounter(currentThread, meta.HIDDEN_THREAD_WAITED_COUNT);
            }
            final boolean report = context.shouldReportVMEvents();
            if (report) {
                context.reportMonitorWait(self, timeout);
            }
            boolean timedOut = !InterpreterToVM.monitorWait(self.getLock(), timeout);
            if (report) {
                context.reportMonitorWaited(self, timedOut);
            }
        } catch (InterruptedException e) {
            profiler.profile(0);
            if (getThreadAccess().isInterrupted(currentThread, true)) {
                throw meta.throwExceptionWithMessage(meta.java_lang_InterruptedException, e.getMessage());
            }
            getThreadAccess().checkDeprecation();
        } catch (IllegalMonitorStateException e) {
            profiler.profile(1);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalMonitorStateException, e.getMessage());
        } catch (IllegalArgumentException e) {
            profiler.profile(2);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, e.getMessage());
        } finally {
            if (context.EnableManagement) {
                meta.HIDDEN_THREAD_BLOCKED_OBJECT.setHiddenObject(currentThread, null);
            }
            meta.getThreadAccess().toRunnable(currentThread);
        }
    }

    // endregion objects

    // region class

    @VmImpl(isJni = true)
    public int JVM_GetClassModifiers(@JavaType(Class.class) StaticObject clazz) {
        Klass klass = clazz.getMirrorKlass(getMeta());
        if (klass.isPrimitive()) {
            final int primitiveModifiers = ACC_ABSTRACT | ACC_FINAL | ACC_PUBLIC;
            assert klass.getClassModifiers() == primitiveModifiers;
            return klass.getClassModifiers();
        }
        return klass.getClassModifiers();
    }

    @VmImpl(isJni = true)
    public @JavaType(String.class) StaticObject JVM_InitClassName(@JavaType(Class.class) StaticObject self) {
        StaticObject name = JVM_GetClassName(self);
        getMeta().java_lang_Class_name.set(self, name);
        return name;
    }

    @VmImpl(isJni = true)
    public @JavaType(Class[].class) StaticObject JVM_GetClassInterfaces(@JavaType(Class.class) StaticObject self) {
        final Klass[] superInterfaces = self.getMirrorKlass().getImplementedInterfaces();

        StaticObject instance = getMeta().java_lang_Class.allocateReferenceArray(superInterfaces.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                return superInterfaces[i].mirror();
            }
        });

        return instance;
    }

    @VmImpl(isJni = true)
    public boolean JVM_IsInterface(@JavaType(Class.class) StaticObject self) {
        return self.getMirrorKlass(getMeta()).isInterface();
    }

    @VmImpl(isJni = true)
    public @JavaType(Object[].class) StaticObject JVM_GetClassSigners(@JavaType(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass.isPrimitive()) {
            return StaticObject.NULL;
        }
        StaticObject signersArray = (StaticObject) getMeta().HIDDEN_SIGNERS.getHiddenObject(self);
        if (signersArray == null || StaticObject.isNull(signersArray)) {
            return StaticObject.NULL;
        }
        return signersArray.copy();
    }

    @VmImpl(isJni = true)
    public void JVM_SetClassSigners(@JavaType(Class.class) StaticObject self, @JavaType(Object[].class) StaticObject signers) {
        Klass klass = self.getMirrorKlass();
        if (!klass.isPrimitive() && !klass.isArray()) {
            getMeta().HIDDEN_SIGNERS.setHiddenObject(self, signers);
        }
    }

    @VmImpl(isJni = true)
    public boolean JVM_IsArrayClass(@JavaType(Class.class) StaticObject self) {
        return self.getMirrorKlass(getMeta()).isArray();
    }

    @VmImpl(isJni = true)
    public boolean JVM_IsHiddenClass(@JavaType(Class.class) StaticObject self) {
        return self.getMirrorKlass(getMeta()).isHidden();
    }

    @VmImpl(isJni = true)
    public boolean JVM_IsPrimitiveClass(@JavaType(Class.class) StaticObject self) {
        return self.getMirrorKlass(getMeta()).isPrimitive();
    }

    @VmImpl(isJni = true)
    public @JavaType(java.lang.reflect.Field[].class) StaticObject JVM_GetClassDeclaredFields(@JavaType(Class.class) StaticObject self, boolean publicOnly) {

        // TODO(peterssen): From Hostpot: 4496456 We need to filter out
        // java.lang.Throwable.backtrace.
        Meta meta = getMeta();
        ArrayList<Field> collectedMethods = new ArrayList<>();
        Klass klass = self.getMirrorKlass();
        klass.ensureLinked();
        for (Field f : klass.getDeclaredFields()) {
            if (!publicOnly || f.isPublic()) {
                collectedMethods.add(f);
            }
        }
        final Field[] fields = collectedMethods.toArray(Field.EMPTY_ARRAY);

        EspressoContext context = meta.getContext();

        // TODO(peterssen): Cache guest j.l.reflect.Field constructor.
        // Calling the constructor is just for validation, manually setting the fields would be
        // faster.
        Method fieldInit;
        if (meta.getJavaVersion().java15OrLater()) {
            fieldInit = meta.java_lang_reflect_Field.lookupDeclaredMethod(Name._init_, context.getSignatures().makeRaw(Type._void,
                            /* declaringClass */ Type.java_lang_Class,
                            /* name */ Type.java_lang_String,
                            /* type */ Type.java_lang_Class,
                            /* modifiers */ Type._int,
                            /* trustedFinal */ Type._boolean,
                            /* slot */ Type._int,
                            /* signature */ Type.java_lang_String,
                            /* annotations */ Type._byte_array));
        } else {
            fieldInit = meta.java_lang_reflect_Field.lookupDeclaredMethod(Name._init_, context.getSignatures().makeRaw(Type._void,
                            /* declaringClass */ Type.java_lang_Class,
                            /* name */ Type.java_lang_String,
                            /* type */ Type.java_lang_Class,
                            /* modifiers */ Type._int,
                            /* slot */ Type._int,
                            /* signature */ Type.java_lang_String,
                            /* annotations */ Type._byte_array));
        }
        StaticObject fieldsArray = meta.java_lang_reflect_Field.allocateReferenceArray(fields.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                final Field f = fields[i];
                StaticObject instance = meta.java_lang_reflect_Field.allocateInstance();

                Attribute rawRuntimeVisibleAnnotations = f.getAttribute(Name.RuntimeVisibleAnnotations);
                StaticObject runtimeVisibleAnnotations = rawRuntimeVisibleAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleAnnotations.getData(), meta)
                                : StaticObject.NULL;

                Attribute rawRuntimeVisibleTypeAnnotations = f.getAttribute(Name.RuntimeVisibleTypeAnnotations);
                StaticObject runtimeVisibleTypeAnnotations = rawRuntimeVisibleTypeAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleTypeAnnotations.getData(), meta)
                                : StaticObject.NULL;
                if (meta.getJavaVersion().java15OrLater()) {
                    fieldInit.invokeDirect(
                                    /* this */ instance,
                                    /* declaringKlass */ f.getDeclaringKlass().mirror(),
                                    /* name */ context.getStrings().intern(f.getName()),
                                    /* type */ f.resolveTypeKlass().mirror(),
                                    /* modifiers */ f.getModifiers(),
                                    /* trustedFinal */ f.isTrustedFinal(),
                                    /* slot */ f.getSlot(),
                                    /* signature */ meta.toGuestString(f.getGenericSignature()),
                                    // FIXME(peterssen): Fill annotations bytes.
                                    /* annotations */ runtimeVisibleAnnotations);
                } else {
                    fieldInit.invokeDirect(
                                    /* this */ instance,
                                    /* declaringKlass */ f.getDeclaringKlass().mirror(),
                                    /* name */ context.getStrings().intern(f.getName()),
                                    /* type */ f.resolveTypeKlass().mirror(),
                                    /* modifiers */ f.getModifiers(),
                                    /* slot */ f.getSlot(),
                                    /* signature */ meta.toGuestString(f.getGenericSignature()),
                                    // FIXME(peterssen): Fill annotations bytes.
                                    /* annotations */ runtimeVisibleAnnotations);
                }
                meta.HIDDEN_FIELD_KEY.setHiddenObject(instance, f);
                meta.HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.setHiddenObject(instance, runtimeVisibleTypeAnnotations);
                return instance;
            }
        });

        return fieldsArray;
    }

    // TODO(tg): inject constructor calltarget.
    @VmImpl(isJni = true)
    public @JavaType(Constructor[].class) StaticObject JVM_GetClassDeclaredConstructors(@JavaType(Class.class) StaticObject self, boolean publicOnly) {
        Meta meta = getMeta();
        ArrayList<Method> collectedMethods = new ArrayList<>();
        Klass klass = self.getMirrorKlass();
        klass.ensureLinked();
        for (Method m : klass.getDeclaredConstructors()) {
            if (Name._init_.equals(m.getName()) && (!publicOnly || m.isPublic())) {
                collectedMethods.add(m);
            }
        }
        final Method[] constructors = collectedMethods.toArray(Method.EMPTY_ARRAY);

        EspressoContext context = meta.getContext();

        // TODO(peterssen): Cache guest j.l.reflect.Constructor constructor.
        // Calling the constructor is just for validation, manually setting the fields would be
        // faster.
        Method constructorInit = meta.java_lang_reflect_Constructor.lookupDeclaredMethod(Name._init_, context.getSignatures().makeRaw(Type._void,
                        /* declaringClass */ Type.java_lang_Class,
                        /* parameterTypes */ Type.java_lang_Class_array,
                        /* checkedExceptions */ Type.java_lang_Class_array,
                        /* modifiers */ Type._int,
                        /* slot */ Type._int,
                        /* signature */ Type.java_lang_String,
                        /* annotations */ Type._byte_array,
                        /* parameterAnnotations */ Type._byte_array));

        StaticObject arr = meta.java_lang_reflect_Constructor.allocateReferenceArray(constructors.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                final Method m = constructors[i];

                Attribute rawRuntimeVisibleAnnotations = m.getAttribute(Name.RuntimeVisibleAnnotations);
                StaticObject runtimeVisibleAnnotations = rawRuntimeVisibleAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleAnnotations.getData(), meta)
                                : StaticObject.NULL;

                Attribute rawRuntimeVisibleParameterAnnotations = m.getAttribute(Name.RuntimeVisibleParameterAnnotations);
                StaticObject runtimeVisibleParameterAnnotations = rawRuntimeVisibleParameterAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleParameterAnnotations.getData(), meta)
                                : StaticObject.NULL;

                Attribute rawRuntimeVisibleTypeAnnotations = m.getAttribute(Name.RuntimeVisibleTypeAnnotations);
                StaticObject runtimeVisibleTypeAnnotations = rawRuntimeVisibleTypeAnnotations != null
                                ? StaticObject.wrap(rawRuntimeVisibleTypeAnnotations.getData(), meta)
                                : StaticObject.NULL;

                final Klass[] rawParameterKlasses = m.resolveParameterKlasses();
                StaticObject parameterTypes = meta.java_lang_Class.allocateReferenceArray(
                                m.getParameterCount(),
                                new IntFunction<StaticObject>() {
                                    @Override
                                    public StaticObject apply(int j) {
                                        return rawParameterKlasses[j].mirror();
                                    }
                                });

                final Klass[] rawCheckedExceptions = m.getCheckedExceptions();
                StaticObject checkedExceptions = meta.java_lang_Class.allocateReferenceArray(rawCheckedExceptions.length, new IntFunction<StaticObject>() {
                    @Override
                    public StaticObject apply(int j) {
                        return rawCheckedExceptions[j].mirror();
                    }
                });

                SignatureAttribute signatureAttribute = (SignatureAttribute) m.getAttribute(Name.Signature);
                StaticObject genericSignature = StaticObject.NULL;
                if (signatureAttribute != null) {
                    String sig = m.getConstantPool().symbolAt(signatureAttribute.getSignatureIndex(), "signature").toString();
                    genericSignature = meta.toGuestString(sig);
                }

                StaticObject instance = meta.java_lang_reflect_Constructor.allocateInstance();
                constructorInit.invokeDirect(
                                /* this */ instance,
                                /* declaringKlass */ m.getDeclaringKlass().mirror(),
                                /* parameterTypes */ parameterTypes,
                                /* checkedExceptions */ checkedExceptions,
                                /* modifiers */ m.getMethodModifiers(),
                                /* slot */ i, // TODO(peterssen): Fill method slot.
                                /* signature */ genericSignature,

                                // FIXME(peterssen): Fill annotations bytes.
                                /* annotations */ runtimeVisibleAnnotations,
                                /* parameterAnnotations */ runtimeVisibleParameterAnnotations);

                meta.HIDDEN_CONSTRUCTOR_KEY.setHiddenObject(instance, m);
                meta.HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.setHiddenObject(instance, runtimeVisibleTypeAnnotations);

                return instance;
            }
        });

        return arr;
    }

    // TODO(tg): inject constructor calltarget.
    @VmImpl(isJni = true)
    public @JavaType(java.lang.reflect.Method[].class) StaticObject JVM_GetClassDeclaredMethods(@JavaType(Class.class) StaticObject self, boolean publicOnly) {
        Meta meta = getMeta();
        ArrayList<Method> collectedMethods = new ArrayList<>();
        Klass klass = self.getMirrorKlass();
        klass.ensureLinked();
        for (Method m : klass.getDeclaredMethods()) {
            if ((!publicOnly || m.isPublic()) &&
                            // Filter out <init> and <clinit> from reflection.
                            !Name._init_.equals(m.getName()) && !Name._clinit_.equals(m.getName())) {
                collectedMethods.add(m);
            }
        }
        final Method[] methods = collectedMethods.toArray(Method.EMPTY_ARRAY);

        return meta.java_lang_reflect_Method.allocateReferenceArray(methods.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                return methods[i].makeMirror();
            }
        });

    }

    @VmImpl(isJni = true)
    public @JavaType(Class[].class) StaticObject JVM_GetDeclaredClasses(@JavaType(Class.class) StaticObject self) {
        Meta meta = getMeta();
        Klass klass = self.getMirrorKlass();
        if (klass.isPrimitive() || klass.isArray()) {
            return meta.java_lang_Class.allocateReferenceArray(0);
        }
        ObjectKlass instanceKlass = (ObjectKlass) klass;
        InnerClassesAttribute innerClasses = (InnerClassesAttribute) instanceKlass.getAttribute(InnerClassesAttribute.NAME);

        if (innerClasses == null || innerClasses.entries().length == 0) {
            return meta.java_lang_Class.allocateReferenceArray(0);
        }

        RuntimeConstantPool pool = instanceKlass.getConstantPool();
        List<Klass> innerKlasses = new ArrayList<>();

        for (InnerClassesAttribute.Entry entry : innerClasses.entries()) {
            if (entry.innerClassIndex != 0 && entry.outerClassIndex != 0) {
                // Check to see if the name matches the class we're looking for
                // before attempting to find the class.
                Symbol<Name> outerDescriptor = pool.classAt(entry.outerClassIndex).getName(pool);

                // Check decriptors/names before resolving.
                if (outerDescriptor.equals(instanceKlass.getName())) {
                    Klass outerKlass = pool.resolvedKlassAt(instanceKlass, entry.outerClassIndex);
                    if (outerKlass == instanceKlass) {
                        Klass innerKlass = pool.resolvedKlassAt(instanceKlass, entry.innerClassIndex);
                        // HotSpot:
                        // Throws an exception if outer klass has not declared k as
                        // an inner klass
                        // Reflection::check_for_inner_class(k, inner_klass, true, CHECK_NULL);
                        // TODO(peterssen): The check in HotSpot is redundant.
                        innerKlasses.add(innerKlass);
                    }
                }
            }
        }

        return meta.java_lang_Class.allocateReferenceArray(innerKlasses.size(), new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int index) {
                return innerKlasses.get(index).mirror();
            }
        });
    }

    /**
     * Return the enclosing class; or null for: primitives, arrays, anonymous classes (declared
     * inside methods).
     */
    private static Klass computeEnclosingClass(ObjectKlass klass) {
        InnerClassesAttribute innerClasses = (InnerClassesAttribute) klass.getAttribute(InnerClassesAttribute.NAME);
        if (innerClasses == null) {
            return null;
        }

        RuntimeConstantPool pool = klass.getConstantPool();

        boolean found = false;
        Klass outerKlass = null;

        for (InnerClassesAttribute.Entry entry : innerClasses.entries()) {
            if (entry.innerClassIndex != 0) {
                Symbol<Name> innerDescriptor = pool.classAt(entry.innerClassIndex).getName(pool);

                // Check decriptors/names before resolving.
                if (innerDescriptor.equals(klass.getName())) {
                    Klass innerKlass = pool.resolvedKlassAt(klass, entry.innerClassIndex);
                    found = (innerKlass == klass);
                    if (found && entry.outerClassIndex != 0) {
                        outerKlass = pool.resolvedKlassAt(klass, entry.outerClassIndex);
                    }
                }
            }
            if (found) {
                break;
            }
        }

        // TODO(peterssen): Follow HotSpot implementation described below.
        // Throws an exception if outer klass has not declared k as an inner klass
        // We need evidence that each klass knows about the other, or else
        // the system could allow a spoof of an inner class to gain access rights.
        return outerKlass;
    }

    @VmImpl(isJni = true)
    public static @JavaType(Class.class) StaticObject JVM_GetDeclaringClass(@JavaType(Class.class) StaticObject self) {
        // Primitives and arrays are not "enclosed".
        if (!(self.getMirrorKlass() instanceof ObjectKlass)) {
            return StaticObject.NULL;
        }
        ObjectKlass k = (ObjectKlass) self.getMirrorKlass();
        Klass outerKlass = computeEnclosingClass(k);
        if (outerKlass == null) {
            return StaticObject.NULL;
        }
        return outerKlass.mirror();
    }

    @VmImpl(isJni = true)
    public @JavaType(String.class) StaticObject JVM_GetSimpleBinaryName(@JavaType(Class.class) StaticObject self) {
        Klass k = self.getMirrorKlass();
        if (k.isPrimitive() || k.isArray()) {
            return StaticObject.NULL;
        }
        ObjectKlass klass = (ObjectKlass) k;
        RuntimeConstantPool pool = klass.getConstantPool();
        InnerClassesAttribute inner = klass.getInnerClasses();
        for (InnerClassesAttribute.Entry entry : inner.entries()) {
            int innerClassIndex = entry.innerClassIndex;
            if (innerClassIndex != 0) {
                if (pool.classAt(innerClassIndex).getName(pool) == klass.getName()) {
                    if (pool.resolvedKlassAt(k, innerClassIndex) == k) {
                        if (entry.innerNameIndex != 0) {
                            Symbol<Name> innerName = pool.symbolAt(entry.innerNameIndex);
                            return getMeta().toGuestString(innerName);
                        } else {
                            break;
                        }
                    }
                }
            }
        }
        return StaticObject.NULL;
    }

    @VmImpl(isJni = true)
    public @JavaType(String.class) StaticObject JVM_GetClassSignature(@JavaType(Class.class) StaticObject self) {
        if (self.getMirrorKlass() instanceof ObjectKlass) {
            ObjectKlass klass = (ObjectKlass) self.getMirrorKlass();
            SignatureAttribute signature = (SignatureAttribute) klass.getAttribute(Name.Signature);
            if (signature != null) {
                String sig = klass.getConstantPool().symbolAt(signature.getSignatureIndex(), "signature").toString();
                return getMeta().toGuestString(sig);
            }
        }
        return StaticObject.NULL;
    }

    @VmImpl(isJni = true)
    public @JavaType(byte[].class) StaticObject JVM_GetClassAnnotations(@JavaType(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass instanceof ObjectKlass) {
            Attribute annotations = ((ObjectKlass) klass).getAttribute(Name.RuntimeVisibleAnnotations);
            if (annotations != null) {
                return StaticObject.wrap(annotations.getData(), getMeta());
            }
        }
        return StaticObject.NULL;
    }

    @VmImpl(isJni = true)
    public @JavaType(byte[].class) StaticObject JVM_GetClassTypeAnnotations(@JavaType(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass instanceof ObjectKlass) {
            Attribute annotations = ((ObjectKlass) klass).getAttribute(Name.RuntimeVisibleTypeAnnotations);
            if (annotations != null) {
                return StaticObject.wrap(annotations.getData(), getMeta());
            }
        }
        return StaticObject.NULL;
    }

    @VmImpl(isJni = true)
    public @JavaType(internalName = "Lsun/reflect/ConstantPool;") StaticObject JVM_GetClassConstantPool(@JavaType(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass.isArray() || klass.isPrimitive()) {
            // No constant pool for arrays and primitives.
            return StaticObject.NULL;
        }
        StaticObject cp = InterpreterToVM.newObject(getMeta().sun_reflect_ConstantPool, false);
        getMeta().sun_reflect_ConstantPool_constantPoolOop.setObject(cp, self);
        return cp;
    }

    @TruffleBoundary
    @VmImpl(isJni = true)
    public boolean JVM_DesiredAssertionStatus(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused, @JavaType(Class.class) StaticObject clazz) {
        if (StaticObject.isNull(clazz.getMirrorKlass().getDefiningClassLoader())) {
            return EspressoOptions.EnableSystemAssertions.getValue(getMeta().getContext().getEnv().getOptions());
        }
        return EspressoOptions.EnableAssertions.getValue(getMeta().getContext().getEnv().getOptions());
    }

    @VmImpl(isJni = true)
    public @JavaType(Object[].class) StaticObject JVM_GetEnclosingMethodInfo(@JavaType(Class.class) StaticObject self) {
        Meta meta = getMeta();
        InterpreterToVM vm = meta.getInterpreterToVM();
        if (self.getMirrorKlass() instanceof ObjectKlass) {
            ObjectKlass klass = (ObjectKlass) self.getMirrorKlass();
            EnclosingMethodAttribute enclosingMethodAttr = klass.getEnclosingMethod();
            if (enclosingMethodAttr == null) {
                return StaticObject.NULL;
            }
            int classIndex = enclosingMethodAttr.getClassIndex();
            if (classIndex == 0) {
                return StaticObject.NULL;
            }
            StaticObject arr = meta.java_lang_Object.allocateReferenceArray(3);
            RuntimeConstantPool pool = klass.getConstantPool();
            Klass enclosingKlass = pool.resolvedKlassAt(klass, classIndex);

            vm.setArrayObject(enclosingKlass.mirror(), 0, arr);

            int methodIndex = enclosingMethodAttr.getMethodIndex();
            if (methodIndex != 0) {
                NameAndTypeConstant nmt = pool.nameAndTypeAt(methodIndex);
                StaticObject name = meta.toGuestString(nmt.getName(pool));
                StaticObject desc = meta.toGuestString(nmt.getDescriptor(pool));

                vm.setArrayObject(name, 1, arr);
                vm.setArrayObject(desc, 2, arr);
            }

            return arr;
        }
        return StaticObject.NULL;
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public @JavaType(internalName = "[Ljava/lang/reflect/RecordComponent;") StaticObject JVM_GetRecordComponents(@JavaType(Class.class) StaticObject self) {
        Klass k = self.getMirrorKlass();
        if (!(k instanceof ObjectKlass)) {
            return StaticObject.NULL;
        }
        ObjectKlass klass = (ObjectKlass) k;
        RecordAttribute record = (RecordAttribute) klass.getAttribute(RecordAttribute.NAME);
        if (record == null) {
            return StaticObject.NULL;
        }
        RecordAttribute.RecordComponentInfo[] components = record.getComponents();
        return getMeta().java_lang_reflect_RecordComponent.allocateReferenceArray(components.length, (i) -> components[i].toGuestComponent(getMeta(), klass));
    }

    @VmImpl(isJni = true)
    public static boolean JVM_IsRecord(@JavaType(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        if (klass instanceof ObjectKlass) {
            return ((ObjectKlass) klass).isRecord();
        }
        return false;
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public @JavaType(Class[].class) StaticObject JVM_GetPermittedSubclasses(@JavaType(Class.class) StaticObject self) {
        Klass k = self.getMirrorKlass();
        if (!(k instanceof ObjectKlass)) {
            return StaticObject.NULL;
        }
        ObjectKlass klass = (ObjectKlass) k;
        if (!klass.isSealed()) {
            return StaticObject.NULL;
        }
        char[] classes = ((PermittedSubclassesAttribute) klass.getAttribute(PermittedSubclassesAttribute.NAME)).getClasses();
        StaticObject[] permittedSubclasses = new StaticObject[classes.length];
        RuntimeConstantPool pool = klass.getConstantPool();
        int nClasses = 0;
        for (int index : classes) {
            Klass permitted;
            try {
                permitted = pool.resolvedKlassAt(klass, index);
            } catch (EspressoException e) {
                /* Suppress and continue */
                continue;
            }
            if (permitted instanceof ObjectKlass) {
                permittedSubclasses[nClasses++] = permitted.mirror();
            }
        }
        if (nClasses == permittedSubclasses.length) {
            return StaticObject.createArray(getMeta().java_lang_Class_array, permittedSubclasses);
        }
        return getMeta().java_lang_Class.allocateReferenceArray(nClasses, (i) -> permittedSubclasses[i]);
    }

    @VmImpl(isJni = true)
    public static int JVM_GetClassAccessFlags(@JavaType(Class.class) StaticObject clazz) {
        Klass klass = clazz.getMirrorKlass();
        if (klass.isPrimitive()) {
            final int primitiveFlags = ACC_ABSTRACT | ACC_FINAL | ACC_PUBLIC;
            assert klass.getModifiers() == primitiveFlags;
            return klass.getModifiers();
        }
        return klass.getModifiers() & Constants.JVM_ACC_WRITTEN_FLAGS;
    }

    @VmImpl(isJni = true)
    public static boolean JVM_AreNestMates(@JavaType(Class.class) StaticObject current, @JavaType(Class.class) StaticObject member) {
        return current.getMirrorKlass().nest() == member.getMirrorKlass().nest();
    }

    @VmImpl(isJni = true)
    public static @JavaType(Class.class) StaticObject JVM_GetNestHost(@JavaType(Class.class) StaticObject current) {
        return current.getMirrorKlass().nest().mirror();
    }

    @VmImpl(isJni = true)
    public @JavaType(Class[].class) StaticObject JVM_GetNestMembers(@JavaType(Class.class) StaticObject current) {
        Klass k = current.getMirrorKlass();
        Klass[] nestMembers = k.getNestMembers();
        StaticObject[] array = new StaticObject[nestMembers.length];
        for (int i = 0; i < nestMembers.length; i++) {
            array[i] = nestMembers[i].mirror();
        }
        return StaticObject.createArray(getMeta().java_lang_Class_array, array);
    }

    @VmImpl(isJni = true)
    public @JavaType(ProtectionDomain.class) StaticObject JVM_GetProtectionDomain(@JavaType(Class.class) StaticObject current) {
        if (StaticObject.isNull(current)) {
            return StaticObject.NULL;
        }
        StaticObject pd = (StaticObject) getMeta().HIDDEN_PROTECTION_DOMAIN.getHiddenObject(current);
        return pd == null ? StaticObject.NULL : pd;
    }

    @VmImpl(isJni = true)
    public @JavaType(String.class) StaticObject JVM_GetClassName(@JavaType(Class.class) StaticObject self) {
        Klass klass = self.getMirrorKlass();
        // Conversion from internal form.
        String externalName = klass.getExternalName();
        // Class names must be interned.
        StaticObject guestString = getMeta().toGuestString(externalName);
        return getStrings().intern(guestString);
    }

    @VmImpl(isJni = true)
    public static @JavaType(Class.class) StaticObject JVM_GetComponentType(@JavaType(Class.class) StaticObject self) {
        if (self.getMirrorKlass().isArray()) {
            Klass componentType = ((ArrayKlass) self.getMirrorKlass()).getComponentType();
            return componentType.mirror();
        }
        return StaticObject.NULL;
    }

    // endregion class

    // region JNI Invocation Interface
    @VmImpl
    public static int DestroyJavaVM(@Inject EspressoContext context) {
        assert context.getCurrentThread() != null;
        try {
            context.destroyVM(!context.ExitHost);
        } catch (EspressoExitException exit) {
            // expected
        }
        return JNI_OK;
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
        assert NativeUtils.interopAsPointer(getJavaVM()) == NativeUtils.interopAsPointer(vmPtr_);
        return attachCurrentThread(penvPtr, argsPtr, false);
    }

    private int attachCurrentThread(@SuppressWarnings("unused") @Pointer TruffleObject penvPtr, @Pointer TruffleObject argsPtr, boolean daemon) {
        JavaVMAttachArgs.JavaVMAttachArgsWrapper attachArgs = getStructs().javaVMAttachArgs.wrap(jni(), argsPtr);
        StaticObject group = null;
        String name = null;
        if (JniVersion.isSupported(attachArgs.version(), getContext().getJavaVersion())) {
            group = attachArgs.group();
            name = NativeUtils.fromUTF8Ptr(attachArgs.name());
        } else {
            getLogger().warning(String.format("AttachCurrentThread with unsupported JavaVMAttachArgs version: 0x%08x", attachArgs.version()));
        }
        StaticObject thread = getContext().createThread(Thread.currentThread(), group, name);
        if (daemon) {
            getMeta().java_lang_Thread_daemon.set(thread, true);
        }
        return JNI_OK;
    }

    @VmImpl
    @TruffleBoundary
    public int DetachCurrentThread(@Inject EspressoContext context) {
        StaticObject currentThread = context.getCurrentThread();
        if (currentThread == null) {
            return JNI_OK;
        }
        getLogger().fine(() -> {
            String guestName = getThreadAccess().getThreadName(currentThread);
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
            getLogger().warning(() -> {
                String guestName = getThreadAccess().getThreadName(currentThread);
                return "DetachCurrentThread called while thread is still executing Java code (" + guestName + ")";
            });
            return JNI_ERR;
        }
        StaticObject pendingException = jniEnv.getPendingException();
        jniEnv.clearPendingException();

        Meta meta = context.getMeta();
        try {
            if (pendingException != null) {
                meta.java_lang_Thread_dispatchUncaughtException.invokeDirect(currentThread, pendingException);
            }

            getThreadAccess().terminate(currentThread);
        } catch (EspressoException e) {
            try {
                StaticObject ex = e.getExceptionObject();
                String exception = ex.getKlass().getExternalName();
                String threadName = getThreadAccess().getThreadName(currentThread);
                context.getLogger().warning(String.format("Exception: %s thrown while terminating thread \"%s\"", exception, threadName));
                Method printStackTrace = ex.getKlass().lookupMethod(Name.printStackTrace, Signature._void);
                printStackTrace.invokeDirect(ex);
            } catch (EspressoException ee) {
                String exception = ee.getExceptionObject().getKlass().getExternalName();
                context.getLogger().warning(String.format("Exception: %s thrown while trying to print stack trace", exception));
            } catch (EspressoExitException ee) {
                // ignore
            }
        } catch (EspressoExitException e) {
            // ignore
        } catch (Throwable t) {
            context.getLogger().severe("Host exception thrown while trying to terminate thread");
            t.printStackTrace();
        }

        return JNI_OK;
    }

    /**
     * <h3>jint GetEnv(JavaVM *vm, void **env, jint version);</h3>
     *
     * @param vmPtr_ The virtual machine instance from which the interface will be retrieved.
     * @param envPtr pointer to the location where the env interface pointer for the current thread
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
        assert NativeUtils.interopAsPointer(getJavaVM()) == NativeUtils.interopAsPointer(vmPtr_);
        if (getUncached().isNull(envPtr)) {
            // Pointer should have been pre-null-checked.
            return JNI_ERR;
        }
        TruffleObject interopPtr = null;
        if (JVMTI.isJvmtiVersion(version)) {
            // JVMTI is requested before the main thread is created.
            // Also note that every request of a JVMTI env returns a freshly created structure.
            interopPtr = jvmti.create(version);
            if (interopPtr == null) {
                return JNI_EVERSION;
            }
        }
        if (JniVersion.isSupported(version, getContext().getJavaVersion())) {
            StaticObject currentThread = getContext().getGuestThreadFromHost(Thread.currentThread());
            if (currentThread == null) {
                return JNI_EDETACHED;
            }
            interopPtr = jniEnv.getNativePointer();
        }
        if (interopPtr != null) {
            NativeUtils.writeToPointerPointer(getUncached(), envPtr, interopPtr);
            return JNI_OK;
        }
        return JNI_EVERSION;
    }

    @VmImpl
    @TruffleBoundary
    public int AttachCurrentThreadAsDaemon(@Pointer TruffleObject vmPtr_, @Pointer TruffleObject penvPtr, @Pointer TruffleObject argsPtr) {
        assert NativeUtils.interopAsPointer(getJavaVM()) == NativeUtils.interopAsPointer(vmPtr_);
        return attachCurrentThread(penvPtr, argsPtr, true);
    }

    // endregion JNI Invocation Interface

    // region exceptions

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

    @VmImpl(isJni = true)
    public static @JavaType(String.class) StaticObject JVM_GetExtendedNPEMessage(@SuppressWarnings("unused") @JavaType(Throwable.class) StaticObject throwable) {
        return StaticObject.NULL;
    }

    @VmImpl(isJni = true)
    public @JavaType(Throwable.class) StaticObject JVM_FillInStackTrace(@JavaType(Throwable.class) StaticObject self, @SuppressWarnings("unused") int dummy) {
        return InterpreterToVM.fillInStackTrace(self, false, getMeta());
    }

    @VmImpl(isJni = true)
    public int JVM_GetStackTraceDepth(@JavaType(Throwable.class) StaticObject self) {
        Meta meta = getMeta();
        StackTrace frames = EspressoException.getFrames(self, meta);
        if (frames == null) {
            return 0;
        }
        return frames.size;
    }

    @VmImpl(isJni = true)
    public @JavaType(StackTraceElement.class) StaticObject JVM_GetStackTraceElement(@JavaType(Throwable.class) StaticObject self, int index,
                    @Inject SubstitutionProfiler profiler) {
        Meta meta = getMeta();
        if (index < 0) {
            profiler.profile(0);
            throw meta.throwException(meta.java_lang_IndexOutOfBoundsException);
        }
        StaticObject ste = meta.java_lang_StackTraceElement.allocateInstance();
        StackTrace frames = EspressoException.getFrames(self, meta);
        if (frames == null || index >= frames.size) {
            profiler.profile(1);
            throw meta.throwException(meta.java_lang_IndexOutOfBoundsException);
        }
        StackElement stackElement = frames.trace[index];
        Method method = stackElement.getMethod();
        if (method == null) {
            return StaticObject.NULL;
        }
        int bci = stackElement.getBCI();

        getMeta().java_lang_StackTraceElement_init.invokeDirect(
                        /* this */ ste,
                        /* declaringClass */ meta.toGuestString(MetaUtil.internalNameToJava(method.getDeclaringKlass().getType().toString(), true, true)),
                        /* methodName */ meta.toGuestString(method.getName()),
                        /* fileName */ meta.toGuestString(method.getSourceFile()),
                        /* lineNumber */ method.bciToLineNumber(bci));

        return ste;
    }

    // endregion exceptions

    // region ConstantPool

    private static void checkTag(ConstantPool pool, int index, ConstantPool.Tag expected, Meta meta, SubstitutionProfiler profiler) {
        ConstantPool.Tag target = pool.tagAt(index);
        if (target != expected) {
            profiler.profile(0);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Wrong type at constant pool index");
        }
    }

    @VmImpl(isJni = true)
    public static int JVM_ConstantPoolGetSize(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool) {
        return jcpool.getMirrorKlass().getConstantPool().length();
    }

    @VmImpl(isJni = true)
    public static @JavaType(Class.class) StaticObject JVM_ConstantPoolGetClassAt(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool,
                    int index,
                    @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.CLASS, meta, profiler);
        return ((RuntimeConstantPool) jcpool.getMirrorKlass().getConstantPool()).resolvedKlassAt(null, index).mirror();
    }

    @VmImpl(isJni = true)
    public static double JVM_ConstantPoolGetDoubleAt(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.DOUBLE, meta, profiler);
        return jcpool.getMirrorKlass().getConstantPool().doubleAt(index);
    }

    @VmImpl(isJni = true)
    public static float JVM_ConstantPoolGetFloatAt(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.FLOAT, meta, profiler);
        return jcpool.getMirrorKlass().getConstantPool().floatAt(index);
    }

    @VmImpl(isJni = true)
    public static @JavaType(String.class) StaticObject JVM_ConstantPoolGetStringAt(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool,
                    int index,
                    @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.STRING, meta, profiler);
        return ((RuntimeConstantPool) jcpool.getMirrorKlass().getConstantPool()).resolvedStringAt(index);
    }

    @VmImpl(isJni = true)
    public @JavaType(String.class) StaticObject JVM_ConstantPoolGetUTF8At(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool,
                    int index,
                    @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.UTF8, meta, profiler);
        return getMeta().toGuestString(jcpool.getMirrorKlass().getConstantPool().symbolAt(index).toString());
    }

    @VmImpl(isJni = true)
    public static int JVM_ConstantPoolGetIntAt(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.INTEGER, meta, profiler);
        return jcpool.getMirrorKlass().getConstantPool().intAt(index);
    }

    @VmImpl(isJni = true)
    public static long JVM_ConstantPoolGetLongAt(@SuppressWarnings("unused") @JavaType(Object.class) StaticObject unused, @JavaType(Object.class) StaticObject jcpool, int index,
                    @Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.LONG, meta, profiler);
        return jcpool.getMirrorKlass().getConstantPool().longAt(index);
    }

    // endregion ConstantPool

    // region class loading

    private Symbol<Type> namePtrToInternal(TruffleObject namePtr) {
        String name = NativeUtils.interopPointerToString(namePtr);
        Symbol<Type> type = null;
        if (name != null) {
            String internalName = name;
            if (!name.startsWith("[")) {
                // Force 'L' type.
                internalName = "L" + name + ";";
            }
            if (!Validation.validTypeDescriptor(ByteSequence.create(internalName), false)) {
                throw getMeta().throwExceptionWithMessage(getMeta().java_lang_NoClassDefFoundError, name);
            }
            type = getTypes().fromClassGetName(internalName);
        }
        return type;
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public @JavaType(Class.class) StaticObject JVM_LookupDefineClass(
                    @JavaType(Class.class) StaticObject lookup,
                    @Pointer TruffleObject namePtr,
                    @Pointer TruffleObject bufPtr,
                    int len,
                    @JavaType(ProtectionDomain.class) StaticObject pd,
                    boolean initialize,
                    int flags,
                    @JavaType(Object.class) StaticObject classData) {
        if (StaticObject.isNull(lookup)) {
            throw getMeta().throwExceptionWithMessage(getMeta().java_lang_InternalError, "Lookup class is null");
        }
        assert !getUncached().isNull(bufPtr);
        assert lookup.getMirrorKlass() instanceof ObjectKlass;

        boolean isNestMate = (flags & NESTMATE_CLASS) == NESTMATE_CLASS;
        boolean isHidden = (flags & HIDDEN_CLASS) == HIDDEN_CLASS;
        boolean isStrong = (flags & STRONG_LOADER_LINK) == STRONG_LOADER_LINK;
        boolean vmAnnotations = (flags & ACCESS_VM_ANNOTATIONS) == ACCESS_VM_ANNOTATIONS;

        ObjectKlass nest = null;
        if (isNestMate) {
            nest = (ObjectKlass) lookup.getMirrorKlass().nest();
        }
        if (!isHidden) {
            if (!StaticObject.isNull(classData)) {
                throw getMeta().throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "classData is only applicable for hidden classes");
            }
            if (isNestMate) {
                throw getMeta().throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "dynamic nestmate is only applicable for hidden classes");
            }
            if (!isStrong) {
                throw getMeta().throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "an ordinary class must be strongly referenced by its defining loader");
            }
            if (vmAnnotations) {
                throw getMeta().throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, "vm annotations only allowed for hidden classes");
            }
            if (flags != STRONG_LOADER_LINK) {
                throw getMeta().throwExceptionWithMessage(getMeta().java_lang_IllegalArgumentException, String.format("invalid flag 0x%x", flags));
            }
        }

        ByteBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Byte);
        final byte[] bytes = new byte[len];
        buf.get(bytes);
        Symbol<Type> type = namePtrToInternal(namePtr); // can be null
        StaticObject loader = lookup.getMirrorKlass().getDefiningClassLoader();

        ObjectKlass k;
        if (isHidden) {
            // Special handling
            k = getRegistries().defineKlass(type, bytes, loader, new ClassRegistry.ClassDefinitionInfo(pd, nest, classData, isStrong));
        } else {
            k = getRegistries().defineKlass(type, bytes, loader, new ClassRegistry.ClassDefinitionInfo(pd));
        }

        if (initialize) {
            k.safeInitialize();
        } else {
            k.ensureLinked();
        }
        return k.mirror();
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public @JavaType(Class.class) StaticObject JVM_DefineClass(@Pointer TruffleObject namePtr,
                    @JavaType(ClassLoader.class) StaticObject loader,
                    @Pointer TruffleObject bufPtr, int len,
                    @JavaType(ProtectionDomain.class) StaticObject pd) {
        ByteBuffer buf = NativeUtils.directByteBuffer(bufPtr, len, JavaKind.Byte);
        final byte[] bytes = new byte[len];
        buf.get(bytes);

        Symbol<Type> type = namePtrToInternal(namePtr); // can be null

        StaticObject clazz = getContext().getRegistries().defineKlass(type, bytes, loader, new ClassRegistry.ClassDefinitionInfo(pd)).mirror();
        assert clazz != null;
        return clazz;
    }

    @VmImpl(isJni = true)
    public @JavaType(Class.class) StaticObject JVM_DefineClassWithSource(@Pointer TruffleObject namePtr, @JavaType(ClassLoader.class) StaticObject loader, @Pointer TruffleObject bufPtr, int len,
                    @JavaType(ProtectionDomain.class) StaticObject pd, @SuppressWarnings("unused") @Pointer TruffleObject source) {
        // FIXME(peterssen): Source is ignored.
        return JVM_DefineClass(namePtr, loader, bufPtr, len, pd);
    }

    @VmImpl(isJni = true)
    public @JavaType(Class.class) StaticObject JVM_FindLoadedClass(@JavaType(ClassLoader.class) StaticObject loader, @JavaType(String.class) StaticObject name) {
        Symbol<Type> type = getTypes().fromClassGetName(getMeta().toHostString(name));
        // HotSpot skips reflection (DelegatingClassLoader) class loaders.
        Klass klass = getRegistries().findLoadedClass(type, nonReflectionClassLoader(loader));
        if (klass == null) {
            return StaticObject.NULL;
        }
        return klass.mirror();
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public @JavaType(Class.class) StaticObject JVM_FindClassFromBootLoader(@Pointer TruffleObject namePtr) {
        String name = NativeUtils.interopPointerToString(namePtr);
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

    @VmImpl(isJni = true)
    @TruffleBoundary
    public @JavaType(Class.class) StaticObject JVM_FindClassFromCaller(@Pointer TruffleObject namePtr,
                    boolean init, @JavaType(ClassLoader.class) StaticObject loader,
                    @JavaType(Class.class) StaticObject caller) {
        Meta meta = getMeta();
        Symbol<Type> type = namePtrToInternal(namePtr);
        Klass result;
        if (Types.isPrimitive(type)) {
            result = null;
        } else {
            StaticObject protectionDomain;
            // If loader is null, shouldn't call ClassLoader.checkPackageAccess; otherwise get
            // NPE. Put it in another way, the bootstrap class loader has all permission and
            // thus no checkPackageAccess equivalence in the VM class loader.
            // The caller is also passed as NULL by the java code if there is no security
            // manager to avoid the performance cost of getting the calling class.
            if (!StaticObject.isNull(caller) && !StaticObject.isNull(loader)) {
                protectionDomain = JVM_GetProtectionDomain(caller);
            } else {
                protectionDomain = StaticObject.NULL;
            }
            result = meta.resolveSymbolOrNull(type, loader, protectionDomain);
        }
        if (result == null) {
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassNotFoundException, NativeUtils.interopPointerToString(namePtr));
        }
        if (init) {
            result.safeInitialize();
        }
        return result.mirror();
    }

    @VmImpl(isJni = true)
    public @JavaType(Class.class) StaticObject JVM_FindPrimitiveClass(@Pointer TruffleObject namePtr) {
        Meta meta = getMeta();
        String hostName = NativeUtils.interopPointerToString(namePtr);
        return findPrimitiveClass(meta, hostName);
    }

    @JavaType(Class.class)
    public static StaticObject findPrimitiveClass(Meta meta, String hostName) {
        switch (hostName) {
            case "boolean":
                return meta._boolean.mirror();
            case "byte":
                return meta._byte.mirror();
            case "char":
                return meta._char.mirror();
            case "short":
                return meta._short.mirror();
            case "int":
                return meta._int.mirror();
            case "float":
                return meta._float.mirror();
            case "double":
                return meta._double.mirror();
            case "long":
                return meta._long.mirror();
            case "void":
                return meta._void.mirror();
            default:
                throw meta.throwExceptionWithMessage(meta.java_lang_ClassNotFoundException, hostName);
        }
    }

    // endregion class loading

    // region Library support

    private final ConcurrentHashMap<Long, @Pointer TruffleObject> handle2Lib = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, @Pointer TruffleObject> handle2Sym = new ConcurrentHashMap<>();

    private static final AtomicLong libraryHandles = new AtomicLong(1);

    public @Pointer TruffleObject getFunction(long handle) {
        return handle2Sym.get(handle);
    }

    @VmImpl
    @TruffleBoundary
    public @Pointer TruffleObject JVM_LoadLibrary(@Pointer TruffleObject namePtr) {
        String name = NativeUtils.interopPointerToString(namePtr);
        getLogger().fine(String.format("JVM_LoadLibrary: '%s'", name));
        TruffleObject lib = getNativeAccess().loadLibrary(Paths.get(name));
        if (lib == null) {
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_UnsatisfiedLinkError, name);
        }
        long handle = getLibraryHandle(lib);
        handle2Lib.put(handle, lib);
        getLogger().fine(String.format("JVM_LoadLibrary: Successfully loaded '%s' with handle %x", name, handle));
        return RawPointer.create(handle);
    }

    private static long getLibraryHandle(TruffleObject lib) {
        // TODO(peterssen): Add a proper API to get native handle for libraries.
        try {
            // Try NFI internals first.
            // TODO(peterssen): Expose library handle via interop asPointer?
            java.lang.reflect.Field f = lib.getClass().getDeclaredField("handle");
            f.setAccessible(true);
            return (long) f.get(lib);
        } catch (NoSuchFieldException e) {
            // Probably a Sulong library, cannot get its native handle, create a fake one.
            return libraryHandles.getAndIncrement();
        } catch (IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @VmImpl
    @TruffleBoundary
    public void JVM_UnloadLibrary(@Pointer TruffleObject libraryPtr) {
        long nativeLibraryPtr = NativeUtils.interopAsPointer(libraryPtr);
        TruffleObject library = handle2Lib.get(nativeLibraryPtr);
        if (library == null) {
            getLogger().severe("JVM_UnloadLibrary with unknown library (not loaded through JVM_LoadLibrary?): " + libraryPtr + " / " + Long.toHexString(nativeLibraryPtr));
        } else {
            getNativeAccess().unloadLibrary(library);
        }
    }

    @VmImpl
    @TruffleBoundary
    @SuppressFBWarnings(value = "AT_OPERATION_SEQUENCE_ON_CONCURRENT_ABSTRACTION", justification = "benign race")
    public @Pointer TruffleObject JVM_FindLibraryEntry(@Pointer TruffleObject libraryPtr, @Pointer TruffleObject namePtr) {
        String name = NativeUtils.interopPointerToString(namePtr);
        long nativePtr = NativeUtils.interopAsPointer(libraryPtr);
        TruffleObject library = handle2Lib.get(nativePtr);
        if (library == null) {
            if (nativePtr == rtldDefaultValue || nativePtr == processHandleValue) {
                library = getNativeAccess().loadDefaultLibrary();
                if (library == null) {
                    getLogger().warning("JVM_FindLibraryEntry from default/global namespace is not supported: " + name);
                    return RawPointer.nullInstance();
                }
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
            if (!getUncached().isPointer(function)) {
                getUncached().toNative(function);
            }
            long handle = getUncached().asPointer(function);
            handle2Sym.put(handle, function);
            return function;
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    // endregion Library support

    // region JNI version

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

    // endregion JNI version

    // region halting

    @VmImpl(isJni = true)
    public void JVM_BeforeHalt() {
        /*
         * currently nop
         */
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

    // endregion halting

    // region properties

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

    public static void setPropertyIfExists(Map<String, String> map, String propertyName, String value) {
        if (value != null && value.length() > 0) {
            map.put(propertyName, value);
        }
    }

    private static void setNumberedProperty(Map<String, String> map, String property, List<String> values) {
        int count = 0;
        for (String value : values) {
            map.put(property + "." + count++, value);
        }
    }

    private Map<String, String> buildPropertiesMap() {
        Map<String, String> map = new HashMap<>();
        OptionValues options = getContext().getEnv().getOptions();

        // Set user-defined system properties.
        for (Map.Entry<String, String> entry : options.get(EspressoOptions.Properties).entrySet()) {
            if (!entry.getKey().equals("sun.nio.MaxDirectMemorySize")) {
                map.put(entry.getKey(), entry.getValue());
            }
        }

        EspressoProperties props = getContext().getVmProperties();

        // Boot classpath setup requires special handling depending on the version.
        String bootClassPathProperty = getJavaVersion().java8OrEarlier() ? "sun.boot.class.path" : "jdk.boot.class.path.append";

        // Espresso uses VM properties, to ensure consistency the user-defined properties (that may
        // differ in some cases) are overwritten.
        map.put(bootClassPathProperty, stringify(props.bootClasspath()));
        map.put("java.class.path", stringify(props.classpath()));
        map.put("java.home", props.javaHome().toString());
        map.put("java.library.path", stringify(props.javaLibraryPath()));
        map.put("sun.boot.library.path", stringify(props.bootLibraryPath()));
        map.put("java.ext.dirs", stringify(props.extDirs()));

        // Modules properties.
        if (getJavaVersion().modulesEnabled()) {
            setPropertyIfExists(map, "jdk.module.main", getModuleMain(options));
            setPropertyIfExists(map, "jdk.module.path", stringify(options.get(EspressoOptions.ModulePath)));
            setNumberedProperty(map, "jdk.module.addreads", options.get(EspressoOptions.AddReads));
            setNumberedProperty(map, "jdk.module.addexports", options.get(EspressoOptions.AddExports));
            setNumberedProperty(map, "jdk.module.addopens", options.get(EspressoOptions.AddOpens));
            setNumberedProperty(map, "jdk.module.addmods", options.get(EspressoOptions.AddModules));
        }

        // Applications expect different formats e.g. 1.8 vs. 11
        String specVersion = getJavaVersion().java8OrEarlier()
                        ? "1." + getJavaVersion()
                        : getJavaVersion().toString();

        // Set VM information.
        map.put("java.vm.specification.version", specVersion);
        map.put("java.vm.specification.name", EspressoLanguage.VM_SPECIFICATION_NAME);
        map.put("java.vm.specification.vendor", EspressoLanguage.VM_SPECIFICATION_VENDOR);
        map.put("java.vm.version", specVersion + "-" + EspressoLanguage.VM_VERSION);
        map.put("java.vm.name", EspressoLanguage.VM_NAME);
        map.put("java.vm.vendor", EspressoLanguage.VM_VENDOR);
        map.put("java.vm.info", EspressoLanguage.VM_INFO);

        map.put("sun.nio.MaxDirectMemorySize", Long.toString(options.get(EspressoOptions.MaxDirectMemorySize)));

        return map;
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public @JavaType(Properties.class) StaticObject JVM_InitProperties(@JavaType(Properties.class) StaticObject properties) {
        Map<String, String> props = buildPropertiesMap();
        Method setProperty = properties.getKlass().lookupMethod(Name.setProperty, Signature.Object_String_String);
        for (Map.Entry<String, String> entry : props.entrySet()) {
            setProperty.invokeWithConversions(properties, entry.getKey(), entry.getValue());
        }
        return properties;
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public @JavaType(String[].class) StaticObject JVM_GetProperties() {
        Map<String, String> props = buildPropertiesMap();
        StaticObject array = getMeta().java_lang_String.allocateReferenceArray(props.size() * 2);
        int index = 0;
        for (Map.Entry<String, String> entry : props.entrySet()) {
            getInterpreterToVM().setArrayObject(getMeta().toGuestString(entry.getKey()), index * 2, array);
            getInterpreterToVM().setArrayObject(getMeta().toGuestString(entry.getValue()), index * 2 + 1, array);
            index++;
        }
        return array;
    }

    // endregion properties

    // region array

    @VmImpl(isJni = true)
    public int JVM_GetArrayLength(@JavaType(Object.class) StaticObject array, @Inject SubstitutionProfiler profiler) {
        try {
            return Array.getLength(MetaUtil.unwrapArrayOrNull(array));
        } catch (IllegalArgumentException e) {
            profiler.profile(0);
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, e.getMessage());
        } catch (NullPointerException e) {
            profiler.profile(1);
            throw getMeta().throwNullPointerException();
        }
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
    @VmImpl(isJni = true)
    public @JavaType(Object.class) StaticObject JVM_GetArrayElement(@JavaType(Object.class) StaticObject array, int index, @Inject SubstitutionProfiler profiler) {
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
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Argument is not an array");
        }
        assert array.getClass().isArray() && array.getClass().getComponentType().isPrimitive();
        if (index < 0 || index >= JVM_GetArrayLength(array, profiler)) {
            profiler.profile(4);
            throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, "index");
        }
        Object elem = Array.get(array, index);
        return getMeta().boxPrimitive(elem);
    }

    // endregion array

    // region assertion

    /**
     * Espresso only supports basic -ea and -esa options. Complex per-class/package filters are
     * unsupported.
     */
    @VmImpl(isJni = true)
    @TruffleBoundary
    public @JavaType(internalName = "Ljava/lang/AssertionStatusDirectives;") StaticObject JVM_AssertionStatusDirectives(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused) {
        Meta meta = getMeta();
        StaticObject instance = meta.java_lang_AssertionStatusDirectives.allocateInstance();
        meta.java_lang_AssertionStatusDirectives.lookupMethod(Name._init_, Signature._void).invokeDirect(instance);
        meta.java_lang_AssertionStatusDirectives_classes.set(instance, meta.java_lang_String.allocateReferenceArray(0));
        meta.java_lang_AssertionStatusDirectives_classEnabled.set(instance, meta._boolean.allocatePrimitiveArray(0));
        meta.java_lang_AssertionStatusDirectives_packages.set(instance, meta.java_lang_String.allocateReferenceArray(0));
        meta.java_lang_AssertionStatusDirectives_packageEnabled.set(instance, meta._boolean.allocatePrimitiveArray(0));
        boolean ea = getContext().getEnv().getOptions().get(EspressoOptions.EnableAssertions);
        meta.java_lang_AssertionStatusDirectives_deflt.set(instance, ea);
        return instance;
    }

    // endregion assertion

    // region stack inspection

    private static final int JVM_CALLER_DEPTH = -1;

    public static int jvmCallerDepth() {
        return JVM_CALLER_DEPTH;
    }

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

    private boolean isTrustedLoader(StaticObject loader) {
        StaticObject nonDelLoader = nonReflectionClassLoader(loader);
        StaticObject systemLoader = (StaticObject) getMeta().java_lang_ClassLoader_getSystemClassLoader.invokeDirect(null);
        while (StaticObject.notNull(systemLoader)) {
            if (systemLoader == nonDelLoader) {
                return true;
            }
            systemLoader = getMeta().java_lang_ClassLoader_parent.getObject(systemLoader);
        }
        return false;
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
        if (!StaticObject.isNull(getMeta().java_lang_System_securityManager.getObject(getMeta().java_lang_System.getStatics()))) {
            if (getMeta().java_security_ProtectionDomain_impliesCreateAccessControlContext == null) {
                return true;
            }
            if ((boolean) getMeta().java_security_AccessControlContext_isAuthorized.get(context)) {
                return true;
            }
            StaticObject pd = JVM_GetProtectionDomain(klass.mirror());
            if (pd != StaticObject.NULL) {
                return (boolean) getMeta().java_security_ProtectionDomain_impliesCreateAccessControlContext.invokeDirect(pd);
            }
        }
        return true;
    }

    /**
     * Returns the caller frame, 'depth' levels up. If securityStackWalk is true, some Espresso
     * frames are skipped according to {@link #isIgnoredBySecurityStackWalk}.
     * 
     * May return null if there is no Java frame on the stack.
     */
    @TruffleBoundary
    private FrameInstance getCallerFrame(int depth, boolean securityStackWalk, Meta meta) {
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
        return Truffle.getRuntime().iterateFrames(
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
    }

    /**
     * Returns the espresso root node for this frame, event if it comes from a different context.
     */
    private static EspressoRootNode getRawEspressoRootFromFrame(FrameInstance frameInstance) {
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
    public EspressoRootNode getEspressoRootFromFrame(FrameInstance frameInstance) {
        return getEspressoRootFromFrame(frameInstance, getContext());
    }

    @TruffleBoundary
    public static EspressoRootNode getEspressoRootFromFrame(FrameInstance frameInstance, EspressoContext context) {
        EspressoRootNode root = getRawEspressoRootFromFrame(frameInstance);
        if (root == null) {
            return null;
        }
        Method method = root.getMethod();
        if (method.getContext() != context) {
            return null;
        }
        return root;
    }

    @TruffleBoundary
    public Method getMethodFromFrame(FrameInstance frameInstance) {
        EspressoRootNode root = getRawEspressoRootFromFrame(frameInstance);
        if (root == null) {
            return null;
        }
        Method method = root.getMethod();
        if (method.getContext() != getContext()) {
            return null;
        }
        return method;
    }

    @VmImpl(isJni = true)
    public @JavaType(Class.class) StaticObject JVM_GetCallerClass(int depth,
                    @Inject SubstitutionProfiler profiler) {
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
                                            if (!method.isCallerSensitive()) {
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
            throw meta.throwException(internalError);
        }

        if (callerMethod == null) {
            return StaticObject.NULL;
        }

        return callerMethod.getDeclaringKlass().mirror();
    }

    @VmImpl(isJni = true)
    public @JavaType(Class[].class) StaticObject JVM_GetClassContext() {
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

    // region privileged

    private @JavaType(AccessControlContext.class) StaticObject createACC(@JavaType(ProtectionDomain[].class) StaticObject context,
                    boolean isPriviledged,
                    @JavaType(AccessControlContext.class) StaticObject priviledgedContext) {
        Klass accKlass = getMeta().java_security_AccessControlContext;
        StaticObject acc = accKlass.allocateInstance();
        getMeta().java_security_AccessControlContext_context.setObject(acc, context);
        getMeta().java_security_AccessControlContext_privilegedContext.setObject(acc, priviledgedContext);
        getMeta().java_security_AccessControlContext_isPrivileged.setBoolean(acc, isPriviledged);
        if (getMeta().java_security_AccessControlContext_isAuthorized != null) {
            getMeta().java_security_AccessControlContext_isAuthorized.setBoolean(acc, true);
        }
        return acc;
    }

    private @JavaType(AccessControlContext.class) StaticObject createDummyACC() {
        Klass pdKlass = getMeta().java_security_ProtectionDomain;
        StaticObject pd = pdKlass.allocateInstance();
        getMeta().java_security_ProtectionDomain_init_CodeSource_PermissionCollection.invokeDirect(pd, StaticObject.NULL, StaticObject.NULL);
        StaticObject context = StaticObject.wrap(new StaticObject[]{pd}, getMeta());
        return createACC(context, false, StaticObject.NULL);
    }

    public PrivilegedStack getPrivilegedStack() {
        return getContext().getLanguage().getThreadLocalState().getPrivilegedStack();
    }

    public static final class PrivilegedStack {
        private final EspressoContext espressoContext;
        private Element top;

        public PrivilegedStack(EspressoContext context) {
            this.espressoContext = context;
        }

        void push(FrameInstance frame, StaticObject context, Klass klass) {
            top = new Element(frame, context, klass, top, espressoContext);
        }

        void pop() {
            assert top != null : "popping empty privileged stack !";
            top = top.next;
        }

        boolean compare(FrameInstance frame) {
            return top != null && top.compare(frame, espressoContext);
        }

        StaticObject peekContext() {
            assert top != null;
            return top.context;
        }

        StaticObject classLoader() {
            assert top != null;
            return top.klass.getDefiningClassLoader();
        }

        static private class Element {
            long frameID;
            StaticObject context;
            Klass klass;
            Element next;

            Element(FrameInstance frame, StaticObject context, Klass klass, Element next, EspressoContext espressoContext) {
                this.frameID = getFrameId(frame, espressoContext);
                this.context = context;
                this.klass = klass;
                this.next = next;
            }

            boolean compare(FrameInstance other, EspressoContext espressoContext) {
                EspressoRootNode rootNode = getEspressoRootFromFrame(other, espressoContext);
                if (rootNode != null) {
                    Frame readOnlyFrame = other.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                    long frameIdOrZero = rootNode.readFrameIdOrZero(readOnlyFrame);
                    return frameIdOrZero != 0 && frameIdOrZero == frameID;
                }
                return false;
            }

            private static long getFrameId(FrameInstance frame, EspressoContext espressoContext) {
                EspressoRootNode rootNode = getEspressoRootFromFrame(frame, espressoContext);
                Frame readOnlyFrame = frame.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                return rootNode.readFrameIdOrZero(readOnlyFrame);
            }
        }
    }

    @VmImpl(isJni = true)
    @SuppressWarnings("unused")
    public @JavaType(Object.class) StaticObject JVM_DoPrivileged(@JavaType(Class.class) StaticObject cls,
                    /* PrivilegedAction or PrivilegedActionException */ @JavaType(Object.class) StaticObject action,
                    @JavaType(AccessControlContext.class) StaticObject context,
                    boolean wrapException,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(action)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        FrameInstance callerFrame = getCallerFrame(1, false, meta);
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
            throw meta.throwException(meta.java_lang_InternalError);
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
            if (meta.java_lang_Exception.isAssignableFrom(e.getExceptionObject().getKlass()) &&
                            !meta.java_lang_RuntimeException.isAssignableFrom(e.getExceptionObject().getKlass())) {
                profiler.profile(3);
                StaticObject wrapper = meta.java_security_PrivilegedActionException.allocateInstance();
                getMeta().java_security_PrivilegedActionException_init_Exception.invokeDirect(wrapper, e.getExceptionObject());
                throw meta.throwException(wrapper);
            }
            profiler.profile(4);
            throw e;
        } finally {
            stack.pop();
        }
        return result;
    }

    @VmImpl(isJni = true)
    @SuppressWarnings("unused")
    public @JavaType(Object.class) StaticObject JVM_GetStackAccessControlContext(@JavaType(Class.class) StaticObject cls) {
        if (getJavaVersion().java11OrEarlier()) {
            return getACCUntil11();
        } else {
            return getACCAfter12();
        }
    }

    private StaticObject getACCAfter12() {
        ArrayList<StaticObject> domains = new ArrayList<>();
        final boolean[] isPrivileged = new boolean[]{false};

        StaticObject context = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<StaticObject>() {
            StaticObject prevDomain = StaticObject.NULL;

            @Override
            public StaticObject visitFrame(FrameInstance frameInstance) {
                Method m = getMethodFromFrame(frameInstance);
                if (m != null) {
                    StaticObject domain = null;
                    StaticObject stackContext = null;
                    StaticObject domainKlass = null;
                    if (m.getDeclaringKlass() == getMeta().java_security_AccessController &&
                                    m.getName() == Name.executePrivileged) {
                        isPrivileged[0] = true;
                        Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                        // 2nd argument: `AccessControlContext context`
                        stackContext = BytecodeNode.getLocalObject(frame, 1);
                        // 3rd argument: Class<?> caller
                        domainKlass = BytecodeNode.getLocalObject(frame, 2);
                    } else {
                        domainKlass = m.getDeclaringKlass().mirror();
                    }
                    domain = JVM_GetProtectionDomain(domainKlass);
                    if (domain != prevDomain && domain != StaticObject.NULL) {
                        domains.add(domain);
                        prevDomain = domain;
                    }
                    if (isPrivileged[0]) {
                        return stackContext;
                    }
                }
                return null;
            }
        });
        return getAccFromContext(domains, isPrivileged[0], context);
    }

    private StaticObject getACCUntil11() {
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
                    StaticObject domain = JVM_GetProtectionDomain(m.getDeclaringKlass().mirror());
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

        return getAccFromContext(domains, isPrivileged[0], context);
    }

    private StaticObject getAccFromContext(ArrayList<StaticObject> domains, boolean isPrivileged, StaticObject context) {
        if (domains.isEmpty()) {
            if (isPrivileged && StaticObject.isNull(context)) {
                return StaticObject.NULL;
            }
            return createACC(StaticObject.NULL, isPrivileged, context == null ? StaticObject.NULL : context);
        }

        StaticObject guestContext = StaticObject.createArray(getMeta().java_security_ProtectionDomain.array(), domains.toArray(StaticObject.EMPTY_ARRAY));
        return createACC(guestContext, isPrivileged, context == null ? StaticObject.NULL : context);
    }

    @VmImpl(isJni = true)
    @SuppressWarnings("unused")
    public @JavaType(Object.class) StaticObject JVM_GetInheritedAccessControlContext(@JavaType(Class.class) StaticObject cls) {
        return getMeta().java_lang_Thread_inheritedAccessControlContext.getObject(getContext().getCurrentThread());
    }

    // endregion privileged

    @VmImpl(isJni = true)
    public @JavaType(Object.class) StaticObject JVM_LatestUserDefinedLoader(@Inject Meta meta) {
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

    @VmImpl(isJni = true)
    public @JavaType(Class.class) StaticObject JVM_CurrentLoadedClass() {
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

    @VmImpl(isJni = true)
    public @JavaType(Class.class) StaticObject JVM_CurrentClassLoader() {
        @JavaType(Class.class)
        StaticObject loadedClass = JVM_CurrentLoadedClass();
        return StaticObject.isNull(loadedClass) ? StaticObject.NULL : loadedClass.getMirrorKlass().getDefiningClassLoader();
    }

    @VmImpl(isJni = true)
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

    @VmImpl(isJni = true)
    @TruffleBoundary
    public int JVM_ClassDepth(@JavaType(String.class) StaticObject name) {
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

    // endregion stack inspection

    // region annotations

    private static @JavaType(java.lang.reflect.Method.class) StaticObject getGuestReflectiveMethodRoot(@JavaType(java.lang.reflect.Method.class) StaticObject seed, Meta meta) {
        assert InterpreterToVM.instanceOf(seed, meta.java_lang_reflect_Method);
        StaticObject curMethod = seed;
        Method target = null;
        while (target == null) {
            target = (Method) meta.HIDDEN_METHOD_KEY.getHiddenObject(curMethod);
            if (target == null) {
                curMethod = meta.java_lang_reflect_Method_root.getObject(curMethod);
            }
        }
        return curMethod;
    }

    private static @JavaType(java.lang.reflect.Field.class) StaticObject getGuestReflectiveFieldRoot(@JavaType(java.lang.reflect.Field.class) StaticObject seed, Meta meta) {
        assert InterpreterToVM.instanceOf(seed, meta.java_lang_reflect_Field);
        StaticObject curField = seed;
        Field target = null;
        while (target == null) {
            target = (Field) meta.HIDDEN_FIELD_KEY.getHiddenObject(curField);
            if (target == null) {
                curField = meta.java_lang_reflect_Field_root.getObject(curField);
            }
        }
        return curField;
    }

    private static @JavaType(java.lang.reflect.Constructor.class) StaticObject getGuestReflectiveConstructorRoot(@JavaType(java.lang.reflect.Constructor.class) StaticObject seed, Meta meta) {
        assert InterpreterToVM.instanceOf(seed, meta.java_lang_reflect_Constructor);
        StaticObject curConstructor = seed;
        Method target = null;
        while (target == null) {
            target = (Method) meta.HIDDEN_CONSTRUCTOR_KEY.getHiddenObject(curConstructor);
            if (target == null) {
                curConstructor = meta.java_lang_reflect_Constructor_root.getObject(curConstructor);
            }
        }
        return curConstructor;
    }

    @VmImpl(isJni = true)
    public @JavaType(Parameter[].class) StaticObject JVM_GetMethodParameters(@JavaType(Object.class) StaticObject executable,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        assert meta.java_lang_reflect_Executable.isAssignableFrom(executable.getKlass());
        StaticObject parameterTypes = (StaticObject) executable.getKlass().lookupMethod(Name.getParameterTypes, Signature.Class_array).invokeDirect(executable);
        int numParams = parameterTypes.length();
        if (numParams == 0) {
            return StaticObject.NULL;
        }

        Method method;
        if (meta.java_lang_reflect_Method.isAssignableFrom(executable.getKlass())) {
            method = Method.getHostReflectiveMethodRoot(executable, meta);
        } else if (meta.java_lang_reflect_Constructor.isAssignableFrom(executable.getKlass())) {
            method = Method.getHostReflectiveConstructorRoot(executable, meta);
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
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Constant pool index out of bounds");
            }
            if (nameIndex != 0 && method.getConstantPool().tagAt(nameIndex) != ConstantPool.Tag.UTF8) {
                profiler.profile(1);
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Wrong type at constant pool index");
            }
        }

        // TODO(peterssen): Cache guest j.l.reflect.Parameter constructor.
        // Calling the constructor is just for validation, manually setting the fields would
        // be faster.
        Method parameterInit = meta.java_lang_reflect_Parameter.lookupDeclaredMethod(Name._init_, getSignatures().makeRaw(Type._void,
                        /* name */ Type.java_lang_String,
                        /* modifiers */ Type._int,
                        /* executable */ Type.java_lang_reflect_Executable,
                        /* index */ Type._int));

        // Use attribute's number of parameters.
        return meta.java_lang_reflect_Parameter.allocateReferenceArray(methodParameters.getEntries().length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int index) {
                MethodParametersAttribute.Entry entry = methodParameters.getEntries()[index];
                StaticObject instance = meta.java_lang_reflect_Parameter.allocateInstance();
                // For a 0 index, give an empty name.
                StaticObject guestName;
                if (entry.getNameIndex() != 0) {
                    guestName = meta.toGuestString(method.getConstantPool().symbolAt(entry.getNameIndex(), "parameter name").toString());
                } else {
                    guestName = getJavaVersion().java9OrLater() ? StaticObject.NULL : meta.toGuestString("");
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

    @VmImpl(isJni = true)
    public @JavaType(byte[].class) StaticObject JVM_GetMethodTypeAnnotations(@JavaType(java.lang.reflect.Executable.class) StaticObject guestReflectionMethod) {
        // guestReflectionMethod can be either a Method or a Constructor.
        if (InterpreterToVM.instanceOf(guestReflectionMethod, getMeta().java_lang_reflect_Method)) {
            StaticObject methodRoot = getGuestReflectiveMethodRoot(guestReflectionMethod, getMeta());
            assert methodRoot != null;
            return (StaticObject) getMeta().HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.getHiddenObject(methodRoot);
        } else if (InterpreterToVM.instanceOf(guestReflectionMethod, getMeta().java_lang_reflect_Constructor)) {
            StaticObject constructorRoot = getGuestReflectiveConstructorRoot(guestReflectionMethod, getMeta());
            assert constructorRoot != null;
            return (StaticObject) getMeta().HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.getHiddenObject(constructorRoot);
        } else {
            throw EspressoError.shouldNotReachHere();
        }
    }

    @VmImpl(isJni = true)
    public @JavaType(byte[].class) StaticObject JVM_GetFieldTypeAnnotations(@JavaType(java.lang.reflect.Field.class) StaticObject guestReflectionField) {
        assert InterpreterToVM.instanceOf(guestReflectionField, getMeta().java_lang_reflect_Field);
        StaticObject fieldRoot = getGuestReflectiveFieldRoot(guestReflectionField, getMeta());
        assert fieldRoot != null;
        return (StaticObject) getMeta().HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.getHiddenObject(fieldRoot);
    }

    // endregion annotations

    // region Invocation API

    @VmImpl
    @TruffleBoundary
    public int JNI_GetCreatedJavaVMs(@Pointer TruffleObject vmBufPtr, int bufLen, @Pointer TruffleObject numVMsPtr) {
        if (bufLen > 0) {
            if (getUncached().isNull(vmBufPtr)) {
                // Pointer should have been pre-null-checked.
                return JNI_ERR;
            }
            NativeUtils.writeToPointerPointer(getUncached(), vmBufPtr, getVM().getJavaVM());
            if (!getUncached().isNull(numVMsPtr)) {
                NativeUtils.writeToIntPointer(getUncached(), numVMsPtr, 1);
            }
        }
        return JNI_OK;
    }

    // endregion Invocation API

    // region threads

    @VmImpl(isJni = true)
    public @JavaType(Thread[].class) StaticObject JVM_GetAllThreads(@SuppressWarnings("unused") @JavaType(Class.class) StaticObject unused) {
        final StaticObject[] threads = getContext().getActiveThreads();
        return getMeta().java_lang_Thread.allocateReferenceArray(threads.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int index) {
                return threads[index];
            }
        });
    }

    // endregion threads

    // region Management

    @VmImpl
    @TruffleBoundary
    public synchronized @Pointer TruffleObject JVM_GetManagement(int version) {
        EspressoContext context = getContext();
        if (!context.EnableManagement) {
            getLogger().severe("JVM_GetManagement: Experimental support for java.lang.management native APIs is disabled.\n" +
                            "Use '--java.EnableManagement=true' to enable experimental support for j.l.management native APIs.");
            return RawPointer.nullInstance();
        }
        assert management != null;
        return management.getManagement(version);
    }

    /**
     * Returns all values declared to the {@link EspressoOptions#VMArguments} option during context
     * creation.
     * <p>
     * In practice, this should be the list of arguments passed to the context, but depending on who
     * built it, it may be any arbitrary list of Strings.
     * <p>
     * Note that even if that's the case, it may differs slightly from the expected list of
     * arguments. The Java world expects this to be the arguments passed to the VM creation, which
     * is expected to have passed through the regular java launcher, and have been de-sugarified
     * (/ex: '-m [module]' -> '-Djdk.module.main=[module]').
     * <p>
     * In the Java-on-Truffle case, the VM arguments and the context builder options are equivalent,
     * but that is not true in the regular Espresso launcher case, or in an embedding scenario.
     */
    @VmImpl(isJni = true)
    @TruffleBoundary
    public @JavaType(String[].class) StaticObject JVM_GetVmArguments() {
        String[] vmArgs = getContext().getVmArguments();
        StaticObject array = getMeta().java_lang_String.allocateReferenceArray(vmArgs.length);
        for (int i = 0; i < vmArgs.length; i++) {
            getInterpreterToVM().setArrayObject(getMeta().toGuestString(vmArgs[i]), i, array);
        }
        return array;
    }

    // endregion Management

    // region Modules

    @VmImpl(isJni = true)
    @TruffleBoundary
    public void JVM_AddModuleExports(@JavaType(internalName = "Ljava/lang/Module;") StaticObject from_module,
                    @Pointer TruffleObject pkgName,
                    @JavaType(internalName = "Ljava/lang/Module;") StaticObject to_module,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(to_module)) {
            profiler.profile(6);
            throw getMeta().throwNullPointerException();
        }
        if (getUncached().isNull(pkgName)) {
            profiler.profile(0);
            throw getMeta().throwNullPointerException();
        }
        ModulesHelperVM.addModuleExports(from_module, NativeUtils.interopPointerToString(pkgName), to_module, getMeta(), profiler);
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public void JVM_AddModuleExportsToAllUnnamed(@JavaType(internalName = "Ljava/lang/Module;") StaticObject from_module, @Pointer TruffleObject pkgName,
                    @Inject SubstitutionProfiler profiler) {
        if (getUncached().isNull(pkgName)) {
            profiler.profile(0);
            throw getMeta().throwNullPointerException();
        }
        ModulesHelperVM.addModuleExportsToAllUnnamed(from_module, NativeUtils.interopPointerToString(pkgName), profiler, getMeta());
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public void JVM_AddModuleExportsToAll(@JavaType(internalName = "Ljava/lang/Module;") StaticObject from_module, @Pointer TruffleObject pkgName,
                    @Inject SubstitutionProfiler profiler) {
        if (getUncached().isNull(pkgName)) {
            profiler.profile(0);
            throw getMeta().throwNullPointerException();
        }
        ModulesHelperVM.addModuleExports(from_module, NativeUtils.interopPointerToString(pkgName), StaticObject.NULL, getMeta(), profiler);
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public void JVM_AddReadsModule(@JavaType(internalName = "Ljava/lang/Module;") StaticObject from_module, @JavaType(internalName = "Ljava/lang/Module;") StaticObject source_module,
                    @Inject SubstitutionProfiler profiler) {
        ModuleEntry fromEntry = ModulesHelperVM.extractFromModuleEntry(from_module, getMeta(), profiler);
        ModuleEntry toEntry = ModulesHelperVM.extractToModuleEntry(source_module, getMeta(), profiler);
        if (fromEntry != toEntry && fromEntry.isNamed()) {
            fromEntry.addReads(toEntry);
        }
    }

    private static final String MODULES = "modules";

    @VmImpl(isJni = true)
    @TruffleBoundary
    public void JVM_DefineModule(@JavaType(internalName = "Ljava/lang/Module;") StaticObject module,
                    boolean is_open,
                    @SuppressWarnings("unused") @JavaType(String.class) StaticObject version,
                    @SuppressWarnings("unused") @JavaType(String.class) StaticObject location,
                    @Pointer TruffleObject pkgs,
                    int num_package,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(module)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        if (num_package < 0) {
            profiler.profile(1);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "num_package must be >= 0");
        }
        if (getUncached().isNull(pkgs) && num_package > 0) {
            profiler.profile(2);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "num_packages must be 0 if packages is null");
        }
        if (!meta.java_lang_Module.isAssignableFrom(module.getKlass())) {
            profiler.profile(3);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "module is not an instance of java.lang.Module");
        }

        StaticObject guestName = meta.java_lang_Module_name.getObject(module);
        if (StaticObject.isNull(guestName)) {
            profiler.profile(4);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "modue name cannot be null");
        }

        String hostName = meta.toHostString(guestName);
        if (hostName.equals(JAVA_BASE)) {
            profiler.profile(5);
            defineJavaBaseModule(module, extractNativePackages(pkgs, num_package, profiler), profiler);
            return;
        }
        profiler.profile(6);
        defineModule(module, hostName, is_open, extractNativePackages(pkgs, num_package, profiler), profiler);
    }

    @SuppressWarnings("try")
    public void defineModule(StaticObject module,
                    String moduleName,
                    boolean is_open,
                    String[] packages,
                    SubstitutionProfiler profiler) {
        Meta meta = getMeta();
        StaticObject loader = meta.java_lang_Module_loader.getObject(module);
        if (loader != nonReflectionClassLoader(loader)) {
            profiler.profile(15);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Class loader is an invalid delegating class loader");
        }

        // Prepare variables
        ClassRegistry registry = getRegistries().getClassRegistry(loader);
        assert registry != null;
        PackageTable packageTable = registry.packages();
        ModuleTable moduleTable = registry.modules();
        assert moduleTable != null && packageTable != null;
        boolean loaderIsBootOrPlatform = ClassRegistry.loaderIsBootOrPlatform(loader, meta);

        ArrayList<Symbol<Name>> pkgSymbols = new ArrayList<>();
        try (EntryTable.BlockLock block = packageTable.write()) {
            for (String str : packages) {
                // Extract the package symbols. Also checks for duplicates.
                if (!loaderIsBootOrPlatform && (str.equals("java") || str.startsWith("java/"))) {
                    // Only modules defined to either the boot or platform class loader, can define
                    // a "java/" package.
                    profiler.profile(14);
                    throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException,
                                    cat("Class loader (", loader.getKlass().getType(), ") tried to define prohibited package name: ", str));
                }
                Symbol<Name> symbol = getNames().getOrCreate(str);
                if (packageTable.lookup(symbol) != null) {
                    profiler.profile(13);
                    throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException,
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
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException,
                                cat("Module ", moduleName, " is already defined"));
            }
            // Register packages
            for (Symbol<Name> pkgSymbol : pkgSymbols) {
                PackageEntry pkgEntry = packageTable.createAndAddEntry(pkgSymbol, moduleEntry);
                assert pkgEntry != null; // should have been checked before
            }
            // Link guest module to its host representation
            meta.HIDDEN_MODULE_ENTRY.setHiddenObject(module, moduleEntry);
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
                String pkg = NativeUtils.interopPointerToString((TruffleObject) getUncached().execute(getPackageAt, pkgs, i));
                if (!Validation.validBinaryName(pkg)) {
                    profiler.profile(7);
                    Meta meta = getMeta();
                    throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException,
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
    public void defineJavaBaseModule(StaticObject module, String[] packages, SubstitutionProfiler profiler) {
        Meta meta = getMeta();
        StaticObject loader = meta.java_lang_Module_loader.getObject(module);
        if (!StaticObject.isNull(loader)) {
            profiler.profile(10);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException,
                            "Class loader must be the bootclass loader");
        }
        PackageTable pkgTable = getRegistries().getBootClassRegistry().packages();
        ModuleEntry javaBaseEntry = getRegistries().getJavaBaseModule();
        try (EntryTable.BlockLock block = pkgTable.write()) {
            if (getRegistries().javaBaseDefined()) {
                profiler.profile(9);
                throw meta.throwException(meta.java_lang_InternalError);
            }
            for (String pkg : packages) {
                Symbol<Name> pkgName = getNames().getOrCreate(pkg);
                if (pkgTable.lookup(pkgName) == null) {
                    pkgTable.createAndAddEntry(pkgName, javaBaseEntry);
                }
            }
            javaBaseEntry.setModule(module);
            meta.HIDDEN_MODULE_ENTRY.setHiddenObject(module, javaBaseEntry);
            getRegistries().processFixupList(module);
        }
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public void JVM_SetBootLoaderUnnamedModule(@JavaType(internalName = "Ljava/lang/Module;") StaticObject module) {
        Meta meta = getMeta();
        if (StaticObject.isNull(module)) {
            throw meta.throwNullPointerException();
        }
        if (!meta.java_lang_Module.isAssignableFrom(module.getKlass())) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "module is not an instance of java.lang.module");
        }
        if (!StaticObject.isNull(meta.java_lang_Module_name.getObject(module))) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "boot loader unnamed module has a name");
        }
        if (!StaticObject.isNull(meta.java_lang_Module_loader.getObject(module))) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Class loader must be the boot class loader");
        }
        ModuleEntry bootUnnamed = getRegistries().getBootClassRegistry().getUnnamedModule();
        bootUnnamed.setModule(module);
        meta.HIDDEN_MODULE_ENTRY.setHiddenObject(module, bootUnnamed);
    }

    // endregion Modules

    // region reference

    @VmImpl(isJni = true)
    public @JavaType(Reference.class) StaticObject JVM_GetAndClearReferencePendingList() {
        return getContext().getAndClearReferencePendingList();
    }

    @VmImpl(isJni = true)
    public void JVM_WaitForReferencePendingList() {
        getContext().waitForReferencePendingList();
    }

    @VmImpl(isJni = true)
    public boolean JVM_HasReferencePendingList() {
        return getContext().hasReferencePendingList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @VmImpl(isJni = true)
    public boolean JVM_PhantomReferenceRefersTo(@JavaType(Reference.class) StaticObject ref, @JavaType(Object.class) StaticObject object,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(ref)) {
            profiler.profile(0);
            getMeta().throwNullPointerException();
        }
        EspressoReference host = (EspressoReference) getMeta().HIDDEN_HOST_REFERENCE.getHiddenObject(ref);
        assert host instanceof Reference;
        // Call host's refersTo. Not available in 8 or 11.
        return ReferenceSupport.phantomReferenceRefersTo((Reference) host, object);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @VmImpl(isJni = true)
    public boolean JVM_ReferenceRefersTo(@JavaType(Reference.class) StaticObject ref, @JavaType(Object.class) StaticObject object,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(ref)) {
            profiler.profile(0);
            getMeta().throwNullPointerException();
        }
        EspressoReference host = (EspressoReference) getMeta().HIDDEN_HOST_REFERENCE.getHiddenObject(ref);
        assert host instanceof Reference;
        // Call host's refersTo. Not available in 8 or 11.
        return ReferenceSupport.referenceRefersTo((Reference) host, object);
    }

    @VmImpl(isJni = true)
    public void JVM_ReferenceClear(@JavaType(Reference.class) StaticObject ref,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(ref)) {
            profiler.profile(0);
            getMeta().throwNullPointerException();
        }
        Target_java_lang_ref_Reference.clear(ref, getMeta());
    }

    // endregion reference

    // region archive

    @VmImpl(isJni = true)
    @SuppressWarnings("unused")
    public void JVM_InitializeFromArchive(@JavaType(Class.class) StaticObject cls) {
        /*
         * Used to reduce boot time of certain initializations through CDS (/ex: module
         * initialization). Currently unsupported.
         */
    }

    @VmImpl(isJni = true)
    public static boolean JVM_IsDumpingClassList() {
        return false;
    }

    @VmImpl(isJni = true)
    public static boolean JVM_IsCDSDumpingEnabled() {
        return false;
    }

    @VmImpl(isJni = true)
    public static boolean JVM_IsSharingEnabled() {
        return false;
    }

    @VmImpl
    public static long JVM_GetRandomSeedForDumping() {
        return 0L;
    }

    // endregion archive

    // region VMSupport
    /**
     * Return the temporary directory that the VM uses for the attach and perf data files.
     *
     * It is important that this directory is well-known and the same for all VM instances. It
     * cannot be affected by configuration variables such as java.io.tmpdir.
     */
    @VmImpl(isJni = true)
    @TruffleBoundary
    public @JavaType(String.class) StaticObject JVM_GetTemporaryDirectory() {
        // TODO: use host VMSupport.getVMTemporaryDirectory(). Not implemented by SVM.
        // host application temporary directory
        return getMeta().toGuestString(System.getProperty("java.io.tmpdir"));
    }

    private static final long ONE_BILLION = 1_000_000_000;
    private static final long MAX_DIFF = 0x0100000000L;

    @VmImpl(isJni = true)
    @SuppressWarnings("unused")
    @TruffleBoundary
    /**
     * Instant.now() uses System.currentTimeMillis() on a host Java 8. This might produce some loss
     * of precision.
     */
    public static long JVM_GetNanoTimeAdjustment(@JavaType(Class.class) StaticObject ignored, long offset) {
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

    @VmImpl(isJni = true)
    @TruffleBoundary
    public static long JVM_MaxObjectInspectionAge() {
        // TODO: somehow use GC.maxObjectInspectionAge() (not supported by SVM);
        return 0;
    }

    // endregion VMSupport

    // region stackwalk

    private final StackWalk stackWalk;

    @VmImpl(isJni = true)
    @SuppressWarnings("unused")
    public void JVM_InitStackTraceElement(@JavaType(StackTraceElement.class) StaticObject element, @JavaType(internalName = "Ljava/lang/StackFrameInfo;") StaticObject info,
                    @Inject Meta meta) {
        if (StaticObject.isNull(element) || StaticObject.isNull(info)) {
            throw meta.throwNullPointerException();
        }
        StaticObject mname = meta.java_lang_StackFrameInfo_memberName.getObject(info);
        if (StaticObject.isNull(mname)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "uninitialized StackFrameInfo !");
        }
        StaticObject clazz = meta.java_lang_invoke_MemberName_clazz.getObject(mname);
        Method m = (Method) meta.HIDDEN_VMTARGET.getHiddenObject(mname);
        if (m == null) {
            throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "uninitialized StackFrameInfo !");
        }
        int bci = meta.java_lang_StackFrameInfo_bci.getInt(info);
        fillInElement(element, new VM.StackElement(m, bci), getMeta().java_lang_Class_getName);
    }

    @VmImpl(isJni = true)
    public void JVM_InitStackTraceElementArray(@JavaType(StackTraceElement[].class) StaticObject elements, @JavaType(Throwable.class) StaticObject throwable,
                    @Inject SubstitutionProfiler profiler) {
        Meta meta = getMeta();
        if (StaticObject.isNull(elements) || StaticObject.isNull(throwable)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        assert elements.isArray();
        VM.StackTrace stackTrace = (VM.StackTrace) meta.HIDDEN_FRAMES.getHiddenObject(throwable);
        if (stackTrace != null) {
            if (elements.length() != stackTrace.size) {
                profiler.profile(1);
                throw meta.throwException(meta.java_lang_IndexOutOfBoundsException);
            }
            for (int i = 0; i < stackTrace.size; i++) {
                if (StaticObject.isNull(elements.get(i))) {
                    profiler.profile(2);
                    throw meta.throwNullPointerException();
                }
                fillInElement(elements.get(i), stackTrace.trace[i], getMeta().java_lang_Class_getName);
            }
        }
    }

    private void fillInElement(@JavaType(StackTraceElement.class) StaticObject ste, VM.StackElement element,
                    Method classGetName) {
        Method m = element.getMethod();
        Klass k = m.getDeclaringKlass();
        StaticObject guestClass = k.mirror();
        StaticObject loader = k.getDefiningClassLoader();
        ModuleEntry module = k.module();

        // Fill in class name
        getMeta().java_lang_StackTraceElement_declaringClass.setObject(ste, classGetName.invokeDirect(guestClass));
        getMeta().java_lang_StackTraceElement_declaringClassObject.setObject(ste, guestClass);

        // Fill in loader name
        if (!StaticObject.isNull(loader)) {
            StaticObject loaderName = getMeta().java_lang_ClassLoader_name.getObject(loader);
            if (!StaticObject.isNull(loader)) {
                getMeta().java_lang_StackTraceElement_classLoaderName.setObject(ste, loaderName);
            }
        }

        // Fill in method name
        Symbol<Name> mname = m.getName();
        getMeta().java_lang_StackTraceElement_methodName.setObject(ste, getMeta().toGuestString(mname));

        // Fill in module
        if (module.isNamed()) {
            getMeta().java_lang_StackTraceElement_moduleName.setObject(ste, getMeta().toGuestString(module.getName()));
            // TODO: module version
        }

        // Fill in source information
        getMeta().java_lang_StackTraceElement_fileName.setObject(ste, getMeta().toGuestString(m.getSourceFile()));
        getMeta().java_lang_StackTraceElement_lineNumber.setInt(ste, m.bciToLineNumber(element.getBCI()));
    }

    private void checkStackWalkArguments(int batchSize, int startIndex, @JavaType(Object[].class) StaticObject frames) {
        if (StaticObject.isNull(frames)) {
            Meta meta = getMeta();
            throw meta.throwNullPointerException();
        }
        assert frames.isArray();
        int limit = startIndex + batchSize;
        if (frames.length() < limit) {
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Not enough space in buffers");
        }
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public @JavaType(Object.class) StaticObject JVM_CallStackWalk(
                    @JavaType(internalName = "Ljava/lang/StackStreamFactory;") StaticObject stackStream, long mode, int skipframes,
                    int batchSize, int startIndex,
                    @JavaType(Object[].class) StaticObject frames) {
        checkStackWalkArguments(batchSize, startIndex, frames);
        return stackWalk.fetchFirstBatch(stackStream, mode, skipframes, batchSize, startIndex, frames, getMeta());
    }

    @VmImpl(isJni = true)
    @TruffleBoundary
    public int JVM_MoreStackWalk(
                    @JavaType(internalName = "Ljava/lang/StackStreamFactory;") StaticObject stackStream, long mode, long anchor,
                    int batchSize, int startIndex,
                    @JavaType(Object[].class) StaticObject frames) {
        checkStackWalkArguments(batchSize, startIndex, frames);
        return stackWalk.fetchNextBatch(stackStream, mode, anchor, batchSize, startIndex, frames, getMeta());
    }

    // endregion stackwalk

    // Checkstyle: resume method name check
}
