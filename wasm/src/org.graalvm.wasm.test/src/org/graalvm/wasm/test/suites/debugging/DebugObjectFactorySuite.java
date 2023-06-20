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

import com.oracle.truffle.api.source.Source;
import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.collection.LongArrayList;
import org.graalvm.wasm.debugging.DebugLineMap;
import org.graalvm.wasm.debugging.data.DebugFunction;
import org.graalvm.wasm.debugging.data.DebugType;
import org.graalvm.wasm.debugging.data.objects.DebugConstantObject;
import org.graalvm.wasm.debugging.data.types.DebugPointerType;
import org.graalvm.wasm.debugging.encoding.AttributeEncodings;
import org.graalvm.wasm.debugging.encoding.Attributes;
import org.graalvm.wasm.debugging.encoding.DataEncoding;
import org.graalvm.wasm.debugging.encoding.Tags;
import org.graalvm.wasm.debugging.parser.DebugData;
import org.graalvm.wasm.debugging.parser.DebugParserContext;
import org.graalvm.wasm.debugging.parser.DebugParserScope;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Test suite for debug entries based on the specification in the
 * <a href="https://dwarfstd.org/doc/DWARF4.pdf">DWARF Debug Information format</a>.
 */
public class DebugObjectFactorySuite {
    private static final class AttributeBuilder {
        private final LongArrayList attributeInfo = new LongArrayList();
        private final ArrayList<Object> attributes = new ArrayList<>();

        private AttributeBuilder() {

        }

        static AttributeBuilder create() {
            return new AttributeBuilder();
        }

        AttributeBuilder add(int attribute, int form, Object value) {
            attributeInfo.add((long) DataEncoding.fromForm(form) << 32 | attribute);
            attributes.add(value);
            return this;
        }

        public Object[] attributeValues() {
            return attributes.toArray(new Object[0]);
        }

        public long[] attributeInfo() {
            return attributeInfo.toArray();
        }
    }

    private static DebugData getCompilationUnit(DebugData[] children) {
        final AttributeBuilder builder = AttributeBuilder.create().add(Attributes.LOCATION, 0x0F, 2).add(Attributes.STMT_LIST, 0x0F, 0).add(Attributes.COMP_DIR, 0x08, "test").add(Attributes.LOW_PC,
                        0x0F, 0).add(Attributes.HIGH_PC, 0x0F, 100);
        return new DebugData(Tags.COMPILATION_UNIT, 0, builder.attributeInfo(), builder.attributeValues(), children);
    }

    private static EconomicMap<Integer, DebugData> getEntryData(DebugData compilationUnit) {
        final EconomicMap<Integer, DebugData> entryData = EconomicMap.create();
        getEntryData(compilationUnit, entryData);
        return entryData;
    }

    private static void getEntryData(DebugData debugData, EconomicMap<Integer, DebugData> entryData) {
        entryData.put(debugData.offset(), debugData);
        for (DebugData d : debugData.children()) {
            getEntryData(d, entryData);
        }
    }

    private static DebugParserContext parseCompilationUnit(TestObjectFactory factory, DebugData... children) {
        return parseCompilationUnit(factory, null, null, children);
    }

    private static DebugParserContext parseCompilationUnit(TestObjectFactory factory, DebugLineMap lineMap, Source source, DebugData... children) {
        final byte[] data = {};
        final DebugData compUnit = getCompilationUnit(children);
        final DebugLineMap[] lineMaps;
        if (lineMap != null) {
            lineMaps = new DebugLineMap[]{lineMap};
        } else {
            lineMaps = null;
        }
        final Source[] sources;
        if (source != null) {
            sources = new Source[]{source};
        } else {
            sources = null;
        }
        final DebugParserContext context = new DebugParserContext(data, 0, getEntryData(compUnit), lineMaps, sources);
        final DebugParserScope scope = DebugParserScope.createGlobalScope();
        for (DebugData d : compUnit.children()) {
            factory.parse(context, scope, d);
        }
        return context;
    }

    @Test
    public void testEmptyCompilationUnit() {
        final TestObjectFactory factory = new TestObjectFactory();
        final DebugParserContext context = parseCompilationUnit(factory);
        Assert.assertEquals(0, context.functions().size());
        Assert.assertEquals(0, context.globals().size());
    }

    @Test
    public void testBaseType() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F,
                        4).add(Attributes.BIT_SIZE, 0x0F, 0).add(Attributes.BIT_OFFSET, 0x0F, 0);
        final DebugData intType = new DebugData(Tags.BASE_TYPE, 1, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, intType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
        final DebugType baseType = factory.getBaseTypes().get(0);
        Assert.assertEquals("int", baseType.asTypeName());
        Assert.assertTrue(baseType.fitsIntoInt());
        Assert.assertTrue(baseType.fitsIntoLong());
        Assert.assertTrue(baseType.isValue());
        Assert.assertFalse(baseType.isDebugObject());
        Assert.assertFalse(baseType.isLocation());
        Assert.assertFalse(baseType.hasArrayElements());
        Assert.assertFalse(baseType.hasMembers());
    }

    @Test
    public void testBaseTypeMissingName() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4).add(Attributes.BIT_SIZE, 0x0F,
                        0).add(Attributes.BIT_OFFSET, 0x0F, 0);
        final DebugData intType = new DebugData(Tags.BASE_TYPE, 1, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, intType);
        Assert.assertEquals(0, factory.getBaseTypes().size());
    }

    @Test
    public void testBaseTypeMissingEncoding() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.BYTE_SIZE, 0x0F, 4).add(Attributes.BIT_SIZE, 0x0F,
                        0).add(Attributes.BIT_OFFSET, 0x0F, 0);
        final DebugData intType = new DebugData(Tags.BASE_TYPE, 1, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, intType);
        Assert.assertEquals(0, factory.getBaseTypes().size());
    }

    @Test
    public void testBaseTypeMissingByteSize() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BIT_SIZE, 0x0F,
                        16).add(Attributes.BIT_OFFSET, 0x0F, 0);
        final DebugData intType = new DebugData(Tags.BASE_TYPE, 1, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, intType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
    }

    @Test
    public void testBaseTypeMissingBitSize() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F,
                        4).add(Attributes.BIT_OFFSET, 0x0F, 0);
        final DebugData intType = new DebugData(Tags.BASE_TYPE, 1, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, intType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
    }

    @Test
    public void testBaseTypeMissingByteSizeAndBitSize() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BIT_OFFSET, 0x0F,
                        0);
        final DebugData intType = new DebugData(Tags.BASE_TYPE, 1, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, intType);
        Assert.assertEquals(0, factory.getBaseTypes().size());
    }

    @Test
    public void testBaseTypeMissingBitOffset() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F,
                        4).add(Attributes.BIT_SIZE, 0x0F, 0);
        final DebugData intType = new DebugData(Tags.BASE_TYPE, 1, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, intType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
    }

    @Test
    public void testBaseTypeWithDataBitOffset() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F,
                        4).add(Attributes.BIT_SIZE, 0x0F, 0).add(Attributes.DATA_BIT_OFFSET, 0x0F, 0);
        final DebugData intType = new DebugData(Tags.BASE_TYPE, 1, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, intType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
        final DebugType type = factory.getBaseTypes().get(0);
        Assert.assertEquals("int", type.asTypeName());
        Assert.assertTrue(type.fitsIntoInt());
        Assert.assertTrue(type.fitsIntoLong());
        Assert.assertTrue(type.isValue());
        Assert.assertFalse(type.isDebugObject());
        Assert.assertFalse(type.isLocation());
        Assert.assertFalse(type.hasArrayElements());
        Assert.assertFalse(type.hasMembers());
    }

    @Test
    public void testUnspecifiedType() {
        final AttributeBuilder attr = AttributeBuilder.create();
        final DebugData unspecifiedType = new DebugData(Tags.UNSPECIFIED_TYPE, 1, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, unspecifiedType);
        Assert.assertEquals(1, factory.getUnspecifiedTypes().size());
        final DebugType type = factory.getUnspecifiedTypes().get(0);
        Assert.assertSame(DebugConstantObject.UNSPECIFIED, type);
        Assert.assertFalse(type.fitsIntoInt());
        Assert.assertFalse(type.fitsIntoLong());
        Assert.assertTrue(type.isValue());
        Assert.assertFalse(type.isDebugObject());
        Assert.assertFalse(type.isLocation());
        Assert.assertFalse(type.hasArrayElements());
        Assert.assertFalse(type.hasMembers());
    }

    @Test
    public void testUnspecifiedTypeWithName() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "void");
        final DebugData unspecifiedType = new DebugData(Tags.UNSPECIFIED_TYPE, 1, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, unspecifiedType);
        Assert.assertEquals(1, factory.getUnspecifiedTypes().size());
        Assert.assertSame(DebugConstantObject.UNSPECIFIED, factory.getUnspecifiedTypes().get(0));
    }

    @Test
    public void testPointerType() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData pointerType = new DebugData(Tags.POINTER_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, pointerType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getPointerTypes().size());
        final DebugType type = factory.getPointerTypes().get(0);
        Assert.assertFalse(type.fitsIntoInt());
        Assert.assertFalse(type.fitsIntoLong());
        Assert.assertFalse(type.isValue());
        Assert.assertFalse(type.isDebugObject());
        Assert.assertTrue(type.isLocation());
        Assert.assertFalse(type.hasArrayElements());
        Assert.assertTrue(type.hasMembers());
        Assert.assertEquals(1, type.memberCount());
    }

    @Test
    public void testPointerTypeMissingBaseTypeAttribute() {
        final AttributeBuilder attr = AttributeBuilder.create();
        final DebugData pointerType = new DebugData(Tags.POINTER_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, pointerType);
        Assert.assertEquals(0, factory.getPointerTypes().size());
    }

    @Test
    public void testPointerTypeMissingBaseType() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData pointerType = new DebugData(Tags.POINTER_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, pointerType);
        Assert.assertEquals(0, factory.getPointerTypes().size());
    }

    @Test
    public void testPointerTypeWithName() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1).add(Attributes.NAME, 0x08, "pointer");
        final DebugData pointerType = new DebugData(Tags.POINTER_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, pointerType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getPointerTypes().size());
    }

    @Test
    public void testReferenceType() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData referenceType = new DebugData(Tags.REFERENCE_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, referenceType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getPointerTypes().size());
        final DebugType type = factory.getPointerTypes().get(0);
        Assert.assertTrue(type instanceof DebugPointerType);
        Assert.assertFalse(type.fitsIntoInt());
        Assert.assertFalse(type.fitsIntoLong());
        Assert.assertFalse(type.isValue());
        Assert.assertFalse(type.isDebugObject());
        Assert.assertTrue(type.isLocation());
        Assert.assertFalse(type.hasArrayElements());
        Assert.assertTrue(type.hasMembers());
        Assert.assertEquals(1, type.memberCount());
    }

    @Test
    public void testReferenceTypeMissingBaseTypeAttribute() {
        final AttributeBuilder attr = AttributeBuilder.create();
        final DebugData referenceType = new DebugData(Tags.REFERENCE_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, referenceType);
        Assert.assertEquals(0, factory.getPointerTypes().size());
    }

    @Test
    public void testReferenceTypeMissingBaseType() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData referenceType = new DebugData(Tags.REFERENCE_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, referenceType);
        Assert.assertEquals(0, factory.getPointerTypes().size());
    }

    @Test
    public void testRValueReferenceType() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData rValueReferenceType = new DebugData(Tags.RVALUE_REFERENCE_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, rValueReferenceType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getPointerTypes().size());
        final DebugType type = factory.getPointerTypes().get(0);
        Assert.assertFalse(type.fitsIntoInt());
        Assert.assertFalse(type.fitsIntoLong());
        Assert.assertFalse(type.isValue());
        Assert.assertFalse(type.isDebugObject());
        Assert.assertTrue(type.isLocation());
        Assert.assertFalse(type.hasArrayElements());
        Assert.assertTrue(type.hasMembers());
        Assert.assertEquals(1, type.memberCount());
    }

    @Test
    public void testRValueReferenceTypeMissingBaseTypeAttribute() {
        final AttributeBuilder attr = AttributeBuilder.create();
        final DebugData rValueReferenceType = new DebugData(Tags.RVALUE_REFERENCE_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, rValueReferenceType);
        Assert.assertEquals(0, factory.getPointerTypes().size());
    }

    @Test
    public void testRValueReferenceTypeMissingBaseType() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData rValueReferenceType = new DebugData(Tags.RVALUE_REFERENCE_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, rValueReferenceType);
        Assert.assertEquals(0, factory.getPointerTypes().size());
    }

    @Test
    public void testConstType() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData constType = new DebugData(Tags.CONST_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, constType, baseType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getQualifiedTypes().size());
    }

    @Test
    public void testConstTypeMissingBaseTypeAttribute() {
        final AttributeBuilder attr = AttributeBuilder.create();
        final DebugData constType = new DebugData(Tags.CONST_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, constType);
        Assert.assertEquals(0, factory.getQualifiedTypes().size());
    }

    @Test
    public void testConstTypeMissingBaseType() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData constType = new DebugData(Tags.CONST_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, constType);
        Assert.assertEquals(0, factory.getQualifiedTypes().size());
    }

    @Test
    public void testRestrictType() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData restrictType = new DebugData(Tags.RESTRICT_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, restrictType, baseType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getQualifiedTypes().size());
    }

    @Test
    public void testRestrictTypeMissingBaseTypeAttribute() {
        final AttributeBuilder attr = AttributeBuilder.create();
        final DebugData restrictType = new DebugData(Tags.RESTRICT_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, restrictType);
        Assert.assertEquals(0, factory.getQualifiedTypes().size());
    }

    @Test
    public void testRestrictTypeMissingBaseType() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData restrictType = new DebugData(Tags.RESTRICT_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, restrictType);
        Assert.assertEquals(0, factory.getQualifiedTypes().size());
    }

    @Test
    public void testVolatileType() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData volatileType = new DebugData(Tags.VOLATILE_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, volatileType, baseType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getQualifiedTypes().size());
    }

    @Test
    public void testVolatileTypeMissingBaseTypeAttribute() {
        final AttributeBuilder attr = AttributeBuilder.create();
        final DebugData volatileType = new DebugData(Tags.VOLATILE_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, volatileType);
        Assert.assertEquals(0, factory.getQualifiedTypes().size());
    }

    @Test
    public void testVolatileTypeMissingBaseType() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData volatileType = new DebugData(Tags.VOLATILE_TYPE, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, volatileType);
        Assert.assertEquals(0, factory.getQualifiedTypes().size());
    }

    @Test
    public void testTypeDef() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "i32").add(Attributes.TYPE, 0x0F, 1);
        final DebugData typeDef = new DebugData(Tags.TYPEDEF, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, typeDef, baseType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getTypeDefs().size());
        final DebugType base = factory.getBaseTypes().get(0);
        final DebugType type = factory.getTypeDefs().get(0);
        Assert.assertEquals("i32", type.asTypeName());
        Assert.assertEquals(base.fitsIntoInt(), type.fitsIntoInt());
        Assert.assertEquals(base.fitsIntoLong(), type.fitsIntoLong());
        Assert.assertEquals(base.isValue(), type.isValue());
        Assert.assertEquals(base.isDebugObject(), type.isDebugObject());
        Assert.assertEquals(base.isLocation(), type.isLocation());
        Assert.assertEquals(base.hasArrayElements(), type.hasArrayElements());
        Assert.assertEquals(base.hasMembers(), type.hasMembers());
    }

    @Test
    public void testTypeDefMissingName() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData typeDef = new DebugData(Tags.TYPEDEF, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, typeDef, baseType);
        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(0, factory.getTypeDefs().size());
    }

    @Test
    public void testTypeDefMissingBaseTypeAttribute() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "i32");
        final DebugData typeDef = new DebugData(Tags.TYPEDEF, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, typeDef);
        Assert.assertEquals(1, factory.getTypeDefs().size());
    }

    @Test
    public void testTypeDefMissingBaseType() {
        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "i32").add(Attributes.TYPE, 0x0F, 1);
        final DebugData typeDef = new DebugData(Tags.TYPEDEF, 2, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);
        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, typeDef);
        Assert.assertEquals(1, factory.getTypeDefs().size());
    }

    @Test
    public void testArrayType() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder dimAttr = AttributeBuilder.create().add(Attributes.COUNT, 0x0F, 20);
        final DebugData dimType = new DebugData(Tags.SUBRANGE_TYPE, 2, dimAttr.attributeInfo(), dimAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "test").add(Attributes.TYPE, 0x0F, 1);
        final DebugData arrayType = new DebugData(Tags.ARRAY_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{dimType});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, arrayType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getArrayTypes().size());
        final DebugType type = factory.getArrayTypes().get(0);
        Assert.assertEquals("test", type.asTypeName());
        Assert.assertFalse(type.fitsIntoInt());
        Assert.assertFalse(type.fitsIntoLong());
        Assert.assertFalse(type.isValue());
        Assert.assertFalse(type.isDebugObject());
        Assert.assertFalse(type.isLocation());
        Assert.assertTrue(type.hasArrayElements());
        Assert.assertFalse(type.hasMembers());
        Assert.assertEquals(1, type.arrayDimensionCount());
        Assert.assertEquals(20, type.arrayDimensionSize(0));
    }

    @Test
    public void testArrayTypeMissingName() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder dimAttr = AttributeBuilder.create().add(Attributes.COUNT, 0x0F, 20);
        final DebugData dimType = new DebugData(Tags.SUBRANGE_TYPE, 2, dimAttr.attributeInfo(), dimAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData arrayType = new DebugData(Tags.ARRAY_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{dimType});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, arrayType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getArrayTypes().size());
    }

    @Test
    public void testArrayTypeMissingElementTypeAttribute() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder dimAttr = AttributeBuilder.create().add(Attributes.COUNT, 0x0F, 20);
        final DebugData dimType = new DebugData(Tags.SUBRANGE_TYPE, 2, dimAttr.attributeInfo(), dimAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "test");
        final DebugData arrayType = new DebugData(Tags.ARRAY_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{dimType});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, arrayType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(0, factory.getArrayTypes().size());
    }

    @Test
    public void testArrayTypeMissingElementType() {
        final AttributeBuilder dimAttr = AttributeBuilder.create().add(Attributes.COUNT, 0x0F, 20);
        final DebugData dimType = new DebugData(Tags.SUBRANGE_TYPE, 2, dimAttr.attributeInfo(), dimAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "test").add(Attributes.TYPE, 0x0F, 1);
        final DebugData arrayType = new DebugData(Tags.ARRAY_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{dimType});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, arrayType);

        Assert.assertEquals(0, factory.getBaseTypes().size());
        Assert.assertEquals(0, factory.getArrayTypes().size());
    }

    @Test
    public void testArrayTypeWithNoDimensions() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "test").add(Attributes.TYPE, 0x0F, 1);
        final DebugData arrayType = new DebugData(Tags.ARRAY_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, arrayType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(0, factory.getArrayTypes().size());
    }

    @Test
    public void testArrayTypeDimensionMissingCount() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder dimAttr = AttributeBuilder.create();
        final DebugData dimType = new DebugData(Tags.SUBRANGE_TYPE, 2, dimAttr.attributeInfo(), dimAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "test").add(Attributes.TYPE, 0x0F, 1);
        final DebugData arrayType = new DebugData(Tags.ARRAY_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{dimType});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, arrayType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getArrayTypes().size());
    }

    @Test
    public void testStructType() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.TYPE, 0x0F, 1).add(Attributes.DATA_MEMBER_LOCATION, 0x0F, 0);
        final DebugData member = new DebugData(Tags.MEMBER, 2, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "t");
        final DebugData structType = new DebugData(Tags.STRUCTURE_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{member});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, structType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().size());
        final DebugType type = factory.getStructTypes().get(0);
        Assert.assertEquals("t", type.asTypeName());
        Assert.assertFalse(type.fitsIntoInt());
        Assert.assertFalse(type.fitsIntoLong());
        Assert.assertFalse(type.isValue());
        Assert.assertFalse(type.isDebugObject());
        Assert.assertFalse(type.isLocation());
        Assert.assertFalse(type.hasArrayElements());
        Assert.assertTrue(type.hasMembers());
        Assert.assertEquals(1, type.memberCount());
    }

    @Test
    public void testStructTypeWithoutName() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.TYPE, 0x0F, 1).add(Attributes.DATA_MEMBER_LOCATION, 0x0F, 0);
        final DebugData member = new DebugData(Tags.MEMBER, 2, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create();
        final DebugData structType = new DebugData(Tags.STRUCTURE_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{member});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, structType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().size());
    }

    @Test
    public void testStructTypeWithoutMembers() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "t");
        final DebugData structType = new DebugData(Tags.STRUCTURE_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[0]);

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, structType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().size());
    }

    @Test
    public void testMemberMissingBaseTypeAttribute() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.DATA_MEMBER_LOCATION, 0x0F, 0);
        final DebugData member = new DebugData(Tags.MEMBER, 2, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "t");
        final DebugData structType = new DebugData(Tags.STRUCTURE_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{member});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, structType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().size());
        Assert.assertEquals(0, factory.getStructTypes().get(0).memberCount());
    }

    @Test
    public void testMemberMissingBaseType() {
        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.DATA_MEMBER_LOCATION, 0x0F, 0);
        final DebugData member = new DebugData(Tags.MEMBER, 2, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "t");
        final DebugData structType = new DebugData(Tags.STRUCTURE_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{member});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, structType);

        Assert.assertEquals(0, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().size());
        Assert.assertEquals(0, factory.getStructTypes().get(0).memberCount());
    }

    @Test
    public void testMemberWithLocationExpression() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.TYPE, 0x0F, 1).add(Attributes.DATA_MEMBER_LOCATION, 0x09,
                        new byte[]{0x04, (byte) 0xED, 0x00, 0x01, (byte) 0x9F});
        final DebugData member = new DebugData(Tags.MEMBER, 2, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "t");
        final DebugData structType = new DebugData(Tags.STRUCTURE_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{member});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, structType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().get(0).memberCount());
    }

    @Test
    public void testMemberWithoutLocation() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.TYPE, 0x0F, 1);
        final DebugData member = new DebugData(Tags.MEMBER, 2, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "t");
        final DebugData structType = new DebugData(Tags.STRUCTURE_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{member});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, structType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().get(0).memberCount());
    }

    @Test
    public void testMemberWithBitValues() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.TYPE, 0x0F, 1).add(Attributes.DATA_MEMBER_LOCATION, 0x0F, 0).add(
                        Attributes.BIT_SIZE, 0x0F, 16).add(Attributes.BIT_OFFSET, 0x0F, 8);
        final DebugData member = new DebugData(Tags.MEMBER, 2, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "t");
        final DebugData structType = new DebugData(Tags.STRUCTURE_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{member});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, structType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().get(0).memberCount());
    }

    @Test
    public void testClassType() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.TYPE, 0x0F, 1).add(Attributes.DATA_MEMBER_LOCATION, 0x0F, 0);
        final DebugData member = new DebugData(Tags.MEMBER, 2, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "C");
        final DebugData classType = new DebugData(Tags.CLASS_TYPE, 3, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{member});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, classType);

        Assert.assertEquals(1, factory.getBaseTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().size());
        final DebugType type = factory.getStructTypes().get(0);
        Assert.assertEquals("C", type.asTypeName());
        Assert.assertFalse(type.fitsIntoInt());
        Assert.assertFalse(type.fitsIntoLong());
        Assert.assertFalse(type.isValue());
        Assert.assertFalse(type.isDebugObject());
        Assert.assertFalse(type.isLocation());
        Assert.assertFalse(type.hasArrayElements());
        Assert.assertTrue(type.hasMembers());
        Assert.assertEquals(1, type.memberCount());
    }

    @Test
    public void testClassTypeWithInheritance() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder baseClassMemberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.TYPE, 0x0F, 1);
        final DebugData baseClassMember = new DebugData(Tags.MEMBER, 2, baseClassMemberAttr.attributeInfo(), baseClassMemberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder baseClassAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "A");
        final DebugData baseClass = new DebugData(Tags.CLASS_TYPE, 3, baseClassAttr.attributeInfo(), baseClassAttr.attributeValues(), new DebugData[]{baseClassMember});

        final AttributeBuilder inhAttr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 3);
        final DebugData inheritance = new DebugData(Tags.INHERITANCE, 4, inhAttr.attributeInfo(), inhAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "b").add(Attributes.TYPE, 0x0F, 1);
        final DebugData member = new DebugData(Tags.MEMBER, 5, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "B");
        final DebugData classType = new DebugData(Tags.CLASS_TYPE, 6, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{inheritance, member});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, classType, baseClass);

        Assert.assertEquals(2, factory.getStructTypes().size());
        Assert.assertEquals(2, factory.getStructTypes().get(1).memberCount());
    }

    @Test
    public void testClassTypeSelfInheritance() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder inhAttr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 6);
        final DebugData inheritance = new DebugData(Tags.INHERITANCE, 4, inhAttr.attributeInfo(), inhAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "b").add(Attributes.TYPE, 0x0F, 1);
        final DebugData member = new DebugData(Tags.MEMBER, 5, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "B");
        final DebugData classType = new DebugData(Tags.CLASS_TYPE, 6, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{inheritance, member});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, classType);

        Assert.assertEquals(1, factory.getStructTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().get(0).memberCount());
    }

    @Test
    public void testClassTypeInheritanceMissingReferenceAttribute() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder baseClassMemberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.TYPE, 0x0F, 1);
        final DebugData baseClassMember = new DebugData(Tags.MEMBER, 2, baseClassMemberAttr.attributeInfo(), baseClassMemberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder baseClassAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "A");
        final DebugData baseClass = new DebugData(Tags.CLASS_TYPE, 3, baseClassAttr.attributeInfo(), baseClassAttr.attributeValues(), new DebugData[]{baseClassMember});

        final AttributeBuilder inhAttr = AttributeBuilder.create();
        final DebugData inheritance = new DebugData(Tags.INHERITANCE, 4, inhAttr.attributeInfo(), inhAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "b").add(Attributes.TYPE, 0x0F, 1);
        final DebugData member = new DebugData(Tags.MEMBER, 5, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "B");
        final DebugData classType = new DebugData(Tags.CLASS_TYPE, 6, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{inheritance, member});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, classType, baseClass);

        Assert.assertEquals(2, factory.getStructTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().get(1).memberCount());
    }

    @Test
    public void testClassTypeInheritanceMissingReference() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder inhAttr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 3);
        final DebugData inheritance = new DebugData(Tags.INHERITANCE, 4, inhAttr.attributeInfo(), inhAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "b").add(Attributes.TYPE, 0x0F, 1);
        final DebugData member = new DebugData(Tags.MEMBER, 5, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "B");
        final DebugData classType = new DebugData(Tags.CLASS_TYPE, 6, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{inheritance, member});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, classType);

        Assert.assertEquals(1, factory.getStructTypes().size());
        Assert.assertEquals(1, factory.getStructTypes().get(0).memberCount());
    }

    @Test
    public void testVariantType() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.TYPE, 0x0F, 1).add(Attributes.DATA_MEMBER_LOCATION, 0x0F, 0);
        final DebugData member = new DebugData(Tags.MEMBER, 2, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder discAttr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1).add(Attributes.DATA_MEMBER_LOCATION, 0x0F, 0);
        final DebugData disc = new DebugData(Tags.MEMBER, 3, discAttr.attributeInfo(), discAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder variantAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "var").add(Attributes.DISCR_VALUE, 0x0F, 0);
        final DebugData variant = new DebugData(Tags.VARIANT, 4, variantAttr.attributeInfo(), variantAttr.attributeValues(), new DebugData[]{member});

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.DISCR, 0x0F, 3);
        final DebugData variantType = new DebugData(Tags.VARIANT_PART, 5, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{disc, variant});

        final AttributeBuilder structAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "v");
        final DebugData struct = new DebugData(Tags.STRUCTURE_TYPE, 6, structAttr.attributeInfo(), structAttr.attributeValues(), new DebugData[]{variantType});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, struct);

        Assert.assertEquals(1, factory.getVariantTypes().size());
    }

    @Test
    public void testVariantTypeMissingDiscriminatorAttribute() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.TYPE, 0x0F, 1).add(Attributes.DATA_MEMBER_LOCATION, 0x0F, 0);
        final DebugData member = new DebugData(Tags.MEMBER, 2, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder discAttr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1).add(Attributes.DATA_MEMBER_LOCATION, 0x0F, 0);
        final DebugData disc = new DebugData(Tags.MEMBER, 3, discAttr.attributeInfo(), discAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder variantAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "var").add(Attributes.DISCR_VALUE, 0x0F, 0);
        final DebugData variant = new DebugData(Tags.VARIANT, 4, variantAttr.attributeInfo(), variantAttr.attributeValues(), new DebugData[]{member});

        final AttributeBuilder attr = AttributeBuilder.create();
        final DebugData variantType = new DebugData(Tags.VARIANT_PART, 5, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{disc, variant});

        final AttributeBuilder structAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "v");
        final DebugData struct = new DebugData(Tags.STRUCTURE_TYPE, 6, structAttr.attributeInfo(), structAttr.attributeValues(), new DebugData[]{variantType});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, struct);

        Assert.assertEquals(0, factory.getVariantTypes().size());
    }

    @Test
    public void testVariantTypeMissingDiscriminant() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder memberAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.TYPE, 0x0F, 1).add(Attributes.DATA_MEMBER_LOCATION, 0x0F, 0);
        final DebugData member = new DebugData(Tags.MEMBER, 2, memberAttr.attributeInfo(), memberAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder variantAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "var").add(Attributes.DISCR_VALUE, 0x0F, 0);
        final DebugData variant = new DebugData(Tags.VARIANT, 3, variantAttr.attributeInfo(), variantAttr.attributeValues(), new DebugData[]{member});

        final AttributeBuilder attr = AttributeBuilder.create().add(Attributes.DISCR, 0x0F, 1);
        final DebugData variantType = new DebugData(Tags.VARIANT_PART, 4, attr.attributeInfo(), attr.attributeValues(), new DebugData[]{variant});

        final AttributeBuilder structAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "v");
        final DebugData struct = new DebugData(Tags.STRUCTURE_TYPE, 5, structAttr.attributeInfo(), structAttr.attributeValues(), new DebugData[]{variantType});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, struct);

        Assert.assertEquals(0, factory.getVariantTypes().size());
    }

    @Test
    public void testEnumType() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder aAttr = AttributeBuilder.create().add(Attributes.CONST_VALUE, 0x0F, 0).add(Attributes.NAME, 0x08, "a");
        final AttributeBuilder bAttr = AttributeBuilder.create().add(Attributes.CONST_VALUE, 0x0F, 1).add(Attributes.NAME, 0x08, "b");

        final DebugData a = new DebugData(Tags.ENUMERATOR, 2, aAttr.attributeInfo(), aAttr.attributeValues(), new DebugData[0]);
        final DebugData b = new DebugData(Tags.ENUMERATOR, 3, bAttr.attributeInfo(), bAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder enumAttr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData e = new DebugData(Tags.ENUMERATION_TYPE, 4, enumAttr.attributeInfo(), enumAttr.attributeValues(), new DebugData[]{a, b});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, e);

        Assert.assertEquals(1, factory.getEnumTypes().size());
    }

    @Test
    public void testEnumTypeMissingBaseTypeAttribute() {
        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder aAttr = AttributeBuilder.create().add(Attributes.CONST_VALUE, 0x0F, 0).add(Attributes.NAME, 0x08, "a");
        final AttributeBuilder bAttr = AttributeBuilder.create().add(Attributes.CONST_VALUE, 0x0F, 1).add(Attributes.NAME, 0x08, "b");

        final DebugData a = new DebugData(Tags.ENUMERATOR, 2, aAttr.attributeInfo(), aAttr.attributeValues(), new DebugData[0]);
        final DebugData b = new DebugData(Tags.ENUMERATOR, 3, bAttr.attributeInfo(), bAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder enumAttr = AttributeBuilder.create();
        final DebugData e = new DebugData(Tags.ENUMERATION_TYPE, 4, enumAttr.attributeInfo(), enumAttr.attributeValues(), new DebugData[]{a, b});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, baseType, e);

        Assert.assertEquals(0, factory.getEnumTypes().size());
    }

    @Test
    public void testEnumTypeMissingBaseType() {
        final AttributeBuilder aAttr = AttributeBuilder.create().add(Attributes.CONST_VALUE, 0x0F, 0).add(Attributes.NAME, 0x08, "a");
        final AttributeBuilder bAttr = AttributeBuilder.create().add(Attributes.CONST_VALUE, 0x0F, 1).add(Attributes.NAME, 0x08, "b");

        final DebugData a = new DebugData(Tags.ENUMERATOR, 2, aAttr.attributeInfo(), aAttr.attributeValues(), new DebugData[0]);
        final DebugData b = new DebugData(Tags.ENUMERATOR, 3, bAttr.attributeInfo(), bAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder enumAttr = AttributeBuilder.create().add(Attributes.TYPE, 0x0F, 1);
        final DebugData e = new DebugData(Tags.ENUMERATION_TYPE, 4, enumAttr.attributeInfo(), enumAttr.attributeValues(), new DebugData[]{a, b});

        final TestObjectFactory factory = new TestObjectFactory();
        parseCompilationUnit(factory, e);

        Assert.assertEquals(0, factory.getEnumTypes().size());
    }

    @Test
    public void testFunction() {
        final DebugLineMap lineMap = new DebugLineMap(Path.of(""));
        lineMap.add(0, 1);
        lineMap.add(10, 2);
        final Source s = Source.newBuilder(WasmLanguage.ID, "", "test").internal(true).build();
        final AttributeBuilder funcAttr = AttributeBuilder.create().add(Attributes.DECL_FILE, 0x0F, 0).add(Attributes.NAME, 0x08, "func").add(Attributes.LOW_PC, 0x0F, 0).add(Attributes.HIGH_PC, 0x0F,
                        10).add(Attributes.FRAME_BASE, 0x09, new byte[]{});
        final DebugData func = new DebugData(Tags.SUBPROGRAM, 1, funcAttr.attributeInfo(), funcAttr.attributeValues(), new DebugData[0]);

        final TestObjectFactory factory = new TestObjectFactory();
        final DebugParserContext context = parseCompilationUnit(factory, lineMap, s, func);

        Assert.assertEquals(1, context.functions().size());
    }

    @Test
    public void testFunctionMissingLineMap() {
        final Source s = Source.newBuilder(WasmLanguage.ID, "", "test").internal(true).build();
        final AttributeBuilder funcAttr = AttributeBuilder.create().add(Attributes.DECL_FILE, 0x0F, 0).add(Attributes.NAME, 0x08, "func").add(Attributes.LOW_PC, 0x0F, 0).add(Attributes.HIGH_PC, 0x0F,
                        10).add(Attributes.FRAME_BASE, 0x09, new byte[]{});
        final DebugData func = new DebugData(Tags.SUBPROGRAM, 1, funcAttr.attributeInfo(), funcAttr.attributeValues(), new DebugData[0]);

        final TestObjectFactory factory = new TestObjectFactory();
        final DebugParserContext context = parseCompilationUnit(factory, null, s, func);

        Assert.assertEquals(0, context.functions().size());
    }

    @Test
    public void testFunctionMissingSource() {
        final DebugLineMap lineMap = new DebugLineMap(Path.of(""));
        lineMap.add(0, 1);
        lineMap.add(10, 2);
        final AttributeBuilder funcAttr = AttributeBuilder.create().add(Attributes.DECL_FILE, 0x0F, 0).add(Attributes.NAME, 0x08, "func").add(Attributes.LOW_PC, 0x0F, 0).add(Attributes.HIGH_PC, 0x0F,
                        10).add(Attributes.FRAME_BASE, 0x09, new byte[]{});
        final DebugData func = new DebugData(Tags.SUBPROGRAM, 1, funcAttr.attributeInfo(), funcAttr.attributeValues(), new DebugData[0]);

        final TestObjectFactory factory = new TestObjectFactory();
        final DebugParserContext context = parseCompilationUnit(factory, lineMap, null, func);

        Assert.assertEquals(1, context.functions().size());
    }

    @Test
    public void testInlinedFunction() {
        final DebugLineMap lineMap = new DebugLineMap(Path.of(""));
        lineMap.add(0, 1);
        lineMap.add(10, 2);
        final Source s = Source.newBuilder(WasmLanguage.ID, "", "test").internal(true).build();
        final AttributeBuilder funcAttr = AttributeBuilder.create().add(Attributes.DECL_FILE, 0x0F, 0).add(Attributes.NAME, 0x08, "func").add(Attributes.LOW_PC, 0x0F, 0).add(Attributes.HIGH_PC, 0x0F,
                        10).add(Attributes.FRAME_BASE, 0x09, new byte[]{}).add(Attributes.INLINE, 0x0F, 1);
        final DebugData func = new DebugData(Tags.SUBPROGRAM, 1, funcAttr.attributeInfo(), funcAttr.attributeValues(), new DebugData[0]);

        final TestObjectFactory factory = new TestObjectFactory();
        final DebugParserContext context = parseCompilationUnit(factory, lineMap, s, func);

        Assert.assertEquals(0, context.functions().size());
    }

    @Test
    public void testFunctionMissingFrameBase() {
        final DebugLineMap lineMap = new DebugLineMap(Path.of(""));
        lineMap.add(0, 1);
        lineMap.add(10, 2);
        final Source s = Source.newBuilder(WasmLanguage.ID, "", "test").internal(true).build();
        final AttributeBuilder funcAttr = AttributeBuilder.create().add(Attributes.DECL_FILE, 0x0F, 0).add(Attributes.NAME, 0x08, "func").add(Attributes.LOW_PC, 0x0F, 0).add(Attributes.HIGH_PC, 0x0F,
                        10);
        final DebugData func = new DebugData(Tags.SUBPROGRAM, 1, funcAttr.attributeInfo(), funcAttr.attributeValues(), new DebugData[0]);

        final TestObjectFactory factory = new TestObjectFactory();
        final DebugParserContext context = parseCompilationUnit(factory, lineMap, s, func);

        Assert.assertEquals(0, context.functions().size());
    }

    @Test
    public void testFunctionWithVariables() {
        final DebugLineMap lineMap = new DebugLineMap(Path.of(""));
        lineMap.add(0, 1);
        lineMap.add(10, 2);
        final Source s = Source.newBuilder(WasmLanguage.ID, "", "test").internal(true).build();

        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder var1Attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "a").add(Attributes.TYPE, 0x0F, 1).add(Attributes.LOCATION, 0x09, new byte[0]);
        final DebugData var1 = new DebugData(Tags.FORMAL_PARAMETER, 2, var1Attr.attributeInfo(), var1Attr.attributeValues(), new DebugData[0]);

        final AttributeBuilder var2Attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "b").add(Attributes.TYPE, 0x0F, 1).add(Attributes.LOCATION, 0x09, new byte[0]);
        final DebugData var2 = new DebugData(Tags.FORMAL_PARAMETER, 3, var2Attr.attributeInfo(), var2Attr.attributeValues(), new DebugData[0]);

        final AttributeBuilder var3Attr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "c").add(Attributes.TYPE, 0x0F, 1).add(Attributes.LOCATION, 0x09, new byte[0]).add(Attributes.DECL_FILE,
                        0x0F, 0).add(Attributes.DECL_LINE, 0x0F, 1);
        final DebugData var3 = new DebugData(Tags.VARIABLE, 4, var3Attr.attributeInfo(), var3Attr.attributeValues(), new DebugData[0]);

        final AttributeBuilder funcAttr = AttributeBuilder.create().add(Attributes.DECL_FILE, 0x0F, 0).add(Attributes.NAME, 0x08, "func").add(Attributes.LOW_PC, 0x0F, 0).add(Attributes.HIGH_PC, 0x0F,
                        10).add(Attributes.FRAME_BASE, 0x09, new byte[]{});
        final DebugData func = new DebugData(Tags.SUBPROGRAM, 3, funcAttr.attributeInfo(), funcAttr.attributeValues(), new DebugData[]{var1, var2, var3});

        final TestObjectFactory factory = new TestObjectFactory();
        final DebugParserContext context = parseCompilationUnit(factory, lineMap, s, baseType, func);

        Assert.assertEquals(1, context.functions().size());
        final DebugFunction function = context.functions().get(0);
        Assert.assertEquals(3, function.locals().memberCount());
    }

    @Test
    public void testFunctionWithVariableMissingTypeAttribute() {
        final DebugLineMap lineMap = new DebugLineMap(Path.of(""));
        lineMap.add(0, 1);
        lineMap.add(10, 2);
        final Source s = Source.newBuilder(WasmLanguage.ID, "", "test").internal(true).build();

        final AttributeBuilder baseAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "int").add(Attributes.ENCODING, 0x0F, AttributeEncodings.SIGNED).add(Attributes.BYTE_SIZE, 0x0F, 4);
        final DebugData baseType = new DebugData(Tags.BASE_TYPE, 1, baseAttr.attributeInfo(), baseAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder varAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "c").add(Attributes.LOCATION, 0x09, new byte[0]).add(Attributes.DECL_FILE,
                        0x0F, 0).add(Attributes.DECL_LINE, 0x0F, 1);
        final DebugData var = new DebugData(Tags.VARIABLE, 4, varAttr.attributeInfo(), varAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder funcAttr = AttributeBuilder.create().add(Attributes.DECL_FILE, 0x0F, 0).add(Attributes.NAME, 0x08, "func").add(Attributes.LOW_PC, 0x0F, 0).add(Attributes.HIGH_PC, 0x0F,
                        10).add(Attributes.FRAME_BASE, 0x09, new byte[]{});
        final DebugData func = new DebugData(Tags.SUBPROGRAM, 3, funcAttr.attributeInfo(), funcAttr.attributeValues(), new DebugData[]{var});

        final TestObjectFactory factory = new TestObjectFactory();
        final DebugParserContext context = parseCompilationUnit(factory, lineMap, s, baseType, func);

        Assert.assertEquals(1, context.functions().size());
        final DebugFunction function = context.functions().get(0);
        Assert.assertEquals(0, function.locals().memberCount());
    }

    @Test
    public void testFunctionWithVariableMissingType() {
        final DebugLineMap lineMap = new DebugLineMap(Path.of(""));
        lineMap.add(0, 1);
        lineMap.add(10, 2);
        final Source s = Source.newBuilder(WasmLanguage.ID, "", "test").internal(true).build();

        final AttributeBuilder varAttr = AttributeBuilder.create().add(Attributes.NAME, 0x08, "c").add(Attributes.TYPE, 0x0F, 1).add(Attributes.LOCATION, 0x09, new byte[0]).add(Attributes.DECL_FILE,
                        0x0F, 0).add(Attributes.DECL_LINE, 0x0F, 1);
        final DebugData var = new DebugData(Tags.VARIABLE, 4, varAttr.attributeInfo(), varAttr.attributeValues(), new DebugData[0]);

        final AttributeBuilder funcAttr = AttributeBuilder.create().add(Attributes.DECL_FILE, 0x0F, 0).add(Attributes.NAME, 0x08, "func").add(Attributes.LOW_PC, 0x0F, 0).add(Attributes.HIGH_PC, 0x0F,
                        10).add(Attributes.FRAME_BASE, 0x09, new byte[]{});
        final DebugData func = new DebugData(Tags.SUBPROGRAM, 3, funcAttr.attributeInfo(), funcAttr.attributeValues(), new DebugData[]{var});

        final TestObjectFactory factory = new TestObjectFactory();
        final DebugParserContext context = parseCompilationUnit(factory, lineMap, s, func);

        Assert.assertEquals(1, context.functions().size());
        final DebugFunction function = context.functions().get(0);
        Assert.assertEquals(0, function.locals().memberCount());
    }
}
