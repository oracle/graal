/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.api.TruffleFile.FileTypeDetector;
import java.nio.charset.Charset;
import java.util.Collections;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.junit.Assert;
import org.junit.Test;

public class LanguageRegistrationTest {

    @ExpectError("Registered language class must be public")
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
    @ExpectError("A TruffleLanguage subclass must have a public no argument constructor.")
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

    @Registration(id = "filedetector1", name = "filedetector1", fileTypeDetectors = {FileTypeDetectorRegistration1.Detector.class})
    public static class FileTypeDetectorRegistration1 extends ProxyLanguage {
        public static class Detector extends ProxyFileTypeDetector {
        }
    }

    @ExpectError("Registered FileTypeDetector class must be public.")
    @Registration(id = "filedetector2", name = "filedetector2", fileTypeDetectors = {FileTypeDetectorRegistration2.Detector.class})
    public static class FileTypeDetectorRegistration2 extends ProxyLanguage {
        static class Detector extends ProxyFileTypeDetector {
        }
    }

    @ExpectError("Registered FileTypeDetector inner-class must be static.")
    @Registration(id = "filedetector3", name = "filedetector3", fileTypeDetectors = {FileTypeDetectorRegistration3.Detector.class})
    public static class FileTypeDetectorRegistration3 extends ProxyLanguage {
        public class Detector extends ProxyFileTypeDetector {
        }
    }

    @ExpectError("A FileTypeDetector subclass must have a public no argument constructor.")
    @Registration(id = "filedetector5", name = "filedetector5", fileTypeDetectors = {FileTypeDetectorRegistration5.Detector.class})
    public static class FileTypeDetectorRegistration5 extends ProxyLanguage {
        public static class Detector extends ProxyFileTypeDetector {
            Detector() {
            }

            @SuppressWarnings("unused")
            public Detector(String name) {
            }
        }
    }

    @interface GenerateLegacyRegistration {
    }

    @GenerateLegacyRegistration
    @Registration(id = "legacyregistration1", name = "LegacyRegistration1", implementationName = "legacy.registration1", version = "1.0.0", characterMimeTypes = "text/x-legacyregistration1")
    public static class LegacyRegistration1 extends ProxyLanguage {
    }

    @GenerateLegacyRegistration
    @Registration(id = "legacyregistration2", name = "LegacyRegistration2", implementationName = "legacy.registration2", version = "2.0.0", byteMimeTypes = "binary/x-legacyregistration2")
    public static class LegacyRegistration2 extends ProxyLanguage {
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

    @Test
    public void testLoadLegacyRegistrations() {
        try (Engine eng = Engine.create()) {
            Language language = eng.getLanguages().get("legacyregistration1");
            Assert.assertNotNull(language);
            Assert.assertEquals("LegacyRegistration1", language.getName());
            Assert.assertEquals("legacy.registration1", language.getImplementationName());
            Assert.assertEquals("1.0.0", language.getVersion());
            Assert.assertEquals(Collections.singleton("text/x-legacyregistration1"), language.getMimeTypes());
            language = eng.getLanguages().get("legacyregistration2");
            Assert.assertNotNull(language);
            Assert.assertEquals("LegacyRegistration2", language.getName());
            Assert.assertEquals("legacy.registration2", language.getImplementationName());
            Assert.assertEquals("2.0.0", language.getVersion());
            Assert.assertEquals(Collections.singleton("binary/x-legacyregistration2"), language.getMimeTypes());
        }
    }
}
