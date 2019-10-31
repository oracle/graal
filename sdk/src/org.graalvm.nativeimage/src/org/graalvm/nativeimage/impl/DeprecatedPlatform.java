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
package org.graalvm.nativeimage.impl;

import org.graalvm.nativeimage.Platform;

public interface DeprecatedPlatform {

    /**
     * Supported operating system: Linux.
     *
     * @since 19.0
     */
    interface LINUX_SUBSTITUTION extends Platform {
    }

    /**
     * Supported operating system: Darwin (MacOS).
     *
     * @since 19.0
     */
    interface DARWIN_SUBSTITUTION extends Platform {
    }

    /**
     * Supported leaf platform: Linux on x86 64-bit.
     *
     * @since 19.0
     */
    class LINUX_SUBSTITUTION_AMD64 implements DeprecatedPlatform.LINUX_SUBSTITUTION, InternalPlatform.LINUX_JNI_AND_SUBSTITUTIONS, Platform.AMD64 {

        /**
         * Instantiates a marker instance of this platform.
         *
         * @since 19.0
         */
        public LINUX_SUBSTITUTION_AMD64() {
        }
    }

    /**
     * Supported leaf platform: Linux on AArch64 64-bit.
     *
     * @since 19.0
     */
    class LINUX_SUBSTITUTION_AARCH64 implements DeprecatedPlatform.LINUX_SUBSTITUTION, InternalPlatform.LINUX_JNI_AND_SUBSTITUTIONS, Platform.AARCH64 {

        /**
         * Instantiates a marker instance of this platform.
         *
         * @since 19.0
         */
        public LINUX_SUBSTITUTION_AARCH64() {
        }
    }

    /**
     * Supported leaf platform: Darwin (MacOS) on x86 64-bit.
     *
     * @since 19.0
     */
    class DARWIN_SUBSTITUTION_AMD64 implements DeprecatedPlatform.DARWIN_SUBSTITUTION, InternalPlatform.DARWIN_JNI_AND_SUBSTITUTIONS, Platform.AMD64 {

        /**
         * Instantiates a marker instance of this platform.
         *
         * @since 19.0
         */
        public DARWIN_SUBSTITUTION_AMD64() {
        }
    }

    /**
     * Supported leaf platform: Darwin (MacOS) on AArch 64-bit.
     *
     * @since 2.0
     */
    class DARWIN_SUBSTITUTION_AARCH64 implements DeprecatedPlatform.DARWIN_SUBSTITUTION, InternalPlatform.DARWIN_JNI_AND_SUBSTITUTIONS, Platform.AARCH64 {

        /**
         * Instantiates a marker instance of this platform.
         *
         * @since 2.0
         */
        public DARWIN_SUBSTITUTION_AARCH64() {
        }
    }
}
