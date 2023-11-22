/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmstat;

import static jdk.vm.ci.meta.JavaKind.Byte;

import java.nio.ByteBuffer;

import com.oracle.svm.core.jdk.DirectByteBufferUtil;

/**
 * Similar to {@link PerfStringConstant} and {@link PerfStringVariable} but supports direct memory
 * access. To ensure the same behavior as on HotSpot, this class also has to treat the maxLength
 * differently (see comment below and in {@link PerfStringVariable}).
 */
public class PerfDirectMemoryString extends PerfDirectMemoryEntry {
    PerfDirectMemoryString(String name, PerfUnit unit) {
        super(name, unit);
    }

    protected ByteBuffer allocate(PerfVariability variability, byte[] value, int maxLength) {
        assert value.length <= maxLength;
        // As on HotSpot, add one extra byte for the null terminator.
        int nullTerminatedMaxLength = maxLength + 1;
        allocate(variability, Byte, nullTerminatedMaxLength);
        writeNullTerminatedString(valuePtr, value, maxLength);
        return DirectByteBufferUtil.allocate(valuePtr.rawValue(), nullTerminatedMaxLength);
    }
}
