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
package org.graalvm.bisect.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.graalvm.bisect.util.Writer;

public class ExperimentImpl implements Experiment {
    private final List<ExecutedMethod> executedMethods;
    private final String executionId;
    private final ExperimentId experimentId;
    private final long totalPeriod;
    private final int totalProftoolMethods;
    private Long graalPeriod;
    private final Map<String, List<ExecutedMethod>> methodsByName;

    public ExperimentImpl(
            String executionId,
            ExperimentId experimentId,
            long totalPeriod,
            int totalProftoolMethods) {
        this.executedMethods = new ArrayList<>();
        this.executionId = executionId;
        this.experimentId = experimentId;
        this.totalPeriod = totalPeriod;
        this.totalProftoolMethods = totalProftoolMethods;
        this.methodsByName = new HashMap<>();
    }

    @Override
    public ExperimentId getExperimentId() {
        return experimentId;
    }

    @Override
    public String getExecutionId() {
        return executionId;
    }

    @Override
    public long getTotalPeriod() {
        return totalPeriod;
    }

    @Override
    public long getGraalPeriod() {
        if (graalPeriod != null) {
            return graalPeriod;
        }
        graalPeriod = executedMethods.stream().mapToLong(ExecutedMethod::getPeriod).sum();
        return graalPeriod;
    }

    @Override
    public List<ExecutedMethod> getExecutedMethods() {
        return executedMethods;
    }

    @Override
    public Map<String, List<ExecutedMethod>> groupHotMethodsByName() {
        Map<String, List<ExecutedMethod>> map = new HashMap<>();
        for (ExecutedMethod method : executedMethods) {
            if (method.isHot()) {
                List<ExecutedMethod> methods = map.computeIfAbsent(method.getCompilationMethodName(), k -> new ArrayList<>());
                methods.add(method);
            }
        }
        return map;
    }

    @Override
    public List<ExecutedMethod> getMethodsByName(String compilationMethodName) {
        return methodsByName.get(compilationMethodName);
    }

    @Override
    public void writeExperimentSummary(Writer writer) {
        String graalExecutionPercent = String.format("%.2f", (double) getGraalPeriod() / totalPeriod * 100);
        String graalHotExecutionPercent = String.format("%.2f", (double) countHotGraalPeriod() / totalPeriod * 100);
        writer.writeln("Experiment " + experimentId + " with execution ID " + executionId);
        writer.increaseIndent();
        writer.writeln("Collected optimization logs for " + executedMethods.size() + " methods");
        writer.writeln("Collected proftool data for " + totalProftoolMethods + " methods");
        writer.writeln("Graal-compiled methods account for " + graalExecutionPercent + "% of execution");
        writer.writeln(countHotMethods() + " hot methods account for " + graalHotExecutionPercent + "% of execution");
        writer.decreaseIndent();
    }

    @Override
    public void writeMethodCompilationList(Writer writer, String compilationMethodName) {
        writer.writeln("In experiment " + experimentId);
        writer.increaseIndent();
        List<ExecutedMethod> executedMethods = methodsByName.get(compilationMethodName)
                        .stream()
                        .sorted(Comparator.comparingLong(executedMethod -> -executedMethod.getPeriod()))
                        .collect(Collectors.toList());
        long hotMethodCount = executedMethods.stream()
                        .filter(ExecutedMethod::isHot)
                        .count();
        writer.writeln(executedMethods.size() + " compilations (" + hotMethodCount + " of which are hot)");
        writer.writeln("Compilations");
        writer.increaseIndent();
        for (ExecutedMethod executedMethod : executedMethods) {
            writer.writeln(executedMethod.getCompilationId() + " (" + executedMethod.createSummaryOfMethodExecution() + ((executedMethod.isHot()) ? ") *hot*" : ")"));
        }
        writer.decreaseIndent(2);
    }

    private long countHotMethods() {
        return executedMethods.stream().filter(ExecutedMethod::isHot).count();
    }

    private long countHotGraalPeriod() {
        return executedMethods.stream().filter(ExecutedMethod::isHot).mapToLong(ExecutedMethod::getPeriod).sum();
    }

    /**
     * Add an executed method to this experiment. The executed method's experiment must be set to this instance.
     * @param executedMethod the executed method to be added
     */
    public void addExecutedMethod(ExecutedMethod executedMethod) {
        assert executedMethod.getExperiment() == this;
        graalPeriod = null;
        executedMethods.add(executedMethod);
        List<ExecutedMethod> methods = methodsByName.computeIfAbsent(executedMethod.getCompilationMethodName(), k -> new ArrayList<>());
        methods.add(executedMethod);
    }
}
