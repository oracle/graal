/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.listeners;

import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.llvm.parser.metadata.MDAttachment;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDBasicType;
import com.oracle.truffle.llvm.parser.metadata.MDCommonBlock;
import com.oracle.truffle.llvm.parser.metadata.MDCompileUnit;
import com.oracle.truffle.llvm.parser.metadata.MDCompositeType;
import com.oracle.truffle.llvm.parser.metadata.MDDerivedType;
import com.oracle.truffle.llvm.parser.metadata.MDEnumerator;
import com.oracle.truffle.llvm.parser.metadata.MDExpression;
import com.oracle.truffle.llvm.parser.metadata.MDFile;
import com.oracle.truffle.llvm.parser.metadata.MDGenericDebug;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariableExpression;
import com.oracle.truffle.llvm.parser.metadata.MDImportedEntity;
import com.oracle.truffle.llvm.parser.metadata.MDKind;
import com.oracle.truffle.llvm.parser.metadata.MDLabel;
import com.oracle.truffle.llvm.parser.metadata.MDLexicalBlock;
import com.oracle.truffle.llvm.parser.metadata.MDLexicalBlockFile;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDLocation;
import com.oracle.truffle.llvm.parser.metadata.MDMacro;
import com.oracle.truffle.llvm.parser.metadata.MDMacroFile;
import com.oracle.truffle.llvm.parser.metadata.MDModule;
import com.oracle.truffle.llvm.parser.metadata.MDNamedNode;
import com.oracle.truffle.llvm.parser.metadata.MDNamespace;
import com.oracle.truffle.llvm.parser.metadata.MDNode;
import com.oracle.truffle.llvm.parser.metadata.MDObjCProperty;
import com.oracle.truffle.llvm.parser.metadata.MDString;
import com.oracle.truffle.llvm.parser.metadata.MDSubprogram;
import com.oracle.truffle.llvm.parser.metadata.MDSubrange;
import com.oracle.truffle.llvm.parser.metadata.MDSubroutine;
import com.oracle.truffle.llvm.parser.metadata.MDTemplateType;
import com.oracle.truffle.llvm.parser.metadata.MDTemplateTypeParameter;
import com.oracle.truffle.llvm.parser.metadata.MDTemplateValue;
import com.oracle.truffle.llvm.parser.metadata.MDValue;
import com.oracle.truffle.llvm.parser.metadata.MetadataValueList;
import com.oracle.truffle.llvm.parser.metadata.ParseUtil;
import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.records.DwTagRecord;
import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.Type;

public class Metadata implements ParserListener {

    private static final int METADATA_STRING = 1;
    private static final int METADATA_VALUE = 2;
    private static final int METADATA_NODE = 3;
    private static final int METADATA_NAME = 4;
    private static final int METADATA_DISTINCT_NODE = 5;
    private static final int METADATA_KIND = 6;
    private static final int METADATA_LOCATION = 7;
    private static final int METADATA_OLD_NODE = 8;
    private static final int METADATA_OLD_FN_NODE = 9;
    private static final int METADATA_NAMED_NODE = 10;
    private static final int METADATA_ATTACHMENT = 11;
    private static final int METADATA_GENERIC_DEBUG = 12;
    private static final int METADATA_SUBRANGE = 13;
    private static final int METADATA_ENUMERATOR = 14;
    private static final int METADATA_BASIC_TYPE = 15;
    private static final int METADATA_FILE = 16;
    private static final int METADATA_DERIVED_TYPE = 17;
    private static final int METADATA_COMPOSITE_TYPE = 18;
    private static final int METADATA_SUBROUTINE_TYPE = 19;
    private static final int METADATA_COMPILE_UNIT = 20;
    protected static final int METADATA_SUBPROGRAM = 21;
    private static final int METADATA_LEXICAL_BLOCK = 22;
    private static final int METADATA_LEXICAL_BLOCK_FILE = 23;
    private static final int METADATA_NAMESPACE = 24;
    private static final int METADATA_TEMPLATE_TYPE = 25;
    private static final int METADATA_TEMPLATE_VALUE = 26;
    private static final int METADATA_GLOBAL_VAR = 27;
    private static final int METADATA_LOCAL_VAR = 28;
    private static final int METADATA_EXPRESSION = 29;
    private static final int METADATA_OBJC_PROPERTY = 30;
    private static final int METADATA_IMPORTED_ENTITY = 31;
    private static final int METADATA_MODULE = 32;
    private static final int METADATA_MACRO = 33;
    private static final int METADATA_MACRO_FILE = 34;
    private static final int METADATA_STRINGS = 35;
    private static final int METADATA_GLOBAL_DECL_ATTACHMENT = 36;
    private static final int METADATA_GLOBAL_VAR_EXPR = 37;
    private static final int METADATA_INDEX_OFFSET = 38;
    private static final int METADATA_INDEX = 39;
    private static final int METADATA_LABEL = 40;
    private static final int METADATA_COMMON_BLOCK = 44;

    public Type getTypeById(long id) {
        return types.get(id);
    }

    public IRScope getScope() {
        return scope;
    }

    protected final IRScope scope;

    private final Set<MDCompositeType> compositeTypes;

    protected final Types types;

    protected final MetadataValueList metadata;

    private String lastParsedName = null;

    Metadata(Types types, IRScope scope) {
        this.types = types;
        this.scope = scope;
        this.metadata = scope.getMetadata();
        this.compositeTypes = new HashSet<>();
    }

    // https://github.com/llvm-mirror/llvm/blob/release_38/include/llvm/Bitcode/LLVMBitCodes.h#L191
    @Override
    public void record(RecordBuffer buffer) {
        long[] args = buffer.dumpArray();
        final int opCode = buffer.getId();
        parseOpcode(buffer, args, opCode);
    }

    protected void parseOpcode(RecordBuffer buffer, long[] args, final int opCode) {
        switch (opCode) {
            case METADATA_STRING:
                metadata.add(MDString.create(buffer));
                break;

            case METADATA_VALUE:
                buffer.skip();
                metadata.add(MDValue.create(buffer.read(), scope));
                break;

            case METADATA_DISTINCT_NODE:
                // we would only care if a node is distinct or not if we wanted to modify the ast
            case METADATA_NODE:
                metadata.add(MDNode.create38(buffer, metadata));
                break;

            case METADATA_NAME:
                // read the name, this must be followed by a NAMED_NODE which will remove it again
                lastParsedName = buffer.readUnicodeString();
                break;

            case METADATA_KIND:
                metadata.addKind(MDKind.create(buffer.read(), buffer.readUnicodeString()));
                break;

            case METADATA_LOCATION:
                metadata.add(MDLocation.create38(buffer, metadata));
                break;

            case METADATA_OLD_NODE:
                createOldNode(buffer);
                break;

            case METADATA_OLD_FN_NODE:
                buffer.skip();
                metadata.add(MDValue.create(buffer.read(), scope));
                break;

            case METADATA_NAMED_NODE:
                createNamedNode(buffer);
                break;

            case METADATA_ATTACHMENT:
                createAttachment(buffer, false);
                break;

            case METADATA_GENERIC_DEBUG:
                metadata.add(MDGenericDebug.create38(buffer, metadata));
                break;

            case METADATA_SUBRANGE:
                metadata.add(MDSubrange.createNewFormat(buffer, metadata));
                break;

            case METADATA_ENUMERATOR:
                metadata.add(MDEnumerator.create38(args, metadata));
                break;

            case METADATA_BASIC_TYPE:
                metadata.add(MDBasicType.create38(args, metadata));
                break;

            case METADATA_FILE:
                metadata.add(MDFile.create38(args, metadata));
                break;

            case METADATA_SUBPROGRAM:
                metadata.add(MDSubprogram.createNewFormat(args, metadata));
                break;

            case METADATA_SUBROUTINE_TYPE:
                metadata.add(MDSubroutine.create38(args, metadata));
                break;

            case METADATA_LEXICAL_BLOCK:
                metadata.add(MDLexicalBlock.create38(args, metadata));
                break;

            case METADATA_LEXICAL_BLOCK_FILE:
                metadata.add(MDLexicalBlockFile.create38(args, metadata));
                break;

            case METADATA_LOCAL_VAR: {
                final MDLocalVariable md = MDLocalVariable.create38(args, metadata);
                metadata.add(md);
                metadata.registerLocal(md);
                break;
            }

            case METADATA_NAMESPACE: {
                final MDNamespace namespace = MDNamespace.create38(args, metadata);
                metadata.registerExportedScope(namespace);
                metadata.add(namespace);
                break;
            }

            case METADATA_GLOBAL_VAR:
                metadata.add(MDGlobalVariable.create38(args, metadata));
                break;

            case METADATA_DERIVED_TYPE:
                metadata.add(MDDerivedType.create38(args, metadata));
                break;

            case METADATA_COMPOSITE_TYPE: {
                final MDCompositeType type = MDCompositeType.create38(args, metadata);
                metadata.add(type);
                compositeTypes.add(type);
                break;
            }

            case METADATA_COMPILE_UNIT:
                metadata.add(MDCompileUnit.create38(args, metadata));
                break;

            case METADATA_TEMPLATE_TYPE:
                metadata.add(MDTemplateType.create38(args, metadata));
                break;

            case METADATA_TEMPLATE_VALUE:
                metadata.add(MDTemplateValue.create38(args, metadata));
                break;

            case METADATA_EXPRESSION:
                metadata.add(MDExpression.create(args));
                break;

            case METADATA_OBJC_PROPERTY:
                metadata.add(MDObjCProperty.create38(args, metadata));
                break;

            case METADATA_IMPORTED_ENTITY:
                metadata.add(MDImportedEntity.create38(args, metadata));
                break;

            case METADATA_MODULE: {
                final MDModule module = MDModule.create38(args, metadata);
                metadata.registerExportedScope(module);
                metadata.add(module);
                break;
            }

            case METADATA_MACRO:
                metadata.add(MDMacro.create38(args, metadata));
                break;

            case METADATA_MACRO_FILE:
                metadata.add(MDMacroFile.create38(args, metadata));
                break;

            case METADATA_STRINGS: {
                // since llvm 3.9 all metadata strings are emitted as a single blob
                final MDString[] strings = MDString.createFromBlob(args);
                for (final MDString string : strings) {
                    metadata.add(string);
                }
                break;
            }

            case METADATA_GLOBAL_VAR_EXPR:
                metadata.add(MDGlobalVariableExpression.create(args, metadata));
                break;

            case METADATA_GLOBAL_DECL_ATTACHMENT: {
                createAttachment(buffer, true);
                break;
            }

            case METADATA_COMMON_BLOCK:
                metadata.add(MDCommonBlock.create(args, metadata));
                break;

            case METADATA_LABEL:
                metadata.add(MDLabel.create(args, metadata));
                break;

            case METADATA_INDEX_OFFSET:
            case METADATA_INDEX:
                // llvm uses these to implement lazy loading, we can safely ignore them
                break;

            default:
                metadata.add(null);
                throw new LLVMParserException("Unsupported opCode in metadata block: " + opCode);
        }
    }

    private void createNamedNode(RecordBuffer buffer) {
        if (lastParsedName != null) {
            metadata.addNamedNode(MDNamedNode.create(lastParsedName, buffer.dumpArray(), metadata));
            lastParsedName = null;
        }
    }

    private void createAttachment(RecordBuffer buffer, boolean isGlobal) {
        long[] args = buffer.dumpArray();
        if (args.length > 0) {
            final int offset = args.length % 2;
            final int targetIndex = (int) args[0];

            for (int i = offset; i < args.length; i += 2) {
                final MDKind kind = metadata.getKind(args[i]);
                final MDAttachment attachment = MDAttachment.create(kind, args[i + 1], metadata);
                if (isGlobal) {
                    scope.attachGlobalMetadata(targetIndex, attachment);
                } else if (offset == 0) {
                    scope.attachFunctionMetadata(attachment);
                } else {
                    scope.attachInstructionMetadata(targetIndex, attachment);
                }
            }
        }
    }

    private static final int ARGINDEX_IDENT = 0;

    private void createOldNode(RecordBuffer buffer) {
        long[] args = buffer.dumpArray();
        if (ParseUtil.isInteger(args, ARGINDEX_IDENT, this) && DwTagRecord.isDwarfDescriptor(ParseUtil.asLong(args, ARGINDEX_IDENT, this))) {
            // this is a debug information descriptor as described in
            // http://releases.llvm.org/3.2/docs/SourceLevelDebugging.html#debug_info_descriptors
            final int ident = ParseUtil.asInt(args, ARGINDEX_IDENT, this);
            final DwTagRecord record = DwTagRecord.decode(ident);

            switch (record) {
                case DW_TAG_COMPILE_UNIT:
                    metadata.add(MDCompileUnit.create32(args, this));
                    break;

                case DW_TAG_FILE_TYPE:
                    metadata.add(MDFile.create32(args, this));
                    break;

                case DW_TAG_CONSTANT:
                case DW_TAG_VARIABLE:
                    // descriptor for a global variable or constant
                    metadata.add(MDGlobalVariable.create32(args, this));
                    break;

                case DW_TAG_SUBPROGRAM:
                    metadata.add(MDSubprogram.create32(args, this));
                    break;

                case DW_TAG_LEXICAL_BLOCK:
                    metadata.add(createLexicalBlock(args));
                    break;

                case DW_TAG_BASE_TYPE:
                    metadata.add(MDBasicType.create32(args, this));
                    break;

                case DW_TAG_FORMAL_PARAMETER:
                case DW_TAG_MEMBER:
                case DW_TAG_POINTER_TYPE:
                case DW_TAG_REFERENCE_TYPE:
                case DW_TAG_TYPEDEF:
                case DW_TAG_CONST_TYPE:
                case DW_TAG_VOLATILE_TYPE:
                case DW_TAG_RESTRICT_TYPE:
                case DW_TAG_FRIEND:
                    metadata.add(MDDerivedType.create32(args, this));
                    break;

                case DW_TAG_CLASS_TYPE:
                case DW_TAG_ARRAY_TYPE:
                case DW_TAG_ENUMERATION_TYPE:
                case DW_TAG_STRUCTURE_TYPE:
                case DW_TAG_UNION_TYPE:
                case DW_TAG_VECTOR_TYPE:
                case DW_TAG_INHERITANCE:
                case DW_TAG_SUBROUTINE_TYPE: {
                    final MDCompositeType type = MDCompositeType.create32(args, this);
                    metadata.add(type);
                    compositeTypes.add(type);
                    break;
                }

                case DW_TAG_SUBRANGE_TYPE:
                    metadata.add(MDSubrange.createOldFormat(args, this));
                    break;

                case DW_TAG_ENUMERATOR:
                    metadata.add(MDEnumerator.create32(args, this));
                    break;

                case DW_TAG_AUTO_VARIABLE:
                case DW_TAG_ARG_VARIABLE:
                case DW_TAG_RETURN_VARIABLE: {
                    final MDLocalVariable md = MDLocalVariable.create32(args, this);
                    metadata.registerLocal(md);
                    metadata.add(md);
                    break;
                }

                case DW_TAG_UNKNOWN:
                    metadata.add(MDNode.create32(args, this));
                    break;

                case DW_TAG_TEMPLATE_TYPE_PARAMETER:
                    metadata.add(MDTemplateTypeParameter.create32(args, this));
                    break;

                case DW_TAG_TEMPLATE_VALUE_PARAMETER:
                    metadata.add(MDTemplateValue.create32(args, this));
                    break;

                case DW_TAG_NAMESPACE:
                    metadata.add(MDNamespace.create32(args, this));
                    break;

                default:
                    metadata.add(MDNode.create32(args, this));
            }
        } else {
            metadata.add(MDNode.create32(args, this));
        }
    }

    private static final int LEXICALBLOCK_DISTINCTOR_ARGINDEX = 2;

    private MDBaseNode createLexicalBlock(long[] args) {
        if (ParseUtil.isInteger(args, LEXICALBLOCK_DISTINCTOR_ARGINDEX, this)) {
            // lexical block
            return MDLexicalBlock.create32(args, this);
        } else {
            // lexical block file
            return MDLexicalBlockFile.create32(args, this);
        }
    }

    @Override
    public void exit() {
        for (MDCompositeType type : compositeTypes) {
            final String identifier = MDString.getIfInstance(type.getIdentifier());
            metadata.registerType(identifier, type);
        }
    }
}
