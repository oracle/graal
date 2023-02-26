/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.hosted.heap;

import static jdk.internal.org.objectweb.asm.Opcodes.ACC_FINAL;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SUPER;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACONST_NULL;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ARETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static jdk.internal.org.objectweb.asm.Opcodes.PUTFIELD;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.V11;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.deopt.DeoptTest;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.heap.Pod.Builder;
import com.oracle.svm.core.heap.Pod.RuntimeSupport.PodFactory;
import com.oracle.svm.core.heap.Pod.RuntimeSupport.PodInfo;
import com.oracle.svm.core.heap.Pod.RuntimeSupport.PodSpec;
import com.oracle.svm.core.hub.Hybrid;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.NativeImageSystemClassLoader;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Type;
import jdk.vm.ci.meta.JavaKind;

/** Support for preparing the creation of {@link Pod} objects during the image build. */
public interface PodSupport {
    static boolean isPresent() {
        return ImageSingletons.contains(PodSupport.class);
    }

    static PodSupport singleton() {
        if (!ImageSingletons.contains(PodSupport.class)) {
            throw UserError.abort("Pods are not available in this native image build.");
        }
        return ImageSingletons.lookup(PodSupport.class);
    }

    /**
     * Registers a superclass and factory interface so that they are available to build {@link Pod}s
     * at runtime via {@link Builder#createExtending(Class, Class)}. The provided superclass must be
     * non-abstract, non-final and accessible (usually public and in an exported package). The
     * factory interface consist solely of methods with a return type to which the superclass
     * {@linkplain Class#isAssignableFrom is assignable to} and their parameter list musts match
     * exactly the parameter list of one of the constructors of the superclass.
     * <p>
     * This method can be called multiple times for the same superclass and different factory
     * interfaces, and for the same factory interface and different superclasses.
     */
    void registerSuperclass(Class<?> superClass, Class<?> factoryInterface);

    boolean isPodClass(Class<?> clazz);

    boolean mustReserveLengthField(Class<?> clazz);
}

@AutomaticallyRegisteredFeature
@Platforms(InternalPlatform.NATIVE_ONLY.class)
final class PodFeature implements PodSupport, InternalFeature {
    private static final AtomicInteger GENERATED_COUNTER = new AtomicInteger();

    private final Map<PodSpec, PodInfo> pods = new ConcurrentHashMap<>();

    /**
     * These classes are ancestors of pod classes which directly inherit from {@link Object}, and so
     * they must reserve space for a length field so subclasses (or the class itself) don't place
     * other fields where the length field must be. If a pod subclasses {@link Object} itself, it is
     * itself included in this set.
     */
    private final Set<Class<?>> classesNeedingLengthFields = ConcurrentHashMap.newKeySet();

    private BeforeAnalysisAccess analysisAccess;
    private volatile boolean instantiated = false;
    private boolean sealed = false;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(PodSupport.class, this);
        ImageSingletons.add(Pod.RuntimeSupport.class, new Pod.RuntimeSupport());

        registerSuperclass(Object.class, Supplier.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ((DuringSetupAccessImpl) access).registerSubstitutionProcessor(new PodFactorySubstitutionProcessor());
    }

    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        this.analysisAccess = access;
        access.registerReachabilityHandler(this::registerAsInstantiated, (Object[]) Builder.class.getDeclaredConstructors());
    }

    private void registerAsInstantiated(DuringAnalysisAccess access) {
        instantiated = true;
        pods.forEach((spec, podInfo) -> registerPodAsInstantiated(access, spec, podInfo));
    }

    private static void registerPodAsInstantiated(BeforeAnalysisAccess a, PodSpec spec, PodInfo info) {
        ((BeforeAnalysisAccessImpl) a).registerAsInHeap(info.podClass, "Pod class registered by PodFeature.");
        Pod.RuntimeSupport.singleton().registerPod(spec, info);
    }

    @Override
    public void registerSuperclass(Class<?> superClass, Class<?> factoryInterface) {
        if (sealed) {
            throw UserError.abort("Pod superclasses can not be registered after analysis has finished");
        }
        if (superClass == null || factoryInterface == null) {
            throw new NullPointerException();
        }
        var spec = new PodSpec(superClass, factoryInterface);
        if (pods.containsKey(spec)) {
            return;
        }
        if (!factoryInterface.isInterface()) {
            throw new IllegalArgumentException("Factory is not an interface: " + factoryInterface);
        }
        for (Method method : factoryInterface.getMethods()) {
            if (!method.getReturnType().isAssignableFrom(superClass)) {
                throw new IllegalArgumentException("The return type of '" + method + "' is not assignable from '" + superClass.getName() + "'");
            }
            try {
                superClass.getDeclaredConstructor(method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Method '" + method + "' does not match any constructor in '" + superClass.getName() + "'", e);
            }
        }
        Class<?> podClass = generatePodClass(superClass);
        Constructor<?> factoryCtor = generatePodFactory(podClass, factoryInterface);

        var info = new PodInfo(podClass, factoryCtor);
        if (pods.putIfAbsent(spec, info) != null) {
            return; // lost a race with another thread
        }

        if (instantiated) {
            registerPodAsInstantiated(analysisAccess, spec, info);
        }
        Class<?> sup = podClass;
        while (sup.getSuperclass() != Object.class) {
            sup = sup.getSuperclass();
        }
        classesNeedingLengthFields.add(sup);
    }

    /**
     * For pods, we need a designated class that (1) is a {@link Hybrid} class and so must never be
     * allocated as an instance class because its {@link LayoutEncoding} describes it as an array
     * and (2) is not subclassed since fields in a subclass would overlap with the array part.
     */
    private static Class<?> generatePodClass(Class<?> superClass) {
        String className = Pod.class.getName() + "$$Generated" + GENERATED_COUNTER.incrementAndGet();

        ClassWriter writer = new ClassWriter(0);
        int access = ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC | ACC_FINAL;
        writer.visit(V11, access, className.replace('.', '/'), null, Type.getInternalName(superClass), null);
        var annotation = writer.visitAnnotation(Type.getDescriptor(Hybrid.class), true);
        annotation.visit("componentType", Type.getType(byte.class));
        annotation.visitEnd();
        writer.visitEnd();
        byte[] data = writer.toByteArray();

        Class<?> podClass = NativeImageSystemClassLoader.singleton().predefineClass(className, data, 0, data.length);
        assert podClass.getSuperclass() == superClass;
        return podClass;
    }

    /**
     * Generate a concrete subclass of the provided factory interface with dummy methods that are
     * subsequently substituted with {@link PodFactorySubstitutionMethod} to allocate pod instances
     * and invoke the matching superclass constructor on them.
     */
    private static Constructor<?> generatePodFactory(Class<?> podClass, Class<?> factoryInterface) {
        String name = Pod.class.getName() + "$$GeneratedFactory" + GENERATED_COUNTER.incrementAndGet();
        String internalName = name.replace('.', '/');
        String podDescriptor = Type.getDescriptor(Pod.class);

        ClassWriter writer = new ClassWriter(0);
        int access = ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC | ACC_FINAL;
        writer.visit(V11, access, internalName, null, Type.getInternalName(Object.class), new String[]{Type.getInternalName(factoryInterface)});
        var annotation = writer.visitAnnotation(Type.getDescriptor(PodFactory.class), true);
        annotation.visit("podClass", Type.getType(podClass));
        annotation.visitEnd();
        writer.visitField(ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC, "pod", podDescriptor, null, null).visitEnd();
        var init = writer.visitMethod(ACC_PUBLIC | ACC_SYNTHETIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(podDescriptor)), null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
        init.visitVarInsn(ALOAD, 0);
        init.visitVarInsn(ALOAD, 1);
        init.visitFieldInsn(PUTFIELD, internalName, "pod", podDescriptor);
        init.visitInsn(RETURN);
        init.visitMaxs(2, 2);
        init.visitEnd();
        for (Method method : factoryInterface.getMethods()) {
            int methodAccess = ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC;
            var impl = writer.visitMethod(methodAccess, method.getName(), Type.getMethodDescriptor(method), null, null);
            if (method.isAnnotationPresent(DeoptTest.class)) {
                impl.visitAnnotation(Type.getDescriptor(DeoptTest.class), true).visitEnd();
            }
            impl.visitCode(); // substituted with custom graph
            impl.visitInsn(ACONST_NULL);
            impl.visitInsn(ARETURN);
            int localSlots = 1; // spare slot needed by generated graph
            for (Class<?> clazz : method.getParameterTypes()) {
                localSlots += JavaKind.fromJavaClass(clazz).getSlotCount();
            }
            impl.visitMaxs(1, localSlots);
            impl.visitEnd();
        }
        writer.visitEnd();
        byte[] data = writer.toByteArray();

        Class<?> factoryClass = NativeImageSystemClassLoader.singleton().predefineClass(name, data, 0, data.length);
        assert factoryInterface.isAssignableFrom(factoryClass);
        return ReflectionUtil.lookupConstructor(factoryClass, Pod.class);
    }

    @Override
    public boolean isPodClass(Class<?> clazz) {
        if ((clazz.getModifiers() & ACC_SYNTHETIC) != 0 && clazz.isAnnotationPresent(Hybrid.class)) {
            for (PodInfo info : pods.values()) {
                if (info.podClass == clazz) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mustReserveLengthField(Class<?> clazz) {
        return classesNeedingLengthFields.contains(clazz);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        analysisAccess = null;
        sealed = true;
    }
}
