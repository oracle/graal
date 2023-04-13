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
import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.library.DefaultExportProvider;
import com.oracle.truffle.api.library.EagerExportProvider;
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

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.LanguageRegistrationTest.FileTypeDetectorRegistration2.Detector " +
                    "must be a static inner-class or a top-level class. To resolve this, make the Detector static or top-level class.")
    @Registration(id = "filedetector2", name = "filedetector2", fileTypeDetectors = {FileTypeDetectorRegistration2.Detector.class})
    public static class FileTypeDetectorRegistration2 extends ProxyLanguage {
        public abstract class Detector extends ProxyFileTypeDetector {
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.LanguageRegistrationTest.FileTypeDetectorRegistration3.Detector " +
                    "must have a no argument public constructor. To resolve this, add public Detector() constructor.")
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

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.LanguageRegistrationTest.FileTypeDetectorRegistration4.Detector " +
                    "must be a public class or package protected class in com.oracle.truffle.api.dsl.test.processor package. " +
                    "To resolve this, make the Detector public or move it to com.oracle.truffle.api.dsl.test.processor.")
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

    @ExpectError("Registered defaultExportProviders must be subclass of com.oracle.truffle.api.library.DefaultExportProvider. " +
                    "To resolve this, implement DefaultExportProvider.")
    @Registration(id = "langdefaultexportprovider1", name = "langdefaultexportprovider1", defaultExportProviders = DefaultExportProviderRegistration1.DefaultExportProviderImpl.class)
    public static class DefaultExportProviderRegistration1 extends ProxyLanguage {
        public static class DefaultExportProviderImpl {
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.LanguageRegistrationTest.DefaultExportProviderRegistration2.DefaultExportProviderImpl " +
                    "must be a static inner-class or a top-level class. To resolve this, make the DefaultExportProviderImpl static or top-level class.")
    @Registration(id = "langdefaultexportprovider2", name = "langdefaultexportprovider2", defaultExportProviders = DefaultExportProviderRegistration2.DefaultExportProviderImpl.class)
    public static class DefaultExportProviderRegistration2 extends ProxyLanguage {
        abstract class DefaultExportProviderImpl extends ProxyDefaultExportProvider {
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.LanguageRegistrationTest.DefaultExportProviderRegistration3.DefaultExportProviderImpl " +
                    "must have a no argument public constructor. To resolve this, add public DefaultExportProviderImpl() constructor.")
    @Registration(id = "langdefaultexportprovider3", name = "langdefaultexportprovider3", defaultExportProviders = DefaultExportProviderRegistration3.DefaultExportProviderImpl.class)
    public static class DefaultExportProviderRegistration3 extends ProxyLanguage {
        static class DefaultExportProviderImpl extends ProxyDefaultExportProvider {

            @SuppressWarnings("unused")
            DefaultExportProviderImpl(String unused) {
            }

            @SuppressWarnings("unused")
            DefaultExportProviderImpl(long unused) {
            }

            @SuppressWarnings("unused")
            private DefaultExportProviderImpl() {
            }
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.LanguageRegistrationTest.DefaultExportProviderRegistration4.DefaultExportProviderImpl " +
                    "must be a public class or package protected class in com.oracle.truffle.api.dsl.test.processor package. " +
                    "To resolve this, make the DefaultExportProviderImpl public or move it to com.oracle.truffle.api.dsl.test.processor.")
    @Registration(id = "langdefaultexportprovider4", name = "langdefaultexportprovider4", defaultExportProviders = DefaultExportProviderRegistration4.DefaultExportProviderImpl.class)
    public static class DefaultExportProviderRegistration4 extends ProxyLanguage {
        private static class DefaultExportProviderImpl extends ProxyDefaultExportProvider {

            @SuppressWarnings("unused")
            DefaultExportProviderImpl() {
            }
        }
    }

    @Registration(id = "langdefaultexportprovider5", name = "langdefaultexportprovider5", defaultExportProviders = DefaultExportProviderRegistration5.DefaultExportProviderImpl.class)
    public static class DefaultExportProviderRegistration5 extends ProxyLanguage {
        static class DefaultExportProviderImpl extends ProxyDefaultExportProvider {

            @SuppressWarnings("unused")
            DefaultExportProviderImpl(String unused) {
            }

            @SuppressWarnings("unused")
            DefaultExportProviderImpl(long unused) {
            }

            DefaultExportProviderImpl() {
            }
        }
    }

    @Registration(id = "langdefaultexportprovider6", name = "langdefaultexportprovider6", defaultExportProviders = {
                    DefaultExportProviderRegistration6.DefaultExportProviderImpl1.class,
                    DefaultExportProviderRegistration6.DefaultExportProviderImpl2.class
    })
    public static class DefaultExportProviderRegistration6 extends ProxyLanguage {
        static class DefaultExportProviderImpl1 extends ProxyDefaultExportProvider {
        }

        static class DefaultExportProviderImpl2 extends ProxyDefaultExportProvider {
        }
    }

    @ExpectError("Registered eagerExportProviders must be subclass of com.oracle.truffle.api.library.EagerExportProvider. " +
                    "To resolve this, implement EagerExportProvider.")
    @Registration(id = "langeagerexportprovider1", name = "langeagerexportprovider1", eagerExportProviders = EagerExportProviderRegistration1.EagerExportProviderImpl.class)
    public static class EagerExportProviderRegistration1 extends ProxyLanguage {
        public static class EagerExportProviderImpl {
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.LanguageRegistrationTest.EagerExportProviderRegistration2.EagerExportProviderImpl " +
                    "must be a static inner-class or a top-level class. To resolve this, make the EagerExportProviderImpl static or top-level class.")
    @Registration(id = "langeagerexportprovider2", name = "langeagerexportprovider2", eagerExportProviders = EagerExportProviderRegistration2.EagerExportProviderImpl.class)
    public static class EagerExportProviderRegistration2 extends ProxyLanguage {
        abstract class EagerExportProviderImpl extends ProxyEagerExportProvider {
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.LanguageRegistrationTest.EagerExportProviderRegistration3.EagerExportProviderImpl" +
                    " must have a no argument public constructor. To resolve this, add public EagerExportProviderImpl() constructor.")
    @Registration(id = "langeagerexportprovider3", name = "langeagerexportprovider3", eagerExportProviders = EagerExportProviderRegistration3.EagerExportProviderImpl.class)
    public static class EagerExportProviderRegistration3 extends ProxyLanguage {
        static class EagerExportProviderImpl extends ProxyEagerExportProvider {

            @SuppressWarnings("unused")
            EagerExportProviderImpl(String unused) {
            }

            @SuppressWarnings("unused")
            EagerExportProviderImpl(long unused) {
            }

            @SuppressWarnings("unused")
            private EagerExportProviderImpl() {
            }
        }
    }

    @ExpectError("The com.oracle.truffle.api.dsl.test.processor.LanguageRegistrationTest.EagerExportProviderRegistration4.EagerExportProviderImpl " +
                    "must be a public class or package protected class in com.oracle.truffle.api.dsl.test.processor package. " +
                    "To resolve this, make the EagerExportProviderImpl public or move it to com.oracle.truffle.api.dsl.test.processor.")
    @Registration(id = "langeagerexportprovider4", name = "langeagerexportprovider4", eagerExportProviders = EagerExportProviderRegistration4.EagerExportProviderImpl.class)
    public static class EagerExportProviderRegistration4 extends ProxyLanguage {
        private static class EagerExportProviderImpl extends ProxyEagerExportProvider {

            @SuppressWarnings("unused")
            EagerExportProviderImpl() {
            }
        }
    }

    @Registration(id = "langeagerexportprovider5", name = "langeagerexportprovider5", eagerExportProviders = EagerExportProviderRegistration5.EagerExportProviderImpl.class)
    public static class EagerExportProviderRegistration5 extends ProxyLanguage {
        static class EagerExportProviderImpl extends ProxyEagerExportProvider {

            @SuppressWarnings("unused")
            EagerExportProviderImpl(String unused) {
            }

            @SuppressWarnings("unused")
            EagerExportProviderImpl(long unused) {
            }

            EagerExportProviderImpl() {
            }
        }
    }

    @Registration(id = "langeagerexportprovider6", name = "langeagerexportprovider6", eagerExportProviders = {
                    EagerExportProviderRegistration6.EagerExportProviderImpl1.class,
                    EagerExportProviderRegistration6.EagerExportProviderImpl2.class
    })
    public static class EagerExportProviderRegistration6 extends ProxyLanguage {
        static class EagerExportProviderImpl1 extends ProxyEagerExportProvider {
        }

        static class EagerExportProviderImpl2 extends ProxyEagerExportProvider {
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

    static class ProxyDefaultExportProvider implements DefaultExportProvider {

        @Override
        public String getLibraryClassName() {
            return null;
        }

        @Override
        public Class<?> getDefaultExport() {
            return null;
        }

        @Override
        public Class<?> getReceiverClass() {
            return null;
        }

        @Override
        public int getPriority() {
            return 0;
        }
    }

    static class ProxyEagerExportProvider implements EagerExportProvider {

        @Override
        public void ensureRegistered() {

        }

        @Override
        public String getLibraryClassName() {
            return null;
        }
    }
}
