/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;

public class InstrumentRegistrationTest {

    @ExpectError("Registered instrument class must be at least package protected.")
    @Registration(id = "NonPublicInstrument")
    private static final class PrivateInstrument extends ProxyInstrument {
    }

    @ExpectError("Registered instrument class must subclass TruffleInstrument.")
    @Registration(id = "WrongSuperClassInstrument")
    public static final class WrongSuperClassInstrument {
    }

    @Registration(id = "MyInstrumentGood", name = "MyInstrumentGood")
    public static final class MyInstrumentGood extends ProxyInstrument {
    }

    @Registration(id = "MyInstrumentGoodWithServices", name = "MyInstrumentGoodWithServices", services = Service1.class)
    public static final class MyInstrumentGoodWithServices extends ProxyInstrument {

    }

    @ExpectError("The class registered in the defaultLibraryExports must be a library export. " +
                    "To resolve this, add the @ExportLibrary to NoLibrary or remove the NoLibrary from the defaultLibraryExports.")
    @Registration(id = "tooldefaultexportprovider1", name = "tooldefaultexportprovider1", defaultLibraryExports = DefaultExportProviderRegistration1.NoLibrary.class)
    public static class DefaultExportProviderRegistration1 extends ProxyInstrument {
        public static class NoLibrary {
        }
    }

    @ExpectError("The library registered in the defaultLibraryExports must have a default export lookup enabled. " +
                    "To resolve this, set the @GenerateLibrary.defaultExportLookupEnabled to true on NoDefaultExportLibrary1 " +
                    "or remove the InvalidToolLibrary2 from the defaultLibraryExports.")
    @Registration(id = "tooldefaultexportprovider2", name = "tooldefaultexportprovider2", defaultLibraryExports = DefaultExportProviderRegistration2.InvalidToolLibrary2.class)
    public static class DefaultExportProviderRegistration2 extends ProxyInstrument {

        @ExportLibrary(value = LanguageRegistrationTest.NoDefaultExportLibrary1.class)
        public static class InvalidToolLibrary2 {

            @ExportMessage
            void execute1() {
            }
        }
    }

    @ExpectError("The library registered in the defaultLibraryExports must have a default export lookup enabled. " +
                    "To resolve this, set the @GenerateLibrary.defaultExportLookupEnabled to true on NoDefaultExportLibrary1, NoDefaultExportLibrary2 " +
                    "or remove the InvalidToolLibrary3 from the defaultLibraryExports.")
    @Registration(id = "tooldefaultexportprovider3", name = "tooldefaultexportprovider3", defaultLibraryExports = DefaultExportProviderRegistration3.InvalidToolLibrary3.class)
    public static class DefaultExportProviderRegistration3 extends ProxyInstrument {

        @ExportLibrary(value = LanguageRegistrationTest.NoDefaultExportLibrary1.class)
        @ExportLibrary(value = LanguageRegistrationTest.NoDefaultExportLibrary2.class)
        public static class InvalidToolLibrary3 {

            @ExportMessage
            void execute1() {
            }

            @ExportMessage
            void execute2() {
            }
        }
    }

    @ExpectError({"The library registered in the defaultLibraryExports must have a default export lookup enabled. " +
                    "To resolve this, set the @GenerateLibrary.defaultExportLookupEnabled to true on NoDefaultExportLibrary1 " +
                    "or remove the InvalidToolLibrary4A from the defaultLibraryExports.",
                    "The library registered in the defaultLibraryExports must have a default export lookup enabled. " +
                                    "To resolve this, set the @GenerateLibrary.defaultExportLookupEnabled to true on NoDefaultExportLibrary2 " +
                                    "or remove the InvalidToolLibrary4B from the defaultLibraryExports."})
    @Registration(id = "tooldefaultexportprovider4", name = "tooldefaultexportprovider4", defaultLibraryExports = {DefaultExportProviderRegistration4.InvalidToolLibrary4A.class,
                    DefaultExportProviderRegistration4.InvalidToolLibrary4B.class})
    public static class DefaultExportProviderRegistration4 extends ProxyInstrument {

        @ExportLibrary(value = LanguageRegistrationTest.NoDefaultExportLibrary1.class)
        public static class InvalidToolLibrary4A {

            @ExportMessage
            void execute1() {
            }
        }

        @ExportLibrary(value = LanguageRegistrationTest.NoDefaultExportLibrary2.class)
        public static class InvalidToolLibrary4B {

            @ExportMessage
            void execute2() {
            }
        }
    }

    @Registration(id = "tooldefaultexportprovider5", name = "tooldefaultexportprovider5", defaultLibraryExports = DefaultExportProviderRegistration5.ValidToolLibrary1.class)
    public static class DefaultExportProviderRegistration5 extends ProxyInstrument {

        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class ValidToolLibrary1 {

            @ExportMessage
            @SuppressWarnings("unused")
            static void execute3(String receiver) {
            }
        }
    }

    @Registration(id = "tooldefaultexportprovider6", name = "tooldefaultexportprovider6", defaultLibraryExports = DefaultExportProviderRegistration6.ValidToolLibrary2.class)
    public static class DefaultExportProviderRegistration6 extends ProxyInstrument {

        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class ValidToolLibrary2 {

            @ExportMessage
            @SuppressWarnings("unused")
            static void execute3(String receiver) {
            }

            @ExportMessage
            @SuppressWarnings("unused")
            static void execute4(String receiver) {
            }
        }
    }

    @Registration(id = "tooldefaultexportprovider7", name = "tooldefaultexportprovider7", defaultLibraryExports = {DefaultExportProviderRegistration7.ValidToolLibrary3A.class,
                    DefaultExportProviderRegistration7.ValidToolLibrary3B.class})
    public static class DefaultExportProviderRegistration7 extends ProxyInstrument {

        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class ValidToolLibrary3A {

            @ExportMessage
            @SuppressWarnings("unused")
            static void execute3(String receiver) {
            }
        }

        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class ValidToolLibrary3B {

            @ExportMessage
            @SuppressWarnings("unused")
            static void execute4(String receiver) {
            }
        }
    }

    @ExpectError("The library registered in the defaultLibraryExports must have a default export lookup enabled. " +
                    "To resolve this, set the @GenerateLibrary.defaultExportLookupEnabled to true on NoDefaultExportLibrary1 " +
                    "or remove the InvalidToolLibrary5 from the defaultLibraryExports.")
    @Registration(id = "tooldefaultexportprovider8", name = "tooldefaultexportprovider8", defaultLibraryExports = {DefaultExportProviderRegistration8.ValidToolLibrary4.class,
                    DefaultExportProviderRegistration8.InvalidToolLibrary5.class})
    public static class DefaultExportProviderRegistration8 extends ProxyInstrument {

        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class ValidToolLibrary4 {

            @ExportMessage
            @SuppressWarnings("unused")
            static void execute3(String receiver) {
            }
        }

        @ExportLibrary(value = LanguageRegistrationTest.NoDefaultExportLibrary1.class)
        public static class InvalidToolLibrary5 {

            @ExportMessage
            void execute1() {
            }
        }
    }

    @ExpectError("The library registered in the aotLibraryExports must be enabled for an ahead of time compilation. " +
                    "To resolve this, set the @ExportLibrary.useForAOT to true on NoLibrary " +
                    "or remove the NoLibrary from aotLibraryExports.")
    @Registration(id = "tooleagerexportprovider1", name = "tooleagerexportprovider1", aotLibraryExports = EagerExportProviderRegistration1.NoLibrary.class)
    public static class EagerExportProviderRegistration1 extends ProxyInstrument {
        public static class NoLibrary {
        }
    }

    @ExpectError("The library registered in the aotLibraryExports must be enabled for an ahead of time compilation. " +
                    "To resolve this, set the @ExportLibrary.useForAOT to true on InvalidToolLibrary6 " +
                    "or remove the InvalidToolLibrary6 from aotLibraryExports.")
    @Registration(id = "tooleagerexportprovider2", name = "tooleagerexportprovider2", aotLibraryExports = EagerExportProviderRegistration2.InvalidToolLibrary6.class)
    public static class EagerExportProviderRegistration2 extends ProxyInstrument {
        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class InvalidToolLibrary6 {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute3(String receiver) {
            }
        }
    }

    @ExpectError("The library registered in the aotLibraryExports must be enabled for an ahead of time compilation. " +
                    "To resolve this, set the @ExportLibrary.useForAOT to true on InvalidToolLibrary7 " +
                    "or remove the InvalidToolLibrary7 from aotLibraryExports.")
    @Registration(id = "tooleagerexportprovider3", name = "tooleagerexportprovider3", aotLibraryExports = EagerExportProviderRegistration3.InvalidToolLibrary7.class)
    public static class EagerExportProviderRegistration3 extends ProxyInstrument {
        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class InvalidToolLibrary7 {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute3(String receiver) {
            }

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute4(String receiver) {
            }
        }
    }

    @ExpectError({"The library registered in the aotLibraryExports must be enabled for an ahead of time compilation. " +
                    "To resolve this, set the @ExportLibrary.useForAOT to true on InvalidToolLibrary8A " +
                    "or remove the InvalidToolLibrary8A from aotLibraryExports.",
                    "The library registered in the aotLibraryExports must be enabled for an ahead of time compilation. " +
                                    "To resolve this, set the @ExportLibrary.useForAOT to true on InvalidToolLibrary8B " +
                                    "or remove the InvalidToolLibrary8B from aotLibraryExports."})
    @Registration(id = "tooleagerexportprovider4", name = "tooleagerexportprovider4", aotLibraryExports = {EagerExportProviderRegistration4.InvalidToolLibrary8A.class,
                    EagerExportProviderRegistration4.InvalidToolLibrary8B.class})
    public static class EagerExportProviderRegistration4 extends ProxyInstrument {
        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class InvalidToolLibrary8A {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute3(String receiver) {
            }
        }

        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class InvalidToolLibrary8B {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute4(String receiver) {
            }
        }
    }

    @Registration(id = "tooleagerexportprovider5", name = "tooleagerexportprovider5", aotLibraryExports = EagerExportProviderRegistration5.ValidToolLibrary5.class)
    public static class EagerExportProviderRegistration5 extends ProxyInstrument {
        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = true, useForAOTPriority = 10)
        public static class ValidToolLibrary5 {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute3(String receiver) {
            }
        }
    }

    @Registration(id = "tooleagerexportprovider6", name = "tooleagerexportprovider6", aotLibraryExports = EagerExportProviderRegistration6.ValidToolLibrary6.class)
    public static class EagerExportProviderRegistration6 extends ProxyInstrument {
        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = true, useForAOTPriority = 10)
        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = true, useForAOTPriority = 10)
        public static class ValidToolLibrary6 {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute3(String receiver) {
            }

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute4(String receiver) {
            }
        }
    }

    @Registration(id = "tooleagerexportprovider7", name = "tooleagerexportprovider7", aotLibraryExports = {EagerExportProviderRegistration7.ValidToolLibrary7A.class,
                    EagerExportProviderRegistration7.ValidToolLibrary7B.class})
    public static class EagerExportProviderRegistration7 extends ProxyInstrument {
        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = true, useForAOTPriority = 10)
        public static class ValidToolLibrary7A {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute3(String receiver) {
            }
        }

        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = true, useForAOTPriority = 10)
        public static class ValidToolLibrary7B {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute4(String receiver) {
            }
        }
    }

    @ExpectError("The library registered in the aotLibraryExports must be enabled for an ahead of time compilation. " +
                    "To resolve this, set the @ExportLibrary.useForAOT to true on InvalidToolLibrary8 " +
                    "or remove the InvalidToolLibrary8 from aotLibraryExports.")
    @Registration(id = "tooleagerexportprovider8", name = "tooleagerexportprovider8", aotLibraryExports = {EagerExportProviderRegistration8.ValidToolLibrary8.class,
                    EagerExportProviderRegistration8.InvalidToolLibrary8.class})
    public static class EagerExportProviderRegistration8 extends ProxyInstrument {
        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = true, useForAOTPriority = 10)
        public static class ValidToolLibrary8 {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute3(String receiver) {
            }
        }

        @ExportLibrary(value = LanguageRegistrationTest.DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class InvalidToolLibrary8 {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute4(String receiver) {
            }
        }
    }

    interface Service1 {
    }

    interface Service2 {
    }
}
