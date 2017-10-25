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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.parser.metadata.Flags;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDBasicType;
import com.oracle.truffle.llvm.parser.metadata.MDCompositeType;
import com.oracle.truffle.llvm.parser.metadata.MDDerivedType;
import com.oracle.truffle.llvm.parser.metadata.MDEnumerator;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDGlobalVariableExpression;
import com.oracle.truffle.llvm.parser.metadata.MDLocalVariable;
import com.oracle.truffle.llvm.parser.metadata.MDNode;
import com.oracle.truffle.llvm.parser.metadata.MDString;
import com.oracle.truffle.llvm.parser.metadata.MDSubrange;
import com.oracle.truffle.llvm.parser.metadata.MDSubroutine;
import com.oracle.truffle.llvm.parser.metadata.MDVoidNode;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceArrayLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceBasicType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceDecoratorType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceEnumLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceMemberType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourcePointerType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceStructLikeType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.oracle.truffle.llvm.runtime.debug.LLVMSourceType.UNKNOWN_TYPE;
import static com.oracle.truffle.llvm.runtime.debug.LLVMSourceType.VOID_TYPE;

final class DITypeExtractor implements MetadataVisitor {

    private static final String COUNT_NAME = "<count>";

    LLVMSourceType parseType(MDBaseNode mdType) {
        if (mdType == null || mdType == MDVoidNode.INSTANCE) {
            return null;
        }
        return resolve(mdType);
    }

    private final Map<MDBaseNode, LLVMSourceType> parsedTypes = new HashMap<>();

    private final DIScopeExtractor scopeExtractor;
    private final DITypeIdentifier typeIdentifier;

    DITypeExtractor(DIScopeExtractor scopeExtractor, DITypeIdentifier typeIdentifier) {
        this.scopeExtractor = scopeExtractor;
        this.typeIdentifier = typeIdentifier;
    }

    @Override
    public void defaultAction(MDBaseNode md) {
        parsedTypes.put(md, UNKNOWN_TYPE);
    }

    private LLVMSourceType resolve(MDBaseNode node, LLVMSourceType defaultValue) {
        final LLVMSourceType resolved = resolve(node);
        return resolved != UNKNOWN_TYPE ? resolved : defaultValue;
    }

    private LLVMSourceType resolve(MDBaseNode node) {
        LLVMSourceType parsedType = parsedTypes.get(node);
        if (parsedType != null) {
            return parsedType;
        }

        node.accept(this);
        parsedType = parsedTypes.get(node);
        return parsedType != null ? parsedType : UNKNOWN_TYPE;
    }

    @Override
    public void visit(MDBasicType mdType) {
        if (parsedTypes.containsKey(mdType)) {
            return;
        }

        final String name = MDNameExtractor.getName(mdType.getName());
        final long size = mdType.getSize();
        final long align = mdType.getAlign();
        final long offset = mdType.getOffset();

        LLVMSourceBasicType.Kind kind;
        switch (mdType.getEncoding()) {
            case DW_ATE_ADDRESS:
                kind = LLVMSourceBasicType.Kind.ADDRESS;
                break;
            case DW_ATE_BOOLEAN:
                kind = LLVMSourceBasicType.Kind.BOOLEAN;
                break;
            case DW_ATE_FLOAT:
                kind = LLVMSourceBasicType.Kind.FLOATING;
                break;
            case DW_ATE_SIGNED:
                kind = LLVMSourceBasicType.Kind.SIGNED;
                break;
            case DW_ATE_SIGNED_CHAR:
                kind = LLVMSourceBasicType.Kind.SIGNED_CHAR;
                break;
            case DW_ATE_UNSIGNED:
                kind = LLVMSourceBasicType.Kind.UNSIGNED;
                break;
            case DW_ATE_UNSIGNED_CHAR:
                kind = LLVMSourceBasicType.Kind.UNSIGNED_CHAR;
                break;
            default:
                kind = LLVMSourceBasicType.Kind.UNKNOWN;
                break;
        }

        final LLVMSourceLocation location = scopeExtractor.resolve(mdType);
        final LLVMSourceType type = new LLVMSourceBasicType(name, size, align, offset, kind, location);
        parsedTypes.put(mdType, type);
    }

    @Override
    public void visit(MDCompositeType mdType) {
        if (parsedTypes.containsKey(mdType)) {
            return;
        }

        final long size = mdType.getSize();
        final long align = mdType.getAlign();
        final long offset = mdType.getOffset();
        final LLVMSourceLocation location = scopeExtractor.resolve(mdType);

        switch (mdType.getTag()) {

            case DW_TAG_VECTOR_TYPE:
            case DW_TAG_ARRAY_TYPE: {
                final boolean isVector = mdType.getTag() == MDCompositeType.Tag.DW_TAG_VECTOR_TYPE;
                final LLVMSourceArrayLikeType type = new LLVMSourceArrayLikeType(size, align, offset, location);
                parsedTypes.put(mdType, type);

                LLVMSourceType baseType = resolve(mdType.getBaseType());

                final List<LLVMSourceType> members = new ArrayList<>(1);
                getElements(mdType.getMembers(), members, false);

                for (int i = members.size() - 1; i > 0; i--) {
                    final long length = extractLength(members.get(i));
                    final long tmpSize = length * baseType.getSize();
                    final LLVMSourceArrayLikeType tmp = new LLVMSourceArrayLikeType(tmpSize, align, 0L, location);
                    setAggregateProperties(isVector, tmp, length, baseType);
                    baseType = tmp;
                }

                setAggregateProperties(isVector, type, extractLength(members.get(0)), baseType);

                break;
            }

            case DW_TAG_CLASS_TYPE:
            case DW_TAG_UNION_TYPE:
            case DW_TAG_STRUCTURE_TYPE: {
                String name = MDNameExtractor.getName(mdType.getName());
                if (mdType.getTag() == MDCompositeType.Tag.DW_TAG_STRUCTURE_TYPE) {
                    name = String.format("struct %s", name);
                } else if (mdType.getTag() == MDCompositeType.Tag.DW_TAG_UNION_TYPE) {
                    name = String.format("union %s", name);
                }

                final LLVMSourceStructLikeType type = new LLVMSourceStructLikeType(name, size, align, offset, location);
                parsedTypes.put(mdType, type);

                final List<LLVMSourceType> members = new ArrayList<>();
                getElements(mdType.getMembers(), members, false);
                for (final LLVMSourceType member : members) {
                    if (member instanceof LLVMSourceMemberType) {
                        type.addMember((LLVMSourceMemberType) member);

                    }
                }
                break;
            }

            case DW_TAG_ENUMERATION_TYPE: {
                final String name = String.format("enum %s", MDNameExtractor.getName(mdType.getName()));
                final LLVMSourceEnumLikeType type = new LLVMSourceEnumLikeType(() -> name, size, align, offset, location);
                parsedTypes.put(mdType, type);

                final List<LLVMSourceType> members = new ArrayList<>();
                getElements(mdType.getMembers(), members, false);
                for (final LLVMSourceType member : members) {
                    type.addValue((int) member.getOffset(), member.getName());
                }
                break;
            }

            default:
                parsedTypes.put(mdType, UNKNOWN_TYPE);
        }
    }

    private static long extractLength(LLVMSourceType count) {
        return COUNT_NAME.equals(count.getName()) ? count.getSize() : 0L;
    }

    private static void setAggregateProperties(boolean isVector, LLVMSourceArrayLikeType aggregate, long length, LLVMSourceType baseType) {
        aggregate.setBaseType(baseType);
        final String nameFormatString;
        if (length < 0) {
            // this case happens for dynamically allocated arrays
            aggregate.setLength(0);
            nameFormatString = isVector ? "%s<?>" : "%s[?]";
        } else {
            aggregate.setLength(length);
            nameFormatString = isVector ? "%s<%d>" : "%s[%d]";
        }
        aggregate.setName(new Supplier<String>() {
            @Override
            @TruffleBoundary
            public String get() {
                return String.format(nameFormatString, baseType.getName(), length);
            }
        });
    }

    @Override
    public void visit(MDSubroutine mdSubroutine) {
        if (parsedTypes.containsKey(mdSubroutine)) {
            return;
        }

        final LLVMSourceLocation location = scopeExtractor.resolve(mdSubroutine);
        final List<LLVMSourceType> members = new ArrayList<>();
        final LLVMSourceDecoratorType type = new LLVMSourceDecoratorType(0L, 0L, 0L, new Function<String, String>() {
            @Override
            @TruffleBoundary
            public String apply(String ignored) {
                if (members.isEmpty()) {
                    return "(void())";
                } else {
                    CompilerDirectives.transferToInterpreter();
                    final LLVMSourceType returnType = members.get(0);
                    final String returnTypeName = returnType != UNKNOWN_TYPE ? returnType.getName() : VOID_TYPE.getName();
                    return returnTypeName + members.subList(1, members.size()).stream().map(LLVMSourceType::getName).collect(Collectors.joining(", ", "(", ")"));
                }
            }
        }, location);
        parsedTypes.put(mdSubroutine, type);
        getElements(mdSubroutine.getTypes(), members, true);
    }

    @Override
    public void visit(MDDerivedType mdType) {
        if (parsedTypes.containsKey(mdType)) {
            return;
        }

        final long size = mdType.getSize();
        final long align = mdType.getAlign();
        final long offset = mdType.getOffset();
        final LLVMSourceLocation location = scopeExtractor.resolve(mdType);

        switch (mdType.getTag()) {

            case DW_TAG_MEMBER: {
                final String name = MDNameExtractor.getName(mdType.getName());
                final LLVMSourceMemberType type = new LLVMSourceMemberType(name, size, align, offset, location);
                parsedTypes.put(mdType, type);

                LLVMSourceType baseType = resolve(mdType.getBaseType());
                if (Flags.BITFIELD.isSetIn(mdType.getFlags()) || (baseType != UNKNOWN_TYPE && baseType.getSize() != size)) {
                    final LLVMSourceDecoratorType decorator = new LLVMSourceDecoratorType(size, align, offset, Function.identity(), location);
                    decorator.setBaseType(baseType);
                    baseType = decorator;
                }
                type.setElementType(baseType);
                break;
            }

            case DW_TAG_POINTER_TYPE: {
                final boolean isSafeToDereference = Flags.OBJECT_POINTER.isSetIn(mdType.getFlags());
                final LLVMSourcePointerType type = new LLVMSourcePointerType(size, align, offset, isSafeToDereference, location);
                parsedTypes.put(mdType, type);

                final LLVMSourceType baseType = resolve(mdType.getBaseType(), LLVMSourceType.VOID_TYPE);
                type.setBaseType(baseType);
                type.setName(() -> {
                    final String baseName = baseType.getName();
                    if (!baseType.isPointer() && baseName.contains(" ")) {
                        return String.format("(%s)*", baseName);
                    } else {
                        return String.format("%s*", baseName);
                    }
                });
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
                final LLVMSourceDecoratorType type = new LLVMSourceDecoratorType(size, align, offset, decorator, location);
                parsedTypes.put(mdType, type);

                final LLVMSourceType baseType = resolve(mdType.getBaseType());
                type.setBaseType(baseType);
                type.setSize(baseType.getSize());
                break;
            }

            case DW_TAG_INHERITANCE: {
                final LLVMSourceMemberType type = new LLVMSourceMemberType("super", size, align, offset, location);
                parsedTypes.put(mdType, type);
                final LLVMSourceType baseType = resolve(mdType.getBaseType());
                type.setElementType(baseType);
                type.setName(() -> String.format("super (%s)", baseType.getName()));

                break;
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
    public void visit(MDString mdString) {
        if (parsedTypes.containsKey(mdString)) {
            return;
        }

        final MDCompositeType referencedType = typeIdentifier.identify(mdString.getString());
        if (referencedType != null) {
            final LLVMSourceType type = resolve(referencedType);
            parsedTypes.put(mdString, type);
        }
    }

    @Override
    public void visit(MDGlobalVariable mdGlobal) {
        if (!parsedTypes.containsKey(mdGlobal)) {
            final LLVMSourceType type = resolve(mdGlobal.getType());
            parsedTypes.put(mdGlobal, type);
        }
    }

    @Override
    public void visit(MDGlobalVariableExpression mdGlobalExpression) {
        if (!parsedTypes.containsKey(mdGlobalExpression)) {
            final LLVMSourceType type = resolve(mdGlobalExpression.getGlobalVariable());
            parsedTypes.put(mdGlobalExpression, type);
        }
    }

    @Override
    public void visit(MDLocalVariable mdLocal) {
        if (!parsedTypes.containsKey(mdLocal)) {
            LLVMSourceType type = resolve(mdLocal.getType());
            if (type == UNKNOWN_TYPE) {
                return;

            } else if (Flags.OBJECT_POINTER.isSetIn(mdLocal.getFlags()) && type instanceof LLVMSourcePointerType) {
                // llvm does not set the objectpointer flag on this pointer type even though it sets
                // it on the pointer type that is used in the function type descriptor
                final LLVMSourcePointerType oldPointer = (LLVMSourcePointerType) type;
                final LLVMSourcePointerType newPointer = new LLVMSourcePointerType(oldPointer.getSize(), oldPointer.getAlign(), oldPointer.getOffset(), true, type.getLocation());
                newPointer.setBaseType(oldPointer.getBaseType());
                newPointer.setName(oldPointer::getName);
                type = newPointer;
            }
            parsedTypes.put(mdLocal, type);
        }
    }

    private void getElements(MDBaseNode elemList, List<LLVMSourceType> elemTypes, boolean includeUnknowns) {
        if (elemList instanceof MDNode) {
            final MDNode elemListNode = (MDNode) elemList;
            for (MDBaseNode elemNode : elemListNode) {
                final LLVMSourceType elemType = resolve(elemNode);
                if (elemType != UNKNOWN_TYPE || includeUnknowns) {
                    elemTypes.add(elemType);
                }
            }

        }
    }

    private static final class IntermediaryType extends LLVMSourceType {

        IntermediaryType(Supplier<String> nameSupplier, long size, long align, long offset) {
            super(nameSupplier, size, align, offset, null);
        }

        @Override
        public LLVMSourceType getOffset(long newOffset) {
            return this;
        }
    }
}
