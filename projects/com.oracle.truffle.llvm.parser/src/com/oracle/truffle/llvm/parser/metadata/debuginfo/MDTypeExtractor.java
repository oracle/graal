/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDBasicType;
import com.oracle.truffle.llvm.parser.metadata.MDCompositeType;
import com.oracle.truffle.llvm.parser.metadata.MDDerivedType;
import com.oracle.truffle.llvm.parser.metadata.MDEnumerator;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDNode;
import com.oracle.truffle.llvm.parser.metadata.MDOldNode;
import com.oracle.truffle.llvm.parser.metadata.MDReference;
import com.oracle.truffle.llvm.parser.metadata.MDSubrange;
import com.oracle.truffle.llvm.parser.metadata.MDTypedValue;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugArrayLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugBasicType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugDecoratorType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugEnumLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugMemberType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugPointerType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugStructLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

final class MDTypeExtractor implements MetadataVisitor {

    private static final String COUNT_NAME = "<count>";

    LLVMDebugType parseType(MDBaseNode mdType) {
        if (mdType == null) {
            return null;
        }
        mdType.accept(this);
        return parsedTypes.getOrDefault(mdType, LLVMDebugType.UNKNOWN_TYPE);
    }

    private final Map<MDBaseNode, LLVMDebugType> parsedTypes = new HashMap<>();

    MDTypeExtractor() {
    }

    @Override
    public void ifVisitNotOverwritten(MDBaseNode md) {
        parsedTypes.put(md, LLVMDebugType.UNKNOWN_TYPE);
    }

    @Override
    public void visit(MDBasicType mdType) {
        if (!parsedTypes.containsKey(mdType)) {

            String name = MDNameExtractor.getName(mdType.getName());
            long size = mdType.getSize();
            long align = mdType.getAlign();
            long offset = mdType.getOffset();

            LLVMDebugBasicType.Kind kind;
            switch (mdType.getEncoding()) {
                case DW_ATE_ADDRESS:
                    kind = LLVMDebugBasicType.Kind.ADDRESS;
                    break;
                case DW_ATE_BOOLEAN:
                    kind = LLVMDebugBasicType.Kind.BOOLEAN;
                    break;
                case DW_ATE_FLOAT:
                    kind = LLVMDebugBasicType.Kind.FLOATING;
                    break;
                case DW_ATE_SIGNED:
                    kind = LLVMDebugBasicType.Kind.SIGNED;
                    break;
                case DW_ATE_SIGNED_CHAR:
                    kind = LLVMDebugBasicType.Kind.SIGNED_CHAR;
                    break;
                case DW_ATE_UNSIGNED:
                    kind = LLVMDebugBasicType.Kind.UNSIGNED;
                    break;
                case DW_ATE_UNSIGNED_CHAR:
                    kind = LLVMDebugBasicType.Kind.UNSIGNED_CHAR;
                    break;
                default:
                    kind = LLVMDebugBasicType.Kind.UNKNOWN;
                    break;
            }

            final LLVMDebugType type = new LLVMDebugBasicType(name, size, align, offset, kind);
            parsedTypes.put(mdType, type);
        }
    }

    @Override
    public void visit(MDCompositeType mdType) {
        if (!parsedTypes.containsKey(mdType)) {
            final long size = mdType.getSize();
            final long align = mdType.getAlign();
            final long offset = mdType.getOffset();

            switch (mdType.getTag()) {

                case DW_TAG_VECTOR_TYPE:
                case DW_TAG_ARRAY_TYPE: {
                    final LLVMDebugArrayLikeType type = new LLVMDebugArrayLikeType(size, align, offset);
                    parsedTypes.put(mdType, type);

                    final MDReference mdBaseType = mdType.getDerivedFrom();
                    mdBaseType.accept(this);
                    LLVMDebugType baseType = parsedTypes.get(mdBaseType);
                    if (baseType == null) {
                        baseType = LLVMDebugType.UNKNOWN_TYPE;
                    }

                    final List<LLVMDebugType> members = new ArrayList<>(1);
                    getElements(mdType.getMemberDescriptors(), members);

                    for (int i = members.size() - 1; i > 0; i--) {
                        final LLVMDebugType count = members.get(i);
                        final long tmpSize = count.getSize() * baseType.getSize(); // TODO alignment
                        final LLVMDebugArrayLikeType tmp = new LLVMDebugArrayLikeType(tmpSize, align, 0L);

                        if (COUNT_NAME.equals(count.getName())) {
                            tmp.setLength(count.getSize());
                            final LLVMDebugType finalBaseType = baseType;
                            tmp.setBaseType(() -> finalBaseType);
                            if (mdType.getTag() == MDCompositeType.Tag.DW_TAG_VECTOR_TYPE) {
                                tmp.setName(() -> String.format("%s<%d>", finalBaseType.getName(), tmp.getLength()));
                            } else {
                                tmp.setName(() -> String.format("%s[%d]", finalBaseType.getName(), tmp.getLength()));
                            }
                        } else {
                            tmp.setLength(0);
                        }
                        baseType = tmp;
                    }

                    final LLVMDebugType count = members.get(0);
                    if (COUNT_NAME.equals(count.getName())) {
                        type.setLength(count.getSize());
                        final LLVMDebugType finalBaseType = baseType;
                        type.setBaseType(() -> finalBaseType);
                        if (mdType.getTag() == MDCompositeType.Tag.DW_TAG_VECTOR_TYPE) {
                            type.setName(() -> String.format("%s<%d>", finalBaseType.getName(), type.getLength()));
                        } else {
                            type.setName(() -> String.format("%s[%d]", finalBaseType.getName(), type.getLength()));
                        }

                    } else {
                        type.setLength(0);
                    }

                    break;
                }

                case DW_TAG_CLASS_TYPE:
                case DW_TAG_UNION_TYPE:
                case DW_TAG_STRUCTURE_TYPE: {
                    final LLVMDebugStructLikeType type = new LLVMDebugStructLikeType(size, align, offset);
                    final String parsedName = MDNameExtractor.getName(mdType.getName());

                    if (mdType.getTag() == MDCompositeType.Tag.DW_TAG_CLASS_TYPE) {
                        type.setName(() -> parsedName);
                    } else if (mdType.getTag() == MDCompositeType.Tag.DW_TAG_STRUCTURE_TYPE) {
                        type.setName(() -> String.format("struct %s", parsedName));
                    } else {
                        type.setName(() -> String.format("union %s", parsedName));
                    }

                    parsedTypes.put(mdType, type);

                    final List<LLVMDebugType> members = new ArrayList<>();
                    getElements(mdType.getMemberDescriptors(), members);
                    for (final LLVMDebugType member : members) {
                        if (member instanceof LLVMDebugMemberType) {
                            type.addMember((LLVMDebugMemberType) member);

                        } else {
                            // we should never get here because the offsets will be wrong, but this
                            // is still better than crashing outright and for testing it at least
                            // does not fail silently
                            final LLVMDebugMemberType namedMember = new LLVMDebugMemberType("<unknown>", member.getSize(), member.getAlign(), member.getOffset());
                            namedMember.setElementType(member);
                            type.addMember(namedMember);
                        }
                    }
                    break;
                }

                case DW_TAG_ENUMERATION_TYPE: {
                    final String parsedName = MDNameExtractor.getName(mdType.getName());
                    final LLVMDebugEnumLikeType type = new LLVMDebugEnumLikeType(() -> String.format("enum %s", parsedName), size, align, offset);
                    parsedTypes.put(mdType, type);

                    final List<LLVMDebugType> members = new ArrayList<>();
                    getElements(mdType.getMemberDescriptors(), members);
                    for (final LLVMDebugType member : members) {
                        type.addValue((int) member.getOffset(), member.getName());
                    }
                    break;
                }

                default:
                    // TODO parse other kinds and remove this
                    parsedTypes.put(mdType, LLVMDebugType.UNKNOWN_TYPE);
            }
        }
    }

    @Override
    public void visit(MDDerivedType mdType) {
        if (!parsedTypes.containsKey(mdType)) {
            long size = mdType.getSize();
            long align = mdType.getAlign();
            long offset = mdType.getOffset();

            switch (mdType.getTag()) {

                case DW_TAG_MEMBER: {
                    final String name = MDNameExtractor.getName(mdType.getName());
                    final LLVMDebugMemberType type = new LLVMDebugMemberType(name, size, align, offset);
                    parsedTypes.put(mdType, type);

                    final MDReference mdBaseType = mdType.getBaseType();
                    mdBaseType.accept(this);
                    LLVMDebugType baseType = parsedTypes.get(mdBaseType);
                    if (baseType == null) {
                        baseType = LLVMDebugType.UNKNOWN_TYPE;
                    }
                    type.setElementType(baseType);
                    break;
                }

                case DW_TAG_POINTER_TYPE: {
                    final LLVMDebugPointerType type = new LLVMDebugPointerType(size, align, offset);
                    parsedTypes.put(mdType, type);

                    final MDReference mdBaseType = mdType.getBaseType();
                    mdBaseType.accept(this);
                    LLVMDebugType baseType = parsedTypes.get(mdBaseType);
                    if (baseType == null) {
                        baseType = LLVMDebugType.UNKNOWN_TYPE;
                    }
                    final LLVMDebugType finalBaseType = baseType; // to be used in lambdas
                    type.setBaseType(() -> finalBaseType);
                    type.setName(() -> String.format("*%s", finalBaseType.getName()));
                    break;
                }

                case DW_TAG_TYPEDEF:
                case DW_TAG_VOLATILE_TYPE:
                case DW_TAG_CONST_TYPE: {
                    final Function<String, String> decorator;
                    switch (mdType.getTag()) {
                        case DW_TAG_VOLATILE_TYPE:
                            decorator = s -> String.format("volatile %s", s);
                            break;
                        case DW_TAG_CONST_TYPE:
                            decorator = s -> String.format("const %s", s);
                            break;
                        case DW_TAG_TYPEDEF: {
                            final String name = MDNameExtractor.getName(mdType.getName());
                            decorator = s -> name;
                            break;
                        }
                        default:
                            decorator = Function.identity();
                    }
                    final LLVMDebugDecoratorType type = new LLVMDebugDecoratorType(size, align, offset, decorator);
                    parsedTypes.put(mdType, type);
                    final MDReference mdBaseType = mdType.getBaseType();
                    mdBaseType.accept(this);
                    LLVMDebugType baseType = parsedTypes.get(mdBaseType);
                    if (baseType == null) {
                        baseType = LLVMDebugType.UNKNOWN_TYPE;
                    }
                    final LLVMDebugType finalBaseType = baseType; // to be used in lambdas
                    type.setBaseType(() -> finalBaseType);
                    break;
                }

                case DW_TAG_INHERITANCE: {
                    final LLVMDebugMemberType type = new LLVMDebugMemberType("super" + mdType.toString(), size, align, offset);
                    parsedTypes.put(mdType, type);

                    final MDReference mdBaseType = mdType.getBaseType();
                    mdBaseType.accept(this);
                    final LLVMDebugType baseType = parsedTypes.getOrDefault(mdBaseType, LLVMDebugType.UNKNOWN_TYPE);
                    type.setElementType(baseType);
                    type.setName(() -> String.format("super (%s)", baseType.getName()));

                    break;
                }

                default:
                    // TODO parse other kinds and remove this
                    parsedTypes.put(mdType, LLVMDebugType.UNKNOWN_TYPE);
            }
        }
    }

    @Override
    public void visit(MDReference mdRef) {
        if (mdRef != MDReference.VOID) {
            if (!parsedTypes.containsKey(mdRef)) {
                final MDBaseNode target = mdRef.get();
                target.accept(this);
                final LLVMDebugType parsedType = parsedTypes.get(target);
                if (parsedType != null) {
                    parsedTypes.put(mdRef, parsedType);
                }
            }
        }
    }

    @Override
    public void visit(MDSubrange mdRange) {
        // for array types the member descriptors contain this as the only element
        parsedTypes.put(mdRange, new IntermediaryType(() -> COUNT_NAME, mdRange.getSize(), 0L, 0L));
    }

    @Override
    public void visit(MDEnumerator mdEnumElement) {
        final String representation = MDNameExtractor.getName(mdEnumElement.getName());
        final long id = mdEnumElement.getValue();
        parsedTypes.put(mdEnumElement, new IntermediaryType(() -> representation, 0, 0, id));
    }

    @Override
    public void visit(MDNode mdTypeList) {
        for (MDBaseNode member : mdTypeList) {
            member.accept(this);
        }
    }

    @Override
    public void visit(MDGlobalVariable mdGlobal) {
        if (!parsedTypes.containsKey(mdGlobal)) {
            final MDReference typeRef = mdGlobal.getType();
            typeRef.accept(this);
            final LLVMDebugType type = parsedTypes.get(typeRef);
            if (type != null) {
                parsedTypes.put(mdGlobal, type);
            }
        }
    }

    @Override
    public void visit(MDLocalVariable mdLocal) {
        if (!parsedTypes.containsKey(mdLocal)) {
            final MDReference typeRef = mdLocal.getType();
            typeRef.accept(this);
            final LLVMDebugType type = parsedTypes.get(typeRef);
            if (type != null) {
                parsedTypes.put(mdLocal, type);
            }
        }
    }

    private void getElements(MDReference elemRef, List<LLVMDebugType> elemTypes) {
        if (elemRef == MDReference.VOID) {
            return;
        }

        MDBaseNode elemList = elemRef.get();
        if (elemList instanceof MDNode) {
            MDNode elemListNode = (MDNode) elemList;
            for (MDBaseNode elemNode : elemListNode) {
                if (elemNode != MDReference.VOID && elemNode instanceof MDReference) {
                    elemNode.accept(this);
                    final LLVMDebugType elemType = parsedTypes.get(((MDReference) elemNode).get());
                    if (elemType != LLVMDebugType.UNKNOWN_TYPE) {
                        elemTypes.add(elemType);
                    }
                }
            }
        } else if (elemList instanceof MDOldNode) {
            MDOldNode elemListNode = (MDOldNode) elemList;
            for (MDTypedValue elemNode : elemListNode) {
                if (elemNode != MDReference.VOID && elemNode instanceof MDReference) {
                    MDReference elementReference = (MDReference) elemNode;
                    elementReference.accept(this);
                    final LLVMDebugType elemType = parsedTypes.get(elementReference.get());
                    if (elemType != LLVMDebugType.UNKNOWN_TYPE) {
                        elemTypes.add(elemType);
                    }
                }
            }
        }
    }

    private static final class IntermediaryType extends LLVMDebugType {

        IntermediaryType(Supplier<String> nameSupplier, long size, long align, long offset) {
            super(nameSupplier, size, align, offset);
        }

        @Override
        public LLVMDebugType getOffset(long newOffset) {
            return this;
        }
    }
}
