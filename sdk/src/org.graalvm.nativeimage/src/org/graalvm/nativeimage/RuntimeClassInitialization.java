/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

/**
 * This class provides methods that can be called during native image building to configure class
 * initialization behavior. By default, all classes that are seen as reachable for a native image
 * are initialized during image building, i.e., the class initialization method is executed during
 * image building and is not seen as a reachable method at runtime. But for some classes, it is
 * necessary to execute the class initialization method at runtime, e.g., when the class
 * initialization method loads shared libraries ({@code System.loadLibrary}), allocates native
 * memory ({@code ByteBuffer.allocateDirect}), or starts threads.
 * <p>
 * This class provides two different registration methods: Classes registered via
 * {@link #delayClassInitialization} are not initialized at all during image generation, and only
 * initialized at runtime, i.e., the class initializer is executed once at runtime. Classes
 * registered via {@link RuntimeClassInitialization#rerunClassInitialization} are initialized during
 * image generation, and again initialized at runtime, i.e., the class initializer is executed
 * twice.
 * <p>
 * Registering a class automatically registers all subclasses too. It would violate the class
 * initialization specification to have an uninitialized class that has an initialized subclass.
 * <p>
 * Initializing classes at runtime comes with some costs and restrictions:
 * <ul>
 * <li>The class initialization status must be checked before a static field access, static method
 * call, and object allocation of such classes. This has an impact on performance.</li>
 * <li>Instances of such classes are not allowed on the image heap, i.e., on the initial heap that
 * is part of the native executable. Otherwise instances would exist before the class is
 * initialized, which violates the class initialization specification.</li>
 * <ul>
 *
 * @since 1.0
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
     * @since 1.0
     */
    public static void delayClassInitialization(Class<?>... classes) {
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).delayClassInitialization(classes);
    }

    /**
     * Registers the provided classes, and all of their subclasses, for class re-initialization at
     * runtime. The classes are still initialized during image generation, i.e., the class
     * initializers run twice.
     * <p>
     * Static fields of the registered classes start out with their default values at runtime, i.e.,
     * values assigned by class initializers (or for any other reason) to static fields are not
     * available at runtime.
     * <p>
     * It is up to the user to ensure that this behavior makes sense and does not lead to wrong
     * application behavior.
     *
     *
     * @since 1.0
     */
    public static void rerunClassInitialization(Class<?>... classes) {
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunClassInitialization(classes);
    }

    private RuntimeClassInitialization() {
    }
}
