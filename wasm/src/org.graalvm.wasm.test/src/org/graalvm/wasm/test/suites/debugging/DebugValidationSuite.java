/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.test.WasmTestUtils.hexStringToByteArray;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.collection.ByteArrayList;
import org.graalvm.wasm.debugging.encoding.Attributes;
import org.graalvm.wasm.debugging.parser.DebugUtil;
import org.graalvm.wasm.test.AbstractBinarySuite;
import org.junit.Test;

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.SourceSection;

public class DebugValidationSuite extends AbstractBinarySuite {

    private static AbstractBinarySuite.BinaryBuilder getDefaultDebugBuilder() {
        return newBuilder().addType(EMPTY_INTS, EMPTY_INTS).addFunction(0, EMPTY_INTS, "0B").addFunctionExport(0, "_main");
    }

    @Test
    public void testEmptyFilePathsInLineSectionHeader() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 00 00 00 00 04 01 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testDebugInfoUnitLengthTooSmall() throws IOException {
        // .debug_info
        // unit_length: 1
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev_index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty

        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("01 00 00 00 04 00 00 00 00 00 04 01 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 16 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testDebugInfoUnitLengthTooLarge() throws IOException {
        // .debug_info
        // unit_length: 15
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev_index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty

        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0F 00 00 00 04 00 00 00 00 00 04 01 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 16 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testDebugInfoUnsupportedVersion5() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 5.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev_index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty

        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 05 00 00 00 00 00 04 01 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testDebugInfoUnsupportedVersion3() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 3.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev_index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty

        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 03 00 00 00 00 00 04 01 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testDebugInfoAbbrevOffsetOutsideAbbrevSection() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 12
        // address_size: 4
        // abbrev_index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty

        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 0C 00 00 00 04 01 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testDebugInfoInvalidAddressSize() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 1
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 00 00 00 00 01 01 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testDebugInfoInvalidAbbrevIndex() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 2
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 00 00 00 00 04 02 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testDebugInfoMissingAttribute() throws IOException {
        // .debug_info
        // unit_length: 8
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("08 00 00 00 04 00 00 00 00 00 04 01")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testDebugInfoUnsupportedLanguage() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: 3
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 00 00 00 00 04 01 03 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testDebugInfoStmtListOutsideLineSection() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 16
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 00 00 00 00 04 01 02 10 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testDebugInfoInvalidWindowsCompDir() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: >
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 00 00 00 00 04 01 02 00 3E 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testMissingAbbrevSection() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 00 00 00 00 04 01 02 00 2F 00")).addCustomSection(".debug_line",
                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testAbbrevSectionEntryWithoutEndBytes() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 00 00 00 00 04 01 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testAbbrevSectionWithoutEndByte() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 00 00 00 00 04 01 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testMissingLineSection() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 00 00 00 00 04 01 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testMissingStrSection() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: ptr to str section
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0F 00 00 00 04 00 00 00 00 00 04 01 02 00 00 00 00 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 0E 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).build();
        runTest(data);
    }

    @Test
    public void testDebugInfoCompDirInStrSection() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: ptr to str section ("/")
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0F 00 00 00 04 00 00 00 00 00 04 01 02 00 00 00 00 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 0E 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).addCustomSection(".debug_str", hexStringToByteArray("2F 00")).build();
        runTest(data);
    }

    @Test
    public void testStrSectionMissingNulByte() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: ptr to str section ("/")
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 15
        // version: 4.0
        // header_length: 9
        // min_instr_length: 0
        // max_ops_per_instr: 1
        // default_instr_stmt: 0
        // line_base: 0
        // opcodeBase: 0
        // paths: empty
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0F 00 00 00 04 00 00 00 00 00 04 01 02 00 00 00 00 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 0E 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("0F 00 00 00 04 00 09 00 00 00 00 01 00 00 00 00 00 00 00")).addCustomSection(".debug_str", hexStringToByteArray("2F")).build();
        runTest(data);
    }

    @Test
    public void testLineSectionHeaderZeroMaxOpsPerInstr() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 38
        // version: 4.0
        // header_length: 27
        // min_instr_length: 1
        // max_ops_per_instr: 0
        // default_instr_stmt: 1
        // line_base: -5
        // line_range: 14
        // opcodeBase: 13
        // standard_opcode_lengths: 0 1 1 1 1 0 0 0 1 0 0 1
        // include_directories: empty
        // file_names: a.c
        // special opcode: 0x20 (divides by max_ops_per_instr)
        // special opcode: 0x20
        // DW_LNE_end_sequence
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 00 00 00 00 04 01 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("26 00 00 00 04 00 1B 00 00 00 01 00 01 FB 0E 0D 00 01 01 01 01 00 00 00 01 00 00 01 00 61 2E 63 00 00 00 00 00 20 20 00 01 01")).build();
        runTest(data);
    }

    @Test
    public void testLineSectionHeaderZeroLineRange() throws IOException {
        // .debug_info
        // unit_length: 12
        // version: 4.0
        // debug_abbrev_offset: 0
        // address_size: 4
        // abbrev index: 1
        // language: c (2)
        // stmt_list: 0
        // comp_dir: /
        //
        // .debug_abbrev:
        // index: 1
        // tag: compilation unit (0x11)
        // children: false
        // attribute name: language (0x13)
        // attribute form: 0x0F
        // attribute name: stmt_list (0x10)
        // attribute form: 0x0F
        // attribute name: comp_dir (0x1B)
        // attribute form: 0x08
        //
        // .debug_line:
        // unit_length: 38
        // version: 4.0
        // header_length: 27
        // min_instr_length: 1
        // max_ops_per_instr: 1
        // default_instr_stmt: 1
        // line_base: -5
        // line_range: 0
        // opcodeBase: 13
        // standard_opcode_lengths: 0 1 1 1 1 0 0 0 1 0 0 1
        // include_directories: empty
        // file_names: a.c
        // special opcode: 0x20 (divides by line_range)
        // special opcode: 0x20
        // DW_LNE_end_sequence
        final byte[] data = getDefaultDebugBuilder().addCustomSection(".debug_info", hexStringToByteArray("0C 00 00 00 04 00 00 00 00 00 04 01 02 00 2F 00")).addCustomSection(".debug_abbrev",
                        hexStringToByteArray("01 11 00 13 0F 10 0F 1B 08 00 00 00")).addCustomSection(".debug_line",
                                        hexStringToByteArray("26 00 00 00 04 00 1B 00 00 00 01 01 01 FB 00 0D 00 01 01 01 01 00 00 00 01 00 00 01 00 61 2E 63 00 00 00 00 00 20 20 00 01 01")).build();
        runTest(data);
    }

    private static void runTest(byte[] data) throws IOException {
        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        final Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(data), "test_main").build();
        Context context = contextBuilder.build();
        Debugger debugger = Debugger.find(context.getEngine());
        DebuggerSession session = debugger.startSession(event -> {
        });
        try {
            Value val = context.eval(source).newInstance().getMember("exports");
            val.getMember("_main").execute();
            session.suspendNextExecution();
        } finally {
            session.close();
            context.close(true);
        }
    }

    private static byte[] createDebugAbbrevSection() {
        final ByteArrayList data = new ByteArrayList();
        // Compilation unit.
        data.addUnsignedInt32(1); // abbreviation index
        data.addUnsignedInt32(0x11); // DW_TAG_compile_unit
        data.add((byte) 1); // has children
        addAttribute(data, Attributes.LANGUAGE, 0x0F); // DW_AT_language, DW_FORM_udata
        addAttribute(data, Attributes.STMT_LIST, 0x0F); // DW_AT_stmt_list, DW_FORM_udata
        addAttribute(data, Attributes.COMP_DIR, 0x08); // DW_AT_comp_dir, DW_FORM_string
        addAttribute(data, Attributes.LOW_PC, 0x01); // DW_AT_low_pc, DW_FORM_addr
        addAttribute(data, Attributes.HIGH_PC, 0x06); // DW_AT_high_pc, DW_FORM_data4
        addAttribute(data, 0, 0);
        // Function.
        data.addUnsignedInt32(2); // abbreviation index
        data.addUnsignedInt32(0x2E); // DW_TAG_subprogram
        data.add((byte) 0); // has no children
        addAttribute(data, Attributes.NAME, 0x08); // DW_AT_name, DW_FORM_string
        addAttribute(data, Attributes.DECL_FILE, 0x0F); // DW_AT_decl_file, DW_FORM_udata
        addAttribute(data, Attributes.LOW_PC, 0x01); // DW_AT_low_pc, DW_FORM_addr
        addAttribute(data, Attributes.HIGH_PC, 0x06); // DW_AT_high_pc, DW_FORM_data4
        addAttribute(data, Attributes.FRAME_BASE, 0x18); // DW_AT_frame_base, DW_FORM_exprloc
        addAttribute(data, 0, 0);
        data.addUnsignedInt32(0); // end of abbreviations
        return data.toArray();
    }

    private static byte[] createDebugInfoSection(int functionStartOffset, int functionEndOffset, String compilationDirectory, String functionName) {
        final ByteArrayList data = new ByteArrayList();
        addU32(data, 0); // unit length placeholder
        addU16(data, 4); // version
        addU32(data, 0); // debug_abbrev_offset
        data.add((byte) 4); // address size
        data.addUnsignedInt32(1); // compilation unit abbreviation index
        data.addUnsignedInt32(2); // DW_LANG_C
        data.addUnsignedInt32(0); // DW_AT_stmt_list
        addString(data, compilationDirectory); // DW_AT_comp_dir
        addU32(data, functionStartOffset);
        addU32(data, functionEndOffset - functionStartOffset);
        data.addUnsignedInt32(2); // function abbreviation index
        addString(data, functionName);
        data.addUnsignedInt32(1); // DW_AT_decl_file
        addU32(data, functionStartOffset);
        addU32(data, functionEndOffset - functionStartOffset);
        data.addUnsignedInt32(0); // empty DW_AT_frame_base expression
        data.addUnsignedInt32(0); // end of compilation unit children
        final byte[] bytes = data.toArray();
        setU32(bytes, 0, bytes.length - 4);
        return bytes;
    }

    private static byte[] createDebugLineSection(int functionInstructionOffset, String fileName, int lineNumber) {
        final ByteArrayList data = new ByteArrayList();
        addU32(data, 0); // line section length placeholder
        addU16(data, 4); // version
        addU32(data, 0); // header length placeholder
        final int headerStartOffset = data.size();
        data.add((byte) 1); // minimum instruction length
        data.add((byte) 1); // maximum operations per instruction
        data.add((byte) 1); // default is_stmt
        data.add((byte) 0); // line base
        data.add((byte) 1); // line range
        data.add((byte) 13); // opcode base
        for (int i = 1; i < 13; i++) {
            data.add((byte) 0); // standard opcode length
        }
        data.add((byte) 0); // end of include directories
        addString(data, fileName);
        data.addUnsignedInt32(0); // directory index
        data.addUnsignedInt32(0); // modification time
        data.addUnsignedInt32(0); // file length
        data.add((byte) 0); // end of file names
        final int headerEndOffset = data.size();
        data.add((byte) 0); // extended opcode
        data.addUnsignedInt32(5); // extended opcode length
        data.add((byte) 2); // DW_LNE_set_address
        addU32(data, functionInstructionOffset);
        data.add((byte) 3); // DW_LNS_advance_line
        data.addSignedInt32(lineNumber - 1);
        data.add((byte) 1); // DW_LNS_copy
        data.add((byte) 0); // extended opcode
        data.addUnsignedInt32(1); // extended opcode length
        data.add((byte) 1); // DW_LNE_end_sequence
        final byte[] bytes = data.toArray();
        setU32(bytes, 0, bytes.length);
        setU32(bytes, 6, headerEndOffset - headerStartOffset);
        return bytes;
    }

    private static void addU16(ByteArrayList data, int value) {
        data.add((byte) (value & 0xFF));
        data.add((byte) ((value >>> 8) & 0xFF));
    }

    private static void addU32(ByteArrayList data, int value) {
        data.add((byte) (value & 0xFF));
        data.add((byte) ((value >>> 8) & 0xFF));
        data.add((byte) ((value >>> 16) & 0xFF));
        data.add((byte) ((value >>> 24) & 0xFF));
    }

    private static void setU32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private static void addAttribute(ByteArrayList data, int attribute, int form) {
        data.addUnsignedInt32(attribute);
        data.addUnsignedInt32(form);
    }

    private static void addString(ByteArrayList data, String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        data.addRange(bytes, 0, bytes.length);
        data.add((byte) 0);
    }

    @Test
    public void testMaterializeInstrumentableNodesWithoutActiveContext() throws IOException {
        final String compilationDirectory = "/";
        final String sourceFileName = "test.c";
        final String functionName = "main";
        final int functionLineNumber = 6;
        try (Engine engine = Engine.create();
                        Context context = Context.newBuilder(WasmLanguage.ID).engine(engine).allowExperimentalOptions(true).option("wasm.DebugTestMode", "true").build()) {
            context.initialize(WasmLanguage.ID);
            final byte[] data;
            context.enter();
            try {
                final WasmContext wasmContext = WasmContext.get(null);
                final var offsetsModule = wasmContext.readModule("test-offsets", getDefaultDebugBuilder().addCustomSection(DebugUtil.INFO_NAME, new byte[0]).build(), null);
                final int functionStartOffset = offsetsModule.functionSourceCodeStartOffset(0);
                final int functionInstructionOffset = offsetsModule.functionSourceCodeInstructionOffset(0);
                final int functionEndOffset = offsetsModule.functionSourceCodeEndOffset(0);
                data = getDefaultDebugBuilder().//
                                addCustomSection(DebugUtil.ABBREV_NAME, createDebugAbbrevSection()).//
                                addCustomSection(DebugUtil.INFO_NAME, createDebugInfoSection(functionStartOffset, functionEndOffset, compilationDirectory, functionName)).//
                                addCustomSection(DebugUtil.LINE_NAME, createDebugLineSection(functionInstructionOffset, sourceFileName, functionLineNumber)).//
                                build();
            } finally {
                context.leave();
            }

            final Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(data), "test").build();
            Value moduleInstance = context.eval(source).newInstance();
            moduleInstance.getMember("exports").getMember("_main").execute();

            final DebugMaterializationInstrument instrument = context.getEngine().getInstruments().get(DebugMaterializationInstrument.ID).lookup(DebugMaterializationInstrument.class);
            instrument.attachMaterializedStatementLoadListener();
            assertEquals(0, instrument.materializedStatementLoadCount());

            final List<SourceSection> sourceSections = instrument.queryStatementSourceSections();
            final SourceSection instrumentedSourceSection = sourceSections.stream().filter(section -> "c".equals(section.getSource().getLanguage())).findFirst().orElse(null);
            assertNotNull("source section", instrumentedSourceSection);
            assertTrue(instrument.materializedStatementLoadCount() > 0);
            assertTrue(instrumentedSourceSection.getSource().toString(), instrumentedSourceSection.getSource().getName().endsWith(sourceFileName));
            assertEquals(functionLineNumber, instrumentedSourceSection.getStartLine());
            // Sources are created with CONTENT_NONE when materializing without an active context.
            assertTrue(instrumentedSourceSection.getCharacters().isEmpty());
            assertFalse(instrumentedSourceSection.getSource().hasCharacters());
        }
    }

    @TruffleInstrument.Registration(id = DebugMaterializationInstrument.ID, services = DebugMaterializationInstrument.class)
    public static final class DebugMaterializationInstrument extends TruffleInstrument {
        static final String ID = "wasm-debug-materialization-test";

        private Instrumenter instrumenter;
        private EventBinding<?> materializedStatementLoadBinding;
        private int materializedStatementLoadCount;

        @Override
        protected void onCreate(Env env) {
            instrumenter = env.getInstrumenter();
            env.registerService(this);
        }

        private static SourceSectionFilter statementSourceSectionFilter() {
            return SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build();
        }

        List<SourceSection> queryStatementSourceSections() {
            return instrumenter.querySourceSections(statementSourceSectionFilter());
        }

        void attachMaterializedStatementLoadListener() {
            if (materializedStatementLoadBinding != null) {
                materializedStatementLoadBinding.dispose();
            }
            materializedStatementLoadCount = 0;
            materializedStatementLoadBinding = instrumenter.attachLoadSourceSectionListener(statementSourceSectionFilter(), event -> {
                final SourceSection section = event.getSourceSection();
                if ("c".equals(section.getSource().getLanguage())) {
                    materializedStatementLoadCount++;
                }
            }, false);
        }

        int materializedStatementLoadCount() {
            return materializedStatementLoadCount;
        }
    }
}
