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
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDNode;
import com.oracle.truffle.llvm.parser.metadata.MDReference;
import com.oracle.truffle.llvm.parser.metadata.MDSubrange;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MDTypeExtractor implements MetadataVisitor {

    void parseType(MDBaseNode mdType) {
        mdType.accept(this);
    }

    private final Map<MDBaseNode, DIType> parsedTypes = new HashMap<>();

    MDTypeExtractor() {
    }

    @Override
    public void ifVisitNotOverwritten(MDBaseNode md) {
        parsedTypes.put(md, DIType.UNKNOWN_TYPE);
    }

    @Override
    public void visit(MDBasicType mdType) {
        if (!parsedTypes.containsKey(mdType)) {

            String name = MDNameExtractor.getName(mdType.getName());
            long size = mdType.getSize();
            long align = mdType.getAlign();
            long offset = mdType.getOffset();

            DIBasicType.Kind kind;
            switch (mdType.getEncoding()) {
                case DW_ATE_ADDRESS:
                    kind = DIBasicType.Kind.ADDRESS;
                    break;
                case DW_ATE_BOOLEAN:
                    kind = DIBasicType.Kind.BOOLEAN;
                    break;
                case DW_ATE_FLOAT:
                    kind = DIBasicType.Kind.FLOATING;
                    break;
                case DW_ATE_SIGNED:
                    kind = DIBasicType.Kind.SIGNED;
                    break;
                case DW_ATE_SIGNED_CHAR:
                    kind = DIBasicType.Kind.SIGNED_CHAR;
                    break;
                case DW_ATE_UNSIGNED:
                    kind = DIBasicType.Kind.UNSIGNED;
                    break;
                case DW_ATE_UNSIGNED_CHAR:
                    kind = DIBasicType.Kind.UNSIGNED_CHAR;
                    break;
                default:
                    kind = DIBasicType.Kind.UNKNOWN;
                    break;
            }

            final DIType type = new DIBasicType(name, size, align, offset, kind);
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

                case DW_TAG_ARRAY_TYPE: {
                    final DIArrayLikeType type = new DIArrayLikeType(size, align, offset);
                    parsedTypes.put(mdType, type);

                    final MDReference mdBaseType = mdType.getDerivedFrom();
                    mdBaseType.accept(this);
                    DIType baseType = parsedTypes.get(mdBaseType);
                    if (baseType == null) {
                        baseType = DIType.UNKNOWN_TYPE;
                    }
                    final DIType finalBaseType = baseType; // to be used in lambdas
                    type.setBaseType(() -> finalBaseType);

                    final List<DIType> members = new ArrayList<>(1);
                    getElements(mdType.getMemberDescriptors(), members);
                    if (members.size() == 1 && DIType.COUNT_NAME.equals(members.get(0).getName())) {
                        type.setLength(members.get(0).getSize());
                    } else {
                        type.setLength(-1);
                    }

                    type.setName(() -> String.format("%s[%d]", finalBaseType.getName(), type.getLength()));
                    break;
                }

                case DW_TAG_STRUCTURE_TYPE: {
                    final DIStructLikeType type = new DIStructLikeType(size, align, offset);
                    final String parsedName = MDNameExtractor.getName(mdType.getName());
                    type.setName(() -> String.format("struct %s", parsedName));
                    parsedTypes.put(mdType, type);

                    final List<DIType> members = new ArrayList<>();
                    getElements(mdType.getMemberDescriptors(), members);
                    for (final DIType member : members) {
                        if (member instanceof DIMemberType) {
                            type.addMember((DIMemberType) member);

                        } else {
                            // we should never get here because the offsets will be wrong, but this
                            // is still better than crashing outright and for testing it at least
                            // does not fail silently
                            final DIMemberType namedMember = new DIMemberType("<unknown>", member.getSize(), member.getAlign(), member.getOffset());
                            namedMember.setElementType(member);
                            type.addMember(namedMember);
                        }
                    }
                    break;
                }

                default:
                    // TODO parse other kinds and remove this
                    parsedTypes.put(mdType, DIType.UNKNOWN_TYPE);
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
                    final DIMemberType type = new DIMemberType(name, size, align, offset);
                    parsedTypes.put(mdType, type);

                    final MDReference mdBaseType = mdType.getBaseType();
                    mdBaseType.accept(this);
                    DIType baseType = parsedTypes.get(mdBaseType);
                    if (baseType == null) {
                        baseType = DIType.UNKNOWN_TYPE;
                    }
                    type.setElementType(baseType);
                    break;
                }

                case DW_TAG_POINTER_TYPE: {
                    final DIPointerType type = new DIPointerType(size, align, offset);
                    parsedTypes.put(mdType, type);

                    final MDReference mdBaseType = mdType.getBaseType();
                    mdBaseType.accept(this);
                    DIType baseType = parsedTypes.get(mdBaseType);
                    if (baseType == null) {
                        baseType = DIType.UNKNOWN_TYPE;
                    }
                    final DIType finalBaseType = baseType; // to be used in lambdas
                    type.setBaseType(() -> finalBaseType);
                    type.setName(() -> String.format("*%s", finalBaseType.getName()));
                    break;
                }

                default:
                    // TODO parse other kinds and remove this
                    parsedTypes.put(mdType, DIType.UNKNOWN_TYPE);
            }
        }
    }

    @Override
    public void visit(MDReference mdRef) {
        if (mdRef != MDReference.VOID) {
            if (!parsedTypes.containsKey(mdRef)) {
                final MDBaseNode target = mdRef.get();
                target.accept(this);
                final DIType parsedType = parsedTypes.get(target);
                if (parsedType != null) {
                    parsedTypes.put(mdRef, parsedType);
                }
            }
        }
    }

    @Override
    public void visit(MDSubrange mdRange) {
        // for array types the member descriptors contain this as the only element
        parsedTypes.put(mdRange, new DIType(() -> DIType.COUNT_NAME, mdRange.getSize(), 0L, 0L));
    }

    @Override
    public void visit(MDNode mdTypeList) {
        for (MDBaseNode member : mdTypeList) {
            member.accept(this);
        }
    }

    @Override
    public void visit(MDGlobalVariable mdGlobal) {
        mdGlobal.getType().accept(this);
    }

    @Override
    public void visit(MDLocalVariable mdLocal) {
        mdLocal.getType().accept(this);
    }

    private void getElements(MDReference elemRef, List<DIType> elemTypes) {
        if (elemRef == MDReference.VOID) {
            return;
        }

        MDBaseNode elemList = elemRef.get();
        if (elemList instanceof MDNode) {
            MDNode elemListNode = (MDNode) elemList;
            for (MDBaseNode elemNode : elemListNode) {
                elemNode.accept(this);
                if (elemNode != MDReference.VOID && elemNode instanceof MDReference) {
                    elemTypes.add(parsedTypes.get(((MDReference) elemNode).get()));
                }
            }
        }
    }
}
