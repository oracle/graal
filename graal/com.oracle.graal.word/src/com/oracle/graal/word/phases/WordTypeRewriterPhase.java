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
package com.oracle.graal.word.phases;

import static com.oracle.graal.api.meta.LocationIdentity.*;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.word.*;
import com.oracle.graal.word.Word.Opcode;
import com.oracle.graal.word.Word.Operation;

/**
 * Transforms all uses of the {@link Word} class into unsigned operations on {@code int} or
 * {@code long} values, depending on the word kind of the underlying platform.
 */
public class WordTypeRewriterPhase extends Phase {

    private final MetaAccessProvider metaAccess;
    private final ResolvedJavaType wordBaseType;
    private final ResolvedJavaType wordImplType;
    private final Kind wordKind;

    public WordTypeRewriterPhase(MetaAccessProvider metaAccess, Kind wordKind) {
        this.metaAccess = metaAccess;
        this.wordKind = wordKind;
        this.wordBaseType = metaAccess.lookupJavaType(WordBase.class);
        this.wordImplType = metaAccess.lookupJavaType(Word.class);
    }

    public ResolvedJavaType getWordBaseType() {
        return wordBaseType;
    }

    public ResolvedJavaType getWordImplType() {
        return wordImplType;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (Node n : GraphOrder.forwardGraph(graph)) {
            if (n instanceof ValueNode && !(n instanceof PhiNode && ((PhiNode) n).isLoopPhi())) {
                ValueNode valueNode = (ValueNode) n;
                if (isWord(valueNode)) {
                    changeToWord(valueNode);
                }
            }
        }
        for (PhiNode phi : graph.getNodes(PhiNode.class)) {
            if (phi.isLoopPhi() && isWord(phi)) {
                changeToWord(phi);
            }
        }

        // Remove casts between different word types (which by now no longer have kind Object)
        for (CheckCastNode checkCastNode : graph.getNodes().filter(CheckCastNode.class).snapshot()) {
            if (!checkCastNode.isDeleted() && checkCastNode.kind() == wordKind) {
                checkCastNode.replaceAtUsages(checkCastNode.object());
                graph.removeFixed(checkCastNode);
            }
        }

        // Remove unnecessary/redundant unsafe casts
        for (UnsafeCastNode unsafeCastNode : graph.getNodes().filter(UnsafeCastNode.class).snapshot()) {
            if (!unsafeCastNode.isDeleted() && unsafeCastNode.object().stamp() == unsafeCastNode.stamp()) {
                graph.replaceFloating(unsafeCastNode, unsafeCastNode.object());
            }
        }

        // Fold constant field reads (e.g. enum constants)
        for (LoadFieldNode load : graph.getNodes(LoadFieldNode.class).snapshot()) {
            ConstantNode constant = load.asConstant(metaAccess);
            if (constant != null) {
                graph.replaceFixedWithFloating(load, constant);
            }
        }

        // Replace ObjectEqualsNodes with IntegerEqualsNodes where the values being compared are
        // words
        for (ObjectEqualsNode objectEqualsNode : graph.getNodes().filter(ObjectEqualsNode.class).snapshot()) {
            ValueNode x = objectEqualsNode.x();
            ValueNode y = objectEqualsNode.y();
            if (x.kind() == wordKind || y.kind() == wordKind) {
                assert x.kind() == wordKind;
                assert y.kind() == wordKind;

                // TODO Remove the whole iteration of ObjectEqualsNodes when we are sure that there
                // is no more code where this triggers.
                throw GraalInternalError.shouldNotReachHere("Comparison of words with == and != is no longer supported");
            }
        }

        for (AccessIndexedNode node : graph.getNodes().filter(AccessIndexedNode.class).snapshot()) {
            ValueNode array = node.array();
            if (array.objectStamp().type() == null) {
                // There are cases where the array does not have a known type yet. Assume it is not
                // a word type.
                continue;
            }
            assert array.objectStamp().type().isArray();
            if (isWord(array.objectStamp().type().getComponentType())) {
                /*
                 * The elementKind of the node is a final field, and other information such as the
                 * stamp depends on elementKind. Therefore, just create a new node and replace the
                 * old one.
                 */
                if (node instanceof LoadIndexedNode) {
                    graph.replaceFixedWithFixed(node, graph.add(new LoadIndexedNode(node.array(), node.index(), wordKind)));
                } else if (node instanceof StoreIndexedNode) {
                    graph.replaceFixedWithFixed(node, graph.add(new StoreIndexedNode(node.array(), node.index(), wordKind, ((StoreIndexedNode) node).value())));
                } else {
                    throw GraalInternalError.shouldNotReachHere();
                }
            }
        }

        for (MethodCallTargetNode callTargetNode : graph.getNodes(MethodCallTargetNode.class).snapshot()) {
            ResolvedJavaMethod targetMethod = callTargetNode.targetMethod();
            if (!callTargetNode.isStatic() && (callTargetNode.receiver().kind() == wordKind || isWord(callTargetNode.receiver()))) {
                targetMethod = getWordImplType().resolveMethod(targetMethod);
            }
            Operation operation = targetMethod.getAnnotation(Word.Operation.class);
            if (operation != null) {
                NodeInputList<ValueNode> arguments = callTargetNode.arguments();
                Invoke invoke = (Invoke) callTargetNode.usages().first();
                assert invoke != null : callTargetNode.targetMethod();

                switch (operation.opcode()) {
                    case NODE_CLASS:
                        assert arguments.size() == 2;
                        ValueNode left = arguments.get(0);
                        ValueNode right = operation.rightOperandIsInt() ? toUnsigned(graph, arguments.get(1), Kind.Int) : fromSigned(graph, arguments.get(1));
                        replace(invoke, nodeClassOp(graph, operation.node(), left, right, invoke));
                        break;

                    case COMPARISON:
                        assert arguments.size() == 2;
                        replace(invoke, comparisonOp(graph, operation.condition(), arguments.get(0), fromSigned(graph, arguments.get(1))));
                        break;

                    case NOT:
                        assert arguments.size() == 1;
                        replace(invoke, graph.unique(new XorNode(wordKind, arguments.get(0), ConstantNode.forIntegerKind(wordKind, -1, graph))));
                        break;

                    case READ: {
                        assert arguments.size() == 2 || arguments.size() == 3;
                        Kind readKind = asKind(callTargetNode.returnType());
                        LocationNode location;
                        if (arguments.size() == 2) {
                            location = makeLocation(graph, arguments.get(1), readKind, ANY_LOCATION);
                        } else {
                            location = makeLocation(graph, arguments.get(1), readKind, arguments.get(2));
                        }
                        replace(invoke, readOp(graph, arguments.get(0), invoke, location, BarrierType.NONE, false));
                        break;
                    }
                    case READ_HEAP: {
                        assert arguments.size() == 4;
                        Kind readKind = asKind(callTargetNode.returnType());
                        LocationNode location = makeLocation(graph, arguments.get(1), readKind, ANY_LOCATION);
                        BarrierType barrierType = (BarrierType) arguments.get(2).asConstant().asObject();
                        replace(invoke, readOp(graph, arguments.get(0), invoke, location, barrierType, arguments.get(3).asConstant().asInt() == 0 ? false : true));
                        break;
                    }
                    case WRITE:
                    case INITIALIZE: {
                        assert arguments.size() == 3 || arguments.size() == 4;
                        Kind writeKind = asKind(targetMethod.getSignature().getParameterType(1, targetMethod.getDeclaringClass()));
                        LocationNode location;
                        if (arguments.size() == 3) {
                            location = makeLocation(graph, arguments.get(1), writeKind, LocationIdentity.ANY_LOCATION);
                        } else {
                            location = makeLocation(graph, arguments.get(1), writeKind, arguments.get(3));
                        }
                        replace(invoke, writeOp(graph, arguments.get(0), arguments.get(2), invoke, location, operation.opcode()));
                        break;
                    }
                    case ZERO:
                        assert arguments.size() == 0;
                        replace(invoke, ConstantNode.forIntegerKind(wordKind, 0L, graph));
                        break;

                    case FROM_UNSIGNED:
                        assert arguments.size() == 1;
                        replace(invoke, fromUnsigned(graph, arguments.get(0)));
                        break;

                    case FROM_SIGNED:
                        assert arguments.size() == 1;
                        replace(invoke, fromSigned(graph, arguments.get(0)));
                        break;

                    case TO_RAW_VALUE:
                        assert arguments.size() == 1;
                        replace(invoke, toUnsigned(graph, arguments.get(0), Kind.Long));
                        break;

                    case FROM_OBJECT:
                        assert arguments.size() == 1;
                        replace(invoke, graph.unique(new UnsafeCastNode(arguments.get(0), StampFactory.forKind(wordKind))));
                        break;

                    case FROM_ARRAY:
                        assert arguments.size() == 2;
                        replace(invoke, graph.unique(new ComputeAddressNode(arguments.get(0), arguments.get(1), StampFactory.forKind(wordKind))));
                        break;

                    case TO_OBJECT:
                        assert arguments.size() == 1;
                        replace(invoke, graph.unique(new UnsafeCastNode(arguments.get(0), invoke.asNode().stamp())));
                        break;

                    default:
                        throw new GraalInternalError("Unknown opcode: %s", operation.opcode());
                }
            }
        }
    }

    private ValueNode fromUnsigned(StructuredGraph graph, ValueNode value) {
        return convert(graph, value, wordKind, ConvertNode.Op.L2I, ConvertNode.Op.UNSIGNED_I2L);
    }

    private ValueNode fromSigned(StructuredGraph graph, ValueNode value) {
        return convert(graph, value, wordKind, ConvertNode.Op.L2I, ConvertNode.Op.I2L);
    }

    private static ValueNode toUnsigned(StructuredGraph graph, ValueNode value, Kind toKind) {
        return convert(graph, value, toKind, ConvertNode.Op.L2I, ConvertNode.Op.UNSIGNED_I2L);
    }

    private static ValueNode convert(StructuredGraph graph, ValueNode value, Kind toKind, ConvertNode.Op longToIntOp, ConvertNode.Op intToLongOp) {
        assert longToIntOp.from == Kind.Long && longToIntOp.to == Kind.Int;
        assert intToLongOp.from == Kind.Int && intToLongOp.to == Kind.Long;
        if (value.kind() == toKind) {
            return value;
        }

        if (toKind == Kind.Int) {
            assert value.kind() == Kind.Long;
            return graph.unique(new ConvertNode(longToIntOp, value));
        } else {
            assert toKind == Kind.Long;
            assert value.kind().getStackKind() == Kind.Int;
            return graph.unique(new ConvertNode(intToLongOp, value));
        }
    }

    private ValueNode nodeClassOp(StructuredGraph graph, Class<? extends ValueNode> nodeClass, ValueNode left, ValueNode right, Invoke invoke) {
        try {
            Constructor<? extends ValueNode> constructor = nodeClass.getConstructor(Kind.class, ValueNode.class, ValueNode.class);
            ValueNode result = graph.add(constructor.newInstance(wordKind, left, right));
            if (result instanceof FixedWithNextNode) {
                graph.addBeforeFixed(invoke.asNode(), (FixedWithNextNode) result);
            }
            return result;
        } catch (Throwable ex) {
            throw new GraalInternalError(ex).addContext(nodeClass.getName());
        }
    }

    private ValueNode comparisonOp(StructuredGraph graph, Condition condition, ValueNode left, ValueNode right) {
        assert left.kind() == wordKind && right.kind() == wordKind;

        // mirroring gets the condition into canonical form
        boolean mirror = condition.canonicalMirror();

        ValueNode a = mirror ? right : left;
        ValueNode b = mirror ? left : right;

        CompareNode comparison;
        if (condition == Condition.EQ || condition == Condition.NE) {
            comparison = new IntegerEqualsNode(a, b);
        } else if (condition.isUnsigned()) {
            comparison = new IntegerBelowThanNode(a, b);
        } else {
            comparison = new IntegerLessThanNode(a, b);
        }

        ConstantNode trueValue = ConstantNode.forInt(1, graph);
        ConstantNode falseValue = ConstantNode.forInt(0, graph);

        if (condition.canonicalNegate()) {
            ConstantNode temp = trueValue;
            trueValue = falseValue;
            falseValue = temp;
        }
        ConditionalNode materialize = graph.unique(new ConditionalNode(graph.unique(comparison), trueValue, falseValue));
        return materialize;
    }

    private LocationNode makeLocation(StructuredGraph graph, ValueNode offset, Kind readKind, ValueNode locationIdentity) {
        if (locationIdentity.isConstant()) {
            return makeLocation(graph, offset, readKind, (LocationIdentity) locationIdentity.asConstant().asObject());
        }
        return SnippetLocationNode.create(locationIdentity, ConstantNode.forObject(readKind, metaAccess, graph), ConstantNode.forLong(0, graph), offset, ConstantNode.forInt(1, graph), graph);
    }

    private static LocationNode makeLocation(StructuredGraph graph, ValueNode offset, Kind readKind, LocationIdentity locationIdentity) {
        return IndexedLocationNode.create(locationIdentity, readKind, 0, offset, graph, 1);
    }

    private static ValueNode readOp(StructuredGraph graph, ValueNode base, Invoke invoke, LocationNode location, BarrierType barrierType, boolean compressible) {
        ReadNode read = graph.add(new ReadNode(base, location, invoke.asNode().stamp(), barrierType, compressible));
        graph.addBeforeFixed(invoke.asNode(), read);
        // The read must not float outside its block otherwise it may float above an explicit zero
        // check on its base address
        read.setGuard(AbstractBeginNode.prevBegin(invoke.asNode()));
        return read;
    }

    private static ValueNode writeOp(StructuredGraph graph, ValueNode base, ValueNode value, Invoke invoke, LocationNode location, Opcode op) {
        assert op == Opcode.WRITE || op == Opcode.INITIALIZE;
        WriteNode write = graph.add(new WriteNode(base, value, location, BarrierType.NONE, false, op == Opcode.WRITE));
        write.setStateAfter(invoke.stateAfter());
        graph.addBeforeFixed(invoke.asNode(), write);
        return write;
    }

    private static void replace(Invoke invoke, ValueNode value) {
        FixedNode next = invoke.next();
        invoke.setNext(null);
        invoke.asNode().replaceAtPredecessor(next);
        invoke.asNode().replaceAtUsages(value);
        GraphUtil.killCFG(invoke.asNode());
    }

    public boolean isWord(ValueNode node) {
        /*
         * If we already know that we have a word type, we do not need to infer the stamp. This
         * avoids exceptions in inferStamp when the inputs have already been rewritten to word,
         * i.e., when the expected input is no longer an object.
         */
        if (isWord0(node)) {
            return true;
        }
        node.inferStamp();
        return isWord0(node);
    }

    private boolean isWord0(ValueNode node) {
        if (node.stamp() == StampFactory.forWord()) {
            return true;
        }
        if (node.kind() == Kind.Object) {
            return isWord(node.objectStamp().type());
        }
        return false;
    }

    public boolean isWord(ResolvedJavaType type) {
        if (type != null && wordBaseType.isAssignableFrom(type)) {
            return true;
        }
        return false;
    }

    public Kind asKind(JavaType type) {
        if (type instanceof ResolvedJavaType) {
            return isWord((ResolvedJavaType) type) ? wordKind : type.getKind();
        } else {
            return Kind.Object;
        }
    }

    private void changeToWord(ValueNode valueNode) {
        if (valueNode.isConstant() && valueNode.asConstant().getKind() == Kind.Object) {
            WordBase value = (WordBase) valueNode.asConstant().asObject();
            ConstantNode newConstant = ConstantNode.forIntegerKind(wordKind, value.rawValue(), valueNode.graph());
            valueNode.graph().replaceFloating((ConstantNode) valueNode, newConstant);
        } else {
            assert !(valueNode instanceof ConstantNode) : "boxed Word constants should not appear in a snippet graph: " + valueNode + ", stamp: " + valueNode.stamp();
            valueNode.setStamp(StampFactory.forKind(wordKind));
        }
    }
}
