/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm;

import com.sun.max.asm.InlineDataDescriptor.*;

/**
 * A binding of an {@linkplain InlineDataDescriptor inline data descriptor} to some bytes decoded
 * from the instruction stream with which the descriptor is associated.
 */
public class InlineData {

    private final InlineDataDescriptor descriptor;
    private final byte[] data;

    /**
     * Creates an object to represent some otherwise unstructured.
     * @param position
     * @param data
     */
    public InlineData(int position, byte[] data) {
        this(new ByteData(position, data.length), data);
    }

    public InlineData(InlineDataDescriptor descriptor, byte[] data) {
        assert descriptor.size() == data.length;
        this.descriptor = descriptor;
        this.data = data;
    }

    public InlineDataDescriptor descriptor() {
        return descriptor;
    }

    public byte[] data() {
        return data;
    }

    public int size() {
        return data.length;
    }
}
