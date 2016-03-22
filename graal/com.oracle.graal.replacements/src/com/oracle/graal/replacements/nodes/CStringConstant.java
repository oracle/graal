/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.oracle.graal.compiler.common.type.DataPointerConstant;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.word.Word;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Represents a compile-time constant zero-terminated UTF-8 string installed with the generated
 * code.
 */
public final class CStringConstant extends DataPointerConstant {

    private static final Charset UTF8 = Charset.forName("utf8");

    private final String string;

    public CStringConstant(String string) {
        super(1);
        assert string != null;
        this.string = string;
    }

    public int getSerializedSize() {
        return string.getBytes(UTF8).length + 1;
    }

    public void serialize(ByteBuffer buffer) {
        byte[] bytes = string.getBytes(UTF8);
        buffer.put(bytes);
        buffer.put((byte) 0);
    }

    public String toValueString() {
        return "c\"" + string + "\"";
    }

    public static boolean intrinsify(GraphBuilderContext b, @SuppressWarnings("unused") ResolvedJavaMethod targetMethod, String string) {
        b.addPush(JavaKind.Object, new ConstantNode(new CStringConstant(string), StampFactory.pointer()));
        return true;
    }

    @NodeIntrinsic
    public static native Word cstring(@ConstantNodeParameter String string);
}
