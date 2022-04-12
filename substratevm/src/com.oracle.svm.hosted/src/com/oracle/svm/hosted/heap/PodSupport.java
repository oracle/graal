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
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SUPER;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static jdk.internal.org.objectweb.asm.Opcodes.V11;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Hybrid;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageSystemClassLoader;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Type;

/** Support for preparing the creation of {@link Pod} objects during the image build. */
public interface PodSupport {
    static PodSupport singleton() {
        return ImageSingletons.lookup(PodSupport.class);
    }

    /**
     * Registers a class so it will be available as a superclass of {@link Pod}s at runtime via
     * {@link com.oracle.svm.core.heap.Pod.Builder#createExtending(Class)}.
     */
    void registerSuperclass(Class<?> clazz);

    boolean isPodClass(Class<?> clazz);

    boolean isPodSuperclass(Class<?> clazz);
}

@AutomaticFeature
final class PodFeature implements PodSupport, Feature {
    private static final AtomicInteger GENERATED_COUNTER = new AtomicInteger();

    private final Set<Class<?>> generated = ConcurrentHashMap.newKeySet();

    /** These classes have to reserve the space where the length field in the pod class will be. */
    private final Set<Class<?>> superClasses = ConcurrentHashMap.newKeySet();

    private BeforeAnalysisAccess analysisAccess;
    private volatile boolean instantiated = false;
    private boolean sealed = false;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(PodSupport.class, this);
        ImageSingletons.add(Pod.RuntimeSupport.class, new Pod.RuntimeSupport());

        registerSuperclass(Object.class);
    }

    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        this.analysisAccess = access;
        access.registerReachabilityHandler(this::registerAsInstantiated, ReflectionUtil.lookupMethod(Pod.class, "newInstance"));
    }

    private void registerAsInstantiated(DuringAnalysisAccess access) {
        instantiated = true;
        generated.forEach(clazz -> registerClassAsInstantiated(access, clazz));
    }

    private static void registerClassAsInstantiated(BeforeAnalysisAccess access, Class<?> podClass) {
        access.registerAsInHeap(podClass);
        runtimeSupport().registerClass(podClass.getSuperclass(), podClass);
    }

    private static Pod.RuntimeSupport runtimeSupport() {
        return ImageSingletons.lookup(Pod.RuntimeSupport.class);
    }

    @Override
    public void registerSuperclass(Class<?> superClass) {
        if (sealed) {
            throw UserError.abort("Pod superclasses can not be registered after analysis has finished");
        }
        if (runtimeSupport().get(superClass) != null) {
            return;
        }
        Class<?> podClass = generatePod(superClass);
        if (instantiated) {
            registerClassAsInstantiated(analysisAccess, podClass);
        }
        Class<?> sup = superClass;
        while (sup != Object.class) {
            superClasses.add(sup);
            sup = sup.getSuperclass();
        }
    }

    /**
     * For {@link Pod}s, we need a designated class that (1) is a hybrid class and so must not be
     * allocated as an instance class anywhere because its {@link LayoutEncoding} describes it as an
     * array and (2) is not subclassed since fields in a subclass would overlap with the array part.
     */
    private Class<?> generatePod(Class<?> superClass) {
        String className = Pod.class.getName() + "$$Generated" + GENERATED_COUNTER.incrementAndGet();

        ClassWriter writer = new ClassWriter(0);
        int access = ACC_PUBLIC | ACC_SUPER | ACC_SYNTHETIC | ACC_FINAL;
        writer.visit(V11, access, className.replace('.', '/'), null, Type.getInternalName(superClass), null);
        writer.visitAnnotation(Type.getDescriptor(Hybrid.class), true)
                        .visit("arrayType", Type.getType(byte[].class));
        writer.visitEnd();
        byte[] data = writer.toByteArray();

        Class<?> podClass = NativeImageSystemClassLoader.singleton().predefineClass(className, data, 0, data.length);
        assert podClass.getSuperclass() == superClass;

        runtimeSupport().registerClass(superClass, podClass);
        generated.add(podClass);
        return podClass;
    }

    @Override
    public boolean isPodClass(Class<?> clazz) {
        return generated.contains(clazz);
    }

    @Override
    public boolean isPodSuperclass(Class<?> clazz) {
        return superClasses.contains(clazz);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        sealed = true;
    }
}
