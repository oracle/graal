package com.oracle.truffle.tools.coverage.impl;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.coverage.Coverage;

import java.io.PrintStream;

class CoverageCLI {
    
    static void handleOutput(TruffleInstrument.Env env, Coverage coverage) {
        PrintStream out = new PrintStream(env.out());
        out.println(coverage.getCoveredRootNodes().size() / coverage.getLoadedRootNodes().size());
    }
}
