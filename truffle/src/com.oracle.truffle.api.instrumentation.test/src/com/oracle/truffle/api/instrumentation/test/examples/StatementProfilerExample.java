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
