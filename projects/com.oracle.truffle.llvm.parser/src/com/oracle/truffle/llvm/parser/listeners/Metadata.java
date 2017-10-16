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
package com.oracle.truffle.llvm.parser.listeners;

import java.util.Arrays;

import com.oracle.truffle.llvm.parser.metadata.MDAttachment;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDBasicType;
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
import com.oracle.truffle.llvm.parser.records.MetadataRecord;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class Metadata implements ParserListener {

    public Type getTypeById(long id) {
        return types.get(id);
    }

    public IRScope getContainer() {
        return container;
    }

    private final IRScope container;

    protected final Types types;

    protected final MetadataValueList metadata;

    private String lastParsedName = null;

    Metadata(Types types, IRScope container) {
        this.types = types;
        this.container = container;
        this.metadata = container.getMetadata();
    }

    // https://github.com/llvm-mirror/llvm/blob/release_38/include/llvm/Bitcode/LLVMBitCodes.h#L191
    @Override
    public void record(long id, long[] args) {
        MetadataRecord record = MetadataRecord.decode(id);
        switch (record) {
            case STRING:
                metadata.add(MDString.create(args));
                break;

            case VALUE:
                metadata.add(MDValue.create(args, this));
                break;

            case DISTINCT_NODE:
                // we would only care if a node is distinct or not if we wanted to modify the ast
            case NODE:
                metadata.add(MDNode.create38(args, metadata));
                break;

            case NAME:
                // read the name, this must be followed by a NAMED_NODE which will remove it again
                lastParsedName = ParseUtil.longArrayToString(0, args);
                break;

            case KIND:
                metadata.addKind(MDKind.create(args));
                break;

            case LOCATION:
                metadata.add(MDLocation.create38(args, metadata));
                break;

            case OLD_NODE:
                createOldNode(args);
                break;

            case OLD_FN_NODE:
                metadata.add(MDValue.create(args, this));
                break;

            case NAMED_NODE:
                createNamedNode(args);
                break;

            case ATTACHMENT:
                createAttachment(args);
                break;

            case GENERIC_DEBUG:
                metadata.add(MDGenericDebug.create38(args, metadata));
                break;

            case SUBRANGE:
                metadata.add(MDSubrange.create38(args));
                break;

            case ENUMERATOR:
                metadata.add(MDEnumerator.create38(args, metadata));
                break;

            case BASIC_TYPE:
                metadata.add(MDBasicType.create38(args, metadata));
                break;

            case FILE:
                metadata.add(MDFile.create38(args, metadata));
                break;

            case SUBPROGRAM:
                metadata.add(MDSubprogram.create38(args, metadata));
                break;

            case SUBROUTINE_TYPE:
                metadata.add(MDSubroutine.create38(args, metadata));
                break;

            case LEXICAL_BLOCK:
                metadata.add(MDLexicalBlock.create38(args, metadata));
                break;

            case LEXICAL_BLOCK_FILE:
                metadata.add(MDLexicalBlockFile.create38(args, metadata));
                break;

            case LOCAL_VAR:
                metadata.add(MDLocalVariable.create38(args, metadata));
                break;

            case NAMESPACE:
                metadata.add(MDNamespace.create38(args, metadata));
                break;

            case GLOBAL_VAR:
                metadata.add(MDGlobalVariable.create38(args, metadata));
                break;

            case DERIVED_TYPE:
                metadata.add(MDDerivedType.create38(args, metadata));
                break;

            case COMPOSITE_TYPE:
                metadata.add(MDCompositeType.create38(args, metadata));
                break;

            case COMPILE_UNIT:
                metadata.add(MDCompileUnit.create38(args, metadata));
                break;

            case TEMPLATE_TYPE:
                metadata.add(MDTemplateType.create38(args, metadata));
                break;

            case TEMPLATE_VALUE:
                metadata.add(MDTemplateValue.create38(args, metadata));
                break;

            case EXPRESSION:
                createExpression(args);
                break;

            case OBJC_PROPERTY:
                metadata.add(MDObjCProperty.create38(args, metadata));
                break;

            case IMPORTED_ENTITY:
                metadata.add(MDImportedEntity.create38(args, metadata));
                break;

            case MODULE:
                metadata.add(MDModule.create38(args, metadata));
                break;

            case MACRO:
                metadata.add(MDMacro.create38(args, metadata));
                break;

            case MACRO_FILE:
                metadata.add(MDMacroFile.create38(args, metadata));
                break;

            case STRINGS: {
                // since llvm 3.9 all metadata strings are emitted as a single blob
                final MDString[] strings = MDString.createFromBlob(args);
                for (final MDString string : strings) {
                    metadata.add(string);
                }
                break;
            }

            case GLOBAL_VAR_EXPR:
                metadata.add(MDGlobalVariableExpression.create(args, metadata));
                break;

            case GLOBAL_DECL_ATTACHMENT: {
                createAttachment(args);
                break;
            }

            case INDEX_OFFSET:
            case INDEX:
                // llvm uses these to implement lazy loading, we can safely ignore them
                break;

            default:
                metadata.add(null);
                throw new UnsupportedOperationException("Unsupported Metadata Record: " + record);
        }
    }

    private void createNamedNode(long[] args) {
        if (lastParsedName == null) {
            return;
        }

        final MDNamedNode node = new MDNamedNode(lastParsedName);
        lastParsedName = null;

        for (long arg : args) {
            node.add(metadata.getNonNullable(arg, node));
        }

        metadata.addNamedNode(node);
    }

    private void createAttachment(long[] args) {
        if (args.length > 0) {
            final int offset = args.length % 2;
            final int targetIndex = (int) args[0];

            for (int i = offset; i < args.length; i += 2) {
                final MDKind kind = metadata.getKind(args[i]);
                final MDAttachment attachment = MDAttachment.create(kind, args[i + 1], metadata);
                if (offset != 0) {
                    container.attachSymbolMetadata(targetIndex, attachment);
                } else {
                    container.attachMetadata(attachment);
                }
            }
        }
    }

    private static final int EXPRESSION_ARGSTARTINDEX = 1;

    private void createExpression(long[] args) {
        // TODO handle
        final long[] elements = Arrays.copyOfRange(args, EXPRESSION_ARGSTARTINDEX, args.length);
        metadata.add(new MDExpression(elements));
    }

    private static final int ARGINDEX_IDENT = 0;

    private void createOldNode(long[] args) {
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
                case DW_TAG_SUBROUTINE_TYPE:
                    metadata.add(MDCompositeType.create32(args, this));
                    break;

                case DW_TAG_SUBRANGE_TYPE:
                    metadata.add(MDSubrange.create32(args, this));
                    break;

                case DW_TAG_ENUMERATOR:
                    metadata.add(MDEnumerator.create32(args, this));
                    break;

                case DW_TAG_AUTO_VARIABLE:
                case DW_TAG_ARG_VARIABLE:
                case DW_TAG_RETURN_VARIABLE:
                    metadata.add(MDLocalVariable.create32(args, this));
                    break;

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
}
