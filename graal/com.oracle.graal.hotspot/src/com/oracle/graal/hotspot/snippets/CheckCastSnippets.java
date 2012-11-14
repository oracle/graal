/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;
import static com.oracle.graal.snippets.SnippetTemplate.*;
import static com.oracle.graal.snippets.SnippetTemplate.Arguments.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.Snippet.Varargs;
import com.oracle.graal.snippets.Snippet.VarargsParameter;
import com.oracle.graal.snippets.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.snippets.SnippetTemplate.Arguments;
import com.oracle.graal.snippets.SnippetTemplate.Key;
import com.oracle.graal.snippets.nodes.*;

/**
 * Snippets used for implementing the type test of a checkcast instruction.
 *
 * The type tests implemented are described in the paper <a href="http://dl.acm.org/citation.cfm?id=583821">
 * Fast subtype checking in the HotSpot JVM</a> by Cliff Click and John Rose.
 */
public class CheckCastSnippets implements SnippetsInterface {

    @NodeIntrinsic(BreakpointNode.class)
    static native void bkpt(Object object, Word hub, Word objectHub);

    /**
     * Type test used when the type being tested against is a final type.
     */
    @Snippet
    public static Object checkcastExact(
                    @Parameter("object") Object object,
                    @Parameter("exactHub") Word exactHub,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            isNull.inc();
            return object;
        }
        Word objectHub = loadHub(object);
        if (objectHub != exactHub) {
            exactMiss.inc();
            //bkpt(object, exactHub, objectHub);
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.ClassCastException);
        }
        exactHit.inc();
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
    public static Object checkcastPrimary(
                    @Parameter("hub") Word hub,
                    @Parameter("object") Object object,
                    @ConstantParameter("checkNull") boolean checkNull,
                    @ConstantParameter("superCheckOffset") int superCheckOffset) {
        if (checkNull && object == null) {
            isNull.inc();
            return object;
        }
        Word objectHub = loadHub(object);
        if (loadWordFromWord(objectHub, superCheckOffset) != hub) {
            displayMiss.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.ClassCastException);
        }
        displayHit.inc();
        return object;
    }

    /**
     * Type test used when the type being tested against is a restricted secondary type.
     */
    @Snippet
    public static Object checkcastSecondary(
                    @Parameter("hub") Word hub,
                    @Parameter("object") Object object,
                    @VarargsParameter("hints") Word[] hints,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            isNull.inc();
            return object;
        }
        Word objectHub = loadHub(object);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            Word hintHub = hints[i];
            if (hintHub == objectHub) {
                hintsHit.inc();
                return object;
            }
        }
        if (!checkSecondarySubType(hub, objectHub)) {
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.ClassCastException);
        }
        return object;
    }

    /**
     * Type test used when the type being tested against is not known at compile time (e.g. the type test
     * in an object array store check).
     */
    @Snippet
    public static Object checkcastDynamic(
                    @Parameter("hub") Word hub,
                    @Parameter("object") Object object,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            isNull.inc();
            return object;
        }
        Word objectHub = loadHub(object);
        if (!checkUnknownSubType(hub, objectHub)) {
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.ClassCastException);
        }
        return object;
    }

    static Word loadWordElement(Word metaspaceArray, int index) {
        return loadWordFromWord(metaspaceArray, metaspaceArrayBaseOffset() + index * wordSize());
    }

    static boolean checkSecondarySubType(Word t, Word s) {
        // if (S.cache == T) return true
        if (loadWordFromWord(s, secondarySuperCacheOffset()) == t) {
            cacheHit.inc();
            return true;
        }

        // if (T == S) return true
        if (s == t) {
            T_equals_S.inc();
            return true;
        }

        // if (S.scan_s_s_array(T)) { S.cache = T; return true; }
        Word secondarySupers = loadWordFromWord(s, secondarySupersOffset());
        int length = loadIntFromWord(secondarySupers, metaspaceArrayLengthOffset());
        for (int i = 0; i < length; i++) {
            if (t == loadWordElement(secondarySupers, i)) {
                DirectObjectStoreNode.storeWord(s, secondarySuperCacheOffset(), 0, t);
                secondariesHit.inc();
                return true;
            }
        }
        secondariesMiss.inc();
        return false;
    }

    static boolean checkUnknownSubType(Word t, Word s) {
        // int off = T.offset
        int superCheckOffset = UnsafeLoadNode.load(t, 0, superCheckOffsetOffset(), Kind.Int);
        boolean primary = superCheckOffset != secondarySuperCacheOffset();

        // if (T = S[off]) return true
        if (loadWordFromWord(s, superCheckOffset) == t) {
            if (primary) {
                cacheHit.inc();
            } else {
                displayHit.inc();
            }
            return true;
        }

        // if (off != &cache) return false
        if (primary) {
            displayMiss.inc();
            return false;
        }

        // if (T == S) return true
        if (s == t) {
            T_equals_S.inc();
            return true;
        }

        // if (S.scan_s_s_array(T)) { S.cache = T; return true; }
        Word secondarySupers = loadWordFromWord(s, secondarySupersOffset());
        int length = loadIntFromWord(secondarySupers, metaspaceArrayLengthOffset());
        for (int i = 0; i < length; i++) {
            if (t == loadWordElement(secondarySupers, i)) {
                DirectObjectStoreNode.storeWord(s, secondarySuperCacheOffset(), 0, t);
                secondariesHit.inc();
                return true;
            }
        }

        secondariesMiss.inc();
        return false;
    }

    public static class Templates extends AbstractTemplates<CheckCastSnippets> {

        private final ResolvedJavaMethod exact;
        private final ResolvedJavaMethod primary;
        private final ResolvedJavaMethod secondary;
        private final ResolvedJavaMethod dynamic;

        public Templates(CodeCacheProvider runtime) {
            super(runtime, CheckCastSnippets.class);
            exact = snippet("checkcastExact", Object.class, Word.class, boolean.class);
            primary = snippet("checkcastPrimary", Word.class, Object.class, boolean.class, int.class);
            secondary = snippet("checkcastSecondary", Word.class, Object.class, Word[].class, boolean.class);
            dynamic = snippet("checkcastDynamic", Word.class, Object.class, boolean.class);
        }

        /**
         * Lowers a checkcast node.
         */
        public void lower(CheckCastNode checkcast, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) checkcast.graph();
            ValueNode object = checkcast.object();
            final HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) checkcast.type();
            TypeCheckHints hintInfo = new TypeCheckHints(checkcast.type(), checkcast.profile(), tool.assumptions(), GraalOptions.CheckcastMinHintHitProbability, GraalOptions.CheckcastMaxHints);
            ValueNode hub = ConstantNode.forConstant(type.klass(), runtime, checkcast.graph());
            boolean checkNull = !object.stamp().nonNull();
            Arguments arguments;
            Key key;

            assert type != null;
            if (hintInfo.exact) {
                ConstantNode[] hints = createHints(hintInfo, runtime, graph);
                assert hints.length == 1;
                key = new Key(exact).add("checkNull", checkNull);
                arguments = arguments("object", object).add("exactHub", hints[0]);
            } else if (type.isPrimaryType()) {
                key = new Key(primary).add("checkNull", checkNull).add("superCheckOffset", type.superCheckOffset());
                arguments = arguments("hub", hub).add("object", object);
            } else {
                ConstantNode[] hints = createHints(hintInfo, runtime, graph);
                key = new Key(secondary).add("hints", Varargs.vargargs(new Word[hints.length], StampFactory.forWord(wordKind(), true))).add("checkNull", checkNull);
                arguments = arguments("hub", hub).add("object", object).add("hints", hints);
            }

            SnippetTemplate template = cache.get(key);
            Debug.log("Lowering checkcast in %s: node=%s, template=%s, arguments=%s", graph, checkcast, template, arguments);
            template.instantiate(runtime, checkcast, DEFAULT_REPLACER, arguments);
        }

        /**
         * Lowers a dynamic checkcast node.
         */
        public void lower(CheckCastDynamicNode checkcast) {
            StructuredGraph graph = (StructuredGraph) checkcast.graph();
            ValueNode hub = checkcast.type();
            ValueNode object = checkcast.object();
            boolean checkNull = !object.stamp().nonNull();

            Key key = new Key(dynamic).add("checkNull", checkNull);
            Arguments arguments = arguments("hub", hub).add("object", object);

            SnippetTemplate template = cache.get(key);
            Debug.log("Lowering dynamic checkcast in %s: node=%s, template=%s, arguments=%s", graph, checkcast, template, arguments);
            template.instantiate(runtime, checkcast, DEFAULT_REPLACER, arguments);
        }

        static ConstantNode[] createHints(TypeCheckHints hints, MetaAccessProvider runtime, Graph graph) {
            ConstantNode[] hintHubs = new ConstantNode[hints.types.length];
            for (int i = 0; i < hintHubs.length; i++) {
                hintHubs[i] = ConstantNode.forConstant(((HotSpotJavaType) hints.types[i]).klass(), runtime, graph);
            }
            return hintHubs;
        }
    }

    private static final SnippetCounter.Group counters = GraalOptions.SnippetCounters ? new SnippetCounter.Group("Checkcast") : null;
    private static final SnippetCounter hintsHit = new SnippetCounter(counters, "hintsHit", "hit a hint type");
    private static final SnippetCounter exactHit = new SnippetCounter(counters, "exactHit", "exact type test succeeded");
    private static final SnippetCounter exactMiss = new SnippetCounter(counters, "exactMiss", "exact type test failed");
    private static final SnippetCounter isNull = new SnippetCounter(counters, "isNull", "object tested was null");
    private static final SnippetCounter cacheHit = new SnippetCounter(counters, "cacheHit", "secondary type cache hit");
    private static final SnippetCounter secondariesHit = new SnippetCounter(counters, "secondariesHit", "secondaries scan succeeded");
    private static final SnippetCounter secondariesMiss = new SnippetCounter(counters, "secondariesMiss", "secondaries scan failed");
    private static final SnippetCounter displayHit = new SnippetCounter(counters, "displayHit", "primary type test succeeded");
    private static final SnippetCounter displayMiss = new SnippetCounter(counters, "displayMiss", "primary type test failed");
    private static final SnippetCounter T_equals_S = new SnippetCounter(counters, "T_equals_S", "object type was equal to secondary type");
}
