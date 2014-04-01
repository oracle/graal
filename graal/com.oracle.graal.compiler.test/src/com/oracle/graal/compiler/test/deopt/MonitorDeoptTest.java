/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
/*
 */
package com.oracle.graal.compiler.test.deopt;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.*;

public final class MonitorDeoptTest extends GraalCompilerTest {

    private enum State {
        INITIAL,
        RUNNING_GRAAL,
        INVALIDATED,
        RUNNING_INTERPRETER,
        TERMINATED
    }

    private static class Monitor {
        private volatile State state = State.INITIAL;

        public synchronized void setState(State newState) {
            state = newState;
            notifyAll();
        }

        public synchronized boolean tryUpdateState(State oldState, State newState) {
            if (state == oldState) {
                state = newState;
                notifyAll();
                return true;
            } else {
                return false;
            }
        }

        public synchronized void waitState(State targetState) throws InterruptedException {
            while (state != targetState) {
                wait();
            }
        }

        public synchronized State getState() {
            return state;
        }

        public synchronized void invalidate(InstalledCode code) {
            state = State.INVALIDATED;
            code.invalidate();
        }
    }

    public static boolean test(Monitor monitor) {
        // initially, we're running as Graal compiled code
        monitor.setState(State.RUNNING_GRAAL);

        for (;;) {
            // wait for the compiled code to be invalidated
            if (monitor.tryUpdateState(State.INVALIDATED, State.RUNNING_INTERPRETER)) {
                break;
            }
        }

        for (int i = 0; i < 500; i++) {
            // wait for the control thread to send the TERMINATED signal
            if (monitor.getState() == State.TERMINATED) {
                return true;
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        }

        // we're running for more than 5 s in the interpreter
        // probably the control thread is deadlocked
        return false;
    }

    private static LoopBeginNode findFirstLoop(StructuredGraph graph) {
        FixedNode node = graph.start();
        for (;;) {
            if (node instanceof LoopBeginNode) {
                return (LoopBeginNode) node;
            } else if (node instanceof FixedWithNextNode) {
                node = ((FixedWithNextNode) node).next();
            } else if (node instanceof AbstractEndNode) {
                node = ((AbstractEndNode) node).merge();
            } else {
                Assert.fail(String.format("unexpected node %s in graph of test method", node));
            }
        }
    }

    /**
     * Remove the safepoint from the first loop in the test method, so only the safepoints on
     * MonitorEnter and MonitorExit remain in the loop. That way, we can make sure it deopts inside
     * the MonitorEnter by invalidating the code while holding the lock.
     */
    private static void removeLoopSafepoint(StructuredGraph graph) {
        LoopBeginNode loopBegin = findFirstLoop(graph);
        for (LoopEndNode end : loopBegin.loopEnds()) {
            end.disableSafepoint();
        }
    }

    @Test
    public void run0() throws Throwable {
        Method method = getMethod("test");

        StructuredGraph graph = parse(method);
        removeLoopSafepoint(graph);

        ResolvedJavaMethod javaMethod = getMetaAccess().lookupJavaMethod(method);
        CompilationResult compilationResult = compile(javaMethod, graph);
        final InstalledCode installedCode = getProviders().getCodeCache().setDefaultMethod(javaMethod, compilationResult);

        final Monitor monitor = new Monitor();

        Thread controlThread = new Thread(new Runnable() {

            public void run() {
                try {
                    // wait for the main thread to start
                    monitor.waitState(State.RUNNING_GRAAL);

                    // invalidate the compiled code while holding the lock
                    // at this point, the compiled method hangs in a MonitorEnter
                    monitor.invalidate(installedCode);

                    // wait for the main thread to continue running in the interpreter
                    monitor.waitState(State.RUNNING_INTERPRETER);

                    // terminate the main thread
                    monitor.setState(State.TERMINATED);
                } catch (InterruptedException e) {
                }
            }
        });

        controlThread.start();

        boolean result = test(monitor);
        Assert.assertTrue(result);
    }
}
