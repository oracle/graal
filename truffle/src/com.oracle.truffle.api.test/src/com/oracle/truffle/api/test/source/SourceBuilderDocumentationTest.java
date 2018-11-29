/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
