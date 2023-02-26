/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.isolated;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.svm.graal.isolated.ClientHandle;
import com.oracle.svm.graal.isolated.ClientIsolateThread;
import com.oracle.svm.graal.isolated.IsolatedCompileClient;
import com.oracle.svm.graal.isolated.IsolatedCompileContext;
import com.oracle.svm.graal.isolated.IsolatedObjectProxy;
import com.oracle.svm.truffle.api.SubstrateCompilableTruffleAST;

final class IsolatedTruffleCallNode extends IsolatedObjectProxy<TruffleCallNode> implements TruffleCallNode {
    IsolatedTruffleCallNode(ClientHandle<TruffleCallNode> node) {
        super(node);
    }

    @Override
    public CompilableTruffleAST getCurrentCallTarget() {
        ClientHandle<SubstrateCompilableTruffleAST> astHandle = getCurrentCallTarget0(IsolatedCompileContext.get().getClient(), handle);
        return new IsolatedCompilableTruffleAST(astHandle);
    }

    @Override
    public int getCallCount() {
        return getCallCount0(IsolatedCompileContext.get().getClient(), handle);
    }

    @Override
    public boolean isInliningForced() {
        return isInliningForced0(IsolatedCompileContext.get().getClient(), handle);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static ClientHandle<SubstrateCompilableTruffleAST> getCurrentCallTarget0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleCallNode> nodeHandle) {
        TruffleCallNode node = IsolatedCompileClient.get().unhand(nodeHandle);
        CompilableTruffleAST target = node.getCurrentCallTarget();
        return IsolatedCompileClient.get().hand((SubstrateCompilableTruffleAST) target);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static int getCallCount0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleCallNode> nodeHandle) {
        TruffleCallNode node = IsolatedCompileClient.get().unhand(nodeHandle);
        return node.getCallCount();
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static boolean isInliningForced0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<TruffleCallNode> nodeHandle) {
        TruffleCallNode node = IsolatedCompileClient.get().unhand(nodeHandle);
        return node.isInliningForced();
    }
}
