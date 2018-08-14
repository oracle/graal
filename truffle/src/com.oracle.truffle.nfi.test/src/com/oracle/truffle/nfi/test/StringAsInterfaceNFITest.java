/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.graalvm.polyglot.Context;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tck.TruffleRunner;

/**
 * This test is to be removed with the PolyglotEngine. Truffle NFI is currently no longer accessible
 * from the embedder API.
 */
public class StringAsInterfaceNFITest {
    private static StdLib stdlib;
    private static TruffleObject rawStdLib;

    @ClassRule public static TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule(Context.newBuilder().allowNativeAccess(true));

    @BeforeClass
    public static void loadLibraries() {
        if (TruffleOptions.AOT) {
            // skip these tests on AOT, since JavaInterop is not yet supported
            return;
        }

        CallTarget load = runWithPolyglot.getTruffleTestEnv().parse(Source.newBuilder("nfi", "default {\n" + //
                        "  strdup(string):string;\n" + //
                        "  malloc(UINT32):pointer;\n" + //
                        "  free(pointer):void;\n" + //
                        "}", "(load default)" //
        ).build());
        rawStdLib = (TruffleObject) load.call();
        stdlib = runWithPolyglot.getPolyglotContext().asValue(rawStdLib).as(StdLib.class);
    }

    interface StdLib {
        long malloc(int size);

        void free(long pointer);

        String strdup(String orig);
    }

    interface Strndup {
        String strndup(String orig, int len);
    }

    @Test
    public void testDuplicateAString() {
        Assume.assumeFalse("disable test on AOT", TruffleOptions.AOT);
        String copy = stdlib.strdup("Ahoj");
        assertEquals("Ahoj", copy);
    }

    @Test
    public void testAllocAndRelease() {
        Assume.assumeFalse("disable test on AOT", TruffleOptions.AOT);
        long mem = stdlib.malloc(512);
        stdlib.free(mem);
    }

    @Test
    public void testAllocAndReleaseWithInvoke() throws Exception {
        Assume.assumeFalse("disable test on AOT", TruffleOptions.AOT);
        Object mem = ForeignAccess.sendInvoke(Message.INVOKE.createNode(), rawStdLib, "malloc", 512);
        assertNotNull("some memory allocated", mem);
        ForeignAccess.sendInvoke(Message.INVOKE.createNode(), rawStdLib, "free", mem);
    }

    @Test
    public void canViewDefaultLibraryAsAnotherInterface() {
        Assume.assumeFalse("disable test on AOT", TruffleOptions.AOT);
        CallTarget load = runWithPolyglot.getTruffleTestEnv().parse(Source.newBuilder("nfi", "default {\n" + //
                        "  strndup(string, UINT32):string;\n" + //
                        "}", "(load default)" //
        ).build());
        Strndup second = runWithPolyglot.getPolyglotContext().asValue(load.call()).as(Strndup.class);

        String copy = stdlib.strdup("Hello World!");
        String hello = second.strndup(copy, 5);
        assertEquals("Hello", hello);
    }

}
