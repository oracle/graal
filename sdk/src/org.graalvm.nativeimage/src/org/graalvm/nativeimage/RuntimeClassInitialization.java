/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
