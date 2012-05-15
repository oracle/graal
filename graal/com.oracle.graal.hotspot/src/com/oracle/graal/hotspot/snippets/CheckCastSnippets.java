/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.snippets;
import static com.oracle.graal.hotspot.snippets.CheckCastSnippets.Counter.*;

import java.io.*;
import java.util.*;

import sun.misc.*;

import com.oracle.graal.compiler.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Fold;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.snippets.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

/**
 * Snippets used for lowering {@link CheckCastNode}s.
 */
public class CheckCastSnippets implements SnippetsInterface {

    /**
     * Checks that a given object is null or is a subtype of a given type.
     *
     * @param hub the hub of the type being checked against
     * @param object the object whose type is being checked against {@code hub}
     * @param hintHubs the hubs of objects that have been profiled during previous executions
     * @param hintsAreExact specifies if {@code hintHubs} contains all subtypes of {@code hub}
     * @return {@code object} if the type check succeeds
     * @throws ClassCastException if the type check fails
     */
    @Snippet
    public static Object checkcast(Object hub, Object object, Object[] hintHubs, boolean hintsAreExact) {
        if (object == null) {
            return object;
        }
        Object objectHub = UnsafeLoadNode.load(object, 0, hubOffset(), CiKind.Object);
        // if we get an exact match: succeed immediately
        for (int i = 0; i < hintHubs.length; i++) {
            Object hintHub = hintHubs[i];
            if (hintHub == objectHub) {
                return object;
            }
        }
        if (hintsAreExact || !TypeCheckSlowPath.check(objectHub, hub)) {
            DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
        }
        return object;
    }

    /**
     * Counters for the various code paths through a type check.
     */
    public enum Counter {
        hintsHit("hit a hint type"),
        hintsMissed("missed the hint types"),
        exact("tested type is (statically) final"),
        noHints_class("profile information is not used (test type is a class)"),
        noHints_iface("profile information is not used (test type is an interface)"),
        noHints_unknown("test type is not a compile-time constant"),
        isNull("object tested is null"),
        exception("type test failed with a ClassCastException");

        final String description;
        final int index;
        long count;

        private Counter(String desc) {
            this.description = desc;
            this.index = ordinal();
        }

        @Fold
        static int countOffset() {
            try {
                return (int) Unsafe.getUnsafe().objectFieldOffset(Counter.class.getDeclaredField("count"));
            } catch (Exception e) {
                throw new GraalInternalError(e);
            }
        }

        /**
         * Increments this counter if counters are enabled. The body of this method has been carefully crafted
         * such that it contains no safepoints and no calls, neither of which are permissible in a snippet.
         * Also, increments are not guaranteed to be atomic but that's acceptable for a counter like this.
         */
        void inc() {
            if (ENABLED) {
                DirectObjectStoreNode.store(this, countOffset(), 0, count + 1);
            }
        }

        static final Counter[] VALUES = values();
        static final boolean ENABLED = GraalOptions.CheckcastCounters;
    }

    /**
     * @see #checkcast(Object, Object, Object[], boolean)
     */
    @Snippet
    public static Object checkcastWithCounters(Object hub, Object object, Object[] hintHubs, boolean hintsAreExact, Counter noHintsCounter) {
        if (object == null) {
            isNull.inc();
            return object;
        }
        Object objectHub = UnsafeLoadNode.load(object, 0, hubOffset(), CiKind.Object);
        if (hintHubs.length == 0) {
            noHintsCounter.inc();
            if (!TypeCheckSlowPath.check(objectHub, hub)) {
                exception.inc();
                DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
            }
        } else {
            // if we get an exact match: succeed immediately
            for (int i = 0; i < hintHubs.length; i++) {
                Object hintHub = hintHubs[i];
                if (hintHub == objectHub) {
                    if (hintsAreExact) {
                        exact.inc();
                    } else {
                        hintsHit.inc();
                    }
                    return object;
                }
            }
            if (!hintsAreExact) {
                if (!TypeCheckSlowPath.check(objectHub, hub)) {
                    exception.inc();
                    DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
                } else {
                    hintsMissed.inc();
                }
            } else {
                exception.inc();
                DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
            }
        }
        return object;
    }

    @Fold
    private static int hubOffset() {
        return CompilerImpl.getInstance().getConfig().hubOffset;
    }

    @Fold
    private static boolean isInterface(RiResolvedType type) {
        return type.isInterface();
    }

    public static void printCounter(PrintStream out, Counter c, long total) {
        double percent = total == 0D ? 0D : ((double) (c.count * 100)) / total;
        out.println(String.format("%16s: %5.2f%%%10d  // %s", c.name(), percent, c.count, c.description));
    }

    public static void printCounters(PrintStream out) {
        if (!Counter.ENABLED) {
            return;
        }
        Counter[] counters = Counter.values();
        Arrays.sort(counters, new Comparator<Counter>() {
            @Override
            public int compare(Counter o1, Counter o2) {
                if (o1.count > o2.count) {
                    return -1;
                } else if (o2.count > o1.count) {
                    return 1;
                }
                return 0;
            }

        });

        long total = 0;
        for (Counter c : counters) {
            total += c.count;
        }

        out.println();
        out.println("** Checkcast counters **");
        for (Counter c : counters) {
            printCounter(out, c, total);
        }
    }
}
