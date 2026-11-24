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
package jdk.graal.compiler.core.test;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.ProfileData.SwitchProbabilityData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class LoopUnswitchFactorTest extends GraalCompilerTest implements CustomizedBytecodePattern {

    private static final int SUCCESSORS = 64;

    @Override
    public byte[] generateClass(String className) {
        // Three switches on the same invariant, executed on one quarter of the loop iterations.
        return ClassFile.of().build(ClassDesc.of(className), cb -> cb.withMethodBody("snippet",
                        MethodTypeDesc.of(CD_void, CD_int.arrayType(), CD_int, CD_int), ACC_PUBLIC_STATIC, b -> {
                            Label loop = b.newLabel();
                            Label end = b.newLabel();
                            Label increment = b.newLabel();
                            Label rareFalse = b.newLabel();
                            Label rareMerge = b.newLabel();
                            b.iconst_0().istore(3).labelBinding(loop);
                            b.iload(3).iload(2).if_icmpge(end);
                            b.ldc(0.25).iload(3).bipush(3).iand().ifne(rareFalse);
                            b.iconst_1().goto_(rareMerge).labelBinding(rareFalse).iconst_0().labelBinding(rareMerge);
                            b.invokestatic(cd(GraalDirectives.class), "injectBranchProbability", MethodTypeDesc.of(CD_boolean, CD_double, CD_boolean));
                            b.ifeq(increment);
                            for (int s = 0; s < 3; s++) {
                                Label merge = b.newLabel();
                                Label[] targets = new Label[SUCCESSORS];
                                List<SwitchCase> cases = new ArrayList<>();
                                for (int i = 0; i < SUCCESSORS; i++) {
                                    targets[i] = b.newLabel();
                                    if (i < SUCCESSORS - 1) {
                                        cases.add(SwitchCase.of(i, targets[i]));
                                    }
                                }
                                b.iload(1).tableswitch(0, SUCCESSORS - 2, targets[SUCCESSORS - 1], cases);
                                for (int i = 0; i < SUCCESSORS; i++) {
                                    b.labelBinding(targets[i]);
                                    b.aload(0).ldc((i + s) % SUCCESSORS).iload(3).iastore().goto_(merge);
                                }
                                b.labelBinding(merge);
                            }
                            b.labelBinding(increment).iinc(3, 1).goto_(loop).labelBinding(end).return_();
                        }));
    }

    private void checkFactor(double firstProbability, ProfileSource source, boolean expected) throws ClassNotFoundException {
        ResolvedJavaMethod method = getResolvedJavaMethod(getClass("UnswitchFactorSnippet"), "snippet");
        // Isolate the correlation heuristic from the minimum frequency and code size limits.
        OptionValues options = new OptionValues(getInitialOptions(),
                        DefaultLoopPolicies.Options.LoopUnswitchMinSplitFrequency, 0.0,
                        DefaultLoopPolicies.Options.LoopUnswitchTrivial, 100000,
                        DefaultLoopPolicies.Options.LoopUnswitchMaxIncrease, 100000);
        StructuredGraph graph = parseEager(method, AllowAssumptions.NO, options);
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        List<ControlSplitNode> splits = new ArrayList<>();
        for (IntegerSwitchNode node : graph.getNodes().filter(IntegerSwitchNode.class)) {
            Assert.assertEquals(SUCCESSORS, node.getSuccessorCount());
            double[] probabilities = new double[SUCCESSORS];
            Arrays.fill(probabilities, (1.0 - firstProbability) / (SUCCESSORS - 1));
            probabilities[0] = firstProbability;
            node.setProfileData(SwitchProbabilityData.create(probabilities, source));
            splits.add(node);
        }
        Assert.assertEquals(3, splits.size());
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        loops.detectCountedLoops();
        Assert.assertEquals(1, loops.loops().size());
        EconomicMap<ValueNode, List<ControlSplitNode>> candidates = EconomicMap.create();
        candidates.put(((IntegerSwitchNode) splits.get(0)).value(), splits);
        Assert.assertEquals(expected, new DefaultLoopPolicies().shouldUnswitch(loops.loops().get(0), candidates).shouldUnswitch());
    }

    @Test
    public void uniformProfile() throws ClassNotFoundException {
        checkFactor(1.0 / SUCCESSORS, ProfileSource.INJECTED, true);
    }

    @Test
    public void skewedProfile() throws ClassNotFoundException {
        checkFactor(0.9, ProfileSource.INJECTED, false);
    }

    @Test
    public void zeroProbability() throws ClassNotFoundException {
        checkFactor(0.0, ProfileSource.INJECTED, false);
    }

    @Test
    public void untrustedProfile() throws ClassNotFoundException {
        checkFactor(0.0, ProfileSource.UNKNOWN, true);
    }
}
