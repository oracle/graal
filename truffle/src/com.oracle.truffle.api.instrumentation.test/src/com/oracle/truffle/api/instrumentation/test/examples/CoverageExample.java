/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test.examples;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Example for simple and optimize able version of an expression coverage instrument. Parts that are
 * already covered or have never been instrumented can be optimized without peak performance
 * overhead.
 *
 * Covered statements are printed to the instrument stream which should demonstrate an alternate way
 * of communication from the instrument to the user.
 */
// BEGIN: com.oracle.truffle.api.instrumentation.test.examples.CoverageExample
@Registration(id = CoverageExample.ID, services = Object.class)
public final class CoverageExample extends TruffleInstrument {

    public static final String ID = "test-coverage";

    private final Set<SourceSection> coverage = new HashSet<>();

    @Override
    protected void onCreate(final Env env) {
        SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder();
        SourceSectionFilter filter = builder.tagIs(ExpressionTag.class).build();
        Instrumenter instrumenter = env.getInstrumenter();
        instrumenter.attachExecutionEventFactory(filter,
                        new CoverageExampleEventFactory(env));
    }

    private class CoverageExampleEventFactory
                    implements ExecutionEventNodeFactory {

        private final Env env;

        CoverageExampleEventFactory(final Env env) {
            this.env = env;
        }

        public ExecutionEventNode create(final EventContext ec) {
            final PrintStream out = new PrintStream(env.out());
            return new ExecutionEventNode() {
                @CompilationFinal private boolean visited;

                @Override
                public void onReturnValue(VirtualFrame vFrame, Object result) {
                    if (!visited) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        visited = true;
                        SourceSection src = ec.getInstrumentedSourceSection();
                        out.print(src.getCharIndex() + " ");
                        coverage.add(src);
                    }
                }
            };
        }
    }

}
// END: com.oracle.truffle.api.instrumentation.test.examples.CoverageExample
