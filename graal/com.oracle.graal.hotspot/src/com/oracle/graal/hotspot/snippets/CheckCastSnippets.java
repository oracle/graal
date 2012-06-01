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
import static com.oracle.graal.hotspot.snippets.ArrayCopySnippets.*;
import static com.oracle.graal.hotspot.snippets.CheckCastSnippets.Counter.*;
import static com.oracle.graal.hotspot.snippets.CheckCastSnippets.TemplateFlag.*;
import static com.oracle.graal.snippets.SnippetTemplate.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import sun.misc.*;

import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.cri.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Fold;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.ri.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.nodes.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.criutils.*;

/**
 * Snippets used for implementing the type test of a checkcast instruction.
 *
 * The test first checks against the profiled types (if any) and then implements the
 * checks described in paper <a href="http://dl.acm.org/citation.cfm?id=583821">
 * Fast subtype checking in the HotSpot JVM</a> by Cliff Click and John Rose.
 */
public class CheckCastSnippets implements SnippetsInterface {

    /**
     * Type test used when the type being tested against is a final type.
     */
    @Snippet
    public static Object checkcastExact(Object object, Object exactHub, boolean checkNull) {
        if (checkNull && object == null) {
            return object;
        }
        Object objectHub = UnsafeLoadNode.loadObject(object, 0, hubOffset(), true);
        if (objectHub != exactHub) {
            DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
        }
        return object;
    }

    /**
     * Type test used when the type being tested against is a restricted primary type.
     *
     * This test ignores use of hints altogether as the display-based type check only
     * involves one extra load where the second load should hit the same cache line as the
     * first.
     */
    @Snippet
    public static Object checkcastPrimary(Object hub, Object object, boolean checkNull, int superCheckOffset) {
        if (checkNull && object == null) {
            return object;
        }
        Object objectHub = UnsafeLoadNode.loadObject(object, 0, hubOffset(), true);
        if (UnsafeLoadNode.loadObject(objectHub, 0, superCheckOffset, true) != hub) {
            DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
        }
        return object;
    }

    /**
     * Type test used when the type being tested against is a restricted secondary type.
     */
    @Snippet
    public static Object checkcastSecondary(Object hub, Object object, Object[] hintHubs, boolean checkNull) {
        if (checkNull && object == null) {
            return object;
        }
        Object objectHub = UnsafeLoadNode.loadObject(object, 0, hubOffset(), true);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hintHubs.length; i++) {
            Object hintHub = hintHubs[i];
            if (hintHub == objectHub) {
                return object;
            }
        }
        if (!checkSecondarySubType(hub, objectHub)) {
            DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
        }
        return object;
    }

    /**
     * Type test used when the type being tested against is not known at compile time (e.g. the type test
     * in an object array store check).
     */
    @Snippet
    public static Object checkcastUnknown(Object hub, Object object, Object[] hintHubs, boolean checkNull) {
        if (checkNull && object == null) {
            return object;
        }
        Object objectHub = UnsafeLoadNode.loadObject(object, 0, hubOffset(), true);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hintHubs.length; i++) {
            Object hintHub = hintHubs[i];
            if (hintHub == objectHub) {
                return object;
            }
        }
        if (!checkUnknownSubType(hub, objectHub)) {
            DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
        }
        return object;
    }

    //This is used instead of a Java array read to avoid the array bounds check.
    static Object loadNonNullObjectElement(Object array, int index) {
        return UnsafeLoadNode.loadObject(array, arrayBaseOffset(CiKind.Object), index * arrayIndexScale(CiKind.Object), true);
    }

    static boolean checkSecondarySubType(Object t, Object s) {
        // if (S.cache == T) return true
        if (UnsafeLoadNode.loadObject(s, 0, secondarySuperCacheOffset(), true) == t) {
            return true;
        }

        // if (T == S) return true
        if (s == t) {
            return true;
        }

        // if (S.scan_s_s_array(T)) { S.cache = T; return true; }
        Object[] secondarySupers = UnsafeCastNode.cast(UnsafeLoadNode.loadObject(s, 0, secondarySupersOffset(), true), Object[].class);

        for (int i = 0; i < secondarySupers.length; i++) {
            if (t == loadNonNullObjectElement(secondarySupers, i)) {
                DirectObjectStoreNode.store(s, secondarySuperCacheOffset(), 0, t);
                return true;
            }
        }

        return false;
    }

    static boolean checkUnknownSubType(Object t, Object s) {
        // int off = T.offset
        int superCheckOffset = UnsafeLoadNode.load(t, 0, superCheckOffsetOffset(), CiKind.Int);

        // if (T = S[off]) return true
        if (UnsafeLoadNode.loadObject(s, 0, superCheckOffset, true) == t) {
            return true;
        }

        // if (off != &cache) return false
        if (superCheckOffset != secondarySuperCacheOffset()) {
            return false;
        }

        // if (T == S) return true
        if (s == t) {
            return true;
        }

        // if (S.scan_s_s_array(T)) { S.cache = T; return true; }
        Object[] secondarySupers = UnsafeCastNode.cast(UnsafeLoadNode.loadObject(s, 0, secondarySupersOffset(), true), Object[].class);
        for (int i = 0; i < secondarySupers.length; i++) {
            if (t == loadNonNullObjectElement(secondarySupers, i)) {
                DirectObjectStoreNode.store(s, secondarySuperCacheOffset(), 0, t);
                return true;
            }
        }

        return false;
    }

    /**
     * Counters for the various code paths through a type check.
     */
    public enum Counter {
        hintsHit("hit a hint type"),
        hintsMissed("missed the hint types"),
        exactType("tested type is (statically) final"),
        noHints("profile information is not used"),
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
         * Also, increments are not guaranteed to be atomic which is acceptable for a counter.
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
     * Type test used when {@link GraalOptions#CheckcastCounters} is enabled.
     */
    @Snippet
    public static Object checkcastCounters(Object hub, Object object, Object[] hintHubs, boolean hintsAreExact) {
        if (object == null) {
            isNull.inc();
            return object;
        }
        Object objectHub = UnsafeLoadNode.loadObject(object, 0, hubOffset(), true);
        if (hintHubs.length == 0) {
            noHints.inc();
            if (!checkUnknownSubType(hub, objectHub)) {
                exception.inc();
                DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
            }
        } else {
            // if we get an exact match: succeed immediately
            ExplodeLoopNode.explodeLoop();
            for (int i = 0; i < hintHubs.length; i++) {
                Object hintHub = hintHubs[i];
                if (hintHub == objectHub) {
                    if (hintsAreExact) {
                        exactType.inc();
                    } else {
                        hintsHit.inc();
                    }
                    return object;
                }
            }
            if (!hintsAreExact) {
                if (!checkUnknownSubType(hub, objectHub)) {
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
    private static int superCheckOffsetOffset() {
        return CompilerImpl.getInstance().getConfig().superCheckOffsetOffset;
    }

    @Fold
    private static int secondarySuperCacheOffset() {
        return CompilerImpl.getInstance().getConfig().secondarySuperCacheOffset;
    }

    @Fold
    private static int secondarySupersOffset() {
        return CompilerImpl.getInstance().getConfig().secondarySupersOffset;
    }

    @Fold
    private static int hubOffset() {
        return CompilerImpl.getInstance().getConfig().hubOffset;
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

    public enum TemplateFlag {
        CHECK_NULL,
        EXACT_HINTS,
        COUNTERS,
        PRIMARY_SUPER,
        SECONDARY_SUPER,
        UNKNOWN_SUPER;

        public int bit(boolean value) {
            if (value) {
                return bit();
            }
            return 0;
        }

        public boolean bool(int flags) {
            return (flags & bit()) != 0;
        }

        static final int FLAGS_BITS = values().length;
        static final int FLAGS_MASK = (1 << FLAGS_BITS) - 1;

        static final int NHINTS_SHIFT = FLAGS_BITS;
        static final int NHINTS_BITS = 3;
        static final int SUPER_CHECK_OFFSET_SHIFT = NHINTS_SHIFT + NHINTS_BITS;

        public int bit() {
            return 1 << ordinal();
        }
    }

    /**
     * Templates for partially specialized checkcast snippet graphs.
     */
    public static class Templates {

        private final ConcurrentHashMap<Integer, SnippetTemplate> templates;
        private final RiResolvedMethod exact;
        private final RiResolvedMethod primary;
        private final RiResolvedMethod secondary;
        private final RiResolvedMethod unknown;
        private final RiResolvedMethod counters;
        private final RiRuntime runtime;

        public Templates(RiRuntime runtime) {
            this.runtime = runtime;
            this.templates = new ConcurrentHashMap<>();
            try {
                exact = runtime.getRiMethod(CheckCastSnippets.class.getDeclaredMethod("checkcastExact", Object.class, Object.class, boolean.class));
                primary = runtime.getRiMethod(CheckCastSnippets.class.getDeclaredMethod("checkcastPrimary", Object.class, Object.class, boolean.class, int.class));
                secondary = runtime.getRiMethod(CheckCastSnippets.class.getDeclaredMethod("checkcastSecondary", Object.class, Object.class, Object[].class, boolean.class));
                unknown = runtime.getRiMethod(CheckCastSnippets.class.getDeclaredMethod("checkcastUnknown", Object.class, Object.class, Object[].class, boolean.class));
                counters = runtime.getRiMethod(CheckCastSnippets.class.getDeclaredMethod("checkcastCounters", Object.class, Object.class, Object[].class, boolean.class));
            } catch (NoSuchMethodException e) {
                throw new GraalInternalError(e);
            }
        }

        /**
         * Interface for lazily creating a snippet template.
         */
        abstract static class Factory {
            abstract SnippetTemplate create(HotSpotKlassOop[] hints, int flags);
        }

        /**
         * Gets a template from the template cache, creating and installing it first if necessary.
         */
        private SnippetTemplate getTemplate(int nHints, int flags, int superCheckOffset, Factory factory) {
            assert (flags & ~FLAGS_MASK) == 0;
            assert nHints >= 0 && nHints < (1 << NHINTS_BITS) - 1 : "nHints out of range";
            assert superCheckOffset >= 0 && superCheckOffset == ((superCheckOffset << SUPER_CHECK_OFFSET_SHIFT) >>> SUPER_CHECK_OFFSET_SHIFT) : "superCheckOffset out of range";
            Integer key = superCheckOffset << SUPER_CHECK_OFFSET_SHIFT | nHints << NHINTS_SHIFT | flags;
            SnippetTemplate result = templates.get(key);
            if (result == null) {
                HotSpotKlassOop[] hints = new HotSpotKlassOop[nHints];
                for (int i = 0; i < hints.length; i++) {
                    hints[i] = new HotSpotKlassOop(null, Templates.class);
                }
                result = factory.create(hints, flags);
                //System.err.println(result);
                templates.put(key, result);
            }
            return result;
        }

        private static HotSpotKlassOop[] createHintHubs(TypeCheckHints hints) {
            HotSpotKlassOop[] hintHubs = new HotSpotKlassOop[hints.types.length];
            for (int i = 0; i < hintHubs.length; i++) {
                hintHubs[i] = ((HotSpotType) hints.types[i]).klassOop();
            }
            return hintHubs;
        }

        /**
         * Lowers a checkcast node.
         */
        public void lower(CheckCastNode checkcast, CiLoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) checkcast.graph();
            ValueNode hub = checkcast.targetClassInstruction();
            ValueNode object = checkcast.object();
            TypeCheckHints hints = new TypeCheckHints(checkcast.targetClass(), checkcast.profile(), tool.assumptions(), GraalOptions.CheckcastMinHintHitProbability, GraalOptions.CheckcastMaxHints);
            Debug.log("Lowering checkcast in %s: node=%s, hintsHubs=%s, exact=%b", graph, checkcast, Arrays.toString(hints.types), hints.exact);

            final HotSpotTypeResolvedImpl target = (HotSpotTypeResolvedImpl) checkcast.targetClass();
            int flags = CHECK_NULL.bit(!object.stamp().nonNull());
            if (GraalOptions.CheckcastCounters) {
                HotSpotKlassOop[] hintHubs = createHintHubs(hints);
                SnippetTemplate template = getTemplate(hintHubs.length, flags | EXACT_HINTS.bit(hints.exact) | COUNTERS.bit(), 0, new Factory() {
                    @SuppressWarnings("hiding")
                    @Override
                    SnippetTemplate create(HotSpotKlassOop[] hints, int flags) {
                        // checkcastCounters(Object hub, Object object, Object[] hintHubs, boolean hintsAreExact)
                        return SnippetTemplate.create(runtime, counters, _, _, hints, EXACT_HINTS.bool(flags));
                    }
                });
                template.instantiate(runtime, checkcast, checkcast, hub, object, hintHubs, hints.exact);
            } else if (target == null) {
                HotSpotKlassOop[] hintHubs = createHintHubs(hints);
                SnippetTemplate template = getTemplate(hintHubs.length, flags | UNKNOWN_SUPER.bit(), 0, new Factory() {
                    @SuppressWarnings("hiding")
                    @Override
                    SnippetTemplate create(HotSpotKlassOop[] hints, int flags) {
                        // checkcastUnknown(Object hub, Object object, Object[] hintHubs, boolean checkNull)
                        return SnippetTemplate.create(runtime, unknown, _, _, hints, CHECK_NULL.bool(flags));
                    }
                });
                template.instantiate(runtime, checkcast, checkcast, hub, object, hintHubs);
            } else if (hints.exact) {
                HotSpotKlassOop[] hintHubs = createHintHubs(hints);
                assert hintHubs.length == 1;
                SnippetTemplate template = getTemplate(hintHubs.length, flags | EXACT_HINTS.bit(), 0, new Factory() {
                    @SuppressWarnings("hiding")
                    @Override
                    SnippetTemplate create(HotSpotKlassOop[] hints, int flags) {
                        // checkcastExact(Object object, Object exactHub, boolean checkNull)
                        return SnippetTemplate.create(runtime, exact, _, hints[0], CHECK_NULL.bool(flags));
                    }
                });
                template.instantiate(runtime, checkcast, checkcast, object, hintHubs[0]);
            } else if (target.isPrimaryType()) {
                SnippetTemplate template = getTemplate(0, flags | PRIMARY_SUPER.bit(), target.superCheckOffset(), new Factory() {
                    @SuppressWarnings("hiding")
                    @Override
                    SnippetTemplate create(HotSpotKlassOop[] hints, int flags) {
                        // checkcastPrimary(Object hub, Object object, boolean checkNull, int superCheckOffset)
                        return SnippetTemplate.create(runtime, primary, _, _, CHECK_NULL.bool(flags), target.superCheckOffset());
                    }
                });
                template.instantiate(runtime, checkcast, checkcast, hub, object);
            } else {
                HotSpotKlassOop[] hintHubs = createHintHubs(hints);
                SnippetTemplate template = getTemplate(hintHubs.length, flags | SECONDARY_SUPER.bit(), 0, new Factory() {
                    @SuppressWarnings("hiding")
                    @Override
                    SnippetTemplate create(HotSpotKlassOop[] hints, int flags) {
                        // checkcastSecondary(Object hub, Object object, Object[] hintHubs, boolean checkNull)
                        return SnippetTemplate.create(runtime, secondary, _, _, hints, CHECK_NULL.bool(flags));
                    }
                });
                template.instantiate(runtime, checkcast, checkcast, hub, object, hintHubs);
            }
            new DeadCodeEliminationPhase().apply(graph);
        }
    }
}
