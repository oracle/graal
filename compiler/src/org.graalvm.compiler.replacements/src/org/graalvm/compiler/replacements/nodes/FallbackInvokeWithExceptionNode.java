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

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.InputType.Value;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;
import static org.graalvm.compiler.nodes.Invoke.CYCLES_UNKNOWN_RATIONALE;
import static org.graalvm.compiler.nodes.Invoke.SIZE_UNKNOWN_RATIONALE;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.word.LocationIdentity;

/**
 * Placeholder for a fallback call from a {@link MacroWithExceptionNode}.
 *
 * The {@link #fallbackFunctionCall()} intrinsic can be used in snippets that lower a
 * {@link MacroWithExceptionNode}. The {@link FallbackInvokeWithExceptionNode} will be replaced with
 * the {@linkplain MacroWithExceptionNode#createInvoke original call} of the macro node. This can be
 * useful for handling exceptional and/or slow path cases.
 *
 * Currently, only one {@link FallbackInvokeWithExceptionNode} is allowed per snippet.
 */
// @formatter:off
@NodeInfo(allowedUsageTypes = {Value, Memory},
        cycles = CYCLES_UNKNOWN, cyclesRationale = CYCLES_UNKNOWN_RATIONALE,
        size   = SIZE_UNKNOWN,   sizeRationale   = SIZE_UNKNOWN_RATIONALE)
// @formatter:on
public final class FallbackInvokeWithExceptionNode extends WithExceptionNode implements SingleMemoryKill {

    public static final NodeClass<FallbackInvokeWithExceptionNode> TYPE = NodeClass.create(FallbackInvokeWithExceptionNode.class);

    public FallbackInvokeWithExceptionNode(@InjectedNodeParameter Stamp stamp) {
        super(TYPE, stamp);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    /**
     * @see FallbackInvokeWithExceptionNode
     */
    @NodeIntrinsic
    public static native Object fallbackFunctionCall();

    /**
     * @see FallbackInvokeWithExceptionNode
     */
    @NodeIntrinsic
    public static native int fallbackFunctionCallInt();
}
