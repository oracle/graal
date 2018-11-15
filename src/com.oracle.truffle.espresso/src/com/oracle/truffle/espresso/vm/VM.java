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
import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.Utils;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.intrinsics.SuppressFBWarnings;
import com.oracle.truffle.espresso.intrinsics.Type;
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
import com.oracle.truffle.espresso.nodes.LinkedNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.runtime.StaticObjectWrapper;
import com.oracle.truffle.espresso.types.TypeDescriptor;
import com.oracle.truffle.nfi.types.NativeSimpleType;

public class VM extends NativeEnv {

    private final TruffleObject initializeMokapotContext;
    private final TruffleObject disposeMokapotContext;
    private final TruffleObject getJavaVM;

    private final JniEnv jniEnv;

    private long vmPtr;

    // mokapot.dll (Windows) or libmokapot.so (Unixes) is the Espresso implementation of the VM
    // interface (libjvm)
    // Espresso loads all shared libraries in a private namespace (e.g. using dlmopen on Linux).
    // mokapot must be loaded strictly before any other library in the private namespace to
    // linking with HotSpot libjvm (or just linking errors), then libjava is loaded and further
    // system libraries, libzip ...
    private final TruffleObject mokapotLibrary = NativeLibrary.loadLibrary(System.getProperty("mokapot.library", "mokapot"));

    // libjava must be loaded after mokapot.
    private final TruffleObject javaLibrary = NativeLibrary.loadLibrary(System.getProperty("java.library", "java"));

    public TruffleObject getJavaLibrary() {
        return javaLibrary;
    }

    public TruffleObject getMokapotLibrary() {
        return mokapotLibrary;
    }

    private VM(JniEnv jniEnv) {
        this.jniEnv = jniEnv;
        try {
            initializeMokapotContext = NativeLibrary.lookupAndBind(mokapotLibrary,
                            "initializeMokapotContext", "(env, sint64, (string): pointer): sint64");

            disposeMokapotContext = NativeLibrary.lookupAndBind(mokapotLibrary,
                            "disposeMokapotContext",
                            "(env, sint64): void");

            getJavaVM = NativeLibrary.lookupAndBind(mokapotLibrary,
                            "getJavaVM",
                            "(env): sint64");

            Callback lookupVmImplCallback = Callback.wrapInstanceMethod(this, "lookupVmImpl", String.class);
            this.vmPtr = (long) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), initializeMokapotContext, jniEnv.getNativePointer(), lookupVmImplCallback);

            assert this.vmPtr != 0;

        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException | UnknownIdentifierException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public long getJavaVM() {
        try {
            return (long) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), getJavaVM);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("getJavaVM failed");
        }
    }

    private static Map<String, Method> buildVmMethods() {
        Map<String, Method> map = new HashMap<>();
        Method[] declaredMethods = VM.class.getDeclaredMethods();
        for (Method method : declaredMethods) {
            VmImpl jniImpl = method.getAnnotation(VmImpl.class);
            if (jniImpl != null) {
                assert !map.containsKey(method.getName()) : "VmImpl for " + method + " already exists";
                map.put(method.getName(), method);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static final Map<String, Method> vmMethods = buildVmMethods();

    public static VM create(JniEnv jniEnv) {
        return new VM(jniEnv);
    }

    public static String vmNativeSignature(Method method) {
        StringBuilder sb = new StringBuilder("(");

        boolean first = true;
        if (method.getAnnotation(JniImpl.class) != null) {
            sb.append(NativeSimpleType.POINTER); // Prepend JNIEnv*;
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

    public TruffleObject lookupVmImpl(String methodName) {
        Method m = vmMethods.get(methodName);
        try {
            // Dummy placeholder for unimplemented/unknown methods.
            if (m == null) {
                System.err.println("Fetching unknown/unimplemented VM method: " + methodName);
                return (TruffleObject) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), jniEnv.dupClosureRefAndCast("(pointer): void"),
                                new Callback(1, args -> {
                                    System.err.println("Calling unimplemented VM method: " + methodName);
                                    throw EspressoError.unimplemented("VM method: " + methodName);
                                }));
            }

            String signature = vmNativeSignature(m);
            Callback target = vmMethodWrapper(m);
            return (TruffleObject) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), jniEnv.dupClosureRefAndCast(signature), target);

        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    public Callback vmMethodWrapper(Method m) {
        int extraArg = (m.getAnnotation(JniImpl.class) != null) ? 1 : 0;

        return new Callback(m.getParameterCount() + extraArg, args -> {

            assert unwrapPointer(args[0]) == jniEnv.getNativePointer() : "Calling JVM_ method " + m + " from alien JniEnv";
            if (m.getAnnotation(JniImpl.class) != null) {
                args = Arrays.copyOfRange(args, 1, args.length); // Strip JNIEnv* pointer, replace
                                                                 // by VM (this) receiver.
            }

            Class<?>[] params = m.getParameterTypes();

            for (int i = 0; i < args.length; ++i) {
                // FIXME(peterssen): Espresso should accept interop null objects, since it doesn't
                // we must convert to Espresso null.
                // FIXME(peterssen): Also, do use proper nodes.
                if (args[i] instanceof TruffleObject) {
                    if (ForeignAccess.sendIsNull(Message.IS_NULL.createNode(), (TruffleObject) args[i])) {
                        args[i] = StaticObject.NULL;
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
                Object ret = m.invoke(this, args);

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
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // region VM methods

    @VmImpl
    @JniImpl
    public long JVM_CurrentTimeMillis(StaticObject ignored) {
        return System.currentTimeMillis();
    }

    @VmImpl
    @JniImpl
    public long JVM_NanoTime(StaticObject ignored) {
        return System.nanoTime();
    }

    /**
     * (Identity) hash code must be respected for wrappers. The same object could be wrapped by two
     * different instances of StaticObjectWrapper. Wrappers are transparent, it's identity comes
     * from the wrapped object.
     */
    @VmImpl
    @JniImpl
    public int JVM_IHashCode(Object object) {
        return System.identityHashCode(MetaUtil.unwrap(object));
    }

    @VmImpl
    @JniImpl
    public void JVM_ArrayCopy(Object ignored, Object src, int srcPos, Object dest, int destPos, int length) {
        try {
            if (src instanceof StaticObjectArray && dest instanceof StaticObjectArray) {
                System.arraycopy(((StaticObjectArray) src).getWrapped(), srcPos, ((StaticObjectArray) dest).getWrapped(), destPos, length);
            } else {
                assert src.getClass().isArray();
                assert dest.getClass().isArray();
                System.arraycopy(src, srcPos, dest, destPos, length);
            }
        } catch (Exception e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @VmImpl
    @JniImpl
    public Object JVM_Clone(Object self) {
        if (self instanceof StaticObjectArray) {
            // For arrays.
            return ((StaticObjectArray) self).copy();
        }

        if (self instanceof int[]) {
            return ((int[]) self).clone();
        } else if (self instanceof byte[]) {
            return ((byte[]) self).clone();
        } else if (self instanceof boolean[]) {
            return ((boolean[]) self).clone();
        } else if (self instanceof long[]) {
            return ((long[]) self).clone();
        } else if (self instanceof float[]) {
            return ((float[]) self).clone();
        } else if (self instanceof double[]) {
            return ((double[]) self).clone();
        } else if (self instanceof char[]) {
            return ((char[]) self).clone();
        } else if (self instanceof short[]) {
            return ((short[]) self).clone();
        }

        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        if (!meta.knownKlass(Cloneable.class).isAssignableFrom(meta(((StaticObject) self).getKlass()))) {
            throw meta.throwEx(java.lang.CloneNotSupportedException.class);
        }

        // Normal object just copy the fields.
        return ((StaticObjectImpl) self).copy();
    }

    @VmImpl
    @JniImpl
    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, .notify is just forwarded from the guest.")
    public void JVM_MonitorNotifyAll(Object self) {
        try {
            MetaUtil.unwrap(self).notifyAll();
        } catch (IllegalMonitorStateException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @VmImpl
    @JniImpl
    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, .notify is just forwarded from the guest.")
    public void JVM_MonitorNotify(Object self) {
        try {
            MetaUtil.unwrap(self).notify();
        } catch (IllegalMonitorStateException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @VmImpl
    @JniImpl
    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, .notify is just forwarded from the guest.")
    public void JVM_MonitorWait(Object self, long timeout) {
        try {
            MetaUtil.unwrap(self).wait(timeout);
        } catch (InterruptedException | IllegalMonitorStateException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @VmImpl
    public void JVM_Halt(int code) {
        // TODO(peterssen): Kill the context, not the whole VM; maybe not even the context.
        Runtime.getRuntime().halt(code);
    }

    @VmImpl
    public void JVM_Exit(int code) {
        // TODO(peterssen): Kill the context, not the whole VM; maybe not even the context.
        System.exit(code);
    }

    @VmImpl
    public boolean JVM_IsNaN(double d) {
        return Double.isNaN(d);
    }

    @VmImpl
    public boolean JVM_SupportsCX8() {
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
    public @Type(String.class) StaticObject JVM_InternString(@Type(String.class) StaticObject self) {
        return Utils.getVm().intern(self);
    }

    // endregion VM methods

    // region JNI Invocation Interface

    @VmImpl
    public int DestroyJavaVM() {
        return JniEnv.JNI_OK;
    }

    @VmImpl
    public int AttachCurrentThread(long penvPtr, long argsPtr) {
        return JniEnv.JNI_OK;
    }

    @VmImpl
    public int DetachCurrentThread() {
        return JniEnv.JNI_OK;
    }

    @VmImpl
    public int GetEnv(long penvPtr, int version) {
        return JniEnv.JNI_OK;
    }

    @VmImpl
    public int AttachCurrentThreadAsDaemon(long penvPtr, long argsPtr) {
        return JniEnv.JNI_OK;
    }

    @VmImpl
    @JniImpl
    public @Type(Throwable.class) StaticObject JVM_FillInStackTrace(@Type(Throwable.class) StaticObject self, int dummy) {
        final ArrayList<FrameInstance> frames = new ArrayList<>(16);
        Truffle.getRuntime().iterateFrames(frameInstance -> {
            frames.add(frameInstance);
            return null;
        });
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        meta.THROWABLE.declaredField("backtrace").set(self, new StaticObjectWrapper<>(meta.OBJECT.rawKlass(), frames.toArray(new FrameInstance[0])));
        return self;
    }

    @VmImpl
    @JniImpl
    public int JVM_GetStackTraceDepth(@Type(Throwable.class) StaticObject self) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        Object backtrace = meta.THROWABLE.declaredField("backtrace").get(self);
        if (backtrace == StaticObject.NULL) {
            return 0;
        }
        return ((FrameInstance[]) ((StaticObjectWrapper<?>) backtrace).getWrapped()).length;
    }

    @VmImpl
    @JniImpl
    public @Type(StackTraceElement.class) StaticObject JVM_GetStackTraceElement(@Type(Throwable.class) StaticObject self, int index) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        StaticObject ste = meta.knownKlass(StackTraceElement.class).allocateInstance();
        Object backtrace = meta.THROWABLE.declaredField("backtrace").get(self);
        FrameInstance[] frames = (FrameInstance[]) ((StaticObjectWrapper<?>) backtrace).getWrapped();

        FrameInstance frame = frames[index];

        RootNode rootNode = ((RootCallTarget) frame.getCallTarget()).getRootNode();
        Meta.Method.WithInstance init = meta(ste).method("<init>", void.class, String.class, String.class, String.class, int.class);
        if (rootNode instanceof LinkedNode) {
            LinkedNode linkedNode = (LinkedNode) rootNode;
            String className = linkedNode.getOriginalMethod().getDeclaringClass().getName();
            init.invoke(className, linkedNode.getOriginalMethod().getName(), null, -1);
        } else {
            // TODO(peterssen): Get access to the original (intrinsified) method and report
            // properly.
            init.invoke("UnknownIntrinsic", "unknownIntrinsic", null, -1);
        }
        return ste;
    }

    @VmImpl
    @JniImpl
    public int JVM_ConstantPoolGetSize(Object unused, StaticObjectClass jcpool) {
        return jcpool.getMirror().getConstantPool().length();
    }

    @VmImpl
    @JniImpl
    public @Type(String.class) StaticObject JVM_ConstantPoolGetUTF8At(Object unused, StaticObjectClass jcpool, int index) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return meta.toGuest(jcpool.getMirror().getConstantPool().utf8At(index).getValue());
    }

    @VmImpl
    @JniImpl
    public @Type(Class.class) StaticObject JVM_DefineClass(String name, Object loader, long bufPtr, int len, @Type(ProtectionDomain.class) Object pd) {
        ByteBuffer buf = JniEnv.directByteBuffer(bufPtr, len, JavaKind.Byte);
        final byte[] bytes = new byte[len];
        buf.get(bytes);
        StaticObjectClass klass = (StaticObjectClass) EspressoLanguage.getCurrentContext().getRegistries().defineKlass(name, bytes, loader).mirror();
        return klass;
    }

    @VmImpl
    @JniImpl
    public @Type(Class.class) StaticObject JVM_DefineClassWithSource(String name, Object loader, long bufPtr, int len,
                    @Type(ProtectionDomain.class) Object pd, String source) {
        // FIXME(peterssen): source is ignored.
        return JVM_DefineClass(name, loader, bufPtr, len, pd);
    }

    @VmImpl
    @JniImpl
    public Object JVM_NewInstanceFromConstructor(@Type(Constructor.class) StaticObject constructor, @Type(Object[].class) StaticObject args0) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        Meta.Klass klass = meta(((StaticObjectClass) meta(constructor).declaredField("clazz").get()).getMirror());
        klass.rawKlass().initialize();
        if (klass.isArray() || klass.isPrimitive() || klass.isInterface() || klass.isAbstract()) {
            throw klass.getMeta().throwEx(InstantiationException.class);
        }
        StaticObject instance = klass.allocateInstance();

        if (args0 == StaticObject.NULL) {
            args0 = (StaticObject) meta.OBJECT.allocateArray(0);
        }

        // Find constructor root.
        MethodInfo target = null;
        while (target == null) {
            target = (MethodInfo) ((StaticObjectImpl) constructor).getHiddenField("$$method_info");
            if (target == null) {
                constructor = (StaticObject) meta(constructor).declaredField("root").get();
            }
        }

        meta(target).invokeDirect(instance, ((StaticObjectArray) args0).getWrapped());
        return instance;
    }

    @VmImpl
    @JniImpl
    public @Type(Class.class) StaticObject JVM_FindLoadedClass(Object loader, @Type(String.class) StaticObject name) {
        EspressoContext context = EspressoLanguage.getCurrentContext();
        TypeDescriptor type = context.getTypeDescriptors().make(MetaUtil.toInternalName(Meta.toHost(name)));
        Klass klass = EspressoLanguage.getCurrentContext().getRegistries().findLoadedClass(type, loader);
        if (klass == null) {
            return StaticObject.NULL;
        }
        return klass.mirror();
    }

    private final ConcurrentHashMap<Long, TruffleObject> handle2Lib = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, TruffleObject> handle2Sym = new ConcurrentHashMap<>();

    @VmImpl
    public void JVM_UnloadLibrary(long handle) {
        // TODO(peterssen): Do unload the library.
        System.err.println("JVM_UnloadLibrary called but library was not unloaded!");
    }

    @VmImpl
    public long JVM_FindLibraryEntry(long libHandle, String name) {
        TruffleObject function = NativeLibrary.lookup(handle2Lib.get(libHandle), name);
        try {
            long handle = (long) ForeignAccess.sendUnbox(Message.UNBOX.createNode(), function);
            if (!handle2Sym.contains(handle)) {
                handle2Sym.put(handle, function);
            }
            return handle;
        } catch (UnsupportedMessageException e) {
        }
        return 0;
    }

    @VmImpl
    public boolean JVM_IsSupportedJNIVersion(int version) {
        return version == JNI_VERSION_1_1 ||
                        version == JNI_VERSION_1_2 ||
                        version == JNI_VERSION_1_4 ||
                        version == JNI_VERSION_1_6 ||
                        version == JNI_VERSION_1_8;
    }

    @VmImpl
    public long JVM_LoadLibrary(String name) {
        TruffleObject lib = NativeLibrary.loadLibrary(name);
        try {
            Field f = lib.getClass().getDeclaredField("handle");
            f.setAccessible(true);
            long handle = (long) f.get(lib);
            if (!handle2Lib.contains(handle)) {
                handle2Lib.put(handle, lib);
            }
            return handle;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @VmImpl
    public int JVM_GetInterfaceVersion() {
        return JniEnv.JVM_INTERFACE_VERSION;
    }

    // endregion JNI Invocation Interface

    public void dispose() {
        assert vmPtr != 0L : "Mokapot already disposed";
        try {
            ForeignAccess.sendExecute(Message.EXECUTE.createNode(), disposeMokapotContext, vmPtr);
            this.vmPtr = 0L;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere("Cannot dispose Espresso libjvm (mokapot).");
        }
        assert vmPtr == 0L;
    }

    public TruffleObject getLibrary(long handle) {
        return handle2Lib.get(handle);
    }

    public TruffleObject getFunction(long handle) {
        return handle2Sym.get(handle);
    }
}
