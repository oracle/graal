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

import uk.ac.man.cs.llvm.bc.ParserListener;
import uk.ac.man.cs.llvm.ir.InstructionGenerator;
import uk.ac.man.cs.llvm.ir.module.records.MetadataRecord;
import uk.ac.man.cs.llvm.ir.types.IntegerConstantType;
import uk.ac.man.cs.llvm.ir.types.Type;

public class Metadata implements ParserListener {

    protected int idx = 1;

    protected final Types types;

    protected final List<Type> symbols;

    // protected MetadataGenerator metadata;

    public Metadata(Types types, List<Type> symbols) {
        this.types = types;
        this.symbols = symbols;
        int i = 0;
        System.out.println("Symbols");
        for (Type s : symbols) {
            System.out.println("!" + i + " - " + s);
            i++;
        }
    }

    /*
     * public static ParserListener getAttachments() { return ParserListener.DEFAULT; }
     *
     * public static ParserListener getKinds() { return ParserListener.DEFAULT; }
     */

    // https://github.com/llvm-mirror/llvm/blob/release_38/include/llvm/Bitcode/LLVMBitCodes.h#L191
    @Override
    public void record(long id, long[] args) {
        MetadataRecord record = MetadataRecord.decode(id);
        switch (record) {
            case STRING:
                createString(args);
                break;

            case VALUE:
                createValue(args);
                break;

            case NODE:
                createNode(args);
                break;

            case NAME:
                createName(args);
                break;

            case DISTINCT_NODE:
                createDistinctNode(args);
                break;

            case KIND:
                createKind(args);
                break;

            case LOCATION:
                createLocation(args);
                break;

            // OLD_NODE
            // OLD_FN_NODE

            case NAMED_NODE:
                createNamedNode(args);
                break;

            case ATTACHMENT:
                createAttachment(args);
                break;

            case GENERIC_DEBUG:
                createGenericDebug(args);
                break;

            case SUBRANGE:
                createSubrange(args);
                break;

            case ENUMERATOR:
                createEnumerator(args);
                break;

            case BASIC_TYPE:
                createBasicType(args);
                break;

            case FILE:
                createFile(args);
                break;

            case SUBPROGRAM:
                createSubprogram(args);
                break;

            case SUBROUTINE_TYPE:
                createSubroutineType(args);
                break;

            case LOCAL_VAR:
                createLocalVar(args);
                break;

            case GLOBAL_VAR:
                createGlobalVar(args);
                break;

            case DERIVED_TYPE:
                createDerivedType(args);
                break;

            case COMPOSITE_TYPE:
                createCompositeType(args);
                break;

            default:
                System.out.println("!" + idx + " - " + record + ": " + Arrays.toString(args));
                break;
        }
        idx++;
    }

    protected void createString(long[] args) {
        String s = "";
        for (long lc : args) {
            s += (char) (lc); // TODO: unicode characters?
        }
        System.out.println("!" + idx + " - " + MetadataRecord.STRING + ": \"" + s + "\"");
    }

    protected void createValue(long[] args) {
        long typeNum = args[0];
        long valueNum = args[1];

        System.out.println("!" + idx + " - " + MetadataRecord.VALUE + " - " + Arrays.toString(args));
    }

    protected void createNode(long[] args) {
        // [n x md num]

        System.out.println("!" + idx + " - " + MetadataRecord.NODE + " - " + Arrays.toString(args));
    }

    protected void createName(long[] args) {
        String s = "";
        for (long lc : args) {
            s += (char) (lc); // TODO: unicode characters?
        }

        System.out.println("!" + idx + " - " + MetadataRecord.NAME + ": \"" + s + "\"");
    }

    protected void createDistinctNode(long[] args) {
        // [n x md num]

        System.out.println("!" + idx + " - " + MetadataRecord.DISTINCT_NODE + " - " + Arrays.toString(args));
    }

    protected void createKind(long[] args) {
        long id = args[0];
        String name = "";
        for (int i = 1; i < args.length; i++) {
            name += (char) (args[i]); // TODO: unicode characters?
        }
        System.out.println("!" + idx + " - " + MetadataRecord.KIND + " id=" + id + ", name=\"" + name + "\"");
    }

    protected void createLocation(long[] args) {
        long distinct = args[0];
        long line = args[1];
        long column = args[2];
        long scope = args[3];
        long inlineAt = args[4];

        System.out.println("!" + idx + " - " + MetadataRecord.LOCATION + " - " + Arrays.toString(args));
    }

    protected void createNamedNode(long[] args) {
        // [n x mdnodes]

        System.out.println("!" + idx + " - " + MetadataRecord.NAMED_NODE + " - " + Arrays.toString(args));
    }

    protected void createAttachment(long[] args) {
        // [n x mdnodes]

        System.out.println("!" + idx + " - " + MetadataRecord.ATTACHMENT + " - " + Arrays.toString(args));
    }

    protected void createGenericDebug(long[] args) {
        long distinct = args[0];
        long tag = args[1];
        long vers = args[2];
        long header = args[3];
        // TODO: args[4] // n x md num

        System.out.println("!" + idx + " - " + MetadataRecord.GENERIC_DEBUG + " - " + Arrays.toString(args));
    }

    protected void createSubrange(long[] args) {
        long distinct = args[0];
        long count = args[1];
        long lo = args[0]; // https://github.com/llvm-mirror/llvm/blob/release_38/include/llvm/Bitcode/LLVMBitCodes.h#L204

        System.out.println("!" + idx + " - " + MetadataRecord.SUBRANGE + ": count=" + count + " - " + Arrays.toString(args));
    }

    protected void createEnumerator(long[] args) {
        long distinct = args[0];
        long value = args[1];
        long name = args[2];

        System.out.println("!" + idx + " - " + MetadataRecord.ENUMERATOR + ": value=" + value + ": name=!" + name + " - " + Arrays.toString(args));
    }

    protected void createBasicType(long[] args) {
        // https://github.com/llvm-mirror/llvm/blob/release_38/lib/Bitcode/Writer/BitcodeWriter.cpp#L916
        long distinct = args[0];
        long tag = args[0];
        long name = args[2];
        long size = args[3];
        long align = args[4];
        long encoding = args[5]; // DW_ATE_signed=5, DW_ATE_signed_char=6

        System.out.println("!" + idx + " - " + MetadataRecord.BASIC_TYPE + ": name=!" + name + ": size=" + size + ": align=" + align + ": encoding=" + encoding + " - " + Arrays.toString(args));
    }

    protected void createFile(long[] args) {
        long distinct = args[0];
        long filename = args[1];
        long directory = args[2];

        System.out.println("!" + idx + " - " + MetadataRecord.FILE + ": filename=!" + filename + ", directory=!" + directory + " - " + Arrays.toString(args));
    }

    protected void createSubprogram(long[] args) {
        // https://github.com/llvm-mirror/llvm/blob/release_38/lib/Bitcode/Writer/BitcodeWriter.cpp#L1030
        long distinct = args[0];
        long scope = args[1];
        long rawName = args[2];
        long rawLinkageName = args[3];
        long file = args[2];
        long line = args[5];
        long type = args[6];
        long isLocalToUnit = args[7];
        long isDefinition = args[8];
        long scopeLine = args[9];
        long containingType = args[10];
        long virtuallity = args[11];
        long virtualIndex = args[12];
        long flags = args[13];
        long isOptimized = args[14];
        long templateParams = args[15];
        long declaration = args[16];
        long variables = args[17];

        System.out.println("!" + idx + " - " + MetadataRecord.SUBPROGRAM + ": name=!" + rawName + ": line=" + line + ": type=!" + type + ": scopeLine=" + scopeLine + " - " + Arrays.toString(args));
    }

    protected void createSubroutineType(long[] args) {
        long distinct = args[0];
        long flags = args[1];
        long types = args[2];

        System.out.println("!" + idx + " - " + MetadataRecord.SUBROUTINE_TYPE + ": types=!" + types + " - " + Arrays.toString(args));
    }

    protected void createLocalVar(long[] args) {
        // https://github.com/llvm-mirror/llvm/blob/release_38/lib/Bitcode/Writer/BitcodeWriter.cpp#L1187
        long distinct = args[0];
        long scope = args[1];
        long name = args[2];
        long file = args[3];
        long line = args[4];
        long type = args[5];
        long arg = args[6];
        long flags = args[7];

        System.out.println("!" + idx + " - " + MetadataRecord.LOCAL_VAR + ": name=!" + name + ", scope=!" + scope + ", line=" + line + ", file=!" + file + ", type=!" + type + " - " +
                        Arrays.toString(args));
    }

    protected void createGlobalVar(long[] args) {
// https://github.com/llvm-mirror/llvm/blob/release_38/lib/Bitcode/Writer/BitcodeWriter.cpp#L1166
        long distinct = args[0];
        long scope = args[1];
        long name = args[2];
        long LinkageName = args[2];
        long file = args[4];
        long line = args[5];
        long type = args[6];
        long localToUnit = args[7];
        long isDefinition = args[8];
        long rawVariable = args[9];
        long staticDataMemberDeclaration = args[10];

        System.out.println("!" + idx + " - " + MetadataRecord.GLOBAL_VAR + ": name=!" + name + ", scope=!" + scope + ", line=" + line + ", file=!" + file + ", type=!" + type + " - " +
                        Arrays.toString(args));
    }

    protected void createDerivedType(long[] args) {
        // https://github.com/llvm-mirror/llvm/blob/release_38/lib/Bitcode/Writer/BitcodeWriter.cpp#L931
        long distinct = args[0];
        long tag = args[1];
        long name = args[2];
        long file = args[3];
        long line = args[4];
        long scope = args[5];
        long baseType = args[6];
        long size = args[7];
        long align = args[8];
        long offset = args[9];
        long flags = args[10];
        long extraData = args[11];

        System.out.println("!" + idx + " - " + MetadataRecord.DERIVED_TYPE + ": name=!" + name + ": size=" + size + ": align=" + align + ": offset=" + offset + ": baseType=!" + baseType + ": file=!" +
                        file + ", line=" + line + " - " + Arrays.toString(args));
    }

    protected void createCompositeType(long[] args) {
        // https://github.com/llvm-mirror/llvm/blob/release_38/lib/Bitcode/Writer/BitcodeWriter.cpp#L953
        long distinct = args[0];
        long tag = args[1];
        long name = args[2];
        long file = args[3];
        long line = args[4];
        long scope = args[5];
        long baseType = args[6];
        long size = args[7];
        long align = args[8];
        long offset = args[9];
        long flags = args[10];
        long elements = args[11];
        long runtimeLang = args[12];
        long vTableHolder = args[13];
        long templateParams = args[14];
        long rawIdentifier = args[15];

        System.out.println("!" + idx + " - " + MetadataRecord.COMPOSITE_TYPE + ": name=!" + name + ": size=" + size + ": align=" + align + ": file=!" +
                        file + ", line=" + line + ", elements=!" + elements + " - " + Arrays.toString(args));
    }
}
