/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.impl.clinit;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

public class ClassInitializationTracking {

    /**
     * Field that is true during native image generation (even during system class loading), but
     * false at run time.
     *
     * Static initializer runs on the hosting VM, setting field value to true during native image
     * generation. At run time, the substituted value is used, setting the field value to false.
     */
    public static final boolean IS_IMAGE_BUILD_TIME;

    static {
        /*
         * Prevents javac from constant folding use of this field. It is set to true by the process
         * that builds the shared library.
         */
        IS_IMAGE_BUILD_TIME = true;
    }

    /**
     * This method is called from the instrumented class initialization methods.
     */
    @SuppressWarnings({"unused", "ConstantConditions"})
    public static void reportClassInitialized(Class<?> c) {
        if (ImageSingletonsSupport.isInstalled() && ImageSingletons.contains(RuntimeClassInitializationSupport.class)) {
            RuntimeClassInitializationSupport runtimeClassInitialization = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
            runtimeClassInitialization.reportClassInitialized(c);
        }
    }

    /**
     * This method is called from the instrumented class initialization methods.
     */
    @SuppressWarnings({"unused"})
    public static void reportObjectInstantiated(Object o) {
        if (ImageSingletonsSupport.isInstalled() && ImageSingletons.contains(RuntimeClassInitializationSupport.class)) {
            RuntimeClassInitializationSupport runtimeClassInitialization = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
            runtimeClassInitialization.reportObjectInstantiated(o);
        }
    }

}
