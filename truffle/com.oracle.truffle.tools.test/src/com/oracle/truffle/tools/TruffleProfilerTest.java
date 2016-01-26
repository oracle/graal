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
package com.oracle.truffle.tools;

import java.io.IOException;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.AbstractInstrumentationTest;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.TruffleProfiler.Counter;
import com.oracle.truffle.tools.TruffleProfiler.Counter.TimeKind;
import com.oracle.truffle.tools.TruffleProfiler.TestHook;

public class TruffleProfilerTest extends AbstractInstrumentationTest {

    private TruffleProfiler profiler;

    @Before
    public void setupProfiler() throws IOException {
        TruffleProfiler.setTestHook(new TestHook() {
            public void onCreate(TruffleProfiler p) {
                profiler = p;
            }
        });
        assertEvalOut("", ""); // ensure profiler gets loaded
        Assert.assertNotNull(profiler);
    }

    @After
    public void clearTestHook() {
        // clear up otherwise test execution of others get affected
        TruffleProfiler.setTestHook(null);
    }

    @Test
    public void testInvocationCounts() throws IOException {
        // Checkstyle: stop
        Source source = lines("ROOT(", // 0-126
                        "DEFINE(foo,ROOT(EXPRESSION)),", // 17-17+16
                        "DEFINE(bar,ROOT(LOOP(10  , CALL(foo)))),", // 47-47+25
                        "DEFINE(baz,ROOT(LOOP(10  , CALL(bar)))),", // 86-86+25
                        "CALL(baz),CALL(baz)", //
                        ")");
        // Checkstyle: resume
        Map<SourceSection, Counter> counters = profiler.getCounters();
        Assert.assertEquals(0, counters.size());
        run(source);

        counters = profiler.getCounters();
        Assert.assertEquals(4, counters.size());

        Counter root = counters.get(source.createSection(null, 0, 140));
        Counter leaf = counters.get(source.createSection(null, 17, 16));
        Counter callfoo = counters.get(source.createSection(null, 47, 27));
        Counter callbar = counters.get(source.createSection(null, 88, 27));

        Assert.assertNotNull(root);
        Assert.assertNotNull(leaf);
        Assert.assertNotNull(callfoo);
        Assert.assertNotNull(callbar);

        final TimeKind testTimeKind = TimeKind.INTERPRETED_AND_COMPILED;
        Assert.assertEquals(1L, root.getInvocations(testTimeKind));
        Assert.assertEquals(200L, leaf.getInvocations(testTimeKind));
        Assert.assertEquals(20L, callfoo.getInvocations(testTimeKind));
        Assert.assertEquals(2L, callbar.getInvocations(testTimeKind));

        engine.getInstruments().get(TruffleProfiler.ID).setEnabled(false);

        run(source);

        Assert.assertEquals(1L, root.getInvocations(testTimeKind));
        Assert.assertEquals(200L, leaf.getInvocations(testTimeKind));
        Assert.assertEquals(20L, callfoo.getInvocations(testTimeKind));
        Assert.assertEquals(2L, callbar.getInvocations(testTimeKind));

        engine.getInstruments().get(TruffleProfiler.ID).setEnabled(true);

        counters = profiler.getCounters();
        Assert.assertEquals(0, counters.size());

        for (int i = 0; i < 10000; i++) {
            run(source);
        }

        root = counters.get(source.createSection(null, 0, 140));
        leaf = counters.get(source.createSection(null, 17, 16));
        callfoo = counters.get(source.createSection(null, 47, 27));
        callbar = counters.get(source.createSection(null, 88, 27));

        Assert.assertEquals(10000L, root.getInvocations(testTimeKind));
        Assert.assertEquals(2000000L, leaf.getInvocations(testTimeKind));
        Assert.assertEquals(200000L, callfoo.getInvocations(testTimeKind));
        Assert.assertEquals(20000L, callbar.getInvocations(testTimeKind));

        engine.dispose();
        engine = null;

        String o = getOut();
        Assert.assertTrue(o != null && o.trim().length() > 0);
    }
}
