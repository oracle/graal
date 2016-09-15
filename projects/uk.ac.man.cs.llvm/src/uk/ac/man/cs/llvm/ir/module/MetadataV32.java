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
package uk.ac.man.cs.llvm.ir.module;

import java.util.Arrays;
import java.util.List;

import uk.ac.man.cs.llvm.ir.module.records.MetadataRecord;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataBasicType;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataCompileUnit;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataCompositeType;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataDerivedType;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataEnumerator;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataFile;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataGlobalVariable;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataLexicalBlock;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataLexicalBlockFile;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataLocalVariable;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataNode;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataSubprogram;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataSubrange;
import uk.ac.man.cs.llvm.ir.module.records.DwTagRecord;
import uk.ac.man.cs.llvm.ir.types.IntegerConstantType;
import uk.ac.man.cs.llvm.ir.types.MetadataConstantType;
import uk.ac.man.cs.llvm.ir.types.Type;

public class MetadataV32 extends Metadata {
    public MetadataV32(Types types, List<Type> symbols) {
        super(types, symbols);
        // it seem's like there is a different offset of the id in LLVM 3.2 and LLVM 3.8
        metadata.setStartIndex(0);
        idx = 0; // TODO: remove
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

    protected int asMetadata(Type t) {
        return (int) ((IntegerConstantType) t).getValue(); // TODO
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

        // printMetadataDebugMsg(); // Enable to allow debugging of the metadata parser

        idx++;
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
                case DW_TAG_GLOBAL_VARIABLE:
                    createDwTagGlobalVariable(parsedArgs);
                    break;

                case DW_TAG_COMPILE_UNIT:
                    createDwTagCompileUnit(parsedArgs);
                    break;

                case DW_TAG_FILE_TYPE:
                    createDwTagFileType(parsedArgs);
                    break;

                case DW_TAG_BASIC_TYPE:
                    createDwTagBasicType(parsedArgs);
                    break;

                case DW_TAG_ARRAY_TYPE:
                case DW_TAG_ENUMERATION_TYPE:
                case DW_TAG_STRUCTURE_TYPE:
                case DW_TAG_UNION_TYPE:
                case DW_TAG_VECTOR_TYPE:
                case DW_TAG_SUBROUTINE_TYPE:
                case DW_TAG_INHERITANCE:
                    createDwCompositeType(parsedArgs);
                    break;

                case DW_TAG_FORMAL_PARAMETER:
                case DW_TAG_MEMBER:
                case DW_TAG_POINTER_TYPE:
                case DW_TAG_REFERENCE_TYPE:
                case DW_TAG_TYPEDEF:
                case DW_TAG_CONST_TYPE:
                case DW_TAG_VOLATILE_TYPE:
                case DW_TAG_RESTRICTED_TYPE:
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

                case DW_TAG_UNKNOWN:
                    parsedArgs.rewind();
                    createDwNode(parsedArgs); // TODO: we need to know the type of the node
                    break;

                default:
                    System.out.println("!" + idx + " - TODO: #" + record);
                    break;
            }
        } else {
            parsedArgs.rewind();
            System.out.println("!" + idx + " - " + MetadataRecord.OLD_NODE + ": " + parsedArgs);
        }
    }

    protected void createOldFnNode(long[] args) {
        // TODO: implement
    }

    protected void createDwNode(MetadataArgumentParser args) {
        MetadataNode node = new MetadataNode();

        while (args.hasNext()) {
            node.add(metadata.getReference(args.next()));
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
        node.setLine(asInt32(args.next()));
        node.setType(metadata.getReference(args.next()));
        node.setLocalToCompileUnit(asInt1(args.next()));
        node.setDefinedInCompileUnit(asInt1(args.next()));
        args.next(); // TODO: Reference to the global variable

        metadata.add(node);
    }

    protected void createDwTagCompileUnit(MetadataArgumentParser args) {
        MetadataCompileUnit node = new MetadataCompileUnit();

        args.next(); // Unused
        node.setLanguage(asInt32(args.next()));
        node.setFile(metadata.getReference(args.next()));
        node.setDirectory(metadata.getReference(args.next()));
        node.setProducer(metadata.getReference(args.next()));
        args.next(); // TODO: Main Compile Unit
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

        args.next(); // Reference to context
        node.setName(metadata.getReference(args.next()));
        node.setFile(metadata.getReference(args.next()));
        node.setLine(asInt32(args.next()));
        node.setSize(asInt64(args.next()));
        node.setAlign(asInt64(args.next()));
        node.setOffset(asInt64(args.next()));
        node.setFlags(asInt32(args.next()));
        node.setEncoding(asInt32(args.next()));

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
        node.setMemberDescriptors(metadata.getReference(args.next()));
        node.setRuntimeLanguage(asInt32(args.next()));

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
        args.next(); // context
        node.setName(metadata.getReference(args.next()));
        node.setDisplayName(metadata.getReference(args.next()));
        node.setLinkageName(metadata.getReference(args.next()));
        node.setFile(metadata.getReference(args.next()));
        node.setLine(asInt32(args.next()));
        node.setType(metadata.getReference(args.next()));
        node.setLocalToUnit(asInt1(args.next()));
        node.setDefinedInCompileUnit(asInt1(args.next()));
        node.setScopeLine(asInt32(args.next()));
        node.setVirtuallity(asInt32(args.next()));
        args.next(); // node.setVirtualIndex(asInt32(args.next())); // TODO: MetadataConstantType
        args.next(); // metadata, ;; indicates which base type contains the vtable pointer...
        node.setFlags(metadata.getReference(args.next()));
        node.setOptimized(asInt1(args.next()));
        args.next(); // Function *,;; Pointer to LLVM function
        node.setTemplateParams(metadata.getReference(args.next()));
        node.setDeclaration(metadata.getReference(args.next()));
        args.next(); // node.setVariables(metadata.getReference(args.next())); // TODO: Invalid
                     // reference type

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

                args.next(); // metadata ;; Reference to the scope we're annotating...
                node.setFile(metadata.getReference(args.next()));

                metadata.add(node);
                break;
            }

            case DW_LEXICAL_BLOCK_LENGTH: {
                MetadataLexicalBlock node = new MetadataLexicalBlock();

                args.next(); // metadata,;; Reference to context descriptor
                node.setLine(asInt32(args.next()));
                node.setColumn(asInt32(args.next()));
                node.setFile(metadata.getReference(args.next()));
                args.next(); // i32 ;; Unique ID to identify blocks from a template function

                metadata.add(node);
                break;
            }

            default:
                throw new RuntimeException("Unknow Lexical Block");
        }
    }

    protected void createDwDerivedType(MetadataArgumentParser args) {
        MetadataDerivedType node = new MetadataDerivedType();

        args.next(); // Context
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

}
