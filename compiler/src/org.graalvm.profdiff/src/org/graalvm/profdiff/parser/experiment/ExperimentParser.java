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
package org.graalvm.profdiff.parser.experiment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.InliningTreeNode;
import org.graalvm.profdiff.core.ProftoolMethod;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.util.json.JSONParser;
import org.graalvm.util.json.JSONParserException;

/**
 * Parses an experiment from its files.
 */
public class ExperimentParser {
    private static final class PartialCompilationUnit {
        String compilationId;

        String methodName;

        InliningTreeNode inliningTreeRoot;

        OptimizationPhase rootPhase;

        long period;
    }

    private static class ProftoolLog {
        String executionId;

        long totalPeriod;

        List<ProftoolMethod> methods = new ArrayList<>();
    }

    private final ExperimentFiles experimentFiles;

    /**
     * The compiler level of graal-compiled methods in the output of proftool.
     */
    private static final int GRAAL_COMPILER_LEVEL = 4;

    /**
     * The name of the resource being parsed at the moment.
     */
    private String currentResourceName = null;

    public ExperimentParser(ExperimentFiles experimentFiles) {
        this.experimentFiles = experimentFiles;
    }

    /**
     * Parses the experiment by combining proftool output and an optimization log. The optimization
     * log is read first and all compiled methods are parsed. The proftool output is then used to
     * add information about execution periods. Method compilations from the optimization log are
     * matched with the proftool output according to compilation IDs.
     *
     * @return the parsed experiment
     * @throws IOException failed to read the experiment files
     * @throws ExperimentParserTypeError the experiment files had an in incorrect format
     */
    public Experiment parse() throws IOException, ExperimentParserError {
        EconomicMap<String, PartialCompilationUnit> partialCompilationUnits = EconomicMap.create();
        for (ExperimentFiles.NamedReader reader : experimentFiles.getOptimizationLogs()) {
            PartialCompilationUnit parseCompilationUnit = parseCompilationUnit(reader);
            partialCompilationUnits.put(parseCompilationUnit.compilationId, parseCompilationUnit);
        }

        Experiment experiment;
        Optional<ExperimentFiles.NamedReader> proftoolLogReader = experimentFiles.getProftoolOutput();
        if (proftoolLogReader.isPresent()) {
            ProftoolLog proftoolLog = parseProftoolLog(proftoolLogReader.get());

            for (ProftoolMethod method : proftoolLog.methods) {
                if (method.getLevel() == null || method.getLevel() != GRAAL_COMPILER_LEVEL) {
                    continue;
                }
                PartialCompilationUnit unit = partialCompilationUnits.get(method.getCompilationId());
                if (unit == null) {
                    System.out.println("Warning: Compilation ID " + method.getCompilationId() + " not found in the optimization log");
                } else {
                    unit.period = method.getPeriod();
                }
            }

            experiment = new Experiment(
                            proftoolLog.executionId,
                            experimentFiles.getExperimentId(),
                            experimentFiles.getCompilationKind(),
                            proftoolLog.totalPeriod,
                            proftoolLog.methods);
        } else {
            experiment = new Experiment(experimentFiles.getExperimentId(), experimentFiles.getCompilationKind());
        }
        for (PartialCompilationUnit unit : partialCompilationUnits.getValues()) {
            experiment.addCompilationUnit(unit.methodName, unit.compilationId, unit.inliningTreeRoot, unit.rootPhase, unit.period);
        }
        return experiment;
    }

    private PartialCompilationUnit parseCompilationUnit(ExperimentFiles.NamedReader reader) throws IOException, ExperimentParserError {
        currentResourceName = reader.getName();
        try {
            JSONParser parser = new JSONParser(reader.getReader());
            EconomicMap<String, Object> log = expectMap(parser.parse(), "the root of a compiled method", false);
            PartialCompilationUnit compilationUnit = new PartialCompilationUnit();
            compilationUnit.compilationId = expectString(log, "compilationId", false);
            compilationUnit.methodName = expectString(log, "compilationMethodName", false);
            compilationUnit.inliningTreeRoot = parseInliningTreeNode(log.get("inliningTreeRoot"));
            EconomicMap<String, Object> rootPhase = expectMap(log.get("rootPhase"), "the root phase of a method", false);
            compilationUnit.rootPhase = parseOptimizationPhase(rootPhase);
            return compilationUnit;
        } catch (JSONParserException parserException) {
            throw new ExperimentParserError(experimentFiles.getExperimentId(), currentResourceName, parserException.getMessage());
        }
    }

    private InliningTreeNode parseInliningTreeNode(Object obj) throws ExperimentParserTypeError {
        if (obj == null) {
            return null;
        }
        EconomicMap<String, Object> map = expectMap(obj, "inliningTreeNode", false);
        String methodName = expectString(map, "targetMethodName", true);
        int bci = expectInteger(map, "bci");
        boolean positive = expectBoolean(map, "positive");
        List<String> reason = expectStringList(map, "reason", true);
        InliningTreeNode inliningTreeNode = new InliningTreeNode(methodName, bci, positive, reason);
        List<Object> inlinees = expectList(map, "inlinees", true);
        if (inlinees == null) {
            return inliningTreeNode;
        }
        for (Object inlinee : inlinees) {
            inliningTreeNode.addChild(parseInliningTreeNode(inlinee));
        }
        return inliningTreeNode;
    }

    private OptimizationPhase parseOptimizationPhase(EconomicMap<String, Object> log) throws ExperimentParserTypeError {
        String phaseName = expectString(log, "phaseName", false);
        OptimizationPhase optimizationPhase = new OptimizationPhase(phaseName);
        List<Object> optimizations = expectList(log, "optimizations", true);
        if (optimizations == null) {
            return optimizationPhase;
        }
        for (Object optimization : optimizations) {
            EconomicMap<String, Object> map = expectMap(optimization, "optimization", false);
            Object subphaseName = map.get("phaseName");
            if (subphaseName instanceof String) {
                optimizationPhase.addChild(parseOptimizationPhase(map));
            } else {
                optimizationPhase.addChild(parseOptimization(map));
            }
        }
        return optimizationPhase;
    }

    private Optimization parseOptimization(EconomicMap<String, Object> optimization) throws ExperimentParserTypeError {
        String optimizationName = expectString(optimization, "optimizationName", false);
        String eventName = expectString(optimization, "eventName", false);
        EconomicMap<String, Object> rawPosition = expectMap(optimization.get("position"), "optimization position", true);
        EconomicMap<String, Integer> position = null;
        if (rawPosition != null) {
            MapCursor<String, Object> cursor = rawPosition.getEntries();
            position = EconomicMap.create();
            while (cursor.advance()) {
                if (!(cursor.getValue() instanceof Integer)) {
                    throw new ExperimentParserTypeError(experimentFiles.getExperimentId(), currentResourceName, "bci", Integer.class, cursor.getValue());
                }
                position.put(cursor.getKey(), (Integer) cursor.getValue());
            }
        }
        optimization.removeKey("optimizationName");
        optimization.removeKey("eventName");
        optimization.removeKey("position");
        EconomicMap<String, Object> properties = optimization.isEmpty() ? null : optimization;
        return new Optimization(optimizationName, eventName, position, properties);
    }

    private ProftoolLog parseProftoolLog(ExperimentFiles.NamedReader profOutput) throws IOException, ExperimentParserError {
        currentResourceName = profOutput.getName();
        try {
            JSONParser parser = new JSONParser(profOutput.getReader());
            ProftoolLog proftoolLog = new ProftoolLog();
            EconomicMap<String, Object> root = expectMap(parser.parse(), "root", false);
            proftoolLog.executionId = expectString(root, "executionId", false);
            proftoolLog.totalPeriod = expectLong(root, "totalPeriod");
            List<Object> codeObjects = expectList(root, "code", false);
            for (Object codeObject : codeObjects) {
                EconomicMap<String, Object> code = expectMap(codeObject, "root.code[]", false);
                String compilationId = expectString(code, "compileId", true);
                if (compilationId != null && compilationId.endsWith("%")) {
                    compilationId = compilationId.substring(0, compilationId.length() - 1);
                }
                String name = expectString(code, "name", false);
                int colonIndex = name.indexOf(':');
                if (colonIndex != -1 && colonIndex + 2 <= name.length()) {
                    name = name.substring(colonIndex + 2);
                }
                long period = expectLong(code, "period");
                Integer level = expectIntegerNullable(code, "level");
                proftoolLog.methods.add(new ProftoolMethod(compilationId, name, level, period));
            }
            return proftoolLog;
        } catch (JSONParserException parserException) {
            throw new ExperimentParserError(experimentFiles.getExperimentId(), currentResourceName, parserException.getMessage());
        }
    }

    private List<String> expectStringList(EconomicMap<String, Object> map, String key, boolean listNullable) throws ExperimentParserTypeError {
        List<Object> objects = expectList(map, key, listNullable);
        if (objects == null) {
            return null;
        }
        List<String> strings = null;
        for (Object object : objects) {
            if (object instanceof String) {
                if (strings == null) {
                    strings = new ArrayList<>();
                }
                strings.add((String) object);
            } else {
                throw new ExperimentParserTypeError(experimentFiles.getExperimentId(), currentResourceName, key + "[]", String.class, object);
            }
        }
        return strings;
    }

    private boolean expectBoolean(EconomicMap<String, Object> map, String key) throws ExperimentParserTypeError {
        Object object = map.get(key);
        if (object instanceof Boolean) {
            return (Boolean) object;
        }
        throw new ExperimentParserTypeError(experimentFiles.getExperimentId(), currentResourceName, key, Boolean.class, object);
    }

    private String expectString(EconomicMap<String, Object> map, String key, boolean nullable) throws ExperimentParserTypeError {
        Object object = map.get(key);
        if ((nullable && object == null) || object instanceof String) {
            return (String) object;
        }
        throw new ExperimentParserTypeError(experimentFiles.getExperimentId(), currentResourceName, key, String.class, object);
    }

    private long expectLong(EconomicMap<String, Object> map, String key) throws ExperimentParserTypeError {
        Object object = map.get(key);
        if (object instanceof Number) {
            return ((Number) object).longValue();
        }
        throw new ExperimentParserTypeError(experimentFiles.getExperimentId(), currentResourceName, key, Long.class, object);
    }

    private int expectInteger(EconomicMap<String, Object> map, String key) throws ExperimentParserTypeError {
        Object object = map.get(key);
        if (object instanceof Integer) {
            return (Integer) object;
        }
        throw new ExperimentParserTypeError(experimentFiles.getExperimentId(), currentResourceName, key, Integer.class, object);
    }

    private Integer expectIntegerNullable(EconomicMap<String, Object> map, String key) throws ExperimentParserTypeError {
        if (map.get(key) == null) {
            return null;
        }
        return expectInteger(map, key);
    }

    @SuppressWarnings("unchecked")
    private EconomicMap<String, Object> expectMap(Object object, String objectName, boolean nullable) throws ExperimentParserTypeError {
        if ((nullable && object == null) || object instanceof EconomicMap) {
            return (EconomicMap<String, Object>) object;
        }
        throw new ExperimentParserTypeError(experimentFiles.getExperimentId(), currentResourceName, objectName, EconomicMap.class, object);
    }

    @SuppressWarnings("unchecked")
    private List<Object> expectList(EconomicMap<String, Object> map, String key, boolean nullable) throws ExperimentParserTypeError {
        Object object = map.get(key);
        if ((nullable && object == null) || object instanceof List) {
            return (List<Object>) object;
        }
        throw new ExperimentParserTypeError(experimentFiles.getExperimentId(), currentResourceName, key, List.class, object);
    }

    /**
     * Parses an experiment by reading the provided logs. If anything fails, it prints the error to
     * stderr and exits.
     *
     * @param experimentId the ID of the parsed experiment
     * @param compilationKind the compilation kind of this experiment
     * @param proftoolPath the file path to the JSON proftool output (mx profjson), can be
     *            {@code null} if the experiment does not have any associated proftool output
     * @param optimizationLogPath the path to the directory containing optimization logs
     * @return an experiment parsed from the provided files
     */
    public static Experiment parseOrExit(ExperimentId experimentId, Experiment.CompilationKind compilationKind, String proftoolPath, String optimizationLogPath) {
        ExperimentFiles files = new ExperimentFilesImpl(experimentId, compilationKind, proftoolPath, optimizationLogPath);
        ExperimentParser parser = new ExperimentParser(files);
        try {
            return parser.parse();
        } catch (IOException e) {
            System.err.println("Could not read the files of the experiment " + experimentId + ": " + e.getMessage());
            System.exit(1);
        } catch (ExperimentParserError e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        return null;
    }
}
