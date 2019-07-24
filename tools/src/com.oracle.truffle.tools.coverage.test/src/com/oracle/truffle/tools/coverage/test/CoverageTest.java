package com.oracle.truffle.tools.coverage.test;

import java.io.ByteArrayOutputStream;

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
        final Coverage coverage = tracker.getCoverage();
        Assert.assertEquals(2, coverage.getLoadedStatements().size());
        Assert.assertEquals(1, coverage.getCoveredStatements().size());
        Assert.assertEquals(4, coverage.getLoadedRootNodes().size());
        Assert.assertEquals(3, coverage.getCoveredRootNodes().size());
    }
}
