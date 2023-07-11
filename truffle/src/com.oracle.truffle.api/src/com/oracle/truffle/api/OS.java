/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

/**
 * Represents a supported operating system.
 *
 * @since 23.1
 */
public enum OS {

    /**
     * The macOS operating system.
     *
     * @since 23.1
     */
    DARWIN("darwin"),

    /**
     * The Linux operating system.
     *
     * @since 23.1
     */
    LINUX("linux"),

    /**
     * The Windows operating system.
     *
     * @since 23.1
     */
    WINDOWS("windows");

    private final String id;

    OS(String id) {
        this.id = id;
    }

    /**
     * Returns the string representing operating system name.
     *
     * @since 23.1
     */
    @Override
    public String toString() {
        return id;
    }

    /**
     * Returns the current operating system.
     *
     * @since 23.1
     */
    public static OS getCurrent() {
        String os = System.getProperty("os.name");
        if (os == null) {
            throw CompilerDirectives.shouldNotReachHere("The 'os.name' system property is not set.");
        } else if (os.equalsIgnoreCase("linux")) {
            return LINUX;
        } else if (os.equalsIgnoreCase("mac os x") || os.equalsIgnoreCase("darwin")) {
            return DARWIN;
        } else if (os.toLowerCase().startsWith("windows")) {
            return WINDOWS;
        } else {
            throw CompilerDirectives.shouldNotReachHere("Unsupported OS name " + os);
        }
    }
}
