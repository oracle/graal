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
package com.oracle.truffle.tools.test;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.test.AbstractInstrumentationTest;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.Profiler;
import com.oracle.truffle.tools.Profiler.Counter;

public class ProfilerTest extends AbstractInstrumentationTest {

    private Profiler profiler;

    // Checkstyle: stop
    private final Source source = lines("ROOT(", // 0-126
                    "DEFINE(foo,ROOT(EXPRESSION)),", // 17-17+16
                    "DEFINE(bar,ROOT(LOOP(10  , CALL(foo)))),", // 47-47+25
                    "DEFINE(baz,ROOT(LOOP(10  , CALL(bar)))),", // 86-86+25
                    "CALL(baz),CALL(baz)", //
                    ")");
    // Checkstyle: resume

    @Before
    public void setupProfiler() {
        profiler = Profiler.find(engine);
        Assert.assertNotNull(profiler);
    }

    @Test
    public void testInvocationCounts() throws IOException {

        profiler.setCollecting(true);
        profiler.setTiming(false);
        Map<SourceSection, Counter> counters = profiler.getCounters();
        Assert.assertEquals(0, counters.size());
        Assert.assertTrue(profiler.isCollecting());
        Assert.assertFalse(profiler.isTiming());
        Assert.assertFalse(profiler.hasData());
        Assert.assertNull(profiler.getMimeTypes());

        assertEvalOut(source, "");

        counters = profiler.getCounters();
        Assert.assertEquals(4, counters.size());
        Assert.assertTrue(profiler.isCollecting());
        Assert.assertFalse(profiler.isTiming());
        Assert.assertTrue(profiler.hasData());

        final SourceSection rootSection = source.createSection(0, 139);
        final SourceSection leafSection = source.createSection(17, 16);
        final SourceSection callfooSection = source.createSection(47, 27);
        final SourceSection callbarSection = source.createSection(88, 27);
        Counter root = counters.get(rootSection);
        Counter leaf = counters.get(leafSection);
        Counter callfoo = counters.get(callfooSection);
        Counter callbar = counters.get(callbarSection);

        Assert.assertNotNull(root);
        Assert.assertNotNull(leaf);
        Assert.assertNotNull(callfoo);
        Assert.assertNotNull(callbar);

        final Counter.TimeKind testTimeKind = Counter.TimeKind.INTERPRETED_AND_COMPILED;
        Assert.assertEquals(1L, root.getInvocations(testTimeKind));
        Assert.assertEquals(200L, leaf.getInvocations(testTimeKind));
        Assert.assertEquals(20L, callfoo.getInvocations(testTimeKind));
        Assert.assertEquals(2L, callbar.getInvocations(testTimeKind));

        profiler.setCollecting(false);

        Assert.assertFalse(profiler.isCollecting());
        Assert.assertFalse(profiler.isTiming());
        Assert.assertTrue(profiler.hasData());

        assertEvalOut(source, "");

        Assert.assertFalse(profiler.isCollecting());
        Assert.assertFalse(profiler.isTiming());
        Assert.assertTrue(profiler.hasData());

        Assert.assertEquals(1L, root.getInvocations(testTimeKind));
        Assert.assertEquals(200L, leaf.getInvocations(testTimeKind));
        Assert.assertEquals(20L, callfoo.getInvocations(testTimeKind));
        Assert.assertEquals(2L, callbar.getInvocations(testTimeKind));

        profiler.clearData();

        Assert.assertFalse(profiler.isCollecting());
        Assert.assertFalse(profiler.isTiming());
        Assert.assertFalse(profiler.hasData());

        Assert.assertEquals(0L, root.getInvocations(testTimeKind));
        Assert.assertEquals(0L, leaf.getInvocations(testTimeKind));
        Assert.assertEquals(0L, callfoo.getInvocations(testTimeKind));
        Assert.assertEquals(0L, callbar.getInvocations(testTimeKind));

        profiler.setCollecting(true);

        Assert.assertTrue(profiler.isCollecting());
        Assert.assertFalse(profiler.isTiming());
        Assert.assertFalse(profiler.hasData());

        assertEvalOut(source, "");

        Assert.assertTrue(profiler.isCollecting());
        Assert.assertFalse(profiler.isTiming());
        Assert.assertTrue(profiler.hasData());

        Assert.assertEquals(1L, root.getInvocations(testTimeKind));
        Assert.assertEquals(200L, leaf.getInvocations(testTimeKind));
        Assert.assertEquals(20L, callfoo.getInvocations(testTimeKind));
        Assert.assertEquals(2L, callbar.getInvocations(testTimeKind));

        profiler.clearData();

        Assert.assertTrue(profiler.isCollecting());
        Assert.assertFalse(profiler.isTiming());
        Assert.assertFalse(profiler.hasData());

        counters = profiler.getCounters();
        Assert.assertEquals(4, counters.size());

        for (int i = 0; i < 10000; i++) {
            assertEvalOut(source, "");
        }

        Assert.assertTrue(profiler.isCollecting());
        Assert.assertFalse(profiler.isTiming());
        Assert.assertTrue(profiler.hasData());

        root = counters.get(rootSection);
        leaf = counters.get(leafSection);
        callfoo = counters.get(callfooSection);
        callbar = counters.get(callbarSection);

        Assert.assertEquals(10000L, root.getInvocations(testTimeKind));
        Assert.assertEquals(2000000L, leaf.getInvocations(testTimeKind));
        Assert.assertEquals(200000L, callfoo.getInvocations(testTimeKind));
        Assert.assertEquals(20000L, callbar.getInvocations(testTimeKind));

        profiler.printHistograms(new PrintStream(out));
        String o = getOut();
        Assert.assertTrue(o != null && o.trim().length() > 0);

        engine.dispose();
        engine = null;
    }

    @Test
    public void testTimingEnabled() throws IOException {

        profiler.setCollecting(true);
        profiler.setTiming(false);
        Map<SourceSection, Counter> counters = profiler.getCounters();
        Assert.assertEquals(0, counters.size());
        Assert.assertTrue(profiler.isCollecting());
        Assert.assertFalse(profiler.isTiming());
        Assert.assertFalse(profiler.hasData());
        Assert.assertNull(profiler.getMimeTypes());

        assertEvalOut(source, "");

        counters = profiler.getCounters();
        Assert.assertEquals(4, counters.size());
        Assert.assertTrue(profiler.isCollecting());
        Assert.assertFalse(profiler.isTiming());
        Assert.assertTrue(profiler.hasData());

        final SourceSection rootSection = source.createSection(0, 139);
        final SourceSection leafSection = source.createSection(17, 16);
        final SourceSection callfooSection = source.createSection(47, 27);
        final SourceSection callbarSection = source.createSection(88, 27);
        Counter root = counters.get(rootSection);
        Counter leaf = counters.get(leafSection);
        Counter callfoo = counters.get(callfooSection);
        Counter callbar = counters.get(callbarSection);

        Assert.assertNotNull(root);
        Assert.assertNotNull(leaf);
        Assert.assertNotNull(callfoo);
        Assert.assertNotNull(callbar);

        final Counter.TimeKind testTimeKind = Counter.TimeKind.INTERPRETED_AND_COMPILED;

        Assert.assertEquals(0, root.getTotalTime(testTimeKind));
        Assert.assertEquals(0, leaf.getTotalTime(testTimeKind));
        Assert.assertEquals(0, callfoo.getTotalTime(testTimeKind));
        Assert.assertEquals(0, callbar.getTotalTime(testTimeKind));

        Assert.assertEquals(0, root.getSelfTime(testTimeKind));
        Assert.assertEquals(0, leaf.getSelfTime(testTimeKind));
        Assert.assertEquals(0, callfoo.getSelfTime(testTimeKind));
        Assert.assertEquals(0, callbar.getSelfTime(testTimeKind));

        profiler.setTiming(true);
        Assert.assertTrue(profiler.isTiming());
        Assert.assertTrue(profiler.hasData());

        assertEvalOut(source, "");

        Assert.assertTrue(root.getTotalTime(testTimeKind) > 0);
        Assert.assertTrue(leaf.getTotalTime(testTimeKind) > 0);
        Assert.assertTrue(callfoo.getTotalTime(testTimeKind) > 0);
        Assert.assertTrue(callbar.getTotalTime(testTimeKind) > 0);

        Assert.assertTrue(root.getSelfTime(testTimeKind) > 0);
        Assert.assertTrue(leaf.getSelfTime(testTimeKind) > 0);
        Assert.assertTrue(callfoo.getSelfTime(testTimeKind) > 0);
        Assert.assertTrue(callbar.getSelfTime(testTimeKind) > 0);

        profiler.setTiming(false);
        Assert.assertFalse(profiler.isTiming());
        Assert.assertTrue(profiler.hasData());

        assertEvalOut(source, "");

        Assert.assertTrue(root.getTotalTime(testTimeKind) > 0);
        Assert.assertTrue(leaf.getTotalTime(testTimeKind) > 0);
        Assert.assertTrue(callfoo.getTotalTime(testTimeKind) > 0);
        Assert.assertTrue(callbar.getTotalTime(testTimeKind) > 0);

        Assert.assertTrue(root.getSelfTime(testTimeKind) > 0);
        Assert.assertTrue(leaf.getSelfTime(testTimeKind) > 0);
        Assert.assertTrue(callfoo.getSelfTime(testTimeKind) > 0);
        Assert.assertTrue(callbar.getSelfTime(testTimeKind) > 0);

        profiler.clearData();

        assertEvalOut(source, "");

        Assert.assertFalse(profiler.isTiming());
        Assert.assertTrue(profiler.hasData());

        Assert.assertEquals(0, root.getTotalTime(testTimeKind));
        Assert.assertEquals(0, leaf.getTotalTime(testTimeKind));
        Assert.assertEquals(0, callfoo.getTotalTime(testTimeKind));
        Assert.assertEquals(0, callbar.getTotalTime(testTimeKind));

        Assert.assertEquals(0, root.getSelfTime(testTimeKind));
        Assert.assertEquals(0, leaf.getSelfTime(testTimeKind));
        Assert.assertEquals(0, callfoo.getSelfTime(testTimeKind));
        Assert.assertEquals(0, callbar.getSelfTime(testTimeKind));
    }

    @Test
    public void testSetMIME() {

        Assert.assertNull(profiler.getMimeTypes());

        String[] mimeTypes = new String[]{"testMIMEType"};
        profiler.setMimeTypes(mimeTypes);
        Assert.assertArrayEquals(mimeTypes, profiler.getMimeTypes());

        mimeTypes = new String[]{"testMIMEType1", "testMIMEType2"};
        profiler.setMimeTypes(mimeTypes);
        Assert.assertArrayEquals(mimeTypes, profiler.getMimeTypes());

        mimeTypes = new String[0];
        profiler.setMimeTypes(mimeTypes);
        Assert.assertNull(profiler.getMimeTypes());

        mimeTypes = null;
        profiler.setMimeTypes(mimeTypes);
        Assert.assertNull(profiler.getMimeTypes());
    }

    @Test
    public void testRootName() throws IOException {

        profiler.setCollecting(true);
        profiler.setTiming(false);
        Map<SourceSection, Counter> counters = profiler.getCounters();
        Assert.assertEquals(0, counters.size());
        Assert.assertTrue(profiler.isCollecting());
        Assert.assertFalse(profiler.isTiming());
        Assert.assertFalse(profiler.hasData());
        Assert.assertNull(profiler.getMimeTypes());

        run(source);

        counters = profiler.getCounters();
        final SourceSection rootSection = source.createSection(0, 139);
        final SourceSection leafSection = source.createSection(17, 16);
        final SourceSection callfooSection = source.createSection(47, 27);
        final SourceSection callbarSection = source.createSection(88, 27);
        Counter root = counters.get(rootSection);
        Counter leaf = counters.get(leafSection);
        Counter callfoo = counters.get(callfooSection);
        Counter callbar = counters.get(callbarSection);

        Assert.assertEquals("", root.getName());
        Assert.assertEquals("foo", leaf.getName());
        Assert.assertEquals("bar", callfoo.getName());
        Assert.assertEquals("baz", callbar.getName());
    }

    @Test
    public void testMIMEFilter() throws IOException {

        profiler.setCollecting(true);
        profiler.setTiming(false);
        Assert.assertNull(profiler.getMimeTypes());
        Assert.assertFalse(profiler.hasData());

        assertEvalOut(source, "");

        Assert.assertTrue(profiler.hasData());

        profiler.clearData();
        profiler.setMimeTypes(new String[]{InstrumentationTestLanguage.MIME_TYPE});

        Assert.assertFalse(profiler.hasData());

        assertEvalOut(source, "");

        Assert.assertTrue(profiler.hasData());

        profiler.clearData();
        profiler.setMimeTypes(new String[]{"foo", InstrumentationTestLanguage.MIME_TYPE});

        Assert.assertFalse(profiler.hasData());

        assertEvalOut(source, "");

        Assert.assertTrue(profiler.hasData());

        profiler.clearData();
        profiler.setMimeTypes(new String[]{"foo"});

        Assert.assertFalse(profiler.hasData());

        assertEvalOut(source, "");

        Assert.assertFalse(profiler.hasData());

        profiler.setMimeTypes(null);

        assertEvalOut(source, "");

        Assert.assertTrue(profiler.hasData());
    }

    @Test(expected = IllegalStateException.class)
    public void testDisposeError() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        profiler.setCollecting(true);

        Method m = Profiler.class.getDeclaredMethod("dispose");
        m.setAccessible(true);
        m.invoke(profiler);

        profiler.setCollecting(false);
    }
}
