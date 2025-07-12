/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.collection.ByteArrayList;

public abstract class AbstractBinarySuite {
    protected static final byte[] EMPTY_BYTES = {};

    protected static void runRuntimeTest(byte[] binary, Consumer<Context.Builder> options, Consumer<Value> testCase) throws IOException {
        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        contextBuilder.allowExperimentalOptions(true);
        if (options != null) {
            options.accept(contextBuilder);
        }
        try (Context context = contextBuilder.build()) {
            Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(binary), "main");
            Source source = sourceBuilder.build();
            testCase.accept(context.eval(source).newInstance().getMember("exports"));
        }
    }

    protected static void runRuntimeTest(byte[] binary, Consumer<Value> testCase) throws IOException {
        runRuntimeTest(binary, null, testCase);
    }

    protected static void runParserTest(byte[] binary, Consumer<Context.Builder> options, BiConsumer<Context, Source> testCase) throws IOException {
        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        if (options != null) {
            options.accept(contextBuilder);
        }
        try (Context context = contextBuilder.build()) {
            Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(binary), "main");
            Source source = sourceBuilder.build();
            testCase.accept(context, source);
        }
    }

    protected static void runParserTest(byte[] binary, BiConsumer<Context, Source> testCase) throws IOException {
        runParserTest(binary, null, testCase);
    }

    protected static BinaryBuilder newBuilder() {
        return new BinaryBuilder();
    }

    private static byte getByte(String hexString) {
        return Byte.parseByte(hexString, 16);
    }

    private static final class BinaryTypes {

        private final List<byte[]> paramEntries = new ArrayList<>();
        private final List<byte[]> resultEntries = new ArrayList<>();

        private void add(byte[] params, byte[] results) {
            paramEntries.add(params);
            resultEntries.add(results);
        }

        private byte[] generateTypeSection() {
            ByteArrayList b = new ByteArrayList();
            b.add(getByte("01"));
            b.add((byte) 0); // length is patched in the end
            b.add((byte) paramEntries.size());
            for (int i = 0; i < paramEntries.size(); i++) {
                b.add(getByte("60"));
                byte[] params = paramEntries.get(i);
                byte[] results = resultEntries.get(i);
                b.add((byte) params.length);
                for (byte param : params) {
                    b.add(param);
                }
                b.add((byte) results.length);
                for (byte result : results) {
                    b.add(result);
                }
            }
            b.set(1, (byte) (b.size() - 2));
            return b.toArray();
        }
    }

    private static final class BinaryTables {
        private final ByteArrayList tables = new ByteArrayList();

        private void add(byte initSize, byte maxSize, byte elemType) {
            tables.add(initSize);
            tables.add(maxSize);
            tables.add(elemType);
        }

        private byte[] generateTableSection() {
            ByteArrayList b = new ByteArrayList();
            b.add(getByte("04"));
            b.add((byte) 0); // length is patched in the end
            final int tableCount = tables.size() / 3;
            b.add((byte) tableCount);
            for (int i = 0; i < tables.size(); i += 3) {
                b.add(tables.get(i + 2));
                b.add(getByte("01"));
                b.add(tables.get(i));
                b.add(tables.get(i + 1));
            }
            b.set(1, (byte) (b.size() - 2));
            return b.toArray();
        }
    }

    private static final class BinaryMemories {
        private final ByteArrayList memories = new ByteArrayList();

        private void add(byte initSize, byte maxSize) {
            memories.add(initSize);
            memories.add(maxSize);
        }

        private byte[] generateMemorySection() {
            ByteArrayList b = new ByteArrayList();
            b.add(getByte("05"));
            b.add((byte) 0); // length is patched in the end
            final int memoryCount = memories.size() / 2;
            b.add((byte) memoryCount);
            for (int i = 0; i < memories.size(); i += 2) {
                b.add(getByte("01"));
                b.add(memories.get(i));
                b.add(memories.get(i + 1));
            }
            b.set(1, (byte) (b.size() - 2));
            return b.toArray();
        }
    }

    private static final class BinaryFunctions {
        private final ByteArrayList types = new ByteArrayList();
        private final List<byte[]> localEntries = new ArrayList<>();
        private final List<byte[]> codeEntries = new ArrayList<>();

        private void add(byte typeIndex, byte[] locals, byte[] code) {
            types.add(typeIndex);
            localEntries.add(locals);
            codeEntries.add(code);
        }

        private byte[] generateFunctionSection() {
            ByteArrayList b = new ByteArrayList();
            b.add(getByte("03"));
            b.add((byte) 0); // length is patched at the end
            final int functionCount = types.size();
            b.add((byte) functionCount);
            for (int i = 0; i < functionCount; i++) {
                b.add(types.get(i));
            }
            b.set(1, (byte) (b.size() - 2));
            return b.toArray();
        }

        private byte[] generateCodeSection() {
            ByteArrayList b = new ByteArrayList();
            b.add(getByte("0A"));
            b.add((byte) 0); // length is patched at the end
            final int functionCount = types.size();
            b.add((byte) functionCount);
            for (int i = 0; i < functionCount; i++) {
                byte[] locals = localEntries.get(i);
                byte[] code = codeEntries.get(i);
                int length = 1 + locals.length + code.length;
                b.add((byte) length);
                b.add((byte) locals.length);
                for (byte l : locals) {
                    b.add(l);
                }
                for (byte op : code) {
                    b.add(op);
                }
            }
            b.set(1, (byte) (b.size() - 2));
            return b.toArray();
        }
    }

    private static final class BinaryExports {
        private final ByteArrayList types = new ByteArrayList();
        private final ByteArrayList indices = new ByteArrayList();
        private final List<byte[]> names = new ArrayList<>();

        private void addFunctionExport(byte functionIndex, String name) {
            types.add(getByte("00"));
            indices.add(functionIndex);
            names.add(name.getBytes(StandardCharsets.UTF_8));
        }

        private byte[] generateExportSection() {
            ByteArrayList b = new ByteArrayList();
            b.add(getByte("07"));
            b.add((byte) 0); // length is patched at the end
            b.add((byte) types.size());
            for (int i = 0; i < types.size(); i++) {
                final byte[] name = names.get(i);
                b.add((byte) name.length);
                for (byte value : name) {
                    b.add(value);
                }
                b.add(types.get(i));
                b.add(indices.get(i));
            }
            b.set(1, (byte) (b.size() - 2));
            return b.toArray();
        }
    }

    private static final class BinaryElements {
        private final List<byte[]> elementEntries = new ArrayList<>();

        private void add(byte[] elements) {
            elementEntries.add(elements);
        }

        private byte[] generateElementSection() {
            ByteArrayList b = new ByteArrayList();
            b.add(getByte("09"));
            b.add((byte) 0); // length is patched at the end
            b.add((byte) elementEntries.size());
            for (byte[] elementEntry : elementEntries) {
                for (byte e : elementEntry) {
                    b.add(e);
                }
            }
            b.set(1, (byte) (b.size() - 2));
            return b.toArray();
        }
    }

    private static final class BinaryDatas {
        private final List<byte[]> dataEntries = new ArrayList<>();

        private void add(byte[] data) {
            dataEntries.add(data);
        }

        private byte[] generateDataCountSection() {
            ByteArrayList b = new ByteArrayList();
            b.add(getByte("0C"));
            b.add((byte) 1);
            b.add((byte) dataEntries.size());
            return b.toArray();
        }

        private byte[] generateDataSection() {
            ByteArrayList b = new ByteArrayList();
            b.add(getByte("0B"));
            b.add((byte) 0); // length is patched at the end
            b.add((byte) dataEntries.size());
            for (byte[] dataEntry : dataEntries) {
                for (byte d : dataEntry) {
                    b.add(d);
                }
            }
            b.set(1, (byte) (b.size() - 2));
            return b.toArray();
        }
    }

    private static final class BinaryGlobals {
        private final ByteArrayList mutabilities = new ByteArrayList();
        private final ByteArrayList valueTypes = new ByteArrayList();
        private final List<byte[]> expressions = new ArrayList<>();

        private void add(byte mutability, byte valueType, byte[] expression) {
            mutabilities.add(mutability);
            valueTypes.add(valueType);
            expressions.add(expression);
        }

        private byte[] generateGlobalSection() {
            ByteArrayList b = new ByteArrayList();
            b.add(getByte("06"));
            b.add((byte) 0); // length is patched at the end
            b.add((byte) mutabilities.size());
            for (int i = 0; i < mutabilities.size(); i++) {
                b.add(valueTypes.get(i));
                b.add(mutabilities.get(i));
                for (byte e : expressions.get(i)) {
                    b.add(e);
                }
            }
            b.set(1, (byte) (b.size() - 2));
            return b.toArray();
        }
    }

    private static final class BinaryCustomSections {
        private final List<byte[]> names = new ArrayList<>();
        private final List<byte[]> sections = new ArrayList<>();

        private void add(String name, byte[] section) {
            names.add(name.getBytes(StandardCharsets.UTF_8));
            sections.add(section);
        }

        private byte[] generateCustomSections() {
            ByteArrayList b = new ByteArrayList();
            for (int i = 0; i < names.size(); i++) {
                b.add(getByte("00"));
                final byte[] name = names.get(i);
                final byte[] section = sections.get(i);
                final int size = 1 + name.length + section.length;
                b.add((byte) size); // length is patched at the end
                b.add((byte) name.length);
                b.addRange(name, 0, name.length);
                b.addRange(section, 0, section.length);
            }
            return b.toArray();
        }
    }

    protected static class BinaryBuilder {
        private final BinaryTypes binaryTypes = new BinaryTypes();
        private final BinaryTables binaryTables = new BinaryTables();
        private final BinaryMemories binaryMemories = new BinaryMemories();
        private final BinaryFunctions binaryFunctions = new BinaryFunctions();
        private final BinaryExports binaryExports = new BinaryExports();
        private final BinaryElements binaryElements = new BinaryElements();
        private final BinaryDatas binaryDatas = new BinaryDatas();
        private final BinaryGlobals binaryGlobals = new BinaryGlobals();

        private final BinaryCustomSections binaryCustomSections = new BinaryCustomSections();

        public BinaryBuilder addType(byte[] params, byte[] results) {
            binaryTypes.add(params, results);
            return this;
        }

        public BinaryBuilder addTable(byte initSize, byte maxSize, byte elemType) {
            binaryTables.add(initSize, maxSize, elemType);
            return this;
        }

        public BinaryBuilder addMemory(byte initSize, byte maxSize) {
            binaryMemories.add(initSize, maxSize);
            return this;
        }

        public BinaryBuilder addFunction(byte typeIndex, byte[] locals, String hexCode) {
            binaryFunctions.add(typeIndex, locals, WasmTestUtils.hexStringToByteArray(hexCode));
            return this;
        }

        public BinaryBuilder addFunctionExport(byte functionIndex, String name) {
            binaryExports.addFunctionExport(functionIndex, name);
            return this;
        }

        public BinaryBuilder addElements(String hexCode) {
            binaryElements.add(WasmTestUtils.hexStringToByteArray(hexCode));
            return this;
        }

        public BinaryBuilder addData(String hexCode) {
            binaryDatas.add(WasmTestUtils.hexStringToByteArray(hexCode));
            return this;
        }

        public BinaryBuilder addGlobal(byte mutability, byte valueType, String hexCode) {
            binaryGlobals.add(mutability, valueType, WasmTestUtils.hexStringToByteArray(hexCode));
            return this;
        }

        public BinaryBuilder addCustomSection(String name, byte[] section) {
            binaryCustomSections.add(name, section);
            return this;
        }

        public byte[] build() {
            final byte[] preamble = {
                            getByte("00"),
                            getByte("61"),
                            getByte("73"),
                            getByte("6D"),
                            getByte("01"),
                            getByte("00"),
                            getByte("00"),
                            getByte("00")
            };
            final byte[] typeSection = binaryTypes.generateTypeSection();
            final byte[] functionSection = binaryFunctions.generateFunctionSection();
            final byte[] tableSection = binaryTables.generateTableSection();
            final byte[] memorySection = binaryMemories.generateMemorySection();
            final byte[] globalSection = binaryGlobals.generateGlobalSection();
            final byte[] exportSection = binaryExports.generateExportSection();
            final byte[] elementSection = binaryElements.generateElementSection();
            final byte[] dataCountSection = binaryDatas.generateDataCountSection();
            final byte[] codeSection = binaryFunctions.generateCodeSection();
            final byte[] dataSection = binaryDatas.generateDataSection();
            final byte[] customSections = binaryCustomSections.generateCustomSections();
            final int totalLength = preamble.length + typeSection.length + functionSection.length + tableSection.length + memorySection.length + globalSection.length + exportSection.length +
                            elementSection.length + dataCountSection.length + codeSection.length + dataSection.length + customSections.length;
            final byte[] binary = new byte[totalLength];
            int length = 0;
            System.arraycopy(preamble, 0, binary, length, preamble.length);
            length += preamble.length;
            System.arraycopy(typeSection, 0, binary, length, typeSection.length);
            length += typeSection.length;
            System.arraycopy(functionSection, 0, binary, length, functionSection.length);
            length += functionSection.length;
            System.arraycopy(tableSection, 0, binary, length, tableSection.length);
            length += tableSection.length;
            System.arraycopy(memorySection, 0, binary, length, memorySection.length);
            length += memorySection.length;
            System.arraycopy(globalSection, 0, binary, length, globalSection.length);
            length += globalSection.length;
            System.arraycopy(exportSection, 0, binary, length, exportSection.length);
            length += exportSection.length;
            System.arraycopy(elementSection, 0, binary, length, elementSection.length);
            length += elementSection.length;
            System.arraycopy(dataCountSection, 0, binary, length, dataCountSection.length);
            length += dataCountSection.length;
            System.arraycopy(codeSection, 0, binary, length, codeSection.length);
            length += codeSection.length;
            System.arraycopy(dataSection, 0, binary, length, dataSection.length);
            length += dataSection.length;
            System.arraycopy(customSections, 0, binary, length, customSections.length);
            return binary;
        }
    }

    public static byte[] hexToBinary(String hex) {
        return WasmTestUtils.hexStringToByteArray("00 61 73 6D 01 00 00 00", hex);
    }
}
