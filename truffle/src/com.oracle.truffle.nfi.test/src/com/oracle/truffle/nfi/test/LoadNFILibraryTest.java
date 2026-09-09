/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.exception.AbstractTruffleException;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class LoadNFILibraryTest extends NFITest {

    private static Object eval(String format, Object... args) {
        return loadLibrary(String.format(format, args));
    }

    /**
     * The {@code RTLD_*} flags are POSIX. On Windows the flag parser ignores names it does not
     * know, so passing them there does not exercise anything -- the request silently collapses to
     * a plain {@code load} and the assertion below passes without testing the flag. Skipping keeps
     * that honest; {@link #loadSearchDllLoadDir()} and friends are the Windows counterparts.
     */
    private static void assumePosixFlags() {
        Assume.assumeFalse("RTLD_* flags are POSIX-only", IS_WINDOWS);
    }

    private static void assumeWindowsFlags() {
        Assume.assumeTrue("LOAD_LIBRARY_SEARCH_* flags are Windows-only", IS_WINDOWS);
    }

    @Test
    public void loadTestLib() {
        Object library = eval("load '%s'", getLibPath("nativetest"));
        Assert.assertNotNull(library);
    }

    @Test
    public void loadLazy() {
        assumePosixFlags();
        Object library = eval("load(RTLD_LAZY) '%s'", getLibPath("nativetest"));
        Assert.assertNotNull(library);
    }

    @Test
    public void loadNow() {
        assumePosixFlags();
        Object library = eval("load(RTLD_NOW) '%s'", getLibPath("nativetest"));
        Assert.assertNotNull(library);
    }

    @Test
    public void loadLocal() {
        assumePosixFlags();
        Object library = eval("load(RTLD_LOCAL) '%s'", getLibPath("nativetest"));
        Assert.assertNotNull(library);
    }

    @Test
    public void loadGlobal() {
        assumePosixFlags();
        Object library = eval("load(RTLD_GLOBAL) '%s'", getLibPath("nativetest"));
        Assert.assertNotNull(library);
    }

    @Test
    public void loadGlobalLazy() {
        assumePosixFlags();
        Object library = eval("load(RTLD_GLOBAL|RTLD_LAZY) '%s'", getLibPath("nativetest"));
        Assert.assertNotNull(library);
    }

    /*
     * Windows counterparts of the RTLD_* cases above. These are the flags a caller needs so that a
     * DLL's own dependencies are found next to it, which is what the POSIX loader does for free via
     * RPATH/$ORIGIN. LOAD_LIBRARY_SEARCH_* requires a fully qualified path, which getLibPath
     * provides.
     */

    @Test
    public void loadSearchDllLoadDir() {
        assumeWindowsFlags();
        Object library = eval("load(LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR) '%s'", getLibPath("nativetest"));
        Assert.assertNotNull(library);
    }

    @Test
    public void loadSearchDefaultDirs() {
        assumeWindowsFlags();
        Object library = eval("load(LOAD_LIBRARY_SEARCH_DEFAULT_DIRS) '%s'", getLibPath("nativetest"));
        Assert.assertNotNull(library);
    }

    @Test
    public void loadSearchDllLoadDirAndDefaultDirs() {
        assumeWindowsFlags();
        // the combination Sulong uses by default when loading a native library by absolute path
        Object library = eval("load(LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_DEFAULT_DIRS) '%s'", getLibPath("nativetest"));
        Assert.assertNotNull(library);
    }

    @Test
    public void loadAlteredSearchPath() {
        assumeWindowsFlags();
        Object library = eval("load(LOAD_WITH_ALTERED_SEARCH_PATH) '%s'", getLibPath("nativetest"));
        Assert.assertNotNull(library);
    }

    @Test
    public void loadUnknownFlag() {
        Object library = eval("load(_UNKNOWN_FLAG) '%s'", getLibPath("nativetest"));
        Assert.assertNotNull(library);
    }

    @Test(expected = AbstractTruffleException.class)
    public void fileNotFound() {
        // a path shaped for the host, so the failure is "not found" rather than "malformed path"
        eval("load '%s'", IS_WINDOWS ? "C:/this/file/does/not/exist.dll" : "/this/file/does/not/exist.so");
    }
}
