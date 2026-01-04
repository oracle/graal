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

import org.graalvm.polybench.ast.Decl;
import org.graalvm.polybench.ast.Decl.Subroutine.Constructor;
import org.graalvm.polybench.ast.Decl.Variable;
import org.graalvm.polybench.ast.Expr;
import org.graalvm.polybench.ast.Expr.Atom;
import org.graalvm.polybench.ast.Expr.Atom.Floating;
import org.graalvm.polybench.ast.Expr.Atom.Int;
import org.graalvm.polybench.ast.Expr.BinaryOp;
import org.graalvm.polybench.ast.Expr.FunctionCall;
import org.graalvm.polybench.ast.Expr.ConstructorCall;
import org.graalvm.polybench.ast.Expr.Reference.CompoundReference;
import org.graalvm.polybench.ast.Expr.Reference.Ident;
import org.graalvm.polybench.ast.Expr.Reference.This;
import org.graalvm.polybench.ast.Operator;
import org.graalvm.polybench.ast.Stat;
import org.graalvm.polybench.ast.Stat.Assign;
import org.graalvm.polybench.ast.Stat.Return;
import org.graalvm.polybench.ast.Stat.Throw;
import org.graalvm.polybench.ast.Tree.Program.Builder;
import org.graalvm.polyglot.Value;

import java.util.Optional;

class PeakTimeMetric extends Metric implements StageableClass {
    long startTime;
    long endTime;
    long totalTime;
    int totalIterations;

    int batchSize;
    Unit unit;

    private enum Unit {
        ns(1.0),
        us(1_000.0),
        ms(1_000_000.0),
        s(1_000_000_000.0);

        private final double factor;

        Unit(double factor) {
            this.factor = factor;
        }
    }

    PeakTimeMetric() {
        this.totalTime = 0L;
        this.batchSize = 1;
        this.unit = Unit.ms;
    }

    @Override
    public void parseBenchSpecificOptions(Value runner) {
        if (runner.hasMember("batchSize")) {
            Value batchSizeMember = runner.getMember("batchSize");
            this.batchSize = batchSizeMember.canExecute() ? batchSizeMember.execute().asInt() : batchSizeMember.asInt();
        }
        if (runner.hasMember("unit")) {
            Value unitMember = runner.getMember("unit");
            String u = unitMember.canExecute() ? unitMember.execute().asString() : unitMember.asString();
            this.unit = Unit.valueOf(u);
        }
    }

    @Override
    public void beforeIteration(boolean warmup, int iteration, Config config) {
        startTime = System.nanoTime();
    }

    @Override
    public void afterIteration(boolean warmup, int iteration, Config config) {
        endTime = System.nanoTime();

        totalTime += endTime - startTime;
        totalIterations++;
    }

    @Override
    public void reset() {
        startTime = 0L;
        endTime = 0L;
        totalTime = 0L;
        totalIterations = 0;
    }

    @Override
    public Optional<Double> reportAfterIteration(Config config) {
        return Optional.of((endTime - startTime) / (unit.factor * batchSize));
    }

    @Override
    public Optional<Double> reportAfterAll() {
        return Optional.of(totalTime / (totalIterations * unit.factor * batchSize));
    }

    @Override
    public String unit() {
        return unit.name();
    }

    @Override
    public String name() {
        return "peak time";
    }

    @Override
    public boolean stageClass(Builder programBuilder) {
        Stat thenBranch;
        try (Decl.Compound.Builder classBuilder = new Decl.Compound.Builder(programBuilder, "PeakTimeMetric", null)) {
            classBuilder.append(Variable.of("startTime"));
            classBuilder.append(Variable.of("endTime"));
            classBuilder.append(Variable.of("totalTime"));
            classBuilder.append(Variable.of("totalIterations"));
            classBuilder.append(Variable.of("batchSize"));
            try (Constructor.Builder methodBuilder = new Constructor.Builder(classBuilder, null)) {
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("startTime")), new Int(0)));
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("endTime")), new Int(0)));
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("totalTime")), new Int(0)));
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("totalIterations")), new Int(0)));
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("batchSize")), new Int(1)));
            }
            try (Decl.Subroutine.Builder methodBuilder = new Decl.Subroutine.Builder(classBuilder,
                            "beforeIteration",
                            new Variable[]{Variable.of("warmup"), Variable.of("iteration")})) {
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("startTime")),
                                FunctionCall.of(new Ident("currentTimeNanos"))));
            }
            try (Decl.Subroutine.Builder methodBuilder = new Decl.Subroutine.Builder(classBuilder,
                            "afterIteration",
                            new Variable[]{Variable.of("warmup"), Variable.of("iteration")})) {
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("endTime")),
                                FunctionCall.of(new Ident("currentTimeNanos"))));
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("totalTime")),
                                new BinaryOp(new CompoundReference(new This(), new Ident("totalTime")),
                                                new BinaryOp(new CompoundReference(new This(), new Ident("endTime")),
                                                                new CompoundReference(new This(), new Ident("startTime")),
                                                                Operator.MINUS),
                                                Operator.PLUS)));
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("totalIterations")),
                                new BinaryOp(new CompoundReference(new This(), new Ident("totalIterations")), new Int(1), Operator.PLUS)));
            }
            try (Decl.Subroutine.Builder methodBuilder = new Decl.Subroutine.Builder(classBuilder, "reset", null)) {
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("startTime")), new Int(0)));
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("endTime")), new Int(0)));
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("totalTime")), new Int(0)));
                methodBuilder.append(new Assign(new CompoundReference(new This(), new Ident("totalIterations")), new Int(0)));
            }
            try (Decl.Subroutine.Builder methodBuilder = new Decl.Subroutine.Builder(classBuilder, "reportAfterIteration", null)) {
                methodBuilder.append(Variable.of("timeDelta", new BinaryOp(new CompoundReference(new This(), new Ident("endTime")),
                                new CompoundReference(new This(), new Ident("startTime")),
                                Operator.MINUS)));
                methodBuilder.append(Variable.of("castToMillis", new BinaryOp(new Floating(1_000_000),
                                new CompoundReference(new This(), new Ident("batchSize")),
                                Operator.MUL)));
                methodBuilder.append(new Return(new BinaryOp(new Ident("timeDelta"), new Ident("castToMillis"), Operator.DIV)));
            }
            try (Decl.Subroutine.Builder methodBuilder = new Decl.Subroutine.Builder(classBuilder, "reportAfterAll", null)) {
                methodBuilder.append(Variable.of("averageTime", new BinaryOp(new CompoundReference(new This(), new Ident("totalTime")),
                                new CompoundReference(new This(), new Ident("totalIterations")),
                                Operator.DIV)));
                methodBuilder.append(Variable.of("castToMillis", new BinaryOp(new Floating(1_000_000),
                                new CompoundReference(new This(), new Ident("batchSize")),
                                Operator.MUL)));
                methodBuilder.append(new Return(new BinaryOp(new Ident("averageTime"), new Ident("castToMillis"), Operator.DIV)));
            }
            try (Decl.Subroutine.Builder methodBuilder = new Decl.Subroutine.Builder(classBuilder, "unit", null)) {
                methodBuilder.append(new Return(new Atom.String("ms")));
            }
            try (Decl.Subroutine.Builder methodBuilder = new Decl.Subroutine.Builder(classBuilder, "parseBenchSpecificOptions", null)) {
                methodBuilder.append(Variable.of("optionsPresent",
                                new BinaryOp(FunctionCall.of(new Ident("checkIfFunctionExists"), new Expr[]{new Atom.String("batchSize")}),
                                                FunctionCall.of(new Ident("checkIfFunctionExists"), new Expr[]{new Atom.String("unit")}),
                                                Operator.OR)));
                // if (optionsPresent) throw new Exception();
                try (Stat.Block.Builder thenBranchBuilder = new Stat.Block.Builder(null)) {
                    thenBranchBuilder.append(new Throw(new Atom.String("The staged version of PeakTimeMetric cannot handle bench specific options such as 'batchSize' or 'unit'!")));
                    thenBranch = thenBranchBuilder.build();
                }
                methodBuilder.append(new Stat.If(new Ident("optionsPresent"), thenBranch, null));
            }
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    @Override
    public boolean stageSingletonInitialization(Builder programBuilder, String singleton) {
        try {
            programBuilder.append(new Variable(singleton, ConstructorCall.of(new Ident("PeakTimeMetric"))));
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }
}
