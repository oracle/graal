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
import uk.ac.man.cs.llvm.ir.model.MetadataBlock;
import uk.ac.man.cs.llvm.ir.model.metadata.BasicType;
import uk.ac.man.cs.llvm.ir.model.metadata.CompileUnit;
import uk.ac.man.cs.llvm.ir.model.metadata.CompositeType;
import uk.ac.man.cs.llvm.ir.model.metadata.DerivedType;
import uk.ac.man.cs.llvm.ir.model.metadata.Enumerator;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataFile;
import uk.ac.man.cs.llvm.ir.model.metadata.GlobalVariable;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataKind;
import uk.ac.man.cs.llvm.ir.model.metadata.LexicalBlock;
import uk.ac.man.cs.llvm.ir.model.metadata.LexicalBlockFile;
import uk.ac.man.cs.llvm.ir.model.metadata.LocalVariable;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataBaseNode;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataString;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataName;
import uk.ac.man.cs.llvm.ir.model.metadata.NamedNode;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataNode;
import uk.ac.man.cs.llvm.ir.model.metadata.Subprogram;
import uk.ac.man.cs.llvm.ir.model.metadata.Subrange;
import uk.ac.man.cs.llvm.ir.model.metadata.MetadataValue;
import uk.ac.man.cs.llvm.ir.module.records.MetadataRecord;
import uk.ac.man.cs.llvm.ir.types.Type;

public class Metadata implements ParserListener {

    protected int idx = 1; // TODO: remove

    protected final Types types;

    protected final List<Type> symbols;

    protected final MetadataBlock metadata = new MetadataBlock();

    protected int oldMetadataSize = 0; // TODO: only for debugging purpose

    public Metadata(Types types, List<Type> symbols) {
        this.types = types;
        this.symbols = symbols;
        metadata.setStartIndex(1);
        int i = 0;
    }

    protected void printMetadataDebugMsg() {
        if (metadata.size() != oldMetadataSize) {
            System.out.println("!" + idx + " - " + metadata.getAbsolute(metadata.size() - 1));
            oldMetadataSize = metadata.size();
        }
    }

    protected static long unrotateSign(long U) {
        return (U & 1) == 1 ? ~(U >> 1) : U >> 1;
    }

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

            case LEXICAL_BLOCK:
                createLexicalBlock(args);
                break;

            case LEXICAL_BLOCK_FILE:
                createLexicalBlockFile(args);
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

            case COMPILE_UNIT:
                createCompileUnit(args);
                break;

            default:
                System.out.println("!" + idx + " - " + record + ": " + Arrays.toString(args));
                break;
        }

        // printMetadataDebugMsg(); // Enable to allow debugging of the metadata parser

        idx++;
    }

    protected void createString(long[] args) {
        String s = "";
        for (long lc : args) {
            s += (char) (lc); // TODO: unicode characters?
        }

        MetadataString node = new MetadataString(s);

        metadata.add(node);
    }

    protected void createValue(long[] args) {
        Type t = MetadataArgumentParser.typeValToType(types, symbols, args[0], args[1]);

        MetadataValue node = new MetadataValue(t);

        metadata.add(node);
    }

    protected void createNode(long[] args) {
        MetadataNode node = new MetadataNode();

        for (long arg : args) {
            node.add(metadata.getReference((int) arg));
        }

        metadata.add(node);
    }

    protected void createName(long[] args) {
        String name = "";
        for (long lc : args) {
            name += (char) (lc); // TODO: unicode characters?
        }

        MetadataName node = new MetadataName(name);

        metadata.add(node);
    }

    protected void createDistinctNode(long[] args) {
        // [n x md num]

        System.out.println("!" + idx + " - " + MetadataRecord.DISTINCT_NODE + " - " + Arrays.toString(args));
    }

    protected void createKind(long[] args) {
        String name = "";
        for (int i = 1; i < args.length; i++) {
            name += (char) (args[i]); // TODO: unicode characters?
        }

        MetadataKind node = new MetadataKind(args[0], name);

        metadata.add(node);
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
        NamedNode node = new NamedNode();

        for (long arg : args) {
            node.add(metadata.getReference((int) arg));
        }

        metadata.add(node);
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
        Subrange node = new Subrange();

        // long distinct = args[0];
        node.setSize(args[1]);
        node.setLowBound(unrotateSign(args[2]));

        metadata.add(node);
    }

    protected void createEnumerator(long[] args) {
        Enumerator node = new Enumerator();

        // long distinct = args[0];
        node.setValue(unrotateSign(args[1]));
        node.setName(metadata.getReference(args[2]));

        metadata.add(node);
    }

    protected void createBasicType(long[] args) {
        BasicType node = new BasicType();

        // long distinct = args[0];
        // long tag = args[0];
        node.setName(metadata.getReference(args[2]));
        node.setSize(args[3]);
        node.setAlign(args[4]);
        node.setEncoding(args[5]);

        metadata.add(node);
    }

    protected void createFile(long[] args) {
        MetadataFile node = new MetadataFile();

        // long distinct = args[0];
        node.setFile(metadata.getReference(args[1]));
        node.setDirectory(metadata.getReference(args[2]));

        metadata.add(node);
    }

    protected void createSubprogram(long[] args) {
        Subprogram node = new Subprogram();

        // long distinct = args[0];
        // long scope = args[1];
        node.setName(metadata.getReference(args[2]));
        node.setLinkageName(metadata.getReference(args[3]));
        node.setFile(metadata.getReference(args[4]));
        node.setLine(args[5]);
        node.setType(metadata.getReference(args[6]));
        node.setLocalToUnit(args[7] == 1);
        node.setDefinedInCompileUnit(args[8] == 1);
        node.setScopeLine(args[9]);
        // long virtuallity = args[11];
        // long virtualIndex = args[12];
        node.setFlags(metadata.getReference(args[13]));
        node.setOptimized(args[14] == 1);
        // long templateParams = args[15];
        // long declaration = args[16];
        // long variables = args[17];

        metadata.add(node);
    }

    protected void createSubroutineType(long[] args) {
        CompositeType node = new CompositeType();

        // long distinct = args[0];
        node.setFlags(args[1]);
        node.setMemberDescriptors(metadata.getReference(args[2])); // TODO: correct?

        metadata.add(node);
    }

    protected void createLexicalBlock(long[] args) {
        LexicalBlock node = new LexicalBlock();

        // long distinct = args[0];
        // long scope = args[1];
        node.setFile(metadata.getReference(args[2]));
        node.setLine(args[3]);
        node.setColumn(args[4]);

        metadata.add(node);
    }

    protected void createLexicalBlockFile(long[] args) {
        LexicalBlockFile node = new LexicalBlockFile();

        // long distinct = args[0];
        // long scope = args[1];
        node.setFile(metadata.getReference(args[2]));
        // long discriminator = args[3]);

        metadata.add(node);
    }

    protected void createLocalVar(long[] args) {
        LocalVariable node = new LocalVariable();

        // long distinct = args[0];
        // long scope = args[1];
        node.setName(metadata.getReference(args[2]));
        node.setFile(metadata.getReference(args[3]));
        node.setLine(args[4]);
        node.setType(metadata.getReference(args[5]));
        node.setArg(args[6]);
        node.setFlags(args[7]);

        metadata.add(node);
    }

    protected void createGlobalVar(long[] args) {
        GlobalVariable node = new GlobalVariable();

        // long distinct = args[0];
        // long scope = args[1];
        node.setName(metadata.getReference(args[2]));
        node.setLinkageName(metadata.getReference(args[3]));
        node.setLine(args[5]);
        node.setType(metadata.getReference(args[6]));
        node.setLocalToCompileUnit(args[7] == 1);
        node.setDefinedInCompileUnit(args[8] == 1);
        // long rawVariable = args[9];
        // long staticDataMemberDeclaration = args[10];

        metadata.add(node);
    }

    protected void createDerivedType(long[] args) {
        DerivedType node = new DerivedType();

        // long distinct = args[0];
        // long tag = args[1];
        node.setName(metadata.getReference(args[2]));
        node.setFile(metadata.getReference(args[3]));
        node.setLine(args[4]);
        // long scope = args[5];
        node.setBaseType(metadata.getReference(args[6]));
        node.setSize(args[7]);
        node.setAlign(args[8]);
        node.setOffset(args[9]);
        node.setFlags(args[10]);
        // long extraData = args[11];

        metadata.add(node);
    }

    protected void createCompositeType(long[] args) {
        CompositeType node = new CompositeType();

        // long distinct = args[0];
        // long tag = args[1];
        node.setName(metadata.getReference(args[2]));
        node.setFile(metadata.getReference(args[3]));
        node.setLine(args[4]);
        // long scope = args[5];
        node.setDerivedFrom(metadata.getReference(args[6])); // TODO: verify
        node.setSize(args[7]);
        node.setAlign(args[8]);
        node.setOffset(args[9]);
        node.setFlags(args[10]);
        node.setMemberDescriptors(metadata.getReference(args[11]));
        node.setRuntimeLanguage(args[12]);
        // long vTableHolder = args[13];
        // long templateParams = args[14];
        // long rawIdentifier = args[15];

        metadata.add(node);
    }

    protected void createCompileUnit(long[] args) {
        CompileUnit node = new CompileUnit();

        // long distinct = args[0]; // always true
        node.setLanguage(args[1]);
        node.setFile(metadata.getReference(args[2]));
        node.setProducer(metadata.getReference(args[3]));
        node.setOptimized(args[4] == 1);
        node.setFlags(metadata.getReference(args[5]));
        node.setRuntimeVersion(args[6]);
        // long rawSplitDebugFilename = args[7];
        // long emissionKind = args[8];
        node.setEnumType(metadata.getReference(args[9]));
        node.setRetainedTypes(metadata.getReference(args[10]));
        node.setSubprograms(metadata.getReference(args[11]));
        node.setGlobalVariables(metadata.getReference(args[12]));
        // long importedEntities = args[13];
        // long DWOId = args[14];
        // long macros = args[15];

        metadata.add(node);
    }
}
