/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage.hosted;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

/**
 * This class provides methods that can be called during native-image building to configure class
 * initialization behavior. By default, all JDK classes that are seen as reachable for a native
 * image are initialized during image building, i.e. the class initialization method is executed
 * during image building and is not seen as a reachable method at runtime. Application classes, on
 * the other hand, are initialized during image building if they can be proven safe. Unsafe classes,
 * e.g. ones that create threads, will be initialized at image run time.
 *
 * For classes that can't be proven safe, it is sometimes beneficial to ensure initialization during
 * image building, and for some that are safe, it is still necessary to initialize at runtime (e.g.,
 * the order of initializer execution matters).
 * <p>
 * This class provides two different registration methods: Classes registered via
 * {@link #initializeAtRunTime} are not initialized at all during image generation, and only
 * initialized at runtime, i.e., the class initializer is executed once at runtime. Classes
 * registered via {@link #initializeAtBuildTime} will be initialized during image building.
 *
 * It is also possible define initialization for whole packages with
 * {@link #initializeAtRunTime(String[])} and {@link #initializeAtBuildTime(String[])}. The rules
 * for packages can be further refined by using methods for individual classes.
 *
 * Initializing classes at runtime comes with some costs and restrictions:
 * <ul>
 * <li>The class initialization status must be checked before a static field access, static method
 * call, and object allocation of such classes. This has an impact on performance.</li>
 * <li>Instances of such classes are not allowed on the image heap, i.e., on the initial heap that
 * is part of the native executable. Otherwise instances would exist before the class is
 * initialized, which violates the class initialization specification.</li>
 * <ul>
 *
 * @since 19.0
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class RuntimeClassInitialization {

    /**
     * Registers the provided classes, and all of their subclasses, for class initialization at
     * runtime. The classes are not initialized automatically during image generation, and also must
     * not be initialized manually by the user during image generation.
     * <p>
     * Unfortunately, classes are initialized for many reasons, and it is not possible to intercept
     * class initialization and report an error at this time. If a registered class gets
     * initialized, an error can be reported only later and the user must manually debug the reason
     * for class initialization. This can be done by, e.g., setting a breakpoint in the class
     * initializer or adding debug printing (print the stack trace) in the class initializer.
     *
     * @since 19.0
     */
    public static void initializeAtRunTime(Class<?>... classes) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        for (Class<?> aClass : classes) {
            ImageSingletons.lookup(RuntimeClassInitializationSupport.class).initializeAtRunTime(aClass, MESSAGE + getCaller(stacktrace));
        }
    }

    /**
     * Registers the provided classes as eagerly initialized during image-build time.
     * <p>
     * All static initializers of {@code classes} will be executed during image-build time and
     * static fields that are assigned values will be available at runtime. {@code static final}
     * fields will be considered as constant.
     * <p>
     * It is up to the user to ensure that this behavior makes sense and does not lead to wrong
     * application behavior.
     *
     * @since 19.0
     */
    public static void initializeAtBuildTime(Class<?>... classes) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        for (Class<?> aClass : classes) {
            ImageSingletons.lookup(RuntimeClassInitializationSupport.class).initializeAtBuildTime(aClass, MESSAGE + getCaller(stacktrace));
        }
    }

    /**
     * Registers all classes in provided packages, and all of their subclasses, for class
     * initialization at runtime. The classes are not initialized automatically during image
     * generation, and also must not be initialized manually by the user during image generation.
     * <p>
     * Unfortunately, classes are initialized for many reasons, and it is not possible to intercept
     * class initialization and report an error at this time. If a registered class gets
     * initialized, an error can be reported only later and the user must manually debug the reason
     * for class initialization. This can be done by, e.g., setting a breakpoint in the class
     * initializer or adding debug printing (print the stack trace) in the class initializer.
     *
     * @since 19.0
     */
    public static void initializeAtRunTime(String... packages) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        for (String aPackage : packages) {
            ImageSingletons.lookup(RuntimeClassInitializationSupport.class).initializeAtRunTime(aPackage, MESSAGE + getCaller(stacktrace));
        }
    }

    /**
     * Registers all classes in provided packages as eagerly initialized during image-build time.
     * <p>
     * All static initializers of {@code classes} will be executed during image-build time and
     * static fields that are assigned values will be available at runtime. {@code static final}
     * fields will be considered as constant.
     * <p>
     * It is up to the user to ensure that this behavior makes sense and does not lead to wrong
     * application behavior.
     *
     * @since 19.0
     */
    public static void initializeAtBuildTime(String... packages) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        for (String aPackage : packages) {
            ImageSingletons.lookup(RuntimeClassInitializationSupport.class).initializeAtBuildTime(aPackage, MESSAGE + getCaller(stacktrace));
        }
    }

    private static String getCaller(StackTraceElement[] stackTrace) {
        StackTraceElement e = stackTrace[2];
        return e.getClassName() + "." + e.getMethodName();
    }

    private static final String MESSAGE = "from feature ";

    private RuntimeClassInitialization() {
    }
}
