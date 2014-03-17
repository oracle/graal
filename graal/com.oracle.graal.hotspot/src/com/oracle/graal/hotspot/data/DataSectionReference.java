/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.data;

import java.nio.*;

import com.oracle.graal.api.code.CompilationResult.Data;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;

/**
 * Represents a reference to the data section. Before the code is installed, all {@link Data} items
 * referenced by a {@link DataPatch} are put into the data section of the method, and replaced by
 * {@link DataSectionReference}.
 */
public class DataSectionReference extends Data {

    public final int offset;

    protected DataSectionReference(int offset) {
        super(0);
        this.offset = offset;
    }

    @Override
    public int getSize(TargetDescription target) {
        return 0;
    }

    @Override
    public Kind getKind() {
        return Kind.Illegal;
    }

    @Override
    public void emit(TargetDescription target, ByteBuffer buffer) {
    }

}
