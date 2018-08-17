/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.io.ByteSequence;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.ExpectError;

@SuppressWarnings("deprecation")
public class MIMETypeTest {

    private static final String TEXT_MIMETYPE = "text/test-js";
    private static final String APPLICATION_MIMETYPE = "application/test-js";

    static {
        Registration reg = MIMETypeTest.class.getAnnotation(Registration.class);
        if (reg != null) {
            // use the mimetype to make the IDE happy about suppress warnings deprecation.
            reg.mimeType();
        }
    }

    // default configuration
    @Registration(id = "MimeTypeLanguage1", name = "")
    public static class MIMETypeLanguage1 extends ProxyLanguage {
    }

    @Test
    public void testMIMETypeLanguage2() {
        Engine engine = Engine.create();
        Language language = engine.getLanguages().get("MIMETypeLanguage2");
        assertTrue(language.getMimeTypes().size() == 2);
        assertTrue(language.getMimeTypes().contains(TEXT_MIMETYPE));
        assertTrue(language.getMimeTypes().contains(APPLICATION_MIMETYPE));
        assertEquals(TEXT_MIMETYPE, language.getDefaultMimeType());
        engine.close();
    }

    @Registration(id = "MIMETypeLanguage2", name = "", defaultMimeType = TEXT_MIMETYPE, characterMimeTypes = {TEXT_MIMETYPE, APPLICATION_MIMETYPE})
    public static class MIMETypeLanguage2 extends ProxyLanguage {
    }

    @Test
    public void testMIMETypeLanguage3() {
        Engine engine = Engine.create();
        Language language = engine.getLanguages().get("MIMETypeLanguage3");
        assertTrue(language.getMimeTypes().size() == 1);
        assertTrue(language.getMimeTypes().contains(TEXT_MIMETYPE));
        assertEquals(TEXT_MIMETYPE, language.getDefaultMimeType());
        engine.close();
    }

    @Registration(id = "MIMETypeLanguage3", name = "", characterMimeTypes = TEXT_MIMETYPE)
    public static class MIMETypeLanguage3 extends ProxyLanguage {
    }

    @Test
    public void testMIMETypeLanguage4() {
        Engine engine = Engine.create();
        Language language = engine.getLanguages().get("MIMETypeLanguage4");
        assertTrue(language.getMimeTypes().size() == 1);
        assertTrue(language.getMimeTypes().contains(TEXT_MIMETYPE));
        assertEquals(TEXT_MIMETYPE, language.getDefaultMimeType());
        engine.close();
    }

    @Registration(id = "MIMETypeLanguage4", name = "", byteMimeTypes = TEXT_MIMETYPE)
    public static class MIMETypeLanguage4 extends ProxyLanguage {
    }

    @Test
    public void testDefaultMimeBytes() throws IOException {
        Context context = Context.create();

        Path file = Files.createTempFile("foobar", ".tjs");
        file.toFile().deleteOnExit();

        Source source = Source.newBuilder("MIMETypeLanguage5", file.toFile()).build();
        assertTrue(source.hasBytes());
        assertFalse(source.hasCharacters());
        assertEquals(APPLICATION_MIMETYPE, source.getMimeType()); // detection

        // should complete
        context.eval(source);

        source = Source.newBuilder("MIMETypeLanguage5", "", "").mimeType(APPLICATION_MIMETYPE).build();
        try {
            context.eval(source);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Error evaluating the source. MIME type '" + APPLICATION_MIMETYPE + "' is byte based for language 'MIMETypeLanguage5' but the source contents are character based.",
                            e.getMessage());
        }

        Source byteBasedSource = Source.newBuilder("MIMETypeLanguage5", "", "").build();
        try {
            context.eval(byteBasedSource);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Error evaluating the source. The language MIMETypeLanguage5 only supports binary based sources but a character based source was provided.", e.getMessage());
        }

        Source illegalSource = Source.newBuilder("MIMETypeLanguage5", "", "").mimeType("application/x-illegal").build();
        try {
            context.eval(illegalSource);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Error evaluating the source. The language MIMETypeLanguage5 does not support MIME type application/x-illegal. Supported MIME types are [" + APPLICATION_MIMETYPE + "].",
                            e.getMessage());
        }

        context.close();
    }

    @Registration(id = "MIMETypeLanguage5", name = "", byteMimeTypes = APPLICATION_MIMETYPE)
    public static class MIMETypeLanguage5 extends ProxyLanguage {
        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        }
    }

    @Test
    public void testDefaultMimeCharacters() throws IOException {
        Context context = Context.create();

        Path file = Files.createTempFile("foobar", ".tjs");
        file.toFile().deleteOnExit();

        Source source = Source.newBuilder("MIMETypeLanguage6", file.toFile()).build();
        assertFalse(source.hasBytes());
        assertTrue(source.hasCharacters());
        assertEquals(APPLICATION_MIMETYPE, source.getMimeType()); // detection
        context.eval(source);

        source = Source.newBuilder("MIMETypeLanguage6", ByteSequence.create(new byte[]{1, 2, 3, 4}), "").mimeType(APPLICATION_MIMETYPE).build();
        try {
            context.eval(source);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Error evaluating the source. " +
                            "MIME type '" + APPLICATION_MIMETYPE + "' is character based for language 'MIMETypeLanguage6' but the source contents are byte based.",
                            e.getMessage());
        }

        Source byteBasedSource = Source.newBuilder("MIMETypeLanguage6", ByteSequence.create(new byte[]{1, 2, 3, 4}), "").build();
        try {
            context.eval(byteBasedSource);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Error evaluating the source. The language MIMETypeLanguage6 only supports character based sources but a binary based source was provided.", e.getMessage());
        }

        Source illegalSource = Source.newBuilder("MIMETypeLanguage6", ByteSequence.create(new byte[]{1, 2, 3, 4}), "").mimeType("application/x-illegal").build();
        try {
            context.eval(illegalSource);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Error evaluating the source. The language MIMETypeLanguage6 does not support MIME type application/x-illegal. Supported MIME types are [" + APPLICATION_MIMETYPE + "].",
                            e.getMessage());
        }

        context.close();
    }

    @Registration(id = "MIMETypeLanguage6", name = "", characterMimeTypes = APPLICATION_MIMETYPE)
    public static class MIMETypeLanguage6 extends ProxyLanguage {

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        }
    }

    @Test
    public void testDefaultMimeCharacters2() {
        Context context = Context.create();

        Source source = Source.newBuilder("MIMETypeLanguage7", ByteSequence.create(new byte[]{1, 2, 3, 4}), "").buildLiteral();
        try {
            context.eval(source);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Error evaluating the source. " +
                            "The language MIMETypeLanguage7 expects character based sources by default but a binary based source was provided. " +
                            "Provide a binary based source instead or specify a MIME type for the source. " +
                            "Available MIME types for binary based sources are [" + APPLICATION_MIMETYPE + "].",
                            e.getMessage());
        }
        context.close();
    }

    @Registration(id = "MIMETypeLanguage7", name = "", defaultMimeType = TEXT_MIMETYPE, characterMimeTypes = TEXT_MIMETYPE, byteMimeTypes = APPLICATION_MIMETYPE)
    public static class MIMETypeLanguage7 extends ProxyLanguage {

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        }
    }

    @Test
    public void testDefaultMimeBytes2() {
        Context context = Context.create();

        Source source = Source.newBuilder("MIMETypeLanguage8", "", "").buildLiteral();
        try {
            context.eval(source);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Error evaluating the source. " +
                            "The language MIMETypeLanguage8 expects character based sources by default but a binary based source was provided. " +
                            "Provide a character based source instead or specify a MIME type for the source. " +
                            "Available MIME types for character based sources are [" + TEXT_MIMETYPE + "].",
                            e.getMessage());
        }
        context.close();
    }

    @Registration(id = "MIMETypeLanguage8", name = "", defaultMimeType = APPLICATION_MIMETYPE, characterMimeTypes = TEXT_MIMETYPE, byteMimeTypes = APPLICATION_MIMETYPE)
    public static class MIMETypeLanguage8 extends ProxyLanguage {

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        }
    }

    @Registration(id = "MimeTypeLanguageLegacy1", name = "", mimeType = TEXT_MIMETYPE)
    public static class MIMETypeLanguageLegacy1 extends ProxyLanguage {
    }

    @Test
    public void testMIMETypeLanguageLegacy1() {
        Engine engine = Engine.create();
        Language language = engine.getLanguages().get("MimeTypeLanguageLegacy1");
        assertTrue(language.getMimeTypes().size() == 1);
        assertTrue(language.getMimeTypes().contains(TEXT_MIMETYPE));
        assertEquals(TEXT_MIMETYPE, language.getDefaultMimeType());
        engine.close();
    }

    @ExpectError("The defaultMimeType is not contained in the list of supported characterMimeTypes or byteMimeTypes. Add the specified default MIME type to character or byte MIME types to resolve this.")
    @Registration(id = "MimeTypeLanguageLegacy2", name = "", defaultMimeType = TEXT_MIMETYPE, mimeType = TEXT_MIMETYPE)
    public static class MIMETypeLanguageLegacy2 extends ProxyLanguage {
    }

    // default configuration
    @ExpectError("Invalid MIME type '' provided. MIME types consist of a type and a subtype separated by '/'.")
    @Registration(id = "MIMETypeLanguageError1", name = "", byteMimeTypes = {""})
    public static class MIMETypeLanguageError1 extends ProxyLanguage {
    }

    @ExpectError("Invalid MIME type '/' provided. MIME types consist of a type and a subtype separated by '/'.")
    @Registration(id = "MIMETypeLanguageError2", name = "", byteMimeTypes = {"/"})
    public static class MIMETypeLanguageError2 extends ProxyLanguage {
    }

    @ExpectError("Invalid MIME type 'a/' provided. MIME types consist of a type and a subtype separated by '/'.")
    @Registration(id = "MIMETypeLanguageError3", name = "", byteMimeTypes = {"a/"})
    public static class MIMETypeLanguageError3 extends ProxyLanguage {
    }

    @ExpectError("Invalid MIME type '/a' provided. MIME types consist of a type and a subtype separated by '/'.")
    @Registration(id = "MIMETypeLanguageError4", name = "", byteMimeTypes = {"/a"})
    public static class MIMETypeLanguageError4 extends ProxyLanguage {
    }

    @ExpectError("Invalid MIME type '' provided. MIME types consist of a type and a subtype separated by '/'.")
    @Registration(id = "MIMETypeLanguageError5", name = "", characterMimeTypes = {""})
    public static class MIMETypeLanguageError5 extends ProxyLanguage {
    }

    @ExpectError("Invalid MIME type '/' provided. MIME types consist of a type and a subtype separated by '/'.")
    @Registration(id = "MIMETypeLanguageError6", name = "", characterMimeTypes = {"/"})
    public static class MIMETypeLanguageError6 extends ProxyLanguage {
    }

    @ExpectError("Invalid MIME type 'a/' provided. MIME types consist of a type and a subtype separated by '/'.")
    @Registration(id = "MIMETypeLanguageError7", name = "", characterMimeTypes = {"a/"})
    public static class MIMETypeLanguageError7 extends ProxyLanguage {
    }

    @ExpectError("Invalid MIME type '/a' provided. MIME types consist of a type and a subtype separated by '/'.")
    @Registration(id = "MIMETypeLanguageError8", name = "", characterMimeTypes = {"/a"})
    public static class MIMETypeLanguageError8 extends ProxyLanguage {
    }

    @ExpectError("No defaultMimeType attribute specified. The defaultMimeType attribute needs to be specified if more than one MIME type was specified.")
    @Registration(id = "MIMETypeLanguageError9", name = "", characterMimeTypes = {TEXT_MIMETYPE, APPLICATION_MIMETYPE})
    public static class MIMETypeLanguageError9 extends ProxyLanguage {
    }

    @ExpectError("No defaultMimeType attribute specified. The defaultMimeType attribute needs to be specified if more than one MIME type was specified.")
    @Registration(id = "MIMETypeLanguageError10", name = "", byteMimeTypes = {TEXT_MIMETYPE, APPLICATION_MIMETYPE})
    public static class MIMETypeLanguageError10 extends ProxyLanguage {
    }

    @ExpectError("The defaultMimeType is not contained in the list of supported characterMimeTypes or byteMimeTypes. Add the specified default MIME type to character or byte MIME types to resolve this.")
    @Registration(id = "MIMETypeLanguageError11", name = "", defaultMimeType = "text/invalid", byteMimeTypes = {TEXT_MIMETYPE, APPLICATION_MIMETYPE})
    public static class MIMETypeLanguageError11 extends ProxyLanguage {
    }

    @ExpectError("Duplicate MIME type specified '" + TEXT_MIMETYPE + "'. MIME types must be unique.")
    @Registration(id = "MIMETypeLanguageError12", name = "", byteMimeTypes = {TEXT_MIMETYPE, TEXT_MIMETYPE})
    public static class MIMETypeLanguageError12 extends ProxyLanguage {
    }

    @ExpectError("Duplicate MIME type specified '" + TEXT_MIMETYPE + "'. MIME types must be unique.")
    @Registration(id = "MIMETypeLanguageError13", name = "", characterMimeTypes = {TEXT_MIMETYPE, TEXT_MIMETYPE})
    public static class MIMETypeLanguageError13 extends ProxyLanguage {
    }

    @ExpectError("Duplicate MIME type specified '" + TEXT_MIMETYPE + "'. MIME types must be unique.")
    @Registration(id = "MIMETypeLanguageError14", name = "", byteMimeTypes = TEXT_MIMETYPE, characterMimeTypes = {TEXT_MIMETYPE})
    public static class MIMETypeLanguageError14 extends ProxyLanguage {
    }

}
