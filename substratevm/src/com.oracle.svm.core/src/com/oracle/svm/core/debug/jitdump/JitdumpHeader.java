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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.objectfile.elf.ELFMachine;

/**
 * Each jitdump file starts with a fixed size header containing the following fields in order:
 * <ul>
 * <li><code>uint32_t magic ...... : a magic number tagging the file type. The value is 4-byte long and represents the string "JiTD" in ASCII form. It written is as 0x4A695444.
 * The reader will detect an endian mismatch when it reads 0x4454694a.</code>
 * <li><code>uint32_t version .... : a 4-byte value representing the format version. It is currently set to 1</code>
 * <li><code>uint32_t total_size . : size in bytes of file header</code>
 * <li><code>uint32_t elf_mach ... : ELF architecture encoding (ELF e_machine value as specified in /usr/include/elf.h)</code>
 * <li><code>uint32_t pad1 ....... : padding. Reserved for future use</code>
 * <li><code>uint32_t pid ........ : JIT runtime process identification (OS specific)</code>
 * <li><code>uint64_t timestamp .. : timestamp of when the file was created</code>
 * <li><code>uint64_t flags ...... : a bitmask of flags</code>
 * </ul>
 * <p>
 * The flags currently defined are as follows:
 * <ul>
 * <li><code>bit 0: JITDUMP_FLAGS_ARCH_TIMESTAMP : set if the jitdump file is using an architecture-specific timestamp clock source. For instance, on x86, one could use TSC directly</code>
 * </ul>
 * <p>
 * See <a href=
 * "https://raw.githubusercontent.com/torvalds/linux/master/tools/perf/Documentation/jitdump-specification.txt">jitdump-specification</a>
 *
 * @param elfMach the {@code ELFMachine} for the current architecture
 * @param pid JIT runtime process identification
 * @param timestamp timestamp of when the file was created
 */
public record JitdumpHeader(ELFMachine elfMach, int pid, long timestamp) {

    /**
     * A value representing the string "JiTD", which serves as a magic number for tagging jitdump
     * files.
     */
    public static final int MAGIC = 0x4A695444;
    /**
     * The jitdump version. The implementation is based on the
     * {@code JITDUMP specification version 2}, which specifies the version number to be set to 1.
     */
    public static final int VERSION = 1;
    /**
     * The size of the jitdump header. Consists of the following fixed size fields:
     * <ul>
     * <li>unint32_t magic => 4 bytes
     * <li>unint32_t version => 4 bytes
     * <li>unint32_t total_size => 4 bytes
     * <li>unint32_t elf_mach => 4 bytes
     * <li>unint32_t pad1 => 4 bytes
     * <li>unint32_t pid => 4 bytes
     * <li>unint64_t timestamp => 8 bytes
     * <li>unint64_t flags => 8 bytes
     * </ul>
     * SIZE = 6*4 + 2*8 = 40
     */
    public static final int SIZE = 40;

    /**
     * Create a new jitdump header tagged with the current {@link System#nanoTime() timestamp}. Also
     * fetches the system's ELF machine encoding and the native image process id.
     */
    public JitdumpHeader() {
        this(ELFMachine.from(ImageSingletons.lookup(Platform.class).getArchitecture()), (int) ProcessProperties.getProcessID(), System.nanoTime());
    }
}
