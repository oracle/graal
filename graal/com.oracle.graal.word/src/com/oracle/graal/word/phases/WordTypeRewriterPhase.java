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
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.word.*;
import com.oracle.graal.word.Word.Opcode;
import com.oracle.graal.word.Word.Operation;
import com.oracle.graal.word.nodes.*;

/**
 * Transforms all uses of the {@link Word} class into unsigned operations on {@code int} or
 * {@code long} values, depending on the word kind of the underlying platform.
 */
public class WordTypeRewriterPhase extends Phase {

    protected final MetaAccessProvider metaAccess;
    protected final SnippetReflectionProvider snippetReflection;
    protected final ResolvedJavaType wordBaseType;
    protected final ResolvedJavaType wordImplType;
    protected final ResolvedJavaType objectAccessType;
    protected final ResolvedJavaType barrieredAccessType;
    protected final Kind wordKind;

    public WordTypeRewriterPhase(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, Kind wordKind) {
        this.metaAccess = metaAccess;
        this.snippetReflection = snippetReflection;
        this.wordKind = wordKind;
        this.wordBaseType = metaAccess.lookupJavaType(WordBase.class);
        this.wordImplType = metaAccess.lookupJavaType(Word.class);
        this.objectAccessType = metaAccess.lookupJavaType(ObjectAccess.class);
        this.barrieredAccessType = metaAccess.lookupJavaType(BarrieredAccess.class);
    }

    @Override
    protected void run(StructuredGraph graph) {
        InferStamps.inferStamps(graph);

        for (Node n : graph.getNodes()) {
            if (n instanceof ValueNode) {
                changeToWord(graph, (ValueNode) n);
            }
        }

        for (Node node : graph.getNodes()) {
            rewriteNode(graph, node);
        }
    }

    /**
     * Change the stamp for word nodes from the object stamp ({@link WordBase} or anything extending
     * or implementing that interface) to the primitive word stamp.
     */
    protected void changeToWord(StructuredGraph graph, ValueNode node) {
        if (isWord(node)) {
            if (node.isConstant()) {
                ConstantNode oldConstant = (ConstantNode) node;
                assert oldConstant.getValue().getKind() == Kind.Object;
                WordBase value = (WordBase) snippetReflection.asObject(oldConstant.getValue());
                ConstantNode newConstant = ConstantNode.forIntegerKind(wordKind, value.rawValue(), node.graph());
                graph.replaceFloating(oldConstant, newConstant);

            } else {
                node.setStamp(StampFactory.forKind(wordKind));
            }
        }
    }

    /**
     * Clean up nodes that are no longer necessary or valid after the stamp change, and perform
     * intrinsification of all methods called on word types.
     */
    protected void rewriteNode(StructuredGraph graph, Node node) {
        if (node instanceof CheckCastNode) {
            rewriteCheckCast(graph, (CheckCastNode) node);
        } else if (node instanceof LoadFieldNode) {
            rewriteLoadField(graph, (LoadFieldNode) node);
        } else if (node instanceof AccessIndexedNode) {
            rewriteAccessIndexed(graph, (AccessIndexedNode) node);
        } else if (node instanceof MethodCallTargetNode) {
            rewriteInvoke(graph, (MethodCallTargetNode) node);
        }
    }

    /**
     * Remove casts between word types (which by now no longer have kind Object).
     */
    protected void rewriteCheckCast(StructuredGraph graph, CheckCastNode node) {
        if (node.getKind() == wordKind) {
            node.replaceAtUsages(node.object());
            graph.removeFixed(node);
        }
    }

    /**
     * Fold constant field reads, e.g. enum constants.
     */
    protected void rewriteLoadField(StructuredGraph graph, LoadFieldNode node) {
        ConstantNode constant = node.asConstant(metaAccess, node.object());
        if (constant != null) {
            node.replaceAtUsages(graph.unique(constant));
            graph.removeFixed(node);
        }
    }

    /**
     * Change loads and stores of word-arrays. Since the element kind is managed by the node on its
     * own and not in the stamp, {@link #changeToWord} does not perform all necessary changes.
     */
    protected void rewriteAccessIndexed(StructuredGraph graph, AccessIndexedNode node) {
        ResolvedJavaType arrayType = StampTool.typeOrNull(node.array());
        /*
         * There are cases where the array does not have a known type yet, i.e., the type is null.
         * In that case we assume it is not a word type.
         */
        if (arrayType != null && isWord(arrayType.getComponentType()) && node.elementKind() != wordKind) {
            /*
             * The elementKind of the node is a final field, and other information such as the stamp
             * depends on elementKind. Therefore, just create a new node and replace the old one.
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

    /**
     * Intrinsification of methods defined on the {@link Word} class that are annotated with
     * {@link Operation}.
     */
    protected void rewriteInvoke(StructuredGraph graph, MethodCallTargetNode callTargetNode) {
        ResolvedJavaMethod targetMethod = callTargetNode.targetMethod();
        final boolean isWordBase = wordBaseType.isAssignableFrom(targetMethod.getDeclaringClass());
        final boolean isObjectAccess = objectAccessType.equals(targetMethod.getDeclaringClass());
        final boolean isBarrieredAccess = barrieredAccessType.equals(targetMethod.getDeclaringClass());
        if (!isWordBase && !isObjectAccess && !isBarrieredAccess) {
            /*
             * Not a method defined on WordBase or a subclass / subinterface, and not on
             * ObjectAccess and not on BarrieredAccess, so nothing to rewrite.
             */
            return;
        }

        Invoke invoke = callTargetNode.invoke();
        if (!callTargetNode.isStatic()) {
            assert callTargetNode.receiver().getKind() == wordKind : "changeToWord() missed the receiver " + callTargetNode.receiver();
            targetMethod = wordImplType.resolveMethod(targetMethod, invoke.getContextType());
        }
        Operation operation = targetMethod.getAnnotation(Word.Operation.class);
        assert operation != null : targetMethod;

        NodeInputList<ValueNode> arguments = callTargetNode.arguments();

        switch (operation.opcode()) {
            case NODE_CLASS:
                assert arguments.size() == 2;
                ValueNode left = arguments.get(0);
                ValueNode right = operation.rightOperandIsInt() ? toUnsigned(graph, arguments.get(1), Kind.Int) : fromSigned(graph, arguments.get(1));

                ValueNode replacement = graph.addOrUnique(createBinaryNodeInstance(operation.node(), left, right));
                if (replacement instanceof FixedWithNextNode) {
                    graph.addBeforeFixed(invoke.asNode(), (FixedWithNextNode) replacement);
                }
                replace(invoke, replacement);
                break;

            case COMPARISON:
                assert arguments.size() == 2;
                replace(invoke, comparisonOp(graph, operation.condition(), arguments.get(0), fromSigned(graph, arguments.get(1))));
                break;

            case NOT:
                assert arguments.size() == 1;
                replace(invoke, graph.unique(new XorNode(arguments.get(0), ConstantNode.forIntegerKind(wordKind, -1, graph))));
                break;

            case READ_POINTER:
            case READ_OBJECT:
            case READ_BARRIERED: {
                assert arguments.size() == 2 || arguments.size() == 3;
                Kind readKind = asKind(callTargetNode.returnType());
                LocationNode location;
                if (arguments.size() == 2) {
                    location = makeLocation(graph, arguments.get(1), readKind, ANY_LOCATION);
                } else {
                    location = makeLocation(graph, arguments.get(1), readKind, arguments.get(2));
                }
                replace(invoke, readOp(graph, arguments.get(0), invoke, location, operation.opcode()));
                break;
            }
            case READ_HEAP: {
                assert arguments.size() == 3;
                Kind readKind = asKind(callTargetNode.returnType());
                LocationNode location = makeLocation(graph, arguments.get(1), readKind, ANY_LOCATION);
                BarrierType barrierType = (BarrierType) snippetReflection.asObject(arguments.get(2).asConstant());
                replace(invoke, readOp(graph, arguments.get(0), invoke, location, barrierType, true));
                break;
            }
            case WRITE_POINTER:
            case WRITE_OBJECT:
            case WRITE_BARRIERED:
            case INITIALIZE: {
                assert arguments.size() == 3 || arguments.size() == 4;
                Kind writeKind = asKind(targetMethod.getSignature().getParameterType(targetMethod.isStatic() ? 2 : 1, targetMethod.getDeclaringClass()));
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
                WordCastNode objectToWord = graph.add(WordCastNode.objectToWord(arguments.get(0), wordKind));
                graph.addBeforeFixed(invoke.asNode(), objectToWord);
                replace(invoke, objectToWord);
                break;

            case FROM_ARRAY:
                assert arguments.size() == 2;
                replace(invoke, graph.unique(new ComputeAddressNode(arguments.get(0), arguments.get(1), StampFactory.forKind(wordKind))));
                break;

            case TO_OBJECT:
                assert arguments.size() == 1;
                WordCastNode wordToObject = graph.add(WordCastNode.wordToObject(arguments.get(0), wordKind));
                graph.addBeforeFixed(invoke.asNode(), wordToObject);
                replace(invoke, wordToObject);
                break;

            default:
                throw new GraalInternalError("Unknown opcode: %s", operation.opcode());
        }
    }

    protected ValueNode fromUnsigned(StructuredGraph graph, ValueNode value) {
        return convert(graph, value, wordKind, true);
    }

    private ValueNode fromSigned(StructuredGraph graph, ValueNode value) {
        return convert(graph, value, wordKind, false);
    }

    protected ValueNode toUnsigned(StructuredGraph graph, ValueNode value, Kind toKind) {
        return convert(graph, value, toKind, true);
    }

    private static ValueNode convert(StructuredGraph graph, ValueNode value, Kind toKind, boolean unsigned) {
        if (value.getKind() == toKind) {
            return value;
        }

        if (toKind == Kind.Int) {
            assert value.getKind() == Kind.Long;
            return graph.unique(new NarrowNode(value, 32));
        } else {
            assert toKind == Kind.Long;
            assert value.getKind().getStackKind() == Kind.Int;
            if (unsigned) {
                return graph.unique(new ZeroExtendNode(value, 64));
            } else {
                return graph.unique(new SignExtendNode(value, 64));
            }
        }
    }

    /**
     * Create an instance of a binary node which is used to lower Word operations. This method is
     * called for all Word operations which are annotated with @Operation(node = ...) and
     * encapsulates the reflective allocation of the node.
     */
    private static ValueNode createBinaryNodeInstance(Class<? extends ValueNode> nodeClass, ValueNode left, ValueNode right) {
        try {
            Constructor<? extends ValueNode> constructor = nodeClass.getConstructor(ValueNode.class, ValueNode.class);
            return constructor.newInstance(left, right);
        } catch (Throwable ex) {
            throw new GraalInternalError(ex).addContext(nodeClass.getName());
        }
    }

    private ValueNode comparisonOp(StructuredGraph graph, Condition condition, ValueNode left, ValueNode right) {
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
            return makeLocation(graph, offset, readKind, (LocationIdentity) snippetReflection.asObject(locationIdentity.asConstant()));
        }
        return SnippetLocationNode.create(snippetReflection, locationIdentity, ConstantNode.forConstant(snippetReflection.forObject(readKind), metaAccess, graph), ConstantNode.forLong(0, graph),
                        fromSigned(graph, offset), ConstantNode.forInt(1, graph), graph);
    }

    protected LocationNode makeLocation(StructuredGraph graph, ValueNode offset, Kind readKind, LocationIdentity locationIdentity) {
        return IndexedLocationNode.create(locationIdentity, readKind, 0, fromSigned(graph, offset), graph, 1);
    }

    protected ValueNode readOp(StructuredGraph graph, ValueNode base, Invoke invoke, LocationNode location, Opcode op) {
        assert op == Opcode.READ_POINTER || op == Opcode.READ_OBJECT || op == Opcode.READ_BARRIERED;
        final BarrierType barrier = (op == Opcode.READ_BARRIERED ? BarrierType.PRECISE : BarrierType.NONE);
        final boolean compressible = (op == Opcode.READ_OBJECT || op == Opcode.READ_BARRIERED);

        return readOp(graph, base, invoke, location, barrier, compressible);
    }

    protected ValueNode readOp(StructuredGraph graph, ValueNode base, Invoke invoke, LocationNode location, BarrierType barrierType, boolean compressible) {
        JavaReadNode read = graph.add(new JavaReadNode(base, location, barrierType, compressible));
        graph.addBeforeFixed(invoke.asNode(), read);
        /*
         * The read must not float outside its block otherwise it may float above an explicit zero
         * check on its base address.
         */
        read.setGuard(BeginNode.prevBegin(invoke.asNode()));
        return read;
    }

    protected ValueNode writeOp(StructuredGraph graph, ValueNode base, ValueNode value, Invoke invoke, LocationNode location, Opcode op) {
        assert op == Opcode.WRITE_POINTER || op == Opcode.WRITE_OBJECT || op == Opcode.WRITE_BARRIERED || op == Opcode.INITIALIZE;
        final BarrierType barrier = (op == Opcode.WRITE_BARRIERED ? BarrierType.PRECISE : BarrierType.NONE);
        final boolean compressible = (op == Opcode.WRITE_OBJECT || op == Opcode.WRITE_BARRIERED);
        final boolean initialize = (op == Opcode.INITIALIZE);
        JavaWriteNode write = graph.add(new JavaWriteNode(base, value, location, barrier, compressible, initialize));
        write.setStateAfter(invoke.stateAfter());
        graph.addBeforeFixed(invoke.asNode(), write);
        return write;
    }

    protected void replace(Invoke invoke, ValueNode value) {
        FixedNode next = invoke.next();
        invoke.setNext(null);
        invoke.asNode().replaceAtPredecessor(next);
        invoke.asNode().replaceAtUsages(value);
        GraphUtil.killCFG(invoke.asNode());
    }

    protected boolean isWord(ValueNode node) {
        return isWord(StampTool.typeOrNull(node));
    }

    protected boolean isWord(ResolvedJavaType type) {
        return type != null && wordBaseType.isAssignableFrom(type);
    }

    protected Kind asKind(JavaType type) {
        if (type instanceof ResolvedJavaType && isWord((ResolvedJavaType) type)) {
            return wordKind;
        } else {
            return type.getKind();
        }
    }
}
