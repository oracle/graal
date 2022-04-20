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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExperimentImpl implements Experiment {
    private final List<ExecutedMethod> executedMethods;
    private final String executionId;
    private final ExperimentId experimentId;
    private final long totalPeriod;
    private final int totalProftoolMethods;
    private final long graalPeriod;
    private final Map<String, List<ExecutedMethod>> methodsByName;

    public ExperimentImpl(
            List<ExecutedMethod> executedMethods,
            String executionId,
            ExperimentId experimentId,
            long totalPeriod,
            int totalProftoolMethods) {
        this.executedMethods = executedMethods;
        this.executionId = executionId;
        this.experimentId = experimentId;
        this.totalPeriod = totalPeriod;
        this.totalProftoolMethods = totalProftoolMethods;
        this.graalPeriod = executedMethods.stream().mapToLong(ExecutedMethod::getPeriod).sum();
        this.methodsByName = new HashMap<>();
        for (ExecutedMethod method : executedMethods) {
            List<ExecutedMethod> methods = methodsByName.computeIfAbsent(method.getCompilationMethodName(), k -> new ArrayList<>());
            methods.add(method);
        }
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
    public String createSummary() {
        String graalExecutionPercent = String.format("%.2f", (double) getGraalPeriod() / totalPeriod * 100);
        String graalHotExecutionPercent = String.format("%.2f", (double) countHotGraalPeriod() / totalPeriod * 100);
        return  "Experiment " + experimentId + " with execution ID " + executionId + "\n" +
                "    Collected optimization logs for " + executedMethods.size() + " methods\n" +
                "    Collected proftool data for " + totalProftoolMethods + " methods\n" +
                "    Graal-compiled methods account for " + graalExecutionPercent + "% of execution\n" +
                "    " + countHotMethods() + " hot methods account for " + graalHotExecutionPercent + "% of execution\n";
    }

    private long countHotMethods() {
        return executedMethods.stream().filter(ExecutedMethod::isHot).count();
    }

    private long countHotGraalPeriod() {
        return executedMethods.stream().filter(ExecutedMethod::isHot).mapToLong(ExecutedMethod::getPeriod).sum();
    }
}
