/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.debugging.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.collection.IntArrayList;
import org.graalvm.wasm.debugging.DebugLineMap;
import org.graalvm.wasm.debugging.parser.DebugParserContext;
import org.graalvm.wasm.debugging.parser.DebugParserScope;
import org.graalvm.wasm.debugging.data.objects.DebugConstantObject;
import org.graalvm.wasm.debugging.data.objects.DebugMember;
import org.graalvm.wasm.debugging.data.objects.DebugParameter;
import org.graalvm.wasm.debugging.data.objects.DebugVariable;
import org.graalvm.wasm.debugging.data.types.DebugArrayType;
import org.graalvm.wasm.debugging.data.types.DebugBaseType;
import org.graalvm.wasm.debugging.data.types.DebugEnumType;
import org.graalvm.wasm.debugging.data.types.DebugInheritance;
import org.graalvm.wasm.debugging.data.types.DebugPointerToMemberType;
import org.graalvm.wasm.debugging.data.types.DebugPointerType;
import org.graalvm.wasm.debugging.data.types.DebugStructType;
import org.graalvm.wasm.debugging.data.types.DebugSubroutineType;
import org.graalvm.wasm.debugging.data.types.DebugTypeDef;
import org.graalvm.wasm.debugging.data.types.DebugVariantType;
import org.graalvm.wasm.debugging.encoding.Attributes;
import org.graalvm.wasm.debugging.encoding.Tags;
import org.graalvm.wasm.debugging.parser.DebugData;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.wasm.debugging.representation.DebugConstantDisplayValue;

/**
 * Represents a factory that creates the internal representation of all types and objects in the
 * debug information.
 */
public abstract class DebugObjectFactory {
    private static final DebugObject[] EMPTY_OBJECTS = {};
    private static final DebugType[] EMPTY_TYPES = {};

    private final EconomicMap<Integer, DebugType> types = EconomicMap.create();
    private final EconomicMap<Integer, DebugObject> objects = EconomicMap.create();

    /**
     * @return The name of the source language.
     */
    public abstract String languageName();

    /**
     * @return The namespace separator used in the source language.
     */
    protected abstract String namespaceSeparator();

    protected DebugType createArrayType(String name, DebugType elementType, int[] dimensionLengths) {
        return new DebugArrayType(name, elementType, dimensionLengths);
    }

    protected DebugType createStructType(String name, DebugObject[] members, DebugType[] superTypes) {
        return new DebugStructType(name, members, superTypes);
    }

    protected DebugType createEnumType(String name, DebugType baseType, EconomicMap<Long, String> values) {
        return new DebugEnumType(name, baseType, values);
    }

    protected DebugObject createParameter(String name, DebugType type, byte[] locationExpression) {
        return new DebugParameter(name, type, locationExpression);
    }

    protected DebugObject createMember(String name, DebugType type, byte[] locationExpression, int offset, int bitOffset, int bitSize) {
        return new DebugMember(name, type, locationExpression, offset, bitOffset, bitSize);
    }

    protected DebugType createPointerType(DebugType baseType) {
        return new DebugPointerType(baseType);
    }

    protected DebugType createSubroutineType(String name, DebugType returnType, DebugType[] parameterTypes) {
        return new DebugSubroutineType(name, returnType, parameterTypes);
    }

    protected DebugType createTypeDef(String name, DebugType baseType) {
        return new DebugTypeDef(name, baseType);
    }

    protected DebugObject createUnspecifiedParameters() {
        return new DebugConstantObject("...", new DebugConstantDisplayValue("..."));
    }

    protected DebugType createVariantType(String name, DebugObject discriminant, EconomicMap<Long, DebugObject> values) {
        return new DebugVariantType(name, discriminant, values);
    }

    protected DebugType createBaseType(String name, int encoding, int byteSize, int bitOffset, int bitSize) {
        return new DebugBaseType(name, encoding, byteSize, bitOffset, bitSize);
    }

    @SuppressWarnings("unused")
    protected DebugType createQualifierType(int tag, String name, DebugType baseType) {
        return baseType;
    }

    protected DebugType createUnspecifiedType() {
        return DebugConstantObject.UNSPECIFIED;
    }

    private Optional<DebugType> parseArrayType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.tryAsString(Attributes.NAME).orElse(null);
        final Optional<? extends DebugType> elementTypeValue = parse(context, scope, data.asI32(Attributes.TYPE));
        if (elementTypeValue.isEmpty()) {
            return Optional.empty();
        }
        final DebugType elementType = elementTypeValue.get();
        final IntArrayList dimensionLengths = new IntArrayList();
        forEachChild(data, Tags.SUBRANGE_TYPE, s -> dimensionLengths.add(s.tryAsI32(Attributes.COUNT).orElse(0)));
        return Optional.of(createArrayType(name, elementType, dimensionLengths.toArray()));
    }

    private Optional<DebugType> parseStructType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.tryAsString(Attributes.NAME).orElse(null);
        final Optional<DebugData> variantPart = getChild(data, Tags.VARIANT_PART);
        if (variantPart.isPresent()) {
            return parseVariantType(context, scope, name, variantPart.get());
        }
        final List<DebugType> superTypes = new ArrayList<>();
        forEachChild(data, Tags.INHERITANCE, (i -> {
            final Optional<? extends DebugType> superType = parse(context, scope, i);
            superType.ifPresent(superTypes::add);
        }));
        final List<DebugObject> members = new ArrayList<>();
        forEachChild(data, Tags.MEMBER, (m -> {
            final Optional<? extends DebugObject> member = parseObject(context, scope, m);
            member.ifPresent(members::add);

        }));
        forEachChild(data, Tags.SUBPROGRAM, (s -> parse(context, scope, s)));
        return Optional.of(createStructType(name, members.toArray(EMPTY_OBJECTS), superTypes.toArray(EMPTY_TYPES)));
    }

    private Optional<DebugType> parseEnumType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.tryAsString(Attributes.NAME).orElse(null);
        final Optional<DebugType> baseType = data.tryAsI32(Attributes.TYPE).flatMap(t -> parse(context, scope, t));
        if (baseType.isEmpty()) {
            return Optional.empty();
        }
        final EconomicMap<Long, String> values = EconomicMap.create();
        forEachChild(data, Tags.ENUMERATOR, (e -> values.put(e.asI64(Attributes.CONST_VALUE), e.asString(Attributes.NAME))));
        return Optional.of(createEnumType(name, baseType.get(), values));
    }

    private Optional<DebugObject> parseFormalParameter(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.tryAsString(Attributes.NAME).orElse(null);
        final Optional<? extends DebugType> type = parse(context, scope, data.asI32(Attributes.TYPE));
        if (type.isEmpty()) {
            return Optional.empty();
        }
        byte[] locationExpression = data.tryAsByteArray(Attributes.LOCATION).orElse(null);
        if (locationExpression == null) {
            locationExpression = data.tryAsI32(Attributes.LOCATION).map(context::readLocationList).orElse(null);
        }
        final DebugObject parameter = createParameter(name, type.get(), locationExpression);
        if (name != null) {
            scope.addVariable(parameter);
        }
        return Optional.of(parameter);
    }

    private Optional<DebugType> parseLexicalBlock(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final DebugParserScope blockScope;
        if (data.hasAttribute(Attributes.LOW_PC)) {
            final int[] pcs = DebugDataUtil.readPcs(data, context);
            blockScope = scope.with(null, pcs[0], pcs[1]);
        } else {
            blockScope = scope;
        }
        parseChildren(context, blockScope, data);
        return Optional.empty();
    }

    private Optional<? extends DebugType> parseImport(DebugParserContext context, DebugParserScope scope, DebugData data) {
        return parse(context, scope, data.asI32(Attributes.IMPORT));
    }

    private Optional<DebugObject> parseMember(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.tryAsString(Attributes.NAME).orElse(null);
        final Optional<? extends DebugType> type = parse(context, scope, data.asI32(Attributes.TYPE));
        if (type.isEmpty()) {
            return Optional.empty();
        }
        final byte[] locationExpression = data.tryAsByteArray(Attributes.DATA_MEMBER_LOCATION).orElse(null);
        final int offset = data.tryAsI32(Attributes.DATA_MEMBER_LOCATION).orElse(0);
        final int bitOffset = data.tryAsI32(Attributes.BIT_OFFSET).orElse(-1);
        final int bitSize = data.tryAsI32(Attributes.BIT_SIZE).orElse(-1);

        return Optional.of(createMember(name, type.get(), locationExpression, offset, bitOffset, bitSize));
    }

    private Optional<DebugType> parsePointerType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final Optional<? extends DebugType> type = data.tryAsI32(Attributes.TYPE).flatMap(t -> parse(context, scope, t));
        return type.map(this::createPointerType);
    }

    private Optional<DebugType> parseSubroutineType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.tryAsString(Attributes.NAME).orElse(null);
        final DebugType returnType = data.tryAsI32(Attributes.TYPE).flatMap(t -> parse(context, scope, t)).orElse(null);
        final List<DebugType> parameterTypes = new ArrayList<>();
        for (DebugData child : data.children()) {
            final int tag = child.tag();
            if (tag == Tags.FORMAL_PARAMETER || tag == Tags.UNSPECIFIED_PARAMETERS) {
                final Optional<? extends DebugType> param = parse(context, scope, child);
                param.ifPresent(parameterTypes::add);
            }
        }
        return Optional.of(createSubroutineType(name, returnType, parameterTypes.toArray(EMPTY_TYPES)));
    }

    private Optional<DebugType> parseTypeDef(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.asString(Attributes.NAME);
        final DebugType baseType = data.tryAsI32(Attributes.TYPE).flatMap(t -> parse(context, scope, t)).orElse(null);
        return Optional.of(createTypeDef(name, baseType));
    }

    private Optional<DebugType> parseVariantType(DebugParserContext context, DebugParserScope scope, String name, DebugData data) {
        final Optional<DebugObject> discriminantValue = data.tryAsI32(Attributes.DISCR).flatMap(d -> parseObject(context, scope, d));
        final DebugObject discriminant;
        if (discriminantValue.isPresent()) {
            discriminant = discriminantValue.get();
        } else {
            final Optional<? extends DebugObject> type = data.tryAsI32(Attributes.TYPE).flatMap(t -> parseObject(context, scope, t));
            if (type.isEmpty()) {
                return Optional.empty();
            }
            discriminant = type.get();
        }
        final EconomicMap<Long, DebugObject> values = EconomicMap.create();
        final DebugParserScope variantScope = scope.with(name);
        forEachChild(data, Tags.VARIANT, (v -> {
            final Optional<Long> discValue = v.tryAsI64(Attributes.DISCR_VALUE);
            if (discValue.isEmpty()) {
                return;
            }
            Optional<? extends DebugObject> entry = Optional.empty();
            for (DebugData child : v.children()) {
                entry = parseObject(context, variantScope, child);
            }
            if (entry.isEmpty()) {
                return;
            }
            values.put(discValue.get(), entry.get());
        }));
        return Optional.of(createVariantType(name, discriminant, values));
    }

    private Optional<DebugType> parseInheritance(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final Optional<DebugType> referenceType = data.tryAsI32(Attributes.TYPE).flatMap(t -> parse(context, scope, t));
        if (referenceType.isEmpty()) {
            return Optional.empty();
        }
        final byte[] locationExpression = data.tryAsByteArray(Attributes.DATA_MEMBER_LOCATION).orElse(null);
        final int memberOffset = data.tryAsI32(Attributes.DATA_MEMBER_LOCATION).orElse(0);
        return Optional.of(new DebugInheritance(referenceType.get(), locationExpression, memberOffset));
    }

    private Optional<DebugType> parsePointerToMemberType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final DebugType memberType = data.tryAsI32(Attributes.TYPE).flatMap(t -> parse(context, scope, t)).orElse(null);
        final DebugType containingType = data.tryAsI32(Attributes.CONTAINING_TYPE).flatMap(t -> parse(context, scope, t)).orElse(null);
        return Optional.of(new DebugPointerToMemberType(memberType, containingType));
    }

    private Optional<DebugType> parseQualifierType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.tryAsString(Attributes.NAME).orElse(null);
        final Optional<DebugType> baseType = data.tryAsI32(Attributes.TYPE).flatMap(t -> parse(context, scope, t));
        return baseType.map(debugType -> createQualifierType(data.tag(), name, debugType));
    }

    private Optional<DebugType> parseBaseType(DebugData data) {
        final String name = data.asString(Attributes.NAME);
        final int encoding = data.asI32(Attributes.ENCODING);
        final int byteSize = data.tryAsI32(Attributes.BYTE_SIZE).orElse(1);
        final int bitSize = data.tryAsI32(Attributes.BIT_SIZE).orElse(-1);
        final int bitOffset = data.tryAsI32(Attributes.BIT_OFFSET).orElse(0);
        return Optional.of(createBaseType(name, encoding, byteSize, bitSize, bitOffset));
    }

    private Optional<DebugType> parseSubprogram(DebugParserContext context, DebugData data) {
        final Optional<Integer> specOffset = data.tryAsI32(Attributes.SPECIFICATION);
        final Optional<DebugData> specData = specOffset.flatMap(context::tryGetData);

        final Optional<Integer> fileIndexValue = specData.flatMap(s -> s.tryAsI32(Attributes.DECL_FILE));
        final int fileIndex = fileIndexValue.or(() -> data.tryAsI32(Attributes.DECL_FILE)).orElse(-1);

        final Optional<String> nameValue = specData.flatMap(s -> s.tryAsString(Attributes.NAME));
        final String name = nameValue.or(() -> data.tryAsString(Attributes.NAME)).orElse(null);

        final Optional<DebugLineMap> debugLineMap = context.tryGetLineMap(fileIndex);
        if (debugLineMap.isEmpty()) {
            return Optional.empty();
        }
        final DebugLineMap lineMap = debugLineMap.get();

        if (data.exists(Attributes.DECLARATION)) {
            return Optional.empty();
        }
        if (data.tryAsI32(Attributes.INLINE).map(i -> i == 1 || i == 3).orElse(false)) {
            return Optional.empty();
        }

        final int[] pcs = DebugDataUtil.readPcs(data, context);
        final int scopeStartPc = pcs[0];
        final int scopeEndPc = pcs[1];
        final int startLine = lineMap.getLine(scopeStartPc);

        final Optional<Source> source = context.tryGetSource(fileIndex);
        final SourceSection sourceSection = source.map(s -> s.createSection(startLine)).orElse(null);

        final byte[] frameBaseExpression = data.asByteArray(Attributes.FRAME_BASE);

        final DebugParserScope functionScope = DebugParserScope.createFunctionScope(scopeStartPc, scopeEndPc, fileIndex);
        parseChildren(context, functionScope, data);
        final List<DebugObject> globals = context.globals();
        final List<DebugObject> variables = functionScope.variables();
        final DebugFunction function = new DebugFunction(name, lineMap, sourceSection, frameBaseExpression, variables, globals);
        if (name != null) {
            context.addFunction(scopeStartPc, function);
        }
        return Optional.of(function);
    }

    private Optional<DebugObject> parseVariable(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final Optional<Integer> specOffset = data.tryAsI32(Attributes.SPECIFICATION);
        final Optional<DebugData> specData = specOffset.flatMap(context::tryGetData);

        final Optional<String> nameValue = specData.flatMap(s -> s.tryAsString(Attributes.NAME));
        final String variableName = nameValue.orElseGet(() -> data.tryAsString(Attributes.NAME).orElse(null));
        final String name = scope.name().map(s -> s + namespaceSeparator() + variableName).orElse(variableName);

        final Optional<DebugType> specTypeValue = specData.flatMap(s -> s.tryAsI32(Attributes.TYPE)).flatMap(t -> parse(context, scope, t));
        final Optional<DebugType> typeValue = specTypeValue.or(() -> data.tryAsI32(Attributes.TYPE).flatMap(t -> parse(context, scope, t)));
        if (typeValue.isEmpty()) {
            return Optional.empty();
        }
        final DebugType type = typeValue.get();

        if (data.exists(Attributes.DECLARATION)) {
            return Optional.empty();
        }

        final byte[] locationExpression = data.tryAsByteArray(Attributes.LOCATION).orElseGet(() -> data.tryAsI32(Attributes.LOCATION).map(context::readLocationList).orElse(null));
        final int fileIndex = data.tryAsI32(Attributes.DECL_FILE).orElse(scope.fileIndex());
        final int startLineNumber = data.tryAsI32(Attributes.DECL_LINE).orElse(-1);
        final int startLocation = context.tryGetSourceLocation(fileIndex, startLineNumber).orElse(-1);
        final int endLocation = scope.endLocation();
        final DebugObject variable;
        if (locationExpression != null) {
            variable = new DebugVariable(name, type, locationExpression, startLocation, endLocation);
        } else {
            variable = DebugConstantObject.UNDEFINED;
        }
        if (variableName != null) {
            scope.addVariable(variable);
        }
        return Optional.of(variable);
    }

    private Optional<DebugType> parseNamespace(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.tryAsString(Attributes.NAME).orElse("");
        parseChildren(context, scope.with(name), data);
        return Optional.empty();
    }

    private Optional<? extends DebugType> parse(DebugParserContext context, DebugParserScope scope, int offset) {
        final Optional<DebugData> data = context.tryGetData(offset);
        if (data.isEmpty()) {
            return Optional.empty();
        }
        return parse(context, scope, data.get());
    }

    /**
     * Parses the given debug data and produces the associated internal type or object
     * representation if possible.
     * 
     * @param context the parsing context
     * @param scope the parsing scope
     * @param data the data of the debug entry
     */
    @TruffleBoundary
    public Optional<? extends DebugType> parse(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final int tag = data.tag();
        if (tag == Tags.VARIABLE || tag == Tags.MEMBER || tag == Tags.FORMAL_PARAMETER || tag == Tags.UNSPECIFIED_PARAMETERS) {
            return parseObject(context, scope, data);
        }
        if (types.containsKey(data.offset())) {
            return Optional.of(types.get(data.offset()));
        }
        final DebugTypeRef objectRef = new DebugTypeRef();
        types.put(data.offset(), objectRef);
        Optional<? extends DebugType> type = switch (data.tag()) {
            case Tags.ARRAY_TYPE -> parseArrayType(context, scope, data);
            case Tags.CLASS_TYPE, Tags.STRUCTURE_TYPE, Tags.UNION_TYPE -> parseStructType(context, scope, data);
            case Tags.ENUMERATION_TYPE -> parseEnumType(context, scope, data);
            case Tags.IMPORTED_DECLARATION, Tags.IMPORTED_MODULE -> parseImport(context, scope, data);
            case Tags.LEXICAL_BLOCK -> parseLexicalBlock(context, scope, data);
            case Tags.POINTER_TYPE, Tags.REFERENCE_TYPE, Tags.RVALUE_REFERENCE_TYPE -> parsePointerType(context, scope, data);
            case Tags.SUBROUTINE_TYPE -> parseSubroutineType(context, scope, data);
            case Tags.TYPEDEF -> parseTypeDef(context, scope, data);
            case Tags.INHERITANCE -> parseInheritance(context, scope, data);
            case Tags.PTR_TO_MEMBER_TYPE -> parsePointerToMemberType(context, scope, data);
            case Tags.ACCESS_DECLARATION, Tags.CONST_TYPE, Tags.VOLATILE_TYPE, Tags.RESTRICT_TYPE -> parseQualifierType(context, scope, data);
            case Tags.BASE_TYPE -> parseBaseType(data);
            case Tags.SUBPROGRAM -> parseSubprogram(context, data);
            case Tags.NAMESPACE -> parseNamespace(context, scope, data);
            case Tags.UNSPECIFIED_TYPE -> Optional.of(createUnspecifiedType());
            default -> Optional.empty();
        };
        if (type.isPresent()) {
            types.put(data.offset(), type.get());
            objectRef.setDelegate(type.get());
        }
        return type;
    }

    private Optional<? extends DebugObject> parseObject(DebugParserContext context, DebugParserScope scope, int offset) {
        final Optional<DebugData> data = context.tryGetData(offset);
        if (data.isEmpty()) {
            return Optional.empty();
        }
        return parseObject(context, scope, data.get());
    }

    private Optional<? extends DebugObject> parseObject(DebugParserContext context, DebugParserScope scope, DebugData data) {
        if (objects.containsKey(data.offset())) {
            return Optional.of(objects.get(data.offset()));
        }
        Optional<DebugObject> object = switch (data.tag()) {
            case Tags.FORMAL_PARAMETER -> parseFormalParameter(context, scope, data);
            case Tags.MEMBER -> parseMember(context, scope, data);
            case Tags.UNSPECIFIED_PARAMETERS -> Optional.of(createUnspecifiedParameters());
            case Tags.VARIABLE -> parseVariable(context, scope, data);
            default -> Optional.empty();
        };
        object.ifPresent(debugObject -> objects.put(data.offset(), debugObject));
        return object;
    }

    private static void forEachChild(DebugData data, int childTag, Consumer<DebugData> func) {
        for (DebugData child : data.children()) {
            if (child.tag() == childTag) {
                func.accept(child);
            }
        }
    }

    private static Optional<DebugData> getChild(DebugData data, int childTag) {
        for (DebugData child : data.children()) {
            if (child.tag() == childTag) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    private void parseChildren(DebugParserContext context, DebugParserScope scope, DebugData data) {
        for (DebugData child : data.children()) {
            parse(context, scope, child);
        }
    }
}
