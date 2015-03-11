/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.word;

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.hotspot.word.HotSpotOperation.HotspotOpcode.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.hotspot.word.HotSpotOperation.HotspotOpcode;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.word.Word.Operation;
import com.oracle.graal.word.phases.*;

public class HotSpotWordTypeRewriterPhase extends WordTypeRewriterPhase {

    public HotSpotWordTypeRewriterPhase(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, ConstantReflectionProvider constantReflection, Kind wordKind) {
        super(metaAccess, snippetReflection, constantReflection, new HotSpotWordTypes(metaAccess, wordKind));
    }

    @Override
    protected void rewriteAccessIndexed(StructuredGraph graph, AccessIndexedNode node) {
        if (node.stamp() instanceof MetaspacePointerStamp && node instanceof LoadIndexedNode && node.elementKind() != Kind.Illegal) {
            /*
             * Prevent rewriting of the MetaspacePointerStamp in the CanonicalizerPhase.
             */
            graph.replaceFixedWithFixed(node, graph.add(new LoadIndexedPointerNode(node.stamp(), node.array(), node.index())));
        } else {
            super.rewriteAccessIndexed(graph, node);
        }
    }

    /**
     * Intrinsification of methods that are annotated with {@link Operation} or
     * {@link HotSpotOperation}.
     */
    @Override
    protected void rewriteInvoke(StructuredGraph graph, MethodCallTargetNode callTargetNode) {
        ResolvedJavaMethod targetMethod = callTargetNode.targetMethod();
        HotSpotOperation operation = targetMethod.getAnnotation(HotSpotOperation.class);
        if (operation == null) {
            super.rewriteInvoke(graph, callTargetNode);
        } else {
            Invoke invoke = callTargetNode.invoke();
            NodeInputList<ValueNode> arguments = callTargetNode.arguments();

            switch (operation.opcode()) {
                case POINTER_EQ:
                case POINTER_NE:
                    assert arguments.size() == 2;
                    replace(invoke, pointerComparisonOp(graph, operation.opcode(), arguments.get(0), arguments.get(1)));
                    break;

                case IS_NULL:
                    assert arguments.size() == 1;
                    replace(invoke, pointerIsNullOp(graph, arguments.get(0)));
                    break;

                case FROM_POINTER:
                    assert arguments.size() == 1;
                    replace(invoke, graph.unique(new PointerCastNode(StampFactory.forKind(wordTypes.getWordKind()), arguments.get(0))));
                    break;

                case TO_KLASS_POINTER:
                    assert arguments.size() == 1;
                    replace(invoke, graph.unique(new PointerCastNode(KlassPointerStamp.klass(), arguments.get(0))));
                    break;

                case TO_METHOD_POINTER:
                    assert arguments.size() == 1;
                    replace(invoke, graph.unique(new PointerCastNode(MethodPointerStamp.method(), arguments.get(0))));
                    break;

                case READ_KLASS_POINTER:
                    assert arguments.size() == 2 || arguments.size() == 3;
                    Stamp readStamp = KlassPointerStamp.klass();
                    LocationNode location;
                    if (arguments.size() == 2) {
                        location = makeLocation(graph, arguments.get(1), ANY_LOCATION);
                    } else {
                        location = makeLocation(graph, arguments.get(1), arguments.get(2));
                    }
                    replace(invoke, readKlassOp(graph, arguments.get(0), invoke, location, readStamp, operation.opcode()));
                    break;

                default:
                    throw GraalInternalError.shouldNotReachHere("unknown operation: " + operation.opcode());
            }
        }
    }

    protected ValueNode readKlassOp(StructuredGraph graph, ValueNode base, Invoke invoke, LocationNode location, Stamp readStamp, HotspotOpcode op) {
        assert op == READ_KLASS_POINTER;
        final BarrierType barrier = BarrierType.NONE;

        ReadNode read = graph.add(new ReadNode(base, location, readStamp, barrier));
        graph.addBeforeFixed(invoke.asNode(), read);
        /*
         * The read must not float outside its block otherwise it may float above an explicit zero
         * check on its base address.
         */
        read.setGuard(AbstractBeginNode.prevBegin(invoke.asNode()));
        return read;
    }

    private static ValueNode pointerComparisonOp(StructuredGraph graph, HotspotOpcode opcode, ValueNode left, ValueNode right) {
        assert left.stamp() instanceof MetaspacePointerStamp && right.stamp() instanceof MetaspacePointerStamp;
        assert opcode == POINTER_EQ || opcode == POINTER_NE;

        PointerEqualsNode comparison = graph.unique(new PointerEqualsNode(left, right));
        ValueNode eqValue = ConstantNode.forBoolean(opcode == POINTER_EQ, graph);
        ValueNode neValue = ConstantNode.forBoolean(opcode == POINTER_NE, graph);
        return graph.unique(new ConditionalNode(comparison, eqValue, neValue));
    }

    private static ValueNode pointerIsNullOp(StructuredGraph graph, ValueNode pointer) {
        assert pointer.stamp() instanceof MetaspacePointerStamp;

        IsNullNode isNull = graph.unique(new IsNullNode(pointer));
        return graph.unique(new ConditionalNode(isNull, ConstantNode.forBoolean(true, graph), ConstantNode.forBoolean(false, graph)));
    }
}
