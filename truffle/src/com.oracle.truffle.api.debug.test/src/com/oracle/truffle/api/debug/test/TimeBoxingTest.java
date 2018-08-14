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
package com.oracle.truffle.api.debug.test;

import java.util.Timer;
import java.util.TimerTask;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;

public class TimeBoxingTest {

    @Test
    public void testTimeBoxing() throws Exception {
        final Context context = Context.create();
        Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(LOOP(infinity,STATEMENT))", "NotEnoughTime").buildLiteral();
        Debugger debugger = context.getEngine().getInstruments().get("debugger").lookup(Debugger.class);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                debugger.startSession(new SuspendedCallback() {
                    public void onSuspend(SuspendedEvent event) {
                        event.prepareKill();
                    }
                }).suspendNextExecution();
            }
        }, 0, 10);

        try {
            context.eval(source); // throws KillException, wrapped by PolyglotException
            Assert.fail();
        } catch (PolyglotException error) {
            Assert.assertTrue(error.isCancelled());
            Assert.assertTrue(error.getMessage(), error.getMessage().contains("Execution cancelled by a debugging session."));
        }
        timer.cancel();
    }

}
