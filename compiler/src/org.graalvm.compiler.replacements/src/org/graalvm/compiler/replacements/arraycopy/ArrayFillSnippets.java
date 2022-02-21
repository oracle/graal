/*
 * Copyright (c) 2022, Alibaba Group Holding Limited. All Rights Reserved.
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
 *
 */
package org.graalvm.compiler.replacements.arraycopy;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.word.Word;

public abstract class ArrayFillSnippets implements Snippets {
    @Node.NodeIntrinsic(ForeignCallNode.class)
    protected static native void jintFill(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, Word to, int value, int count);

    @Node.NodeIntrinsic(ForeignCallNode.class)
    protected static native void jshortFill(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, Word to, int value, int count);

    @Node.NodeIntrinsic(ForeignCallNode.class)
    protected static native void jbyteFill(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, Word to, int value, int count);

    @Node.NodeIntrinsic(ForeignCallNode.class)
    protected static native void arrayofJintFill(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, Word to, int value, int count);

    @Node.NodeIntrinsic(ForeignCallNode.class)
    protected static native void arrayofJshortFill(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, Word to, int value, int count);

    @Node.NodeIntrinsic(ForeignCallNode.class)
    protected static native void arrayofJbyteFill(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, Word to, int value, int count);

    protected abstract ForeignCallDescriptor jintFillCallDescriptor();

    protected abstract ForeignCallDescriptor jshortFillCallDescriptor();

    protected abstract ForeignCallDescriptor jbyteFillCallDescriptor();

    protected abstract ForeignCallDescriptor arrayofJintFillCallDescriptor();

    protected abstract ForeignCallDescriptor arrayofJshortFillCallDescriptor();

    protected abstract ForeignCallDescriptor arrayofJbyteFillCallDescriptor();
}