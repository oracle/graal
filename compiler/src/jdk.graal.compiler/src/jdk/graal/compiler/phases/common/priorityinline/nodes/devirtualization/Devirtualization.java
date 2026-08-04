/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.nodes.devirtualization;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;

import jdk.graal.compiler.phases.common.priorityinline.InliningProvider;

/**
 * The strategy object for devirtualization. See {@link DevirtualizationUtil}.
 */
public abstract class Devirtualization {
    /**
     * The node-source position (in the current compilation unit) of the invoke that is being
     * devirtualized.
     */
    public abstract NodeSourcePosition callerPosition();

    /**
     * The invoke-node-relative probability of this devirtualization case.
     * <p>
     * This is the probability the particular method of this devirtualization getting dispatched,
     * when the original virtual invoke gets executed.
     */
    public abstract double probability();

    /**
     * Create the invocation block for the direct call.
     */
    public abstract AbstractBeginNode createInvocationBlock(StructuredGraph graph, Invoke originalInvoke, AbstractMergeNode returnMerge, PhiNode returnValuePhi, AbstractMergeNode exceptionMerge,
                    PhiNode exceptionObjectPhi, InliningProvider inlinlingProvider);

    /**
     * Create the condition for this devirtualization case.
     */
    public abstract LogicNode createDevirtualizationCondition(CoreProviders coreProviders, InliningProvider inliningProvider, StructuredGraph graph, Invoke virtualInvoke,
                    AbstractBeginNode invocationBlock);
}
