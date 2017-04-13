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

import com.oracle.truffle.nfi.types.NativeLibraryDescriptor;
import com.oracle.truffle.nfi.types.Parser;
import org.junit.Assert;
import org.junit.Test;

public class ParseLibraryDescriptorTest {

    @Test
    public void parseDefault() {
        NativeLibraryDescriptor def = Parser.parseLibraryDescriptor("default");
        Assert.assertTrue("isDefaultLibrary", def.isDefaultLibrary());
    }

    @Test
    public void parseFileStringSingle() {
        NativeLibraryDescriptor test = Parser.parseLibraryDescriptor("load 'test filename'");
        Assert.assertEquals("file name", "test filename", test.getFilename());
    }

    @Test
    public void parseFileStringDouble() {
        NativeLibraryDescriptor test = Parser.parseLibraryDescriptor("load \"test filename\"");
        Assert.assertEquals("file name", "test filename", test.getFilename());
    }

    @Test
    public void parseFileIdent() {
        NativeLibraryDescriptor test = Parser.parseLibraryDescriptor("load /test/path/file.so");
        Assert.assertEquals("file name", "/test/path/file.so", test.getFilename());
    }

    @Test
    public void parseWithOneFlag() {
        NativeLibraryDescriptor test = Parser.parseLibraryDescriptor("load(RTLD_NOW) testfile");
        Assert.assertEquals("file name", "testfile", test.getFilename());
        Assert.assertEquals("flag count", 1, test.getFlags().size());
        Assert.assertEquals("RTLD_NOW", test.getFlags().get(0));
    }

    @Test
    public void parseWithTwoFlags() {
        NativeLibraryDescriptor test = Parser.parseLibraryDescriptor("load(RTLD_NOW | RTLD_GLOBAL) testfile");
        Assert.assertEquals("file name", "testfile", test.getFilename());
        Assert.assertEquals("flag count", 2, test.getFlags().size());
        Assert.assertEquals("RTLD_NOW", test.getFlags().get(0));
        Assert.assertEquals("RTLD_GLOBAL", test.getFlags().get(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseUnknownCommand() {
        Parser.parseLibraryDescriptor("_unknown_command");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseUnknownToken() {
        Parser.parseLibraryDescriptor("%");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseEmpty() {
        Parser.parseLibraryDescriptor("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseEmptyFlags() {
        Parser.parseLibraryDescriptor("load() testfile");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseFlagsError() {
        Parser.parseLibraryDescriptor("load(RTLD_NOW .) testfile");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseStringError() {
        Parser.parseLibraryDescriptor("load 'testfile");
    }
}
