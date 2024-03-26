package com.oracle.truffle.sl.test;

import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AbstractSLTest {

    public enum RunMode {
        AST,
        BYTECODE
    }

    @Parameters(name = "{0}")
    public static List<RunMode> getModes() {
        return List.of(RunMode.values());
    }

    @Parameter(0) public RunMode mode;

    protected Engine.Builder newEngineBuilder(String... languages) {
        var b = Engine.newBuilder(languages);
        b.allowExperimentalOptions(true);
        if (mode == RunMode.BYTECODE) {
            b.option("sl.UseBytecode", "true");
        } else {
            b.option("sl.UseBytecode", "false");
        }
        return b;
    }

    protected Context.Builder newContextBuilder(String... languages) {
        var b = Context.newBuilder(languages);
        b.allowExperimentalOptions(true);
        if (mode == RunMode.BYTECODE) {
            b.option("sl.UseBytecode", "true");
        } else {
            b.option("sl.UseBytecode", "false");
        }
        return b;
    }

}
