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

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

/**
 * Tests the "default" library, the NFI equivalent of {@code dlopen(NULL)} / {@code RTLD_DEFAULT}.
 * <p>
 * The contract is that looking a symbol up in it searches the main executable <em>and</em> every
 * shared object already loaded into the process. These tests run identically on every platform on
 * purpose: the Windows implementation used to search only the launcher executable's export table,
 * which resolved essentially nothing, and the test suite worked around that by redirecting to
 * re-exported copies in the test library rather than covering the real path.
 * <p>
 * The symbols used here are C standard library functions, which are loaded into every process this
 * test can run in: glibc on Linux, libSystem on macOS, ucrtbase.dll on Windows.
 */
@RunWith(TruffleRunner.class)
public class DefaultLibraryNFITest extends NFITest {

    @BeforeClass
    public static void checkDefaultLibraryAvailable() {
        Assume.assumeNotNull(defaultLibrary);
    }

    public static class CallAbs extends NFITestRootNode {

        private final Object abs = lookupAndBindDefault("abs", "(sint32):sint32");
        @Child InteropLibrary absInterop = getInterop(abs);

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            return absInterop.execute(abs, frame.getArguments()[0]);
        }
    }

    /**
     * Resolves a libc function through the default library and calls it. On Windows this only
     * passes if the lookup walks the loaded modules -- {@code abs} is exported by ucrtbase.dll, not
     * by the launcher executable.
     */
    @Test
    public void lookupLibcFunction(@Inject(CallAbs.class) CallTarget target) {
        Assert.assertEquals("abs(-42)", 42, target.call(-42));
    }

    @Test
    public void lookupLibcFunctionTwice(@Inject(CallAbs.class) CallTarget target) {
        // guards against a lookup that caches the first miss rather than the resolved symbol
        Assert.assertEquals("abs(-1)", 1, target.call(-1));
        Assert.assertEquals("abs(-2)", 2, target.call(-2));
    }

    /**
     * A symbol that exists in no loaded module must fail cleanly rather than resolving to garbage.
     */
    @Test
    public void lookupMissingSymbolFails() {
        boolean threw = false;
        try {
            lookupAndBindDefault("nfi_test_symbol_that_does_not_exist", "():void");
        } catch (Throwable ex) {
            // The failed lookup surfaces as an AssertionError wrapping
            // UnknownIdentifierException, so this cannot narrow to Exception.
            threw = true;
        }
        Assert.assertTrue("expected the lookup of an undefined symbol to fail", threw);
    }
}
