/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test.examples;

import java.io.IOException;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.test.AbstractInstrumentationTest;
import com.oracle.truffle.api.instrumentation.test.examples.StatementProfilerExample.Counter;
import com.oracle.truffle.api.instrumentation.test.examples.StatementProfilerExample.ProfilerFrontEnd;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.polyglot.Source;

public final class StatementProfilerExampleTest extends AbstractInstrumentationTest {

    private static StatementProfilerExample profiler;

    public static class TestFrontEnd implements ProfilerFrontEnd {
        public void onAttach(StatementProfilerExample example) {
            StatementProfilerExampleTest.profiler = example;
        }
    }

    @BeforeClass
    public static void installFrontEnd() {
        StatementProfilerExample.installFrontEnd(TestFrontEnd.class);
    }

    @Before
    public void setupProfiler() throws IOException {
        assureEnabled(engine.getInstruments().get(StatementProfilerExample.ID));
        // force profiler frontend attachement
        run(lines(""));
    }

    @Test
    public void testStatements() throws IOException {
        Source source = lines("ROOT(", // 1
                        "STATEMENT(EXPRESSION", // 2
                        ", STATEMENT),", // 3
                        "STATEMENT)"); // 4

        Map<SourceSection, Counter> counters = profiler.getCounters();
        for (int i = 0; i < 1000; i++) {
            if (i == 0) {
                Assert.assertTrue(counters.isEmpty());
            } else {
                assertLine(counters, 2, i);
                assertLine(counters, 3, i);
                assertLine(counters, 4, i);
            }
            run(source);
        }
    }

    @Test
    public void testLoopZero() throws IOException {
        Source source = lines("LOOP(0", // 1
                        ",STATEMENT(", // 2
                        /**/"STATEMENT),", // 3
                        "STATEMENT)"); // 4
        Map<SourceSection, Counter> counters = profiler.getCounters();
        for (int i = 0; i < 10; i++) {
            run(source);

            // never actually executed
            Assert.assertTrue(counters.isEmpty());
        }
    }

    @Test
    public void testLoopHundreds() throws IOException {
        Source source = lines("LOOP(100", // 1
                        ",STATEMENT(", // 2
                        /**/"STATEMENT),", // 3
                        "STATEMENT)"); // 4
        Map<SourceSection, Counter> counters = profiler.getCounters();
        for (int i = 0; i < 10; i++) {
            if (i == 0) {
                Assert.assertTrue(counters.isEmpty());
            } else {
                assertLine(counters, 2, i * 100);
                assertLine(counters, 3, i * 100);
                assertLine(counters, 4, i * 100);
            }
            run(source);
        }
    }

    private static void assertLine(Map<SourceSection, Counter> counters, int line, int count) {
        boolean found = false;
        for (SourceSection section : counters.keySet()) {
            if (section.getStartLine() == line) {
                Assert.assertFalse(found);
                found = true;
                Assert.assertEquals(count, counters.get(section).getCount());
            }
        }
        Assert.assertTrue(found);
    }

}
