/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.nfi.types.NativeSource;
import com.oracle.truffle.nfi.types.Parser;
import org.junit.Assert;
import org.junit.Test;

public class ParseSourceTest {

    private static void testSimpleDescriptor(String descriptor) {
        NativeSource src = Parser.parseNFISource(descriptor);
        Assert.assertEquals("getLibraryDescriptor", descriptor, src.getLibraryDescriptor());
        Assert.assertTrue("isDefaultBackend", src.isDefaultBackend());
        Assert.assertEquals("preBoundSymbolsLength", 0, src.preBoundSymbolsLength());
    }

    @Test
    public void parseDefault() {
        testSimpleDescriptor("default");
    }

    @Test
    public void parseFileString() {
        testSimpleDescriptor("load 'test filename'");
    }

    @Test
    public void parseWithFlags() {
        testSimpleDescriptor("load(RTLD_NOW | RTLD_GLOBAL) \"testfile\"");
    }

    @Test
    public void testWithBackend() {
        NativeSource src = Parser.parseNFISource("with backend load 'testfile'");
        Assert.assertEquals("getLibraryDescriptor", "load 'testfile'", src.getLibraryDescriptor());
        Assert.assertEquals("preBoundSymbolsLength", 0, src.preBoundSymbolsLength());

        Assert.assertFalse("isDefaultBackend", src.isDefaultBackend());
        Assert.assertEquals("getNFIBackendId", "backend", src.getNFIBackendId());
    }

    @Test
    public void testWithPreBind() {
        NativeSource src = Parser.parseNFISource("load 'testfile' { sym1 (sint32):sint32; sym2 (double):void; }");
        Assert.assertEquals("getLibraryDescriptor", "load 'testfile'", src.getLibraryDescriptor());
        Assert.assertTrue("isDefaultBackend", src.isDefaultBackend());

        Assert.assertEquals("preBoundSymbolsLength", 2, src.preBoundSymbolsLength());

        Assert.assertEquals("getPreBoundSymbol(0)", "sym1", src.getPreBoundSymbol(0));
        Assert.assertEquals("getPreBoundSignature(0)", "(sint32):sint32", src.getPreBoundSignature(0));

        Assert.assertEquals("getPreBoundSymbol(1)", "sym2", src.getPreBoundSymbol(1));
        Assert.assertEquals("getPreBoundSignature(1)", "(double):void", src.getPreBoundSignature(1));
    }
}
