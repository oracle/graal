/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.sl.SLLanguage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test of SL instrumentation.
 */
public class SLInstrumentTest {

    @Test
    public void testOutput() throws IOException {
        String code = "function main() {\n" +
                        "  f = fac(5);\n" +
                        "  println(f);\n" +
                        "}\n" +
                        "function fac(n) {\n" +
                        "  println(n);\n" +
                        "  if (n <= 1) {\n" +
                        "    return 1;\n" + // break
                        "  }\n" +
                        "  return n * fac(n - 1);\n" +
                        "}\n";
        String fullOutput = "5\n4\n3\n2\n1\n120\n";
        String fullLines = "[5, 4, 3, 2, 1, 120]";
        // Pure exec:
        Source source = Source.newBuilder(code).name("testing").mimeType(SLLanguage.MIME_TYPE).build();
        ByteArrayOutputStream engineOut = new ByteArrayOutputStream();
        PolyglotEngine engine = PolyglotEngine.newBuilder().setOut(engineOut).build();
        engine.eval(source);
        String engineOutput = fullOutput;
        Assert.assertEquals(engineOutput, engineOut.toString());

        // Check output
        PolyglotEngine.Instrument outInstr = engine.getInstruments().get("testOutputHandlerInstrument");
        outInstr.setEnabled(true);
        TruffleInstrument.Env env = outInstr.lookup(Streams.class).env;
        ByteArrayOutputStream consumedOut = new ByteArrayOutputStream();
        EventBinding<ByteArrayOutputStream> outputConsumerBinding = env.getInstrumenter().attachOutConsumer(consumedOut);
        Assert.assertEquals(0, consumedOut.size());
        engine.eval(source);
        BufferedReader fromOutReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(consumedOut.toByteArray())));
        engineOutput = engineOutput + fullOutput;
        Assert.assertEquals(engineOutput, engineOut.toString());
        Assert.assertTrue(fromOutReader.ready());
        Assert.assertEquals(fullLines, readLinesList(fromOutReader));

        // Check two output readers
        ByteArrayOutputStream consumedOut2 = new ByteArrayOutputStream();
        EventBinding<ByteArrayOutputStream> outputConsumerBinding2 = env.getInstrumenter().attachOutConsumer(consumedOut2);
        Assert.assertEquals(0, consumedOut2.size());
        engine.eval(source);
        fromOutReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(consumedOut.toByteArray())));
        BufferedReader fromOutReader2 = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(consumedOut2.toByteArray())));
        engineOutput = engineOutput + fullOutput;
        Assert.assertEquals(engineOutput, engineOut.toString());
        Assert.assertTrue(fromOutReader.ready());
        Assert.assertTrue(fromOutReader2.ready());
        String fullLines2x = fullLines.substring(0, fullLines.length() - 1) + ", " + fullLines.substring(1);
        Assert.assertEquals(fullLines2x, readLinesList(fromOutReader));
        Assert.assertEquals(fullLines, readLinesList(fromOutReader2));

        // One output reader closes, the other still receives the output
        outputConsumerBinding.dispose();
        consumedOut.reset();
        consumedOut2.reset();
        engine.eval(source);
        engineOutput = engineOutput + fullOutput;
        Assert.assertEquals(engineOutput, engineOut.toString());
        Assert.assertEquals(0, consumedOut.size());
        Assert.assertTrue(consumedOut2.size() > 0);
        fromOutReader2 = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(consumedOut2.toByteArray())));
        Assert.assertEquals(fullLines, readLinesList(fromOutReader2));

        // Remaining closes and pure exec successful:
        consumedOut2.reset();
        outputConsumerBinding2.dispose();
        engine.eval(source);
        engineOutput = engineOutput + fullOutput;
        Assert.assertEquals(engineOutput, engineOut.toString());
        Assert.assertEquals(0, consumedOut.size());
        Assert.assertEquals(0, consumedOut2.size());

        // Add a reader again and disable the instrument:
        env.getInstrumenter().attachOutConsumer(consumedOut);
        outInstr.setEnabled(false);
        engine.eval(source);
        engineOutput = engineOutput + fullOutput;
        Assert.assertEquals(engineOutput, engineOut.toString());
        Assert.assertEquals(0, consumedOut.size());
        Assert.assertEquals(0, consumedOut2.size());
    }

    String readLinesList(BufferedReader br) throws IOException {
        List<String> lines = new ArrayList<>();
        while (br.ready()) {
            String line = br.readLine();
            if (line == null) {
                break;
            }
            lines.add(line);
        }
        return lines.toString();
    }

    @TruffleInstrument.Registration(id = "testOutputHandlerInstrument")
    public static class OutputHandlerInstrument extends TruffleInstrument {

        @Override
        protected void onCreate(final TruffleInstrument.Env env) {
            env.registerService(new Streams(env));
        }
    }

    private static class Streams {

        TruffleInstrument.Env env;

        Streams(TruffleInstrument.Env env) {
            this.env = env;
        }
    }

}
