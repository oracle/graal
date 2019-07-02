/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test.parser;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.nfi.spi.types.NativeLibraryDescriptor;
import com.oracle.truffle.nfi.test.parser.backend.TestLibrary;
import com.oracle.truffle.tck.TruffleRunner.RunWithPolyglotRule;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

public class ParseLibraryDescriptorTest {

    @ClassRule public static RunWithPolyglotRule runWithPolyglot = new RunWithPolyglotRule();

    private static NativeLibraryDescriptor parseLibraryDescriptor(String descriptor) {
        Source source = Source.newBuilder("nfi", String.format("with test %s", descriptor), "ParseLibraryDescriptorTest").internal(true).build();
        CallTarget target = runWithPolyglot.getTruffleTestEnv().parseInternal(source);
        TestLibrary library = (TestLibrary) target.call();
        return library.descriptor;
    }

    @Test
    public void parseDefault() {
        NativeLibraryDescriptor def = parseLibraryDescriptor("default");
        Assert.assertTrue("isDefaultLibrary", def.isDefaultLibrary());
    }

    @Test
    public void parseFileStringSingle() {
        NativeLibraryDescriptor test = parseLibraryDescriptor("load 'test filename'");
        Assert.assertEquals("file name", "test filename", test.getFilename());
    }

    @Test
    public void parseFileStringDouble() {
        NativeLibraryDescriptor test = parseLibraryDescriptor("load \"test filename\"");
        Assert.assertEquals("file name", "test filename", test.getFilename());
    }

    @Test
    public void parseFileIdent() {
        NativeLibraryDescriptor test = parseLibraryDescriptor("load /test/path/file.so");
        Assert.assertEquals("file name", "/test/path/file.so", test.getFilename());
    }

    @Test
    public void parseWithOneFlag() {
        NativeLibraryDescriptor test = parseLibraryDescriptor("load(RTLD_NOW) testfile");
        Assert.assertEquals("file name", "testfile", test.getFilename());
        Assert.assertEquals("flag count", 1, test.getFlags().size());
        Assert.assertEquals("RTLD_NOW", test.getFlags().get(0));
    }

    @Test
    public void parseWithTwoFlags() {
        NativeLibraryDescriptor test = parseLibraryDescriptor("load(RTLD_NOW | RTLD_GLOBAL) testfile");
        Assert.assertEquals("file name", "testfile", test.getFilename());
        Assert.assertEquals("flag count", 2, test.getFlags().size());
        Assert.assertEquals("RTLD_NOW", test.getFlags().get(0));
        Assert.assertEquals("RTLD_GLOBAL", test.getFlags().get(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseUnknownCommand() {
        parseLibraryDescriptor("_unknown_command");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseUnknownToken() {
        parseLibraryDescriptor("%");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseEmpty() {
        parseLibraryDescriptor("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseEmptyFlags() {
        parseLibraryDescriptor("load() testfile");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseFlagsError() {
        parseLibraryDescriptor("load(RTLD_NOW .) testfile");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseStringError() {
        parseLibraryDescriptor("load 'testfile");
    }
}
