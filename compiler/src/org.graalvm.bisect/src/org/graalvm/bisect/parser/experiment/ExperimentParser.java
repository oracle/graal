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
package org.graalvm.bisect.parser.experiment;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.bisect.core.ExecutedMethodBuilder;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.core.ExperimentImpl;
import org.graalvm.bisect.core.optimization.Optimization;
import org.graalvm.bisect.core.optimization.OptimizationImpl;
import org.graalvm.bisect.core.optimization.OptimizationPhase;
import org.graalvm.bisect.core.optimization.OptimizationPhaseImpl;
import org.graalvm.bisect.json.JSONParser;

/**
 * Parses an experiment from its files.
 */
public class ExperimentParser {
    private final ExperimentFiles experimentFiles;
    private static final int GRAAL_COMPILER_LEVEL = 4;

    public ExperimentParser(ExperimentFiles experimentFiles) {
        this.experimentFiles = experimentFiles;
    }

    public ExperimentFiles getExperimentFiles() {
        return experimentFiles;
    }

    /**
     * Parses the experiment by combining proftool output and an optimization log. The optimization log is read first
     * and all compiled methods are parsed. The proftool output is then used to add information about execution periods.
     * Method compilations from the optimization log are matched with the proftool output according to compilation IDs.
     * @return the parsed experiment
     * @throws IOException failed to read the experiment files
     * @throws ExperimentParserException the experiment files had an in incorrect format
     */
    public Experiment parse() throws IOException, ExperimentParserException {
        // parse optimization logs to ExecutedMethodsBuilders
        Map<String, ExecutedMethodBuilder> methodByCompilationId = new HashMap<>();
        for (Reader optimizationLog : experimentFiles.getOptimizationLogs()) {
            ExecutedMethodBuilder method = parseCompiledMethod(optimizationLog);
            methodByCompilationId.put(method.getCompilationId(), method);
        }

        ProftoolLog proftoolLog = parseProftoolLog(experimentFiles.getProftoolOutput());

        // check that execution IDs match
        for (ExecutedMethodBuilder methodBuilder : methodByCompilationId.values()) {
            if (!Objects.equals(methodBuilder.getExecutionId(), proftoolLog.executionId)) {
                throw new ExperimentParserException(
                        "Execution ID mismatch: " + proftoolLog.executionId  +
                        " vs " + methodBuilder.getExecutionId(),
                        experimentFiles.getExperimentId());
            }
        }

        // augment optimization logs with proftool data
        for (ProftoolMethod method : proftoolLog.code) {
            if (method.level == null || method.level != GRAAL_COMPILER_LEVEL) {
                continue;
            }
            ExecutedMethodBuilder builder = methodByCompilationId.get(method.compilationId);
            if (builder == null) {
                System.out.println("Warning: Compilation ID " + method.compilationId + " not found in the optimization log");
            } else {
                builder.setPeriod(method.period);
            }
        }

        ExperimentImpl experiment = new ExperimentImpl(
                proftoolLog.executionId,
                experimentFiles.getExperimentId(),
                proftoolLog.totalPeriod,
                proftoolLog.code.size()
        );
        for (ExecutedMethodBuilder builder : methodByCompilationId.values()) {
            builder.setExperiment(experiment);
            experiment.addExecutedMethod(builder.build());
        }
        return experiment;
    }

    private ExecutedMethodBuilder parseCompiledMethod(Reader optimizationLog)
            throws IOException, ExperimentParserException {
        JSONParser parser = new JSONParser(optimizationLog);
        Map<String, Object> log = expectMap(parser.parse(), "root");
        ExecutedMethodBuilder builder = new ExecutedMethodBuilder();
        builder.setExecutionId(expectString(log.get("executionId"), "root.executionId"));
        builder.setCompilationId(expectString(log.get("compilationId"), "root.compilationId"));
        builder.setCompilationMethodName(
                expectString(log.get("compilationMethodName"), "root.compilationMethodName")
        );
        Map<String, Object> rootPhase = expectMap(log.get("rootPhase"), "root.rootPhase");
        builder.setRootPhase(parseOptimizationPhase(rootPhase));
        return builder;
    }

    private OptimizationPhase parseOptimizationPhase(Map<String, Object> log) throws ExperimentParserException {
        String phaseName = expectString(log.get("phaseName"), "phase.phaseName");
        OptimizationPhaseImpl optimizationPhase = new OptimizationPhaseImpl(phaseName);
        List<Object> optimizations = expectListNullable(log.get("optimizations"), "phase.optimizations");
        if (optimizations == null) {
            return optimizationPhase;
        }
        for (Object optimization : optimizations) {
            Map<String, Object> map = expectMap(optimization, "phase.optimizations[]");
            Object subphaseName = map.get("phaseName");
            if (subphaseName instanceof String) {
                optimizationPhase.addChild(parseOptimizationPhase(map));
            } else {
                optimizationPhase.addChild(parseOptimization(map));
            }
        }
        return optimizationPhase;
    }

    private Optimization parseOptimization(Map<String, Object> optimization) throws ExperimentParserException {
        String optimizationName = expectString(optimization.get("optimizationName"), "optimization.optimizationName");
        String eventName = expectString(optimization.get("eventName"), "optimization.eventName");
        int bci = expectInt(optimization.get("bci"), "optimization.bci");
        optimization.remove("optimizationName");
        optimization.remove("eventName");
        optimization.remove("bci");
        return new OptimizationImpl(optimizationName, eventName, bci, optimization);
    }

    private static class ProftoolMethod {
        String compilationId;
        long period;
        Integer level;
    }

    private static class ProftoolLog {
        String executionId;
        long totalPeriod;
        List<ProftoolMethod> code = new ArrayList<>();
    }

    private ProftoolLog parseProftoolLog(Reader profOutput) throws IOException, ExperimentParserException {
        JSONParser parser = new JSONParser(profOutput);
        ProftoolLog proftoolLog = new ProftoolLog();
        Map<String, Object> root = expectMap(parser.parse(), "root");
        proftoolLog.executionId = expectString(root.get("executionId"), "root.executionId");
        proftoolLog.totalPeriod = expectLong(root.get("totalPeriod"), "root.totalPeriod");
        List<Object> codeObjects = expectList(root.get("code"), "root.code");
        for (Object codeObject : codeObjects) {
            Map<String, Object> code = expectMap(codeObject, "root.code[]");
            ProftoolMethod method = new ProftoolMethod();
            method.compilationId = expectStringNullable(code.get("compileId"), "root.code[].compileId");
            if (method.compilationId != null && method.compilationId.endsWith("%")) {
                method.compilationId = method.compilationId.substring(0, method.compilationId.length() - 1);
            }
            method.period = expectLong(code.get("period"), "root.code[].period");
            method.level = expectIntegerNullable(code.get("level"), "root.code[].level");
            proftoolLog.code.add(method);
        }
        return proftoolLog;
    }

    private String expectString(Object object, String path) throws ExperimentParserException {
        if (object instanceof String) {
            return (String) object;
        }
        throw new ExperimentParserException("expected " + path + " to be a string", experimentFiles.getExperimentId());
    }

    private String expectStringNullable(Object object, String path) throws ExperimentParserException {
        if (object == null) {
            return null;
        }
        return expectString(object, path);
    }

    private long expectLong(Object object, String path) throws ExperimentParserException {
        if (object instanceof Number) {
            return ((Number) object).longValue();
        }
        throw new ExperimentParserException("expected " + path + " to be a number", experimentFiles.getExperimentId());
    }

    private int expectInt(Object object, String path) throws ExperimentParserException {
        if (object instanceof Number) {
            return ((Number) object).intValue();
        }
        throw new ExperimentParserException(
                "expected " + path + " to be an int",
                experimentFiles.getExperimentId()
        );
    }

    private Integer expectIntegerNullable(Object object, String path) throws ExperimentParserException {
        if (object == null) {
            return (Integer) object;
        }
        return expectInt(object, path);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> expectMap(Object object, String path) throws ExperimentParserException {
        if (object instanceof Map) {
            return (Map<String, Object>) object;
        }
        throw new ExperimentParserException("expected " + path + " to be an object", experimentFiles.getExperimentId());
    }

    @SuppressWarnings("unchecked")
    private List<Object> expectList(Object object, String path) throws ExperimentParserException {
        if (object instanceof List) {
            return (List<Object>) object;
        }
        throw new ExperimentParserException("expected " + path + " to be an array", experimentFiles.getExperimentId());
    }

    private List<Object> expectListNullable(Object object, String path) throws ExperimentParserException {
        if (object == null) {
            return null;
        }
        return expectList(object, path);
    }
}
