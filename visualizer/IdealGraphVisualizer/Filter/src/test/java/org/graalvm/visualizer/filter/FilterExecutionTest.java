/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.filter;

import static org.junit.Assert.*;

import java.util.concurrent.CancellationException;

import org.graalvm.visualizer.graph.Diagram;
import org.junit.Test;
import org.openide.cookies.OpenCookie;
import org.openide.util.Lookup;

import jdk.graal.compiler.graphio.parsing.model.ChangedEvent;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.Properties;

/**
 * @author sdedic
 */
public class FilterExecutionTest {

    static class F implements Filter {
        private ChangedEvent<Filter> change = new ChangedEvent<>(this);

        @Override
        public String getName() {
            return "Test";
        }

        @Override
        public void applyWith(FilterEnvironment env) {
        }

        @Override
        public boolean cancel(FilterEnvironment d) {
            return false;
        }

        @Override
        public OpenCookie getEditor() {
            return null;
        }

        @Override
        public ChangedEvent<Filter> getChangedEvent() {
            return change;
        }

        @Override
        public Properties getProperties() {
            return Properties.newProperties();
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }
    }

    private Object envValue;

    /**
     * Checks that the filter is executed, gets environment.
     */
    @Test
    public void testProcessIsolated() {
        FilterChain ch = new FilterChain();
        InputGraph gr = InputGraph.createTestGraph("Ahoj");
        Diagram d = Diagram.createDiagram(gr, "testDiagram");

        ch.addFilter(new F() {
            @Override
            public void applyWith(FilterEnvironment env) {
                assertNotNull(env);
                assertSame(d, env.getDiagram());
                envValue = env.getScriptEnvironment().getValue("test");
                env.getScriptEnvironment().setValue("test", "failed");
            }

        });
        ch.apply(d);
        assertNull(envValue);

        // try the second time, new environment should be passed
        ch.apply(d);
        assertNull(envValue);
    }

    /**
     * Checks that two filters in sequence get the same environment
     */
    @Test
    public void testProcessCommonEnvironment() {
        FilterChain ch = new FilterChain();
        InputGraph gr = InputGraph.createTestGraph("Ahoj");
        Diagram d = Diagram.createDiagram(gr, "testDiagram");

        ch.addFilter(new F() {
            @Override
            public void applyWith(FilterEnvironment env) {
                assertNotNull(env);
                assertSame(d, env.getDiagram());
                Object en = env.getScriptEnvironment().getValue("test");
                assertNull(en);
                env.getScriptEnvironment().setValue("test", envValue = "failed");
            }

        });
        ch.addFilter(new F() {
            @Override
            public void applyWith(FilterEnvironment env) {
                assertNotNull(env);
                assertSame(d, env.getDiagram());
                Object o = env.getScriptEnvironment().getValue("test");
                assertSame(envValue, o);
            }

        });

        ch.apply(d);
        ch.apply(d);
    }

    /**
     * Checks that filter invoked from another filter
     * accesses the same environment
     */
    @Test
    public void testNestedFilterAccessesEnvironment() {
        FilterChain ch = new FilterChain();
        InputGraph gr = InputGraph.createTestGraph("Ahoj");
        Diagram d = Diagram.createDiagram(gr, "testDiagram");

        ch.addFilter(new F() {
            @Override
            public void applyWith(FilterEnvironment env) {
                assertNotNull(env);
                assertSame(d, env.getDiagram());
                Object en = env.getScriptEnvironment().getValue("test");
                assertNull(en);
                envValue = "failed";

                env.getScriptEnvironment().setValue("test", envValue);

                Filter nested = new F() {
                    @Override
                    public void applyWith(FilterEnvironment env) {
                        assertSame(envValue, env.getScriptEnvironment().getValue("test"));
                    }

                };
                nested.apply(env.getDiagram());
            }
        });

        Filters.apply(ch, d);
    }

    FilterExecution execution;
    int cancelCount;

    /**
     * Checks that cancel will stop processing in the chain
     */
    @Test
    public void testCancelStopsChain() {
        FilterChain ch = new FilterChain();
        InputGraph gr = InputGraph.createTestGraph("Ahoj");
        Diagram d = Diagram.createDiagram(gr, "testDiagram");

        ch.addFilter(new F() {
            @Override
            public void applyWith(FilterEnvironment env) {
                assertNotNull(env);
                // should be cancelled from outside, but...
                execution.cancel();
            }

            @Override
            public boolean cancel(FilterEnvironment env) {
                cancelCount++;
                return true;
            }
        });
        ch.addFilter(new F() {
            @Override
            public void applyWith(FilterEnvironment env) {
                fail("Should have not been called");
            }

            @Override
            public boolean cancel(FilterEnvironment env) {
                cancelCount++;
                return true;
            }
        });

        execution = Filters.applyWithCancel(ch, d, null);
        try {
            execution.process();
            fail("execution should fail with FilterCancelledException");
        } catch (FilterCanceledException ex) {
            // expected
        }
        assertEquals("All filters in chain should get cancel", 2, cancelCount);
    }

    int pos;

    /**
     * Checks that various ways of cancellation all throw the same
     * {@link FilterCanceledException}
     */
    @Test
    public void testInterruptThrowsCancelException() {
        class CF extends F {
            int n;

            public CF(int n) {
                this.n = n;
            }

            @Override
            public void applyWith(FilterEnvironment env) {
                if (pos > n) {
                    fail("Should have been cancelled");
                } else if (pos < n) {
                    return;
                }
                switch (n) {
                    case 1:
                        throw new FilterCanceledException(null);
                    case 2:
                        throw new CancellationException("Ahoj");
                    case 3:
                        Thread.currentThread().interrupt();
                        break;
                }
            }
        }

        FilterChain ch = new FilterChain();
        ch.addFilter(new CF(1));
        ch.addFilter(new CF(2));
        ch.addFilter(new CF(3));

        InputGraph gr = InputGraph.createTestGraph("Ahoj");
        Diagram d = Diagram.createDiagram(gr, "testDiagram");

        pos = 1;

        try {
            ch.apply(d);
            fail("Should be cancelled");
        } catch (FilterCanceledException ex) {
            // OK
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable ex) {
            fail("FilterCancelledException expected");
        }
    }

    @Test
    public void testCancelNestedFilter() {
        FilterChain ch = new FilterChain();
        InputGraph gr = InputGraph.createTestGraph("Ahoj");
        Diagram d = Diagram.createDiagram(gr, "testDiagram");
        Diagram d2 = Diagram.createDiagram(gr, "testDiagram");
        ch.addFilter(new F() {
            @Override
            public void applyWith(FilterEnvironment env) {
                assertNotNull(env);
                assertSame(d, env.getDiagram());
                Object en = env.getScriptEnvironment().getValue("test");
                assertNull(en);
                envValue = "failed";

                env.getScriptEnvironment().setValue("test", envValue);

                Filter nested = new F() {
                    @Override
                    public void applyWith(FilterEnvironment env) {
                        execution.cancel();
                    }

                };
                nested.apply(d2);
            }
        });
        ch.addFilter(new F() {
            @Override
            public void apply(Diagram d) {
                fail("Should have been cancelled");
            }
        });
        execution = Filters.applyWithCancel(ch, d, null);
        try {
            execution.process();
            fail("Should be cancelled");
        } catch (FilterCanceledException ex) {
            // expected
            assertTrue(execution.isCancelled());
        }
    }
}
