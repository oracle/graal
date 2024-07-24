/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.InputType.Association;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_4;

import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.spi.Simplifiable;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

@NodeInfo(allowedUsageTypes = {Association}, cycles = CYCLES_0, size = SIZE_4)
public final class LoopExitNode extends BeginStateSplitNode implements IterableNodeType, Simplifiable {

    public static final NodeClass<LoopExitNode> TYPE = NodeClass.create(LoopExitNode.class);

    /*
     * The declared type of the field cannot be LoopBeginNode, because loop explosion during partial
     * evaluation can temporarily assign a non-loop begin. This node will then be deleted shortly
     * after - but we still must not have type system violations for that short amount of time.
     */
    @Input(Association) AbstractBeginNode loopBegin;

    public LoopExitNode(LoopBeginNode loop) {
        super(TYPE);
        assert loop != null;
        loopBegin = loop;
    }

    public LoopBeginNode loopBegin() {
        return (LoopBeginNode) loopBegin;
    }

    public void setLoopBegin(AbstractBeginNode loopBegin) {
        updateUsages(this.loopBegin, loopBegin);
        this.loopBegin = loopBegin;
    }

    @Override
    public NodeIterable<Node> anchored() {
        return super.anchored().filter(n -> {
            if (n instanceof ProxyNode) {
                ProxyNode proxyNode = (ProxyNode) n;
                return proxyNode.proxyPoint() != this;
            }
            return true;
        });
    }

    @Override
    public void prepareDelete(FixedNode evacuateFrom) {
        removeProxies();
        super.prepareDelete(evacuateFrom);
    }

    public void removeProxies() {
        if (this.hasUsages()) {
            outer: while (true) {
                for (ProxyNode vpn : proxies().snapshot()) {
                    ValueNode value = vpn.value();
                    vpn.replaceAtUsagesAndDelete(value);
                    if (value == this) {
                        // Guard proxy could have this input as value.
                        continue outer;
                    }
                }
                break;
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public NodeIterable<ProxyNode> proxies() {
        return (NodeIterable) usages().filter(n -> {
            if (n instanceof ProxyNode) {
                ProxyNode proxyNode = (ProxyNode) n;
                return proxyNode.proxyPoint() == this;
            }
            return false;
        });
    }

    public void removeExit() {
        removeExit(false);
    }

    public void removeExit(boolean forKillCFG) {
        this.removeProxies();
        FrameState loopStateAfter = this.stateAfter();
        if (!forKillCFG || predecessor() != null) {
            // When killing control flow, don't replace this node with a BeginNode if it appears
            // this node is soon to be killed because it's missing a predecessor
            graph().replaceFixedWithFixed(this, graph().add(new BeginNode()));
        }
        if (this.isAlive()) {
            this.setLoopBegin(null);
        }
        if (loopStateAfter != null) {
            GraphUtil.tryKillUnused(loopStateAfter);
        }
    }

    @Override
    public void simplify(SimplifierTool tool) {
        Node prev = this.predecessor();
        while (tool.allUsagesAvailable() && prev instanceof BeginNode && prev.hasNoUsages()) {
            AbstractBeginNode begin = (AbstractBeginNode) prev;
            // Keep a single BeginNode in between LoopExitNodes and WithExceptionNodes that can
            // potentially be used as value inputs for proxies of this exit (transitively)
            if (!(prev.predecessor() instanceof WithExceptionNode)) {
                this.setNodeSourcePosition(begin.getNodeSourcePosition());
                graph().removeFixed(begin);
                prev = prev.predecessor();
            } else {
                break;
            }
        }
    }

    public static final String ErrorMessagePredecessorSplit = "Predecessor must not be a control split node that can be used as value node as that could mean scheduling of proxy nodes goes wrong";

    @Override
    public boolean verify() {
        /*
         * State verification for loop exits is special in that loop exits with exception handling
         * BCIs must not survive until code generation, thus they are cleared shortly before frame
         * state assignment, thus we only verify them until their removal
         */
        assert !this.graph().getGraphState().getFrameStateVerification().implies(GraphState.FrameStateVerificationFeature.LOOP_EXITS) ||
                        this.stateAfter != null : "Loop exit must have a state until FSA " + this;

        // Because the scheduler doesn't schedule ProxyNodes, the inputs to the ProxyNode can end up
        // in the wrong place in the earliest local schedule. Ensuring there's a BeginNode before
        // the LoopExitNode creates an earlier location where those nodes can be scheduled.
        assert !(predecessor() instanceof InvokeWithExceptionNode) : String.format("%s '%s'", ErrorMessagePredecessorSplit, predecessor());
        return super.verify();
    }
}
