package org.graalvm.compiler.truffle.compiler.benchmark;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.runtime.SLFunction;

public class SLPartialEvaluationBenchmarks {

    public static class ExampleOneBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected OptimizedCallTarget createCallTarget() {
            PolyglotEngine engine = PolyglotEngine.newBuilder().build();
            engine.eval(Source.newBuilder("function plus(x, y) { return x + y; }").name("plus.sl").mimeType(SLLanguage.MIME_TYPE).build());
            SLFunction function = engine.findGlobalSymbol("plus").as(SLFunction.class);
            return (OptimizedCallTarget) function.getCallTarget();
        }

    }

    public static class ExampleTwoBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected OptimizedCallTarget createCallTarget() {
            PolyglotEngine engine = PolyglotEngine.newBuilder().build();
            engine.eval(Source.newBuilder("function minus(x, y) { return x - y; }").name("minus.sl").mimeType(SLLanguage.MIME_TYPE).build());
            SLFunction function = engine.findGlobalSymbol("minus").as(SLFunction.class);
            return (OptimizedCallTarget) function.getCallTarget();
        }

    }

}
