/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.core.debug.jitdump;

import java.util.List;

import com.oracle.objectfile.debugentry.CompiledMethodEntry;

/**
 * The record contains source lines debug information, i.e., a way to map a code address back to a
 * source line. This information may be used by the performance tool.
 * <p>
 * The record has the following fields following the fixed-size record header in order:
 * <ul>
 * <li><code>uint64_t code_addr: address of function for which the debug information is generated</code>
 * <li><code>uint64_t nr_entry : number of debug entries for the function</code>
 * <li><code>debug_entry[n]: array of nr_entry debug entries for the function</code>
 * </ul>
 * <p>
 * The debug_entry describes the source line information. It is defined as follows in order:
 * <ul>
 * <li><code>uint64_t code_addr: address of function for which the debug information is generated</code>
 * <li><code>uint32_t line     : source file line number (starting at 1)</code>
 * <li><code>uint32_t discrim  : column discriminator, 0 is default</code>
 * <li><code>char name[n]      : source file name in ASCII, including null termination</code>
 * <ul>
 * <p>
 * The debug_entry entries are saved in sequence but given that they have variable sizes due to the
 * file name string, they cannot be indexed directly. They need to be walked sequentially. The next
 * debug_entry is found at sizeof(debug_entry) + strlen(name) + 1.
 * <p>
 * IMPORTANT: The JIT_CODE_DEBUG for a given function must always be generated BEFORE the
 * JIT_CODE_LOAD for the function. This facilitates greatly the parser for the jitdump file.
 * <p>
 * See <a href=
 * "https://raw.githubusercontent.com/torvalds/linux/master/tools/perf/Documentation/jitdump-specification.txt">jitdump-specification</a>
 *
 * @param header the {@code JitdumpRecordHeader} of this record
 * @param address address of function for which the debug information is generated
 * @param entries list of debug entries for the function
 */
public record JitdumpDebugInfoRecord(JitdumpRecordHeader header, long address, List<JitdumpDebugEntry> entries) {

    public record JitdumpDebugEntry(long address, int line, int discriminator, String filename) {

        private static final int BASE_SIZE = 16;
        public int getSize() {
            return BASE_SIZE + filename.getBytes().length + 1;
        }
    }

    private static final int BASE_SIZE = JitdumpRecordHeader.SIZE + 16;

    public static JitdumpDebugInfoRecord create(CompiledMethodEntry compiledMethodEntry, long address) {
        List<JitdumpDebugEntry> entries = compiledMethodEntry.topDownRangeStream(false).map(r -> new JitdumpDebugEntry(r.getLo(), r.getLine(), 0, r.getFileName())).toList();
        int recordSize = BASE_SIZE + entries.stream().mapToInt(JitdumpDebugEntry::getSize).sum();
        JitdumpRecordHeader header = new JitdumpRecordHeader(JitdumpRecordId.JIT_CODE_DEBUG_INFO, recordSize);
        return new JitdumpDebugInfoRecord(header, address, entries);
    }
}
