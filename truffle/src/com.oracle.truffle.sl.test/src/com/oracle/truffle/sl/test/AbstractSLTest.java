/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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
        b.option("sl.UseBytecode", Boolean.toString(mode.isBytecode()));
        if (mode.isBytecode()) {
            if (mode == RunMode.BYTECODE_CACHED) {
                b.option("sl.ForceBytecodeTier", "CACHED");
            } else if (mode == RunMode.BYTECODE_UNCACHED) {
                b.option("sl.ForceBytecodeTier", "UNCACHED");
                if (TruffleTestAssumptions.isOptimizingRuntime()) {
                    // The uncached interpreter compiles to a deopt. Disable compilation because
                    // compilation tests can time out due to lack of progress.
                    b.option("engine.Compilation", "false");
                }
            } else {
                assert mode == RunMode.BYTECODE_DEFAULT;
                b.option("sl.ForceBytecodeTier", "");
            }
        }
        return b;
    }

    protected Context.Builder newContextBuilder(String... languages) {
        var b = Context.newBuilder(languages);
        b.allowExperimentalOptions(true);
        b.option("sl.UseBytecode", Boolean.toString(mode.isBytecode()));
        return b;
    }

}
