/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
