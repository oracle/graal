/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Predicate;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Assert;

import jdk.vm.ci.code.TargetDescription;

public abstract class MatchRuleTest extends GraalCompilerTest {
    private LIR lir;

    protected LIR getLIR() {
        return lir;
    }

    @Override
    protected LIRSuites createLIRSuites(OptionValues options) {
        LIRSuites suites = super.createLIRSuites(options);
        suites.getPreAllocationOptimizationStage().appendPhase(new CheckPhase());
        return suites;
    }

    public class CheckPhase extends LIRPhase<PreAllocationOptimizationContext> {
        @Override
        protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PreAllocationOptimizationContext context) {
            lir = lirGenRes.getLIR();
        }
    }

    protected void checkLIR(String methodName, Predicate<LIRInstruction> predicate, int expected) {
        checkLIR(methodName, predicate, 0, expected);
    }

    protected void checkLIR(String methodName, Predicate<LIRInstruction> predicate, int blockId, int expected) {
        compile(getResolvedJavaMethod(methodName), null);
        int actualOpNum = 0;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.getBlockById(lir.codeEmittingOrder()[blockId]))) {
            if (predicate.test(ins)) {
                actualOpNum++;
            }
        }
        Assert.assertEquals(expected, actualOpNum);
    }

    protected void checkLIRforAll(String methodName, Predicate<LIRInstruction> predicate, int expected) {
        checkLIRforAll(null, methodName, predicate, expected);
    }

    protected void checkLIRforAll(OptionValues options, String methodName, Predicate<LIRInstruction> predicate, int expected) {
        if (options != null) {
            compile(getResolvedJavaMethod(methodName), null, options);
        } else {
            compile(getResolvedJavaMethod(methodName), null);
        }
        int actualOpNum = 0;
        for (int blockId : lir.codeEmittingOrder()) {
            if (LIR.isBlockDeleted(blockId)) {
                continue;
            }
            BasicBlock<?> block = lir.getBlockById(blockId);
            for (LIRInstruction ins : lir.getLIRforBlock(block)) {
                if (predicate.test(ins)) {
                    actualOpNum++;
                }
            }
        }
        Assert.assertEquals(expected, actualOpNum);
    }
}
