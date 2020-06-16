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
package com.oracle.truffle.nfi.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;

public class LoadNFILibraryTest extends NFITest {

    private final String nativeTestLib = System.getProperty("native.test.lib");

    private static TruffleObject eval(String format, Object... args) {
        Source source = Source.newBuilder("nfi", String.format(format, args), "LoadLibraryTest").internal(true).build();
        CallTarget target = runWithPolyglot.getTruffleTestEnv().parseInternal(source);
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
