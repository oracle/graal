/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck;

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.vm.EventConsumer;
import com.oracle.truffle.api.vm.PolyglotEngine;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Example of an execution shielded by a timeout. Used in {@link SuspendedEvent#prepareKill()}
 * javadoc.
 */
// BEGIN: com.oracle.truffle.tck.ExecWithTimeOut#tckSnippets
class ExecWithTimeOut extends EventConsumer<SuspendedEvent> //
                implements Runnable {

    boolean pauseRequested;
    boolean pauseResult;

    ExecWithTimeOut() {
        super(SuspendedEvent.class);
    }

    private void initTimeOut(ScheduledExecutorService executor) {
        // schedule pausing actions after a timeout
        executor.schedule(this, 10, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        // if the script is running too long
        pauseRequested = true;
        final Debugger debugger = getDebugger();
        // request pause to generate SuspendedEvent
        pauseResult = debugger.pause();
    }

    @Override
    protected void on(SuspendedEvent event) {
        if (pauseRequested) {
            // when execution stops either debug or kill it:
            event.prepareKill();
        }
    }

    void executeWithTimeOut(
                    ScheduledExecutorService executor, // run us later
                    PolyglotEngine.Value function, // unknown function
                    Object parameter // parameter for the function
    ) throws IOException {
        initTimeOut(executor);
        Throwable caught = null;
        try {
            // execute with timer on
            function.execute(parameter);
        } catch (ThreadDeath t) {
            caught = t;
        }
        assertTrue("Pause requested", pauseRequested);
        assertTrue("Pause performed successfully", pauseResult);
        assertNotNull("Execution ended with throwable", caught);
        assertEquals("Our Exception", // prepareKill produces
                        "com.oracle.truffle.api.debug.KillException", //
                        caught.getClass().getName());
    }
    // FINISH: com.oracle.truffle.tck.ExecWithTimeOut#tckSnippets

    public void tckSnippets() {
    }

    PolyglotEngine engine;
    Debugger foundDebugger;

    Debugger getDebugger() {
        if (foundDebugger == null) {
            foundDebugger = Debugger.find(engine);
        }
        return foundDebugger;
    }

}
