/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.nodes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.metadata.MetadataSymbol;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BinaryOperationConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BlockAddressConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CompareConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.GetElementPointerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.InlineAsmConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.SelectConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.StringConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.UndefinedConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.VectorConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.DoubleConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.FloatConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.X86FP80Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.ValueInstructionVisitor;
import com.oracle.truffle.llvm.parser.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.OpaqueType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.runtime.types.symbols.SSAValue;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public final class LLVMSymbolReadResolver {

    private final boolean storeSSAValueInSlot;
    private final LLVMParserRuntime runtime;
    private final NodeFactory nodeFactory;
    private final FrameDescriptor frame;
    private final GetStackSpaceFactory getStackSpaceFactory;
    private final DataLayout dataLayout;

    private final InternalVisitor visitor = new InternalVisitor();
    private LLVMExpressionNode resolvedNode = null;

    private static void unsupported(Object obj) {
        throw new LLVMParserException("Cannot resolve symbol: " + obj);
    }

    private final class InternalVisitor extends ValueInstructionVisitor {

        private final TypeVisitor nullValueVisitor = new TypeVisitor() {

            private void unsupportedType(Type type) {
                throw new LLVMParserException("Unsupported Type for Zero Constant: " + type);
            }

            @Override
            public void visit(FunctionType type) {
                resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(null, type);
            }

            @Override
            public void visit(PrimitiveType type) {
                switch (type.getPrimitiveKind()) {
                    case I1:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(false, type);
                        break;
                    case I8:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray((byte) 0, type);
                        break;
                    case I16:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray((short) 0, type);
                        break;
                    case I32:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(0, type);
                        break;
                    case I64:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(0L, type);
                        break;
                    case FLOAT:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(0.0f, type);
                        break;
                    case DOUBLE:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(0.0d, type);
                        break;
                    case X86_FP80:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(null, type);
                        break;
                    default:
                        unsupportedType(type);
                }
            }

            @Override
            public void visit(MetaType metaType) {
                if (metaType == MetaType.DEBUG) {
                    resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(null, metaType);
                } else {
                    unsupportedType(metaType);
                }
            }

            @Override
            public void visit(PointerType type) {
                resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(null, type);
            }

            @Override
            public void visit(ArrayType type) {
                try {
                    final long arraySize = type.getSize(dataLayout);
                    if (arraySize == 0) {
                        resolvedNode = null;
                    } else {
                        LLVMExpressionNode target = getStackSpaceFactory.createGetStackSpace(nodeFactory, type);
                        resolvedNode = nodeFactory.createZeroNode(target, arraySize);
                    }
                } catch (TypeOverflowException e) {
                    resolvedNode = Type.handleOverflowExpression(e);
                }
            }

            @Override
            public void visit(StructureType structureType) {
                try {
                    final long structSize = structureType.getSize(dataLayout);
                    if (structSize == 0) {
                        final LLVMNativePointer minusOneNode = LLVMNativePointer.create(-1);
                        resolvedNode = nodeFactory.createLiteral(minusOneNode, new PointerType(structureType));
                    } else {
                        LLVMExpressionNode addressnode = getStackSpaceFactory.createGetStackSpace(nodeFactory, structureType);
                        resolvedNode = nodeFactory.createZeroNode(addressnode, structSize);
                    }
                } catch (TypeOverflowException e) {
                    resolvedNode = Type.handleOverflowExpression(e);
                }
            }

            @Override
            public void visit(VectorType vectorType) {
                final int nrElements = vectorType.getNumberOfElementsInt();
                resolvedNode = nodeFactory.createZeroVectorInitializer(nrElements, vectorType);
            }

            @Override
            public void visit(VariableBitWidthType type) {
                resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(BigInteger.ZERO, type);
            }

            @Override
            public void visit(VoidType type) {
                unsupportedType(type);
            }

            @Override
            public void visit(OpaqueType type) {
                unsupportedType(type);
            }
        };

        @Override
        public void defaultAction(SymbolImpl symbol) {
            unsupported(symbol);
        }

        @Override
        public void visit(MetadataSymbol constant) {
            // metadata is passed as argument to some dbg.* methods. Sulong resolves required
            // metadata already during parsing and does not require such a value at runtime. We
            // resolve this type to a constant value here to avoid having to identify all functions,
            // like dbg.label, that receive metadata but are in practice noops at runtime.
            resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(0, PrimitiveType.I32);
        }

        @Override
        public void visit(ArrayConstant array) {
            final LLVMExpressionNode[] values = new LLVMExpressionNode[array.getElementCount()];
            for (int i = 0; i < array.getElementCount(); i++) {
                values[i] = resolve(array.getElement(i));
            }
            resolvedNode = nodeFactory.createArrayLiteral(values, array.getType(), getStackSpaceFactory);
        }

        @Override
        public void visit(StructureConstant constant) {
            final int elementCount = constant.getElementCount();
            final Type[] types = new Type[elementCount];
            final LLVMExpressionNode[] constants = new LLVMExpressionNode[elementCount];
            for (int i = 0; i < elementCount; i++) {
                types[i] = constant.getElementType(i);
                constants[i] = resolve(constant.getElement(i));
            }
            resolvedNode = nodeFactory.createStructureConstantNode(constant.getType(), getStackSpaceFactory, constant.isPacked(), types, constants);
        }

        @Override
        public void visit(VectorConstant constant) {
            final List<LLVMExpressionNode> values = new ArrayList<>();
            for (int i = 0; i < constant.getLength(); i++) {
                values.add(resolve(constant.getElement(i)));
            }
            resolvedNode = nodeFactory.createVectorLiteralNode(values, constant.getType());
        }

        @Override
        public void visit(BigIntegerConstant constant) {
            try {
                final Type type = constant.getType();
                if (Long.compareUnsigned(type.getBitSize(), Long.SIZE) <= 0) {
                    resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(constant.getValue().longValueExact(), type);
                } else {
                    resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(constant.getValue(), type);
                }
            } catch (TypeOverflowException e) {
                resolvedNode = Type.handleOverflowExpression(e);
            }
        }

        @Override
        public void visit(BinaryOperationConstant operation) {
            final LLVMExpressionNode lhs = resolve(operation.getLHS());
            final LLVMExpressionNode rhs = resolve(operation.getRHS());

            resolvedNode = LLVMBitcodeTypeHelper.createArithmeticInstruction(lhs, rhs, operation.getOperator(), operation.getType(), nodeFactory);
        }

        @Override
        public void visit(BlockAddressConstant constant) {
            final LLVMNativePointer blockAddress = LLVMNativePointer.create(constant.getBlockIndex());
            final PointerType type = new PointerType(null);
            resolvedNode = nodeFactory.createLiteral(blockAddress, type);
        }

        @Override
        public void visit(CastConstant constant) {
            final LLVMExpressionNode fromNode = resolve(constant.getValue());
            resolvedNode = LLVMBitcodeTypeHelper.createCast(fromNode, constant.getType(), constant.getValue().getType(), constant.getOperator(), nodeFactory);
        }

        @Override
        public void visit(CompareConstant compare) {
            final LLVMExpressionNode lhs = resolve(compare.getLHS());
            final LLVMExpressionNode rhs = resolve(compare.getRHS());

            resolvedNode = CommonNodeFactory.createComparison(compare.getOperator(), compare.getLHS().getType(), lhs, rhs);
        }

        @Override
        public void visit(DoubleConstant constant) {
            final double dVal = constant.getValue();
            resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(dVal, constant.getType());
        }

        @Override
        public void visit(FloatConstant constant) {
            final float fVal = constant.getValue();
            resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(fVal, constant.getType());
        }

        @Override
        public void visit(X86FP80Constant constant) {
            final byte[] xVal = constant.getValue();
            resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(xVal, constant.getType());
        }

        @Override
        public void visit(GetElementPointerConstant constant) {
            resolvedNode = resolveElementPointer(constant.getBasePointer(), constant.getIndices(), (symbol, excludeOtherIndex, other, others) -> resolve(symbol));
        }

        @Override
        public void visit(InlineAsmConstant inlineAsmConstant) {
            throw new LLVMParserException("Cannot resolve Inline ASM");
        }

        @Override
        public void visit(IntegerConstant constant) {
            final Type type = constant.getType();
            final long lVal = constant.getValue();
            if (type instanceof PrimitiveType) {
                switch (((PrimitiveType) type).getPrimitiveKind()) {
                    case I1:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(lVal != 0, type);
                        break;
                    case I8:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray((byte) lVal, type);
                        break;
                    case I16:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray((short) lVal, type);
                        break;
                    case I32:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray((int) lVal, type);
                        break;
                    case I64:
                        resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(lVal, type);
                        break;
                    default:
                        throw new LLVMParserException("Unsupported IntegerConstant: " + type);
                }
            } else if (type instanceof VariableBitWidthType) {
                resolvedNode = CommonNodeFactory.createSimpleConstantNoArray(lVal, type);
            } else {
                throw new LLVMParserException("Unsupported IntegerConstant: " + type);
            }
        }

        @Override
        public void visit(NullConstant nullConstant) {
            nullConstant.getType().accept(nullValueVisitor);
        }

        @Override
        public void visit(StringConstant constant) {
            final String chars = constant.getString();

            final LLVMExpressionNode[] values = new LLVMExpressionNode[chars.length() + (constant.isCString() ? 1 : 0)];
            for (int i = 0; i < chars.length(); i++) {
                values[i] = nodeFactory.createLiteral((byte) chars.charAt(i), PrimitiveType.I8);
            }
            if (constant.isCString()) {
                values[values.length - 1] = nodeFactory.createLiteral((byte) 0, PrimitiveType.I8);
            }
            resolvedNode = nodeFactory.createArrayLiteral(values, constant.getType(), getStackSpaceFactory);
        }

        @Override
        public void visit(UndefinedConstant undefinedConstant) {
            undefinedConstant.getType().accept(nullValueVisitor);
        }

        @Override
        public void visit(SelectConstant constant) {
            final LLVMExpressionNode conditionNode = resolve(constant.getCondition());
            final LLVMExpressionNode trueValueNode = resolve(constant.getTrueValue());
            final LLVMExpressionNode falseValueNode = resolve(constant.getFalseValue());
            resolvedNode = nodeFactory.createSelect(constant.getType(), conditionNode, trueValueNode, falseValueNode);
        }

        @Override
        public void visit(FunctionDeclaration toResolve) {
            LLVMFunction value = runtime.lookupFunction(toResolve.getName());
            resolvedNode = nodeFactory.createLiteral(value, toResolve.getType());
        }

        @Override
        public void visit(FunctionDefinition toResolve) {
            LLVMFunction value = runtime.lookupFunction(toResolve.getName());
            resolvedNode = nodeFactory.createLiteral(value, toResolve.getType());
        }

        @Override
        public void visit(GlobalAlias alias) {
            LLVMSymbol symbol = runtime.lookupSymbol(alias.getName());
            if (symbol.isFunction()) {
                LLVMFunction value = symbol.asFunction();
                resolvedNode = nodeFactory.createLiteral(value, alias.getType());
            } else if (symbol.isGlobalVariable()) {
                LLVMGlobal value = symbol.asGlobalVariable();
                resolvedNode = nodeFactory.createLiteral(value, alias.getType());
            } else {
                throw new LLVMParserException("Unexpected symbol: " + symbol.getClass());
            }
        }

        @Override
        public void visit(GlobalVariable global) {
            LLVMGlobal value = runtime.lookupGlobal(global.getName());
            resolvedNode = nodeFactory.createLiteral(value, new PointerType(global.getType()));
        }

        private void visitStackValue(SSAValue value) {
            FrameSlot slot = frame.findFrameSlot(value.getFrameIdentifier());
            if (slot == null) {
                slot = findOrAddFrameSlot(frame, value);
            }
            resolvedNode = CommonNodeFactory.createFrameRead(value.getType(), slot);
        }

        @Override
        public void visit(FunctionParameter param) {
            visitStackValue(param);
        }

        @Override
        public void visitValueInstruction(ValueInstruction value) {
            visitStackValue(value);
        }
    }

    public LLVMSymbolReadResolver(LLVMParserRuntime runtime, FrameDescriptor frame, GetStackSpaceFactory getStackSpaceFactory, DataLayout dataLayout, boolean storeSSAValueInSlot) {
        this.runtime = runtime;
        this.storeSSAValueInSlot = storeSSAValueInSlot;
        this.nodeFactory = runtime.getNodeFactory();
        this.frame = frame;
        this.getStackSpaceFactory = getStackSpaceFactory;
        this.dataLayout = dataLayout;
    }

    public FrameSlot findOrAddFrameSlot(FrameDescriptor descriptor, SSAValue value) {
        FrameSlot slot = descriptor.findFrameSlot(value.getFrameIdentifier());
        Object info = storeSSAValueInSlot ? value : null;
        if (slot == null) {
            slot = descriptor.findOrAddFrameSlot(value.getFrameIdentifier(), info, Type.getFrameSlotKind(value.getType()));
        }
        assert slot.getInfo() == info;
        return slot;
    }

    public static Integer evaluateIntegerConstant(SymbolImpl constant) {
        if (constant instanceof IntegerConstant) {
            assert ((IntegerConstant) constant).getValue() == (int) ((IntegerConstant) constant).getValue();
            return (int) ((IntegerConstant) constant).getValue();
        } else if (constant instanceof BigIntegerConstant) {
            return ((BigIntegerConstant) constant).getValue().intValueExact();
        } else if (constant instanceof NullConstant) {
            return 0;
        } else {
            return null;
        }
    }

    public static Long evaluateLongIntegerConstant(SymbolImpl constant) {
        if (constant instanceof IntegerConstant) {
            return ((IntegerConstant) constant).getValue();
        } else if (constant instanceof BigIntegerConstant) {
            return ((BigIntegerConstant) constant).getValue().longValueExact();
        } else if (constant instanceof NullConstant) {
            return 0L;
        } else {
            return null;
        }
    }

    public interface OptimizedResolver {
        LLVMExpressionNode resolve(SymbolImpl symbol, int excludeOtherIndex, SymbolImpl other, SymbolImpl... others);
    }

    /**
     * Turns a base value and a list of indices into a list of "get element pointer" operations, and
     * allows callers to intercept the resolution of values to nodes (used for frame slot
     * optimization in LLVMBitcodeInstructionVisitor).
     */
    public LLVMExpressionNode resolveElementPointer(SymbolImpl base, SymbolImpl[] indices, OptimizedResolver resolver) {
        LLVMExpressionNode[] indexNodes = new LLVMExpressionNode[indices.length];
        Long[] indexConstants = new Long[indices.length];
        Type[] indexTypes = new Type[indices.length];

        for (int i = indices.length - 1; i >= 0; i--) {
            SymbolImpl indexSymbol = indices[i];
            indexConstants[i] = evaluateLongIntegerConstant(indexSymbol);
            indexTypes[i] = indexSymbol.getType();
            if (indexConstants[i] == null) {
                indexNodes[i] = resolver.resolve(indexSymbol, i, base, indices);
            }
        }

        LLVMExpressionNode currentAddress = resolver.resolve(base, -1, null, indices);
        Type currentType = base.getType();

        return CommonNodeFactory.createNestedElementPointerNode(nodeFactory, dataLayout, indexNodes, indexConstants, indexTypes, currentAddress, currentType);
    }

    public LLVMExpressionNode resolve(SymbolImpl symbol) {
        if (symbol == null) {
            return null;
        }
        resolvedNode = null;
        symbol.accept(visitor);
        return resolvedNode;
    }
}
