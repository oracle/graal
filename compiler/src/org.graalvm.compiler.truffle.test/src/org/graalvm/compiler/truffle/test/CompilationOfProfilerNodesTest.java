package org.graalvm.compiler.truffle.test;


import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.tools.profiler.CPUSampler;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class CompilationOfProfilerNodesTest extends TestWithSynchronousCompiling {

    private Context context;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private CPUSampler sampler;

    @Before
    public void setup() {
        context = Context.newBuilder().out(out).err(err).build();
        sampler = context.getEngine().getInstruments().get("cpusampler").lookup(CPUSampler.class);
    }

    String defaultSourceForSampling = "ROOT(" +
            "DEFINE(foo,ROOT(BLOCK(SLEEP(1),INVALIDATE)))," +
            "DEFINE(bar,ROOT(BLOCK(STATEMENT,CALL(foo))))," +
            "LOOP(20, CALL(bar))" +
            ")";


    @Test
    public void testInvalidationsDontPoluteShadowStack() {
        // Test assumes that foo will be inlined into bar
        try(TruffleCompilerOptions.TruffleOptionsOverrideScope inline = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleFunctionInlining, true)) {
            sampler.setStackLimit(3);
            sampler.setCollecting(true);
            final Source source = Source.create(InstrumentationTestLanguage.ID, defaultSourceForSampling);
            context.eval(InstrumentationTestLanguage.ID, source.getCharacters());

            Assert.assertFalse(sampler.hasStackOverflowed());
        }
    }
}
