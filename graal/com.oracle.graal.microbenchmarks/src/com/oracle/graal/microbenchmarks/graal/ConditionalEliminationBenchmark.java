package com.oracle.graal.microbenchmarks.graal;

import org.openjdk.jmh.annotations.*;

import com.oracle.graal.microbenchmarks.graal.util.*;
import com.oracle.graal.phases.common.*;

public class ConditionalEliminationBenchmark extends GraalBenchmark {

    @MethodSpec(declaringClass = ConditionalEliminationBenchmark.class, name = "nullnessSnippet")
    public static class Nullness extends GraphState {
    }

    @SuppressWarnings("unused")
    public static int nullnessSnippet(Object a, Object b) {
        if (a == null) {
            if (a == b) {
                if (b == null) {
                    return 1;
                } else {
                    return -2;
                }
            } else {
                if (b == null) {
                    return -3;
                } else {
                    return 4;
                }
            }
        } else {
            if (a == b) {
                if (b == null) {
                    return -5;
                } else {
                    return 6;
                }
            } else {
                if (b == null) {
                    return 7;
                } else {
                    return 8;
                }
            }
        }
    }

    @Benchmark
    @Warmup(iterations = 20)
    public void nullness(Nullness s, GraalState g) {
        new ConditionalEliminationPhase().apply(s.graph);
    }

    @MethodSpec(declaringClass = ConditionalEliminationBenchmark.class, name = "searchSnippet")
    public static class Search extends GraphState {
    }

    static class Entry {
        final String name;

        public Entry(String name) {
            this.name = name;
        }
    }

    static class EntryWithNext extends Entry {
        public EntryWithNext(String name, Entry next) {
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

        } while (true);
    }

    @Benchmark
    public void search(Search s, GraalState g) {
        new ConditionalEliminationPhase().apply(s.graph);
    }
}
