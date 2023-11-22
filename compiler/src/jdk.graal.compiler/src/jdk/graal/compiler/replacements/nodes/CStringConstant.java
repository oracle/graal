/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;

import jdk.graal.compiler.core.common.type.DataPointerConstant;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.graph.Node.NodeIntrinsicFactory;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.word.Word;

import jdk.vm.ci.meta.JavaKind;

/**
 * Represents a compile-time constant zero-terminated UTF-8 string installed with the generated
 * code.
 */
@NodeIntrinsicFactory
public final class CStringConstant extends DataPointerConstant {

    private static final Charset UTF8 = Charset.forName("utf8");

    private final String string;

    public CStringConstant(String string) {
        super(1);
        assert string != null;
        this.string = string;
    }

    @Override
    public int getSerializedSize() {
        return string.getBytes(UTF8).length + 1;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        byte[] bytes = string.getBytes(UTF8);
        buffer.put(bytes);
        buffer.put((byte) 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CStringConstant that = (CStringConstant) o;
        return Objects.equals(string, that.string);
    }

    @Override
    public String toString() {
        return "CStringConstant{" +
                        "string='" + string + '\'' +
                        '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(string);
    }

    @Override
    public String toValueString() {
        return "c\"" + string + "\"";
    }

    public static boolean intrinsify(GraphBuilderContext b, CStringConstant string) {
        b.addPush(JavaKind.Object, new ConstantNode(string, StampFactory.pointer()));
        return true;
    }

    public static boolean intrinsify(GraphBuilderContext b, String string) {
        b.addPush(JavaKind.Object, new ConstantNode(new CStringConstant(string), StampFactory.pointer()));
        return true;
    }

    @NodeIntrinsic
    public static native Word cstring(@ConstantNodeParameter CStringConstant string);

    @NodeIntrinsic
    public static native Word cstring(@ConstantNodeParameter String string);
}
