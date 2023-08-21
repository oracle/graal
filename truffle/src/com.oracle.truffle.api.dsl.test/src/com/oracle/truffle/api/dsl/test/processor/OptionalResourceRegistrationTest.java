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
package com.oracle.truffle.api.dsl.test.processor;

import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.dsl.test.ExpectError;

import java.nio.file.Path;

public class OptionalResourceRegistrationTest {

    @InternalResource.Id(value = "optional-resource", optionalFor = "test-language")
    public static final class OptionalResource1 implements InternalResource {

        @Override
        public void unpackFiles(Env env, Path targetDirectory) {
        }

        @Override
        public String versionHash(Env env) {
            return null;
        }
    }

    @ExpectError("The class OptionalResourceRegistrationTest.OptionalResource2 must be a static inner-class or a top-level class. " +
                    "To resolve this, make the OptionalResource2 static or top-level class.")
    @InternalResource.Id(value = "optional-resource", optionalFor = "test-language")
    public final class OptionalResource2 implements InternalResource {

        @Override
        public void unpackFiles(Env env, Path targetDirectory) {
        }

        @Override
        public String versionHash(Env env) {
            return null;
        }
    }

    @ExpectError("The class OptionalResourceRegistrationTest.OptionalResource3 must be public or package protected in the com.oracle.truffle.api.dsl.test.processor package. " +
                    "To resolve this, make the OptionalResourceRegistrationTest.OptionalResource3 package protected or move it to the com.oracle.truffle.api.dsl.test.processor package.")
    @InternalResource.Id(value = "optional-resource", optionalFor = "test-language")
    private static final class OptionalResource3 implements InternalResource {

        @Override
        public void unpackFiles(Env env, Path targetDirectory) {
        }

        @Override
        public String versionHash(Env env) {
            return null;
        }
    }

    @ExpectError("The class OptionalResourceRegistrationTest.OptionalResource4 must have a no argument public or package protected constructor. " +
                    "To resolve this, add OptionalResource4() constructor.")
    @InternalResource.Id(value = "optional-resource", optionalFor = "test-language")
    public static final class OptionalResource4 implements InternalResource {

        @SuppressWarnings("unused")
        private OptionalResource4() {
        }

        @SuppressWarnings("unused")
        public OptionalResource4(String str) {
        }

        @Override
        public void unpackFiles(Env env, Path targetDirectory) {
        }

        @Override
        public String versionHash(Env env) {
            return null;
        }
    }

    @ExpectError("The annotation @Id can be applied only to InternalResource instances. " +
                    "To resolve this, remove the @Id annotation or implement InternalResource.")
    @InternalResource.Id(value = "optional-resource", optionalFor = "test-language")
    private static final class OptionalResource5 {
    }
}
