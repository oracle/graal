/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.word;

import static jdk.graal.compiler.nodes.ConstantNode.forInt;
import static jdk.graal.compiler.nodes.ConstantNode.forIntegerKind;
import static org.graalvm.word.LocationIdentity.any;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.impl.WordFactoryOperation;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.bytecode.BridgeMethodUtils;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.calc.Condition.CanonicalizedCondition;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.TypePlugin;
import jdk.graal.compiler.nodes.java.AbstractCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.LogicCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.java.ValueCompareAndSwapNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.word.Word.Opcode;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A plugin for calls to {@linkplain Word.Operation word operations}, as well as all other nodes
 * that need special handling for {@link Word} types.
 */
public class WordOperationPlugin implements NodePlugin, TypePlugin, InlineInvokePlugin {
    protected final WordTypes wordTypes;
    protected final JavaKind wordKind;
    private final BarrierSet barrierSet;
    protected final SnippetReflectionProvider snippetReflection;
    protected final ConstantReflectionProvider constantReflection;

    public WordOperationPlugin(SnippetReflectionProvider snippetReflection, ConstantReflectionProvider constantReflection, WordTypes wordTypes, BarrierSet barrierSet) {
        this.constantReflection = constantReflection;
        this.snippetReflection = snippetReflection;
        this.wordTypes = wordTypes;
        this.wordKind = wordTypes.getWordKind();
        this.barrierSet = barrierSet;
        assert barrierSet != null;
    }

    @Override
    public boolean canChangeStackKind(GraphBuilderContext b) {
        return true;
    }

    /**
     * Processes a call to a method if it is annotated as a word operation by adding nodes to the
     * graph being built that implement the denoted operation.
     *
     * @return {@code true} iff {@code method} is annotated with {@link Word.Operation} (and was
     *         thus processed by this method)
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
    public StampPair interceptType(GraphBuilderTool b, JavaType declaredType, boolean nonNull) {
        Stamp wordStamp = null;
        if (declaredType instanceof ResolvedJavaType) {
            ResolvedJavaType resolved = (ResolvedJavaType) declaredType;
            if (wordTypes.isWord(resolved)) {
                wordStamp = wordTypes.getWordStamp(resolved);
            } else if (resolved.isArray() && wordTypes.isWord(resolved.getElementalType())) {
                TypeReference trusted = TypeReference.createTrustedWithoutAssumptions(resolved);
                wordStamp = StampFactory.object(trusted, nonNull);
            }
        }
        if (wordStamp != null) {
            return StampPair.createSingle(wordStamp);
        } else {
            return null;
        }
    }

    @Override
    public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        if (wordTypes.isWord(invoke.asNode())) {
            invoke.asNode().setStamp(wordTypes.getWordStamp(StampTool.typeOrNull(invoke.asNode())));
        }
    }

    @Override
    public boolean handleLoadField(GraphBuilderContext b, ValueNode receiver, ResolvedJavaField field) {
        StampPair wordStamp = interceptType(b, field.getType(), false);
        if (wordStamp != null) {
            LoadFieldNode loadFieldNode = LoadFieldNode.createOverrideStamp(wordStamp, receiver, field);
            b.addPush(field.getJavaKind(), loadFieldNode);
            return true;
        }
        return false;
    }

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField staticField) {
        return handleLoadField(b, null, staticField);
    }

    @Override
    public boolean handleLoadIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, GuardingNode boundsCheck, JavaKind elementKind) {
        ResolvedJavaType arrayType = StampTool.typeOrNull(array);
        /*
         * There are cases where the array does not have a known type yet, i.e., the type is null.
         * In that case we assume it is not a word type.
         */
        if (arrayType != null && wordTypes.isWord(arrayType.getComponentType())) {
            assert elementKind == JavaKind.Object : Assertions.errorMessage(array, index, boundsCheck, elementKind);
            b.addPush(elementKind, createLoadIndexedNode(array, index, boundsCheck));
            return true;
        }
        return false;
    }

    protected LoadIndexedNode createLoadIndexedNode(ValueNode array, ValueNode index, GuardingNode boundsCheck) {
        return new LoadIndexedNode(null, array, index, boundsCheck, wordKind);
    }

    @Override
    public boolean handleStoreField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field, ValueNode value) {
        if (field.getJavaKind() == JavaKind.Object) {
            boolean isWordField = wordTypes.isWord(field.getType());
            boolean isWordValue = value.getStackKind() == wordKind;

            if (isWordField && !isWordValue) {
                throw bailout(b, "Cannot store a non-word value into a word field: " + field.format("%H.%n"));
            } else if (!isWordField && isWordValue) {
                throw bailout(b, "Cannot store a word value into a non-word field: " + field.format("%H.%n"));
            }
        }

        /* We never need to intercept the field store. */
        return false;
    }

    @Override
    public boolean handleStoreStaticField(GraphBuilderContext b, ResolvedJavaField field, ValueNode value) {
        return handleStoreField(b, null, field, value);
    }

    @Override
    public boolean handleStoreIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, GuardingNode boundsCheck, GuardingNode storeCheck, JavaKind elementKind, ValueNode value) {
        ResolvedJavaType arrayType = StampTool.typeOrNull(array);
        if (arrayType != null && wordTypes.isWord(arrayType.getComponentType())) {
            assert elementKind == JavaKind.Object : Assertions.errorMessage(array, index, boundsCheck, storeCheck, elementKind, value);
            if (value.getStackKind() != wordKind) {
                throw bailout(b, "Cannot store a non-word value into a word array: " + arrayType.toJavaName(true));
            }
            GraalError.guarantee(storeCheck == null, "Word array stores are primitive stores and therefore do not require a store check");
            b.add(createStoreIndexedNode(array, index, boundsCheck, value));
            return true;
        }
        if (elementKind == JavaKind.Object && value.getStackKind() == wordKind) {
            throw bailout(b, "Cannot store a word value into a non-word array: " + arrayType.toJavaName(true));
        }
        return false;
    }

    protected StoreIndexedNode createStoreIndexedNode(ValueNode array, ValueNode index, GuardingNode boundsCheck, ValueNode value) {
        return new StoreIndexedNode(array, index, boundsCheck, null, wordKind, value);
    }

    @Override
    public boolean handleCheckCast(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        if (!wordTypes.isWord(type)) {
            if (object.getStackKind() != JavaKind.Object) {
                throw bailout(b, "Cannot cast a word value to a non-word type: " + type.toJavaName(true));
            }
            return false;
        }

        if (object.getStackKind() != wordKind) {
            throw bailout(b, "Cannot cast a non-word value to a word type: " + type.toJavaName(true));
        }
        b.push(JavaKind.Object, object);
        return true;
    }

    @Override
    public boolean handleInstanceOf(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        if (wordTypes.isWord(type)) {
            throw bailout(b, "Cannot use instanceof for word a type: " + type.toJavaName(true));
        } else if (object.getStackKind() != JavaKind.Object) {
            throw bailout(b, "Cannot use instanceof on a word value: " + type.toJavaName(true));
        }
        return false;
    }

    protected void processWordOperation(GraphBuilderContext b, ValueNode[] args, ResolvedJavaMethod wordMethod) throws GraalError {
        JavaKind returnKind = wordMethod.getSignature().getReturnKind();
        WordFactoryOperation factoryOperation = BridgeMethodUtils.getAnnotation(WordFactoryOperation.class, wordMethod);
        if (factoryOperation != null) {
            switch (factoryOperation.opcode()) {
                case ZERO:
                    assert NumUtil.assertArrayLength(args, 0);
                    b.addPush(returnKind, forIntegerKind(wordKind, 0L));
                    return;

                case FROM_UNSIGNED:
                    assert NumUtil.assertArrayLength(args, 1);
                    b.push(returnKind, fromUnsigned(b, args[0]));
                    return;

                case FROM_SIGNED:
                    assert NumUtil.assertArrayLength(args, 1);
                    b.push(returnKind, fromSigned(b, args[0]));
                    return;
            }
        }

        Word.Operation operation = BridgeMethodUtils.getAnnotation(Word.Operation.class, wordMethod);
        if (operation == null) {
            throw bailout(b, "Cannot call method on a word value: " + wordMethod.format("%H.%n(%p)"));
        }
        switch (operation.opcode()) {
            case NODE_CLASS:
            case INTEGER_DIVISION_NODE_CLASS:
                assert NumUtil.assertArrayLength(args, 2);
                ValueNode left = args[0];
                ValueNode right = operation.rightOperandIsInt() ? toUnsigned(b, args[1], JavaKind.Int) : fromSigned(b, args[1]);

                b.addPush(returnKind, createBinaryNodeInstance(b, operation.node(), left, right, operation.opcode() == Word.Opcode.INTEGER_DIVISION_NODE_CLASS));
                break;

            case COMPARISON:
                assert NumUtil.assertArrayLength(args, 2);
                b.push(returnKind, comparisonOp(b, operation.condition(), args[0], fromSigned(b, args[1])));
                break;

            case IS_NULL:
                assert NumUtil.assertArrayLength(args, 1);
                b.push(returnKind, comparisonOp(b, Condition.EQ, args[0], ConstantNode.forIntegerKind(wordKind, 0L)));
                break;

            case IS_NON_NULL:
                assert NumUtil.assertArrayLength(args, 1);
                b.push(returnKind, comparisonOp(b, Condition.NE, args[0], ConstantNode.forIntegerKind(wordKind, 0L)));
                break;

            case NOT:
                assert NumUtil.assertArrayLength(args, 1);
                b.addPush(returnKind, new XorNode(args[0], b.add(forIntegerKind(wordKind, -1))));
                break;

            case READ_POINTER:
            case READ_OBJECT:
            case READ_BARRIERED: {
                assert NumUtil.assertArrayLength(args, 2, 3);
                JavaKind readKind = wordTypes.asKind(wordMethod.getSignature().getReturnType(wordMethod.getDeclaringClass()));
                AddressNode address = makeAddress(b, args[0], args[1]);
                LocationIdentity location;
                if (args.length == 2) {
                    location = any();
                } else {
                    assert GraphUtil.assertIsConstant(args[2]);
                    location = snippetReflection.asObject(LocationIdentity.class, args[2].asJavaConstant());
                    assert location != null : snippetReflection.asObject(Object.class, args[2].asJavaConstant());
                }
                b.push(returnKind, readOp(b, readKind, address, location, operation.opcode()));
                break;
            }

            case READ_POINTER_VOLATILE:
            case READ_BARRIERED_VOLATILE: {
                assert NumUtil.assertArrayLength(args, 2, 3);
                JavaKind readKind = wordTypes.asKind(wordMethod.getSignature().getReturnType(wordMethod.getDeclaringClass()));
                AddressNode address = makeAddress(b, args[0], args[1]);
                LocationIdentity location;
                if (args.length == 2) {
                    location = any();
                } else {
                    assert GraphUtil.assertIsConstant(args[2]);
                    location = snippetReflection.asObject(LocationIdentity.class, args[2].asJavaConstant());
                    assert location != null : snippetReflection.asObject(Object.class, args[2].asJavaConstant());
                }
                b.push(returnKind, readVolatileOp(b, readKind, address, location, operation.opcode()));
                break;
            }
            case READ_HEAP: {
                assert NumUtil.assertArrayLength(args, 3, 4);
                JavaKind readKind = wordTypes.asKind(wordMethod.getSignature().getReturnType(wordMethod.getDeclaringClass()));
                AddressNode address = makeAddress(b, args[0], args[1]);
                BarrierType barrierType = snippetReflection.asObject(BarrierType.class, args[2].asJavaConstant());
                LocationIdentity location;
                if (args.length == 3) {
                    location = any();
                } else {
                    assert GraphUtil.assertIsConstant(args[3]);
                    location = snippetReflection.asObject(LocationIdentity.class, args[3].asJavaConstant());
                }
                b.push(returnKind, readOp(b, readKind, address, location, barrierType, true));
                break;
            }

            case WRITE_POINTER:
            case WRITE_OBJECT:
            case WRITE_BARRIERED:
            case INITIALIZE:
            case WRITE_POINTER_SIDE_EFFECT_FREE:
            case WRITE_POINTER_VOLATILE: {
                assert NumUtil.assertArrayLength(args, 3, 4);
                JavaKind writeKind = wordTypes.asKind(wordMethod.getSignature().getParameterType(wordMethod.isStatic() ? 2 : 1, wordMethod.getDeclaringClass()));
                AddressNode address = makeAddress(b, args[0], args[1]);
                LocationIdentity location;
                if (args.length == 3) {
                    location = any();
                } else {
                    assert GraphUtil.assertIsConstant(args[3]);
                    location = snippetReflection.asObject(LocationIdentity.class, args[3].asJavaConstant());
                }
                writeOp(b, writeKind, address, location, args[2], operation.opcode());
                break;
            }

            case TO_RAW_VALUE:
                assert NumUtil.assertArrayLength(args, 1);
                b.push(returnKind, toUnsigned(b, args[0], JavaKind.Long));
                break;

            case OBJECT_TO_TRACKED:
                assert NumUtil.assertArrayLength(args, 1);
                WordCastNode objectToTracked = b.add(WordCastNode.objectToTrackedPointer(args[0], wordKind));
                b.push(returnKind, objectToTracked);
                break;

            case OBJECT_TO_UNTRACKED:
                assert NumUtil.assertArrayLength(args, 1);
                WordCastNode objectToUntracked = b.add(WordCastNode.objectToUntrackedPointer(args[0], wordKind));
                b.push(returnKind, objectToUntracked);
                break;

            case FROM_ADDRESS:
                assert NumUtil.assertArrayLength(args, 1);
                WordCastNode addressToWord = b.add(WordCastNode.addressToWord(args[0], wordKind));
                b.push(returnKind, addressToWord);
                break;

            case TO_OBJECT:
                assert NumUtil.assertArrayLength(args, 1);
                WordCastNode wordToObject = b.add(WordCastNode.wordToObject(args[0], wordKind));
                b.push(returnKind, wordToObject);
                break;

            case TO_TYPED_OBJECT:
                assert NumUtil.assertArrayLength(args, 3);
                assert GraphUtil.assertIsConstant(args[1]);
                assert GraphUtil.assertIsConstant(args[2]);
                ResolvedJavaType type = constantReflection.asJavaType(args[1].asJavaConstant());
                boolean nonNull = args[2].asJavaConstant().asInt() != 0;
                TypeReference trusted = TypeReference.createTrustedWithoutAssumptions(type);
                ObjectStamp stamp = StampFactory.object(trusted, nonNull);
                WordCastNode wordToObjectTyped = b.add(WordCastNode.wordToTypedObject(args[0], stamp));
                b.push(returnKind, wordToObjectTyped);
                break;

            case TO_OBJECT_NON_NULL:
                assert NumUtil.assertArrayLength(args, 1);
                WordCastNode wordToObjectNonNull = b.add(WordCastNode.wordToObjectNonNull(args[0], wordKind));
                b.push(returnKind, wordToObjectNonNull);
                break;

            case CAS_POINTER:
                assert NumUtil.assertArrayLength(args, 5);
                AddressNode address = makeAddress(b, args[0], args[1]);
                JavaKind valueKind = wordTypes.asKind(wordMethod.getSignature().getParameterType(1, wordMethod.getDeclaringClass()));
                assert valueKind.equals(wordTypes.asKind(wordMethod.getSignature().getParameterType(2, wordMethod.getDeclaringClass()))) : wordMethod.getSignature();
                assert GraphUtil.assertIsConstant(args[4]) : Arrays.toString(args);
                LocationIdentity location = snippetReflection.asObject(LocationIdentity.class, args[4].asJavaConstant());
                JavaType returnType = wordMethod.getSignature().getReturnType(wordMethod.getDeclaringClass());
                b.addPush(returnKind, casOp(valueKind, wordTypes.asKind(returnType), address, location, args[2], args[3]));
                break;
            default:
                throw new GraalError("Unknown opcode: %s", operation.opcode());
        }
    }

    /**
     * Create an instance of a binary node which is used to lower {@link Word} operations. This
     * method is called for all {@link Word} operations which are annotated with @Operation(node =
     * ...) and encapsulates the reflective allocation of the node.
     */
    private static ValueNode createBinaryNodeInstance(GraphBuilderContext b, Class<? extends ValueNode> nodeClass, ValueNode left, ValueNode right, boolean isIntegerDivision) {
        try {
            GuardingNode zeroCheck = isIntegerDivision ? b.maybeEmitExplicitDivisionByZeroCheck(right) : null;
            Class<?>[] parameterTypes = isIntegerDivision ? new Class<?>[]{ValueNode.class, ValueNode.class, GuardingNode.class} : new Class<?>[]{ValueNode.class, ValueNode.class};
            Constructor<?> cons = nodeClass.getDeclaredConstructor(parameterTypes);
            Object[] initargs = isIntegerDivision ? new Object[]{left, right, zeroCheck} : new Object[]{left, right};
            return (ValueNode) cons.newInstance(initargs);
        } catch (Throwable ex) {
            throw new GraalError(ex).addContext(nodeClass.getName());
        }
    }

    private ValueNode comparisonOp(GraphBuilderContext graph, Condition condition, ValueNode left, ValueNode right) {
        assert left.getStackKind() == wordKind && right.getStackKind() == wordKind : "Stack kind must match " + left + " " + right;

        CanonicalizedCondition canonical = condition.canonicalize();

        ValueNode a = canonical.mustMirror() ? right : left;
        ValueNode b = canonical.mustMirror() ? left : right;

        CompareNode comparison;
        if (canonical.getCanonicalCondition() == CanonicalCondition.EQ) {
            comparison = new IntegerEqualsNode(a, b);
        } else if (canonical.getCanonicalCondition() == CanonicalCondition.BT) {
            comparison = new IntegerBelowNode(a, b);
        } else {
            assert canonical.getCanonicalCondition() == CanonicalCondition.LT : Assertions.errorMessage(canonical.getCanonicalCondition(), condition, left, right);
            comparison = new IntegerLessThanNode(a, b);
        }

        ConstantNode trueValue = graph.add(forInt(1));
        ConstantNode falseValue = graph.add(forInt(0));

        if (canonical.mustNegate()) {
            ConstantNode temp = trueValue;
            trueValue = falseValue;
            falseValue = temp;
        }
        return graph.add(new ConditionalNode(graph.add(comparison), trueValue, falseValue));
    }

    protected ValueNode readOp(GraphBuilderContext b, JavaKind readKind, AddressNode address, LocationIdentity location, Opcode op) {
        assert op == Opcode.READ_POINTER || op == Opcode.READ_OBJECT || op == Opcode.READ_BARRIERED : op;
        final BarrierType barrier = (op == Opcode.READ_BARRIERED && readKind.isObject() ? barrierSet.readBarrierType(location, address, null) : BarrierType.NONE);
        final boolean compressible = (op == Opcode.READ_OBJECT || op == Opcode.READ_BARRIERED);

        return readOp(b, readKind, address, location, barrier, compressible);
    }

    public static ValueNode readOp(GraphBuilderContext b, JavaKind readKind, AddressNode address, LocationIdentity location, BarrierType barrierType, boolean compressible) {
        /*
         * A JavaReadNode is lowered to a ReadNode that will not float. This means it cannot float
         * above an explicit zero check on its base address or any other test that ensures the read
         * is safe.
         */
        JavaReadNode read = b.add(new JavaReadNode(readKind, address, location, barrierType, MemoryOrderMode.PLAIN, compressible));
        return read;
    }

    protected ValueNode readVolatileOp(GraphBuilderContext b, JavaKind readKind, AddressNode address, LocationIdentity location, Opcode op) {
        assert op == Opcode.READ_POINTER_VOLATILE || op == Opcode.READ_BARRIERED_VOLATILE : op;
        final BarrierType barrier = op == Opcode.READ_BARRIERED_VOLATILE && readKind.isObject() ? barrierSet.readBarrierType(location, address, null) : BarrierType.NONE;
        final boolean compressible = op == Opcode.READ_BARRIERED_VOLATILE;
        /*
         * A JavaOrderedReadNode is lowered to an OrderedReadNode that will not float. This means it
         * cannot float above an explicit zero check on its base address or any other test that
         * ensures the read is safe.
         */
        JavaReadNode read = b.add(new JavaReadNode(readKind, address, location, barrier, MemoryOrderMode.VOLATILE, compressible));
        return read;
    }

    protected void writeOp(GraphBuilderContext b, JavaKind writeKind, AddressNode address, LocationIdentity location, ValueNode value, Opcode op) {
        assert op == Opcode.WRITE_POINTER || op == Opcode.WRITE_POINTER_SIDE_EFFECT_FREE || op == Opcode.WRITE_OBJECT || op == Opcode.WRITE_BARRIERED || op == Opcode.INITIALIZE ||
                        op == Opcode.WRITE_POINTER_VOLATILE : op;
        assert op != Opcode.INITIALIZE || location.isInit() : "must use init location for initializing";

        final BarrierType barrier = (op == Word.Opcode.WRITE_BARRIERED ? BarrierType.UNKNOWN : BarrierType.NONE);
        final boolean compressible = (op == Word.Opcode.WRITE_OBJECT || op == Word.Opcode.WRITE_BARRIERED);
        final boolean hasSideEffect = (op != Word.Opcode.WRITE_POINTER_SIDE_EFFECT_FREE);
        final MemoryOrderMode memoryOrder = op == Word.Opcode.WRITE_POINTER_VOLATILE ? MemoryOrderMode.VOLATILE : MemoryOrderMode.PLAIN;

        b.add(new JavaWriteNode(writeKind, address, location, value, barrier, compressible, hasSideEffect, memoryOrder));
    }

    protected AbstractCompareAndSwapNode casOp(JavaKind writeKind, JavaKind returnKind, AddressNode address, LocationIdentity location, ValueNode expectedValue, ValueNode newValue) {
        boolean isLogic = returnKind == JavaKind.Boolean;
        assert isLogic || writeKind == returnKind : writeKind + " != " + returnKind;
        AbstractCompareAndSwapNode cas;
        if (isLogic) {
            cas = new LogicCompareAndSwapNode(address, expectedValue, newValue, location, BarrierType.NONE, MemoryOrderMode.VOLATILE);
        } else {
            cas = new ValueCompareAndSwapNode(address, expectedValue, newValue, location, BarrierType.NONE, MemoryOrderMode.VOLATILE);
        }
        return cas;
    }

    public AddressNode makeAddress(GraphBuilderContext b, ValueNode base, ValueNode offset) {
        return b.add(new OffsetAddressNode(base, fromSigned(b, offset)));
    }

    public ValueNode fromUnsigned(GraphBuilderContext b, ValueNode value) {
        return convert(b, value, wordKind, true);
    }

    public ValueNode fromSigned(GraphBuilderContext b, ValueNode value) {
        return convert(b, value, wordKind, false);
    }

    public ValueNode toUnsigned(GraphBuilderContext b, ValueNode value, JavaKind toKind) {
        return convert(b, value, toKind, true);
    }

    public ValueNode convert(GraphBuilderContext b, ValueNode value, JavaKind toKind, boolean unsigned) {
        if (value.getStackKind() == toKind) {
            return value;
        }

        if (toKind == JavaKind.Int) {
            assert value.getStackKind() == JavaKind.Long : Assertions.errorMessage(value, toKind, unsigned);
            return b.add(new NarrowNode(value, 32));
        } else {
            assert toKind == JavaKind.Long : Assertions.errorMessage(value, toKind, unsigned);
            assert value.getStackKind() == JavaKind.Int : Assertions.errorMessage(value, toKind, unsigned);
            if (unsigned) {
                return b.add(new ZeroExtendNode(value, 64));
            } else {
                return b.add(new SignExtendNode(value, 64));
            }
        }
    }

    private static BailoutException bailout(GraphBuilderContext b, String msg) {
        throw b.bailout(msg + "\nat " + b.getCode().asStackTraceElement(b.bci()));
    }
}
