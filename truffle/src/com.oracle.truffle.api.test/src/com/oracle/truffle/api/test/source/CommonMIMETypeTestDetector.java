/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommonMIMETypeTestDetector implements TruffleFile.FileTypeDetector {

    private static final Pattern DOC_TYPE_PATTERN = Pattern.compile("<!DOCTYPE\\s+(\\S+)\\s+");
    private static final Pattern ENCODING_PATTERN = Pattern.compile("<\\?xml version=\"1.0\" encoding=\"(.*)\"\\?>");

    @Override
    public String findMimeType(TruffleFile file) throws IOException {
        String name = file.getName();
        if (name != null) {
            if (name.endsWith(".java")) {
                return "text/x-java";
            }
            if (name.endsWith(".tjs")) {
                return "application/test-js";
            }
            if (name.endsWith(".txt")) {
                return "text/plain";
            }
            if (name.endsWith(".xml")) {
                String content = new String(file.readAllBytes(), "UTF-8");
                Matcher m = DOC_TYPE_PATTERN.matcher(content);
                if (m.find()) {
                    String docType = m.group(1);
                    return "text/" + docType + "+xml";
                }
                return "text/xml";
            }
        }
        return null;
    }

    @Override
    public Charset findEncoding(TruffleFile file) throws IOException {
        String name = file.getName();
        if (name.endsWith(".xml")) {
            String content = new String(file.readAllBytes(), "UTF-8");
            Matcher m = ENCODING_PATTERN.matcher(content);
            if (m.find()) {
                String encoding = m.group(1);
                try {
                    return Charset.forName(encoding);
                } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                    // pass and return null
                }
            }
        }
        return null;
    }

    @TruffleLanguage.Registration(id = "TestFooXML", name = "", byteMimeTypes = "text/foo+xml", fileTypeDetectors = {CommonMIMETypeTestDetector.class})
    public static class TestFooXMLLanguage extends ProxyLanguage {
    }
}
