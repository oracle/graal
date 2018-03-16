/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import static com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.EXPRESSION;
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
        SourceSectionFilter filter = builder.tagIs(EXPRESSION).build();
        Instrumenter instrumenter = env.getInstrumenter();
        instrumenter.attachExecutionEventFactory(filter,
                        new CoverageEventFactory(env));
    }

    private class CoverageEventFactory implements ExecutionEventNodeFactory {

        private final Env env;

        CoverageEventFactory(final Env env) {
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
