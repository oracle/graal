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
package com.oracle.truffle.espresso.vm;

import static com.oracle.truffle.espresso.jni.JniVersion.JNI_VERSION_1_1;
import static com.oracle.truffle.espresso.jni.JniVersion.JNI_VERSION_1_2;
import static com.oracle.truffle.espresso.jni.JniVersion.JNI_VERSION_1_4;
import static com.oracle.truffle.espresso.jni.JniVersion.JNI_VERSION_1_6;
import static com.oracle.truffle.espresso.jni.JniVersion.JNI_VERSION_1_8;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.stream.Collectors;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.MethodParametersAttribute;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jni.Callback;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.jni.JniImpl;
import com.oracle.truffle.espresso.jni.NFIType;
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
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;

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

            Callback lookupVmImplCallback = Callback.wrapInstanceMethod(this, "lookupVmImpl", String.class);
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

    private static Map<String, java.lang.reflect.Method> buildVmMethods() {
        Map<String, java.lang.reflect.Method> map = new HashMap<>();
        java.lang.reflect.Method[] declaredMethods = VM.class.getDeclaredMethods();
        for (java.lang.reflect.Method method : declaredMethods) {
            VmImpl jniImpl = method.getAnnotation(VmImpl.class);
            if (jniImpl != null) {
                assert !map.containsKey(method.getName()) : "VmImpl for " + method + " already exists";
                map.put(method.getName(), method);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static final Map<String, java.lang.reflect.Method> vmMethods = buildVmMethods();

    public static VM create(JniEnv jniEnv) {
        return new VM(jniEnv);
    }

    public static String vmNativeSignature(java.lang.reflect.Method method) {
        StringBuilder sb = new StringBuilder("(");

        boolean first = true;
        if (method.getAnnotation(JniImpl.class) != null) {
            sb.append(NativeSimpleType.SINT64); // Prepend JNIEnv*;
            first = false;
        }

        for (Parameter param : method.getParameters()) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }

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

    private static final int JVM_CALLER_DEPTH = -1;

    public TruffleObject lookupVmImpl(String methodName) {
        java.lang.reflect.Method m = vmMethods.get(methodName);
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

            String signature = vmNativeSignature(m);
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

    public Callback vmMethodWrapper(java.lang.reflect.Method m) {
        int extraArg = (m.getAnnotation(JniImpl.class) != null) ? 1 : 0;

        return new Callback(m.getParameterCount() + extraArg, new Callback.Function() {
            @Override
            @CompilerDirectives.TruffleBoundary
            public Object call(Object... rawArgs) {

                boolean isJni = (m.getAnnotation(JniImpl.class) != null);

                Object[] args;
                if (isJni) {
                    assert (long) rawArgs[0] == jniEnv.getNativePointer() : "Calling JVM_ method " + m + " from alien JniEnv";
                    args = Arrays.copyOfRange(rawArgs, 1, rawArgs.length); // Strip JNIEnv* pointer,
                    // replace
                    // by VM (this) receiver.
                } else {
                    args = rawArgs;
                }

                Class<?>[] params = m.getParameterTypes();

                for (int i = 0; i < args.length; ++i) {
                    // FIXME(peterssen): Espresso should accept interop null objects, since it
                    // doesn't
                    // we must convert to Espresso null.
                    // FIXME(peterssen): Also, do use proper nodes.
                    if (args[i] instanceof TruffleObject) {
                        if (InteropLibrary.getFactory().getUncached().isNull(args[i])) {
                            if (StaticObject.class.isAssignableFrom(params[i])) {
                                args[i] = StaticObject.NULL;
                            } else {
                                args[i] = null;
                            }
                        }
                    } else {
                        // TruffleNFI pass booleans as byte, do the proper conversion.
                        if (params[i] == boolean.class) {
                            args[i] = ((byte) args[i]) != 0;
                        }
                    }
                }
                try {
                    // Substitute raw pointer by proper `this` reference.
                    // System.err.print("Call DEFINED method: " + m.getName() +
                    // Arrays.toString(shiftedArgs));
                    Object ret = m.invoke(VM.this, args);

                    if (ret instanceof Boolean) {
                        return (boolean) ret ? (byte) 1 : (byte) 0;
                    }

                    if (ret instanceof Character) {
                        return (short) (char) ret;
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
                    if (isJni) {
                        if (targetEx instanceof EspressoException) {
                            jniEnv.getThreadLocalPendingException().set(((EspressoException) targetEx).getException());
                            return defaultValue(m.getReturnType());
                        }
                    }
                    if (targetEx instanceof RuntimeException) {
                        throw (RuntimeException) targetEx;
                    }
                    if (targetEx instanceof StackOverflowError) {
                        throw getContext().getStackOverflow();
                    }
                    if (targetEx instanceof OutOfMemoryError) {
                        throw getContext().getOutOfMemory();
                    }
                    if (targetEx instanceof ThreadDeath) {
                        throw getMeta().throwEx(ThreadDeath.class);
                    }
                    // FIXME(peterssen): Handle VME exceptions back to guest.
                    throw EspressoError.shouldNotReachHere(targetEx);
                } catch (IllegalAccessException | IllegalArgumentException e) {
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
            Field field = klass.getDeclaredField("VM_SUPPORTS_LONG_CAS");
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

    // endregion JNI Invocation Interface
    @VmImpl
    @JniImpl
    public @Host(Throwable.class) StaticObject JVM_FillInStackTrace(@Host(Throwable.class) StaticObject self, @SuppressWarnings("unused") int dummy) {
        final ArrayList<FrameInstance> frames = new ArrayList<>(32);
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {
            @Override
            public Object visitFrame(FrameInstance frameInstance) {
                CallTarget callTarget = frameInstance.getCallTarget();
                if (callTarget instanceof RootCallTarget) {
                    RootNode rootNode = ((RootCallTarget) callTarget).getRootNode();
                    if (rootNode instanceof EspressoRootNode) {
                        frames.add(frameInstance);
                    }
                }
                return null;
            }
        });
        // Avoid printing the Throwable initialization
        int nonThrowableInitStartIndex = 0;
        boolean skipFillInStackTrace = true;
        boolean skipThrowableInit = true;
        for (FrameInstance fi : frames) {
            Method m = ((EspressoRootNode) ((RootCallTarget) fi.getCallTarget()).getRootNode()).getMethod();
            if (skipFillInStackTrace) {
                if (!((m.getName() == Name.fillInStackTrace) || (m.getName() == Name.fillInStackTrace0))) {
                    skipFillInStackTrace = false;
                }
            } else if (skipThrowableInit) {
                if (!(m.getName() == Name.INIT) || !m.getMeta().Throwable.isAssignableFrom(m.getDeclaringKlass())) {
                    skipThrowableInit = false;
                    break;
                }
            }
            nonThrowableInitStartIndex++;
        }
        self.setHiddenField(getMeta().HIDDEN_FRAMES, frames.subList(nonThrowableInitStartIndex, frames.size()).toArray(new FrameInstance[0]));

        getMeta().Throwable_backtrace.set(self, self);
        return self;
    }

    @VmImpl
    @JniImpl
    public int JVM_GetStackTraceDepth(@Host(Throwable.class) StaticObject self) {
        Meta meta = getMeta();
        StaticObject backtrace = (StaticObject) meta.Throwable_backtrace.get(self);
        if (StaticObject.isNull(backtrace)) {
            return 0;
        }
        return ((FrameInstance[]) backtrace.getHiddenField(meta.HIDDEN_FRAMES)).length;
    }

    @VmImpl
    @JniImpl
    public @Host(StackTraceElement.class) StaticObject JVM_GetStackTraceElement(@Host(Throwable.class) StaticObject self, int index) {
        Meta meta = getMeta();
        StaticObject ste = meta.StackTraceElement.allocateInstance();
        StaticObject backtrace = (StaticObject) meta.Throwable_backtrace.get(self);
        FrameInstance[] frames = ((FrameInstance[]) backtrace.getHiddenField(meta.HIDDEN_FRAMES));
        FrameInstance frame = frames[index];
        if (frame == null) {
            return StaticObject.NULL;
        }

        EspressoRootNode rootNode = (EspressoRootNode) ((RootCallTarget) frame.getCallTarget()).getRootNode();

        meta.StackTraceElement_init.invokeDirect(
                        /* this */ ste,
                        /* declaringClass */ meta.toGuestString(MetaUtil.internalNameToJava(rootNode.getMethod().getDeclaringKlass().getType().toString(), true, true)),
                        /* methodName */ meta.toGuestString(rootNode.getMethod().getName()),
                        /* fileName */ StaticObject.NULL,
                        /* lineNumber */ -1);

        return ste;
    }

    @VmImpl
    @JniImpl
    public static int JVM_ConstantPoolGetSize(@SuppressWarnings("unused") Object unused, StaticObject jcpool) {
        return jcpool.getMirrorKlass().getConstantPool().length();
    }

    @VmImpl
    @JniImpl
    public static @Host(Class.class) StaticObject JVM_ConstantPoolGetClassAt(@SuppressWarnings("unused") Object unused, @Host(Object.class) StaticObject jcpool, int index) {
        return ((RuntimeConstantPool) jcpool.getMirrorKlass().getConstantPool()).resolvedKlassAt(null, index).mirror();
    }

    @VmImpl
    @JniImpl
    public static double JVM_ConstantPoolGetDoubleAt(@SuppressWarnings("unused") Object unused, @Host(Object.class) StaticObject jcpool, int index) {
        return jcpool.getMirrorKlass().getConstantPool().doubleAt(index);
    }

    @VmImpl
    @JniImpl
    public static float JVM_ConstantPoolGetFloatAt(@SuppressWarnings("unused") Object unused, @Host(Object.class) StaticObject jcpool, int index) {
        return jcpool.getMirrorKlass().getConstantPool().floatAt(index);
    }

    @VmImpl
    @JniImpl
    public static @Host(String.class) StaticObject JVM_ConstantPoolGetStringAt(@SuppressWarnings("unused") Object unused, @Host(Object.class) StaticObject jcpool, int index) {
        return ((RuntimeConstantPool) jcpool.getMirrorKlass().getConstantPool()).resolvedStringAt(index);
    }

    @VmImpl
    @JniImpl
    public @Host(String.class) StaticObject JVM_ConstantPoolGetUTF8At(@SuppressWarnings("unused") Object unused, StaticObject jcpool, int index) {
        return getMeta().toGuestString(jcpool.getMirrorKlass().getConstantPool().utf8At(index).toString());
    }

    @VmImpl
    @JniImpl
    public static int JVM_ConstantPoolGetIntAt(@SuppressWarnings("unused") Object unused, StaticObject jcpool, int index) {
        return jcpool.getMirrorKlass().getConstantPool().intAt(index);
    }

    @VmImpl
    @JniImpl
    public static long JVM_ConstantPoolGetLongAt(@SuppressWarnings("unused") Object unused, StaticObject jcpool, int index) {
        return jcpool.getMirrorKlass().getConstantPool().longAt(index);
    }

    @VmImpl
    @JniImpl
    public @Host(Class.class) StaticObject JVM_DefineClass(String name, @Host(ClassLoader.class) StaticObject loader, long bufPtr, int len,
                    @Host(ProtectionDomain.class) StaticObject pd) {
        ByteBuffer buf = JniEnv.directByteBuffer(bufPtr, len, JavaKind.Byte);
        final byte[] bytes = new byte[len];
        buf.get(bytes);

        // TODO(peterssen): Name is in binary form, but separator can be either / or . .
        Symbol<Type> type = getTypes().fromClassGetName(name);

        StaticObject clazz = getContext().getRegistries().defineKlass(type, bytes, loader).mirror();
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
            Field f = lib.getClass().getDeclaredField("handle");
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

        // TODO(peterssen): Use EspressoProperties to store classpath.
        EspressoError.guarantee(options.hasBeenSet(EspressoOptions.Classpath), "Classpath must be defined.");
        setProperty.invokeWithConversions(properties, "java.class.path", props.classpath().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));

        setProperty.invokeWithConversions(properties, "java.home", props.javaHome().toString());
        setProperty.invokeWithConversions(properties, "sun.boot.class.path", props.bootClasspath().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        setProperty.invokeWithConversions(properties, "java.library.path", props.javaLibraryPath().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        setProperty.invokeWithConversions(properties, "sun.boot.library.path", props.bootLibraryPath().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        setProperty.invokeWithConversions(properties, "java.ext.dirs", props.extDirs().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));

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

    @VmImpl
    @JniImpl
    public static @Host(Class.class) StaticObject JVM_GetCallerClass(int depth) {
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
        CallTarget caller = Truffle.getRuntime().iterateFrames(
                        new FrameInstanceVisitor<CallTarget>() {
                            @Override
                            public CallTarget visitFrame(FrameInstance frameInstance) {
                                if (frameInstance.getCallTarget() instanceof RootCallTarget) {
                                    RootCallTarget callTarget = (RootCallTarget) frameInstance.getCallTarget();
                                    RootNode rootNode = callTarget.getRootNode();
                                    if (rootNode instanceof EspressoRootNode) {
                                        if (--depthCounter[0] < 0) {
                                            return frameInstance.getCallTarget();
                                        }
                                    }
                                }
                                return null;
                            }
                        });

        // System.err.print("JVM_GetCallerClass: ");
        RootCallTarget callTarget = (RootCallTarget) caller;
        RootNode rootNode = callTarget.getRootNode();
        if (rootNode instanceof EspressoRootNode) {
            // System.err.println(((EspressoRootNode)
            // rootNode).getMethod().getDeclaringKlass().getName().toString());
            return ((EspressoRootNode) rootNode).getMethod().getDeclaringKlass().mirror();
        }

        throw EspressoError.shouldNotReachHere();
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
                                if (frameInstance.getCallTarget() instanceof RootCallTarget) {
                                    RootCallTarget callTarget = (RootCallTarget) frameInstance.getCallTarget();
                                    RootNode rootNode = callTarget.getRootNode();
                                    if (rootNode instanceof EspressoRootNode) {
                                        Method m = ((EspressoRootNode) rootNode).getMethod();
                                        if (!isIgnoredBySecurityStackWalk(m, getMeta()) && !m.isNative()) {
                                            result.add(m.getDeclaringKlass().mirror());
                                        }
                                    }
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

    @VmImpl
    @JniImpl
    public static @Host(Object.class) StaticObject JVM_LatestUserDefinedLoader() {
        StaticObject result = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<StaticObject>() {
            public StaticObject visitFrame(FrameInstance frameInstance) {
                if (frameInstance.getCallTarget() instanceof RootCallTarget) {
                    RootCallTarget callTarget = (RootCallTarget) frameInstance.getCallTarget();
                    RootNode rootNode = callTarget.getRootNode();
                    if (rootNode instanceof EspressoRootNode) {
                        StaticObject loader = ((EspressoRootNode) rootNode).getMethod().getDeclaringKlass().getDefiningClassLoader();
                        if (StaticObject.notNull(loader) && !Type.sun_misc_Launcher_ExtClassLoader.equals(loader.getKlass().getType())) {
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
        return klass.getModifiers();
    }

    @VmImpl
    @JniImpl
    public @Host(Class.class) StaticObject JVM_FindClassFromBootLoader(String name) {
        Klass klass = getRegistries().loadKlassWithBootClassLoader(getTypes().fromClassGetName(name));
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

    private static StaticObject getGuestReflectiveMethodRoot(@Host(java.lang.reflect.Field.class) StaticObject seed) {
        Meta meta = seed.getKlass().getMeta();
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
                    hostName = method.getConstantPool().utf8At(entry.getNameIndex(), "parameter name").toString();
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
    public static Object JVM_GetMethodTypeAnnotations(@Host(Object.class) StaticObject guestReflectionMethod) {
        StaticObject methodRoot = getGuestReflectiveMethodRoot(guestReflectionMethod);
        assert methodRoot != null;
        return methodRoot.getHiddenField(methodRoot.getKlass().getMeta().HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
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
        String[] packages = getRegistries().getBootClassRegistry().getPackagePaths();
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
}
