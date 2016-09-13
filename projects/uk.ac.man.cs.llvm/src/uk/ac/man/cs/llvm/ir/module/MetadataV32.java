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
import uk.ac.man.cs.llvm.ir.module.records.DwTagRecord;
import uk.ac.man.cs.llvm.ir.types.IntegerConstantType;
import uk.ac.man.cs.llvm.ir.types.Type;

public class MetadataV32 extends Metadata {
    public MetadataV32(Types types, List<Type> symbols) {
        super(types, symbols);
        idx = 0; // it seem's like there is a different offset of the id in LLVM 3.2 and LLVM 3.8
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

    protected long asMetadata(Type t) {
        return ((IntegerConstantType) t).getValue(); // TODO
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
            DwTagRecord record = DwTagRecord.decode(asInt32(parsedArgs.next()));
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
                case DW_TAG_ENUMERATION_TYPE:
                    createDwTagEnumerationType(parsedArgs);
                    break;
                case DW_TAG_ENUMERATOR:
                    createDwTagEnumerator(parsedArgs);
                    break;
                case DW_TAG_SUBPROGRAM:
                    createDwTagSubprogram(parsedArgs);
                    break;
                case DW_TAG_SUBROUTINE_TYPE:
                    createDwTagSubroutineType(parsedArgs);
                    break;
                case DW_TAG_AUTO_VARIABLE:
                    createDwTagAutoVariable(parsedArgs);
                    break;
                case DW_TAG_ARRAY_TYPE:
                    createDwTagArrayType(parsedArgs);
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
                case DW_TAG_STRUCTURE_TYPE:
                    createDwTagStructureType(parsedArgs);
                    break;
                default:
                    parsedArgs.rewind();
                    System.out.println("!" + idx + " - " + MetadataRecord.OLD_NODE + " - UNSUPORTED: #" + asInt32(parsedArgs.next()));
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
        // TODO

        System.out.println("!" + idx + " - DW_Node: ");
    }

    protected void createDwTagVariable(MetadataArgumentParser args) {
        // TODO

        System.out.println("!" + idx + " - DW_TAG_variable");
    }

    protected void createDwTagCompileUnit(MetadataArgumentParser args) {
        /*
         * @formatter:off
         *
         *  metadata !{
         *    i32 786449,                       ;; Tag
         *    i32 0,                            ;; Context
         *    i32 4,                            ;; Language
         *    metadata !"foo.cpp",              ;; File
         *    metadata !"/Volumes/Data/tmp",    ;; Directory
         *    metadata !"clang version 3.1 ",   ;; Producer
         *    i1 true,                          ;; Deprecated field
         *    i1 false,                         ;; "isOptimized"?
         *    metadata !"",                     ;; Flags
         *    i32 0,                            ;; Runtime Version
         *    metadata !1,                      ;; Enum Types
         *    metadata !1,                      ;; Retained Types
         *    metadata !1,                      ;; Subprograms
         *    metadata !3                       ;; Global Variables
         *  } ; [ DW_TAG_compile_unit ]
         *
         * @formatter:on
         */
        long context = asInt32(args.next());
        long language = asInt32(args.next());
        long file = asMetadata(args.next());
        long directory = asMetadata(args.next());
        long producer = asMetadata(args.next());
        boolean isDeprecatedField = asInt1(args.next());
        boolean isOptimized = asInt1(args.next());
        long flags = asMetadata(args.next());
        long runtimeVersion = asInt32(args.next());
        long enumType = asMetadata(args.next());
        long retainedTypes = asMetadata(args.next());
        long subprograms = asMetadata(args.next());
        long globalVariables = asMetadata(args.next());

        System.out.println("!" + idx + " - DW_TAG_compile_unit");
    }

    protected void createDwTagFileType(MetadataArgumentParser args) {
        long file = asMetadata(args.next());
        long directory = asMetadata(args.next());
        args.next(); // TODO

        System.out.println("!" + idx + " - DW_TAG_file_type");
    }

    protected void createDwTagBaseType(MetadataArgumentParser args) {
        /*
         * @formatter:off
         *
         * metadata !{
         *   i32 786468,        ;; Tag
         *   null,              ;; Unused
         *   null,              ;; Unused
         *   metadata !"int",   ;; Name
         *   i32 0,             ;; Line
         *   i64 32,            ;; Size in Bits
         *   i64 32,            ;; Align in Bits
         *   i64 0,             ;; Offset
         *   i32 0,             ;; Flags
         *   i32 5              ;; Encoding
         * } ; [ DW_TAG_base_type ]
         *
         * @formatter:on
         */

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

    protected void createDwTagEnumerationType(MetadataArgumentParser args) {
        // TODO

        System.out.println("!" + idx + " - DW_TAG_enumeration_type");
    }

    protected void createDwTagEnumerator(MetadataArgumentParser args) {
        // TODO long name = args[3];
        // long value = getIntegerConstant(args[5]);

        System.out.println("!" + idx + " - DW_TAG_enumerator");
    }

    protected void createDwTagSubprogram(MetadataArgumentParser args) {
        // TODO

        System.out.println("!" + idx + " - DW_TAG_subprogram");
    }

    protected void createDwTagSubroutineType(MetadataArgumentParser args) {
        // TODO

        System.out.println("!" + idx + " - DW_TAG_subroutine_type");
    }

    protected void createDwTagAutoVariable(MetadataArgumentParser args) {
        // TODO

        System.out.println("!" + idx + " - DW_TAG_auto_variable");
    }

    protected void createDwTagArrayType(MetadataArgumentParser args) {
        // TODO

        System.out.println("!" + idx + " - DW_TAG_array_type");
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

    protected void createDwTagStructureType(MetadataArgumentParser args) {
        // TODO

        System.out.println("!" + idx + " - DW_TAG_structure_type");
    }

}
