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
package com.oracle.truffle.espresso.vm;

import static com.oracle.truffle.espresso.jni.JniVersion.JNI_VERSION_1_1;
import static com.oracle.truffle.espresso.jni.JniVersion.JNI_VERSION_1_2;
import static com.oracle.truffle.espresso.jni.JniVersion.JNI_VERSION_1_4;
import static com.oracle.truffle.espresso.jni.JniVersion.JNI_VERSION_1_6;
import static com.oracle.truffle.espresso.jni.JniVersion.JNI_VERSION_1_8;

import java.lang.reflect.Array;
import java.lang.reflect.Parameter;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlContext;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.Utils;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.MethodParametersAttribute;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.Validation;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jni.Callback;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.jni.JniImpl;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.jni.NativeLibrary;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
import com.oracle.truffle.espresso.runtime.EspressoProperties;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.substitutions.SuppressFBWarnings;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Class;

/**
 * Espresso implementation of the VM interface (libjvm).
 */
public final class VM extends NativeEnv implements ContextAccess {

    private final TruffleObject initializeMokapotContext;
    private final TruffleObject disposeMokapotContext;
    private final TruffleObject getJavaVM;

    private final JniEnv jniEnv;

    private long vmPtr;

    // mokapot.dll (Windows) or libmokapot.so (Unixes) is the Espresso implementation of the VM
    // interface (libjvm).
    // Espresso loads all shared libraries in a private namespace (e.g. using dlmopen on Linux).
    // libmokapot must be loaded strictly before any other library in the private namespace to
    // avoid linking with HotSpot libjvm, then libjava is loaded and further system libraries,
    // libzip, libnet, libnio ...
    private final TruffleObject mokapotLibrary;

    // libjava must be loaded after mokapot.
    private final TruffleObject javaLibrary;

    public TruffleObject getJavaLibrary() {
        return javaLibrary;
    }

    private VM(JniEnv jniEnv) {
        this.jniEnv = jniEnv;
        try {
            EspressoProperties props = getContext().getVmProperties();

            List<Path> libjavaSearchPaths = new ArrayList<>();
            libjavaSearchPaths.addAll(props.bootLibraryPath());
            libjavaSearchPaths.addAll(props.javaLibraryPath());

            mokapotLibrary = loadLibrary(props.espressoLibraryPath(), "mokapot");

            assert mokapotLibrary != null;
            javaLibrary = loadLibrary(libjavaSearchPaths, "java");

            initializeMokapotContext = NativeLibrary.lookupAndBind(mokapotLibrary,
                            "initializeMokapotContext", "(env, sint64, (string): pointer): sint64");

            disposeMokapotContext = NativeLibrary.lookupAndBind(mokapotLibrary,
                            "disposeMokapotContext",
                            "(env, sint64): void");

            getJavaVM = NativeLibrary.lookupAndBind(mokapotLibrary,
                            "getJavaVM",
                            "(env): sint64");

            Callback lookupVmImplCallback = new Callback(LOOKUP_VM_IMPL_PARAMETER_COUNT, new Callback.Function() {
                @Override
                public Object call(Object... args) {
                    try {
                        return VM.this.lookupVmImpl((String) args[0]);
                    } catch (ClassCastException e) {
                        throw EspressoError.shouldNotReachHere(e);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw EspressoError.shouldNotReachHere(e);
                    }
                }
            });
            this.vmPtr = (long) InteropLibrary.getFactory().getUncached().execute(initializeMokapotContext, jniEnv.getNativePointer(), lookupVmImplCallback);

            assert this.vmPtr != 0;

        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException | UnknownIdentifierException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Override
    public final EspressoContext getContext() {
        return jniEnv.getContext();
    }

    public long getJavaVM() {
        try {
            return (long) InteropLibrary.getFactory().getUncached().execute(getJavaVM);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("getJavaVM failed");
        }
    }

    private static Map<String, VMSubstitutor> buildVmMethods() {
        Map<String, VMSubstitutor> map = new HashMap<>();
        for (VMSubstitutor method : VMCollector.getInstance()) {
            assert !map.containsKey(method.methodName()) : "VmImpl for " + method + " already exists";
            map.put(method.methodName(), method);
        }
        return Collections.unmodifiableMap(map);
    }

    private static final Map<String, VMSubstitutor> vmMethods = buildVmMethods();

    public static VM create(JniEnv jniEnv) {
        return new VM(jniEnv);
    }

    private static final int JVM_CALLER_DEPTH = -1;

    public static final int LOOKUP_VM_IMPL_PARAMETER_COUNT = 1;

    public TruffleObject lookupVmImpl(String methodName) {
        VMSubstitutor m = vmMethods.get(methodName);
        try {
            // Dummy placeholder for unimplemented/unknown methods.
            if (m == null) {
                // System.err.println("Fetching unknown/unimplemented VM method: " + methodName);
                return (TruffleObject) InteropLibrary.getFactory().getUncached().execute(jniEnv.dupClosureRefAndCast("(pointer): void"),
                                new Callback(1, new Callback.Function() {
                                    @Override
                                    public Object call(Object... args) {
                                        CompilerDirectives.transferToInterpreter();
                                        System.err.println("Calling unimplemented VM method: " + methodName);
                                        throw EspressoError.unimplemented("VM method: " + methodName);
                                    }
                                }));
            }

            String signature = m.jniNativeSignature();
            Callback target = vmMethodWrapper(m);
            return (TruffleObject) InteropLibrary.getFactory().getUncached().execute(jniEnv.dupClosureRefAndCast(signature), target);

        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    // region VM methods

    @VmImpl
    @JniImpl
    public static long JVM_CurrentTimeMillis(@SuppressWarnings("unused") StaticObject ignored) {
        return System.currentTimeMillis();
    }

    @VmImpl
    @JniImpl
    public static long JVM_NanoTime(@SuppressWarnings("unused") StaticObject ignored) {
        return System.nanoTime();
    }

    /**
     * (Identity) hash code must be respected for wrappers. The same object could be wrapped by two
     * different instances of StaticObjectWrapper. Wrappers are transparent, it's identity comes
     * from the wrapped object.
     */
    @VmImpl
    @JniImpl
    public static int JVM_IHashCode(@Host(Object.class) StaticObject object) {
        return System.identityHashCode(MetaUtil.maybeUnwrapNull(object));
    }

    @VmImpl
    @JniImpl
    public void JVM_ArrayCopy(@SuppressWarnings("unused") Object ignored, @Host(Object.class) StaticObject src, int srcPos, @Host(Object.class) StaticObject dest, int destPos, int length) {
        try {
            if (src.isArray() && dest.isArray()) {
                System.arraycopy((src).unwrap(), srcPos, dest.unwrap(), destPos, length);
            } else {
                assert src.getClass().isArray();
                assert dest.getClass().isArray();
                System.arraycopy(src, srcPos, dest, destPos, length);
            }
        } catch (Exception e) {
            throw getMeta().throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @VmImpl
    @JniImpl
    public @Host(Object.class) StaticObject JVM_Clone(@Host(Object.class) StaticObject self) {
        if (self.isArray()) {
            // For arrays.
            return self.copy();
        }
        Meta meta = getMeta();
        if (!meta.Cloneable.isAssignableFrom(self.getKlass())) {
            throw meta.throwEx(java.lang.CloneNotSupportedException.class);
        }

        // Normal object just copy the fields.
        return self.copy();
    }

    public Callback vmMethodWrapper(VMSubstitutor m) {
        int extraArg = (m.isJni()) ? 1 : 0;

        return new Callback(m.parameterCount() + extraArg, new Callback.Function() {
            @Override
            @CompilerDirectives.TruffleBoundary
            public Object call(Object... args) {
                boolean isJni = m.isJni();
                try {

                    // Substitute raw pointer by proper `this` reference.
                    // System.err.print("Call DEFINED method: " + m.getName() +
                    // Arrays.toString(shiftedArgs));
                    return m.invoke(VM.this, args);
                } catch (EspressoException e) {
                    if (isJni) {
                        jniEnv.getThreadLocalPendingException().set(e.getException());
                        return defaultValue(m.returnType());
                    }
                    throw EspressoError.shouldNotReachHere(e);
                } catch (StackOverflowError | OutOfMemoryError e) {
                    if (isJni) {
                        // This will most likely SOE again. Nothing we can do about that
                        // unfortunately.
                        jniEnv.getThreadLocalPendingException().set(getMeta().initEx(e.getClass()));
                        return defaultValue(m.returnType());
                    }
                    throw e;
                } catch (RuntimeException | VirtualMachineError e) {
                    throw e;
                } catch (ThreadDeath e) {
                    throw getMeta().throwEx(ThreadDeath.class);
                } catch (Throwable e) {
                    throw EspressoError.shouldNotReachHere(e);
                }
            }
        });
    }

    @VmImpl
    @JniImpl
    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, .notifyAll is just forwarded from the guest.")
    public void JVM_MonitorNotifyAll(@Host(Object.class) StaticObject self) {
        try {
            self.notifyAll();
        } catch (IllegalMonitorStateException e) {
            throw getMeta().throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @VmImpl
    @JniImpl
    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, .notify is just forwarded from the guest.")
    public void JVM_MonitorNotify(@Host(Object.class) StaticObject self) {
        try {
            self.notify();
        } catch (IllegalMonitorStateException e) {
            throw getMeta().throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @VmImpl
    @JniImpl
    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, .wait is just forwarded from the guest.")
    public void JVM_MonitorWait(@Host(Object.class) StaticObject self, long timeout) {
        try {
            self.wait(timeout);
        } catch (InterruptedException | IllegalMonitorStateException | IllegalArgumentException e) {
            throw getMeta().throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @VmImpl
    public static boolean JVM_IsNaN(double d) {
        return Double.isNaN(d);
    }

    @VmImpl
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
    // TODO(peterssen): @Type annotaion only for readability purposes.
    public @Host(String.class) StaticObject JVM_InternString(@Host(String.class) StaticObject self) {
        return getInterpreterToVM().intern(self);
    }

    // endregion VM methods

    // region JNI Invocation Interface
    @VmImpl
    public static int DestroyJavaVM() {
        return JniEnv.JNI_OK;
    }

    @SuppressWarnings("unused")
    @VmImpl
    public static int AttachCurrentThread(long penvPtr, long argsPtr) {
        System.err.println("AttachCurrentThread!!! " + penvPtr + " " + Thread.currentThread());
        return JniEnv.JNI_OK;
    }

    @VmImpl
    public static int DetachCurrentThread() {
        System.err.println("DetachCurrentThread!!!" + Thread.currentThread());
        return JniEnv.JNI_OK;
    }

    /**
     * <h3>jint GetEnv(JavaVM *vm, void **env, jint version);</h3>
     *
     * @param vmPtr_ The virtual machine instance from which the interface will be retrieved.
     * @param envPtr pointer to the location where the JNI interface pointer for the current thread
     *            will be placed.
     * @param version The requested JNI version.
     *
     * @returns If the current thread is not attached to the VM, sets *env to NULL, and returns
     *          JNI_EDETACHED. If the specified version is not supported, sets *env to NULL, and
     *          returns JNI_EVERSION. Otherwise, sets *env to the appropriate interface, and returns
     *          JNI_OK.
     */
    @SuppressWarnings("unused")
    @VmImpl
    public int GetEnv(long vmPtr_, long envPtr, int version) {
        // TODO(peterssen): Check the thread is attached, and that the VM pointer matches.
        LongBuffer buf = directByteBuffer(envPtr, 1, JavaKind.Long).asLongBuffer();
        buf.put(jniEnv.getNativePointer());
        return JniEnv.JNI_OK;
    }

    @SuppressWarnings("unused")
    @VmImpl
    public static int AttachCurrentThreadAsDaemon(long penvPtr, long argsPtr) {
        return JniEnv.JNI_OK;
    }

    public static class StackElement {
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
        public StackElement[] trace;
        public int size;
        public int capacity;

        public StackTrace() {
            this.trace = new StackElement[EspressoContext.DEFAULT_STACK_SIZE];
            this.capacity = EspressoContext.DEFAULT_STACK_SIZE;
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

    // endregion JNI Invocation Interface
    @VmImpl
    @JniImpl
    public @Host(Throwable.class) StaticObject JVM_FillInStackTrace(@Host(Throwable.class) StaticObject self, @SuppressWarnings("unused") int dummy) {
        assert EspressoException.isUnwinding(self, getMeta());
        self.setHiddenField(getMeta().HIDDEN_FRAMES, new StackTrace());
        return self;
    }

    @VmImpl
    @JniImpl
    @SuppressWarnings("unchecked")
    public int JVM_GetStackTraceDepth(@Host(Throwable.class) StaticObject self) {
        Meta meta = getMeta();
        StackTrace frames = (StackTrace) self.getHiddenField(meta.HIDDEN_FRAMES);
        if (EspressoException.isUnwinding(self, meta)) {
            InterpreterToVM.fillInStackTrace(self, false, meta);
        }
        assert !EspressoException.isUnwinding(self, meta);
        return frames.size;
    }

    @VmImpl
    @JniImpl
    @SuppressWarnings("unchecked")
    public @Host(StackTraceElement.class) StaticObject JVM_GetStackTraceElement(@Host(Throwable.class) StaticObject self, int index) {
        Meta meta = getMeta();
        if (index < 0) {
            throw meta.throwEx(IndexOutOfBoundsException.class);
        }
        StaticObject ste = meta.StackTraceElement.allocateInstance();
        StackTrace frames = EspressoException.getFrames(self, meta);
        if (EspressoException.isUnwinding(self, meta)) {
            InterpreterToVM.fillInStackTrace(self, false, meta);
        }
        assert !EspressoException.isUnwinding(self, meta);
        if (index >= frames.size) {
            throw meta.throwEx(IndexOutOfBoundsException.class);
        }
        StackElement stackElement = frames.trace[index];
        Method method = stackElement.getMethod();
        if (method == null) {
            return StaticObject.NULL;
        }
        int bci = stackElement.getBCI();

        meta.StackTraceElement_init.invokeDirect(
                        /* this */ ste,
                        /* declaringClass */ meta.toGuestString(MetaUtil.internalNameToJava(method.getDeclaringKlass().getType().toString(), true, true)),
                        /* methodName */ meta.toGuestString(method.getName()),
                        /* fileName */ meta.toGuestString(method.getSourceFile()),
                        /* lineNumber */ method.BCItoLineNumber(bci));

        return ste;
    }

    private static void checkTag(ConstantPool pool, int index, ConstantPool.Tag expected) {
        ConstantPool.Tag target = pool.tagAt(index);
        if (target != expected) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwExWithMessage(IllegalArgumentException.class, "Wrong type at constant pool index");
        }
    }

    @VmImpl
    @JniImpl
    public static int JVM_ConstantPoolGetSize(@SuppressWarnings("unused") Object unused, StaticObject jcpool) {
        return jcpool.getMirrorKlass().getConstantPool().length();
    }

    @VmImpl
    @JniImpl
    public static @Host(Class.class) StaticObject JVM_ConstantPoolGetClassAt(@SuppressWarnings("unused") Object unused, @Host(Object.class) StaticObject jcpool, int index) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.CLASS);
        return ((RuntimeConstantPool) jcpool.getMirrorKlass().getConstantPool()).resolvedKlassAt(null, index).mirror();
    }

    @VmImpl
    @JniImpl
    public static double JVM_ConstantPoolGetDoubleAt(@SuppressWarnings("unused") Object unused, @Host(Object.class) StaticObject jcpool, int index) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.DOUBLE);
        return jcpool.getMirrorKlass().getConstantPool().doubleAt(index);
    }

    @VmImpl
    @JniImpl
    public static float JVM_ConstantPoolGetFloatAt(@SuppressWarnings("unused") Object unused, @Host(Object.class) StaticObject jcpool, int index) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.FLOAT);
        return jcpool.getMirrorKlass().getConstantPool().floatAt(index);
    }

    @VmImpl
    @JniImpl
    public static @Host(String.class) StaticObject JVM_ConstantPoolGetStringAt(@SuppressWarnings("unused") Object unused, @Host(Object.class) StaticObject jcpool, int index) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.STRING);
        return ((RuntimeConstantPool) jcpool.getMirrorKlass().getConstantPool()).resolvedStringAt(index);
    }

    @VmImpl
    @JniImpl
    public @Host(String.class) StaticObject JVM_ConstantPoolGetUTF8At(@SuppressWarnings("unused") Object unused, StaticObject jcpool, int index) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.UTF8);
        return getMeta().toGuestString(jcpool.getMirrorKlass().getConstantPool().symbolAt(index).toString());
    }

    @VmImpl
    @JniImpl
    public static int JVM_ConstantPoolGetIntAt(@SuppressWarnings("unused") Object unused, StaticObject jcpool, int index) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.INTEGER);
        return jcpool.getMirrorKlass().getConstantPool().intAt(index);
    }

    @VmImpl
    @JniImpl
    public static long JVM_ConstantPoolGetLongAt(@SuppressWarnings("unused") Object unused, StaticObject jcpool, int index) {
        checkTag(jcpool.getMirrorKlass().getConstantPool(), index, ConstantPool.Tag.LONG);
        return jcpool.getMirrorKlass().getConstantPool().longAt(index);
    }

    @VmImpl
    @JniImpl
    public @Host(Class.class) StaticObject JVM_DefineClass(String name, @Host(ClassLoader.class) StaticObject loader, long bufPtr, int len,
                    @Host(ProtectionDomain.class) StaticObject pd) {
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
                throw getMeta().throwExWithMessage(NoClassDefFoundError.class, name);
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
    public @Host(Class.class) StaticObject JVM_DefineClassWithSource(String name, @Host(ClassLoader.class) StaticObject loader, long bufPtr, int len,
                    @Host(ProtectionDomain.class) StaticObject pd, @SuppressWarnings("unused") String source) {
        // FIXME(peterssen): Source is ignored.
        return JVM_DefineClass(name, loader, bufPtr, len, pd);
    }

    @VmImpl
    @JniImpl
    public @Host(Class.class) StaticObject JVM_FindLoadedClass(@Host(ClassLoader.class) StaticObject loader, @Host(String.class) StaticObject name) {
        Symbol<Type> type = getTypes().fromClassGetName(Meta.toHostString(name));
        Klass klass = getRegistries().findLoadedClass(type, loader);
        if (klass == null) {
            return StaticObject.NULL;
        }
        return klass.mirror();
    }

    private final ConcurrentHashMap<Long, TruffleObject> handle2Lib = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, TruffleObject> handle2Sym = new ConcurrentHashMap<>();

    // region Library support
    @VmImpl
    public long JVM_LoadLibrary(String name) {
        try {
            TruffleObject lib = NativeLibrary.loadLibrary(Paths.get(name));
            java.lang.reflect.Field f = lib.getClass().getDeclaredField("handle");
            f.setAccessible(true);
            long handle = (long) f.get(lib);
            handle2Lib.put(handle, lib);
            return handle;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @VmImpl
    public static void JVM_UnloadLibrary(@SuppressWarnings("unused") long handle) {
        // TODO(peterssen): Do unload the library.
        System.err.println("JVM_UnloadLibrary called but library was not unloaded!");
    }

    @VmImpl
    public long JVM_FindLibraryEntry(long libHandle, String name) {
        if (libHandle == 0) {
            System.err.println("JVM_FindLibraryEntry from default/global namespace (0): " + name);
            return 0L;
        }
        // TODO(peterssen): Workaround for MacOS flags: RTLD_DEFAULT...
        if (-6 < libHandle && libHandle < 0) {
            System.err.println("JVM_FindLibraryEntry with unsupported flag/handle/namespace (" + libHandle + "): " + name);
            return 0L;
        }
        try {
            TruffleObject function = NativeLibrary.lookup(handle2Lib.get(libHandle), name);
            long handle = InteropLibrary.getFactory().getUncached().asPointer(function);
            handle2Sym.put(handle, function);
            return handle;
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        } catch (UnknownIdentifierException e) {
            return 0; // not found
        }
    }

    // endregion Library support
    @VmImpl
    public static boolean JVM_IsSupportedJNIVersion(int version) {
        return version == JNI_VERSION_1_1 ||
                        version == JNI_VERSION_1_2 ||
                        version == JNI_VERSION_1_4 ||
                        version == JNI_VERSION_1_6 ||
                        version == JNI_VERSION_1_8;
    }

    @VmImpl
    public static int JVM_GetInterfaceVersion() {
        return JniEnv.JVM_INTERFACE_VERSION;
    }

    public void dispose() {
        assert vmPtr != 0L : "Mokapot already disposed";
        try {
            InteropLibrary.getFactory().getUncached().execute(disposeMokapotContext, vmPtr);
            this.vmPtr = 0L;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot dispose Espresso libjvm (mokapot).");
        }
        assert vmPtr == 0L;
    }

    @VmImpl
    public static long JVM_TotalMemory() {
        // TODO(peterssen): What to report here?
        return Runtime.getRuntime().totalMemory();
    }

    @VmImpl
    public static long JVM_MaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    @VmImpl
    public static void JVM_GC() {
        System.gc();
    }

    @VmImpl
    public static void JVM_Halt(int code) {
        throw new EspressoExitException(code);
    }

    @VmImpl
    public static void JVM_Exit(int code) {
        // System.exit(code);
        // Unlike Halt, runs finalizers
        throw new EspressoExitException(code);
    }

    @VmImpl
    @JniImpl
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
        setProperty.invokeWithConversions(properties, "java.class.path", Utils.stringify(props.classpath()));
        setProperty.invokeWithConversions(properties, "java.home", props.javaHome().toString());
        setProperty.invokeWithConversions(properties, "sun.boot.class.path", Utils.stringify(props.bootClasspath()));
        setProperty.invokeWithConversions(properties, "java.library.path", Utils.stringify(props.javaLibraryPath()));
        setProperty.invokeWithConversions(properties, "sun.boot.library.path", Utils.stringify(props.bootLibraryPath()));
        setProperty.invokeWithConversions(properties, "java.ext.dirs", Utils.stringify(props.extDirs()));

        // Set VM information.
        setProperty.invokeWithConversions(properties, "java.vm.specification.version", EspressoLanguage.VM_SPECIFICATION_VERSION);
        setProperty.invokeWithConversions(properties, "java.vm.specification.name", EspressoLanguage.VM_SPECIFICATION_NAME);
        setProperty.invokeWithConversions(properties, "java.vm.specification.vendor", EspressoLanguage.VM_SPECIFICATION_VENDOR);
        setProperty.invokeWithConversions(properties, "java.vm.version", EspressoLanguage.VM_VERSION);
        setProperty.invokeWithConversions(properties, "java.vm.name", EspressoLanguage.VM_NAME);
        setProperty.invokeWithConversions(properties, "java.vm.vendor", EspressoLanguage.VM_VENDOR);
        setProperty.invokeWithConversions(properties, "java.vm.info", EspressoLanguage.VM_INFO);

        return properties;
    }

    @VmImpl
    @JniImpl
    public int JVM_GetArrayLength(@Host(Object.class) StaticObject array) {
        try {
            return Array.getLength(MetaUtil.unwrapArrayOrNull(array));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw getMeta().throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    @VmImpl
    @JniImpl
    public static boolean JVM_DesiredAssertionStatus(@Host(Class.class) StaticObject unused, @Host(Class.class) StaticObject cls) {
        // TODO(peterssen): Assertions are always disabled, use the VM arguments.
        return false;
    }

    private static FrameInstance getCallerFrame(int depth) {
        // TODO(peterssen): HotSpot verifies that the method is marked as @CallerSensitive.
        // Non-Espresso frames (e.g TruffleNFI) are ignored.
        // The call stack should look like this:
        // 2 : the @CallerSensitive annotated method.
        // ... : skipped non-Espresso frames.
        // 1 : getCallerClass method.
        // ... :
        // 0 : the callee.
        //
        // JVM_CALLER_DEPTH => the caller.
        int callerDepth = (depth == JVM_CALLER_DEPTH) ? 2 : depth + 1;

        final int[] depthCounter = new int[]{callerDepth};
        FrameInstance target = Truffle.getRuntime().iterateFrames(
                        new FrameInstanceVisitor<FrameInstance>() {
                            @Override
                            public FrameInstance visitFrame(FrameInstance frameInstance) {
                                Method m = getMethodFromFrame(frameInstance);
                                if (m != null && --depthCounter[0] < 0) {
                                    return frameInstance;
                                }
                                return null;
                            }
                        });
        if (target != null) {
            return target;
        }
        throw EspressoError.shouldNotReachHere();
    }

    private static EspressoRootNode getEspressoRootFromFrame(FrameInstance frameInstance) {
        if (frameInstance.getCallTarget() instanceof RootCallTarget) {
            RootCallTarget callTarget = (RootCallTarget) frameInstance.getCallTarget();
            RootNode rootNode = callTarget.getRootNode();
            if (rootNode instanceof EspressoRootNode) {
                return ((EspressoRootNode) rootNode);
            }
        }
        return null;
    }

    private static Method getMethodFromFrame(FrameInstance frameInstance) {
        EspressoRootNode root = getEspressoRootFromFrame(frameInstance);
        if (root != null) {
            return root.getMethod();
        }
        return null;
    }

    private static Method getCallerMethod(int depth) {
        FrameInstance callerFrame = getCallerFrame(depth);
        if (callerFrame == null) {
            return null;
        }
        return getMethodFromFrame(callerFrame);
    }

    @VmImpl
    @JniImpl
    public static @Host(Class.class) StaticObject JVM_GetCallerClass(int depth) {
        Method callerMethod = getCallerMethod(depth);
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
        return StaticObject.createArray(getMeta().Class_Array, result.toArray(StaticObject.EMPTY_ARRAY));
    }

    private static boolean isIgnoredBySecurityStackWalk(Method m, Meta meta) {
        Klass holderKlass = m.getDeclaringKlass();
        if (holderKlass == meta.Method && m.getName() == Name.invoke) {
            return true;
        }
        if (meta.MethodAccessorImpl.isAssignableFrom(holderKlass)) {
            return true;
        }
        if (MethodHandleIntrinsics.isMethodHandleIntrinsic(m, meta)) {
            return true;
        }
        return false;
    }

    private boolean isAuthorized(StaticObject context, Klass klass) {
        if (!StaticObject.isNull(getMeta().System.getStatics().getField(getMeta().System_securityManager))) {
            if (getMeta().ProtectionDomain_impliesCreateAccessControlContext == null) {
                return true;
            }
            if ((boolean) getMeta().AccessControlContext_isAuthorized.invokeDirect(context)) {
                return true;
            }
            StaticObject pd = Target_java_lang_Class.getProtectionDomain0(klass.mirror());
            if (pd != StaticObject.NULL) {
                return (boolean) getMeta().ProtectionDomain_impliesCreateAccessControlContext.invokeDirect(pd);
            }
        }
        return true;
    }

    private @Host(AccessControlContext.class) StaticObject createACC(@Host(ProtectionDomain[].class) StaticObject context,
                    boolean isPriviledged,
                    @Host(AccessControlContext.class) StaticObject priviledgedContext) {
        Klass accKlass = getMeta().AccessControlContext;
        StaticObject acc = accKlass.allocateInstance();
        acc.setField(getMeta().ACC_context, context);
        acc.setField(getMeta().ACC_privilegedContext, priviledgedContext);
        acc.setBooleanField(getMeta().ACC_isPrivileged, isPriviledged);
        if (getMeta().ACC_isAuthorized != null) {
            acc.setBooleanField(getMeta().ACC_isAuthorized, true);
        }
        return acc;
    }

    private @Host(AccessControlContext.class) StaticObject createDummyACC() {
        Klass pdKlass = getMeta().ProtectionDomain;
        StaticObject pd = pdKlass.allocateInstance();
        getMeta().ProtectionDomain_init_CodeSource_PermissionCollection.invokeDirect(pd, StaticObject.NULL, StaticObject.NULL);
        StaticObject context = StaticObject.wrap(new StaticObject[]{pd});
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

        public void push(FrameInstance frame, StaticObject context) {
            top = new Element(frame, context, top);
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

        static private class Element {
            long frameID;
            StaticObject context;
            Element next;

            public Element(FrameInstance frame, StaticObject context, Element next) {
                this.frameID = initPrivilegedFrame(frame);
                this.context = context;
                this.next = next;
            }

            public boolean compare(FrameInstance other) {
                try {
                    FrameSlot slot = privilegedFrameSlots.get(getMethodFromFrame(other).identity());
                    return slot != null && other.getFrame(FrameInstance.FrameAccess.READ_ONLY).getLong(slot) == frameID;
                } catch (FrameSlotTypeException e) {
                    return false;
                }
            }

            // Dummy.
            private static final Object frameIdSlotIdentifier = new Object();

            private static final EconomicMap<Method, FrameSlot> privilegedFrameSlots = EconomicMap.create(Equivalence.IDENTITY);

            private static long newFrameID = 0L;

            /**
             * Injects the frame ID in the frame. Spawns a new frame slot in the frame descriptor of
             * the corresponding RootNode if needed.
             * 
             * @param frame the current privileged frame.
             * @return the frame ID of the frame.
             */
            private static long initPrivilegedFrame(FrameInstance frame) {
                Method m = getMethodFromFrame(frame);
                FrameSlot slot = privilegedFrameSlots.get(m.identity());
                if (slot == null) {
                    slot = initSlot(frame, m);
                }
                assert slot == privilegedFrameSlots.get(m.identity());
                long id = ++newFrameID;
                frame.getFrame(FrameInstance.FrameAccess.READ_WRITE).setLong(slot, id);
                return id;
            }

            /**
             * Responsible for spawning the frame slot of root nodes that haven't yet been
             * encountered by JVM_doPrivileged.
             */
            private static FrameSlot initSlot(FrameInstance frame, Method m) {
                synchronized (privilegedFrameSlots) {
                    FrameSlot result = privilegedFrameSlots.get(m.identity());
                    if (result != null) {
                        return result;
                    }
                    result = getEspressoRootFromFrame(frame).getFrameDescriptor().addFrameSlot(frameIdSlotIdentifier, FrameSlotKind.Long);
                    privilegedFrameSlots.put(m, result);
                    return result;
                }
            }
        }
    }

    private static final ThreadLocal<PrivilegedStack> privilegedStackThreadLocal = ThreadLocal.withInitial(PrivilegedStack.supplier);

    @VmImpl
    @JniImpl
    @CompilerDirectives.TruffleBoundary
    @SuppressWarnings("unused")
    public @Host(Object.class) StaticObject JVM_DoPrivileged(@Host(Class.class) StaticObject cls,
                    @Host(typeName = "PrivilegedAction OR PrivilegedActionException") StaticObject action,
                    @Host(AccessControlContext.class) StaticObject context,
                    boolean wrapException) {
        if (StaticObject.isNull(action)) {
            throw getMeta().throwEx(NullPointerException.class);
        }
        FrameInstance callerFrame = getCallerFrame(0);
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
            getMeta().throwEx(InternalError.class);
        }

        // Prepare the privileged stack
        PrivilegedStack stack = privilegedStackThreadLocal.get();
        stack.push(callerFrame, acc);

        // Execute the action.
        StaticObject result = StaticObject.NULL;
        try {
            result = (StaticObject) run.invokeDirect(action);
        } catch (EspressoException e) {
            if (getMeta().Exception.isAssignableFrom(e.getException().getKlass()) &&
                            !getMeta().RuntimeException.isAssignableFrom(e.getException().getKlass())) {
                StaticObject wrapper = getMeta().PrivilegedActionException.allocateInstance();
                getMeta().PrivilegedActionException_init_Exception.invokeDirect(wrapper, e.getException());
                throw new EspressoException(wrapper);
            }
            throw e;
        } finally {
            stack.pop();
        }
        return result;
    }

    @VmImpl
    @JniImpl
    @CompilerDirectives.TruffleBoundary
    @SuppressWarnings("unused")
    public @Host(Object.class) StaticObject JVM_GetStackAccessControlContext(@Host(Class.class) StaticObject cls) {
        ArrayList<StaticObject> domains = new ArrayList<>();
        final PrivilegedStack stack = privilegedStackThreadLocal.get();
        final boolean[] isPrivileged = new boolean[]{false};

        StaticObject context = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<StaticObject>() {
            StaticObject prevDomain = StaticObject.NULL;

            public StaticObject visitFrame(FrameInstance frameInstance) {
                Method m = getMethodFromFrame(frameInstance);
                if (m != null) {
                    if (stack.compare(frameInstance)) {
                        isPrivileged[0] = true;
                    }
                    StaticObject domain = Target_java_lang_Class.getProtectionDomain0(m.getDeclaringKlass().mirror());
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

        StaticObject guestContext = StaticObject.createArray(getMeta().ProtectionDomain.array(), domains.toArray(StaticObject.EMPTY_ARRAY));
        return createACC(guestContext, isPrivileged[0], context == null ? StaticObject.NULL : context);
    }

    @VmImpl
    @JniImpl
    @SuppressWarnings("unused")
    public @Host(Object.class) StaticObject JVM_GetInheritedAccessControlContext(@Host(Class.class) StaticObject cls) {
        return getContext().getCurrentThread().getField(getMeta().Thread_inheritedAccessControlContext);
    }

    @VmImpl
    @JniImpl
    public static @Host(Object.class) StaticObject JVM_LatestUserDefinedLoader() {
        StaticObject result = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<StaticObject>() {
            public StaticObject visitFrame(FrameInstance frameInstance) {
                Method m = getMethodFromFrame(frameInstance);
                if (m != null) {
                    Klass holder = m.getDeclaringKlass();
                    Meta meta = holder.getMeta();
                    // vfst.skip_reflection_related_frames(); // Only needed for 1.4 reflection
                    if (meta.MethodAccessorImpl.isAssignableFrom(holder) || meta.ConstructorAccessorImpl.isAssignableFrom(holder)) {
                        return null;
                    }

                    StaticObject loader = holder.getDefiningClassLoader();
                    // if (loader != NULL && !SystemDictionary::is_ext_class_loader(loader))
                    if (StaticObject.notNull(loader) && !Type.sun_misc_Launcher_ExtClassLoader.equals(loader.getKlass().getType())) {
                        return loader;
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
        return klass.getModifiers();
    }

    @VmImpl
    @JniImpl
    public @Host(Class.class) StaticObject JVM_FindClassFromBootLoader(String name) {
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

        Klass klass = getRegistries().loadKlassWithBootClassLoader(type);
        if (klass == null) {
            return StaticObject.NULL;
        }

        return klass.mirror();
    }

    public TruffleObject getLibrary(long handle) {
        return handle2Lib.get(handle);
    }

    public TruffleObject getFunction(long handle) {
        return handle2Sym.get(handle);
    }

    /**
     * Returns the value of the indexed component in the specified array object. The value is
     * automatically wrapped in an object if it has a primitive type.
     *
     * @param array the array
     * @param index the index
     * @returns the (possibly wrapped) value of the indexed component in the specified array
     * @exception NullPointerException If the specified object is null
     * @exception IllegalArgumentException If the specified object is not an array
     * @exception ArrayIndexOutOfBoundsException If the specified {@code index} argument is
     *                negative, or if it is greater than or equal to the length of the specified
     *                array
     */
    @VmImpl
    @JniImpl
    public @Host(Object.class) StaticObject JVM_GetArrayElement(@Host(Object.class) StaticObject array, int index) {
        if (StaticObject.isNull(array)) {
            throw getMeta().throwEx(NullPointerException.class);
        }
        if (array.isArray()) {
            return getInterpreterToVM().getArrayObject(index, array);
        }
        if (!array.getClass().isArray()) {
            throw getMeta().throwExWithMessage(IllegalArgumentException.class, "Argument is not an array");
        }
        assert array.getClass().isArray() && array.getClass().getComponentType().isPrimitive();
        if (index < 0 || index >= JVM_GetArrayLength(array)) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, "index");
        }
        Object elem = Array.get(array, index);
        return guestBox(elem);
    }

    private static @Host(java.lang.reflect.Method.class) StaticObject getGuestReflectiveMethodRoot(@Host(java.lang.reflect.Method.class) StaticObject seed) {
        Meta meta = seed.getKlass().getMeta();
        assert InterpreterToVM.instanceOf(seed, meta.Method);
        StaticObject curMethod = seed;
        Method target = null;
        while (target == null) {
            target = (Method) curMethod.getHiddenField(meta.HIDDEN_METHOD_KEY);
            if (target == null) {
                curMethod = (StaticObject) meta.Method_root.get(curMethod);
            }
        }
        return curMethod;
    }

    private static @Host(java.lang.reflect.Field.class) StaticObject getGuestReflectiveFieldRoot(@Host(java.lang.reflect.Field.class) StaticObject seed) {
        Meta meta = seed.getKlass().getMeta();
        assert InterpreterToVM.instanceOf(seed, meta.Field);
        StaticObject curField = seed;
        Field target = null;
        while (target == null) {
            target = (Field) curField.getHiddenField(meta.HIDDEN_FIELD_KEY);
            if (target == null) {
                curField = (StaticObject) meta.Field_root.get(curField);
            }
        }
        return curField;
    }

    private static @Host(java.lang.reflect.Constructor.class) StaticObject getGuestReflectiveConstructorRoot(@Host(java.lang.reflect.Constructor.class) StaticObject seed) {
        Meta meta = seed.getKlass().getMeta();
        assert InterpreterToVM.instanceOf(seed, meta.Constructor);
        StaticObject curConstructor = seed;
        Method target = null;
        while (target == null) {
            target = (Method) curConstructor.getHiddenField(meta.HIDDEN_CONSTRUCTOR_KEY);
            if (target == null) {
                curConstructor = (StaticObject) meta.Constructor_root.get(curConstructor);
            }
        }
        return curConstructor;
    }

    @VmImpl
    @JniImpl
    public @Host(Parameter[].class) StaticObject JVM_GetMethodParameters(@Host(Object.class) StaticObject executable) {
        assert getMeta().Executable.isAssignableFrom(executable.getKlass());
        StaticObject parameterTypes = (StaticObject) executable.getKlass().lookupMethod(Name.getParameterTypes, Signature.Class_array).invokeDirect(executable);
        int numParams = parameterTypes.length();
        if (numParams == 0) {
            return StaticObject.NULL;
        }

        Method method;
        if (getMeta().Method.isAssignableFrom(executable.getKlass())) {
            method = Method.getHostReflectiveMethodRoot(executable);
        } else if (getMeta().Constructor.isAssignableFrom(executable.getKlass())) {
            method = Method.getHostReflectiveConstructorRoot(executable);
        } else {
            throw EspressoError.shouldNotReachHere();
        }

        MethodParametersAttribute methodParameters = (MethodParametersAttribute) method.getAttribute(Name.MethodParameters);

        if (methodParameters == null) {
            return StaticObject.NULL;
        }
        // Verify first.
        int cpLength = method.getConstantPool().length();
        for (MethodParametersAttribute.Entry entry : methodParameters.getEntries()) {
            int nameIndex = entry.getNameIndex();
            if (nameIndex < 0 || nameIndex >= cpLength) {
                throw getMeta().throwExWithMessage(getMeta().IllegalArgumentException,
                                getMeta().toGuestString("Constant pool index out of bounds"));
            }
            if (nameIndex != 0 && method.getConstantPool().tagAt(nameIndex) != ConstantPool.Tag.UTF8) {
                throw getMeta().throwExWithMessage(getMeta().IllegalArgumentException,
                                getMeta().toGuestString("Wrong type at constant pool index"));
            }
        }

        // TODO(peterssen): Cache guest j.l.reflect.Parameter constructor.
        // Calling the constructor is just for validation, manually setting the fields would
        // be faster.
        Method parameterInit = getMeta().Parameter.lookupDeclaredMethod(Name.INIT, getSignatures().makeRaw(Type._void,
                        /* name */ Type.String,
                        /* modifiers */ Type._int,
                        /* executable */ Type.Executable,
                        /* index */ Type._int));

        return getMeta().Parameter.allocateArray(numParams, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int index) {
                MethodParametersAttribute.Entry entry = methodParameters.getEntries()[index];
                StaticObject instance = getMeta().Parameter.allocateInstance();
                // For a 0 index, give an empty name.
                String hostName = "";
                if (entry.getNameIndex() != 0) {
                    hostName = method.getConstantPool().symbolAt(entry.getNameIndex(), "parameter name").toString();
                }
                parameterInit.invokeDirect(/* this */ instance,
                                /* name */ getMeta().toGuestString(hostName),
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
        if (InterpreterToVM.instanceOf(guestReflectionMethod, getMeta().Method)) {
            StaticObject methodRoot = getGuestReflectiveMethodRoot(guestReflectionMethod);
            assert methodRoot != null;
            return (StaticObject) methodRoot.getHiddenField(methodRoot.getKlass().getMeta().HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        } else if (InterpreterToVM.instanceOf(guestReflectionMethod, getMeta().Constructor)) {
            StaticObject constructorRoot = getGuestReflectiveConstructorRoot(guestReflectionMethod);
            assert constructorRoot != null;
            return (StaticObject) constructorRoot.getHiddenField(constructorRoot.getKlass().getMeta().HIDDEN_CONSTRUCTOR_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        } else {
            throw EspressoError.shouldNotReachHere();
        }
    }

    @VmImpl
    @JniImpl
    public @Host(byte[].class) StaticObject JVM_GetFieldTypeAnnotations(@Host(java.lang.reflect.Field.class) StaticObject guestReflectionField) {
        assert InterpreterToVM.instanceOf(guestReflectionField, getMeta().Field);
        StaticObject fieldRoot = getGuestReflectiveFieldRoot(guestReflectionField);
        assert fieldRoot != null;
        return (StaticObject) fieldRoot.getHiddenField(fieldRoot.getKlass().getMeta().HIDDEN_FIELD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
    }

    private StaticObject guestBox(Object elem) {
        if (elem instanceof Integer) {
            return (StaticObject) getMeta().Integer_valueOf.invokeDirect(null, (int) elem);
        }
        if (elem instanceof Boolean) {
            return (StaticObject) getMeta().Boolean_valueOf.invokeDirect(null, (boolean) elem);
        }
        if (elem instanceof Byte) {
            return (StaticObject) getMeta().Byte_valueOf.invokeDirect(null, (byte) elem);
        }
        if (elem instanceof Character) {
            return (StaticObject) getMeta().Character_valueOf.invokeDirect(null, (char) elem);
        }
        if (elem instanceof Short) {
            return (StaticObject) getMeta().Short_valueOf.invokeDirect(null, (short) elem);
        }
        if (elem instanceof Float) {
            return (StaticObject) getMeta().Float_valueOf.invokeDirect(null, (float) elem);
        }
        if (elem instanceof Double) {
            return (StaticObject) getMeta().Double_valueOf.invokeDirect(null, (double) elem);
        }
        if (elem instanceof Long) {
            return (StaticObject) getMeta().Long_valueOf.invokeDirect(null, (long) elem);
        }

        throw EspressoError.shouldNotReachHere("Not a boxed type " + elem);
    }

    @VmImpl
    @JniImpl
    public @Host(String.class) StaticObject JVM_GetSystemPackage(@Host(String.class) StaticObject name) {
        String hostPkgName = Meta.toHostString(name);
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
        StaticObject result = StaticObject.createArray(getMeta().String.getArrayClass(), array);
        return result;
    }

    @VmImpl
    public static long JVM_FreeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    /**
     * Espresso only supports basic -ea and -esa options. Complex per-class/package filters are
     * unsupported.
     */
    @VmImpl
    @JniImpl
    public @Host(typeName = "Ljava/lang/AssertionStatusDirectives;") StaticObject JVM_AssertionStatusDirectives(@SuppressWarnings("unused") @Host(Class.class) StaticObject unused) {
        Meta meta = getMeta();
        StaticObject instance = meta.AssertionStatusDirectives.allocateInstance();
        meta.AssertionStatusDirectives.lookupMethod(Name.INIT, Signature._void).invokeDirect(instance);
        meta.AssertionStatusDirectives_classes.set(instance, meta.String.allocateArray(0));
        meta.AssertionStatusDirectives_classEnabled.set(instance, meta._boolean.allocateArray(0));
        meta.AssertionStatusDirectives_packages.set(instance, meta.String.allocateArray(0));
        meta.AssertionStatusDirectives_packageEnabled.set(instance, meta._boolean.allocateArray(0));
        boolean ea = getContext().getEnv().getOptions().get(EspressoOptions.EnableAssertions);
        meta.AssertionStatusDirectives_deflt.set(instance, ea);
        return instance;
    }

    @VmImpl
    public static int JVM_ActiveProcessorCount() {
        return Runtime.getRuntime().availableProcessors();
    }
}
