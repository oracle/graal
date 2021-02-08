/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.Node.NodeIntrinsicFactory;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.IndexAddressNode;

import jdk.vm.ci.meta.JavaKind;

/**
 * Reads a value of kind {@code resultKind} from {@code array[index]}, where {@code arrayKind}
 * denotes the array's type, and {@code indexKind} denotes the index scale. This allows to e.g. read
 * an {@code int}-value from a {@code byte}-array, with the index scaling of a {@code char} -array.
 * {@code resultKind} and {@code indexKind} may be chosen freely, but {@code arrayKind} must always
 * be the actual type of the given array. The caller is responsible for making sure that the
 * resulting read operation does not exceed the array's bounds.
 */
@NodeIntrinsicFactory
public class TypePunnedArrayReadIntrinsic {

    @NodeIntrinsic
    public static native int read(@ConstantNodeParameter JavaKind arrayKind, @ConstantNodeParameter JavaKind indexKind, @ConstantNodeParameter JavaKind resultKind, Object array, long index);

    public static boolean intrinsify(GraphBuilderContext b, JavaKind arrayKind, JavaKind indexKind, JavaKind resultKind, ValueNode array,
                    ValueNode index) {
        b.addPush(resultKind, new JavaReadNode(resultKind, new IndexAddressNode(array, index, arrayKind, indexKind), NamedLocationIdentity.getArrayLocation(arrayKind),
                        BarrierType.NONE, false));
        return true;
    }

    @NodeIntrinsicFactory
    public static class ReadLong {

        @NodeIntrinsic
        public static native long read(@ConstantNodeParameter JavaKind arrayKind, @ConstantNodeParameter JavaKind indexKind, Object array, long index);

        public static boolean intrinsify(GraphBuilderContext b, JavaKind arrayKind, JavaKind indexKind, ValueNode array,
                        ValueNode index) {
            b.addPush(JavaKind.Long, new JavaReadNode(JavaKind.Long, new IndexAddressNode(array, index, arrayKind, indexKind), NamedLocationIdentity.getArrayLocation(arrayKind),
                            BarrierType.NONE, false));
            return true;
        }
    }
}
