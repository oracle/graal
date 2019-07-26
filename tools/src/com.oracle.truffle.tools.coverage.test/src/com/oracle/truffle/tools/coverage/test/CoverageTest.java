/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.coverage.test;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import com.oracle.truffle.tools.coverage.Coverage;
import com.oracle.truffle.tools.coverage.CoverageTracker;
import com.oracle.truffle.tools.coverage.impl.CoverageInstrument;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;

public class CoverageTest {

    protected Source makeSource(String s) {
        return Source.newBuilder(InstrumentationTestLanguage.ID, s, "test").buildLiteral();
    }

    @Test
    public void testSamplerJson() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        Context context = Context.newBuilder().in(System.in).out(out).err(err).option(CoverageInstrument.ID, "true").option("cpusampler.Output", "json").build();
        Source defaultSourceForSampling = makeSource("ROOT(\n" +
                        "DEFINE(foo,ROOT(SLEEP(1))),\n" +
                        "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo))))),\n" +
                        "DEFINE(baz,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar))))),\n" +
                        "CALL(bar)\n" +
                        ")");
        context.eval(defaultSourceForSampling);
        final CoverageTracker tracker = CoverageInstrument.getTracker(context.getEngine());
        final Map<com.oracle.truffle.api.source.Source, Coverage.PerSource> coverage = tracker.getCoverage().getCoverage();
        Assert.assertEquals(1, coverage.size());
        for (Map.Entry<com.oracle.truffle.api.source.Source, Coverage.PerSource> entry : coverage.entrySet()) {
            Assert.assertEquals(2, entry.getValue().getLoadedStatements().size());
            Assert.assertEquals(1, entry.getValue().getCoveredStatements().size());
            Assert.assertEquals(4, entry.getValue().getLoadedRoots().size());
            Assert.assertEquals(3, entry.getValue().getCoveredRoots().size());
        }
    }
}
