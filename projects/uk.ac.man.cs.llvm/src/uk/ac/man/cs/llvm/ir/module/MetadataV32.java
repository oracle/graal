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
import uk.ac.man.cs.llvm.ir.model.metadata.CompileUnit;
import uk.ac.man.cs.llvm.ir.model.metadata.CompositeType;
import uk.ac.man.cs.llvm.ir.model.metadata.Enumerator;
import uk.ac.man.cs.llvm.ir.model.metadata.File;
import uk.ac.man.cs.llvm.ir.model.metadata.Node;
import uk.ac.man.cs.llvm.ir.model.metadata.Subprogram;
import uk.ac.man.cs.llvm.ir.module.records.DwTagRecord;
import uk.ac.man.cs.llvm.ir.types.IntegerConstantType;
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

        idx++;
    }

    protected void createOldNode(long[] args) {
        MetadataArgumentParser parsedArgs = new MetadataArgumentParser(types, symbols, args);

        if (parsedArgs.peek() instanceof IntegerConstantType) {
            // System.out.println(parsedArgs);

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

            // TODO: some hack, has to be changed when we can identify type informations clearly
            if (ident < 0x00010000) {
                record = DwTagRecord.DW_TAG_UNKNOW;
            }

            /*
             * How the data is stored: http://llvm.org/releases/3.2/docs/SourceLevelDebugging.html
             */
            switch (record) {
                case DW_TAG_VARIABLE:
                    createDwTagVariable(parsedArgs);
                    break;

                case DW_TAG_COMPILE_UNIT:
                    createDwTagCompileUnit(parsedArgs);
                    break;

                case DW_TAG_FILE_TYPE:
                    createDwTagFileType(parsedArgs);
                    break;

                case DW_TAG_BASE_TYPE:
                    createDwTagBaseType(parsedArgs);
                    break;

                case DW_TAG_ARRAY_TYPE:
                case DW_TAG_ENUMERATION_TYPE:
                case DW_TAG_STRUCTURE_TYPE:
                case DW_TAG_UNION_TYPE:
                case DW_TAG_VECTOR_TYPE:
                case DW_TAG_SUBROUTINE_TYPE:
                case DW_TAG_INHERITANCE:
                    createDwCompositeType(parsedArgs, record);
                    break;

                case DW_TAG_ENUMERATOR:
                    createDwTagEnumerator(parsedArgs);
                    break;

                case DW_TAG_SUBPROGRAM:
                    createDwTagSubprogram(parsedArgs);
                    break;

                case DW_TAG_AUTO_VARIABLE:
                    createDwTagAutoVariable(parsedArgs);
                    break;

                case DW_TAG_SUBRANGE_TYPE:
                    createDwTagSubrangeType(parsedArgs);
                    break;

                case DW_TAG_LEXICAL_BLOCK:
                    createDwTagLexicalBlock(parsedArgs);
                    break;

                case DW_TAG_MEMBER:
                    createDwTagMember(parsedArgs);
                    break;

                default:
                    System.out.println("!" + idx + " - TODO: #" + record);
                    break;

                case DW_TAG_UNKNOW:
                    parsedArgs.rewind();
                    // System.out.println("!" + idx + " - " + MetadataRecord.OLD_NODE + " -
                    // UNSUPORTED: #" + asInt32(parsedArgs.next()));
                    createDwNode(parsedArgs); // TODO: we need to know the type of the node
                    break;
            }
        } else if (args[0] == 6) {
            parsedArgs.rewind();
            createDwNode(parsedArgs); // no idea how the real name is, it's normaly a list of
                                      // metadata
            // seperated with 6
        } else {
            parsedArgs.rewind();
            System.out.println("!" + idx + " - " + MetadataRecord.OLD_NODE + ": " + parsedArgs);
        }
    }

    protected void createOldFnNode(long[] args) {
        System.out.println("!" + idx + " - " + MetadataRecord.OLD_FN_NODE + ": " + Arrays.toString(args));
    }

    protected void createDwNode(MetadataArgumentParser args) {
        Node node = new Node();

        while (args.hasNext()) {
            node.add(metadata.getReference(args.next()));
        }

        metadata.add(node);

        System.out.println("!" + idx + " - " + node);
    }

    protected void createDwTagVariable(MetadataArgumentParser args) {
        // TODO

        System.out.println("!" + idx + " - DW_TAG_variable");
    }

    protected void createDwTagCompileUnit(MetadataArgumentParser args) {
        CompileUnit node = new CompileUnit();

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

        System.out.println("!" + idx + " - " + node);
    }

    protected void createDwTagFileType(MetadataArgumentParser args) {
        File node = new File();

        node.setFile(metadata.getReference(args.next()));
        node.setDirectory(metadata.getReference(args.next()));
        args.next(); // Unused

        metadata.add(node);

        System.out.println("!" + idx + " - " + node);
    }

    protected void createDwTagBaseType(MetadataArgumentParser args) {
        // TODO: implement
        args.next(); // Unused
        args.next(); // Unused
        long name = asMetadata(args.next());
        long line = asInt32(args.next());
        long size = asInt64(args.next());
        long align = asInt64(args.next());
        long offset = asInt64(args.next());
        long flags = asInt32(args.next());
        long encoding = asInt32(args.next());

        System.out.println("!" + idx + " - DW_TAG_base_type");
    }

    protected void createDwCompositeType(MetadataArgumentParser args, DwTagRecord record) {
        CompositeType node = new CompositeType();

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

        System.out.println("!" + idx + " - " + node + " - (" + record + ")");
    }

    protected void createDwTagEnumerator(MetadataArgumentParser args) {
        Enumerator node = new Enumerator();

        node.setName(metadata.getReference(args.next()));
        node.setValue(asInt64(args.next()));

        metadata.add(node);

        System.out.println("!" + idx + " - " + node);
    }

    protected void createDwTagSubprogram(MetadataArgumentParser args) {
        Subprogram node = new Subprogram();

        // TODO: somehow reverse engineered, has to be checked
        args.next();
        args.next(); // scope?
        node.setName(metadata.getReference(args.next()));
        node.setLinkageName(metadata.getReference(args.next()));
        args.next();
        node.setFile(metadata.getReference(args.next()));
        node.setLine(asInt32(args.next()));
        args.next(); // DW_TAG_subroutine_type
        node.setLocalToUnit(asInt1(args.next()));
        node.setDefinition(asInt1(args.next()));
        args.next();
        args.next();
        args.next();
        args.next();
        node.setOptimized(asInt1(args.next()));
        args.next();
        args.next();
        args.next();
        args.next();
        node.setScopeLine(asInt32(args.next()));

        metadata.add(node);

        System.out.println("!" + idx + " - " + node);
    }

    protected void createDwTagAutoVariable(MetadataArgumentParser args) {
        // TODO

        System.out.println("!" + idx + " - DW_TAG_auto_variable");
    }

    protected void createDwTagSubrangeType(MetadataArgumentParser args) {
        // TODO

        System.out.println("!" + idx + " - DW_TAG_subrange_type");
    }

    protected void createDwTagLexicalBlock(MetadataArgumentParser args) {
        // TODO

        System.out.println("!" + idx + " - DW_TAG_lexical_block");
    }

    protected void createDwTagMember(MetadataArgumentParser args) {
        // TODO

        System.out.println("!" + idx + " - DW_TAG_member");
    }

}
