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

package com.oracle.svm.hosted.webimage.wasm.ast;

import java.util.Arrays;

import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;

public class Data extends ModuleField {

    public final WasmId.Data id;
    public final byte[] data;
    public final long offset;
    public final boolean active;

    /**
     * Constructor for active data segment.
     */
    public Data(WasmId.Data id, byte[] data, long offset, Object comment) {
        super(comment);
        this.data = data;
        this.offset = offset;
        this.active = offset >= 0;
        this.id = id;

        // The offset must fit into an u32
        assert !this.active || offset <= 1L << 32 : offset;
    }

    /**
     * Constructor for passive data segment.
     */
    public Data(WasmId.Data id, byte[] data, Object comment) {
        this(id, data, -1, comment);
    }

    public int getSize() {
        return data.length;
    }

    @Override
    public String toString() {
        return "Data{" +
                        "id=" + id +
                        ", data=" + Arrays.toString(data) +
                        ", offset=" + offset +
                        ", active=" + active +
                        '}';
    }
}
