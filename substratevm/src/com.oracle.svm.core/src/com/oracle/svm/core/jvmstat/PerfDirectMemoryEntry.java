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

import java.nio.ByteBuffer;

/**
 * {@link PerfDirectMemoryEntry} supports direct access to the underlying performance data memory
 * via a {@link ByteBuffer}. All subclasses may only be used in cases where JDK code wants to create
 * a {@link PerfDataEntry} and needs direct memory access to the data of this entry.
 *
 * If these entries would be used for VM-internal performance data (such as GC performance data),
 * the issues described in {@link MutablePerfDataEntry} could occur.
 */
public abstract class PerfDirectMemoryEntry extends AbstractPerfDataEntry {
    protected ByteBuffer byteBuffer;

    PerfDirectMemoryEntry(String name, PerfUnit unit) {
        super(name, unit);
    }

    public ByteBuffer getByteBuffer() {
        assert byteBuffer != null;
        return byteBuffer;
    }
}
