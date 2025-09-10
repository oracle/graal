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

/**
 * The {@link JitdumpHeader jitdump file header} is immediately followed by records. Each record
 * starts with a fixed size header describing the record that follows.
 * <p>
 * The record header is specified in order as follows:
 * <ul>
 * <li><code>uint32_t id        : a value identifying the {@link JitdumpRecordId record type}.</code>
 * <li><code>uint32_t total_size: the size in bytes of the record including the header.</code>
 * <li><code>uint64_t timestamp : a timestamp of when the record was created.</code>
 * </ul>
 * <p>
 * The payload of the record must immediately follow the record header without padding.
 * <p>
 * See <a href=
 * "https://raw.githubusercontent.com/torvalds/linux/master/tools/perf/Documentation/jitdump-specification.txt">jitdump-specification</a>
 *
 * @param id the record type
 * @param recordSize the size of a record including the header
 * @param timestamp the creation timestamp of the record
 */
public record JitdumpRecordHeader(JitdumpRecordId id, int recordSize, long timestamp) {

    /**
     * The size of the jitdump record header. Consists of the following fixed size fields:
     * <ul>
     * <li>unint32_t id => 4 bytes
     * <li>unint32_t total_size => 4 bytes
     * <li>unint64_t timestamp => 8 bytes
     * </ul>
     * SIZE = 2*4 + 8 = 16
     */
    public static final int SIZE = 16;

    /**
     * Create a new jitdump record header tagged with the current {@link System#nanoTime()
     * timestamp}.
     * 
     * @param id the {@link JitdumpRecordId} of the header
     * @param recordSize the total size of the jitdump record, including the header
     */
    public JitdumpRecordHeader(JitdumpRecordId id, int recordSize) {
        this(id, recordSize, System.nanoTime());
    }
}
