/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleFile.FileTypeDetector;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.dsl.test.ExpectError;
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

    @Registration(id = "languageresource1", name = "languageresource1", internalResources = {
                    InternalResourceRegistration1.Resource1.class,
                    InternalResourceRegistration1.Resource2.class
    })
    public static class InternalResourceRegistration1 extends ProxyLanguage {
        @InternalResource.Id("test-resource-1")
        public static class Resource1 extends ProxyInternalResource {
        }

        @InternalResource.Id("test-resource-2")
        public static class Resource2 extends ProxyInternalResource {
        }
    }

    @ExpectError("The class LanguageRegistrationTest.InternalResourceRegistration2.Resource must be a static inner-class or a top-level class. " +
                    "To resolve this, make the Resource static or top-level class.")
    @Registration(id = "languageresource2", name = "languageresource2", internalResources = {InternalResourceRegistration2.Resource.class})
    public static class InternalResourceRegistration2 extends ProxyLanguage {
        @InternalResource.Id("test-resource")
        public abstract class Resource extends ProxyInternalResource {
        }
    }

    @ExpectError("The class LanguageRegistrationTest.InternalResourceRegistration3.Resource must have a no argument public constructor. " +
                    "To resolve this, add public Resource() constructor.")
    @Registration(id = "languageresource3", name = "languageresource3", internalResources = {InternalResourceRegistration3.Resource.class})
    public static class InternalResourceRegistration3 extends ProxyLanguage {
        @InternalResource.Id("test-resource")
        public static class Resource extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource(String unused) {
            }

            @SuppressWarnings("unused")
            Resource(long unused) {
            }

            @SuppressWarnings("unused")
            private Resource() {
            }
        }
    }

    @ExpectError("The class LanguageRegistrationTest.InternalResourceRegistration4.Resource must be public or package protected " +
                    "in the com.oracle.truffle.api.dsl.test.processor package. To resolve this, make the " +
                    "LanguageRegistrationTest.InternalResourceRegistration4.Resource public or move it to the " +
                    "com.oracle.truffle.api.dsl.test.processor package.")
    @Registration(id = "languageresource4", name = "languageresource4", internalResources = {InternalResourceRegistration4.Resource.class})
    public static class InternalResourceRegistration4 extends ProxyLanguage {
        @InternalResource.Id("test-resource")
        private static class Resource extends ProxyInternalResource {
            @SuppressWarnings("unused")
            Resource() {
            }
        }
    }

    @Registration(id = "languageresource5", name = "languageresource5", internalResources = {InternalResourceRegistration5.Resource.class})
    public static class InternalResourceRegistration5 extends ProxyLanguage {
        @InternalResource.Id("test-resource")
        public static class Resource extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource(String unused) {
            }

            @SuppressWarnings("unused")
            Resource(long unused) {
            }

            Resource() {
            }
        }
    }

    @ExpectError("The class LanguageRegistrationTest.InternalResourceRegistration6.Resource must be annotated by the @Id annotation. " +
                    "To resolve this, add '@Id(\"resource-id\")' annotation.")
    @Registration(id = "languageresource6", name = "languageresource6", internalResources = {InternalResourceRegistration6.Resource.class})
    public static class InternalResourceRegistration6 extends ProxyLanguage {

        public static class Resource extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource() {
            }
        }
    }

    @ExpectError("Internal resources must have unique ids within the component. " +
                    "But LanguageRegistrationTest.InternalResourceRegistration7.Resource1 and LanguageRegistrationTest.InternalResourceRegistration7.Resource2 use the same id duplicated-id. " +
                    "To resolve this, change the @Id value on LanguageRegistrationTest.InternalResourceRegistration7.Resource1 or LanguageRegistrationTest.InternalResourceRegistration7.Resource2.")
    @Registration(id = "languageresource7", name = "languageresource7", internalResources = {InternalResourceRegistration7.Resource1.class, InternalResourceRegistration7.Resource2.class})
    public static class InternalResourceRegistration7 extends ProxyLanguage {

        @InternalResource.Id("duplicated-id")
        public static class Resource1 extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource1() {
            }
        }

        @InternalResource.Id("duplicated-id")
        public static class Resource2 extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource2() {
            }
        }
    }

    @ExpectError("The '@Id.componentId' for an required internal resources must be unset or equal to '@Registration.id'. " +
                    "To resolve this, remove the '@Id.componentId = \"other-language\"'.")
    @Registration(id = "languageresource8", name = "languageresource8", internalResources = {InternalResourceRegistration8.Resource1.class})
    public static class InternalResourceRegistration8 extends ProxyLanguage {

        @InternalResource.Id(value = "resource-id", componentId = "other-language")
        public static class Resource1 extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource1() {
            }
        }
    }

    @ExpectError("Optional internal resources must not be registered using '@Registration' annotation. To resolve this, " +
                    "remove the 'LanguageRegistrationTest.InternalResourceRegistration9.Resource1' from 'internalResources' the or " +
                    "make the 'LanguageRegistrationTest.InternalResourceRegistration9.Resource1' non-optional by removing 'optional = true'.")
    @Registration(id = "languageresource9", name = "languageresource9", internalResources = {InternalResourceRegistration9.Resource1.class})
    public static class InternalResourceRegistration9 extends ProxyLanguage {

        @InternalResource.Id(value = "resource-id", componentId = "languageresource9", optional = true)
        public static class Resource1 extends ProxyInternalResource {

            @SuppressWarnings("unused")
            Resource1() {
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

    static class ProxyInternalResource implements InternalResource {

        @Override
        public void unpackFiles(Env env, Path targetDirectory) {
        }

        @Override
        public String versionHash(Env env) {
            return "1";
        }
    }
}
