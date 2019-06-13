/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.OpaqueType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public final class LLVMSymbolReadResolver {

    private final LLVMParserRuntime runtime;
    private final LLVMContext context;
    private final NodeFactory nodeFactory;
    private final FrameDescriptor frame;
    private final GetStackSpaceFactory getStackSpaceFactory;

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
                resolvedNode = nodeFactory.createSimpleConstantNoArray(null, type);
            }

            @Override
            public void visit(PrimitiveType type) {
                switch (type.getPrimitiveKind()) {
                    case I1:
                        resolvedNode = nodeFactory.createSimpleConstantNoArray(false, type);
                        break;
                    case I8:
                        resolvedNode = nodeFactory.createSimpleConstantNoArray((byte) 0, type);
                        break;
                    case I16:
                        resolvedNode = nodeFactory.createSimpleConstantNoArray((short) 0, type);
                        break;
                    case I32:
                        resolvedNode = nodeFactory.createSimpleConstantNoArray(0, type);
                        break;
                    case I64:
                        resolvedNode = nodeFactory.createSimpleConstantNoArray(0L, type);
                        break;
                    case FLOAT:
                        resolvedNode = nodeFactory.createSimpleConstantNoArray(0.0f, type);
                        break;
                    case DOUBLE:
                        resolvedNode = nodeFactory.createSimpleConstantNoArray(0.0d, type);
                        break;
                    case X86_FP80:
                        resolvedNode = nodeFactory.createSimpleConstantNoArray(null, type);
                        break;
                    default:
                        unsupportedType(type);
                }
            }

            @Override
            public void visit(MetaType metaType) {
                if (metaType == MetaType.DEBUG) {
                    resolvedNode = nodeFactory.createSimpleConstantNoArray(null, metaType);
                } else {
                    unsupportedType(metaType);
                }
            }

            @Override
            public void visit(PointerType type) {
                resolvedNode = nodeFactory.createSimpleConstantNoArray(null, type);
            }

            @Override
            public void visit(ArrayType type) {
                final int arraySize = context.getByteSize(type);
                if (arraySize == 0) {
                    resolvedNode = null;
                } else {
                    LLVMExpressionNode target = getStackSpaceFactory.createGetStackSpace(context, type);
                    resolvedNode = nodeFactory.createZeroNode(target, arraySize);
                }
            }

            @Override
            public void visit(StructureType structureType) {
                final int structSize = context.getByteSize(structureType);
                if (structSize == 0) {
                    final LLVMNativePointer minusOneNode = LLVMNativePointer.create(-1);
                    resolvedNode = nodeFactory.createLiteral(minusOneNode, new PointerType(structureType));
                } else {
                    LLVMExpressionNode addressnode = getStackSpaceFactory.createGetStackSpace(context, structureType);
                    resolvedNode = nodeFactory.createZeroNode(addressnode, structSize);
                }
            }

            @Override
            public void visit(VectorType vectorType) {
                final int nrElements = vectorType.getNumberOfElements();
                resolvedNode = nodeFactory.createZeroVectorInitializer(nrElements, vectorType);
            }

            @Override
            public void visit(VariableBitWidthType type) {
                resolvedNode = nodeFactory.createSimpleConstantNoArray(BigInteger.ZERO, type);
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
            final Type type = constant.getType();
            if (type.getBitSize() <= Long.SIZE) {
                resolvedNode = nodeFactory.createSimpleConstantNoArray(constant.getValue().longValueExact(), type);
            } else {
                resolvedNode = nodeFactory.createSimpleConstantNoArray(constant.getValue(), type);
            }
        }

        @Override
        public void visit(BinaryOperationConstant operation) {
            final LLVMExpressionNode lhs = resolve(operation.getLHS());
            final LLVMExpressionNode rhs = resolve(operation.getRHS());

            resolvedNode = LLVMBitcodeTypeHelper.createArithmeticInstruction(nodeFactory, lhs, rhs, operation.getOperator(), operation.getType());
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
            resolvedNode = LLVMBitcodeTypeHelper.createCast(nodeFactory, fromNode, constant.getType(), constant.getValue().getType(), constant.getOperator());
        }

        @Override
        public void visit(CompareConstant compare) {
            final LLVMExpressionNode lhs = resolve(compare.getLHS());
            final LLVMExpressionNode rhs = resolve(compare.getRHS());

            resolvedNode = nodeFactory.createComparison(compare.getOperator(), compare.getLHS().getType(), lhs, rhs);
        }

        @Override
        public void visit(DoubleConstant constant) {
            final double dVal = constant.getValue();
            resolvedNode = nodeFactory.createSimpleConstantNoArray(dVal, constant.getType());
        }

        @Override
        public void visit(FloatConstant constant) {
            final float fVal = constant.getValue();
            resolvedNode = nodeFactory.createSimpleConstantNoArray(fVal, constant.getType());
        }

        @Override
        public void visit(X86FP80Constant constant) {
            final byte[] xVal = constant.getValue();
            resolvedNode = nodeFactory.createSimpleConstantNoArray(xVal, constant.getType());
        }

        @Override
        public void visit(GetElementPointerConstant constant) {
            resolvedNode = resolveElementPointer(constant.getBasePointer(), constant.getIndices());
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
                        resolvedNode = nodeFactory.createSimpleConstantNoArray(lVal != 0, type);
                        break;
                    case I8:
                        resolvedNode = nodeFactory.createSimpleConstantNoArray((byte) lVal, type);
                        break;
                    case I16:
                        resolvedNode = nodeFactory.createSimpleConstantNoArray((short) lVal, type);
                        break;
                    case I32:
                        resolvedNode = nodeFactory.createSimpleConstantNoArray((int) lVal, type);
                        break;
                    case I64:
                        resolvedNode = nodeFactory.createSimpleConstantNoArray(lVal, type);
                        break;
                    default:
                        throw new LLVMParserException("Unsupported IntegerConstant: " + type);
                }
            } else if (type instanceof VariableBitWidthType) {
                resolvedNode = nodeFactory.createSimpleConstantNoArray(lVal, type);
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
            LLVMManagedPointer value = LLVMManagedPointer.create(runtime.lookupFunction(toResolve.getName(), toResolve.isOverridable()));
            resolvedNode = nodeFactory.createLiteral(value, toResolve.getType());
        }

        @Override
        public void visit(FunctionDefinition toResolve) {
            LLVMManagedPointer value = LLVMManagedPointer.create(runtime.lookupFunction(toResolve.getName(), toResolve.isOverridable()));
            resolvedNode = nodeFactory.createLiteral(value, toResolve.getType());
        }

        @Override
        public void visit(GlobalAlias alias) {
            LLVMSymbol symbol = runtime.lookupSymbol(alias.getName(), alias.isOverridable());
            if (symbol.isFunction()) {
                LLVMManagedPointer value = LLVMManagedPointer.create(symbol.asFunction());
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
            LLVMGlobal value = runtime.lookupGlobal(global.getName(), global.isOverridable());
            resolvedNode = nodeFactory.createLiteral(value, new PointerType(global.getType()));
        }

        @Override
        public void visit(FunctionParameter param) {
            final FrameSlot slot = frame.findFrameSlot(param.getName());
            resolvedNode = nodeFactory.createFrameRead(param.getType(), slot);
        }

        @Override
        public void visitValueInstruction(ValueInstruction value) {
            final FrameSlot slot = frame.findFrameSlot(value.getName());
            resolvedNode = nodeFactory.createFrameRead(value.getType(), slot);
        }
    }

    public LLVMSymbolReadResolver(LLVMParserRuntime runtime, FrameDescriptor frame, GetStackSpaceFactory getStackSpaceFactory) {
        this.runtime = runtime;
        this.context = runtime.getContext();
        this.nodeFactory = context.getLanguage().getNodeFactory();
        this.frame = frame;
        this.getStackSpaceFactory = getStackSpaceFactory;
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

    public LLVMExpressionNode resolveElementPointer(SymbolImpl base, List<SymbolImpl> indices) {
        LLVMExpressionNode currentAddress = resolve(base);
        Type currentType = base.getType();

        for (int i = 0, indicesSize = indices.size(); i < indicesSize; i++) {
            final SymbolImpl indexSymbol = indices.get(i);
            final Type indexType = indexSymbol.getType();

            final Long indexInteger = evaluateLongIntegerConstant(indexSymbol);
            if (indexInteger == null) {
                // the index is determined at runtime
                if (currentType instanceof StructureType) {
                    // according to http://llvm.org/docs/LangRef.html#getelementptr-instruction
                    throw new LLVMParserException("Indices on structs must be constant integers!");
                }
                AggregateType aggregate = (AggregateType) currentType;
                final long indexedTypeLength = context.getIndexOffset(1, aggregate);
                currentType = aggregate.getElementType(1);
                final LLVMExpressionNode indexNode = resolve(indexSymbol);
                currentAddress = nodeFactory.createTypedElementPointer(currentAddress, indexNode, indexedTypeLength, currentType);
            } else {
                // the index is a constant integer
                AggregateType aggregate = (AggregateType) currentType;
                final long addressOffset = context.getIndexOffset(indexInteger, aggregate);
                currentType = aggregate.getElementType(indexInteger);

                // creating a pointer inserts type information, this needs to happen for the address
                // computed by getelementptr even if it is the same as the basepointer
                if (addressOffset != 0 || i == indicesSize - 1) {
                    final LLVMExpressionNode indexNode;
                    if (indexType == PrimitiveType.I32) {
                        indexNode = nodeFactory.createLiteral(1, PrimitiveType.I32);
                    } else if (indexType == PrimitiveType.I64) {
                        indexNode = nodeFactory.createLiteral(1L, PrimitiveType.I64);
                    } else {
                        throw new AssertionError(indexType);
                    }
                    currentAddress = nodeFactory.createTypedElementPointer(currentAddress, indexNode, addressOffset, currentType);
                }
            }
        }

        return currentAddress;
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
