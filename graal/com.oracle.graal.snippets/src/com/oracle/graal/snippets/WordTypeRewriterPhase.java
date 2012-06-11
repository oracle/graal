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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.calc.ConvertNode.Op;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.snippets.Word.Opcode;
import com.oracle.graal.snippets.Word.Operation;

/**
 * Transforms all uses of the {@link Word} class into unsigned
 * operations on {@code int} or {@code long} values, depending
 * on the word kind of the underlying platform.
 */
public class WordTypeRewriterPhase extends Phase {

    private final Kind wordKind;

    public WordTypeRewriterPhase(TargetDescription target) {
        this.wordKind = target.wordKind;
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

        // Remove all checkcasts to Word
        for (CheckCastNode checkCastNode : graph.getNodes(CheckCastNode.class).snapshot()) {
            if (!checkCastNode.object().stamp().kind().isObject()) {
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

        for (MethodCallTargetNode callTargetNode : graph.getNodes(MethodCallTargetNode.class).snapshot()) {
            ResolvedJavaMethod targetMethod = callTargetNode.targetMethod();
            Operation operation = targetMethod.getAnnotation(Word.Operation.class);
            if (operation != null) {
                NodeInputList<ValueNode> arguments = callTargetNode.arguments();
                Invoke invoke = (Invoke) callTargetNode.usages().first();
                assert invoke != null;

                Opcode opcode = operation.value();
                switch (opcode) {
                    case COMPARE: {
                        assert arguments.size() == 3;
                        assert arguments.get(1) instanceof ConstantNode;
                        Condition condition = (Condition) arguments.get(1).asConstant().asObject();
                        invoke.intrinsify(compare(condition, graph, arguments.first(), arguments.last()));
                        break;
                    }

                    case PLUS: {
                        ValueNode addend = asWordKind(graph, arguments.last());
                        IntegerAddNode op = graph.unique(new IntegerAddNode(wordKind, arguments.first(), addend));
                        invoke.intrinsify(op);
                        break;
                    }
                    default: {
                        throw new GraalInternalError("Unknown opcode: %s", opcode);
                    }
                }
            }
        }
    }

    /**
     * Creates comparison node for a given condition and two input values.
     */
    private ValueNode compare(Condition condition, StructuredGraph graph, ValueNode left, ValueNode right) {
        assert condition.isUnsigned();
        assert left.kind() == wordKind;
        assert right.kind() == wordKind;

        // mirroring gets the condition into canonical form
        boolean mirror = condition.canonicalMirror();

        ValueNode a = mirror ? right : left;
        ValueNode b = mirror ? left : right;

        MaterializeNode materialize;
        if (condition == Condition.EQ || condition == Condition.NE) {
            materialize = MaterializeNode.create(graph.unique(new IntegerEqualsNode(a, b)), graph);
        } else {
            materialize = MaterializeNode.create(graph.unique(new IntegerBelowThanNode(a, b)), graph);
        }

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
                assert value.kind().isInt();
                op = Op.I2L;
            } else {
                assert wordKind.isInt();
                assert value.kind().isLong();
                op = Op.L2I;
            }
            return graph.unique(new ConvertNode(op, value));
        }
        return value;
    }

    public boolean isWord(ValueNode node) {
        return isWord(node.stamp().declaredType());
    }

    public boolean isWord(ResolvedJavaType type) {
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
//                    for (ValueNode v : phi.values()) {
//                        assertTrue(v.kind() == phi.kind(), "all phi values must have same kind");
//                    }

            } else if (n instanceof ReturnNode) {
                changeToWord((ValueNode) n);
            }
        }
    }
}
