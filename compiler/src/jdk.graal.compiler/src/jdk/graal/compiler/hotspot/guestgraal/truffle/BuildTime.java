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
package jdk.graal.compiler.hotspot.guestgraal.truffle;

import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id;
import jdk.graal.compiler.debug.DebugHandlersFactory;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.graal.compiler.truffle.hotspot.TruffleCallBoundaryInstrumentationFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Class used to initialize Truffle and Graal compiler in the image build time.
 */
public class BuildTime {

    private static final String JNI_CALLS_CLASS = "com.oracle.svm.graal.hotspot.guestgraal.TruffleFromLibGraalStartPoint";
    private static final String NATIVE_IMAGE_METHODS_CLASS = "com.oracle.svm.graal.hotspot.guestgraal.NativeImageHostEntryPoint";

    private static Lookup hostLookup;
    private static Map<String, MethodHandle> hostMethods;

    /**
     * Configures Truffle and graal compiler for libgraal.
     *
     * @param initializeInBuildTime callback used to initialize classes in build-time.
     *
     */
    public static void configureGraalForLibGraal(Consumer<Class<?>> initializeInBuildTime) {
        initializeInBuildTime.accept(BuildTime.class);
        initializeInBuildTime.accept(NativeImageHostCalls.class);
        initializeInBuildTime.accept(TruffleLibGraalShutdownHook.ShutdownHook.class);
        initializeInBuildTime.accept(HSConsumer.class);
        initializeInBuildTime.accept(HSTruffleCompilable.class);
        initializeInBuildTime.accept(HSTruffleCompilationTask.class);
        initializeInBuildTime.accept(HSTruffleCompilerListener.class);
        initializeInBuildTime.accept(HSTruffleCompilerRuntime.class);
        initializeInBuildTime.accept(HSTruffleSourceLanguagePosition.class);
        initializeInBuildTime.accept(HSTruffleCompilerListener.class);

        GraalServices.load(TruffleCallBoundaryInstrumentationFactory.class);
        GraalServices.load(TruffleHostEnvironment.Lookup.class);
        GraalServices.load(DebugHandlersFactory.class);
        TruffleHostEnvironment.overrideLookup(new LibGraalTruffleHostEnvironmentLookup());
    }

    /**
     * Gets {@link Lookup} for method handles to call Graal and JVMCI methods.
     *
     * @param lookup a {@link Lookup} to resolve hadles to call into native-image host
     * @return a {@link Lookup} to resolve compiler entry methods
     */
    public static Lookup getLookup(Lookup lookup) {
        hostLookup = lookup;
        return MethodHandles.lookup();
    }

    static MethodHandle getHostMethodHandleOrFail(Id id) {
        return getHostMethodHandleOrFail(id.getMethodName());
    }

    static MethodHandle getHostMethodHandleOrFail(String name) {
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
    }

    private static Map<String, MethodHandle> initializeHostMethods() {
        try {
            Map<String, MethodHandle> result = new HashMap<>();
            Class<?> hostClass = hostLookup.findClass(JNI_CALLS_CLASS);
            Set<String> methodNames = new HashSet<>();
            Arrays.stream(Id.values()).map(Id::getMethodName).forEach(methodNames::add);
            for (Method m : hostClass.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers())) {
                    String methodName = m.getName();
                    if (methodNames.remove(methodName)) {
                        result.put(methodName, hostLookup.unreflect(m));
                    }
                }
            }
            if (!methodNames.isEmpty()) {
                throw new RuntimeException(String.format("Cannot find methods for following ids %s in %s", methodNames, JNI_CALLS_CLASS));
            }
            Arrays.stream(NativeImageHostCalls.class.getDeclaredMethods()).map(Method::getName).forEach(methodNames::add);
            hostClass = hostLookup.findClass(NATIVE_IMAGE_METHODS_CLASS);
            for (Method m : hostClass.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers())) {
                    String methodName = m.getName();
                    if (methodNames.remove(methodName)) {
                        if (result.put(methodName, hostLookup.unreflect(m)) != null) {
                            throw new RuntimeException(String.format("Duplicate methods for name %s in %s", methodName, NATIVE_IMAGE_METHODS_CLASS));
                        }
                    }
                }
            }
            if (!methodNames.isEmpty()) {
                throw new RuntimeException(String.format("Cannot find following methods %s in %s", methodNames, NATIVE_IMAGE_METHODS_CLASS));
            }
            return result;
        } catch (ClassNotFoundException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
