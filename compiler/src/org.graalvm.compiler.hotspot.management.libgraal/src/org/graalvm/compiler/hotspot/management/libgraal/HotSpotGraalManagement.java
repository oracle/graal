/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.management.libgraal;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Supplier;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.graalvm.compiler.hotspot.HotSpotGraalManagementRegistration;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.hotspot.management.HotSpotGraalRuntimeMBean;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.libgraal.jni.JNI;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

/**
 * Dynamically registers an MBean with the {@link ManagementFactory#getPlatformMBeanServer()}.
 *
 * Polling for an active platform MBean server is done by calling
 * {@link MBeanServerFactory#findMBeanServer(String)} with an argument value of {@code null}. Once
 * this returns an non-empty list, {@link ManagementFactory#getPlatformMBeanServer()} can be called
 * to obtain a reference to the platform MBean server instance.
 */
@ServiceProvider(HotSpotGraalManagementRegistration.class)
public final class HotSpotGraalManagement implements HotSpotGraalManagementRegistration {

    private static final byte[] HS_BEAN_CLASS = null;
    private static final String HS_BEAN_CLASS_NAME = null;
    private static final byte[] HS_BEAN_FACTORY_CLASS = null;
    private static final String HS_BEAN_FACTORY_CLASS_NAME = null;

    private HotSpotGraalRuntimeMBean bean;
    private volatile boolean needsRegistration = true;
    HotSpotGraalManagement nextDeferred;

    public HotSpotGraalManagement() {
    }

    @Override
    public void initialize(HotSpotGraalRuntime runtime) {
        if (bean == null) {
            if (runtime.getManagement() != this) {
                throw new IllegalArgumentException("Cannot initialize a second management object for runtime " + runtime.getName());
            }
        } else if (bean.getRuntime() != runtime) {
            throw new IllegalArgumentException("Cannot change the runtime a management interface is associated with");
        }
    }

    @Override
    public ObjectName poll(boolean sync) {
        if (sync) {
//            registration.poll();
        }
        if (bean == null || needsRegistration) {
            // initialize() has not been called, it failed or registration failed
            return null;
        }
        return bean.getObjectName();
    }

    static void defineRequiredClassesInHotSpot(JNI.JNIEnv env) {
        System.out.println("Defining JMX Bean.");
        defineClassInHotSpot(env, HS_BEAN_CLASS_NAME, HS_BEAN_CLASS);
        defineClassInHotSpot(env, HS_BEAN_FACTORY_CLASS_NAME, HS_BEAN_FACTORY_CLASS);
        System.out.println("Defined JMX Bean.");
    }

    private static void defineClassInHotSpot(JNI.JNIEnv env, String clazzName, byte[] clazz) {
        CCharPointer classData = UnmanagedMemory.malloc(clazz.length);
        ByteBuffer buffer = CTypeConversion.asByteBuffer(classData, clazz.length);
        buffer.put(clazz);
        try (CTypeConversion.CCharPointerHolder className = CTypeConversion.toCString(clazzName)) {
            env.getFunctions().getDefineClass().call(
                    env,
                    className.get(),
                    WordFactory.nullPointer(),
                    classData,
                    clazz.length);
        } finally {
            UnmanagedMemory.free(classData);
        }
    }


    static final class Factory implements Supplier<HotSpotGraalManagementRegistration> {

        private static final Queue<HotSpotGraalManagement> instances = new ArrayDeque<>();

        Factory() {
        }

        @Override
        public HotSpotGraalManagementRegistration get() {
            return enqueue(new HotSpotGraalManagement());
        }

        synchronized HotSpotGraalManagement poll() {
            return instances.poll();
        }

        private static synchronized HotSpotGraalManagement enqueue(HotSpotGraalManagement instance) {
            instances.add(instance);
            return instance;
        }
    }
}
