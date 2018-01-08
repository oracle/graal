/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;

/**
 * Testing of concurrent debugging with multiple threads.
 */
public class ConcurrentDebuggingTest {

    @Test
    public void testConcurrentBreakpoints() {
        int numThreads = 100;
        String code = "ROOT(DEFINE(foo,\n" +
                        "  STATEMENT\n" +
                        "),\n" +
                        "LOOP(" + numThreads + ", SPAWN(foo)),\n" +
                        "JOIN())";
        final Source source = Source.create(InstrumentationTestLanguage.ID, code);
        Breakpoint breakpoint = Breakpoint.newBuilder(source.getURI()).lineIs(2).build();
        Context context = Context.newBuilder().allowCreateThread(true).build();
        Debugger debugger = context.getEngine().getInstruments().get("debugger").lookup(Debugger.class);
        AtomicInteger hits = new AtomicInteger(0);
        try (DebuggerSession session = debugger.startSession((SuspendedEvent event) -> {
            assertEquals(1, event.getBreakpoints().size());
            assertEquals(breakpoint, event.getBreakpoints().get(0));
            hits.incrementAndGet();
        })) {
            session.install(breakpoint);
            context.eval(source);
        }
        assertEquals(numThreads, hits.get());
    }

}
