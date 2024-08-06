/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.libffi;

/**
 * Versioning for libtrufflenfi.so and the corresponding SVM implementation.
 *
 * On Hotspot, the Java code of the NFI libffi backend is working together with a JNI native
 * library. On SVM, this native library doesn't exist. Instead, substitutions written in system java
 * are used. That means the interface of functions substituted by SVM can not be changed without
 * breaking compatibility of the Truffle NFI maven artifact with older versions of SVM.
 *
 * This version number can be used to make changes in a compatible way. For example, instead of
 * changing the signature of a native method that's substituted by SVM, a new method can be
 * introduced, and the Java code can use this version number to decide whether to call the old or
 * the new method.
 *
 * Version history:
 * <ul>
 * <li>0 "old" versions, before this version check was introduced
 * <li>1 first version with this version check
 * <li>2 introduced thread-local NFIState and cross-backend exception propagation
 * </ul>
 */
final class NativeLibVersion {

    public static int get() {
        return VERSION;
    }

    private static final int VERSION;

    static {
        int version;
        try {
            version = getLibTruffleNFIVersion();
        } catch (UnsatisfiedLinkError e) {
            // older than the first version that introduced version checks
            version = 0;
        }
        VERSION = version;
    }

    private static native int getLibTruffleNFIVersion();
}
