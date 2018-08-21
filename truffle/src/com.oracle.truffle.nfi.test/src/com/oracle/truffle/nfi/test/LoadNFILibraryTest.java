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
package com.oracle.truffle.nfi.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;

public class LoadNFILibraryTest extends NFITest {

    private final String nativeTestLib = System.getProperty("native.test.lib");

    private static TruffleObject eval(String format, Object... args) {
        Source source = Source.newBuilder("nfi", String.format(format, args), "LoadLibraryTest").build();
        CallTarget target = runWithPolyglot.getTruffleTestEnv().parse(source);
        return (TruffleObject) target.call();
    }

    @Test
    public void loadDefault() {
        TruffleObject library = eval("default");
        Assert.assertNotNull(library);
    }

    @Test
    public void loadTestLib() {
        TruffleObject library = eval("load '%s'", nativeTestLib);
        Assert.assertNotNull(library);
    }

    @Test
    public void loadLazy() {
        TruffleObject library = eval("load(RTLD_LAZY) '%s'", nativeTestLib);
        Assert.assertNotNull(library);
    }

    @Test
    public void loadNow() {
        TruffleObject library = eval("load(RTLD_NOW) '%s'", nativeTestLib);
        Assert.assertNotNull(library);
    }

    @Test
    public void loadLocal() {
        TruffleObject library = eval("load(RTLD_LOCAL) '%s'", nativeTestLib);
        Assert.assertNotNull(library);
    }

    @Test
    public void loadGlobal() {
        TruffleObject library = eval("load(RTLD_GLOBAL) '%s'", nativeTestLib);
        Assert.assertNotNull(library);
    }

    @Test
    public void loadGlobalLazy() {
        TruffleObject library = eval("load(RTLD_GLOBAL|RTLD_LAZY) '%s'", nativeTestLib);
        Assert.assertNotNull(library);
    }

    @Test
    public void loadUnknownFlag() {
        TruffleObject library = eval("load(_UNKNOWN_FLAG) '%s'", nativeTestLib);
        Assert.assertNotNull(library);
    }

    @Test(expected = UnsatisfiedLinkError.class)
    public void fileNotFound() {
        eval("load /this/file/does/not/exist.so");
    }
}
