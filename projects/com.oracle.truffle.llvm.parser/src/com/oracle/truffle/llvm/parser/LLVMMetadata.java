/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser;

import com.oracle.truffle.llvm.parser.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.constants.MetadataConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitorAdapter;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.types.MetadataVisitor;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBaseNode;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBasicType;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBlock;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBlock.MetadataReference;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataCompositeType;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataDerivedType;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataFnNode;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataNode;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataReferenceType;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataString;
import com.oracle.truffle.llvm.runtime.types.metadata.subtypes.MetadataSubtypeName;
import com.oracle.truffle.llvm.runtime.types.metadata.subtypes.MetadataSubtypeType;
import com.oracle.truffle.llvm.runtime.types.metadata.subtypes.MetadataSubytypeSizeAlignOffset;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

/**
 * Parse all those "@llvm.dbg.declare" call instructions and add the fitting Metadata reference to
 * the referenced instruction to allow parsing of those data later one.
 */
final class LLVMMetadata implements ModelVisitor {

    public static LLVMMetadata generate(ModelModule model, DataLayoutConverter.DataSpecConverterImpl targetDataLayout) {
        LLVMMetadata visitor = new LLVMMetadata(targetDataLayout);

        model.accept(visitor);

        return visitor;
    }

    private final DataLayoutConverter.DataSpecConverterImpl targetDataLayout;

    private LLVMMetadata(DataLayoutConverter.DataSpecConverterImpl targetDataLayout) {
        this.targetDataLayout = targetDataLayout;
    }

    @Override
    public void visit(FunctionDefinition function) {
        LLVMMetadataFunctionVisitor visitor = new LLVMMetadataFunctionVisitor(function.getMetadata());

        function.accept(visitor);
    }

    private final class LLVMMetadataFunctionVisitor implements FunctionVisitor, InstructionVisitorAdapter {
        private InstructionBlock currentBlock = null;

        private final MetadataBlock metadata;

        private LLVMMetadataFunctionVisitor(MetadataBlock metadata) {
            this.metadata = metadata;
        }

        @Override
        public void visit(InstructionBlock block) {
            this.currentBlock = block;
            block.accept(this);
        }

        /*
         * TODO: metadata seems to be misalign by 8
         *
         * I don't know why, but there is a little misalign between calculated and real metadata id.
         * This has to be solved in the future!
         *
         * Possible issues could probably arrive when there are changes in the number of MDKinds.
         * This has to be evaluated in the future
         */
        private static final int SYMBOL_MISALIGN = 8;

        /**
         * Check if the current call instruction declares a variable.
         *
         * If yes, we can link the type informations with the metadata informations parsed before,
         * and use those linking to get additional type informations if needed. Like getting the
         * name of a structure element when doing an GetElementPointerInstruction.
         */
        @Override
        public void visit(VoidCallInstruction call) {
            Symbol callTarget = call.getCallTarget();

            if (callTarget instanceof FunctionDeclaration) {
                if (((FunctionDeclaration) (callTarget)).getName().equals("@llvm.dbg.declare")) {

                    int symbolMetadataId = (int) ((MetadataConstant) call.getArgument(0)).getValue() + SYMBOL_MISALIGN;
                    int symbolIndex = ((MetadataFnNode) metadata.get(symbolMetadataId)).getPointer().getSymbolIndex();
                    long metadataId = ((MetadataConstant) call.getArgument(1)).getValue();
                    Symbol referencedSymbol = currentBlock.getFunctionSymbols().getSymbol(symbolIndex);

                    MetadataSubtypeType localVar = (MetadataSubtypeType) metadata.getReference(metadataId).get();
                    MetadataReference typeReference = localVar.getType();

                    if (referencedSymbol instanceof AllocateInstruction) {
                        linkTypeToMetadataInformations(((AllocateInstruction) referencedSymbol).getPointeeType(), typeReference);
                    }
                }
            }
        }

        private void linkTypeToMetadataInformations(Type target, MetadataReference sourceReference) {
            /*
             * currently, we only attach Metadata type informations to Reference Types (Arrays,
             * Vectors, Structures)
             */
            if (target instanceof MetadataReferenceType) {
                MetadataReferenceType metadataRefType = (MetadataReferenceType) target;
                metadataRefType.setValidatedMetadataReference(getBaseType(sourceReference));
            }
        }

        private MetadataReference getBaseType(MetadataReference type) {
            if (type.get() instanceof MetadataDerivedType) {
                return ((MetadataDerivedType) type.get()).getTrueBaseType();
            }
            return type;
        }

        /**
         * Try to get the corresponding variable name, which is fetched by the
         * GetElementPointerInstruction.
         *
         * The Problem is, the correct metadata is currently only referenced on AllocateInstruction,
         * which means we have to find the correct metadata node and the correct type offset, to be
         * able to get the correct name of the function.
         *
         * This is only a first implementation, to find simple element names references.
         */
        @Override
        public void visit(GetElementPointerInstruction gep) {
            if (gep.getBasePointer() instanceof Instruction) {
                Instruction bpInstr = (Instruction) gep.getBasePointer();
                bpInstr.accept(new InstructionVisitorAdapter() {
                    @Override
                    public void visit(AllocateInstruction allocate) {
                        Type pointeeType = allocate.getPointeeType();
                        if (pointeeType instanceof StructureType) {
                            handleGetElementPointerInstructionOfStructure(gep, (StructureType) pointeeType);
                        }
                    }

                    @Override
                    public void visit(CastInstruction cast) {
                        /*
                         * Our problem is that bitcast operations are done in a way where it's
                         * pretty complicated to find out what changed. To be more specific, we only
                         * see what byte we modify, and then there is a longer list of operations,
                         * like "and", "or", "shift",... to get the actual number of this byte.
                         */
                    }

                    @Override
                    public void visit(GetElementPointerInstruction instr) {
                        /*
                         * This instruction likely refers to a new type, which means we have to find
                         * out what type is referenced here and use it for further name calculation
                         * (required for array inside structures, structure inside structure, ...).
                         */
                    }
                });
            }
        }

        /**
         * Our GetElementPointerInstruction points to a structure. Let's try to find the
         * corresponding variable name and append it to the GetElementPointerInstruction.
         */
        private void handleGetElementPointerInstructionOfStructure(GetElementPointerInstruction gep, StructureType struct) {

            if (!struct.getMetadataReference().isPresent()) {
                return;
            }

            MetadataBaseNode metadataNode = struct.getMetadataReference().get();

            metadataNode.accept(new MetadataVisitor() {
                @Override
                public void visit(MetadataCompositeType alias) {
                    parseCompositeTypeStruct(gep, struct, alias);
                }

                @Override
                public void visit(MetadataDerivedType alias) {
                    // TODO: type check
                    /*
                     * TODO: what about getBaseType which we used before? Why are we not already
                     * referencing MetadataCompositeType?
                     */
                    MetadataCompositeType compNode = (MetadataCompositeType) alias.getBaseType().get();
                    parseCompositeTypeStruct(gep, struct, compNode);
                }

                @Override
                public void visit(MetadataBasicType alias) {
                    // TODO: implement?
                }

                @Override
                public void ifVisitNotOverwritten(MetadataBaseNode alias) {
                    throw new AssertionError("unknow node type: " + alias);
                }
            });
        }

        /**
         * When we have a GetElementPointerInstruction on a structure, we can find the name of the
         * referenced variable simply by comparing the element Offset given by the structureType
         * with the offset defined in the metadata of the structure.
         */
        private void parseCompositeTypeStruct(GetElementPointerInstruction target, StructureType struct, MetadataCompositeType node) {
            struct.setName(((MetadataString) node.getName().get()).getString());

            Symbol idx = target.getIndices().get(1);
            // either the symbol is an IntegerConstant, or null, which simply represents the value 0
            int parsedIndex = idx instanceof IntegerConstant ? (int) ((IntegerConstant) (idx)).getValue() : 0;

            long offset = struct.getOffsetOf(parsedIndex, targetDataLayout) * Byte.SIZE;

            MetadataReference ref = parseMetadataReferenceFromOffset(offset, node);
            if (ref.isPresent()) {
                setElementPointerName(target, ref.get());
            }
        }

        /**
         * check which offset matches the given one in the metadata and set the found element name.
         */
        private MetadataReference parseMetadataReferenceFromOffset(long offset, MetadataCompositeType node) {
            MetadataNode elements = (MetadataNode) node.getMemberDescriptors().get();
            for (MetadataReference element : elements) {
                if (getOffset(element.get()) == offset) {
                    return element;
                }
            }
            return MetadataBlock.voidRef;
        }

        /**
         * get the offset of a given MetadataNode.
         */
        private long getOffset(MetadataBaseNode element) {
            if (element instanceof MetadataSubytypeSizeAlignOffset) {
                return ((MetadataSubytypeSizeAlignOffset) element).getOffset();
            }
            throw new AssertionError("unknow node type: " + element);
        }

        /**
         * Assign the variable name of the metadata node to the corresponding
         * GetElementPointerInstruction.
         *
         * @param gep Our GetElementPointerInstruction which now get's a name reference
         * @param element The Metadata Node which contains informations about the element, which is
         *            retrieved in the GetElementPointerInstruction
         */
        private void setElementPointerName(GetElementPointerInstruction gep, MetadataBaseNode element) {
            if (element instanceof MetadataSubtypeName) {
                if (((MetadataSubtypeName) element).getName().isPresent()) {
                    String elementName = ((MetadataString) ((MetadataSubtypeName) element).getName().get()).getString();
                    LLVMLogger.info("Derived name = " + elementName);
                    gep.setReferenceName(elementName);
                } else {
                    LLVMLogger.info("There is no element name present, which we can use");
                }
            } else {
                LLVMLogger.info("This is not a valid Metadata Type: " + element);
            }
        }
    }
}
