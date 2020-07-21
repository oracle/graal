/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.impl.InternalPlatform;

/**
 * Root of the interface hierarchy for architectures, OS, and supported combinations of them.
 * <p>
 * A platform group (e.g., an architecture or OS) is an interface extending {@link Platform}. A leaf
 * platform, e.g., a supported architecture-OS-combination, is a class that implements all the
 * groups that it belongs to. A leaf platform class must be non-abstract and must have a no-argument
 * constructor. It is good practice to make leaf platform classes {@code final} (to prevent
 * accidental subclassing) and to avoid state (i.e., no fields).
 * <p>
 * The annotation {@link Platforms} restricts a type, method, or field to certain platform groups or
 * leaf platforms.
 * <p>
 * This system makes the set of platform groups and leaf platforms extensible. Some standard
 * platforms are defined as inner classes.
 *
 * @since 19.0
 */
public interface Platform {

    /**
     * The system property name that specifies the fully qualified name of the {@link Platform}
     * implementation class that should be used. If the property is not specified, the platform
     * class is inferred from the standard architectures and operating systems specified in this
     * file, i.e., in most cases it is not necessary to use this property.
     *
     * @since 19.0
     */
    String PLATFORM_PROPERTY_NAME = "svm.platform";

    /**
     * Returns true if the current platform (the platform that the native image is built for) is
     * included in the provided platform group.
     * <p>
     * The platformGroup must be a compile-time constant, so that the call to this method can be
     * replaced with the constant boolean result.
     *
     * @since 19.0
     */
    static boolean includedIn(Class<? extends Platform> platformGroup) {
        return platformGroup.isInstance(ImageSingletons.lookup(Platform.class));
    }

    /*
     * The standard architectures that are supported.
     */
    /**
     * Supported architecture: x86 64-bit.
     *
     * @since 19.0
     */
    interface AMD64 extends Platform {

    }

    /**
     * Supported architecture: ARMv8 64-bit.
     *
     * @since 19.0
     */
    interface AARCH64 extends Platform {

    }

    /*
     * The standard operating systems that are supported.
     */
    /**
     * Supported operating system: Linux.
     *
     * @since 19.0
     */
    interface LINUX extends InternalPlatform.PLATFORM_JNI {

    }

    /**
     * Supported operating system: Darwin (MacOS).
     *
     * @since 19.0
     */
    interface DARWIN extends InternalPlatform.PLATFORM_JNI {

    }

    /**
     * Supported operating system: Windows.
     *
     * @since 19.0
     */
    interface WINDOWS extends InternalPlatform.PLATFORM_JNI {

    }

    /*
     * The standard leaf platforms, i.e., OS-architecture combinations that we support.
     */
    /**
     * Supported leaf platform: Linux on x86 64-bit.
     *
     * @since 19.0
     */
    class LINUX_AMD64 implements LINUX, AMD64 {

        /**
         * Instantiates a marker instance of this platform.
         *
         * @since 19.0
         */
        public LINUX_AMD64() {
        }

    }

    /**
     * Supported leaf platform: Linux on AArch64 64-bit.
     *
     * @since 19.0
     */
    final class LINUX_AARCH64 implements LINUX, AARCH64 {

        /**
         * Instantiates a marker instance of this platform.
         *
         * @since 19.0
         */
        public LINUX_AARCH64() {
        }

    }

    /**
     * Supported leaf platform: Darwin (MacOS) on x86 64-bit.
     *
     * @since 19.0
     */
    class DARWIN_AMD64 implements DARWIN, AMD64 {

        /**
         * Instantiates a marker instance of this platform.
         *
         * @since 19.0
         */
        public DARWIN_AMD64() {
        }
    }

    /**
     * Supported leaf platform: Darwin (MacOS) on AArch 64-bit.
     *
     * @since 2.0
     */
    final class DARWIN_AARCH64 implements DARWIN, AARCH64 {

        /**
         * Instantiates a marker instance of this platform.
         *
         * @since 2.0
         */
        public DARWIN_AARCH64() {
        }
    }

    /**
     * Supported leaf platform: Windows on x86 64-bit.
     *
     * @since 19.0
     */
    class WINDOWS_AMD64 implements WINDOWS, AMD64 {

        /**
         * Instantiates a marker instance of this platform.
         *
         * @since 19.0
         */
        public WINDOWS_AMD64() {
        }

    }

    /**
     * Marker for elements (types, methods, or fields) that are only visible during native image
     * generation and cannot be used at run time, regardless of the actual platform.
     *
     * @since 19.0
     */
    final class HOSTED_ONLY implements Platform {
        private HOSTED_ONLY() {
        }
    }

}
