/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Used by mx to compute the version of the implementation of the Java Platform.
 * Prints a string that contains:
 * - the version number
 * - the optional pre-release identifier
 * - the optional build number (typically not available in local builds)
 * - optional build information
 * See: {@link mx_sdk_vm_impl.graalvm_version()}
 *
 * Examples:
 * - with `Java(TM) SE Runtime Environment (build 17.0.7+8-LTS-jvmci-23.0-b10)`
 *   prints: `JDK_VERSION_INFO="17.0.7||+8|-LTS-jvmci-23.0-b10"`
 * - with `OpenJDK Runtime Environment (build 21-ea+19-1566)`
 *   prints: `JDK_VERSION_INFO="21|ea|+19|-1566"`
 */
public class JDKVersionInfo {
    public static void main(String[] args) {
        Runtime.Version v = Runtime.version();

        String version = v.patch() != 0 ? "." + v.patch() : "";
        if (version != "" || v.update() != 0) {
            version = "." + v.update() + version;
        }
        if (version != "" || v.interim() != 0) {
            version = "." + v.interim() + version;
        }
        version = v.feature() + version;

        System.out.printf("JDK_VERSION_INFO=\"%s|%s|%s|%s\"",
            version,
            v.pre().orElse(""),
            v.build().isPresent() ? "+" + v.build().get() : "",
            v.optional().isPresent() ? "-" + v.optional().get() : ""
        );
    }
}
