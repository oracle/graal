/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.test.instrument.InstrumentationTestMode;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class SLJavaInteropTest {

    @Before
    public void before() {
        InstrumentationTestMode.set(true);
    }

    @After
    public void after() {
        InstrumentationTestMode.set(false);
    }

    @Test
    public void asFunction() throws Exception {
        String scriptText = "function main() {\n" + "    println(\"Called!\");\n" + "}\n";
        Source script = Source.fromText(scriptText, "Test").withMimeType("application/x-sl");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(os).build();
        engine.eval(script);
        PolyglotEngine.Value main = engine.findGlobalSymbol("main");
        final Object value = main.get();
        assertTrue("It's truffle object", value instanceof TruffleObject);
        SLFunction rawFunction = main.as(SLFunction.class);
        assertNotNull("One can get the type of the inner Truffle Object", rawFunction);
        Runnable runnable = JavaInterop.asJavaFunction(Runnable.class, (TruffleObject) value);
        runnable.run();

        assertEquals("Called!\n", os.toString("UTF-8"));
    }
}
