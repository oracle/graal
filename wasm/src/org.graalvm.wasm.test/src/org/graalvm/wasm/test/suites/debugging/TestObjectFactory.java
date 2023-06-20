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

package org.graalvm.wasm.test.suites.debugging;

import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.debugging.data.DebugObject;
import org.graalvm.wasm.debugging.data.DebugObjectFactory;
import org.graalvm.wasm.debugging.data.DebugType;

import java.util.ArrayList;

public class TestObjectFactory extends DebugObjectFactory {
    private final ArrayList<DebugType> baseTypes = new ArrayList<>();
    private final ArrayList<DebugType> unspecifiedTypes = new ArrayList<>();
    private final ArrayList<DebugType> pointerTypes = new ArrayList<>();
    private final ArrayList<DebugType> qualifiedTypes = new ArrayList<>();
    private final ArrayList<DebugType> typeDefs = new ArrayList<>();
    private final ArrayList<DebugType> arrayTypes = new ArrayList<>();
    private final ArrayList<DebugType> structTypes = new ArrayList<>();
    private final ArrayList<DebugType> enumTypes = new ArrayList<>();
    private final ArrayList<DebugType> variantTypes = new ArrayList<>();

    @Override
    public String languageName() {
        return "test";
    }

    @Override
    protected String namespaceSeparator() {
        return "::";
    }

    @Override
    protected DebugType createBaseType(String name, int encoding, int byteSize, int bitOffset, int bitSize) {
        final DebugType baseType = super.createBaseType(name, encoding, byteSize, bitOffset, bitSize);
        baseTypes.add(baseType);
        return baseType;
    }

    public ArrayList<DebugType> getBaseTypes() {
        return baseTypes;
    }

    @Override
    protected DebugType createUnspecifiedType() {
        final DebugType unspecifiedType = super.createUnspecifiedType();
        unspecifiedTypes.add(unspecifiedType);
        return unspecifiedType;
    }

    public ArrayList<DebugType> getUnspecifiedTypes() {
        return unspecifiedTypes;
    }

    @Override
    protected DebugType createPointerType(DebugType baseType) {
        final DebugType pointerType = super.createPointerType(baseType);
        pointerTypes.add(pointerType);
        return pointerType;
    }

    public ArrayList<DebugType> getPointerTypes() {
        return pointerTypes;
    }

    @Override
    protected DebugType createQualifierType(int tag, String name, DebugType baseType) {
        final DebugType qualifiedType = super.createQualifierType(tag, name, baseType);
        qualifiedTypes.add(qualifiedType);
        return qualifiedType;
    }

    public ArrayList<DebugType> getQualifiedTypes() {
        return qualifiedTypes;
    }

    @Override
    protected DebugType createTypeDef(String name, DebugType baseType) {
        final DebugType typeDef = super.createTypeDef(name, baseType);
        typeDefs.add(typeDef);
        return typeDef;
    }

    public ArrayList<DebugType> getTypeDefs() {
        return typeDefs;
    }

    @Override
    protected DebugType createArrayType(String name, DebugType elementType, int[] dimensionLengths) {
        final DebugType arrayType = super.createArrayType(name, elementType, dimensionLengths);
        arrayTypes.add(arrayType);
        return arrayType;
    }

    public ArrayList<DebugType> getArrayTypes() {
        return arrayTypes;
    }

    @Override
    protected DebugType createStructType(String name, DebugObject[] members, DebugType[] superTypes) {
        final DebugType structType = super.createStructType(name, members, superTypes);
        structTypes.add(structType);
        return structType;
    }

    public ArrayList<DebugType> getStructTypes() {
        return structTypes;
    }

    @Override
    protected DebugType createEnumType(String name, DebugType baseType, EconomicMap<Long, String> values) {
        final DebugType enumType = super.createEnumType(name, baseType, values);
        enumTypes.add(enumType);
        return enumType;
    }

    public ArrayList<DebugType> getEnumTypes() {
        return enumTypes;
    }

    @Override
    protected DebugType createVariantType(String name, DebugObject discriminant, EconomicMap<Long, DebugObject> values) {
        final DebugType variantType = super.createVariantType(name, discriminant, values);
        variantTypes.add(variantType);
        return variantType;
    }

    public ArrayList<DebugType> getVariantTypes() {
        return variantTypes;
    }
}
