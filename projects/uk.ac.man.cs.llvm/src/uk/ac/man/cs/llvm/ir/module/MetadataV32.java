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
import uk.ac.man.cs.llvm.ir.types.Type;

public class MetadataV32 extends Metadata {
    public MetadataV32(List<Type> symbols) {
        super(symbols);
        idx = 0; // it seem's like there is a different offset of the id in LLVM 3.2 and LLVM 3.8
    }

    @Override
    public void record(long id, long[] args) {
        MetadataRecord record = MetadataRecord.decode(id);
        // changes can be found here:
        // https://github.com/llvm-mirror/llvm/commit/dad20b2ae2544708d6a33abdb9bddd0a329f50e0
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
        if (args[0] == 0) {
            long val;
            try {
                val = getIntegerConstant(args[1]);
            } catch (Exception e) {
                System.out.println("!" + idx + " - failed " + MetadataRecord.OLD_NODE + ": " + Arrays.toString(args));
                return;
            }

            switch ((int) val) {
                case 786484: // DW_TAG_variable
                    createDwTagVariable(args);
                    break;
                case 786449: // DW_TAG_compile_unit
                    createDwTagCompileUnit(args);
                    break;
                case 786473: // DW_TAG_file_type
                    createDwTagFileType(args);
                    break;
                case 786468: // DW_TAG_base_type
                    createDwTagBaseType(args);
                    break;
                case 786436: // DW_TAG_enumeration_type
                    createDwTagEnumerationType(args);
                    break;
                case 786472: // DW_TAG_enumerator
                    createDwTagEnumerator(args);
                    break;
                case 786478: // DW_TAG_subprogram
                    createDwTagSubprogram(args);
                    break;
                case 786453: // DW_TAG_subroutine_type
                    createDwTagSubroutineType(args);
                    break;
                case 786688: // DW_TAG_auto_variable
                    createDwTagAutoVariable(args);
                    break;
                case 786433: // DW_TAG_array_type
                    createDwTagArrayType(args);
                    break;
                case 786465: // DW_TAG_subrange_type
                    createDwTagSubrangeType(args);
                    break;
                case 786443: // DW_TAG_lexical_block
                    createDwTagLexicalBlock(args);
                    break;
                case 786445: // DW_TAG_member
                    createDwTagMember(args);
                    break;
                case 786451: // DW_TAG_structure_type
                    createDwTagStructureType(args);
                    break;
                default:
                    System.out.println("!" + idx + " - " + MetadataRecord.OLD_NODE + " - UNSUPORTED #" + val + ": " + Arrays.toString(args));
                    break;
            }
        } else if (args[0] == 6) {
            createDwNode(args); // no idea how the real name is, it's normaly a list of metadata
                                // seperated with 6
        } else {
            System.out.println("!" + idx + " - " + MetadataRecord.OLD_NODE + ": " + Arrays.toString(args));
        }
    }

    protected void createOldFnNode(long[] args) {
        System.out.println("!" + idx + " - " + MetadataRecord.OLD_FN_NODE + ": " + Arrays.toString(args));
    }

    protected void createDwNode(long[] args) {
        assert (args.length % 2 == 0);

        long[] parsedArgs = new long[args.length / 2];

        for (int i = 0; i < parsedArgs.length; i++) {
            assert (args[i * 2] == 6);
            parsedArgs[i] = args[i * 2 + 1];
        }

        System.out.println("!" + idx + " - DW_Node: " + Arrays.toString(parsedArgs));
    }

    protected void createDwTagVariable(long[] args) {
        System.out.println("!" + idx + " - DW_TAG_variable: " + Arrays.toString(args));
    }

    protected void createDwTagCompileUnit(long[] args) {
        System.out.println("!" + idx + " - DW_TAG_compile_unit: " + Arrays.toString(args));
    }

    protected void createDwTagFileType(long[] args) {
        long filename = args[3];
        long directory = args[5];
        // TODO: args[7];

        System.out.println("!" + idx + " - DW_TAG_file_type: " + Arrays.toString(args));
    }

    protected void createDwTagBaseType(long[] args) {
        System.out.println("!" + idx + " - DW_TAG_base_type: " + Arrays.toString(args));
    }

    protected void createDwTagEnumerationType(long[] args) {
        System.out.println("!" + idx + " - DW_TAG_enumeration_type: " + Arrays.toString(args));
    }

    protected void createDwTagEnumerator(long[] args) {
        long name = args[3];
        long value = getIntegerConstant(args[5]);

        System.out.println("!" + idx + " - DW_TAG_enumerator: " + Arrays.toString(args));
    }

    protected void createDwTagSubprogram(long[] args) {
        System.out.println("!" + idx + " - DW_TAG_subprogram: " + Arrays.toString(args));
    }

    protected void createDwTagSubroutineType(long[] args) {
        System.out.println("!" + idx + " - DW_TAG_subroutine_type: " + Arrays.toString(args));
    }

    protected void createDwTagAutoVariable(long[] args) {
        System.out.println("!" + idx + " - DW_TAG_auto_variable: " + Arrays.toString(args));
    }

    protected void createDwTagArrayType(long[] args) {
        System.out.println("!" + idx + " - DW_TAG_array_type: " + Arrays.toString(args));
    }

    protected void createDwTagSubrangeType(long[] args) {
        System.out.println("!" + idx + " - DW_TAG_subrange_type: " + Arrays.toString(args));
    }

    protected void createDwTagLexicalBlock(long[] args) {
        System.out.println("!" + idx + " - DW_TAG_lexical_block: " + Arrays.toString(args));
    }

    protected void createDwTagMember(long[] args) {
        System.out.println("!" + idx + " - DW_TAG_member: " + Arrays.toString(args));
    }

    protected void createDwTagStructureType(long[] args) {
        System.out.println("!" + idx + " - DW_TAG_structure_type: " + Arrays.toString(args));
    }

}
