/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.code;

import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.core.gen.DebugInfoBuilder;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.NodeValueMap;

import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.StackLockValue;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.Value;

public final class SubstrateDebugInfoBuilder extends DebugInfoBuilder {

    private final SharedMethod method;

    public SubstrateDebugInfoBuilder(StructuredGraph graph, MetaAccessExtensionProvider metaAccessExtensionProvider, NodeValueMap nodeValueMap) {
        super(nodeValueMap, metaAccessExtensionProvider, graph.getDebug());
        this.method = (SharedMethod) graph.method();
    }

    @Override
    protected JavaKind storageKind(JavaType type) {
        return ((SharedType) type).getStorageKind();
    }

    @Override
    protected JavaValue computeLockValue(FrameState state, int lockIndex) {
        JavaValue object = toJavaValue(state.lockAt(lockIndex));
        boolean eliminated = object instanceof VirtualObject || state.monitorIdAt(lockIndex).isEliminated();

        if (eliminated && method.isDeoptTarget()) {
            throw VMError.shouldNotReachHere("Deoptimization target method must not have virtual objects or eliminated locks: " + method);
        }

        return new StackLockValue(object, Value.ILLEGAL, eliminated);
    }
}
