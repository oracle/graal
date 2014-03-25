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

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.word.*;

/**
 * Converts a compile-time constant Java string into a C string installed with the generated code.
 */
public final class CStringNode extends FloatingNode implements LIRGenLowerable {

    private final String string;

    private CStringNode(String string) {
        super(null);
        this.string = string;
    }

    public void generate(LIRGenerator gen) {
        gen.setResult(this, gen.emitMove(new RawDataValue(gen.target().wordKind, toCString(string))));
    }

    /**
     * Converts a String to a null terminated byte array suitable for use as a C string value.
     */
    public static byte[] toCString(String value) {
        byte[] bytes = value.getBytes();
        byte[] nulTerminated = Arrays.copyOf(bytes, bytes.length + 1);
        nulTerminated[bytes.length] = 0;
        return nulTerminated;
    }

    @NodeIntrinsic(setStampFromReturnType = true)
    public static native Word cstring(@ConstantNodeParameter String string);
}
