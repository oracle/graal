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

import java.util.Arrays;
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
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataGlobalVariable;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataKind;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataLexicalBlock;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataLexicalBlockFile;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataLocalVariable;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataName;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataNamedNode;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataNode;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataString;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataSubprogram;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataSubrange;
import com.oracle.truffle.llvm.parser.base.model.metadata.MetadataValue;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;

import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.ParserListener;
import com.oracle.truffle.llvm.parser.bc.impl.parser.ir.module.records.MetadataRecord;

public class Metadata implements ParserListener {

    protected final Types types;

    protected final List<Type> symbols;

    protected final MetadataBlock metadata;

    public Metadata(Types types, List<Type> symbols, SymbolGenerator generator) {
        this.types = types;
        this.symbols = symbols;
        metadata = generator.getMetadata();
        metadata.setStartIndex(1);
    }

    protected void printMetadataDebugMsg() {
        LLVMLogger.info("!" + (metadata.getCurrentIndex() - 1) + " - " + metadata.getAbsolute(metadata.size() - 1));
    }

    protected static long unrotateSign(long u) {
        return (u & 1) == 1 ? ~(u >> 1) : u >> 1;
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
                metadata.add(null);
                LLVMLogger.info("! - TODO: #" + record + ": " + Arrays.toString(args));
                break;
        }

        if (LLVMBaseOptionFacade.verboseEnabled()) {
            printMetadataDebugMsg();
        }
    }

    private static final long LONG_ARRAY_TO_STRING_BYTE_PART = 0x000000FF;

    private static String longArrayToString(long[] args) {
        // We use a byte array, so "new String(...)" is able to handle Unicode Characters correctly
        byte[] bytes = new byte[args.length];
        for (int i = 0; i < args.length; i++) {
            bytes[i] = (byte) (args[i] & LONG_ARRAY_TO_STRING_BYTE_PART);
        }
        return new String(bytes);
    }

    protected void createString(long[] args) {
        MetadataString node = new MetadataString(longArrayToString(args));

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
        MetadataName node = new MetadataName(longArrayToString(args));

        metadata.add(node);
    }

    protected void createDistinctNode(long[] args) {
        // [n x md num]
        metadata.add(null);
        LLVMLogger.info("! - " + MetadataRecord.DISTINCT_NODE + " - " + Arrays.toString(args));
    }

    protected void createKind(long[] args) {
        long id = args[0];
        String name = longArrayToString(Arrays.copyOfRange(args, 1, args.length));

        MetadataKind node = new MetadataKind(id, name);

        metadata.add(node);
    }

    protected void createLocation(long[] args) {
        // int i = 0;
        //
        // long distinct = args[argNumber++];
        // long line = args[i++];
        // long column = args[i++];
        // long scope = args[i++];
        // long inlineAt = args[i++];
        metadata.add(null);
        LLVMLogger.info("! - " + MetadataRecord.LOCATION + " - " + Arrays.toString(args));
    }

    protected void createNamedNode(long[] args) {
        MetadataNamedNode node = new MetadataNamedNode();

        for (long arg : args) {
            node.add(metadata.getReference((int) arg));
        }

        metadata.add(node);
    }

    protected void createAttachment(long[] args) {
        // [n x mdnodes]
        metadata.add(null);
        LLVMLogger.info("! - " + MetadataRecord.ATTACHMENT + " - " + Arrays.toString(args));
    }

    protected void createGenericDebug(long[] args) {
        // int i = 0;
        //
        // long distinct = args[i++];
        // long tag = args[i++];
        // long vers = args[i++];
        // long header = args[i++];
        // TODO: args[4] // n x md num
        metadata.add(null);
        LLVMLogger.info("! - " + MetadataRecord.GENERIC_DEBUG + " - " + Arrays.toString(args));
    }

    protected void createSubrange(long[] args) {
        MetadataSubrange node = new MetadataSubrange();

        int i = 0;
        i++; // distinct
        node.setSize(args[i++]);
        node.setLowBound(unrotateSign(args[i++]));

        metadata.add(node);
    }

    protected void createEnumerator(long[] args) {
        MetadataEnumerator node = new MetadataEnumerator();

        int i = 0;
        i++; // distinct
        node.setValue(unrotateSign(args[i++]));
        node.setName(metadata.getReference(args[i++]));

        metadata.add(node);
    }

    protected void createBasicType(long[] args) {
        MetadataBasicType node = new MetadataBasicType();

        int i = 0;
        i++; // distinct
        i++; // tag
        node.setName(metadata.getReference(args[i++]));
        node.setSize(args[i++]);
        node.setAlign(args[i++]);
        node.setEncoding(args[i++]);

        metadata.add(node);
    }

    protected void createFile(long[] args) {
        MetadataFile node = new MetadataFile();

        int i = 0;
        i++; // distinct
        node.setFile(metadata.getReference(args[i++]));
        node.setDirectory(metadata.getReference(args[i++]));

        metadata.add(node);
    }

    protected void createSubprogram(long[] args) {
        MetadataSubprogram node = new MetadataSubprogram();

        int i = 0;
        i++; // distinct
        i++; // scope
        node.setName(metadata.getReference(args[i++]));
        node.setLinkageName(metadata.getReference(args[i++]));
        node.setFile(metadata.getReference(args[i++]));
        node.setLine(args[i++]);
        node.setType(metadata.getReference(args[i++]));
        node.setLocalToUnit(args[i++] == 1);
        node.setDefinedInCompileUnit(args[i++] == 1);
        node.setScopeLine(args[i++]);
        node.setContainingType(metadata.getReference(args[i++]));
        node.setVirtuallity(args[i++]);
        node.setVirtualIndex(args[i++]);
        node.setFlags(args[i++]);
        node.setOptimized(args[i++] == 1);
        node.setTemplateParams(metadata.getReference(args[i++]));
        node.setDeclaration(metadata.getReference(args[i++]));
        node.setVariables(metadata.getReference(args[i++]));

        metadata.add(node);
    }

    protected void createSubroutineType(long[] args) {
        MetadataCompositeType node = new MetadataCompositeType();

        int i = 0;
        i++; // distinct
        node.setFlags(args[i++]);
        node.setMemberDescriptors(metadata.getReference(args[i++])); // TODO: correct?

        metadata.add(node);
    }

    protected void createLexicalBlock(long[] args) {
        MetadataLexicalBlock node = new MetadataLexicalBlock();

        int i = 0;
        i++; // distinct
        i++; // scope
        node.setFile(metadata.getReference(args[i++]));
        node.setLine(args[i++]);
        node.setColumn(args[i++]);

        metadata.add(node);
    }

    protected void createLexicalBlockFile(long[] args) {
        MetadataLexicalBlockFile node = new MetadataLexicalBlockFile();

        int i = 0;
        i++; // distinct
        i++; // scope
        node.setFile(metadata.getReference(args[i++]));
        i++; // discriminator

        metadata.add(node);
    }

    protected void createLocalVar(long[] args) {
        MetadataLocalVariable node = new MetadataLocalVariable();

        int i = 0;
        i++; // distinct
        i++; // scope
        node.setName(metadata.getReference(args[i++]));
        node.setFile(metadata.getReference(args[i++]));
        node.setLine(args[i++]);
        node.setType(metadata.getReference(args[i++]));
        node.setArg(args[i++]);
        node.setFlags(args[i++]);

        metadata.add(node);
    }

    protected void createGlobalVar(long[] args) {
        MetadataGlobalVariable node = new MetadataGlobalVariable();

        int i = 0;
        i++; // distinct
        i++; // scope
        node.setName(metadata.getReference(args[i++]));
        node.setLinkageName(metadata.getReference(args[i++]));
        node.setLine(args[i++]);
        node.setType(metadata.getReference(args[i++]));
        node.setLocalToCompileUnit(args[i++] == 1);
        node.setDefinedInCompileUnit(args[i++] == 1);
        i++; // rawVariable
        i++; // staticDataMemberDeclaration

        metadata.add(node);
    }

    protected void createDerivedType(long[] args) {
        MetadataDerivedType node = new MetadataDerivedType();

        int i = 0;
        i++; // distinct
        i++; // tag
        node.setName(metadata.getReference(args[i++]));
        node.setFile(metadata.getReference(args[i++]));
        node.setLine(args[i++]);
        i++; // scope
        node.setBaseType(metadata.getReference(args[i++]));
        node.setSize(args[i++]);
        node.setAlign(args[i++]);
        node.setOffset(args[i++]);
        node.setFlags(args[i++]);
        i++; // extraData

        metadata.add(node);
    }

    protected void createCompositeType(long[] args) {
        MetadataCompositeType node = new MetadataCompositeType();

        int i = 0;
        i++; // distinct
        i++; // tag
        node.setName(metadata.getReference(args[i++]));
        node.setFile(metadata.getReference(args[i++]));
        node.setLine(args[i++]);
        i++; // scope
        node.setDerivedFrom(metadata.getReference(args[i++])); // TODO: verify
        node.setSize(args[i++]);
        node.setAlign(args[i++]);
        node.setOffset(args[i++]);
        node.setFlags(args[i++]);
        node.setMemberDescriptors(metadata.getReference(args[i++]));
        node.setRuntimeLanguage(args[i++]);
        i++; // vTableHolder
        i++; // templateParams
        i++; // rawIdentifier

        metadata.add(node);
    }

    protected void createCompileUnit(long[] args) {
        MetadataCompileUnit node = new MetadataCompileUnit();

        int i = 0;
        i++; // distinct, always true
        node.setLanguage(DwLangNameRecord.decode(args[i++]));
        node.setFile(metadata.getReference(args[i++]));
        node.setProducer(metadata.getReference(args[i++]));
        node.setOptimized(args[i++] == 1);
        node.setFlags(metadata.getReference(args[i++]));
        node.setRuntimeVersion(args[i++]);
        i++; // rawSplitDebugFilename
        i++; // emissionKind
        node.setEnumType(metadata.getReference(args[i++]));
        node.setRetainedTypes(metadata.getReference(args[i++]));
        node.setSubprograms(metadata.getReference(args[i++]));
        node.setGlobalVariables(metadata.getReference(args[i++]));
        i++; // importedEntities
        i++; // DWOId
        i++; // macros

        metadata.add(node);
    }
}
