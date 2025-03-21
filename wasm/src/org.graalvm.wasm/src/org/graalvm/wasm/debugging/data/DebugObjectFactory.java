/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.collection.IntArrayList;
import org.graalvm.wasm.debugging.DebugLineMap;
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
import org.graalvm.wasm.debugging.parser.DebugParserContext;
import org.graalvm.wasm.debugging.parser.DebugParserScope;
import org.graalvm.wasm.debugging.parser.DebugUtil;
import org.graalvm.wasm.debugging.representation.DebugConstantDisplayValue;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

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

    private DebugType parseArrayType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.asStringOrNull(Attributes.NAME);
        final DebugType elementType = parse(context, scope, data.asI32OrDefault(Attributes.TYPE));
        if (elementType == null) {
            return null;
        }
        if (getChildOrNull(data, Tags.SUBRANGE_TYPE) == null) {
            return null;
        }
        final IntArrayList dimensionLengths = new IntArrayList();
        forEachChild(data, Tags.SUBRANGE_TYPE, s -> dimensionLengths.add(s.asU32OrDefault(Attributes.COUNT, 0)));
        return createArrayType(name, elementType, dimensionLengths.toArray());
    }

    private DebugType parseStructType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.asStringOrNull(Attributes.NAME);
        final DebugData variantPart = getChildOrNull(data, Tags.VARIANT_PART);
        if (variantPart != null) {
            return parseVariantType(context, scope, name, variantPart);
        }
        final List<DebugType> superTypes = new ArrayList<>();
        forEachChild(data, Tags.INHERITANCE, (i -> {
            if (i.asI32OrDefault(Attributes.TYPE, -1) == data.offset()) {
                // class inheriting from itself
                return;
            }
            final DebugType superType = parse(context, scope, i);
            if (superType != null) {
                superTypes.add(superType);
            }
        }));
        final List<DebugObject> members = new ArrayList<>();
        forEachChild(data, Tags.MEMBER, (m -> {
            final DebugObject member = parseObject(context, scope, m);
            if (member != null) {
                members.add(member);
            }
        }));
        forEachChild(data, Tags.SUBPROGRAM, (s -> parse(context, scope, s)));
        return createStructType(name, members.toArray(EMPTY_OBJECTS), superTypes.toArray(EMPTY_TYPES));
    }

    private DebugType parseEnumType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.asStringOrNull(Attributes.NAME);
        final DebugType baseType = parse(context, scope, data.asI32OrDefault(Attributes.TYPE));
        if (baseType == null) {
            return null;
        }
        final EconomicMap<Long, String> values = EconomicMap.create();
        forEachChild(data, Tags.ENUMERATOR, (e -> {
            final long constValue = e.asI64OrDefault(Attributes.CONST_VALUE);
            if (constValue != DebugUtil.DEFAULT_I64) {
                values.put(constValue, e.asStringOrNull(Attributes.NAME));
            }
        }));
        return createEnumType(name, baseType, values);
    }

    private DebugObject parseFormalParameter(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.asStringOrNull(Attributes.NAME);
        final DebugType type = parse(context, scope, data.asI32OrDefault(Attributes.TYPE));
        if (type == null) {
            return null;
        }
        byte[] locationExpression = data.asByteArrayOrNull(Attributes.LOCATION);
        if (locationExpression == null) {
            final int location = data.asI32OrDefault(Attributes.LOCATION);
            if (location != DebugUtil.DEFAULT_I32) {
                locationExpression = context.readLocationListOrNull(location);
            }
        }
        final DebugObject parameter = createParameter(name, type, locationExpression);
        if (name != null) {
            scope.addVariable(parameter);
        }
        return parameter;
    }

    private void parseLexicalBlock(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final DebugParserScope blockScope;
        if (data.hasAttribute(Attributes.LOW_PC)) {
            final int[] pcs = DebugDataUtil.readPcsOrNull(data, context);
            if (pcs == null) {
                return;
            }
            assert pcs.length == 2 : "the pc range of a debug lexical block must contain exactly two values (start pc and end pc) ";
            blockScope = scope.with(null, pcs[0], pcs[1]);
        } else {
            blockScope = scope;
        }
        parseChildren(context, blockScope, data);
    }

    private DebugType parseImport(DebugParserContext context, DebugParserScope scope, DebugData data) {
        return parse(context, scope, data.asI32OrDefault(Attributes.IMPORT));
    }

    private DebugObject parseMember(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.asStringOrNull(Attributes.NAME);
        final DebugType type = parse(context, scope, data.asI32OrDefault(Attributes.TYPE));
        if (type == null) {
            return null;
        }
        final byte[] locationExpression = data.asByteArrayOrNull(Attributes.DATA_MEMBER_LOCATION);
        final int offset = data.asI32OrDefault(Attributes.DATA_MEMBER_LOCATION, 0);
        final int bitOffset = data.asI32OrDefault(Attributes.BIT_OFFSET, -1);
        final int bitSize = data.asI32OrDefault(Attributes.BIT_SIZE, -1);

        return createMember(name, type, locationExpression, offset, bitOffset, bitSize);
    }

    private DebugType parsePointerType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final DebugType type = parse(context, scope, data.asI32OrDefault(Attributes.TYPE));
        if (type == null) {
            return null;
        }
        return createPointerType(type);
    }

    private DebugType parseSubroutineType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.asStringOrNull(Attributes.NAME);
        final DebugType returnType = parse(context, scope, data.asI32OrDefault(Attributes.TYPE));
        final List<DebugType> parameterTypes = new ArrayList<>();
        for (DebugData child : data.children()) {
            final int tag = child.tag();
            if (tag == Tags.FORMAL_PARAMETER || tag == Tags.UNSPECIFIED_PARAMETERS) {
                final DebugType param = parse(context, scope, child);
                if (param != null) {
                    parameterTypes.add(param);
                }
            }
        }
        return createSubroutineType(name, returnType, parameterTypes.toArray(EMPTY_TYPES));
    }

    private DebugType parseTypeDef(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.asStringOrNull(Attributes.NAME);
        if (name == null) {
            return null;
        }
        final DebugType baseType = parse(context, scope, data.asI32OrDefault(Attributes.TYPE));
        return createTypeDef(name, baseType);
    }

    private DebugType parseVariantType(DebugParserContext context, DebugParserScope scope, String name, DebugData data) {
        final int discr = data.asI32OrDefault(Attributes.DISCR);
        final DebugObject discriminant;
        if (discr != DebugUtil.DEFAULT_I32) {
            discriminant = parseObject(context, scope, discr);
        } else {
            discriminant = parseObject(context, scope, data.asI32OrDefault(Attributes.TYPE));
        }
        if (discriminant == null) {
            return null;
        }
        final EconomicMap<Long, DebugObject> values = EconomicMap.create();
        final DebugParserScope variantScope = scope.with(name);
        forEachChild(data, Tags.VARIANT, (v -> {
            final long discValue = v.asI64OrDefault(Attributes.DISCR_VALUE);
            if (discValue == DebugUtil.DEFAULT_I64) {
                return;
            }
            DebugObject entry = null;
            for (DebugData child : v.children()) {
                entry = parseObject(context, variantScope, child);
            }
            if (entry == null) {
                return;
            }
            values.put(discValue, entry);
        }));
        return createVariantType(name, discriminant, values);
    }

    private DebugType parseInheritance(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final DebugType referenceType = parse(context, scope, data.asI32OrDefault(Attributes.TYPE));
        if (referenceType == null) {
            return null;
        }
        final byte[] locationExpression = data.asByteArrayOrNull(Attributes.DATA_MEMBER_LOCATION);
        final int memberOffset = data.asI32OrDefault(Attributes.DATA_MEMBER_LOCATION, 0);
        return new DebugInheritance(referenceType, locationExpression, memberOffset);
    }

    private DebugType parsePointerToMemberType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final DebugType memberType = parse(context, scope, data.asI32OrDefault(Attributes.TYPE));
        final DebugType containingType = parse(context, scope, data.asI32OrDefault(Attributes.CONTAINING_TYPE));
        return new DebugPointerToMemberType(memberType, containingType);
    }

    private DebugType parseQualifierType(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.asStringOrNull(Attributes.NAME);
        final DebugType baseType = parse(context, scope, data.asI32OrDefault(Attributes.TYPE));
        if (baseType == null) {
            return null;
        }
        return createQualifierType(data.tag(), name, baseType);
    }

    private DebugType parseBaseType(DebugData data) {
        final String name = data.asStringOrNull(Attributes.NAME);
        if (name == null) {
            return null;
        }
        final int encoding = data.asI32OrDefault(Attributes.ENCODING);
        if (encoding == DebugUtil.DEFAULT_I32) {
            return null;
        }
        int byteSize = data.asI32OrDefault(Attributes.BYTE_SIZE);
        final int bitSize = data.asI32OrDefault(Attributes.BIT_SIZE, -1);
        if (byteSize == DebugUtil.DEFAULT_I32) {
            if (bitSize == -1) {
                return null;
            }
            byteSize = 1;
        }
        int bitOffset = data.asI32OrDefault(Attributes.BIT_OFFSET, -1);
        if (bitOffset == -1) {
            bitOffset = data.asI32OrDefault(Attributes.DATA_BIT_OFFSET, 0);
        }
        return createBaseType(name, encoding, byteSize, bitSize, bitOffset);
    }

    private DebugType parseSubprogram(DebugParserContext context, DebugData data) {
        final int fileIndex;
        final String name;
        final DebugData specData = context.dataOrNull(data.asI32OrDefault(Attributes.SPECIFICATION));
        if (specData != null) {
            final int fileIndexValue = specData.asU32OrDefault(Attributes.DECL_FILE);
            if (fileIndexValue != DebugUtil.DEFAULT_I32) {
                fileIndex = fileIndexValue;
            } else {
                fileIndex = data.asU32OrDefault(Attributes.DECL_FILE, -1);
            }
            name = specData.asStringOrNull(Attributes.NAME);
        } else {
            fileIndex = data.asU32OrDefault(Attributes.DECL_FILE, -1);
            name = data.asStringOrNull(Attributes.NAME);
        }

        final DebugLineMap lineMap = context.lineMapOrNull(fileIndex);
        if (lineMap == null) {
            return null;
        }

        if (data.exists(Attributes.DECLARATION)) {
            return null;
        }
        final int inline = data.asI32OrDefault(Attributes.INLINE, 0);
        if (inline == 1 || inline == 3) {
            return null;
        }

        final int[] pcs = DebugDataUtil.readPcsOrNull(data, context);
        if (pcs == null) {
            return null;
        }
        assert pcs.length == 2 : "the pc range of a debug subprogram (function) must contain exactly two values (start pc and end pc)";
        final int scopeStartPc = pcs[0];
        final int scopeEndPc = pcs[1];
        final int startLine = lineMap.getNextLine(scopeStartPc, scopeEndPc);
        if (startLine == -1) {
            return null;
        }

        final Path path = context.pathOrNull(fileIndex);

        final byte[] frameBaseExpression = data.asByteArrayOrNull(Attributes.FRAME_BASE);
        if (frameBaseExpression == null) {
            return null;
        }

        final DebugParserScope functionScope = DebugParserScope.createFunctionScope(scopeStartPc, scopeEndPc, fileIndex);
        parseChildren(context, functionScope, data);
        final List<DebugObject> globals = context.globals();
        final List<DebugObject> variables = functionScope.variables();
        final DebugFunction function = new DebugFunction(name, lineMap, path, context.language(), startLine, frameBaseExpression, variables, globals);
        if (name != null) {
            context.addFunction(scopeStartPc, function);
        }
        return function;
    }

    private DebugObject parseVariable(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String variableName;
        final DebugType type;
        final DebugData specData = context.dataOrNull(data.asI32OrDefault(Attributes.SPECIFICATION));
        if (specData != null) {
            variableName = specData.asStringOrNull(Attributes.NAME);
            type = parse(context, scope, specData.asI32OrDefault(Attributes.TYPE));
        } else {
            variableName = data.asStringOrNull(Attributes.NAME);
            type = parse(context, scope, data.asI32OrDefault(Attributes.TYPE));
        }
        if (type == null) {
            return null;
        }
        final String name;
        final String scopeName = scope.nameOrNull();
        if (scopeName == null) {
            name = variableName;
        } else {
            name = scopeName + namespaceSeparator() + variableName;
        }

        if (data.exists(Attributes.DECLARATION)) {
            return null;
        }

        byte[] locationExpression = data.asByteArrayOrNull(Attributes.LOCATION);
        if (locationExpression == null) {
            final int location = data.asI32OrDefault(Attributes.LOCATION);
            if (location != DebugUtil.DEFAULT_I32) {
                locationExpression = context.readLocationListOrNull(location);
            }
        }
        // check if the spec data contains the relevant information instead
        if (locationExpression == null && specData != null) {
            locationExpression = specData.asByteArrayOrNull(Attributes.LOCATION);
            if (locationExpression == null) {
                final int location = data.asI32OrDefault(Attributes.LOCATION);
                if (location != DebugUtil.DEFAULT_I32) {
                    locationExpression = context.readLocationListOrNull(location);
                }
            }
        }
        int fileIndex = data.asU32OrDefault(Attributes.DECL_FILE, -1);
        if (fileIndex == -1) {
            if (specData != null) {
                fileIndex = specData.asU32OrDefault(Attributes.DECL_FILE, scope.fileIndex());
            } else {
                fileIndex = scope.fileIndex();
            }
        }
        int startLineNumber = data.asU32OrDefault(Attributes.DECL_LINE, -1);
        if (startLineNumber == -1 && specData != null) {
            startLineNumber = specData.asU32OrDefault(Attributes.DECL_LINE, -1);
        }
        int startLocation = context.sourceOffsetOrDefault(fileIndex, startLineNumber, scope.startLocation());
        final int endLocation = scope.endLocation();
        final DebugObject variable;
        if (startLocation != -1 && locationExpression != null) {
            variable = new DebugVariable(name, type, locationExpression, startLocation, endLocation);
        } else {
            variable = DebugConstantObject.UNDEFINED;
        }
        if (variableName != null) {
            scope.addVariable(variable);
        }
        return variable;
    }

    private DebugType parseNamespace(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final String name = data.asStringOrEmpty(Attributes.NAME);
        parseChildren(context, scope.with(name), data);
        return null;
    }

    private DebugType parse(DebugParserContext context, DebugParserScope scope, int offset) {
        if (offset == DebugUtil.DEFAULT_I32) {
            return null;
        }
        final DebugData data = context.dataOrNull(offset);
        if (data == null) {
            return null;
        }
        return parse(context, scope, data);
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
    public DebugType parse(DebugParserContext context, DebugParserScope scope, DebugData data) {
        final int tag = data.tag();
        if (tag == Tags.LEXICAL_BLOCK) {
            parseLexicalBlock(context, scope, data);
            return null;
        }
        if (tag == Tags.VARIABLE || tag == Tags.MEMBER || tag == Tags.FORMAL_PARAMETER || tag == Tags.UNSPECIFIED_PARAMETERS) {
            return parseObject(context, scope, data);
        }
        if (types.containsKey(data.offset())) {
            return types.get(data.offset());
        }
        final DebugTypeRef objectRef = new DebugTypeRef();
        types.put(data.offset(), objectRef);
        final DebugType type = switch (data.tag()) {
            case Tags.ARRAY_TYPE -> parseArrayType(context, scope, data);
            case Tags.CLASS_TYPE, Tags.STRUCTURE_TYPE, Tags.UNION_TYPE -> parseStructType(context, scope, data);
            case Tags.ENUMERATION_TYPE -> parseEnumType(context, scope, data);
            case Tags.IMPORTED_DECLARATION, Tags.IMPORTED_MODULE -> parseImport(context, scope, data);
            case Tags.POINTER_TYPE, Tags.REFERENCE_TYPE, Tags.RVALUE_REFERENCE_TYPE -> parsePointerType(context, scope, data);
            case Tags.SUBROUTINE_TYPE -> parseSubroutineType(context, scope, data);
            case Tags.TYPEDEF -> parseTypeDef(context, scope, data);
            case Tags.INHERITANCE -> parseInheritance(context, scope, data);
            case Tags.PTR_TO_MEMBER_TYPE -> parsePointerToMemberType(context, scope, data);
            case Tags.ACCESS_DECLARATION, Tags.CONST_TYPE, Tags.VOLATILE_TYPE, Tags.RESTRICT_TYPE -> parseQualifierType(context, scope, data);
            case Tags.BASE_TYPE -> parseBaseType(data);
            case Tags.SUBPROGRAM -> parseSubprogram(context, data);
            case Tags.NAMESPACE -> parseNamespace(context, scope, data);
            case Tags.UNSPECIFIED_TYPE -> createUnspecifiedType();
            default -> null;
        };
        if (type != null) {
            types.put(data.offset(), type);
            objectRef.setDelegate(type);
        }
        return type;
    }

    private DebugObject parseObject(DebugParserContext context, DebugParserScope scope, int offset) {
        if (offset == DebugUtil.DEFAULT_I32) {
            return null;
        }
        final DebugData data = context.dataOrNull(offset);
        if (data == null) {
            return null;
        }
        return parseObject(context, scope, data);
    }

    private DebugObject parseObject(DebugParserContext context, DebugParserScope scope, DebugData data) {
        if (objects.containsKey(data.offset())) {
            return objects.get(data.offset());
        }
        DebugObject object = switch (data.tag()) {
            case Tags.FORMAL_PARAMETER -> parseFormalParameter(context, scope, data);
            case Tags.MEMBER -> parseMember(context, scope, data);
            case Tags.UNSPECIFIED_PARAMETERS -> createUnspecifiedParameters();
            case Tags.VARIABLE -> parseVariable(context, scope, data);
            default -> null;
        };
        if (object != null) {
            objects.put(data.offset(), object);
        }
        return object;
    }

    private static void forEachChild(DebugData data, int childTag, Consumer<DebugData> func) {
        for (DebugData child : data.children()) {
            if (child.tag() == childTag) {
                func.accept(child);
            }
        }
    }

    private static DebugData getChildOrNull(DebugData data, int childTag) {
        for (DebugData child : data.children()) {
            if (child.tag() == childTag) {
                return child;
            }
        }
        return null;
    }

    private void parseChildren(DebugParserContext context, DebugParserScope scope, DebugData data) {
        for (DebugData child : data.children()) {
            parse(context, scope, child);
        }
    }
}
