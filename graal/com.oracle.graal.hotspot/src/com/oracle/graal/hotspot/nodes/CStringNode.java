/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import static com.oracle.graal.graph.UnsafeAccess.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.word.*;

/**
 * Converts a compile-time constant Java string into a malloc'ed C string. The malloc'ed string is
 * never reclaimed so this should only be used for strings in permanent code such as compiled stubs.
 */
public final class CStringNode extends FloatingNode implements Lowerable {

    private final String string;

    private CStringNode(String string) {
        super(null);
        this.string = string;
    }

    @Override
    public void lower(LoweringTool tool) {
        byte[] formatBytes = string.getBytes();
        long cstring = unsafe.allocateMemory(formatBytes.length + 1);
        for (int i = 0; i < formatBytes.length; i++) {
            unsafe.putByte(cstring + i, formatBytes[i]);
        }
        unsafe.putByte(cstring + formatBytes.length, (byte) 0);
        ConstantNode replacement = ConstantNode.forLong(cstring, graph());
        graph().replaceFloating(this, replacement);
    }

    @NodeIntrinsic(setStampFromReturnType = true)
    public static native Word cstring(@ConstantNodeParameter String string);
}
