/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.libgraal.truffle;

import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import org.graalvm.nativeimage.ImageInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Class used to initialize the Truffle extensions to the Graal compiler in the image build time.
 */
public class BuildTime {

    private static Lookup hostLookup;
    private static Class<?> truffleFromLibGraalStartPoint;
    private static Class<?> nativeImageHostEntryPoint;
    private static Map<String, MethodHandle> hostMethods;

    /**
     * Configures Truffle services to the Graal compiler in the image build time.
     */
    public static void configureGraalForLibGraal() {
        TruffleHostEnvironment.overrideLookup(new LibGraalTruffleHostEnvironmentLookup());
    }

    /**
     * Obtains a {@link Lookup} instance for resolving method handles to invoke Graal and JVMCI
     * methods.
     * <p>
     * This method is invoked reflectively by {@code LibGraalFeature.initializeTruffle()} in the
     * native-image classloader to facilitate the exchange of lookup instances between the
     * native-image classloader and the LibGraalClassLoader.
     * </p>
     *
     * @param lookup a {@link Lookup} instance used to resolve handles for calling into the
     *            native-image host.
     * @param fromLibGraal a class that contains methods for making calls to HotSpot using the JNI
     *            API.
     * @param nativeImageSupport a class that provides native-image and JNI helper methods.
     * @return a {@link Entry} containing a {@link Lookup} instance and a class with compiler entry
     *         methods. The {@link Lookup} instance can be used to resolve the compiler entry
     *         methods within the provided class.
     */
    public static Entry<Lookup, Class<?>> initializeLookup(Lookup lookup, Class<?> fromLibGraal, Class<?> nativeImageSupport) {
        if (hostLookup != null) {
            throw new IllegalStateException("Host lookup has already been initialized. BuildTime.initializeLookup should only be called once during the native image build process.");
        }
        hostLookup = Objects.requireNonNull(lookup, "lookup must be non null");
        truffleFromLibGraalStartPoint = Objects.requireNonNull(fromLibGraal, "fromLibGraal must be non null");
        nativeImageHostEntryPoint = Objects.requireNonNull(nativeImageSupport, "nativeImageSupport must be non null");
        return Map.entry(MethodHandles.lookup(), GraalEntryPoints.class);
    }

    static MethodHandle getHostMethodHandleOrFail(Id id) {
        return getHostMethodHandleOrFail(id.getMethodName());
    }

    static MethodHandle getHostMethodHandleOrFail(String name) {
        if (ImageInfo.inImageBuildtimeCode()) {
            /*
             * Native-image initializes BuildTime also in the platform classloader. In this case we
             * return null.
             */
            ClassLoader myLoader = BuildTime.class.getClassLoader();
            if (myLoader == null || myLoader == ClassLoader.getPlatformClassLoader() || myLoader == ClassLoader.getSystemClassLoader()) {
                return null;
            }
            if (hostMethods == null) {
                hostMethods = initializeHostMethods();
            }
            MethodHandle handle = hostMethods.get(name);
            if (handle != null) {
                return handle;
            } else {
                throw new NoSuchElementException(name);
            }
        } else if (ImageInfo.inImageRuntimeCode()) {
            /*
             * The getHostMethodHandleOrFail should never be called in the native-image execution
             * time.
             */
            throw new IllegalStateException("Should not be reachable at libgraal runtime");
        } else {
            /*
             * HS proxy classes and BuildTime are not used in Jargraal, but the CheckGraalInvariants
             * test eagerly initializes these proxy classes, leading to a call to
             * getHostMethodHandleOrFail. In this scenario, we return null to prevent the test from
             * crashing.
             */
            return null;
        }
    }

    private static Map<String, MethodHandle> initializeHostMethods() {
        try {
            Map<String, MethodHandle> result = new HashMap<>();
            Set<String> methodNames = new HashSet<>();
            Arrays.stream(Id.values()).map(Id::getMethodName).forEach(methodNames::add);
            for (Method m : truffleFromLibGraalStartPoint.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers())) {
                    String methodName = m.getName();
                    if (methodNames.remove(methodName)) {
                        result.put(methodName, hostLookup.unreflect(m));
                    }
                }
            }
            if (!methodNames.isEmpty()) {
                throw new RuntimeException(String.format("Cannot find methods for following ids %s in %s", methodNames, truffleFromLibGraalStartPoint.getName()));
            }
            Arrays.stream(NativeImageHostCalls.class.getDeclaredMethods()).map(Method::getName).forEach(methodNames::add);
            for (Method m : nativeImageHostEntryPoint.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers())) {
                    String methodName = m.getName();
                    if (methodNames.remove(methodName)) {
                        if (result.put(methodName, hostLookup.unreflect(m)) != null) {
                            throw new RuntimeException(String.format("Duplicate methods for name %s in %s", methodName, nativeImageHostEntryPoint));
                        }
                    }
                }
            }
            if (!methodNames.isEmpty()) {
                throw new RuntimeException(String.format("Cannot find following methods %s in %s", methodNames, nativeImageHostEntryPoint));
            }
            return result;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
