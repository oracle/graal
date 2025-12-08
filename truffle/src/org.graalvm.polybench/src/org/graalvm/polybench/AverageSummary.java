/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench;

import org.graalvm.polybench.ast.Decl.Compound;
import org.graalvm.polybench.ast.Decl.Subroutine;
import org.graalvm.polybench.ast.Decl.Variable;
import org.graalvm.polybench.ast.Expr.Atom.Int;
import org.graalvm.polybench.ast.Expr.Atom.Null;
import org.graalvm.polybench.ast.Expr.BinaryOp;
import org.graalvm.polybench.ast.Expr.ConstructorCall;
import org.graalvm.polybench.ast.Expr.ListLengthCall;
import org.graalvm.polybench.ast.Expr.Reference.Ident;
import org.graalvm.polybench.ast.Operator;
import org.graalvm.polybench.ast.Stat;
import org.graalvm.polybench.ast.Stat.Assign;
import org.graalvm.polybench.ast.Stat.Block;
import org.graalvm.polybench.ast.Stat.Return;
import org.graalvm.polybench.ast.Tree.Program.Builder;

import java.util.Optional;

/**
 * Summarizes the results of a polybench benchmark with an average value computed from iteration
 * datapoints.
 */
class AverageSummary implements Summary, StageableClass {
    @Override
    public Optional<Double> postprocess(double[] results) {
        double sum = 0;
        for (Double result : results) {
            sum += result;
        }
        return results.length > 0 ? Optional.of(sum / results.length) : Optional.empty();
    }

    @Override
    public String toString() {
        return "AverageSummary{}";
    }

    @Override
    public boolean stageClass(Builder programBuilder) {
        Stat thenBranch;
        Stat elseBranch;
        try (Compound.Builder classBuilder = new Compound.Builder(programBuilder, "AverageSummary", null)) {
            try (Subroutine.Builder methodBuilder = new Subroutine.Builder(classBuilder, "postprocess", new Variable[]{Variable.of("results")})) {
                methodBuilder.append(Variable.of("sum", new Int(0)));
                try (Stat.Foreach.Builder foreachBuilder = new Stat.Foreach.Builder(methodBuilder, Variable.of("result"), new Ident("results"))) {
                    foreachBuilder.append(new Assign(new Ident("sum"),
                                    new BinaryOp(new Ident("sum"), new Ident("result"), Operator.PLUS)));
                }
                methodBuilder.append(Variable.of("resultsLength", new ListLengthCall(new Ident("results"))));
                // if (resultsLength > 0)
                try (Block.Builder thenBranchBuilder = new Block.Builder(null)) {
                    thenBranchBuilder.append(new Return(new BinaryOp(new Ident("sum"), new Ident("resultsLength"), Operator.DIV)));
                    thenBranch = thenBranchBuilder.build();
                }
                try (Block.Builder elseBranchBuilder = new Block.Builder(null)) {
                    elseBranchBuilder.append(new Return(new Null()));
                    elseBranch = elseBranchBuilder.build();
                }
                methodBuilder.append(new Stat.If(new BinaryOp(new Ident("resultsLength"), new Int(0), Operator.GREATER_THAN), thenBranch, elseBranch));
            }
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    @Override
    public boolean stageSingletonInitialization(Builder programBuilder, String singleton) {
        try {
            programBuilder.append(Variable.of(singleton, ConstructorCall.of(new Ident("AverageSummary"))));
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }
}
