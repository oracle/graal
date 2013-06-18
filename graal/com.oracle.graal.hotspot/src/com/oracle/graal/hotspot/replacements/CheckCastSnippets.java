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

import static com.oracle.graal.api.code.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.*;
import static com.oracle.graal.nodes.extended.UnsafeCastNode.*;
import static com.oracle.graal.phases.GraalOptions.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;
import static com.oracle.graal.replacements.nodes.BranchProbabilityNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.Snippet.VarargsParameter;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

/**
 * Snippets used for implementing the type test of a checkcast instruction.
 * 
 * The type tests implemented are described in the paper <a
 * href="http://dl.acm.org/citation.cfm?id=583821"> Fast subtype checking in the HotSpot JVM</a> by
 * Cliff Click and John Rose.
 */
public class CheckCastSnippets implements Snippets {

    @NodeIntrinsic(BreakpointNode.class)
    static native void bkpt(Object object, Word hub, Word objectHub);

    /**
     * Type test used when the type being tested against is a final type.
     */
    @Snippet
    public static Object checkcastExact(Object object, Word exactHub, @ConstantParameter boolean checkNull) {
        if (checkNull && probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
        } else {
            Word objectHub = loadHub(object);
            if (objectHub.notEqual(exactHub)) {
                exactMiss.inc();
                DeoptimizeNode.deopt(InvalidateReprofile, ClassCastException);
            }
            exactHit.inc();
        }
        /*
         * make sure that the unsafeCast is done *after* the check above, cf. ReadAfterCheckCast
         */
        BeginNode anchorNode = BeginNode.anchor(StampFactory.forNodeIntrinsic());
        return unsafeCast(verifyOop(object), StampFactory.forNodeIntrinsic(), anchorNode);
    }

    /**
     * Type test used when the type being tested against is a restricted primary type.
     * 
     * This test ignores use of hints altogether as the display-based type check only involves one
     * extra load where the second load should hit the same cache line as the first.
     */
    @Snippet
    public static Object checkcastPrimary(Word hub, Object object, @ConstantParameter int superCheckOffset, @ConstantParameter boolean checkNull) {
        if (checkNull && probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
        } else {
            Word objectHub = loadHub(object);
            if (objectHub.readWord(superCheckOffset, LocationIdentity.FINAL_LOCATION).notEqual(hub)) {
                displayMiss.inc();
                DeoptimizeNode.deopt(InvalidateReprofile, ClassCastException);
            }
            displayHit.inc();
        }
        BeginNode anchorNode = BeginNode.anchor(StampFactory.forNodeIntrinsic());
        return unsafeCast(verifyOop(object), StampFactory.forNodeIntrinsic(), anchorNode);
    }

    /**
     * Type test used when the type being tested against is a restricted secondary type.
     */
    @Snippet
    public static Object checkcastSecondary(Word hub, Object object, @VarargsParameter Word[] hints, @ConstantParameter boolean checkNull) {
        if (checkNull && probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
        } else {
            Word objectHub = loadHub(object);
            // if we get an exact match: succeed immediately
            ExplodeLoopNode.explodeLoop();
            for (int i = 0; i < hints.length; i++) {
                Word hintHub = hints[i];
                if (hintHub.equal(objectHub)) {
                    hintsHit.inc();
                    BeginNode anchorNode = BeginNode.anchor(StampFactory.forNodeIntrinsic());
                    return unsafeCast(verifyOop(object), StampFactory.forNodeIntrinsic(), anchorNode);
                }
            }
            if (!checkSecondarySubType(hub, objectHub)) {
                DeoptimizeNode.deopt(InvalidateReprofile, ClassCastException);
            }
        }
        BeginNode anchorNode = BeginNode.anchor(StampFactory.forNodeIntrinsic());
        return unsafeCast(verifyOop(object), StampFactory.forNodeIntrinsic(), anchorNode);
    }

    /**
     * Type test used when the type being tested against is not known at compile time (e.g. the type
     * test in an object array store check).
     */
    @Snippet
    public static Object checkcastDynamic(Word hub, Object object, @ConstantParameter boolean checkNull) {
        if (checkNull && probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
        } else {
            Word objectHub = loadHub(object);
            if (!checkUnknownSubType(hub, objectHub)) {
                DeoptimizeNode.deopt(InvalidateReprofile, ClassCastException);
            }
        }
        BeginNode anchorNode = BeginNode.anchor(StampFactory.forNodeIntrinsic());
        return unsafeCast(verifyOop(object), StampFactory.forNodeIntrinsic(), anchorNode);
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo exact = snippet(CheckCastSnippets.class, "checkcastExact");
        private final SnippetInfo primary = snippet(CheckCastSnippets.class, "checkcastPrimary");
        private final SnippetInfo secondary = snippet(CheckCastSnippets.class, "checkcastSecondary");
        private final SnippetInfo dynamic = snippet(CheckCastSnippets.class, "checkcastDynamic");

        public Templates(CodeCacheProvider runtime, Replacements replacements, TargetDescription target) {
            super(runtime, replacements, target);
        }

        /**
         * Lowers a checkcast node.
         */
        public void lower(CheckCastNode checkcast, LoweringTool tool) {
            StructuredGraph graph = checkcast.graph();
            ValueNode object = checkcast.object();
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) checkcast.type();
            TypeCheckHints hintInfo = new TypeCheckHints(checkcast.type(), checkcast.profile(), tool.assumptions(), CheckcastMinHintHitProbability.getValue(), CheckcastMaxHints.getValue());
            ValueNode hub = ConstantNode.forConstant(type.klass(), runtime, checkcast.graph());

            Arguments args;
            if (hintInfo.exact != null) {
                args = new Arguments(exact);
                args.add("object", object);
                args.add("exactHub", ConstantNode.forConstant(((HotSpotResolvedObjectType) hintInfo.exact).klass(), runtime, graph));
            } else if (type.isPrimaryType()) {
                args = new Arguments(primary);
                args.add("hub", hub);
                args.add("object", object);
                args.addConst("superCheckOffset", type.superCheckOffset());
            } else {
                ConstantNode[] hints = createHints(hintInfo, runtime, true, graph).hubs;
                args = new Arguments(secondary);
                args.add("hub", hub);
                args.add("object", object);
                args.addVarargs("hints", Word.class, StampFactory.forKind(getWordKind()), hints);
            }
            args.addConst("checkNull", !object.stamp().nonNull());

            SnippetTemplate template = template(args);
            Debug.log("Lowering checkcast in %s: node=%s, template=%s, arguments=%s", graph, checkcast, template, args);
            template.instantiate(runtime, checkcast, DEFAULT_REPLACER, args);
        }

        /**
         * Lowers a dynamic checkcast node.
         */
        public void lower(CheckCastDynamicNode checkcast) {
            StructuredGraph graph = checkcast.graph();
            ValueNode object = checkcast.object();

            Arguments args = new Arguments(dynamic);
            args.add("hub", checkcast.type());
            args.add("object", object);
            args.addConst("checkNull", !object.stamp().nonNull());

            SnippetTemplate template = template(args);
            Debug.log("Lowering dynamic checkcast in %s: node=%s, template=%s, arguments=%s", graph, checkcast, template, args);
            template.instantiate(runtime, checkcast, DEFAULT_REPLACER, args);
        }
    }
}
