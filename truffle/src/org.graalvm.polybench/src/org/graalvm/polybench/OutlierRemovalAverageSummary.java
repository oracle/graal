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
import org.graalvm.polybench.ast.Decl.Subroutine.Constructor;
import org.graalvm.polybench.ast.Decl.Variable;
import org.graalvm.polybench.ast.Expr;
import org.graalvm.polybench.ast.Expr.Atom.Floating;
import org.graalvm.polybench.ast.Expr.Atom.Null;
import org.graalvm.polybench.ast.Expr.BinaryOp;
import org.graalvm.polybench.ast.Expr.FunctionCall;
import org.graalvm.polybench.ast.Expr.ConstructorCall;
import org.graalvm.polybench.ast.Expr.ListLengthCall;
import org.graalvm.polybench.ast.Expr.ListSortCall;
import org.graalvm.polybench.ast.Expr.Reference.CompoundReference;
import org.graalvm.polybench.ast.Expr.Reference.Ident;
import org.graalvm.polybench.ast.Expr.Reference.Super;
import org.graalvm.polybench.ast.Expr.Reference.This;
import org.graalvm.polybench.ast.Operator;
import org.graalvm.polybench.ast.Stat;
import org.graalvm.polybench.ast.Stat.Assign;
import org.graalvm.polybench.ast.Stat.If;
import org.graalvm.polybench.ast.Stat.Return;
import org.graalvm.polybench.ast.Tree.Program.Builder;

import java.util.Arrays;
import java.util.Optional;

/**
 * Summarizes the results of a polybench benchmark with an average of iteration datapoints that fall
 * between `lowerThreshold` and `upperThreshold` percentiles.
 */
class OutlierRemovalAverageSummary extends AverageSummary {
    final double lowerThreshold;
    final double upperThreshold;

    OutlierRemovalAverageSummary(double lowerThreshold, double upperThreshold) {
        this.lowerThreshold = lowerThreshold;
        this.upperThreshold = upperThreshold;
    }

    @Override
    public Optional<Double> postprocess(double[] results) {
        if (results.length == 0) {
            return Optional.empty();
        }

        int n = results.length;
        Arrays.sort(results);
        int fromIndex = (int) Math.ceil(lowerThreshold * n);
        int toIndex = (int) Math.floor(upperThreshold * n);
        if (fromIndex >= toIndex) {
            return Optional.empty();
        }

        return super.postprocess(Arrays.copyOfRange(results, fromIndex, toIndex));
    }

    @Override
    public String toString() {
        return "OutlierRemovalAverageSummary{lowerThreshold=" + lowerThreshold + ", upperThreshold=" + upperThreshold + "}";
    }

    @Override
    public boolean stageClass(Builder programBuilder) {
        Stat thenBranch;
        if (!super.stageClass(programBuilder)) {
            return false;
        }
        try (Compound.Builder classBuilder = new Compound.Builder(programBuilder,
                        "OutlierRemovalAverageSummary",
                        new Ident[]{new Ident("AverageSummary")})) {
            classBuilder.append(Variable.of("lowerThreshold"));
            classBuilder.append(Variable.of("upperThreshold"));
            try (Constructor.Builder methodBuilder = new Constructor.Builder(classBuilder,
                            new Variable[]{Variable.of("lowerThreshold"), Variable.of("upperThreshold")})) {
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("lowerThreshold")), new Ident("lowerThreshold")));
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("upperThreshold")), new Ident("upperThreshold")));
            }
            try (Subroutine.Builder methodBuilder = new Subroutine.Builder(classBuilder, "postprocess", new Variable[]{Variable.of("results")})) {
                methodBuilder.append(Variable.of("resultsLength", new ListLengthCall(new Ident("results"))));
                // if (resultsLength == 0) return null;
                try (Stat.Block.Builder thenBranchBuilder = new Stat.Block.Builder(null)) {
                    thenBranchBuilder.append(new Return(new Null()));
                    thenBranch = thenBranchBuilder.build();
                }
                methodBuilder.append(new If(new BinaryOp(new Ident("resultsLength"), new Expr.Atom.Int(0), Operator.EQUALS), thenBranch, null));
                // sort the list and determine the slicing indexes
                methodBuilder.append(Variable.of("sortedResults", new ListSortCall(new Ident("results"))));
                methodBuilder.append(Variable.of("fromIndex", FunctionCall.of(new Ident("ceil"),
                                new Expr[]{new BinaryOp(new CompoundReference(new This(), new Ident("lowerThreshold")),
                                                new Ident("resultsLength"),
                                                Operator.MUL)})));
                methodBuilder.append(Variable.of("toIndex", FunctionCall.of(new Ident("floor"),
                                new Expr[]{new BinaryOp(new CompoundReference(new This(), new Ident("upperThreshold")),
                                                new Ident("resultsLength"),
                                                Operator.MUL)})));
                // if (fromIndex >= toIndex) return null;
                try (Stat.Block.Builder thenBranchBuilder = new Stat.Block.Builder(null)) {
                    thenBranchBuilder.append(new Return(new Null()));
                    thenBranch = thenBranchBuilder.build();
                }
                methodBuilder.append(new If(new BinaryOp(new Ident("fromIndex"), new Ident("toIndex"), Operator.GREATER_OR_EQUALS), thenBranch, null));
                // Return average of sliced list
                methodBuilder.append(Variable.of("slicedResults", FunctionCall.of(new Ident("sliceList"),
                                new Expr[]{new Ident("sortedResults"), new Ident("fromIndex"), new Ident("toIndex")})));
                methodBuilder.append(new Return(FunctionCall.of(new CompoundReference(new Super(), new Ident("postprocess")),
                                new Expr[]{new Ident("slicedResults")})));
            }
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    @Override
    public boolean stageSingletonInitialization(Builder programBuilder, String singleton) {
        try {
            programBuilder.append(Variable.of(singleton, new ConstructorCall(new Ident("OutlierRemovalAverageSummary"),
                            new Expr[]{new Floating((float) lowerThreshold), new Floating((float) upperThreshold)})));
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }
}
