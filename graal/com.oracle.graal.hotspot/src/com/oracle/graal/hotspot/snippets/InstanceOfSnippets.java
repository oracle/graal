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
import static com.oracle.graal.hotspot.snippets.CheckCastSnippets.*;
import static com.oracle.graal.hotspot.snippets.CheckCastSnippets.Templates.*;
import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;
import static com.oracle.graal.snippets.Snippet.Varargs.*;
import static com.oracle.graal.snippets.SnippetTemplate.Arguments.*;
import static com.oracle.graal.snippets.nodes.JumpNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.Snippet.VarargsParameter;
import com.oracle.graal.snippets.SnippetTemplate.AbstractTemplates;
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
     * A test against a final type with the result being {@linkplain ConditionalNode materialized}.
     */
    @Snippet
    public static Object materializeExact(
                    @Parameter("object") Object object,
                    @Parameter("exactHub") Object exactHub,
                    @Parameter("trueValue") Object trueValue,
                    @Parameter("falseValue") Object falseValue,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            isNull.inc();
            return falseValue;
        }
        Object objectHub = loadHub(object);
        if (objectHub != exactHub) {
            exactMiss.inc();
            return falseValue;
        }
        exactHit.inc();
        return trueValue;
    }

    /**
     * A test against a final type with the result being {@linkplain IfNode branched} upon.
     */
    @Snippet
    public static void ifExact(
                    @Parameter("object") Object object,
                    @Parameter("exactHub") Object exactHub,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            isNull.inc();
            jump(IfNode.FALSE_EDGE);
            return;
        }
        Object objectHub = loadHub(object);
        if (objectHub != exactHub) {
            exactMiss.inc();
            jump(IfNode.FALSE_EDGE);
            return;
        }
        exactHit.inc();
        jump(IfNode.TRUE_EDGE);
    }

    /**
     * A test against a primary type with the result being {@linkplain ConditionalNode materialized}.
     */
    @Snippet
    public static Object materializePrimary(
                    @Parameter("hub") Object hub,
                    @Parameter("object") Object object,
                    @Parameter("trueValue") Object trueValue,
                    @Parameter("falseValue") Object falseValue,
                    @ConstantParameter("checkNull") boolean checkNull,
                    @ConstantParameter("superCheckOffset") int superCheckOffset) {
        if (checkNull && object == null) {
            isNull.inc();
            return falseValue;
        }
        Object objectHub = loadHub(object);
        if (UnsafeLoadNode.loadObject(objectHub, 0, superCheckOffset, true) != hub) {
            displayMiss.inc();
            return falseValue;
        }
        displayHit.inc();
        return trueValue;
    }

    /**
     * A test against a primary type with the result being {@linkplain IfNode branched} upon.
     */
    @Snippet
    public static void ifPrimary(
                    @Parameter("hub") Object hub,
                    @Parameter("object") Object object,
                    @ConstantParameter("checkNull") boolean checkNull,
                    @ConstantParameter("superCheckOffset") int superCheckOffset) {
        if (checkNull && object == null) {
            isNull.inc();
            jump(IfNode.FALSE_EDGE);
            return;
        }
        Object objectHub = loadHub(object);
        if (UnsafeLoadNode.loadObject(objectHub, 0, superCheckOffset, true) != hub) {
            displayMiss.inc();
            jump(IfNode.FALSE_EDGE);
            return;
        }
        displayHit.inc();
        jump(IfNode.TRUE_EDGE);
        return;
    }

    /**
     * A test against a restricted secondary type type with the result being {@linkplain ConditionalNode materialized}.
     */
    @Snippet
    public static Object materializeSecondary(
                    @Parameter("hub") Object hub,
                    @Parameter("object") Object object,
                    @Parameter("trueValue") Object trueValue,
                    @Parameter("falseValue") Object falseValue,
                    @VarargsParameter("hints") Object[] hints,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            isNull.inc();
            return falseValue;
        }
        Object objectHub = loadHub(object);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            Object hintHub = hints[i];
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

    /**
     * A test against a restricted secondary type with the result being {@linkplain IfNode branched} upon.
     */
    @Snippet
    public static void ifSecondary(
                    @Parameter("hub") Object hub,
                    @Parameter("object") Object object,
                    @VarargsParameter("hints") Object[] hints,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            isNull.inc();
            jump(IfNode.FALSE_EDGE);
            return;
        }
        Object objectHub = loadHub(object);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            Object hintHub = hints[i];
            if (hintHub == objectHub) {
                hintsHit.inc();
                jump(IfNode.TRUE_EDGE);
                return;
            }
        }
        if (!checkSecondarySubType(hub, objectHub)) {
            jump(IfNode.FALSE_EDGE);
            return;
        }
        jump(IfNode.TRUE_EDGE);
        return;
    }

    /**
     * A test against an unknown (at compile time) type with the result being {@linkplain ConditionalNode materialized}.
     */
    @Snippet
    public static Object materializeUnknown(
                    @Parameter("hub") Object hub,
                    @Parameter("object") Object object,
                    @Parameter("trueValue") Object trueValue,
                    @Parameter("falseValue") Object falseValue,
                    @VarargsParameter("hints") Object[] hints,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            isNull.inc();
            return falseValue;
        }
        Object objectHub = loadHub(object);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            Object hintHub = hints[i];
            if (hintHub == objectHub) {
                hintsHit.inc();
                return trueValue;
            }
        }
        if (!checkUnknownSubType(hub, objectHub)) {
            return falseValue;
        }
        return trueValue;
    }

    static boolean checkSecondarySubType(Object t, Object s) {
        // if (S.cache == T) return true
        if (UnsafeLoadNode.loadObject(s, 0, secondarySuperCacheOffset(), true) == t) {
            cacheHit.inc();
            return true;
        }

        // if (T == S) return true
        if (s == t) {
            T_equals_S.inc();
            return true;
        }

        // if (S.scan_s_s_array(T)) { S.cache = T; return true; }
        Object[] secondarySupers = UnsafeCastNode.cast(UnsafeLoadNode.loadObject(s, 0, secondarySupersOffset(), true), Object[].class);

        for (int i = 0; i < secondarySupers.length; i++) {
            if (t == loadNonNullObjectElement(secondarySupers, i)) {
                DirectObjectStoreNode.storeObject(s, secondarySuperCacheOffset(), 0, t);
                secondariesHit.inc();
                return true;
            }
        }
        secondariesMiss.inc();
        return false;
    }

    public static class Templates extends AbstractTemplates<InstanceOfSnippets> {

        private final ResolvedJavaMethod ifExact;
        private final ResolvedJavaMethod ifPrimary;
        private final ResolvedJavaMethod ifSecondary;
        private final ResolvedJavaMethod materializeExact;
        private final ResolvedJavaMethod materializePrimary;
        private final ResolvedJavaMethod materializeSecondary;

        public Templates(CodeCacheProvider runtime) {
            super(runtime, InstanceOfSnippets.class);
            ifExact = snippet("ifExact", Object.class, Object.class, boolean.class);
            ifPrimary = snippet("ifPrimary", Object.class, Object.class, boolean.class, int.class);
            ifSecondary = snippet("ifSecondary", Object.class, Object.class, Object[].class, boolean.class);

            materializeExact = snippet("materializeExact", Object.class, Object.class, Object.class, Object.class, boolean.class);
            materializePrimary = snippet("materializePrimary", Object.class, Object.class, Object.class, Object.class, boolean.class, int.class);
            materializeSecondary = snippet("materializeSecondary", Object.class, Object.class, Object.class, Object.class, Object[].class, boolean.class);
        }

        public void lower(InstanceOfNode instanceOf, LoweringTool tool) {
            ValueNode hub = instanceOf.targetClassInstruction();
            ValueNode object = instanceOf.object();
            TypeCheckHints hintInfo = new TypeCheckHints(instanceOf.targetClass(), instanceOf.profile(), tool.assumptions(), GraalOptions.CheckcastMinHintHitProbability, GraalOptions.CheckcastMaxHints);
            final HotSpotResolvedJavaType target = (HotSpotResolvedJavaType) instanceOf.targetClass();
            boolean checkNull = !object.stamp().nonNull();

            for (Node usage : instanceOf.usages().snapshot()) {
                Arguments arguments = null;
                Key key = null;

                // instanceof nodes are lowered separately for each usage. To simply graph modifications,
                // we duplicate the instanceof node for each usage.
                InstanceOfNode duplicate = instanceOf.graph().add(new InstanceOfNode(instanceOf.targetClassInstruction(), instanceOf.targetClass(), instanceOf.object(), instanceOf.profile()));
                usage.replaceFirstInput(instanceOf, duplicate);

                if (usage instanceof IfNode) {

                    IfNode ifNode = (IfNode) usage;
                    if (hintInfo.exact) {
                        HotSpotKlassOop[] hints = createHints(hintInfo);
                        assert hints.length == 1;
                        key = new Key(ifExact).add("checkNull", checkNull);
                        arguments = arguments("object", object).add("exactHub", hints[0]);
                    } else if (target.isPrimaryType()) {
                        key = new Key(ifPrimary).add("checkNull", checkNull).add("superCheckOffset", target.superCheckOffset());
                        arguments = arguments("hub", hub).add("object", object);
                    } else {
                        HotSpotKlassOop[] hints = createHints(hintInfo);
                        key = new Key(ifSecondary).add("hints", vargargs(Object.class, hints.length)).add("checkNull", checkNull);
                        arguments = arguments("hub", hub).add("object", object).add("hints", hints);
                    }

                    SnippetTemplate template = cache.get(key);
                    template.instantiate(runtime, duplicate, ifNode, arguments);
                    assert ifNode.isDeleted();

                } else if (usage instanceof ConditionalNode) {

                    ConditionalNode materialize = (ConditionalNode) usage;
                    materialize.replaceAtUsages(duplicate);
                    ValueNode falseValue = materialize.falseValue();
                    ValueNode trueValue = materialize.trueValue();

                    // The materialize node is no longer connected to anyone -> kill it
                    materialize.clearInputs();
                    assert materialize.usages().isEmpty();
                    GraphUtil.killWithUnusedFloatingInputs(materialize);

                    if (hintInfo.exact) {
                        HotSpotKlassOop[] hints = createHints(hintInfo);
                        assert hints.length == 1;
                        key = new Key(materializeExact).add("checkNull", checkNull);
                        arguments = arguments("object", object).add("exactHub", hints[0]).add("trueValue", trueValue).add("falseValue", falseValue);
                    } else if (target.isPrimaryType()) {
                        key = new Key(materializePrimary).add("checkNull", checkNull).add("superCheckOffset", target.superCheckOffset());
                        arguments = arguments("hub", hub).add("object", object).add("trueValue", trueValue).add("falseValue", falseValue);
                    } else {
                        HotSpotKlassOop[] hints = createHints(hintInfo);
                        key = new Key(materializeSecondary).add("hints", vargargs(Object.class, hints.length)).add("checkNull", checkNull);
                        arguments = arguments("hub", hub).add("object", object).add("hints", hints).add("trueValue", trueValue).add("falseValue", falseValue);
                    }

                    SnippetTemplate template = cache.get(key);
                    template.instantiate(runtime, duplicate, tool.lastFixedNode(), arguments);
                } else {
                    throw new GraalInternalError("Unexpected usage of %s: %s", instanceOf, usage);
                }
            }

            assert !instanceOf.isDeleted();
            assert instanceOf.usages().isEmpty();
            GraphUtil.killWithUnusedFloatingInputs(instanceOf);
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
