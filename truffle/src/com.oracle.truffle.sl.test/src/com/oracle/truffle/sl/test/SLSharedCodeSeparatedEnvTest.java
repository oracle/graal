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
package com.oracle.truffle.sl.test;

import static com.oracle.truffle.sl.test.SLJavaInteropTest.toUnixString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.PolyglotAccess;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.sl.SLLanguage;

public class SLSharedCodeSeparatedEnvTest {

    private ByteArrayOutputStream osRuntime;
    private ByteArrayOutputStream os1;
    private ByteArrayOutputStream os2;
    private Engine engine;
    private Context e1;
    private Context e2;

    @Before
    public void initializeEngines() {
        osRuntime = new ByteArrayOutputStream();
        engine = Engine.newBuilder().out(osRuntime).err(osRuntime).build();

        os1 = new ByteArrayOutputStream();
        os2 = new ByteArrayOutputStream();

        int instances = SLLanguage.counter;
        // @formatter:off
        e1 = Context.newBuilder("sl").engine(engine).out(os1).allowPolyglotAccess(PolyglotAccess.ALL).build();
        e1.getPolyglotBindings().putMember("extra", 1);
        e2 = Context.newBuilder("sl").engine(engine).out(os2).allowPolyglotAccess(PolyglotAccess.ALL).build();
        e2.getPolyglotBindings().putMember("extra", 2);
        e1.initialize("sl");
        e2.initialize("sl");
        assertEquals("One SLLanguage instance created", instances + 1, SLLanguage.counter);
    }

    @After
    public void closeEngines() {
        engine.close();
    }

    @Test
    public void shareCodeUseDifferentOutputStreams() throws Exception {

        String sayHello =
            "function main() {\n" +
            "  println(\"Ahoj\" + import(\"extra\"));" +
            "}";
        // @formatter:on

        e1.eval("sl", sayHello);
        assertEquals("Ahoj1\n", toUnixString(os1));
        assertEquals("", toUnixString(os2));

        e2.eval("sl", sayHello);
        assertEquals("Ahoj1\n", toUnixString(os1));
        assertEquals("Ahoj2\n", toUnixString(os2));
    }

    @Test
    public void instrumentsSeeOutputOfBoth() throws Exception {
        Instrument outInstr = e2.getEngine().getInstruments().get("captureOutput");
        ByteArrayOutputStream outConsumer = outInstr.lookup(ByteArrayOutputStream.class);
        assertNotNull("Stream capturing is ready", outConsumer);

        String sayHello = "function main() {\n" +
                        "  println(\"Ahoj\" + import(\"extra\"));" +
                        "}";
        // @formatter:on

        e1.eval("sl", sayHello);
        assertEquals("Ahoj1\n", toUnixString(os1));
        assertEquals("", toUnixString(os2));

        e2.eval("sl", sayHello);
        assertEquals("Ahoj1\n", toUnixString(os1));
        assertEquals("Ahoj2\n", toUnixString(os2));

        engine.close();

        assertEquals("Output of both contexts and instruments is capturable",
                        "initializingOutputCapture\n" +
                                        "Ahoj1\n" +
                                        "Ahoj2\n" +
                                        "endOfOutputCapture\n",
                        toUnixString(outConsumer));

        assertEquals("Output of instrument goes not to os runtime if specified otherwise",
                        "initializingOutputCapture\n" + "endOfOutputCapture\n",
                        toUnixString(osRuntime));
    }

    @TruffleInstrument.Registration(id = "captureOutput", services = ByteArrayOutputStream.class)
    public static class CaptureOutput extends TruffleInstrument {
        private EventBinding<ByteArrayOutputStream> binding;

        @Override
        protected void onCreate(final TruffleInstrument.Env env) {
            final ByteArrayOutputStream capture = new ByteArrayOutputStream() {
                @Override
                public void write(byte[] b) throws IOException {
                    super.write(b);
                }

                @Override
                public synchronized void write(byte[] b, int off, int len) {
                    super.write(b, off, len);
                }

                @Override
                public synchronized void write(int b) {
                    super.write(b);
                }
            };
            binding = env.getInstrumenter().attachOutConsumer(capture);
            env.registerService(capture);
            try {
                env.out().write("initializingOutputCapture\n".getBytes("UTF-8"));
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        protected void onDispose(Env env) {
            try {
                env.out().write("endOfOutputCapture\n".getBytes("UTF-8"));
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            binding.dispose();
        }
    }
}
