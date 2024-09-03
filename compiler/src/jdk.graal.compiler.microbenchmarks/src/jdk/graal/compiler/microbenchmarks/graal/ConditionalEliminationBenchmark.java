/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.microbenchmarks.graal;

import org.openjdk.jmh.annotations.Benchmark;

import jdk.graal.compiler.microbenchmarks.graal.util.GraalState;
import jdk.graal.compiler.microbenchmarks.graal.util.GraphState;
import jdk.graal.compiler.microbenchmarks.graal.util.MethodSpec;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;

public class ConditionalEliminationBenchmark extends GraalBenchmark {

    @MethodSpec(declaringClass = ConditionalEliminationBenchmark.class, name = "nullnessSnippet")
    public static class Nullness extends GraphState {
    }

    @SuppressWarnings("unused")
    public static int nullnessSnippet(Object a, Object b, int n, int m) {
        int result = 0;
        if (a == null) {
            if (a == b) {
                if (b == null) {
                    for (int i = 0; i < n; ++i) {
                        if (i % 3 == 0) {
                            break;
                        }
                        result <<= 1;
                        if (i % 7 == 0) {
                            break;
                        }
                    }
                    return 1;
                } else {
                    return -2;
                }
            } else {
                if (b == null) {
                    for (int i = 0; i < n; ++i) {
                        if (i % 4 == 0) {
                            break;
                        }
                        result <<= 1;
                        if (i % 7 == 0) {
                            break;
                        }
                    }
                    return result;
                } else {
                    return 4;
                }
            }
        } else {
            if (a == b) {
                if (b == null) {
                    for (int i = 0; i < n; ++i) {
                        if (i % 5 == 0) {
                            break;
                        }
                        result <<= 1;
                        if (i % 7 == 0) {
                            break;
                        }
                    }
                    return result;
                } else {
                    return 6;
                }
            } else {
                if (b == null) {
                    for (int i = 0; i < n; ++i) {
                        if (i % 6 == 0) {
                            break;
                        }
                        result <<= 1;
                        if (i % 7 == 0) {
                            break;
                        }
                    }
                    return result;
                } else {
                    return 8;
                }
            }
        }
    }

    @Benchmark
    public void nullness(Nullness s, GraalState g) {
        new ConditionalEliminationPhase(CanonicalizerPhase.create(), false).apply(s.graph, g.providers);
    }

    @Benchmark
    public void newDominatorConditionalElimination(Nullness s, GraalState g) {
        new ConditionalEliminationPhase(CanonicalizerPhase.create(), false).apply(s.graph, g.providers);
    }

    @MethodSpec(declaringClass = ConditionalEliminationBenchmark.class, name = "searchSnippet")
    public static class Search extends GraphState {
    }

    static class Entry {
        final String name;

        Entry(String name) {
            this.name = name;
        }
    }

    static class EntryWithNext extends Entry {
        EntryWithNext(String name, Entry next) {
            super(name);
            this.next = next;
        }

        final Entry next;
    }

    public static Entry searchSnippet(Entry start, String name, Entry alternative) {
        Entry current = start;
        do {
            while (current instanceof EntryWithNext) {
                if (name != null && current.name == name) {
                    current = null;
                } else {
                    Entry next = ((EntryWithNext) current).next;
                    current = next;
                }
            }

            if (current != null) {
                if (current.name.equals(name)) {
                    return current;
                }
            }
            if (current == alternative) {
                return null;
            }
            current = alternative;

        } while (true); // TERMINATION ARGUMENT: benchmark
    }

    @Benchmark
    public void search(Search s, GraalState g) {
        new ConditionalEliminationPhase(CanonicalizerPhase.create(), false).apply(s.graph, g.providers);
    }
}
