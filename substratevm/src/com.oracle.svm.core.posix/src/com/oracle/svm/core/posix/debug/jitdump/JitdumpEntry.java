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

package com.oracle.svm.core.posix.debug.jitdump;

import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CUnsigned;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.c.ProjectHeaderFile;
import com.oracle.svm.core.debug.SubstrateDebugInfoInstaller;

/**
 * The jitdump entries are based on the <a href=
 * "https://github.com/torvalds/linux/blob/46a51f4f5edade43ba66b3c151f0e25ec8b69cb6/tools/perf/Documentation/jitdump-specification.txt">Jitdump
 * specification</a>. This defines structs that match the entry descriptions in the specification.
 */
@CContext(JitdumpEntry.JitdumpRecordDirective.class)
public class JitdumpEntry {
    public static class JitdumpRecordDirective implements CContext.Directives {
        @Override
        public boolean isInConfiguration() {
            return SubstrateDebugInfoInstaller.Options.hasRuntimeDebugInfoFormatSupport(SubstrateDebugInfoInstaller.DEBUG_INFO_JITDUMP_NAME);
        }

        @Override
        public List<String> getHeaderFiles() {
            return Collections.singletonList(ProjectHeaderFile.resolve("", "include/jitdump_entry.h"));
        }
    }

    /**
     * Each jitdump record has a record type.
     */
    @CEnum("record_type")
    enum RecordType {
        /**
         * Record describing a jitted function.
         */
        JIT_CODE_LOAD,
        /**
         * Record describing an already jitted function which is moved.
         */
        JIT_CODE_MOVE,
        /**
         * Record describing the debug information for a jitted function.
         */
        JIT_CODE_DEBUG_INFO,
        /**
         * Record marking the end of the jit runtime (optional).
         */
        JIT_CODE_CLOSE,
        /**
         * Record describing a function unwinding information.
         */
        JIT_CODE_UNWINDING_INFO;

        @CEnumValue
        public native int getCValue();
    }

    /**
     * Each jitdump file starts with a fixed size header followed by jitdump records.
     */
    @CStruct(value = "file_header", addStructKeyword = true)
    public interface FileHeader extends PointerBase {
        /**
         * A magic number tagging the file type (see {@link JitdumpProvider#MAGIC}).
         */
        @CField("magic")
        @CUnsigned
        int getMagic();

        @CField("magic")
        void setMagic(@CUnsigned int magic);

        /**
         * A 4-byte value representing the format version (see {@link JitdumpProvider#VERSION}).
         */
        @CField("version")
        @CUnsigned
        int getVersion();

        @CField("version")
        void setVersion(@CUnsigned int version);

        /**
         * Size in bytes of file header.
         */
        @CField("total_size")
        @CUnsigned
        int getTotalSize();

        @CField("total_size")
        void setTotalSize(@CUnsigned int totalSize);

        /**
         * ELF architecture encoding (ELF e_machine value as specified in /usr/include/elf.h).
         */
        @CField("elf_mach")
        @CUnsigned
        int getElfMach();

        @CField("elf_mach")
        void setElfMach(@CUnsigned int elfMach);

        /**
         * Padding, reserved for future use.
         */
        @CField("pad1")
        @CUnsigned
        int getPad1();

        @CField("pad1")
        void setPad1(@CUnsigned int pad1);

        /**
         * JIT runtime process id.
         */
        @CField("pid")
        @CUnsigned
        int getPid();

        @CField("pid")
        void setPid(@CUnsigned int pid);

        /**
         * Timestamp of when the file was created (from {@code LinuxTime#CLOCK_MONOTONIC()}).
         */
        @CField("timestamp")
        @CUnsigned
        long getTimestamp();

        @CField("timestamp")
        void setTimestamp(@CUnsigned long timestamp);

        /**
         * A bitmask of flags.
         * <ul>
         * <li><code>bit 0: JITDUMP_FLAGS_ARCH_TIMESTAMP : set if the jitdump file is using an architecture-specific timestamp clock source.</code>
         * </ul>
         */
        @CField("flags")
        @CUnsigned
        long getFlags();

        @CField("flags")
        void setFlags(@CUnsigned long flags);
    }

    /**
     * The {@link FileHeader jitdump file header} is immediately followed by records. Each record
     * starts with a fixed size header. The payload of the record must immediately follow the record
     * header without padding.
     */
    @CStruct(value = "record_header", addStructKeyword = true)
    public interface RecordHeader extends PointerBase {
        /**
         * A value identifying the {@link RecordType record type}.
         */
        @CField("id")
        @CUnsigned
        int getId();

        @CField("id")
        void setId(@CUnsigned int id);

        /**
         * The size in bytes of the record including the header.
         */
        @CField("total_size")
        @CUnsigned
        int getTotalSize();

        @CField("total_size")
        void setTotalSize(@CUnsigned int totalSize);

        /**
         * A timestamp of when the record was created (from {@code LinuxTime#CLOCK_MONOTONIC()}).
         */
        @CField("timestamp")
        @CUnsigned
        long getTimestamp();

        @CField("timestamp")
        void setTimestamp(@CUnsigned long timestamp);
    }

    /**
     * A code load record is immediately followed by the function name in ASCII (including the null
     * termination) and the raw byte encoding of the jitted code. The format supports empty
     * functions with no native code.
     * <p>
     * The record header total_size field is inclusive of all components:
     * <ul>
     * <li><code>record header</code>
     * <li><code>fixed-sized fields</code>
     * <li><code>function name string, including termination</code>
     * <li><code>native code length</code>
     * </ul>
     * <p>
     * 
     */
    @CStruct(value = "code_load_record", addStructKeyword = true)
    public interface CodeLoadRecord extends RecordHeader {
        /**
         * OS process id of the runtime generating the jitted code.
         */
        @CField("pid")
        @CUnsigned
        int getPid();

        @CField("pid")
        void setPid(@CUnsigned int pid);

        /**
         * OS thread id of the runtime thread generating the jitted code.
         */
        @CField("tid")
        @CUnsigned
        int getTid();

        @CField("tid")
        void setTid(@CUnsigned int tid);

        /**
         * Virtual address of jitted code start.
         */
        @CField("vma")
        @CUnsigned
        long getVma();

        @CField("vma")
        void setVma(@CUnsigned long vma);

        /**
         * Code start address for the jitted code (by default vma = code_addr).
         */
        @CField("code_addr")
        @CUnsigned
        long getCodeAddr();

        @CField("code_addr")
        void setCodeAddr(@CUnsigned long codeAddr);

        /**
         * Size in bytes of the generated jitted code.
         */
        @CField("code_size")
        @CUnsigned
        long getCodeSize();

        @CField("code_size")
        void setCodeSize(@CUnsigned long codeSize);

        /**
         * Unique identifier for the jitted code.
         * <p>
         * The code_index is used to uniquely identify each jitted function. The index can be a
         * monotonically increasing 64-bit value. Each time a function is jitted it gets a new
         * number. This value is used in case the code for a function is moved and avoids having to
         * issue another {@code JIT_CODE_LOAD} record.
         */
        @CField("code_index")
        @CUnsigned
        long getCodeIndex();

        @CField("code_index")
        void setCodeIndex(@CUnsigned long codeIndex);
    }

    /**
     * The debug entry describes source line information and is part of a {@link DebugInfoRecord
     * debug info record}.
     * <p>
     * A debug entry is immediately followed by the corresponding source file name in ASCII
     * (including null termination).
     */
    @CStruct(value = "debug_entry", addStructKeyword = true)
    public interface DebugEntry extends PointerBase {
        /**
         * Address of function (or inlined function) of this debug entry.
         */
        @CField("code_addr")
        @CUnsigned
        long getCodeAddr();

        @CField("code_addr")
        void setCodeAddr(@CUnsigned long codeAddr);

        /**
         * Source file line number (starting at 1).
         */
        @CField("line")
        @CUnsigned
        int getLine();

        @CField("line")
        void setLine(@CUnsigned int line);

        /**
         * Column discriminator, 0 is default.
         */
        @CField("discrim")
        @CUnsigned
        int getDiscrim();

        @CField("discrim")
        void setDiscrim(@CUnsigned int discrim);
    }

    /**
     * The debug info record contains source lines debug information, i.e., a way to map a code
     * address back to a source line. This information may be used by the performance tool.
     * <p>
     * A debug info record is immediately followed by an array of nr_entry {@link DebugEntry debug
     * entries}. The debug entries are saved in sequence but given that they have variable sizes due
     * to the file name string, they cannot be indexed directly. The next debug entry is found at
     * sizeof(debug_entry) + strlen(filename) + 1.
     * <p>
     * IMPORTANT: The debug info record for a given function must always be generated BEFORE the
     * code load record for the function. The parser only holds one debug info record in memory and
     * attaches it to the next code load record.
     */
    @CStruct(value = "debug_info_record", addStructKeyword = true)
    public interface DebugInfoRecord extends RecordHeader {
        /**
         * Address of function for which the debug information is generated.
         */
        @CField("code_addr")
        @CUnsigned
        long getCodeAddr();

        @CField("code_addr")
        void setCodeAddr(@CUnsigned long codeAddr);

        /**
         * Number of debug entries for the function.
         */
        @CField("nr_entry")
        @CUnsigned
        long getNrEntry();

        @CField("nr_entry")
        void setNrEntry(@CUnsigned long nrEntry);
    }
}
