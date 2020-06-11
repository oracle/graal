package com.oracle.truffle.tools.warmup.test;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.tools.warmup.impl.WarmupEstimatorInstrument;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class WarmupEstimatorTest {
    private static final String defaultSourceString = "ROOT(\n" +
                    "DEFINE(foo,ROOT(STATEMENT)),\n" +
                    "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo))))),\n" +
                    "DEFINE(neverCalled,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar))))),\n" +
                    "CALL(bar)\n" +
                    ")";
    private static final Source defaultSource = makeSource(defaultSourceString);
    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;

    @Before
    public void setUp() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
    }

    private static Source makeSource(String s) {
        return Source.newBuilder(InstrumentationTestLanguage.ID, s, "test").buildLiteral();
    }

    @Test
    public void testBasic() {
        try (Context context = defaultContext().option(WarmupEstimatorInstrument.ID + ".RootNames", "foo").build()) {
            context.eval(defaultSource);
        }
        final String output = out.toString();
        assertContains(output, "Best time       |");
        assertContains(output, "Best iter       |");
        assertContains(output, "Epsilon         | 1.05");
        assertContains(output, "Peak Start Iter |");
        assertContains(output, "Peak Start Time |");
        assertContains(output, "Warmup time     |");
        assertContains(output, "Warmup cost     |");
        assertContains(output, "Iterations      | 10");
    }

    @Test
    public void testMultiRoot() {
        try (Context context = defaultContext().option(WarmupEstimatorInstrument.ID + ".RootNames", "foo,bar").build()) {
            context.eval(defaultSource);
        }
        final String output = out.toString();
        assertContains(output, "foo");
        assertContains(output, "bar");
    }

    @Test
    public void testRawOutput() {
        try (Context context = defaultContext().option(WarmupEstimatorInstrument.ID + ".RootNames", "foo").option(WarmupEstimatorInstrument.ID + ".Output", "raw").build()) {
            context.eval(defaultSource);
        }
        final String output = out.toString();
        Assert.assertTrue(output.startsWith("["));
        Assert.assertTrue(output.endsWith("]"));
        assertContains(output, "{");
        assertContains(output, "}");
        Assert.assertEquals(11, output.split(",").length);
    }

    private void assertContains(String output, String expected) {
        Assert.assertTrue(output.contains(expected));
    }

    private Context.Builder defaultContext() {
        return Context.newBuilder().in(System.in).out(out).err(err).option(WarmupEstimatorInstrument.ID, "true").allowExperimentalOptions(true);
    }
}
