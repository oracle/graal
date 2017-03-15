/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotRuntime;
import com.oracle.truffle.sl.SLLanguage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executor;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class SLSharedCodeSeparatedEnvTest implements Executor {

    private ByteArrayOutputStream os1;
    private ByteArrayOutputStream os2;
    private PolyglotEngine e1;
    private PolyglotEngine e2;
    private int instances;
    private int executes;

    @Before
    public void initializeEngines() {
        PolyglotRuntime runtime = PolyglotRuntime.newBuilder().build();

        os1 = new ByteArrayOutputStream();
        os2 = new ByteArrayOutputStream();

        // @formatter:off
        e1 = PolyglotEngine.newBuilder().
            runtime(runtime).
            setOut(os1).
            globalSymbol("extra", 1).
            build();
        e2 = PolyglotEngine.newBuilder().
            runtime(runtime).
            setOut(os2).
            globalSymbol("extra", 2).
            executor(this).
            build();

        instances = SLLanguage.counter;
    }

    @Test
    public void shareCodeUseDifferentOutputStreams() throws Exception {
        Source sayHello = Source.newBuilder(
            "function main() {\n" +
            "  println(\"Ahoj\" + import(\"extra\"));" +
            "}"
        ).mimeType("application/x-sl").name("sayHello.sl").build();
        // @formatter:on

        executes = 0;
        e1.eval(sayHello);
        assertEquals("Ahoj1\n", os1.toString("UTF-8"));
        assertEquals("", os2.toString("UTF-8"));

        e2.eval(sayHello);
        assertEquals("Ahoj1\n", os1.toString("UTF-8"));
        assertEquals("Ahoj2\n", os2.toString("UTF-8"));

        assertEquals("Executor used once", 1, executes);
    }

    @After
    public void cleanUpEngines() {
        e1.dispose();
        e2.dispose();

        assertEquals("One SLLanguage instance created", instances + 1, SLLanguage.counter);
    }

    @Override
    public void execute(Runnable command) {
        executes++;
        command.run();
    }


}
