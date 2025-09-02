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

import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.objectfile.debugentry.MethodEntry;

import jdk.graal.compiler.code.CompilationResult;

/**
 * The record has the following fields following the fixed-size record header in order:
 * <ul>
 * <li><code>uint32_t pid: OS process id of the runtime generating the jitted code</code>
 * <li><code>uint32_t tid: OS thread identification of the runtime thread generating the jitted code</code>
 * <li><code>uint64_t vma: virtual address of jitted code start</code>
 * <li><code>uint64_t code_addr: code start address for the jitted code. By default vma = code_addr</code>
 * <li><code>uint64_t code_size: size in bytes of the generated jitted code</code>
 * <li><code>uint64_t code_index: unique identifier for the jitted code (see below)</code>
 * <li><code>char[n]: function name in ASCII including the null termination</code>
 * <li><code>native code: raw byte encoding of the jitted code</code>
 * </ul>
 * <p>
 * The record header total_size field is inclusive of all components:
 * <ul>
 * <li><code>record header</code>
 * <li><code>fixed-sized fields</code>
 * <li><code>function name string, including termination</code>
 * <li><code>native code length</code>
 * <li><code>record specific variable data (e.g., array of data entries)</code>
 * </ul>
 * <p>
 * The code_index is used to uniquely identify each jitted function. The index can be a
 * monotonically increasing 64-bit value. Each time a function is jitted it gets a new number. This
 * value is used in case the code for a function is moved and avoids having to issue another
 * JIT_CODE_LOAD record.
 * <p>
 * The format supports empty functions with no native code.
 * <p>
 * See <a href=
 * "https://raw.githubusercontent.com/torvalds/linux/master/tools/perf/Documentation/jitdump-specification.txt">jitdump-specification</a>
 *
 * @param header the {@code JitdumpRecordHeader} of this record
 * @param pid the process id of the runtime generating the jitted code
 * @param tid the thread id of the runtime thread generating the jitted code
 * @param address code start address for the jitted code
 * @param size size in bytes of the generated jitted code
 * @param name function name
 */
public record JitdumpCodeLoadRecord(JitdumpRecordHeader header, int pid, int tid, long address, long size, String name, byte[] code) {

    private static final int BASE_SIZE = JitdumpRecordHeader.SIZE + 40;

    public static JitdumpCodeLoadRecord create(MethodEntry method, CompilationResult compilation, int codeSize, long address) {
        String name = method.getSymbolName();
        int recordSize = BASE_SIZE + name.getBytes().length + 1 + codeSize;
        JitdumpRecordHeader header = new JitdumpRecordHeader(JitdumpRecordId.JIT_CODE_LOAD, recordSize);
        int pid = (int) ProcessProperties.getProcessID();
        int tid = (int) Thread.currentThread().threadId();
        return new JitdumpCodeLoadRecord(header, pid, tid, address, codeSize, name, compilation.getTargetCode());
    }
}
