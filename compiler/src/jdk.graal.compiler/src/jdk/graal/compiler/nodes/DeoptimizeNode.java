/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;
import jdk.vm.ci.meta.Value;

@NodeInfo(shortName = "Deopt", nameTemplate = "Deopt {p#reason/s}")
public final class DeoptimizeNode extends AbstractDeoptimizeNode implements Lowerable, LIRLowerable, StaticDeoptimizingNode {
    public static final int DEFAULT_DEBUG_ID = 0;

    public static final NodeClass<DeoptimizeNode> TYPE = NodeClass.create(DeoptimizeNode.class);
    protected DeoptimizationAction action;
    protected DeoptimizationReason reason;
    protected int debugId;
    protected Speculation speculation;
    protected boolean mayConvertToGuard;

    public DeoptimizeNode(DeoptimizationAction action, DeoptimizationReason reason) {
        this(action, reason, DEFAULT_DEBUG_ID, SpeculationLog.NO_SPECULATION, null);
    }

    public DeoptimizeNode(DeoptimizationAction action, DeoptimizationReason reason, Speculation speculation) {
        this(action, reason, DEFAULT_DEBUG_ID, speculation, null);
    }

    public DeoptimizeNode(DeoptimizationAction action, DeoptimizationReason reason, int debugId, Speculation speculation, FrameState stateBefore) {
        super(TYPE, stateBefore);
        assert action != null;
        assert reason != null;
        this.action = action;
        this.reason = reason;
        this.debugId = debugId;
        assert speculation != null;
        this.speculation = speculation;
        this.mayConvertToGuard = true;
    }

    @Override
    public DeoptimizationAction getAction() {
        return action;
    }

    @Override
    public void setAction(DeoptimizationAction action) {
        this.action = action;
    }

    @Override
    public DeoptimizationReason getReason() {
        return reason;
    }

    @Override
    public void setReason(DeoptimizationReason reason) {
        this.reason = reason;
    }

    @SuppressWarnings("deprecation")
    public int getDebugId() {
        int deoptDebugId = debugId;
        if (deoptDebugId == DEFAULT_DEBUG_ID) {
            DebugContext debug = getDebug();
            if ((debug.isDumpEnabledForMethod() || debug.isLogEnabledForMethod())) {
                deoptDebugId = this.getId();
            }
        }
        return deoptDebugId;
    }

    public void setDebugId(int debugId) {
        assert debugId != DEFAULT_DEBUG_ID;
        this.debugId = debugId;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value actionAndReason = tool.emitJavaConstant(tool.getMetaAccess().encodeDeoptActionAndReason(action, reason, getDebugId()));
        Value speculationValue = tool.emitJavaConstant(tool.getMetaAccess().encodeSpeculation(speculation));
        gen.getLIRGeneratorTool().emitDeoptimize(actionAndReason, speculationValue, gen.state(this));
    }

    @Override
    public ValueNode getActionAndReason(MetaAccessProvider metaAccess) {
        return ConstantNode.forConstant(metaAccess.encodeDeoptActionAndReason(action, reason, getDebugId()), metaAccess, graph());
    }

    @Override
    public ValueNode getSpeculation(MetaAccessProvider metaAccess) {
        return ConstantNode.forConstant(metaAccess.encodeSpeculation(speculation), metaAccess, graph());
    }

    @Override
    public Speculation getSpeculation() {
        return speculation;
    }

    public boolean canFloat() {
        return canFloat(getReason(), getAction());
    }

    /**
     * Some combinations of reason and action should never be converted into floating guards as they
     * need to be anchored in the control flow. If they are allowed to float they could move too
     * high and would be executed under the wrong conditions.
     */
    public static boolean canFloat(DeoptimizationReason reason, DeoptimizationAction action) {
        return action != DeoptimizationAction.None && reason != DeoptimizationReason.Unresolved && reason != DeoptimizationReason.NotCompiledExceptionHandler &&
                        reason != DeoptimizationReason.UnreachedCode;
    }

    /**
     * Determine whether this deopt may be converted to a guard. Conversion of a deopt in a branch
     * to a fixed guard can eliminate other nodes, including side effects, in the branch. Even if
     * the resulting guard {@linkplain #canFloat() is not allowed to float}, it can therefore end up
     * with an imprecise frame state. Preventing the conversion to a guard ensures that we don't
     * lose precise states, and this is necessary in some contexts.
     */
    public boolean mayConvertToGuard() {
        return mayConvertToGuard;
    }

    /** Set a new value for the {@link #mayConvertToGuard()} flag. */
    public void mayConvertToGuard(boolean newMayConvertToGuard) {
        this.mayConvertToGuard = newMayConvertToGuard;
    }

    @NodeIntrinsic
    public static native void deopt(@ConstantNodeParameter DeoptimizationAction action, @ConstantNodeParameter DeoptimizationReason reason);

    public void setSpeculation(Speculation speculate) {
        assert speculation.equals(SpeculationLog.NO_SPECULATION);
        speculation = speculate;
    }
}
