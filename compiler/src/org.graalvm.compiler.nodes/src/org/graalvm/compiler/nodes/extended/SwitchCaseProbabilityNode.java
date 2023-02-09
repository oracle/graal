/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ProfileData;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.Simplifiable;
import org.graalvm.compiler.nodes.spi.SimplifierTool;

import jdk.vm.ci.meta.JavaKind;

/**
 * Injects profile data to a {@link SwitchNode}. Specifically, it sets the successor probability for
 * the switch-case block this node is located in. For a given {@link SwitchNode}, either none or all
 * of its successors should be followed by a SwitchCaseProbabilityNode, and their probabilities
 * should sum to one.
 *
 * Instances of this node class will be simplified by their preceding switch node, injecting the
 * given probability of each branch into the switch profile data. Then all probability nodes will be
 * removed at once.
 */
@NodeInfo(cycles = CYCLES_0, cyclesRationale = "Artificial Node", size = SIZE_0)
public final class SwitchCaseProbabilityNode extends FixedWithNextNode implements Simplifiable, Lowerable {

    public static final NodeClass<SwitchCaseProbabilityNode> TYPE = NodeClass.create(SwitchCaseProbabilityNode.class);

    @Input ValueNode probability;

    public SwitchCaseProbabilityNode(ValueNode probability) {
        super(TYPE, StampFactory.forKind(JavaKind.Void));
        this.probability = probability;
    }

    public ValueNode getProbability() {
        return probability;
    }

    public void setProbability(ValueNode probability) {
        updateUsages(this.probability, probability);
        this.probability = probability;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        AbstractBeginNode switchSuccessor = AbstractBeginNode.prevBegin(this);
        GraalError.guarantee(switchSuccessor != null && switchSuccessor.predecessor() instanceof SwitchNode, "SwitchCaseProbabilityNode can only be used within a switch statement");

        /*
         * We simplify all SwitchCaseProbabilityNodes at once to check whether all switch cases have
         * an injected probability attached to them.
         */
        SwitchNode switchNode = (SwitchNode) switchSuccessor.predecessor();
        double[] probabilities = switchNode.getKeyProbabilities();
        double sum = 0.0;
        for (int i = 0; i < probabilities.length; ++i) {
            GraalError.guarantee(switchNode.keySuccessor(i).next() instanceof SwitchCaseProbabilityNode, "All switch branches must be followed by a SwitchCaseProbabilityNode");
            SwitchCaseProbabilityNode caseProbabilityNode = (SwitchCaseProbabilityNode) switchNode.keySuccessor(i).next();
            ValueNode probabilityNode = caseProbabilityNode.getProbability();
            if (!probabilityNode.isConstant()) {
                /*
                 * If any of the probabilities are not constant we bail out of simplification, which
                 * will cause compilation to fail later during lowering since the node will be left
                 * behind
                 */
                return;
            }

            double probabilityValue = probabilityNode.asJavaConstant().asDouble();
            if (probabilityValue < 0.0) {
                throw new GraalError("A negative probability of " + probabilityValue + " is not allowed!");
            } else if (probabilityValue > 1.0) {
                throw new GraalError("A probability of more than 1.0 (" + probabilityValue + ") is not allowed!");
            } else if (Double.isNaN(probabilityValue)) {
                /*
                 * We allow NaN if the node is in unreachable code that will eventually fall away,
                 * or else an error will be thrown during lowering since we keep the node around.
                 */
                return;
            }
            probabilities[i] = probabilityValue;
            sum += probabilityValue;
            caseProbabilityNode.replaceAtUsages(null);
            graph().removeFixed(caseProbabilityNode);
        }

        GraalError.guarantee(sum == 1.0, "Sum of all injected switch case probabilities must be 1");
        switchNode.setProfileData(ProfileData.SwitchProbabilityData.create(probabilities, ProfileSource.INJECTED));
    }

    @Override
    public void lower(LoweringTool tool) {
        throw new GraalError("Switch case probability could not be injected, because the probability value did not reduce to a constant value.");
    }
}
