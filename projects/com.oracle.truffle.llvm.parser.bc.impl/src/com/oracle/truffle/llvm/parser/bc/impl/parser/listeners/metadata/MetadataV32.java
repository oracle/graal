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
package com.oracle.truffle.llvm.parser.bc.impl.parser.ir.module;

import java.util.List;

import com.oracle.truffle.llvm.parser.base.model.blocks.MetadataBlock;
import com.oracle.truffle.llvm.parser.base.model.enums.DwLangNameRecord;
import com.oracle.truffle.llvm.parser.base.model.generators.SymbolGenerator;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataBasicType;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataCompileUnit;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataCompositeType;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataDerivedType;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataEnumerator;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataFile;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataFnNode;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataGlobalVariable;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataLexicalBlock;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataLexicalBlockFile;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataLocalVariable;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataNode;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataSubprogram;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataSubrange;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataTemplateTypeParameter;
import com.oracle.truffle.llvm.parser.base.model.types.IntegerConstantType;
import com.oracle.truffle.llvm.parser.base.model.types.MetadataConstantPointerType;
import com.oracle.truffle.llvm.parser.base.model.types.MetadataConstantType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;
import com.oracle.truffle.llvm.parser.bc.impl.parser.ir.module.records.MetadataRecord;
import com.oracle.truffle.llvm.parser.bc.impl.parser.ir.module.records.DwTagRecord;

public class MetadataV32 extends Metadata {
    public MetadataV32(Types types, List<Type> symbols, SymbolGenerator generator) {
        super(types, symbols, generator);
        // it seem's like there is a different offset of the id in LLVM 3.2 and LLVM 3.8
        metadata.setStartIndex(0);
    }

    protected boolean asInt1(Type t) {
        return ((IntegerConstantType) t).getValue() == 1;
    }

    protected int asInt32(Type t) {
        return (int) ((IntegerConstantType) t).getValue();
    }

    protected long asInt64(Type t) {
        return ((IntegerConstantType) t).getValue();
    }

    @Override
    public void record(long id, long[] args) {
        MetadataRecord record = MetadataRecord.decode(id);

        switch (record) {
            case OLD_NODE:
                createOldNode(args);
                break;

            case OLD_FN_NODE:
                createOldFnNode(args);
                break;

            default:
                super.record(id, args);
                return;
        }

        if (LLVMBaseOptionFacade.verboseEnabled()) {
            printMetadataDebugMsg();
        }
    }

    protected void createOldNode(long[] args) {
        MetadataArgumentParser parsedArgs = new MetadataArgumentParser(types, symbols, args);

        if (parsedArgs.peek() instanceof MetadataConstantType) {
            createDwNode(parsedArgs);
        } else if (parsedArgs.peek() instanceof IntegerConstantType) {
            /*
             * http://llvm.org/releases/3.2/docs/SourceLevelDebugging.html#LLVMDebugVersion
             *
             * The first field of a descriptor is always an i32 containing a tag value identifying
             * the content of the descriptor. The remaining fields are specific to the descriptor.
             * The values of tags are loosely bound to the tag values of DWARF information entries.
             * However, that does not restrict the use of the information supplied to DWARF targets.
             * To facilitate versioning of debug information, the tag is augmented with the current
             * debug version (LLVMDebugVersion = 8 << 16 or 0x80000 or 524288.)
             */
            int ident = asInt32(parsedArgs.next());
            DwTagRecord record = DwTagRecord.decode(ident);
            /*
             * How the data is stored: http://llvm.org/releases/3.2/docs/SourceLevelDebugging.html
             */
            switch (record) {
                case DW_TAG_VARIABLE:
                    createDwTagGlobalVariable(parsedArgs);
                    break;

                case DW_TAG_COMPILE_UNIT:
                    createDwTagCompileUnit(parsedArgs);
                    break;

                case DW_TAG_FILE_TYPE:
                    createDwTagFileType(parsedArgs);
                    break;

                case DW_TAG_BASE_TYPE:
                    createDwTagBasicType(parsedArgs);
                    break;

                case DW_TAG_ARRAY_TYPE:
                case DW_TAG_ENUMERATION_TYPE:
                case DW_TAG_STRUCTURE_TYPE:
                case DW_TAG_UNION_TYPE:
                case DW_TAG_VECTOR_TYPE:
                case DW_TAG_SUBROUTINE_TYPE:
                case DW_TAG_INHERITANCE:
                case DW_TAG_CLASS_TYPE: // TODO: correct?
                    createDwCompositeType(parsedArgs);
                    break;

                case DW_TAG_FORMAL_PARAMETER:
                case DW_TAG_MEMBER:
                case DW_TAG_POINTER_TYPE:
                case DW_TAG_REFERENCE_TYPE:
                case DW_TAG_TYPEDEF:
                case DW_TAG_CONST_TYPE:
                case DW_TAG_VOLATILE_TYPE:
                case DW_TAG_RESTRICT_TYPE:
                    createDwDerivedType(parsedArgs);
                    break;

                case DW_TAG_AUTO_VARIABLE:
                case DW_TAG_ARG_VARIABLE:
                case DW_TAG_RETURN_VARIABLE:
                    createDwTagLocalVariable(parsedArgs);
                    break;

                case DW_TAG_ENUMERATOR:
                    createDwTagEnumerator(parsedArgs);
                    break;

                case DW_TAG_SUBPROGRAM:
                    createDwTagSubprogram(parsedArgs);
                    break;

                case DW_TAG_SUBRANGE_TYPE:
                    createDwTagSubrangeType(parsedArgs);
                    break;

                case DW_TAG_LEXICAL_BLOCK:
                    createDwTagLexicalBlock(parsedArgs);
                    break;

                case DW_TAG_TEMPLATE_TYPE_PARAMETER:
                    createDwTagTemplateTypeParameter(parsedArgs);
                    break;

                case DW_TAG_UNKNOWN:
                    parsedArgs.rewind();
                    createDwNode(parsedArgs); // TODO: we need to know the type of the node
                    break;

                default:
                    metadata.add(null);
                    LLVMLogger.info("! - TODO: #" + record + " - " + ident);
                    break;
            }
        } else {
            parsedArgs.rewind();
            metadata.add(null);
            LLVMLogger.info("! - TODO: #" + MetadataRecord.OLD_NODE + ": " + parsedArgs);
        }
    }

    protected void createOldFnNode(long[] args) {
        MetadataArgumentParser parsedArgs = new MetadataArgumentParser(types, symbols, args);

        metadata.add(new MetadataFnNode((MetadataConstantPointerType) parsedArgs.next()));
    }

    protected void createDwNode(MetadataArgumentParser args) {
        MetadataNode node = new MetadataNode();

        while (args.hasNext()) {
            Type next = args.next();
            if (next instanceof MetadataConstantType) {
                node.add(metadata.getReference(next));
            } else {
                node.add(MetadataBlock.voidRef); // TODO: implement
            }
        }

        metadata.add(node);
    }

    protected void createDwTagGlobalVariable(MetadataArgumentParser args) {
        MetadataGlobalVariable node = new MetadataGlobalVariable();

        args.next(); // Unused
        node.setContext(metadata.getReference(args.next()));
        node.setName(metadata.getReference(args.next()));
        node.setDisplayName(metadata.getReference(args.next()));
        node.setLinkageName(metadata.getReference(args.next()));
        node.setFile(metadata.getReference(args.next()));
        node.setLine(asInt32(args.next()));
        node.setType(metadata.getReference(args.next()));
        node.setLocalToCompileUnit(asInt1(args.next()));
        node.setDefinedInCompileUnit(asInt1(args.next()));
        // TODO: Reference to the global variable, can be MetadataConstant but also
        // MetadataConstantPointer
        args.next();

        metadata.add(node);
    }

    protected void createDwTagCompileUnit(MetadataArgumentParser args) {
        MetadataCompileUnit node = new MetadataCompileUnit();

        args.next(); // Unused
        node.setLanguage(DwLangNameRecord.decode(asInt32(args.next())));
        node.setFile(metadata.getReference(args.next()));
        node.setDirectory(metadata.getReference(args.next()));
        node.setProducer(metadata.getReference(args.next()));
        asInt1(args.next()); // TODO: Main Compile Unit
        node.setOptimized(asInt1(args.next()));
        node.setFlags(metadata.getReference(args.next()));
        node.setRuntimeVersion(asInt32(args.next()));
        node.setEnumType(metadata.getReference(args.next()));
        node.setRetainedTypes(metadata.getReference(args.next()));
        node.setSubprograms(metadata.getReference(args.next()));
        node.setGlobalVariables(metadata.getReference(args.next()));

        metadata.add(node);
    }

    protected void createDwTagFileType(MetadataArgumentParser args) {
        MetadataFile node = new MetadataFile();

        node.setFile(metadata.getReference(args.next()));
        node.setDirectory(metadata.getReference(args.next()));
        args.next(); // Unused

        metadata.add(node);
    }

    protected void createDwTagBasicType(MetadataArgumentParser args) {
        MetadataBasicType node = new MetadataBasicType();

        if (args.hasNext()) {
            metadata.getReference(args.next()); // Reference to context
            node.setName(metadata.getReference(args.next()));
            node.setFile(metadata.getReference(args.next()));
            node.setLine(asInt32(args.next()));
            node.setSize(asInt64(args.next()));
            node.setAlign(asInt64(args.next()));
            node.setOffset(asInt64(args.next()));
            node.setFlags(asInt32(args.next()));
            node.setEncoding(asInt32(args.next()));
        } else {
            /*
             * I don't know why, but there is the possibility for an empty DwTagBaseType
             *
             * example file which reproduces this special case:
             *
             * - test-suite-3.2.src/SingleSource/Regression/C++/2003-06-08-VirtualFunctions.cpp
             */
        }

        metadata.add(node);
    }

    protected void createDwCompositeType(MetadataArgumentParser args) {
        MetadataCompositeType node = new MetadataCompositeType();

        node.setContext(metadata.getReference(args.next()));
        node.setName(metadata.getReference(args.next()));
        node.setFile(metadata.getReference(args.next()));
        node.setLine(asInt32(args.next()));
        node.setSize(asInt64(args.next()));
        node.setAlign(asInt64(args.next()));
        node.setOffset(asInt64(args.next()));
        node.setFlags(asInt32(args.next()));
        node.setDerivedFrom(metadata.getReference(args.next()));
        if (args.hasNext()) {
            node.setMemberDescriptors(metadata.getReference(args.next()));
            node.setRuntimeLanguage(asInt32(args.next()));
        } else {
            /*
             * I don't know why, but there is the possibility for an pre ended DwTagComposizeType
             *
             * example file which reproduces this special case:
             *
             * - test-suite-3.2.src/SingleSource/Regression/C++/2003-06-08-VirtualFunctions.cpp
             */
        }

        metadata.add(node);
    }

    protected void createDwTagEnumerator(MetadataArgumentParser args) {
        MetadataEnumerator node = new MetadataEnumerator();

        node.setName(metadata.getReference(args.next()));
        node.setValue(asInt64(args.next()));

        metadata.add(node);
    }

    protected void createDwTagSubprogram(MetadataArgumentParser args) {
        MetadataSubprogram node = new MetadataSubprogram();

        args.next(); // Unused
        metadata.getReference(args.next()); // context
        node.setName(metadata.getReference(args.next()));
        node.setDisplayName(metadata.getReference(args.next()));
        node.setLinkageName(metadata.getReference(args.next()));
        node.setFile(metadata.getReference(args.next()));
        node.setLine(asInt32(args.next()));
        node.setType(metadata.getReference(args.next()));
        node.setLocalToUnit(asInt1(args.next()));
        node.setDefinedInCompileUnit(asInt1(args.next()));
        node.setVirtuallity(asInt32(args.next()));
        node.setVirtualIndex(asInt32(args.next()));
        metadata.getReference(args.next()); // which base type contains the vtable pointer...
        node.setFlags(asInt32(args.next()));
        node.setOptimized(asInt1(args.next()));
        args.next(); // Function *,;; Pointer to LLVM function
        node.setTemplateParams(metadata.getReference(args.next()));
        node.setDeclaration(metadata.getReference(args.next()));
        node.setVariables(metadata.getReference(args.next()));
        node.setScopeLine(asInt32(args.next())); // TODO: correct?

        metadata.add(node);
    }

    private static final long DW_TAG_LOCAL_VARIABLE_LINE_PART = 0x00FFFFFF;
    private static final long DW_TAG_LOCAL_VARIABLE_ARG_PART = 0xFF000000;
    private static final long DW_TAG_LOCAL_VARIABLE_ARG_SHIFT = 24;

    protected void createDwTagLocalVariable(MetadataArgumentParser args) {
        MetadataLocalVariable node = new MetadataLocalVariable();

        node.setContext(metadata.getReference(args.next()));
        node.setName(metadata.getReference(args.next()));
        node.setFile(metadata.getReference(args.next()));
        long lineAndArg = asInt32(args.next());
        long line = lineAndArg & DW_TAG_LOCAL_VARIABLE_LINE_PART;
        long arg = (lineAndArg & DW_TAG_LOCAL_VARIABLE_ARG_PART) >> DW_TAG_LOCAL_VARIABLE_ARG_SHIFT;
        node.setLine(line);
        node.setArg(arg);
        node.setType(metadata.getReference(args.next()));
        node.setFlags(asInt32(args.next()));
        // metadata ;; (optional) Reference to inline location

        metadata.add(node);
    }

    protected void createDwTagSubrangeType(MetadataArgumentParser args) {
        MetadataSubrange node = new MetadataSubrange();

        // TODO: if lowBound > highBound --> bound not included
        long lowBound = asInt64(args.next());
        long highBound = asInt64(args.next());

        node.setLowBound(lowBound);
        node.setSize(highBound - lowBound + 1);

        metadata.add(node);
    }

    private static final int DW_LEXICAL_BLOCK_FILE_LENGTH = 2;
    private static final int DW_LEXICAL_BLOCK_LENGTH = 5;

    protected void createDwTagLexicalBlock(MetadataArgumentParser args) {
        switch (args.remaining()) {
            case DW_LEXICAL_BLOCK_FILE_LENGTH: {
                MetadataLexicalBlockFile node = new MetadataLexicalBlockFile();

                metadata.getReference(args.next()); // Reference to the scope we're annotating...
                node.setFile(metadata.getReference(args.next()));

                metadata.add(node);
                break;
            }

            case DW_LEXICAL_BLOCK_LENGTH: {
                MetadataLexicalBlock node = new MetadataLexicalBlock();

                metadata.getReference(args.next()); // Reference to context descriptor
                node.setLine(asInt32(args.next()));
                node.setColumn(asInt32(args.next()));
                node.setFile(metadata.getReference(args.next()));
                asInt32(args.next()); // Unique ID to identify blocks from a template function

                metadata.add(node);
                break;
            }

            default:
                throw new RuntimeException("Unknow Lexical Block");
        }
    }

    protected void createDwDerivedType(MetadataArgumentParser args) {
        MetadataDerivedType node = new MetadataDerivedType();

        metadata.getReference(args.next()); // Context
        node.setName(metadata.getReference(args.next()));
        node.setFile(metadata.getReference(args.next()));
        node.setLine(asInt32(args.next()));
        node.setSize(asInt64(args.next()));
        node.setAlign(asInt64(args.next()));
        node.setOffset(asInt64(args.next()));
        node.setFlags(asInt32(args.next()));
        node.setBaseType(metadata.getReference(args.next()));
        // metadata, ;; (optional) Name of the Objective C property getter selector.
        // metadata, ;; (optional) Name of the Objective C property setter selector.
        // i32 ;; (optional) Objective C property attributes.

        metadata.add(node);
    }

    private void createDwTagTemplateTypeParameter(MetadataArgumentParser args) {
        MetadataTemplateTypeParameter node = new MetadataTemplateTypeParameter();

        metadata.getReference(args.next()); // Context?
        node.setName(metadata.getReference(args.next()));
        node.setBaseType(metadata.getReference(args.next()));
        args.next(); // TODO: Unknown
        args.next(); // TODO: Unknown

        metadata.add(node);
    }
}
