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
 * A jitdump record header specifies the type and length of a jitdump record. The following record
 * types are defined:
 * <ul>
 * <li><code>Value 0 : JIT_CODE_LOAD      : record describing a jitted function</code>
 * <li><code>Value 1 : JIT_CODE_MOVE      : record describing an already jitted function which is moved</code>
 * <li><code>Value 2 : JIT_CODE_DEBUG_INFO: record describing the debug information for a jitted function</code>
 * <li><code>Value 3 : JIT_CODE_CLOSE     : record marking the end of the jit runtime (optional)</code>
 * <li><code>Value 4 : JIT_CODE_UNWINDING_INFO: record describing a function unwinding information</code>
 * </ul>
 * <p>
 * See <a href=
 * "https://raw.githubusercontent.com/torvalds/linux/master/tools/perf/Documentation/jitdump-specification.txt">jitdump-specification</a>
 */
public enum JitdumpRecordId {
    JIT_CODE_LOAD(0),
    JIT_CODE_MOVE(1),
    JIT_CODE_DEBUG_INFO(2),
    JIT_CODE_CLOSE(3),
    JIT_CODE_UNWINDING_INFO(4);

    private final int value;

    JitdumpRecordId(int s) {
        value = s;
    }

    public int value() {
        return value;
    }
}
