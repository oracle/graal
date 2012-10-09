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
package com.oracle.graal.snippets;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.calc.ConvertNode.Op;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.snippets.Word.Opcode;
import com.oracle.graal.snippets.Word.Operation;

/**
 * Transforms all uses of the {@link Word} class into unsigned
 * operations on {@code int} or {@code long} values, depending
 * on the word kind of the underlying platform.
 */
public class WordTypeRewriterPhase extends Phase {

    private final Kind wordKind;
    private final ResolvedJavaType wordType;

    public WordTypeRewriterPhase(Kind wordKind, ResolvedJavaType wordType) {
        this.wordKind = wordKind;
        this.wordType = wordType;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (Node n : graph.getNodes()) {
            if (n instanceof ValueNode) {
                ValueNode valueNode = (ValueNode) n;
                if (isWord(valueNode)) {
                    changeToWord(valueNode);
                }
            }
        }

        // Remove unnecessary/redundant unsafe casts
        for (UnsafeCastNode unsafeCastNode : graph.getNodes().filter(UnsafeCastNode.class).snapshot()) {
            if (!unsafeCastNode.isDeleted() && unsafeCastNode.object().stamp() == unsafeCastNode.stamp()) {
                graph.replaceFloating(unsafeCastNode, unsafeCastNode.object());
            }
        }

        // Replace ObjectEqualsNodes with IntegerEqualsNodes where the values being compared are words
        for (ObjectEqualsNode objectEqualsNode : graph.getNodes().filter(ObjectEqualsNode.class).snapshot()) {
            ValueNode x = objectEqualsNode.x();
            ValueNode y = objectEqualsNode.y();
            if (x.kind() == wordKind || y.kind() == wordKind) {
                assert x.kind() == wordKind;
                assert y.kind() == wordKind;
                graph.replaceFloating(objectEqualsNode, graph.unique(new IntegerEqualsNode(x, y)));
            }
        }

        for (MethodCallTargetNode callTargetNode : graph.getNodes(MethodCallTargetNode.class).snapshot()) {
            ResolvedJavaMethod targetMethod = callTargetNode.targetMethod();
            Operation operation = targetMethod.getAnnotation(Word.Operation.class);
            if (operation != null) {
                NodeInputList<ValueNode> arguments = callTargetNode.arguments();
                Invoke invoke = (Invoke) callTargetNode.usages().first();
                assert invoke != null : callTargetNode.targetMethod();

                Opcode opcode = operation.value();
                switch (opcode) {
                    case ZERO: {
                        assert arguments.size() == 0;
                        replace(invoke, wordKind.isLong() ? ConstantNode.forLong(0L, graph) : ConstantNode.forInt(0, graph));
                        break;
                    }

                    case ABOVE: {
                        assert arguments.size() == 2;
                        replace(invoke, compare(Condition.AT, graph, arguments.first(), arguments.last()));
                        break;
                    }

                    case ABOVE_EQUAL: {
                        assert arguments.size() == 2;
                        replace(invoke, compare(Condition.AE, graph, arguments.first(), arguments.last()));
                        break;
                    }

                    case BELOW: {
                        assert arguments.size() == 2;
                        replace(invoke, compare(Condition.BT, graph, arguments.first(), arguments.last()));
                        break;
                    }

                    case BELOW_EQUAL: {
                        assert arguments.size() == 2;
                        replace(invoke, compare(Condition.BE, graph, arguments.first(), arguments.last()));
                        break;
                    }

                    case PLUS: {
                        ValueNode addend = asWordKind(graph, arguments.last());
                        IntegerAddNode op = graph.unique(new IntegerAddNode(wordKind, arguments.first(), addend));
                        replace(invoke, op);
                        break;
                    }

                    case MINUS: {
                        ValueNode addend = asWordKind(graph, arguments.last());
                        IntegerSubNode op = graph.unique(new IntegerSubNode(wordKind, arguments.first(), addend));
                        replace(invoke, op);
                        break;
                    }

                    case AND: {
                        ValueNode operand = asWordKind(graph, arguments.last());
                        AndNode op = graph.unique(new AndNode(wordKind, arguments.first(), operand));
                        replace(invoke, op);
                        break;
                    }

                    case OR: {
                        ValueNode operand = asWordKind(graph, arguments.last());
                        OrNode op = graph.unique(new OrNode(wordKind, arguments.first(), operand));
                        replace(invoke, op);
                        break;
                    }

                    case XOR: {
                        ValueNode operand = asWordKind(graph, arguments.last());
                        XorNode op = graph.unique(new XorNode(wordKind, arguments.first(), operand));
                        replace(invoke, op);
                        break;
                    }

                    case W2A: {
                        assert arguments.size() == 1;
                        ValueNode value = arguments.first();
                        ResolvedJavaType targetType = (ResolvedJavaType) targetMethod.getSignature().getReturnType(targetMethod.getDeclaringClass());
                        UnsafeCastNode cast = graph.unique(new UnsafeCastNode(value, targetType));
                        replace(invoke, cast);
                        break;
                    }

                    case W2I: {
                        assert arguments.size() == 1;
                        ValueNode value = arguments.first();
                        ValueNode intValue = fromWordKindTo(graph, value, Kind.Int);
                        replace(invoke, intValue);
                        break;
                    }

                    case W2L: {
                        assert arguments.size() == 1;
                        ValueNode value = arguments.first();
                        ValueNode longValue = fromWordKindTo(graph, value, Kind.Long);
                        replace(invoke, longValue);
                        break;
                    }

                    case A2W: {
                        assert arguments.size() == 1;
                        ValueNode value = arguments.first();
                        assert value.kind() == Kind.Object : value + ", " + targetMethod;
                        UnsafeCastNode cast = graph.unique(new UnsafeCastNode(value, wordType));
                        replace(invoke, cast);
                        break;
                    }

                    case L2W: {
                        assert arguments.size() == 1;
                        ValueNode value = arguments.first();
                        assert value.kind() == Kind.Long;
                        ValueNode wordValue = asWordKind(graph, value);
                        replace(invoke, wordValue);
                        break;
                    }

                    case I2W: {
                        assert arguments.size() == 1;
                        ValueNode value = arguments.first();
                        assert value.kind() == Kind.Int;
                        replace(invoke, asWordKind(graph, value));
                        break;
                    }

                    default: {
                        throw new GraalInternalError("Unknown opcode: %s", opcode);
                    }
                }
            }
        }
    }

    protected void replace(Invoke invoke, ValueNode value) {
        FixedNode next = invoke.next();
        invoke.setNext(null);
        invoke.node().replaceAtPredecessor(next);
        invoke.node().replaceAtUsages(value);
        GraphUtil.killCFG(invoke.node());
    }

    /**
     * Creates comparison node for a given condition and two input values.
     */
    private ValueNode compare(Condition condition, StructuredGraph graph, ValueNode left, ValueNode right) {
        assert condition.isUnsigned() : condition;
        assert left.kind() == wordKind;
        assert right.kind() == wordKind;

        // mirroring gets the condition into canonical form
        boolean mirror = condition.canonicalMirror();

        ValueNode a = mirror ? right : left;
        ValueNode b = mirror ? left : right;

        MaterializeNode materialize = MaterializeNode.create(graph.unique(new IntegerBelowThanNode(a, b)));

        ValueNode op;
        if (condition.canonicalNegate()) {
            op = (ValueNode) materialize.negate();
        } else {
            op = materialize;
        }
        return op;
    }

    /**
     * Adds a node if necessary to convert a given value into the word kind.
     *
     * @return the node for {@code value} producing a value of word kind
     */
    private ValueNode asWordKind(StructuredGraph graph, ValueNode value) {
        if (value.kind() != wordKind) {
            Op op;
            if (wordKind.isLong()) {
                assert value.kind().isStackInt();
                op = Op.I2L;
            } else {
                assert wordKind.isStackInt();
                assert value.kind().isLong();
                op = Op.L2I;
            }
            return graph.unique(new ConvertNode(op, value));
        }
        return value;
    }

    private static ValueNode fromWordKindTo(StructuredGraph graph, ValueNode value, Kind to) {
        Kind from = value.kind();
        if (from != to) {
            Op op;
            if (from.isLong()) {
                op = Op.L2I;
            } else {
                assert from.isStackInt();
                op = Op.I2L;
            }
            return graph.unique(new ConvertNode(op, value));
        }
        return value;
    }

    public static boolean isWord(ValueNode node) {
        if (node.stamp() instanceof WordStamp) {
            return true;
        }
        if (node.kind().isObject()) {
            return isWord(node.objectStamp().type());
        }
        return false;
    }

    public static boolean isWord(ResolvedJavaType type) {
        if (type != null && type.toJava() == Word.class) {
            return true;
        }
        return false;
    }

    private void changeToWord(ValueNode valueNode) {
        assert !(valueNode instanceof ConstantNode);
        valueNode.setStamp(StampFactory.forKind(wordKind));

        // Propagate word kind.
        for (Node n : valueNode.usages()) {
            if (n instanceof PhiNode) {
                changeToWord((ValueNode) n);
                PhiNode phi = (PhiNode) n;
                assert phi.type() == PhiType.Value;
            } else if (n instanceof ReturnNode) {
                changeToWord((ValueNode) n);
            }
        }
    }
}
