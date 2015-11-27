/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LocationIdentity;

import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;

/**
 * Copy a value at a location specified as an offset relative to a source object to another location
 * specified as an offset relative to destination object. No null checks are performed.
 */
public final class UnsafeCopyNode {

    public static boolean intrinsify(GraphBuilderContext b, ValueNode sourceObject, ValueNode sourceOffset, ValueNode destinationObject, ValueNode destinationOffset, JavaKind accessKind,
                    LocationIdentity locationIdentity) {
        UnsafeLoadNode value = b.add(new UnsafeLoadNode(sourceObject, sourceOffset, accessKind, locationIdentity));
        b.add(new UnsafeStoreNode(destinationObject, destinationOffset, value, accessKind, locationIdentity));
        return true;
    }

    @NodeIntrinsic
    public static native void copy(Object srcObject, long srcOffset, Object destObject, long destOffset, @ConstantNodeParameter JavaKind kind, @ConstantNodeParameter LocationIdentity locationIdentity);
}
