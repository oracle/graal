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
package jdk.graal.compiler.duplication.test;

import static jdk.graal.compiler.core.test.GraalCompilerTest.getInitialOptions;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.duplication.opt.BudgetCostModel;
import jdk.graal.compiler.duplication.opt.OptimizationEffect;

public class DuplicationCostModelTest {

    static BudgetCostModel model;

    @After
    public void tearDown() {
        model = null;
    }

    @Test
    public void test01() {
        DebugContext debug = new Builder(getInitialOptions()).build();
        model = new BudgetCostModel(1000);
        /*
         * the same benefit vs cost must get costlier the harder we reach our bound
         */
        for (int i = 0; i < 120; i++) {
            try (DebugContext.Scope _ = debug.scope("OptCostModel")) {
                debug.log("Iteration %d", i);
            }
            if (i % 7 == 0 && i > 0) {
                try (DebugContext.Scope _ = debug.scope("OptCostModel")) {
                    debug.log("Penetrating improvement overruling");
                }
                int benefit = 75;
                int cost = 25;
                toModel(benefit, cost);
            } else if (i % 9 == 0 && i > 0) {
                try (DebugContext.Scope _ = debug.scope("OptCostModel")) {
                    debug.log("Penetrating high improvement overruling");
                }
                int benefit = 100;
                int cost = 75;
                toModel(benefit, cost);
            } else {
                int benefit = 10;
                int cost = 10;
                toModel(benefit, cost);
            }
        }

        debug.log("Adding real improvement late");
        toModel(100, 1);
        debug.log("Adding real very weak improvement late");
        toModel(1, 100);

    }

    static void toModel(int benefit, int cost) {
        DebugContext debug = new Builder(getInitialOptions()).build();
        OptimizationEffect op = model.potentialOpt(benefit, cost);
        model.applyLastOp();
        try (DebugContext.Scope _ = debug.scope("OptCostModel")) {
            debug.log("%s", op);
            debug.log("%s", model);
        }
    }

    @Test
    public void test02() {
        model = new BudgetCostModel(1000);
        model.potentialOpt(1, 5);
        model.applyLastOp();
        model.potentialOpt(1, 5);
        model.applyLastOp();
        model.potentialOpt(2, 15);
        model.applyLastOp();
        model.potentialOpt(1, 5);
        model.applyLastOp();
        model.potentialOpt(1, 5);
        model.applyLastOp();
        model.potentialOpt(1, 5);
        model.applyLastOp();
        model.potentialOpt(1, 5);
        model.applyLastOp();
        model.potentialOpt(1, 5);
        model.applyLastOp();
    }

    @Test
    public void test03() {
        model = new BudgetCostModel(10);
        model.potentialOpt(1, 5);
        model.applyLastOp();
        OptimizationEffect effect = model.potentialOpt(1, 6);
        model.applyLastOp();
        Assert.assertTrue(effect.budgetExceeded());
    }

}
