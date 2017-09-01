/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.parser.LLVMLabelList;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.NodeFactory;
import com.oracle.truffle.llvm.parser.instructions.LLVMArithmeticInstructionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMConversionType;
import com.oracle.truffle.llvm.parser.instructions.LLVMLogicalInstructionKind;
import com.oracle.truffle.llvm.parser.model.enums.Flag;
import com.oracle.truffle.llvm.parser.model.enums.Linkage;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BinaryOperationConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BlockAddressConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CompareConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.GetElementPointerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.InlineAsmConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.MetadataConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
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
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalValueSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.ConstantVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.ValueInstructionVisitor;
import com.oracle.truffle.llvm.parser.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
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
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LLVMSymbolReadResolver {

    private final LLVMParserRuntime runtime;
    private final FunctionDefinition method;
    private final FrameDescriptor frame;
    private final Map<String, Integer> labels;
    private final LLVMLabelList allLabels;

    private final InternalVisitor visitor = new InternalVisitor();
    private LLVMExpressionNode resolvedNode = null;

    private static void unsupported(Object obj) {
        throw new UnsupportedOperationException("Cannot resolve symbol: " + obj);
    }

    private final class InternalVisitor extends ValueInstructionVisitor implements ConstantVisitor, ModelVisitor {

        private final TypeVisitor nullValueVisitor = new TypeVisitor() {

            private void unsupportedType(Type type) {
                throw new UnsupportedOperationException("Unsupported Type for Zero Constant: " + type);
            }

            @Override
            public void visit(FunctionType type) {
                resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, null, type);
            }

            @Override
            public void visit(PrimitiveType type) {
                switch (type.getPrimitiveKind()) {
                    case I1:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, false, type);
                        break;
                    case I8:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, (byte) 0, type);
                        break;
                    case I16:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, (short) 0, type);
                        break;
                    case I32:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, 0, type);
                        break;
                    case I64:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, 0L, type);
                        break;
                    case FLOAT:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, 0.0f, type);
                        break;
                    case DOUBLE:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, 0.0d, type);
                        break;
                    case X86_FP80:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, null, type);
                        break;
                    default:
                        unsupportedType(type);
                }
            }

            @Override
            public void visit(MetaType metaType) {
                if (metaType == MetaType.DEBUG) {
                    resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, null, metaType);
                } else {
                    unsupportedType(metaType);
                }
            }

            @Override
            public void visit(PointerType type) {
                resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, null, type);
            }

            @Override
            public void visit(ArrayType type) {
                final int arraySize = runtime.getByteSize(type);
                if (arraySize == 0) {
                    resolvedNode = null;
                } else {
                    final LLVMExpressionNode target = runtime.allocateFunctionLifetime(type, runtime.getByteSize(type), runtime.getByteAlignment(type));
                    resolvedNode = runtime.getNodeFactory().createZeroNode(runtime, target, arraySize);
                }
            }

            @Override
            public void visit(StructureType structureType) {
                final int structSize = runtime.getByteSize(structureType);
                if (structSize == 0) {
                    final LLVMAddress minusOneNode = LLVMAddress.fromLong(-1);
                    resolvedNode = runtime.getNodeFactory().createLiteral(runtime, minusOneNode, new PointerType(structureType));
                } else {
                    final int alignment = runtime.getByteAlignment(structureType);
                    final LLVMExpressionNode addressnode = runtime.allocateFunctionLifetime(structureType, structSize, alignment);
                    resolvedNode = runtime.getNodeFactory().createZeroNode(runtime, addressnode, structSize);
                }
            }

            @Override
            public void visit(VectorType vectorType) {
                final int nrElements = vectorType.getNumberOfElements();
                resolvedNode = runtime.getNodeFactory().createZeroVectorInitializer(runtime, nrElements, vectorType);
            }

            @Override
            public void visit(VariableBitWidthType type) {
                resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, BigInteger.ZERO, type);
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
        public void defaultAction(Object obj) {
            unsupported(obj);
        }

        @Override
        public void defaultAction(Instruction inst) {
            unsupported(inst);
        }

        @Override
        public void visit(ArrayConstant array) {
            final List<LLVMExpressionNode> values = new ArrayList<>(array.getElementCount());
            for (int i = 0; i < array.getElementCount(); i++) {
                values.add(resolve(array.getElement(i)));
            }
            final Type arrayType = array.getType();
            resolvedNode = runtime.getNodeFactory().createArrayLiteral(runtime, values, arrayType);
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
            resolvedNode = runtime.getNodeFactory().createStructureConstantNode(runtime, constant.getType(), constant.isPacked(), types, constants);
        }

        @Override
        public void visit(VectorConstant constant) {
            final List<LLVMExpressionNode> values = new ArrayList<>();
            for (int i = 0; i < constant.getLength(); i++) {
                values.add(resolve(constant.getElement(i)));
            }
            resolvedNode = runtime.getNodeFactory().createVectorLiteralNode(runtime, values, constant.getType());
        }

        @Override
        public void visit(BigIntegerConstant constant) {
            final Type type = constant.getType();
            if (type.getBitSize() <= Long.SIZE) {
                resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, constant.getValue().longValueExact(), type);
            } else {
                resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, constant.getValue(), type);
            }
        }

        @Override
        public void visit(BinaryOperationConstant operation) {
            final LLVMExpressionNode lhs = resolve(operation.getLHS());
            final LLVMExpressionNode rhs = resolve(operation.getRHS());
            final Type baseType = operation.getType();

            final LLVMArithmeticInstructionType arithmeticInstructionType = LLVMBitcodeTypeHelper.toArithmeticInstructionType(operation.getOperator());
            if (arithmeticInstructionType != null) {
                resolvedNode = runtime.getNodeFactory().createArithmeticOperation(runtime, lhs, rhs, arithmeticInstructionType, baseType, new Flag[0]);
                return;
            }

            final LLVMLogicalInstructionKind logicalInstructionType = LLVMBitcodeTypeHelper.toLogicalInstructionType(operation.getOperator());
            if (logicalInstructionType != null) {
                resolvedNode = runtime.getNodeFactory().createLogicalOperation(runtime, lhs, rhs, logicalInstructionType, baseType, new Flag[0]);
                return;
            }

            throw new UnsupportedOperationException("Unsupported Binary Operator: " + operation.getOperator());
        }

        @Override
        public void visit(BlockAddressConstant constant) {
            int val;
            if (allLabels != null) {
                val = allLabels.labels(constant.getFunction().getName()).get(constant.getInstructionBlock().getName());
            } else {
                assert constant.getFunction() == method;
                val = labels.get(constant.getInstructionBlock().getName());
            }
            resolvedNode = runtime.getNodeFactory().createLiteral(runtime, LLVMAddress.fromLong(val), new PointerType(null));
        }

        @Override
        public void visit(CastConstant constant) {
            final LLVMConversionType type = LLVMBitcodeTypeHelper.toConversionType(constant.getOperator());
            final LLVMExpressionNode fromNode = resolve(constant.getValue());
            resolvedNode = runtime.getNodeFactory().createCast(runtime, fromNode, constant.getType(), constant.getValue().getType(), type);
        }

        @Override
        public void visit(CompareConstant compare) {
            final LLVMExpressionNode lhs = resolve(compare.getLHS());
            final LLVMExpressionNode rhs = resolve(compare.getRHS());
            resolvedNode = runtime.getNodeFactory().createComparison(runtime, compare.getOperator(), compare.getLHS().getType(), lhs, rhs);
        }

        @Override
        public void visit(DoubleConstant constant) {
            final double dVal = constant.getValue();
            resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, dVal, constant.getType());
        }

        @Override
        public void visit(FloatConstant constant) {
            final float fVal = constant.getValue();
            resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, fVal, constant.getType());
        }

        @Override
        public void visit(X86FP80Constant constant) {
            final byte[] xVal = constant.getValue();
            resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, xVal, constant.getType());
        }

        @Override
        public void visit(GetElementPointerConstant constant) {
            resolvedNode = resolveElementPointer(constant.getBasePointer(), constant.getIndices());
        }

        @Override
        public void visit(InlineAsmConstant inlineAsmConstant) {
            throw new AssertionError("Cannot resolve Inline ASM");
        }

        @Override
        public void visit(IntegerConstant constant) {
            final Type type = constant.getType();
            final long lVal = constant.getValue();
            if (type instanceof PrimitiveType) {
                switch (((PrimitiveType) type).getPrimitiveKind()) {
                    case I1:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, lVal != 0, type);
                        break;
                    case I8:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, (byte) lVal, type);
                        break;
                    case I16:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, (short) lVal, type);
                        break;
                    case I32:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, (int) lVal, type);
                        break;
                    case I64:
                        resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, lVal, type);
                        break;
                    default:
                        throw new UnsupportedOperationException("Unsupported IntegerConstant: " + type);
                }
            } else if (type instanceof VariableBitWidthType) {
                resolvedNode = runtime.getNodeFactory().createSimpleConstantNoArray(runtime, lVal, type);
            } else {
                throw new UnsupportedOperationException("Unsupported IntegerConstant: " + type);
            }
        }

        @Override
        public void visit(NullConstant nullConstant) {
            nullConstant.getType().accept(nullValueVisitor);
        }

        @Override
        public void visit(StringConstant constant) {
            final String chars = constant.getString();

            final NodeFactory nodeFactory = runtime.getNodeFactory();
            final List<LLVMExpressionNode> values = new ArrayList<>(chars.length());
            for (int i = 0; i < chars.length(); i++) {
                values.add(nodeFactory.createLiteral(runtime, (byte) chars.charAt(i), PrimitiveType.I8));
            }
            if (constant.isCString()) {
                values.add(nodeFactory.createLiteral(runtime, (byte) 0, PrimitiveType.I8));
            }

            resolvedNode = nodeFactory.createArrayLiteral(runtime, values, constant.getType());
        }

        @Override
        public void visit(UndefinedConstant undefinedConstant) {
            undefinedConstant.getType().accept(nullValueVisitor);
        }

        @Override
        public void visit(MetadataConstant constant) {
            // TODO: point to Metadata
            resolvedNode = runtime.getNodeFactory().createLiteral(runtime, constant.getValue(), PrimitiveType.I64);
        }

        @Override
        public void visit(FunctionDeclaration toResolve) {
            final boolean global = !Linkage.isFileLocal(toResolve.getLinkage());
            final LLVMContext.FunctionFactory generator = i -> LLVMFunctionDescriptor.createDescriptor(runtime.getContext(), toResolve.getName(), toResolve.getType(), i);
            final Object value = runtime.getScope().lookupOrCreateFunction(runtime.getContext(), toResolve.getName(), global, generator);
            resolvedNode = runtime.getNodeFactory().createLiteral(runtime, value, toResolve.getType());
        }

        @Override
        public void visit(FunctionDefinition toResolve) {
            final boolean global = !Linkage.isFileLocal(toResolve.getLinkage());
            final LLVMContext.FunctionFactory generator = i -> LLVMFunctionDescriptor.createDescriptor(runtime.getContext(), toResolve.getName(), toResolve.getType(), i);
            final Object value = runtime.getScope().lookupOrCreateFunction(runtime.getContext(), toResolve.getName(), global, generator);
            resolvedNode = runtime.getNodeFactory().createLiteral(runtime, value, toResolve.getType());
        }

        @Override
        public void visit(GlobalAlias alias) {
            resolvedNode = runtime.getGlobalAddress(LLVMSymbolReadResolver.this, alias);
        }

        @Override
        public void visit(GlobalConstant constant) {
            resolvedNode = runtime.getGlobalAddress(LLVMSymbolReadResolver.this, constant);
        }

        @Override
        public void visit(GlobalVariable variable) {
            resolvedNode = runtime.getGlobalAddress(LLVMSymbolReadResolver.this, variable);
        }

        @Override
        public void visitValueInstruction(ValueInstruction value) {
            final FrameSlot slot = frame.findFrameSlot(value.getName());
            resolvedNode = runtime.getNodeFactory().createFrameRead(runtime, value.getType(), slot);
        }
    }

    public LLVMSymbolReadResolver(LLVMParserRuntime runtime, LLVMLabelList allLabels) {
        this(runtime, null, null, null, allLabels);
    }

    public LLVMSymbolReadResolver(LLVMParserRuntime runtime, FunctionDefinition method, FrameDescriptor frame, Map<String, Integer> labels) {
        this(runtime, method, frame, labels, null);
    }

    private LLVMSymbolReadResolver(LLVMParserRuntime runtime, FunctionDefinition method, FrameDescriptor frame, Map<String, Integer> labels, LLVMLabelList allLabels) {
        this.runtime = runtime;
        this.method = method;
        this.frame = frame;
        this.labels = labels;
        this.allLabels = allLabels;
    }

    public static Integer evaluateIntegerConstant(Symbol constant) {
        if (constant instanceof IntegerConstant) {
            return (int) ((IntegerConstant) constant).getValue();
        } else if (constant instanceof BigIntegerConstant) {
            return ((BigIntegerConstant) constant).getValue().intValueExact();
        } else if (constant instanceof NullConstant) {
            return 0;
        } else {
            return null;
        }
    }

    public LLVMExpressionNode resolveElementPointer(Symbol base, List<Symbol> indices) {
        LLVMExpressionNode currentAddress = resolve(base);
        Type currentType = base.getType();

        for (int i = 0, indicesSize = indices.size(); i < indicesSize; i++) {
            final Symbol indexSymbol = indices.get(i);
            final Type indexType = indexSymbol.getType();

            final Integer indexInteger = evaluateIntegerConstant(indexSymbol);
            if (indexInteger == null) {
                // the index is determined at runtime
                if (currentType instanceof StructureType) {
                    // according to http://llvm.org/docs/LangRef.html#getelementptr-instruction
                    throw new IllegalStateException("Indices on structs must be constant integers!");
                }
                AggregateType aggregate = (AggregateType) currentType;
                final int indexedTypeLength = runtime.getIndexOffset(1, aggregate);
                currentType = aggregate.getElementType(1);
                final LLVMExpressionNode indexNode = resolve(indexSymbol);
                currentAddress = runtime.getNodeFactory().createTypedElementPointer(runtime, currentAddress, indexNode, indexedTypeLength, currentType);
            } else {
                // the index is a constant integer
                AggregateType aggregate = (AggregateType) currentType;
                final int addressOffset = runtime.getIndexOffset(indexInteger, aggregate);
                currentType = aggregate.getElementType(indexInteger);

                // creating a pointer inserts type information, this needs to happen for the address
                // computed by getelementptr even if it is the same as the basepointer
                if (addressOffset != 0 || i == indicesSize - 1) {
                    final LLVMExpressionNode indexNode;
                    if (indexType == PrimitiveType.I32) {
                        indexNode = runtime.getNodeFactory().createLiteral(runtime, 1, PrimitiveType.I32);
                    } else if (indexType == PrimitiveType.I64) {
                        indexNode = runtime.getNodeFactory().createLiteral(runtime, 1L, PrimitiveType.I64);
                    } else {
                        throw new AssertionError(indexType);
                    }
                    currentAddress = runtime.getNodeFactory().createTypedElementPointer(runtime, currentAddress, indexNode, addressOffset, currentType);
                }
            }
        }

        return currentAddress;
    }

    public LLVMExpressionNode resolve(Symbol symbol) {
        resolvedNode = null;
        if (symbol instanceof ValueInstruction) {
            ((ValueInstruction) symbol).accept(visitor);

        } else if (symbol instanceof Constant) {
            ((Constant) symbol).accept(visitor);

        } else if (symbol instanceof GlobalValueSymbol) {
            ((GlobalValueSymbol) symbol).accept(visitor);

        } else if (symbol instanceof FunctionParameter) {
            final FrameSlot slot = frame.findFrameSlot(((FunctionParameter) symbol).getName());
            resolvedNode = runtime.getNodeFactory().createFrameRead(runtime, symbol.getType(), slot);

        } else {
            unsupported(symbol);
        }

        return resolvedNode;
    }
}
