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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.tools.coverage.CoverageTracker;
import com.oracle.truffle.tools.coverage.RootCoverage;
import com.oracle.truffle.tools.coverage.SectionCoverage;
import com.oracle.truffle.tools.coverage.SourceCoverage;
import com.oracle.truffle.tools.coverage.impl.CoverageInstrument;

public class CoverageTest {

    protected Source makeSource(String s) {
        return Source.newBuilder(InstrumentationTestLanguage.ID, s, "test").buildLiteral();
    }

    @Test
    public void testBasic() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        Context context = Context.newBuilder().in(System.in).out(out).err(err).option(CoverageInstrument.ID, "true").option("cpusampler.Output", "json").build();
        Source source = makeSource("ROOT(\n" +
                        "DEFINE(foo,ROOT(SLEEP(1))),\n" +
                        "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo))))),\n" +
                        "DEFINE(neverCalled,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar))))),\n" +
                        "CALL(bar)\n" +
                        ")");
        context.eval(source);
        final CoverageTracker tracker = CoverageInstrument.getTracker(context.getEngine());
        final SourceCoverage[] coverage = tracker.getCoverage();
        Assert.assertEquals("Unexpected number of sources in coverage", 1, coverage.length);
        Assert.assertEquals("Unexpected number of roots in coverage", 4, coverage[0].getRoots().length);
        for (RootCoverage root : coverage[0].getRoots()) {
            switch (root.getName()) {
                case "foo":
                    assertCoverage(root, 0, 0, "foo", true);
                    break;
                case "bar":
                    assertCoverage(root, 1, 1, "bar", true);
                    break;
                case "neverCalled":
                    assertCoverage(root, 1, 0, "neverCalled", false);
                    break;
                case "":
                    assertCoverage(root, 0, 0, "", true);
                    break;
            }
        }
    }

    private static void assertCoverage(RootCoverage root, int expectedLoaded, int expectedCovered, String name, boolean covered) {
        Assert.assertEquals("Wrong root name!", name, root.getName());
        Assert.assertEquals("Unexpected \"" + name + "\" root coverage", covered, root.isCovered());
        final SectionCoverage[] sectionCoverage = root.getSectionCoverage();
        Assert.assertEquals("Unexpected number of statements loaded ", expectedLoaded, sectionCoverage.length);
        Assert.assertEquals("Unexpected number of statements covered", expectedCovered, countCovered(sectionCoverage));
    }

    private static int countCovered(SectionCoverage[] sectionCoverage) {
        int count = 0;
        for (SectionCoverage coverage : sectionCoverage) {
            if (coverage.isCovered()) {
                count++;
            }
        }
        return count;
    }
}
