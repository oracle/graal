/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
