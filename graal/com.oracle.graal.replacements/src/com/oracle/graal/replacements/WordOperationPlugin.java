/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.nodes.ConstantNode.*;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.word.*;
import com.oracle.graal.word.Word.Opcode;
import com.oracle.graal.word.Word.Operation;
import com.oracle.graal.word.nodes.*;

/**
 * A {@link GenericInvocationPlugin} for calls to {@linkplain Operation word operations}.
 */
public class WordOperationPlugin implements GenericInvocationPlugin {
    protected final WordTypes wordTypes;
    protected final Kind wordKind;
    protected final SnippetReflectionProvider snippetReflection;

    public WordOperationPlugin(SnippetReflectionProvider snippetReflection, WordTypes wordTypes) {
        this.snippetReflection = snippetReflection;
        this.wordTypes = wordTypes;
        this.wordKind = wordTypes.getWordKind();
    }

    /**
     * Processes a call to a method if it is annotated with {@link Operation} by adding nodes to the
     * graph being built that implement the denoted operation.
     *
     * @return {@code true} iff {@code method} is annotated with {@link Operation} (and was thus
     *         processed by this method)
     */
    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (!wordTypes.isWordOperation(method)) {
            return false;
        }
        processWordOperation(b, args, wordTypes.getWordOperation(method, b.getMethod().getDeclaringClass()));
        return true;
    }

    protected void processWordOperation(GraphBuilderContext b, ValueNode[] args, ResolvedJavaMethod wordMethod) throws GraalInternalError {
        Operation operation = wordMethod.getAnnotation(Word.Operation.class);
        Kind returnKind = wordMethod.getSignature().getReturnKind();
        Kind returnStackKind = returnKind.getStackKind();
        switch (operation.opcode()) {
            case NODE_CLASS:
                assert args.length == 2;
                ValueNode left = args[0];
                ValueNode right = operation.rightOperandIsInt() ? toUnsigned(b, args[1], Kind.Int) : fromSigned(b, args[1]);

                b.addPush(returnStackKind, createBinaryNodeInstance(operation.node(), left, right));
                break;

            case COMPARISON:
                assert args.length == 2;
                b.push(returnStackKind, comparisonOp(b, operation.condition(), args[0], fromSigned(b, args[1])));
                break;

            case NOT:
                assert args.length == 1;
                b.addPush(returnStackKind, new XorNode(args[0], b.add(forIntegerKind(wordKind, -1))));
                break;

            case READ_POINTER:
            case READ_OBJECT:
            case READ_BARRIERED: {
                assert args.length == 2 || args.length == 3;
                Kind readKind = wordTypes.asKind(wordMethod.getSignature().getReturnType(wordMethod.getDeclaringClass()));
                LocationNode location;
                if (args.length == 2) {
                    location = makeLocation(b, args[1], any());
                } else {
                    location = makeLocation(b, args[1], args[2]);
                }
                b.push(returnStackKind, readOp(b, readKind, args[0], location, operation.opcode()));
                break;
            }
            case READ_HEAP: {
                assert args.length == 3;
                Kind readKind = wordTypes.asKind(wordMethod.getSignature().getReturnType(wordMethod.getDeclaringClass()));
                LocationNode location = makeLocation(b, args[1], any());
                BarrierType barrierType = snippetReflection.asObject(BarrierType.class, args[2].asJavaConstant());
                b.push(returnStackKind, readOp(b, readKind, args[0], location, barrierType, true));
                break;
            }
            case WRITE_POINTER:
            case WRITE_OBJECT:
            case WRITE_BARRIERED:
            case INITIALIZE: {
                assert args.length == 3 || args.length == 4;
                Kind writeKind = wordTypes.asKind(wordMethod.getSignature().getParameterType(wordMethod.isStatic() ? 2 : 1, wordMethod.getDeclaringClass()));
                LocationNode location;
                if (args.length == 3) {
                    location = makeLocation(b, args[1], LocationIdentity.any());
                } else {
                    location = makeLocation(b, args[1], args[3]);
                }
                writeOp(b, writeKind, args[0], args[2], location, operation.opcode());
                break;
            }
            case ZERO:
                assert args.length == 0;
                b.addPush(returnStackKind, forIntegerKind(wordKind, 0L));
                break;

            case FROM_UNSIGNED:
                assert args.length == 1;
                b.push(returnStackKind, fromUnsigned(b, args[0]));
                break;

            case FROM_SIGNED:
                assert args.length == 1;
                b.push(returnStackKind, fromSigned(b, args[0]));
                break;

            case TO_RAW_VALUE:
                assert args.length == 1;
                b.push(returnStackKind, toUnsigned(b, args[0], Kind.Long));
                break;

            case FROM_WORDBASE:
                assert args.length == 1;
                b.push(returnStackKind, args[0]);
                break;

            case FROM_OBJECT:
                assert args.length == 1;
                WordCastNode objectToWord = b.add(WordCastNode.objectToWord(args[0], wordKind));
                b.push(returnStackKind, objectToWord);
                break;

            case FROM_ARRAY:
                assert args.length == 2;
                b.addPush(returnStackKind, new ComputeAddressNode(args[0], args[1], StampFactory.forKind(wordKind)));
                break;

            case TO_OBJECT:
                assert args.length == 1;
                WordCastNode wordToObject = b.add(WordCastNode.wordToObject(args[0], wordKind));
                b.push(returnStackKind, wordToObject);
                break;

            default:
                throw new GraalInternalError("Unknown opcode: %s", operation.opcode());
        }
    }

    /**
     * Create an instance of a binary node which is used to lower {@link Word} operations. This
     * method is called for all {@link Word} operations which are annotated with @Operation(node =
     * ...) and encapsulates the reflective allocation of the node.
     */
    private static ValueNode createBinaryNodeInstance(Class<? extends ValueNode> nodeClass, ValueNode left, ValueNode right) {
        try {
            Constructor<?> cons = nodeClass.getDeclaredConstructor(ValueNode.class, ValueNode.class);
            return (ValueNode) cons.newInstance(left, right);
        } catch (Throwable ex) {
            throw new GraalInternalError(ex).addContext(nodeClass.getName());
        }
    }

    private ValueNode comparisonOp(GraphBuilderContext graph, Condition condition, ValueNode left, ValueNode right) {
        assert left.getKind() == wordKind && right.getKind() == wordKind;

        // mirroring gets the condition into canonical form
        boolean mirror = condition.canonicalMirror();

        ValueNode a = mirror ? right : left;
        ValueNode b = mirror ? left : right;

        CompareNode comparison;
        if (condition == Condition.EQ || condition == Condition.NE) {
            comparison = new IntegerEqualsNode(a, b);
        } else if (condition.isUnsigned()) {
            comparison = new IntegerBelowNode(a, b);
        } else {
            comparison = new IntegerLessThanNode(a, b);
        }

        ConstantNode trueValue = graph.add(forInt(1));
        ConstantNode falseValue = graph.add(forInt(0));

        if (condition.canonicalNegate()) {
            ConstantNode temp = trueValue;
            trueValue = falseValue;
            falseValue = temp;
        }
        ConditionalNode materialize = graph.add(new ConditionalNode(graph.add(comparison), trueValue, falseValue));
        return materialize;
    }

    protected ValueNode readOp(GraphBuilderContext b, Kind readKind, ValueNode base, LocationNode location, Opcode op) {
        assert op == Opcode.READ_POINTER || op == Opcode.READ_OBJECT || op == Opcode.READ_BARRIERED;
        final BarrierType barrier = (op == Opcode.READ_BARRIERED ? BarrierType.PRECISE : BarrierType.NONE);
        final boolean compressible = (op == Opcode.READ_OBJECT || op == Opcode.READ_BARRIERED);

        return readOp(b, readKind, base, location, barrier, compressible);
    }

    public static ValueNode readOp(GraphBuilderContext b, Kind readKind, ValueNode base, LocationNode location, BarrierType barrierType, boolean compressible) {
        JavaReadNode read = b.add(new JavaReadNode(readKind, base, location, barrierType, compressible));
        /*
         * The read must not float outside its block otherwise it may float above an explicit zero
         * check on its base address.
         */
        read.setGuard(AbstractBeginNode.prevBegin(read));
        return read;
    }

    protected void writeOp(GraphBuilderContext b, Kind writeKind, ValueNode base, ValueNode value, LocationNode location, Opcode op) {
        assert op == Opcode.WRITE_POINTER || op == Opcode.WRITE_OBJECT || op == Opcode.WRITE_BARRIERED || op == Opcode.INITIALIZE;
        final BarrierType barrier = (op == Opcode.WRITE_BARRIERED ? BarrierType.PRECISE : BarrierType.NONE);
        final boolean compressible = (op == Opcode.WRITE_OBJECT || op == Opcode.WRITE_BARRIERED);
        final boolean initialize = (op == Opcode.INITIALIZE);
        b.add(new JavaWriteNode(writeKind, base, value, location, barrier, compressible, initialize));
    }

    public LocationNode makeLocation(GraphBuilderContext b, ValueNode offset, LocationIdentity locationIdentity) {
        return b.add(new IndexedLocationNode(locationIdentity, 0, fromSigned(b, offset), 1));
    }

    public LocationNode makeLocation(GraphBuilderContext b, ValueNode offset, ValueNode locationIdentity) {
        if (locationIdentity.isConstant()) {
            return makeLocation(b, offset, snippetReflection.asObject(LocationIdentity.class, locationIdentity.asJavaConstant()));
        }
        return b.add(new SnippetLocationNode(snippetReflection, locationIdentity, b.add(ConstantNode.forLong(0)), fromSigned(b, offset), b.add(ConstantNode.forInt(1))));
    }

    public ValueNode fromUnsigned(GraphBuilderContext b, ValueNode value) {
        return convert(b, value, wordKind, true);
    }

    public ValueNode fromSigned(GraphBuilderContext b, ValueNode value) {
        return convert(b, value, wordKind, false);
    }

    public ValueNode toUnsigned(GraphBuilderContext b, ValueNode value, Kind toKind) {
        return convert(b, value, toKind, true);
    }

    public ValueNode convert(GraphBuilderContext b, ValueNode value, Kind toKind, boolean unsigned) {
        if (value.getKind() == toKind) {
            return value;
        }

        if (toKind == Kind.Int) {
            assert value.getKind() == Kind.Long;
            return b.add(new NarrowNode(value, 32));
        } else {
            assert toKind == Kind.Long;
            assert value.getKind().getStackKind() == Kind.Int;
            if (unsigned) {
                return b.add(new ZeroExtendNode(value, 64));
            } else {
                return b.add(new SignExtendNode(value, 64));
            }
        }
    }

    public WordTypes getWordTypes() {
        return wordTypes;
    }
}
