/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.core.pair;

import java.util.Spliterator;
import java.util.stream.StreamSupport;

import org.graalvm.collections.Pair;
import org.graalvm.profdiff.core.CompilationFragment;
import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.Method;
import org.graalvm.profdiff.core.Writer;
import jdk.graal.compiler.util.CollectionsUtil;

/**
 * A pair of methods from two experiments with equal method names.
 */
public class MethodPair {
    /**
     * A method from the first experiment.
     */
    private final Method method1;

    /**
     * A method from the second experiment.
     */
    private final Method method2;

    /**
     * Constructs a method pair. The methods must have equal names. The first one must belong to
     * {@link ExperimentId#ONE} and the second one to {@link ExperimentId#TWO}.
     *
     * @param method1 a method from the first experiment
     * @param method2 a method from the second experiment
     */
    public MethodPair(Method method1, Method method2) {
        assert method1.getExperiment().getExperimentId() == ExperimentId.ONE;
        assert method2.getExperiment().getExperimentId() == ExperimentId.TWO;
        assert method1.getMethodName().equals(method2.getMethodName());
        this.method1 = method1;
        this.method2 = method2;
    }

    /**
     * Gets the method from the first experiment.
     */
    public Method getMethod1() {
        return method1;
    }

    /**
     * Gets the method from the second experiment.
     */
    public Method getMethod2() {
        return method2;
    }

    /**
     * Gets the name of the methods (which is equal for both).
     */
    public String getMethodName() {
        return method1.getMethodName();
    }

    /**
     * Gets the sum of the execution periods of the methods.
     */
    public long getTotalPeriod() {
        return method1.getTotalPeriod() + method2.getTotalPeriod();
    }

    /**
     * Returns {@code true} if both methods contain at least one hot compilation unit.
     */
    private boolean bothHot() {
        return method1.isHot() && method2.isHot();
    }

    /**
     * Returns {@code true} if at least one of the methods contains at least one hot compilation
     * unit.
     */
    private boolean someHot() {
        return method1.isHot() || method2.isHot();
    }

    /**
     * Returns an iterable over all pairs of compilations, excluding pairs where both compilations
     * are fragments. The compilations are sorted by their execution periods (the highest first).
     *
     * @return an iterable over pairs of hot compilations
     */
    public Iterable<CompilationUnitPair> getHotCompilationUnitPairsByDescendingPeriod() {
        return () -> {
            Spliterator<Pair<CompilationUnit, CompilationUnit>> pairs = CollectionsUtil.cartesianProduct(
                            method1.getHotCompilationUnitsByDescendingPeriod(),
                            method2.getHotCompilationUnitsByDescendingPeriod()).spliterator();
            return StreamSupport.stream(pairs, false).filter(pair -> !(pair.getLeft() instanceof CompilationFragment && pair.getRight() instanceof CompilationFragment)).map(
                            pair -> new CompilationUnitPair(pair.getLeft(), pair.getRight())).iterator();
        };
    }

    /**
     * Writes a header with the method name and a list of compilations using the destination writer.
     *
     * @param writer the destination writer
     */
    public void writeHeaderAndCompilationList(Writer writer) {
        writer.write("Method " + getMethodName());
        if (someHot() && !bothHot()) {
            ExperimentId experimentId = method1.isHot() ? ExperimentId.ONE : ExperimentId.TWO;
            writer.writeln(" is hot only in experiment " + experimentId);
        } else {
            writer.writeln();
        }
        writer.increaseIndent();
        method1.writeCompilationList(writer);
        method2.writeCompilationList(writer);
        writer.decreaseIndent();
    }
}
