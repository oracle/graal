package com.oracle.truffle.sl.test;

import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class AbstractSLTest {

    public enum RunMode {
        AST,
        BYTECODE_UNCACHED,
        BYTECODE_DEFAULT,
        BYTECODE_CACHED;

        public boolean isBytecode() {
            switch (this) {
                case BYTECODE_CACHED:
                case BYTECODE_UNCACHED:
                case BYTECODE_DEFAULT:
                    return true;
                default:
                    return false;
            }
        }
    }

    @Parameters(name = "{0}")
    public static List<RunMode> getModes() {
        return List.of(RunMode.values());
    }

    @Parameter(0) public RunMode mode;

    protected Engine.Builder newEngineBuilder(String... languages) {
        var b = Engine.newBuilder(languages);
        b.allowExperimentalOptions(true);
        if (mode.isBytecode()) {
            b.option("sl.UseBytecode", "true");
            if (mode == RunMode.BYTECODE_CACHED) {
                b.option("sl.ForceBytecodeTier", "UNCACHED");
            } else if (mode == RunMode.BYTECODE_UNCACHED) {
                b.option("sl.ForceBytecodeTier", "CACHED");
            } else {
                assert mode == RunMode.BYTECODE_DEFAULT;
                b.option("sl.ForceBytecodeTier", "");
            }
        } else {
            b.option("sl.UseBytecode", "false");
        }
        return b;
    }

    protected Context.Builder newContextBuilder(String... languages) {
        var b = Context.newBuilder(languages);
        b.allowExperimentalOptions(true);
        if (mode.isBytecode()) {
            b.option("sl.UseBytecode", "true");
        } else {
            b.option("sl.UseBytecode", "false");
        }
        return b;
    }

}
