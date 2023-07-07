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

import static org.graalvm.wasm.test.WasmTestUtils.hexStringToByteArray;

import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.test.AbstractBinarySuite;
import org.junit.Test;

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;

public class DebugValidationSuite extends AbstractBinarySuite {

    private static AbstractBinarySuite.BinaryBuilder getDefaultDebugBuilder() {
        return newBuilder().addType(EMPTY_BYTES, EMPTY_BYTES).addFunction((byte) 0, EMPTY_BYTES, "0B").addFunctionExport((byte) 0, "_main");
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

    private static void runTest(byte[] data) throws IOException {
        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        final Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(data), "test_main").build();
        Context context = contextBuilder.build();
        Debugger debugger = Debugger.find(context.getEngine());
        DebuggerSession session = debugger.startSession(event -> {
        });
        try {
            Value val = context.eval(source);
            val.getMember("_main").execute();
            session.suspendNextExecution();
        } finally {
            session.close();
            context.close(true);
        }
    }
}
