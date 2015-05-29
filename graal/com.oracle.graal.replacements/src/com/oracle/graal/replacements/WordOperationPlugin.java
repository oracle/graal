/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.nodes.ConstantNode.*;
import static com.oracle.jvmci.meta.LocationIdentity.*;

import java.lang.reflect.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.word.*;
import com.oracle.graal.word.Word.Opcode;
import com.oracle.graal.word.Word.Operation;
import com.oracle.graal.word.nodes.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.meta.*;

/**
 * A plugin for calls to {@linkplain Operation word operations}, as well as all other nodes that
 * need special handling for {@link Word} types.
 */
public class WordOperationPlugin implements NodePlugin, ParameterPlugin, InlineInvokePlugin {
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
    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (!wordTypes.isWordOperation(method)) {
            return false;
        }
        processWordOperation(b, args, wordTypes.getWordOperation(method, b.getMethod().getDeclaringClass()));
        return true;
    }

    @Override
    public FloatingNode interceptParameter(GraphBuilderContext b, int index, Stamp stamp) {
        ResolvedJavaType type = StampTool.typeOrNull(stamp);
        if (wordTypes.isWord(type)) {
            return new ParameterNode(index, wordTypes.getWordStamp(type));
        }
        return null;
    }

    @Override
    public void notifyOfNoninlinedInvoke(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        if (wordTypes.isWord(invoke.asNode())) {
            invoke.asNode().setStamp(wordTypes.getWordStamp(StampTool.typeOrNull(invoke.asNode())));
        }
    }

    @Override
    public boolean handleLoadField(GraphBuilderContext b, ValueNode receiver, ResolvedJavaField field) {
        if (field.getType() instanceof ResolvedJavaType && wordTypes.isWord((ResolvedJavaType) field.getType())) {
            LoadFieldNode loadFieldNode = new LoadFieldNode(receiver, field);
            loadFieldNode.setStamp(wordTypes.getWordStamp((ResolvedJavaType) field.getType()));
            b.addPush(field.getKind(), loadFieldNode);
            return true;
        }
        return false;
    }

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField staticField) {
        return handleLoadField(b, null, staticField);
    }

    @Override
    public boolean handleLoadIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, Kind elementKind) {
        ResolvedJavaType arrayType = StampTool.typeOrNull(array);
        /*
         * There are cases where the array does not have a known type yet, i.e., the type is null.
         * In that case we assume it is not a word type.
         */
        if (arrayType != null && wordTypes.isWord(arrayType.getComponentType())) {
            assert elementKind == Kind.Object;
            b.addPush(elementKind, createLoadIndexedNode(array, index));
            return true;
        }
        return false;
    }

    protected LoadIndexedNode createLoadIndexedNode(ValueNode array, ValueNode index) {
        return new LoadIndexedNode(array, index, wordTypes.getWordKind());
    }

    @Override
    public boolean handleStoreIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, Kind elementKind, ValueNode value) {
        ResolvedJavaType arrayType = StampTool.typeOrNull(array);
        if (arrayType != null && wordTypes.isWord(arrayType.getComponentType())) {
            assert elementKind == Kind.Object;
            if (value.getKind() != wordTypes.getWordKind()) {
                throw b.bailout("Cannot store a non-word value into a word array: " + arrayType.toJavaName(true));
            }
            b.add(createStoreIndexedNode(array, index, value));
            return true;
        }
        if (elementKind == Kind.Object && value.getKind() == wordTypes.getWordKind()) {
            throw b.bailout("Cannot store a word value into a non-word array: " + arrayType.toJavaName(true));
        }
        return false;
    }

    protected StoreIndexedNode createStoreIndexedNode(ValueNode array, ValueNode index, ValueNode value) {
        return new StoreIndexedNode(array, index, wordTypes.getWordKind(), value);
    }

    @Override
    public boolean handleCheckCast(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        if (!wordTypes.isWord(type)) {
            if (object.getKind() != Kind.Object) {
                throw b.bailout("Cannot cast a word value to a non-word type: " + type.toJavaName(true));
            }
            return false;
        }

        if (object.getKind() != wordTypes.getWordKind()) {
            throw b.bailout("Cannot cast a non-word value to a word type: " + type.toJavaName(true));
        }
        b.push(Kind.Object, object);
        return true;
    }

    @Override
    public boolean handleInstanceOf(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        if (wordTypes.isWord(type)) {
            throw b.bailout("Cannot use instanceof for word a type: " + type.toJavaName(true));
        } else if (object.getKind() != Kind.Object) {
            throw b.bailout("Cannot use instanceof on a word value: " + type.toJavaName(true));
        }
        return false;
    }

    protected void processWordOperation(GraphBuilderContext b, ValueNode[] args, ResolvedJavaMethod wordMethod) throws JVMCIError {
        Operation operation = wordMethod.getAnnotation(Word.Operation.class);
        Kind returnKind = wordMethod.getSignature().getReturnKind();
        switch (operation.opcode()) {
            case NODE_CLASS:
                assert args.length == 2;
                ValueNode left = args[0];
                ValueNode right = operation.rightOperandIsInt() ? toUnsigned(b, args[1], Kind.Int) : fromSigned(b, args[1]);

                b.addPush(returnKind, createBinaryNodeInstance(operation.node(), left, right));
                break;

            case COMPARISON:
                assert args.length == 2;
                b.push(returnKind, comparisonOp(b, operation.condition(), args[0], fromSigned(b, args[1])));
                break;

            case NOT:
                assert args.length == 1;
                b.addPush(returnKind, new XorNode(args[0], b.add(forIntegerKind(wordKind, -1))));
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
                b.push(returnKind, readOp(b, readKind, args[0], location, operation.opcode()));
                break;
            }
            case READ_HEAP: {
                assert args.length == 3;
                Kind readKind = wordTypes.asKind(wordMethod.getSignature().getReturnType(wordMethod.getDeclaringClass()));
                LocationNode location = makeLocation(b, args[1], any());
                BarrierType barrierType = snippetReflection.asObject(BarrierType.class, args[2].asJavaConstant());
                b.push(returnKind, readOp(b, readKind, args[0], location, barrierType, true));
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
                b.addPush(returnKind, forIntegerKind(wordKind, 0L));
                break;

            case FROM_UNSIGNED:
                assert args.length == 1;
                b.push(returnKind, fromUnsigned(b, args[0]));
                break;

            case FROM_SIGNED:
                assert args.length == 1;
                b.push(returnKind, fromSigned(b, args[0]));
                break;

            case TO_RAW_VALUE:
                assert args.length == 1;
                b.push(returnKind, toUnsigned(b, args[0], Kind.Long));
                break;

            case FROM_WORDBASE:
                assert args.length == 1;
                b.push(returnKind, args[0]);
                break;

            case FROM_OBJECT:
                assert args.length == 1;
                WordCastNode objectToWord = b.add(WordCastNode.objectToWord(args[0], wordKind));
                b.push(returnKind, objectToWord);
                break;

            case FROM_ARRAY:
                assert args.length == 2;
                b.addPush(returnKind, new ComputeAddressNode(args[0], args[1], StampFactory.forKind(wordKind)));
                break;

            case TO_OBJECT:
                assert args.length == 1;
                WordCastNode wordToObject = b.add(WordCastNode.wordToObject(args[0], wordKind));
                b.push(returnKind, wordToObject);
                break;

            default:
                throw new JVMCIError("Unknown opcode: %s", operation.opcode());
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
            throw new JVMCIError(ex).addContext(nodeClass.getName());
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
        /*
         * A JavaReadNode lowered to a ReadNode that will not float. This means it cannot float
         * above an explicit zero check on its base address or any other test that ensures the read
         * is safe.
         */
        JavaReadNode read = b.add(new JavaReadNode(readKind, base, location, barrierType, compressible));
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
