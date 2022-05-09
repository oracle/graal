package com.oracle.truffle.regex.dsj;

import com.oracle.truffle.regex.jmh.BenchmarkBase;
import com.oracle.truffle.regex.tregex.test.TRegexTestDummyLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class JavaRegexBenchmark extends BenchmarkBase {

    @State(Scope.Benchmark)
    public static class BenchState {
        String regex = "[Hh]ello [Ww]orld!";
        String input = "hello World!";
        Pattern javaPattern = Pattern.compile(regex);
        Context context;
        Value tregexPattern;

        public BenchState() {
            context = Context.newBuilder().build();
            context.enter();
            tregexPattern = context.eval(TRegexTestDummyLanguage.ID, "Flavor=JavaUtilPattern" + '/' + regex + '/');
        }

        @TearDown
        public void tearDown() {
            context.leave();
            context.close();
        }
    }

    @Benchmark
    public boolean javaPattern(BenchState state) {
        return state.javaPattern.matcher(state.input).find();
    }

    @Benchmark
    public boolean tregex(BenchState state) {
        return state.tregexPattern.invokeMember("exec", state.input, 0).getMember("isMatch").asBoolean();
    }
}
