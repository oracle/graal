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
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.snippets.CheckCastSnippets.*;
import static com.oracle.graal.hotspot.snippets.CheckCastSnippets.Templates.*;
import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;
import static com.oracle.graal.snippets.SnippetTemplate.Arguments.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.Snippet.Varargs;
import com.oracle.graal.snippets.Snippet.VarargsParameter;
import com.oracle.graal.snippets.SnippetTemplate.Arguments;
import com.oracle.graal.snippets.SnippetTemplate.Key;
import com.oracle.graal.snippets.nodes.*;

/**
 * Snippets used for implementing the type test of an instanceof instruction.
 * Since instanceof is a floating node, it is lowered separately for each of
 * its usages.
 *
 * The type tests implemented are described in the paper <a href="http://dl.acm.org/citation.cfm?id=583821">
 * Fast subtype checking in the HotSpot JVM</a> by Cliff Click and John Rose.
 */
public class InstanceOfSnippets implements SnippetsInterface {

    /**
     * A test against a final type.
     */
    @Snippet
    public static Object instanceofExact(
                    @Parameter("object") Object object,
                    @Parameter("exactHub") Word exactHub,
                    @Parameter("trueValue") Object trueValue,
                    @Parameter("falseValue") Object falseValue,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            isNull.inc();
            return falseValue;
        }
        Word objectHub = loadHub(object);
        if (objectHub != exactHub) {
            exactMiss.inc();
            return falseValue;
        }
        exactHit.inc();
        return trueValue;
    }

    /**
     * A test against a primary type.
     */
    @Snippet
    public static Object instanceofPrimary(
                    @Parameter("hub") Word hub,
                    @Parameter("object") Object object,
                    @Parameter("trueValue") Object trueValue,
                    @Parameter("falseValue") Object falseValue,
                    @ConstantParameter("checkNull") boolean checkNull,
                    @ConstantParameter("superCheckOffset") int superCheckOffset) {
        if (checkNull && object == null) {
            isNull.inc();
            return falseValue;
        }
        Word objectHub = loadHub(object);
        if (loadWordFromWord(objectHub, superCheckOffset) != hub) {
            displayMiss.inc();
            return falseValue;
        }
        displayHit.inc();
        return trueValue;
    }

    /**
     * A test against a restricted secondary type type.
     */
    @Snippet
    public static Object instanceofSecondary(
                    @Parameter("hub") Word hub,
                    @Parameter("object") Object object,
                    @Parameter("trueValue") Object trueValue,
                    @Parameter("falseValue") Object falseValue,
                    @VarargsParameter("hints") Word[] hints,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            isNull.inc();
            return falseValue;
        }
        Word objectHub = loadHub(object);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            Word hintHub = hints[i];
            if (hintHub == objectHub) {
                hintsHit.inc();
                return trueValue;
            }
        }
        if (!checkSecondarySubType(hub, objectHub)) {
            return falseValue;
        }
        return trueValue;
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
                DirectObjectStoreNode.storeObject(s, secondarySuperCacheOffset(), 0, t);
                secondariesHit.inc();
                return true;
            }
        }
        secondariesMiss.inc();
        return false;
    }

    public static class Templates extends InstanceOfSnippetsTemplates<InstanceOfSnippets> {

        private final ResolvedJavaMethod instanceofExact;
        private final ResolvedJavaMethod instanceofPrimary;
        private final ResolvedJavaMethod instanceofSecondary;

        public Templates(CodeCacheProvider runtime, Assumptions assumptions) {
            super(runtime, assumptions, InstanceOfSnippets.class);
            instanceofExact = snippet("instanceofExact", Object.class, Word.class, Object.class, Object.class, boolean.class);
            instanceofPrimary = snippet("instanceofPrimary", Word.class, Object.class, Object.class, Object.class, boolean.class, int.class);
            instanceofSecondary = snippet("instanceofSecondary", Word.class, Object.class, Object.class, Object.class, Word[].class, boolean.class);
        }

        @Override
        protected KeyAndArguments getKeyAndArguments(InstanceOfUsageReplacer replacer, LoweringTool tool) {
            InstanceOfNode instanceOf = replacer.instanceOf;
            ValueNode trueValue = replacer.trueValue;
            ValueNode falseValue = replacer.falseValue;
            ValueNode object = instanceOf.object();
            TypeCheckHints hintInfo = new TypeCheckHints(instanceOf.type(), instanceOf.profile(), tool.assumptions(), GraalOptions.InstanceOfMinHintHitProbability, GraalOptions.InstanceOfMaxHints);
            final HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) instanceOf.type();
            ConstantNode hub = ConstantNode.forConstant(type.klass(), runtime, instanceOf.graph());
            boolean checkNull = !object.stamp().nonNull();
            Arguments arguments;
            Key key;
            if (hintInfo.exact) {
                ConstantNode[] hints = createHints(hintInfo, runtime, hub.graph());
                assert hints.length == 1;
                key = new Key(instanceofExact).add("checkNull", checkNull);
                arguments = arguments("object", object).add("exactHub", hints[0]).add("trueValue", trueValue).add("falseValue", falseValue);
            } else if (type.isPrimaryType()) {
                key = new Key(instanceofPrimary).add("checkNull", checkNull).add("superCheckOffset", type.superCheckOffset());
                arguments = arguments("hub", hub).add("object", object).add("trueValue", trueValue).add("falseValue", falseValue);
            } else {
                ConstantNode[] hints = createHints(hintInfo, runtime, hub.graph());
                key = new Key(instanceofSecondary).add("hints", Varargs.vargargs(new Word[hints.length], wordStamp())).add("checkNull", checkNull);
                arguments = arguments("hub", hub).add("object", object).add("hints", hints).add("trueValue", trueValue).add("falseValue", falseValue);
            }
            return new KeyAndArguments(key, arguments);
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
