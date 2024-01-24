/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;

/**
 * Allows a custom callback to be executed when this node is reachable.
 *
 * Native Image is able to automatically deduce usages of dynamic Java features such as reflection,
 * proxies, etc. Usually, this is done in an invocation plugin. This plugin can do one of two
 * things:
 *
 * <ol>
 * <li>Fold the invocation, leaving behind for example a Method object.</li>
 * <li>Leave the invocation as is and based on the arguments to the invoke register additional
 * metadata.</li>
 * </ol>
 *
 * Registering the metadata directly in the invocation plugin can lead to over-registration:
 * invocation plugins are processed during inlining before analysis and the invoke for which they
 * are executed may not exist in the final graph. Use this node for such registrations instead.
 *
 * To use:
 * <ol>
 * <li>Create a subclass of {@link RequiredInvocationPlugin} that is also a decorator
 * (override @{@link RequiredInvocationPlugin#isDecorator()})</li>
 * <li>When applying the plugin, add this node to the graph with a @{link {@link Runnable} that
 * registers the metadata.}</li>
 * </ol>
 */
@NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
public final class ReachabilityRegistrationNode extends FixedWithNextNode implements Canonicalizable {
    public static final NodeClass<ReachabilityRegistrationNode> TYPE = NodeClass.create(ReachabilityRegistrationNode.class);

    private final AnalysisFuture<Void> registrationTask;

    protected ReachabilityRegistrationNode(Runnable registrationHandler) {
        super(TYPE, StampFactory.forVoid());
        this.registrationTask = new AnalysisFuture<>(registrationHandler, null);
    }

    public static ReachabilityRegistrationNode create(Runnable registrationHandler, ParsingReason reason) {
        VMError.guarantee(reason.duringAnalysis() && reason != ParsingReason.JITCompilation);
        return new ReachabilityRegistrationNode(registrationHandler);
    }

    public AnalysisFuture<Void> getRegistrationTask() {
        return registrationTask;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (BuildPhaseProvider.isAnalysisFinished()) {
            return null;
        }
        return this;
    }
}
