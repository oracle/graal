package com.oracle.graal.microbenchmarks.graal;

import java.util.*;

import org.openjdk.jmh.annotations.*;

import com.oracle.graal.microbenchmarks.graal.util.*;

@Warmup(iterations = 15)
public class FrameStateAssigmentPhaseBenchmark extends GraalBenchmark {

    @MethodSpec(declaringClass = StringTokenizer.class, name = "nextToken", parameters = {String.class})
    public static class StringTokenizedNextToken extends FrameStateAssignmentState {
    }

    @Benchmark
    public void nextToken(StringTokenizedNextToken s) {
        s.phase.apply(s.graph);
    }
}
