/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

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
import com.oracle.truffle.llvm.parser.metadata.MDSubprogram;
import com.oracle.truffle.llvm.parser.metadata.MDSubrange;
import com.oracle.truffle.llvm.parser.metadata.MDSubroutine;
import com.oracle.truffle.llvm.parser.metadata.MDType;
import com.oracle.truffle.llvm.parser.metadata.MDValue;
import com.oracle.truffle.llvm.parser.metadata.MDVoidNode;
import com.oracle.truffle.llvm.parser.metadata.MetadataValueList;
import com.oracle.truffle.llvm.parser.metadata.MetadataVisitor;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceArrayLikeType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceBasicType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceDecoratorType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceEnumLikeType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceMemberType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourcePointerType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceStaticMemberType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceStructLikeType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

import static com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType.UNKNOWN;

final class DITypeExtractor implements MetadataVisitor {

    private static final String COUNT_NAME = "<count>";

    LLVMSourceType parseType(MDBaseNode mdType) {
        if (mdType == null || mdType == MDVoidNode.INSTANCE) {
            return null;
        }
        return resolve(mdType);
    }

    private final Map<MDBaseNode, LLVMSourceType> parsedTypes = new HashMap<>();
    private final Map<LLVMSourceStaticMemberType, SymbolImpl> staticMembers;

    private final DIScopeBuilder scopeBuilder;
    private final MetadataValueList metadata;

    DITypeExtractor(DIScopeBuilder scopeBuilder, MetadataValueList metadata, Map<LLVMSourceStaticMemberType, SymbolImpl> staticMembers) {
        this.scopeBuilder = scopeBuilder;
        this.metadata = metadata;
        this.staticMembers = staticMembers;
    }

    @Override
    public void defaultAction(MDBaseNode md) {
        parsedTypes.put(md, UNKNOWN);
    }

    private LLVMSourceType resolve(MDBaseNode node, LLVMSourceType defaultValue) {
        final LLVMSourceType resolved = resolve(node);
        return resolved != UNKNOWN ? resolved : defaultValue;
    }

    private LLVMSourceType resolve(MDBaseNode node) {
        LLVMSourceType parsedType = parsedTypes.get(node);
        if (parsedType != null) {
            return parsedType;
        }

        node.accept(this);
        parsedType = parsedTypes.get(node);
        return parsedType != null ? parsedType : UNKNOWN;
    }

    @Override
    public void visit(MDBasicType mdType) {
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

        final LLVMSourceLocation location = scopeBuilder.buildLocation(mdType);
        final LLVMSourceType type = new LLVMSourceBasicType(name, size, align, offset, kind, location);
        parsedTypes.put(mdType, type);
    }

    @Override
    public void visit(MDCompositeType mdType) {
        final long size = mdType.getSize();
        final long align = mdType.getAlign();
        final long offset = mdType.getOffset();
        final LLVMSourceLocation location = scopeBuilder.buildLocation(mdType);

        switch (mdType.getTag()) {

            case DW_TAG_VECTOR_TYPE:
            case DW_TAG_ARRAY_TYPE: {
                final boolean isVector = mdType.getTag() == MDType.DwarfTag.DW_TAG_VECTOR_TYPE;
                final LLVMSourceArrayLikeType type = new LLVMSourceArrayLikeType(size, align, offset, location);
                parsedTypes.put(mdType, type);

                LLVMSourceType baseType = resolve(mdType.getBaseType());

                final List<LLVMSourceType> members = new ArrayList<>(1);
                getElements(mdType.getMembers(), members, false);

                for (int i = members.size() - 1; i > 0; i--) {
                    final long length = extractLength(members.get(i));
                    final long tmpSize = length * baseType.getSize();
                    final LLVMSourceArrayLikeType tmp = new LLVMSourceArrayLikeType(tmpSize >= 0 ? tmpSize : 0, align, 0L, location);
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
                if (mdType.getTag() == MDType.DwarfTag.DW_TAG_STRUCTURE_TYPE) {
                    name = String.format("struct %s", name);
                } else if (mdType.getTag() == MDType.DwarfTag.DW_TAG_UNION_TYPE) {
                    name = String.format("union %s", name);
                }

                final LLVMSourceStructLikeType type = new LLVMSourceStructLikeType(name, size, align, offset, location);
                parsedTypes.put(mdType, type);

                final List<LLVMSourceType> members = new ArrayList<>();
                getElements(mdType.getMembers(), members, false);
                for (final LLVMSourceType member : members) {
                    if (member instanceof LLVMSourceMemberType) {
                        type.addDynamicMember((LLVMSourceMemberType) member);

                    } else if (member instanceof LLVMSourceStaticMemberType) {
                        type.addStaticMember((LLVMSourceStaticMemberType) member);
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

            default: {
                parsedTypes.put(mdType, LLVMSourceType.UNKNOWN);
            }
        }
    }

    private static long extractLength(LLVMSourceType count) {
        return COUNT_NAME.equals(count.getName()) ? count.getSize() : -1L;
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
                String baseName = baseType.getName();
                if (baseName.contains(" ")) {
                    baseName = String.format("(%s)", baseName);
                }
                return String.format(nameFormatString, baseName, length);
            }
        });
    }

    @Override
    public void visit(MDSubroutine mdSubroutine) {
        final List<LLVMSourceType> members = new ArrayList<>();
        final LLVMSourceFunctionType type = new LLVMSourceFunctionType(members);
        parsedTypes.put(mdSubroutine, type);
        getElements(mdSubroutine.getTypes(), members, true);
    }

    @Override
    public void visit(MDDerivedType mdType) {
        final long size = mdType.getSize();
        final long align = mdType.getAlign();
        final long offset = mdType.getOffset();
        final LLVMSourceLocation location = scopeBuilder.buildLocation(mdType);

        switch (mdType.getTag()) {

            case DW_TAG_MEMBER: {
                if (Flags.ARTIFICIAL.isAllFlags(mdType.getFlags())) {
                    parsedTypes.put(mdType, LLVMSourceType.VOID);
                    break;
                }

                final String name = MDNameExtractor.getName(mdType.getName());

                if (Flags.STATIC_MEMBER.isSetIn(mdType.getFlags())) {
                    final LLVMSourceStaticMemberType type = new LLVMSourceStaticMemberType(name, size, align, location);
                    parsedTypes.put(mdType, type);
                    final LLVMSourceType baseType = resolve(mdType.getBaseType());
                    type.setElementType(baseType);

                    if (mdType.getExtraData() instanceof MDValue) {
                        staticMembers.put(type, ((MDValue) mdType.getExtraData()).getValue());
                    }

                    break;
                }

                final LLVMSourceMemberType type = new LLVMSourceMemberType(name, size, align, offset, location);
                parsedTypes.put(mdType, type);

                LLVMSourceType baseType = resolve(mdType.getBaseType());
                if (Flags.BITFIELD.isSetIn(mdType.getFlags()) || (baseType != UNKNOWN && baseType.getSize() != size)) {
                    final LLVMSourceDecoratorType decorator = new LLVMSourceDecoratorType(size, align, offset, Function.identity(), location);
                    decorator.setBaseType(baseType);
                    baseType = decorator;
                }
                type.setElementType(baseType);
                break;
            }

            case DW_TAG_REFERENCE_TYPE:
            case DW_TAG_POINTER_TYPE: {
                final boolean isSafeToDereference = Flags.OBJECT_POINTER.isSetIn(mdType.getFlags());
                final boolean isReference = mdType.getTag() == MDType.DwarfTag.DW_TAG_REFERENCE_TYPE;
                final LLVMSourcePointerType type = new LLVMSourcePointerType(size, align, offset, isSafeToDereference, isReference, location);
                parsedTypes.put(mdType, type);

                // LLVM does not specify a void type, if this is indicated, the reference is simply
                // null
                final LLVMSourceType baseType = resolve(mdType.getBaseType(), LLVMSourceType.VOID);
                type.setBaseType(baseType);
                type.setName(() -> {
                    final String baseName = baseType.getName();
                    final String sym = isReference ? " &" : "*";
                    if (!baseType.isPointer() && baseName.contains(" ")) {
                        return String.format("(%s)%s", baseName, sym);
                    } else {
                        return String.format("%s%s", baseName, sym);
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

            default: {
                parsedTypes.put(mdType, LLVMSourceType.UNKNOWN);
            }
        }
    }

    @Override
    public void visit(MDSubrange mdRange) {
        // for array types the member descriptors contain this as the only element
        Long countValue = LLVMSymbolReadResolver.evaluateLongIntegerConstant(MDValue.getIfInstance(mdRange.getCount()));
        if (countValue == null) {
            // in llvm 7, the count can point to an MDLocal. the first release of llvm 7 contains a
            // bug in which this MDLocal is anonymous and does not point to an actual value.
            // starting with llvm 7, the count can also be a run-time value if it is not known at
            // compile-time, we do not support that feature and instead indicate to the user that we
            // do not know the actual value and therefore cannot display the members of the array
            countValue = -1L;
        }
        parsedTypes.put(mdRange, new IntermediaryType(() -> COUNT_NAME, countValue, 0L, 0L));
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
            resolve(member);
        }
    }

    @Override
    public void visit(MDString mdString) {
        final MDCompositeType referencedType = metadata.identifyType(mdString.getString());
        if (referencedType != null) {
            final LLVMSourceType type = resolve(referencedType);
            parsedTypes.put(mdString, type);
        }
    }

    @Override
    public void visit(MDGlobalVariable mdGlobal) {
        final LLVMSourceType type = resolve(mdGlobal.getType());
        parsedTypes.put(mdGlobal, type);

        if (mdGlobal.getStaticMemberDeclaration() != MDVoidNode.INSTANCE && mdGlobal.getVariable() instanceof MDValue) {
            final LLVMSourceType declType = resolve(mdGlobal.getStaticMemberDeclaration());
            final SymbolImpl symbol = ((MDValue) mdGlobal.getVariable()).getValue();
            if (declType instanceof LLVMSourceStaticMemberType) {
                staticMembers.put((LLVMSourceStaticMemberType) declType, symbol);
            }
        }
    }

    @Override
    public void visit(MDGlobalVariableExpression mdGlobalExpression) {
        final LLVMSourceType type = resolve(mdGlobalExpression.getGlobalVariable());
        parsedTypes.put(mdGlobalExpression, type);
    }

    @Override
    public void visit(MDLocalVariable mdLocal) {
        LLVMSourceType type = resolve(mdLocal.getType());
        if (Flags.OBJECT_POINTER.isSetIn(mdLocal.getFlags()) && type instanceof LLVMSourcePointerType) {
            // llvm does not set the objectpointer flag on this pointer type even though it sets
            // it on the pointer type that is used in the function type descriptor
            final LLVMSourcePointerType oldPointer = (LLVMSourcePointerType) type;
            final LLVMSourcePointerType newPointer = new LLVMSourcePointerType(oldPointer.getSize(), oldPointer.getAlign(), oldPointer.getOffset(), true, oldPointer.isReference(),
                            type.getLocation());
            newPointer.setBaseType(oldPointer.getBaseType());
            newPointer.setName(oldPointer::getName);
            type = newPointer;
        }
        parsedTypes.put(mdLocal, type);
    }

    @Override
    public void visit(MDSubprogram mdSubprogram) {
        parsedTypes.put(mdSubprogram, resolve(mdSubprogram.getType()));
    }

    @Override
    public void visit(MDVoidNode md) {
        parsedTypes.put(md, LLVMSourceType.VOID);
    }

    private void getElements(MDBaseNode elemList, List<LLVMSourceType> elemTypes, boolean includeUnknowns) {
        if (elemList instanceof MDNode) {
            final MDNode elemListNode = (MDNode) elemList;
            for (MDBaseNode elemNode : elemListNode) {
                final LLVMSourceType elemType = resolve(elemNode);
                if (elemType != UNKNOWN || includeUnknowns) {
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
