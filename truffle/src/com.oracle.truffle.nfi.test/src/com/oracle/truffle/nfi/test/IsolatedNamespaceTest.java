/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

/**
 * NFI provides a native isolated namespace on Linux (via dlmopen); one per NFI context.
 *
 * This test ensures that the same library can be loaded twice: in the global namespace and in the
 * isolated namespace; and that the libraries are effectively isolated.
 */
public class IsolatedNamespaceTest extends NFITest {

    private static Object globalLibrary;
    private static Object isolatedLibrary;

    @Test
    public void testIsolatedNamespace() throws UnsupportedMessageException, ArityException, UnsupportedTypeException {
        // get_and_set mutates some static state.
        Object globalGetAndSet = lookupAndBind(globalLibrary, "get_and_set", "(sint32) : sint32");
        Object isolatedGetAndSet = lookupAndBind(isolatedLibrary, "get_and_set", "(sint32) : sint32");
        UNCACHED_INTEROP.execute(globalGetAndSet, 123);
        UNCACHED_INTEROP.execute(isolatedGetAndSet, 456);
        Assert.assertEquals(123, (int) UNCACHED_INTEROP.execute(globalGetAndSet, 321));
        Assert.assertEquals(456, (int) UNCACHED_INTEROP.execute(isolatedGetAndSet, 654));
    }

    @BeforeClass
    public static void loadIsolatedLibraries() {
        // The library is loaded in the global namespace.
        globalLibrary = loadLibrary("load '" + System.getProperty("native.isolation.test.lib") + "'");
        try {
            // And loaded again in the isolated namespace.
            isolatedLibrary = loadLibrary("load(ISOLATED_NAMESPACE) '" + System.getProperty("native.isolation.test.lib") + "'");
        } catch (IllegalArgumentException iea) {
            Assume.assumeNoException("Cannot load test library in isolated namespace", iea);
        }
    }

    @AfterClass
    public static void releaseLibraries() {
        globalLibrary = null;
        isolatedLibrary = null;
    }
}
