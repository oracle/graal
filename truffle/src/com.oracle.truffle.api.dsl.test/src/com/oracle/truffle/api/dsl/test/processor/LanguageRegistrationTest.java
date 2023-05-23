/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.charset.Charset;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleFile.FileTypeDetector;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class LanguageRegistrationTest {

    @ExpectError("Registered language class must be at least package protected")
    @TruffleLanguage.Registration(id = "myLang", name = "", version = "0")
    private static final class MyLang {
    }

    @ExpectError("Registered language inner-class must be static")
    @TruffleLanguage.Registration(id = "myLangNonStatic", name = "", version = "0")
    public final class MyLangNonStatic {
    }

    @ExpectError("Registered language class must subclass TruffleLanguage")
    @TruffleLanguage.Registration(id = "myLang", name = "", version = "0")
    public static final class MyLangNoSubclass {
    }

    @TruffleLanguage.Registration(id = "myLangNoCnstr", name = "", version = "0")
    @ExpectError("A TruffleLanguage subclass must have at least package protected no argument constructor.")
    public static final class MyLangWrongConstr extends TruffleLanguage<Object> {

        private MyLangWrongConstr() {
        }

        @Override
        protected CallTarget parse(ParsingRequest env) throws IOException {
            throw new IOException();
        }

        @Override
        protected Object createContext(Env env) {
            throw new UnsupportedOperationException();
        }

    }

    @TruffleLanguage.Registration(id = "myLangNoField", name = "myLangNoField", version = "0")
    public static final class MyLangGood extends TruffleLanguage<Object> {

        public MyLangGood() {
        }

        @Override
        protected CallTarget parse(ParsingRequest env) throws IOException {
            throw new IOException();
        }

        @Override
        protected Object createContext(Env env) {
            throw new UnsupportedOperationException();
        }

    }

    @ExpectError("The attribute id is mandatory.")
    @Registration(name = "")
    public static class InvalidIDError1 extends ProxyLanguage {
    }

    @ExpectError("The attribute id is mandatory.")
    @Registration(id = "", name = "")
    public static class InvalidIDError2 extends ProxyLanguage {
    }

    @ExpectError("Id 'graal' is reserved for other use and must not be used as id.")
    @Registration(id = "graal", name = "")
    public static class InvalidIDError3 extends ProxyLanguage {
    }

    @ExpectError("Id 'engine' is reserved for other use and must not be used as id.")
    @Registration(id = "engine", name = "")
    public static class InvalidIDError4 extends ProxyLanguage {
    }

    @ExpectError("Id 'compiler' is reserved for other use and must not be used as id.")
    @Registration(id = "compiler", name = "")
    public static class InvalidIDError5 extends ProxyLanguage {
    }

    @Registration(id = "filedetector1", name = "filedetector1", fileTypeDetectors = {
                    FileTypeDetectorRegistration1.Detector1.class,
                    FileTypeDetectorRegistration1.Detector2.class
    })
    public static class FileTypeDetectorRegistration1 extends ProxyLanguage {
        public static class Detector1 extends ProxyFileTypeDetector {
        }

        public static class Detector2 extends ProxyFileTypeDetector {
        }
    }

    @ExpectError("The class LanguageRegistrationTest.FileTypeDetectorRegistration2.Detector must be a static inner-class or a top-level class. " +
                    "To resolve this, make the Detector static or top-level class.")
    @Registration(id = "filedetector2", name = "filedetector2", fileTypeDetectors = {FileTypeDetectorRegistration2.Detector.class})
    public static class FileTypeDetectorRegistration2 extends ProxyLanguage {
        public abstract class Detector extends ProxyFileTypeDetector {
        }
    }

    @ExpectError("The class LanguageRegistrationTest.FileTypeDetectorRegistration3.Detector must have a no argument public constructor. " +
                    "To resolve this, add public Detector() constructor.")
    @Registration(id = "filedetector3", name = "filedetector3", fileTypeDetectors = {FileTypeDetectorRegistration3.Detector.class})
    public static class FileTypeDetectorRegistration3 extends ProxyLanguage {
        public static class Detector extends ProxyFileTypeDetector {

            @SuppressWarnings("unused")
            Detector(String unused) {
            }

            @SuppressWarnings("unused")
            Detector(long unused) {
            }

            @SuppressWarnings("unused")
            private Detector() {
            }
        }
    }

    @ExpectError("The class LanguageRegistrationTest.FileTypeDetectorRegistration4.Detector must be public or package protected " +
                    "in the com.oracle.truffle.api.dsl.test.processor package. To resolve this, make the " +
                    "LanguageRegistrationTest.FileTypeDetectorRegistration4.Detector public or move it to the " +
                    "com.oracle.truffle.api.dsl.test.processor package.")
    @Registration(id = "filedetector4", name = "filedetector4", fileTypeDetectors = {FileTypeDetectorRegistration4.Detector.class})
    public static class FileTypeDetectorRegistration4 extends ProxyLanguage {
        private static class Detector extends ProxyFileTypeDetector {
            @SuppressWarnings("unused")
            Detector() {
            }
        }
    }

    @Registration(id = "filedetector5", name = "filedetector5", fileTypeDetectors = {FileTypeDetectorRegistration5.Detector.class})
    public static class FileTypeDetectorRegistration5 extends ProxyLanguage {
        public static class Detector extends ProxyFileTypeDetector {

            @SuppressWarnings("unused")
            Detector(String unused) {
            }

            @SuppressWarnings("unused")
            Detector(long unused) {
            }

            Detector() {
            }
        }
    }

    @ExpectError("The class LanguageRegistrationTest.DefaultExportProviderRegistration1.NoLibrary must have the @ExportLibrary annotation. " +
                    "To resolve this, add the @ExportLibrary annotation to the library class or remove the library from the defaultLibraryExports list.")
    @Registration(id = "langdefaultexportprovider1", name = "langdefaultexportprovider1", defaultLibraryExports = DefaultExportProviderRegistration1.NoLibrary.class)
    public static class DefaultExportProviderRegistration1 extends ProxyLanguage {
        public static class NoLibrary {
        }
    }

    @ExpectError("The class LanguageRegistrationTest.NoDefaultExportLibrary1 must set @GenerateLibrary(defaultExportLookupEnabled = true). " +
                    "To resolve this, set the @GenerateLibrary(defaultExportLookupEnabled = true) attribute on type LanguageRegistrationTest.NoDefaultExportLibrary1 " +
                    "or remove the LanguageRegistrationTest.DefaultExportProviderRegistration2.InvalidLangLibrary2 from the defaultLibraryExports list.")
    @Registration(id = "langdefaultexportprovider2", name = "langdefaultexportprovider2", defaultLibraryExports = DefaultExportProviderRegistration2.InvalidLangLibrary2.class)
    public static class DefaultExportProviderRegistration2 extends ProxyLanguage {

        @ExportLibrary(value = NoDefaultExportLibrary1.class)
        public static class InvalidLangLibrary2 {

            @ExportMessage
            void execute1() {
            }
        }
    }

    @ExpectError("The class LanguageRegistrationTest.NoDefaultExportLibrary1, LanguageRegistrationTest.NoDefaultExportLibrary2 must set @GenerateLibrary(defaultExportLookupEnabled = true). " +
                    "To resolve this, set the @GenerateLibrary(defaultExportLookupEnabled = true) attribute on type LanguageRegistrationTest.NoDefaultExportLibrary1, LanguageRegistrationTest.NoDefaultExportLibrary2 " +
                    "or remove the LanguageRegistrationTest.DefaultExportProviderRegistration3.InvalidLangLibrary3 from the defaultLibraryExports list.")
    @Registration(id = "langdefaultexportprovider3", name = "langdefaultexportprovider3", defaultLibraryExports = DefaultExportProviderRegistration3.InvalidLangLibrary3.class)
    public static class DefaultExportProviderRegistration3 extends ProxyLanguage {

        @ExportLibrary(value = NoDefaultExportLibrary1.class)
        @ExportLibrary(value = NoDefaultExportLibrary2.class)
        public static class InvalidLangLibrary3 {

            @ExportMessage
            void execute1() {
            }

            @ExportMessage
            void execute2() {
            }
        }
    }

    @ExpectError({"The class LanguageRegistrationTest.NoDefaultExportLibrary1 must set @GenerateLibrary(defaultExportLookupEnabled = true). " +
                    "To resolve this, set the @GenerateLibrary(defaultExportLookupEnabled = true) attribute on type LanguageRegistrationTest.NoDefaultExportLibrary1 " +
                    "or remove the LanguageRegistrationTest.DefaultExportProviderRegistration4.InvalidLangLibrary4A from the defaultLibraryExports list.",
                    "The class LanguageRegistrationTest.NoDefaultExportLibrary2 must set @GenerateLibrary(defaultExportLookupEnabled = true). " +
                                    "To resolve this, set the @GenerateLibrary(defaultExportLookupEnabled = true) attribute on type LanguageRegistrationTest.NoDefaultExportLibrary2 " +
                                    "or remove the LanguageRegistrationTest.DefaultExportProviderRegistration4.InvalidLangLibrary4B from the defaultLibraryExports list."})
    @Registration(id = "langdefaultexportprovider4", name = "langdefaultexportprovider4", defaultLibraryExports = {DefaultExportProviderRegistration4.InvalidLangLibrary4A.class,
                    DefaultExportProviderRegistration4.InvalidLangLibrary4B.class})
    public static class DefaultExportProviderRegistration4 extends ProxyLanguage {

        @ExportLibrary(value = NoDefaultExportLibrary1.class)
        public static class InvalidLangLibrary4A {

            @ExportMessage
            void execute1() {
            }
        }

        @ExportLibrary(value = NoDefaultExportLibrary2.class)
        public static class InvalidLangLibrary4B {

            @ExportMessage
            void execute2() {
            }
        }
    }

    @Registration(id = "langdefaultexportprovider5", name = "langdefaultexportprovider5", defaultLibraryExports = DefaultExportProviderRegistration5.ValidLangLibrary1.class)
    public static class DefaultExportProviderRegistration5 extends ProxyLanguage {

        @ExportLibrary(value = DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class ValidLangLibrary1 {

            @ExportMessage
            @SuppressWarnings("unused")
            static void execute3(String receiver) {
            }
        }
    }

    @Registration(id = "langdefaultexportprovider6", name = "langdefaultexportprovider6", defaultLibraryExports = DefaultExportProviderRegistration6.ValidLangLibrary2.class)
    public static class DefaultExportProviderRegistration6 extends ProxyLanguage {

        @ExportLibrary(value = DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        @ExportLibrary(value = DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class ValidLangLibrary2 {

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

    @Registration(id = "langdefaultexportprovider7", name = "langdefaultexportprovider7", defaultLibraryExports = {DefaultExportProviderRegistration7.ValidLangLibrary3A.class,
                    DefaultExportProviderRegistration7.ValidLangLibrary3B.class})
    public static class DefaultExportProviderRegistration7 extends ProxyLanguage {

        @ExportLibrary(value = DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class ValidLangLibrary3A {

            @ExportMessage
            @SuppressWarnings("unused")
            static void execute3(String receiver) {
            }
        }

        @ExportLibrary(value = DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class ValidLangLibrary3B {

            @ExportMessage
            @SuppressWarnings("unused")
            static void execute4(String receiver) {
            }
        }
    }

    @ExpectError("The class LanguageRegistrationTest.NoDefaultExportLibrary1 must set @GenerateLibrary(defaultExportLookupEnabled = true). " +
                    "To resolve this, set the @GenerateLibrary(defaultExportLookupEnabled = true) attribute on type LanguageRegistrationTest.NoDefaultExportLibrary1 " +
                    "or remove the LanguageRegistrationTest.DefaultExportProviderRegistration8.InvalidLangLibrary5 from the defaultLibraryExports list.")
    @Registration(id = "langdefaultexportprovider8", name = "langdefaultexportprovider8", defaultLibraryExports = {DefaultExportProviderRegistration8.ValidLangLibrary4.class,
                    DefaultExportProviderRegistration8.InvalidLangLibrary5.class})
    public static class DefaultExportProviderRegistration8 extends ProxyLanguage {

        @ExportLibrary(value = DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class ValidLangLibrary4 {

            @ExportMessage
            @SuppressWarnings("unused")
            static void execute3(String receiver) {
            }
        }

        @ExportLibrary(value = NoDefaultExportLibrary1.class)
        public static class InvalidLangLibrary5 {

            @ExportMessage
            void execute1() {
            }
        }
    }

    @ExpectError("The class LanguageRegistrationTest.EagerExportProviderRegistration1.NoLibrary must set @ExportLibrary(useForAOT = true). " +
                    "To resolve this, set ExportLibrary(useForAOT = true) on type LanguageRegistrationTest.EagerExportProviderRegistration1.NoLibrary " +
                    "or remove the library from the aotLibraryExports list.")
    @Registration(id = "langeagerexportprovider1", name = "langeagerexportprovider1", aotLibraryExports = EagerExportProviderRegistration1.NoLibrary.class)
    public static class EagerExportProviderRegistration1 extends ProxyLanguage {
        public static class NoLibrary {
        }
    }

    @ExpectError("The class LanguageRegistrationTest.EagerExportProviderRegistration2.InvalidLangLibrary6 must set @ExportLibrary(useForAOT = true). " +
                    "To resolve this, set ExportLibrary(useForAOT = true) on type LanguageRegistrationTest.EagerExportProviderRegistration2.InvalidLangLibrary6 " +
                    "or remove the library from the aotLibraryExports list.")
    @Registration(id = "langeagerexportprovider2", name = "langeagerexportprovider2", aotLibraryExports = EagerExportProviderRegistration2.InvalidLangLibrary6.class)
    public static class EagerExportProviderRegistration2 extends ProxyLanguage {
        @ExportLibrary(value = DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class InvalidLangLibrary6 {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute3(String receiver) {
            }
        }
    }

    @ExpectError("The class LanguageRegistrationTest.EagerExportProviderRegistration3.InvalidLangLibrary7 must set @ExportLibrary(useForAOT = true). " +
                    "To resolve this, set ExportLibrary(useForAOT = true) on type LanguageRegistrationTest.EagerExportProviderRegistration3.InvalidLangLibrary7 " +
                    "or remove the library from the aotLibraryExports list.")
    @Registration(id = "langeagerexportprovider3", name = "langeagerexportprovider3", aotLibraryExports = EagerExportProviderRegistration3.InvalidLangLibrary7.class)
    public static class EagerExportProviderRegistration3 extends ProxyLanguage {
        @ExportLibrary(value = DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        @ExportLibrary(value = DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class InvalidLangLibrary7 {

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

    @ExpectError({"The class LanguageRegistrationTest.EagerExportProviderRegistration4.InvalidLangLibrary8A must set @ExportLibrary(useForAOT = true). " +
                    "To resolve this, set ExportLibrary(useForAOT = true) on type LanguageRegistrationTest.EagerExportProviderRegistration4.InvalidLangLibrary8A " +
                    "or remove the library from the aotLibraryExports list.",
                    "The class LanguageRegistrationTest.EagerExportProviderRegistration4.InvalidLangLibrary8B must set @ExportLibrary(useForAOT = true). " +
                                    "To resolve this, set ExportLibrary(useForAOT = true) on type LanguageRegistrationTest.EagerExportProviderRegistration4.InvalidLangLibrary8B " +
                                    "or remove the library from the aotLibraryExports list."})
    @Registration(id = "langeagerexportprovider4", name = "langeagerexportprovider4", aotLibraryExports = {EagerExportProviderRegistration4.InvalidLangLibrary8A.class,
                    EagerExportProviderRegistration4.InvalidLangLibrary8B.class})
    public static class EagerExportProviderRegistration4 extends ProxyLanguage {
        @ExportLibrary(value = DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class InvalidLangLibrary8A {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute3(String receiver) {
            }
        }

        @ExportLibrary(value = DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class InvalidLangLibrary8B {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute4(String receiver) {
            }
        }
    }

    @Registration(id = "langeagerexportprovider5", name = "langeagerexportprovider5", aotLibraryExports = EagerExportProviderRegistration5.ValidLangLibrary5.class)
    public static class EagerExportProviderRegistration5 extends ProxyLanguage {
        @ExportLibrary(value = DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = true, useForAOTPriority = 10)
        public static class ValidLangLibrary5 {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute3(String receiver) {
            }
        }
    }

    @Registration(id = "langeagerexportprovider6", name = "langeagerexportprovider6", aotLibraryExports = EagerExportProviderRegistration6.ValidLangLibrary6.class)
    public static class EagerExportProviderRegistration6 extends ProxyLanguage {
        @ExportLibrary(value = DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = true, useForAOTPriority = 10)
        @ExportLibrary(value = DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = true, useForAOTPriority = 10)
        public static class ValidLangLibrary6 {

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

    @Registration(id = "langeagerexportprovider7", name = "langeagerexportprovider7", aotLibraryExports = {EagerExportProviderRegistration7.ValidLangLibrary7A.class,
                    EagerExportProviderRegistration7.ValidLangLibrary7B.class})
    public static class EagerExportProviderRegistration7 extends ProxyLanguage {
        @ExportLibrary(value = DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = true, useForAOTPriority = 10)
        public static class ValidLangLibrary7A {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute3(String receiver) {
            }
        }

        @ExportLibrary(value = DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = true, useForAOTPriority = 10)
        public static class ValidLangLibrary7B {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute4(String receiver) {
            }
        }
    }

    @ExpectError("The class LanguageRegistrationTest.EagerExportProviderRegistration8.InvalidLangLibrary8 must set @ExportLibrary(useForAOT = true). " +
                    "To resolve this, set ExportLibrary(useForAOT = true) on type LanguageRegistrationTest.EagerExportProviderRegistration8.InvalidLangLibrary8 " +
                    "or remove the library from the aotLibraryExports list.")
    @Registration(id = "langeagerexportprovider8", name = "langeagerexportprovider8", aotLibraryExports = {EagerExportProviderRegistration8.ValidLangLibrary8.class,
                    EagerExportProviderRegistration8.InvalidLangLibrary8.class})
    public static class EagerExportProviderRegistration8 extends ProxyLanguage {
        @ExportLibrary(value = DefaultExportLibrary1.class, receiverType = String.class, priority = 10, useForAOT = true, useForAOTPriority = 10)
        public static class ValidLangLibrary8 {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute3(String receiver) {
            }
        }

        @ExportLibrary(value = DefaultExportLibrary2.class, receiverType = String.class, priority = 10, useForAOT = false)
        public static class InvalidLangLibrary8 {

            @SuppressWarnings("unused")
            @ExportMessage
            static void execute4(String receiver) {
            }
        }
    }

    static class ProxyFileTypeDetector implements FileTypeDetector {

        @Override
        @SuppressWarnings("unused")
        public String findMimeType(TruffleFile file) throws IOException {
            return null;
        }

        @Override
        @SuppressWarnings("unused")
        public Charset findEncoding(TruffleFile file) throws IOException {
            return null;
        }
    }

    @GenerateLibrary
    abstract static class NoDefaultExportLibrary1 extends Library {
        public abstract void execute1(Object receiver);
    }

    @GenerateLibrary
    abstract static class NoDefaultExportLibrary2 extends Library {
        public abstract void execute2(Object receiver);
    }

    @GenerateLibrary(defaultExportLookupEnabled = true)
    @GenerateAOT
    abstract static class DefaultExportLibrary1 extends Library {
        public abstract void execute3(Object receiver);
    }

    @GenerateLibrary(defaultExportLookupEnabled = true)
    @GenerateAOT
    abstract static class DefaultExportLibrary2 extends Library {
        public abstract void execute4(Object receiver);
    }
}
