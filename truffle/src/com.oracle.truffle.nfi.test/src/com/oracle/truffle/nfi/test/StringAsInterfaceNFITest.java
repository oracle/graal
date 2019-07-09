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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess.Implementable;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tck.TruffleRunner;

public class StringAsInterfaceNFITest {
    private static StdLib stdlib;
    private static TruffleObject rawStdLib;

    @ClassRule public static TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule(Context.newBuilder().allowNativeAccess(true));

    @BeforeClass
    public static void loadLibraries() {
        if (TruffleOptions.AOT || NFITest.IS_WINDOWS) {
            // skip these tests on AOT, since JavaInterop is not yet supported
            // skip these tests on Windows, since the default library is different
            return;
        }

        CallTarget load = runWithPolyglot.getTruffleTestEnv().parseInternal(Source.newBuilder("nfi", "default {\n" + //
                        "  strdup(string):string;\n" + //
                        "  malloc(UINT32):pointer;\n" + //
                        "  free(pointer):void;\n" + //
                        "}", "(load default)" //
        ).internal(true).build());
        rawStdLib = (TruffleObject) load.call();
        stdlib = runWithPolyglot.getPolyglotContext().asValue(rawStdLib).as(StdLib.class);
    }

    @Implementable
    interface StdLib {
        Object malloc(int size);

        void free(Object pointer);

        String strdup(String orig);
    }

    @Implementable
    interface Strndup {
        String strndup(String orig, int len);
    }

    private static void assumptions() {
        // host interop is not fully functional on SVM
        Assume.assumeFalse("disable test on AOT", TruffleOptions.AOT);

        // the default posix string functions don't exist on Windows
        Assume.assumeFalse("disable test on Windows", NFITest.IS_WINDOWS);
    }

    @Test
    public void testDuplicateAString() {
        assumptions();
        String copy = stdlib.strdup("Ahoj");
        assertEquals("Ahoj", copy);
    }

    @Test
    public void testAllocAndRelease() {
        assumptions();
        Object mem = stdlib.malloc(512);
        stdlib.free(mem);
    }

    @Test
    public void testAllocAndReleaseWithInvoke() throws Exception {
        assumptions();
        Object mem = NFITest.UNCACHED_INTEROP.invokeMember(rawStdLib, "malloc", 512);
        assertNotNull("some memory allocated", mem);
        NFITest.UNCACHED_INTEROP.invokeMember(rawStdLib, "free", mem);
    }

    @Test
    public void canViewDefaultLibraryAsAnotherInterface() {
        assumptions();
        CallTarget load = runWithPolyglot.getTruffleTestEnv().parseInternal(Source.newBuilder("nfi", "default {\n" + //
                        "  strndup(string, UINT32):string;\n" + //
                        "}", "(load default)" //
        ).internal(true).build());
        Strndup second = runWithPolyglot.getPolyglotContext().asValue(load.call()).as(Strndup.class);

        String copy = stdlib.strdup("Hello World!");
        String hello = second.strndup(copy, 5);
        assertEquals("Hello", hello);
    }

}
