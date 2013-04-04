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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.replacements.HotSpotSnippetUtils.*;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.*;
import static com.oracle.graal.replacements.SnippetTemplate.Arguments.*;
import static com.oracle.graal.replacements.nodes.BranchProbabilityNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.*;
import com.oracle.graal.replacements.SnippetTemplate.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

/**
 * Snippets used for implementing the type test of an instanceof instruction. Since instanceof is a
 * floating node, it is lowered separately for each of its usages.
 * 
 * The type tests implemented are described in the paper <a
 * href="http://dl.acm.org/citation.cfm?id=583821"> Fast subtype checking in the HotSpot JVM</a> by
 * Cliff Click and John Rose.
 */
public class InstanceOfSnippets implements Snippets {

    // @formatter:off

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
            probability(NOT_FREQUENT_PROBABILITY);
            isNull.inc();
            return falseValue;
        }
        Word objectHub = loadHub(object);
        if (objectHub.notEqual(exactHub)) {
            probability(LIKELY_PROBABILITY);
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
            probability(NOT_FREQUENT_PROBABILITY);
            isNull.inc();
            return falseValue;
        }
        Word objectHub = loadHub(object);
        if (objectHub.readWord(superCheckOffset, FINAL_LOCATION).notEqual(hub)) {
            probability(NOT_LIKELY_PROBABILITY);
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
            probability(NOT_FREQUENT_PROBABILITY);
            isNull.inc();
            return falseValue;
        }
        Word objectHub = loadHub(object);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            Word hintHub = hints[i];
            if (hintHub.equal(objectHub)) {
                probability(NOT_FREQUENT_PROBABILITY);
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
     * Type test used when the type being tested against is not known at compile time.
     */
    @Snippet
    public static Object instanceofDynamic(
                    @Parameter("mirror") Class mirror,
                    @Parameter("object") Object object,
                    @Parameter("trueValue") Object trueValue,
                    @Parameter("falseValue") Object falseValue,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            probability(NOT_FREQUENT_PROBABILITY);
            isNull.inc();
            return falseValue;
        }

        Word hub = loadWordFromObject(mirror, klassOffset());
        Word objectHub = loadHub(object);
        if (!checkUnknownSubType(hub, objectHub)) {
            return falseValue;
        }
        return trueValue;
    }

    // @formatter:on

    public static class Templates extends InstanceOfSnippetsTemplates<InstanceOfSnippets> {

        private final ResolvedJavaMethod instanceofExact;
        private final ResolvedJavaMethod instanceofPrimary;
        private final ResolvedJavaMethod instanceofSecondary;
        private final ResolvedJavaMethod instanceofDynamic;

        public Templates(CodeCacheProvider runtime, Replacements replacements, TargetDescription target) {
            super(runtime, replacements, target, InstanceOfSnippets.class);
            instanceofExact = snippet("instanceofExact", Object.class, Word.class, Object.class, Object.class, boolean.class);
            instanceofPrimary = snippet("instanceofPrimary", Word.class, Object.class, Object.class, Object.class, boolean.class, int.class);
            instanceofSecondary = snippet("instanceofSecondary", Word.class, Object.class, Object.class, Object.class, Word[].class, boolean.class);
            instanceofDynamic = snippet("instanceofDynamic", Class.class, Object.class, Object.class, Object.class, boolean.class);
        }

        @Override
        protected KeyAndArguments getKeyAndArguments(InstanceOfUsageReplacer replacer, LoweringTool tool) {
            if (replacer.instanceOf instanceof InstanceOfNode) {
                InstanceOfNode instanceOf = (InstanceOfNode) replacer.instanceOf;
                ValueNode trueValue = replacer.trueValue;
                ValueNode falseValue = replacer.falseValue;
                ValueNode object = instanceOf.object();
                TypeCheckHints hintInfo = new TypeCheckHints(instanceOf.type(), instanceOf.profile(), tool.assumptions(), GraalOptions.InstanceOfMinHintHitProbability, GraalOptions.InstanceOfMaxHints);
                final HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) instanceOf.type();
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
                    key = new Key(instanceofSecondary).add("hints", Varargs.vargargs(new Word[hints.length], StampFactory.forKind(wordKind()))).add("checkNull", checkNull);
                    arguments = arguments("hub", hub).add("object", object).add("hints", hints).add("trueValue", trueValue).add("falseValue", falseValue);
                }
                return new KeyAndArguments(key, arguments);
            } else {
                assert replacer.instanceOf instanceof InstanceOfDynamicNode;
                InstanceOfDynamicNode instanceOf = (InstanceOfDynamicNode) replacer.instanceOf;
                ValueNode trueValue = replacer.trueValue;
                ValueNode falseValue = replacer.falseValue;
                ValueNode object = instanceOf.object();
                ValueNode mirror = instanceOf.mirror();
                boolean checkNull = !object.stamp().nonNull();
                Key key = new Key(instanceofDynamic).add("checkNull", checkNull);
                Arguments arguments = arguments("mirror", mirror).add("object", object).add("trueValue", trueValue).add("falseValue", falseValue);
                return new KeyAndArguments(key, arguments);
            }
        }
    }
}
