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
//        String regex = "[Hh]ello [Ww]orld!";
//        String regex = "(?<x>abc|def)=\\k<x>";
        String regex = "(?<x>abc){3}";
//        String input = "hello World!";
//        String input = "def=def";
        String input = "abcabcabc";

        // Benchmarks ausweiten, Daten sammeln, backtracken (* gefolgt mit anderen Ausdruck ".*xy.*ab" mit "ababxyxyxyxyababxyxyxy" (automatenbasiert vs backtracker))
        // gegenüberstellen mit HelloWorld (backtracked nicht)
        // Backtracker vs Backtracker
        // lookaround and lookahead regex (?=asdfsadf) (tregex schafft so gut wie alles außer bei negative lookahead (backtracker))
        // lookbehind (?<=s\w{1,7})t (tregex fällt auf backtracker zurück)
        // selber Regex mit größeren Input und vergleichen
        // mx --dy graal/compiler --jdk jvmci benchmark jmh-dist:TREGEX_UNIT_TESTS -- -D"polyglot.log.regex.MatchingStrategy.level=ALL" -- com.oracle.truffle.regex.dsj.JavaRegexBenchmark
        // Benchmarks erweitern mit der Abfrage an der Capture Groups
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

    // see if it makes a difference to check for capture groups or just "isMatch"
    // add another @Benchmark method
    @Benchmark
    public boolean tregex(BenchState state) {
        return state.tregexPattern.invokeMember("exec", state.input, 0).getMember("isMatch").asBoolean();
//
//        Value mem = state.tregexPattern.invokeMember("exec", state.input, 0);
//        if(mem.getMember("isMatch").asBoolean()) {
//            // print as well to illustrate
//            int n = state.tregexPattern.getMember("groupCount").asInt();    // number of groups
//            int result = 0;
//            for (int i = 0; i < n; i++) {
//                int start = mem.invokeMember("getStart", 0).asInt();
//                int end = mem.invokeMember("getEnd", 0).asInt();
//                result += start + end;
//            }
//            return result;
//        } else
//            return -1;
    }
}
