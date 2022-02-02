/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

/**
 * When Graal {@link ValueNode} classes implement interfaces, it is frequently necessary to convert
 * from an interface type back to a Node. This could be easily done using a cast, but casts come
 * with a run time cost. Using a conversion method, which is implemented once on
 * {@link ValueNode#asNode()}, avoids a cast. But it is faster only as long as the implementation
 * method can be inlined. Therefore, it is important that only one implementation of the interface
 * method exists, so that either single-implementor speculation (for JIT compilation) or static
 * analysis results (for AOT compilation) allow the one implementation to be inlined.
 *
 * Subinterfaces like {@link FixedNodeInterface} provide a conversion method that has a more precise
 * return type. Note that these sub-interfaces use a different method name like
 * {@link FixedNodeInterface#asFixedNode()}, which then have another single implementation without a
 * cast in {@link FixedNode#asFixedNode()}.
 */
public interface ValueNodeInterface {
    /*
     * This method is called `asNode` and not `asValueNode` partly for historic reasons, because
     * originally the interface was called just `NodeInterface`. But since there are so many
     * callers, we also want to keep the call sites as short as possible.
     */
    ValueNode asNode();
}
