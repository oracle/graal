/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.source.SourceSection;

@Registration(id = StatementProfilerExample.ID, services = Object.class)
public class StatementProfilerExample extends TruffleInstrument {

    public static final String ID = "test-profiler";

    private final Map<SourceSection, Counter> counters = Collections.synchronizedMap(new HashMap<SourceSection, Counter>());

    @Override
    protected void onCreate(Env env) {
        for (Class<? extends ProfilerFrontEnd> frontEnd : installedFrontEnds) {
            try {
                frontEnd.getDeclaredConstructor().newInstance().onAttach(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        env.getInstrumenter().attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new ExecutionEventNodeFactory() {
            public ExecutionEventNode create(final EventContext context) {
                return new ExecutionEventNode() {
                    private final Counter counter = createCounter(context.getInstrumentedSourceSection());

                    @Override
                    public void onReturnValue(VirtualFrame vFrame, Object result) {
                        counter.increment();
                    }
                };
            }
        });
    }

    private Counter createCounter(SourceSection section) {
        CompilerAsserts.neverPartOfCompilation();
        // For a production profiler you might want to differentiate between sources.
        Counter counter = counters.get(section);
        if (counter == null) {
            counter = new Counter();
            counters.put(section, counter);
        }
        return counter;
    }

    public Map<SourceSection, Counter> getCounters() {
        return counters;
    }

    public static final class Counter {

        private int count;

        private Counter() {
        }

        public int getCount() {
            return count;
        }

        void increment() {
            count++;
        }
    }

    // in a production debugger this should be implemented using a proper service provider interface

    private static final List<Class<? extends ProfilerFrontEnd>> installedFrontEnds = new ArrayList<>();

    public static void installFrontEnd(Class<? extends ProfilerFrontEnd> frontEndClass) {
        installedFrontEnds.add(frontEndClass);
    }

    public interface ProfilerFrontEnd {

        void onAttach(StatementProfilerExample example);

    }

}
