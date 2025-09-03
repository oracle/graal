/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.test.spec;

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.svm.hosted.webimage.test.util.JTTTestSuite;
import com.oracle.svm.webimage.jtt.xhr.UrlStreamTest;

public class JS_JTT_XHR extends JTTTestSuite {
    // @formatter:off
    // In the window context, `self` and `window` are aliases to the global object.
    // When using XHR with this definition, the test will fail. Used to verify that XHR is not used in window context.
    private static final String WINDOW_DEFS = ""
            + "this.self = this;"
            + "this.window = this;"
            + "this.XMLHttpRequest = function() { throw 'Using XHR not expected'; };";
    // In the worker context, `self` is an alias to the global object, and `window` is undefined.
    // This definition mocks the XMLHttpRequest class. Format string with four parameters: expected url, status code, status text, response ascii string.
    private static final String WORKER_DEFS = ""
            + "class XMLHttpRequestMock {"
            + "    constructor() {"
            + "        this.state = 0;"
            + "    }"
            + "    open(method, url, async) {"
            + "        if(this.state !== 0)"
            + "            throw 'Illegal state ' + this.state;"
            + "        if(method !== 'GET')"
            + "            throw 'Unexpected method ' + method;"
            + "        if(url !== '%s')"
            + "            throw 'Unexpected URL ' + url;"
            + "        if(async !== false)"
            + "            throw 'Unexpected async parameter ' + async;"
            + "        this.state = 1;"
            + "    }"
            + "    send() {"
            + "        if(this.state !== 1)"
            + "            throw 'Illegal state ' + this.state;"
            + "        if(this.responseType !== 'arraybuffer')"
            + "            throw 'Unexpected responseType ' + this.responseType;"
            + "        this.state = 2;"
            + "        this.status = %s;"
            + "        this.statusText = '%s';"
            + "        this.response = new Int8Array('%s'.split('').map(c => c.charCodeAt())).buffer;"
            + "    }"
            + "}"
            + "this.self = this;"
            + "this.XMLHttpRequest = XMLHttpRequestMock;";
    // @formatter:on
    // URL used for testing. No actual request is sent.
    private static final String URL = "https://www.graalvm.org/";
    private static final String CONTENT = "Run Programs Faster Anywhere";

    @BeforeClass
    public static void setupClass() {
        compileToJS(UrlStreamTest.class, "--enable-url-protocols=http,https");
    }

    @Test
    public void urlStreamTest() {
        testFileAgainstNoBuild(new String[]{CONTENT}, String.format(WORKER_DEFS, URL, 200, "OK", CONTENT), URL);
    }

    @Test
    public void httpErrorTest() {
        final String expectedMessage = "java.io.IOException: 404 Not Found";
        testFileAgainstNoBuild(new String[]{"Caught " + expectedMessage}, String.format(WORKER_DEFS, URL, 404, "Not Found", "Error Page"), URL);
    }

    @Test
    public void windowTest() {
        final String expectedMessage = "java.io.IOException: HTTP(S) URL connections are not allowed on main thread";
        testFileAgainstNoBuild(new String[]{"Caught " + expectedMessage}, WINDOW_DEFS, URL);
    }
}
