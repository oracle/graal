/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.source;

import static com.oracle.truffle.api.test.ReflectionUtils.invokeStatic;
import static com.oracle.truffle.api.test.ReflectionUtils.loadRelative;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.net.URL;

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

public class SourceBuilderDocumentationTest extends AbstractPolyglotTest {

    private static final Class<?> SOURCE_SNIPPETS = loadRelative(SourceBuilderDocumentationTest.class, "SourceSnippets");

    private static boolean loadedOK;

    @BeforeClass
    public static void isAvailable() {
        loadedOK = SOURCE_SNIPPETS != null;
    }

    @Test
    public void relativeURL() throws Exception {
        if (!loadedOK) {
            return;
        }

        URL resource = SourceBuilderDocumentationTest.class.getResource("sample.js");
        assertNotNull("Sample js file found", resource);
        invokeStatic(SOURCE_SNIPPETS, "fromURL", SourceBuilderDocumentationTest.class);
    }

    @Test
    public void relativeURLWithOwnContent() throws Exception {
        if (!loadedOK) {
            return;
        }

        URL resource = SourceBuilderDocumentationTest.class.getResource("sample.js");
        assertNotNull("Sample js file found", resource);
        invokeStatic(SOURCE_SNIPPETS, "fromURLWithOwnContent", SourceBuilderDocumentationTest.class);
    }

    public void fileSample() throws Exception {
        if (!loadedOK) {
            return;
        }

        File sample = File.createTempFile("sample", ".java");
        sample.deleteOnExit();
        invokeStatic(SOURCE_SNIPPETS, "fromFile", sample.getParentFile(), sample.getName());
        sample.delete();
    }

    @Test
    public void stringSample() throws Exception {
        if (!loadedOK) {
            return;
        }

        Source source = (Source) invokeStatic(SOURCE_SNIPPETS, "fromAString");
        assertNotNull("Every source must have URI", source.getURI());
    }

    @Test
    public void readerSample() throws Exception {
        if (!loadedOK) {
            return;
        }

        Source source = (Source) invokeStatic(SOURCE_SNIPPETS, "fromReader", SourceBuilderDocumentationTest.class);
        assertNotNull("Every source must have URI", source.getURI());
    }
}
