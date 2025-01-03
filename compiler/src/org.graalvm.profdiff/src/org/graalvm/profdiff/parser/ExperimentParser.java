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
package org.graalvm.profdiff.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.ProftoolMethod;
import org.graalvm.profdiff.core.Writer;

import jdk.graal.compiler.nodes.OptimizationLogImpl;

/**
 * Parses an experiment from its files.
 */
public class ExperimentParser {

    /**
     * The compilation kind property in a proftool profile. Possible values are
     * {@link #COMPILATION_AOT} or {@link #COMPILATION_JIT}. {@code null} is interpreted as
     * {@link #COMPILATION_JIT} for backward compatibility.
     */
    public static final String COMPILATION_KIND = "compilationKind";

    /**
     * The literal marking an AOT compilation in a proftool profile.
     */
    public static final String COMPILATION_AOT = "AOT";

    /**
     * The literal marking a JIT compilation in a proftool profile.
     */
    public static final String COMPILATION_JIT = "JIT";

    /**
     * The execution ID property in a proftool profile. The execution ID is available only for JIT
     * experiments.
     */
    public static final String EXECUTION_ID = "executionId";

    /**
     * The total period property in a proftool profile. The value is the sum of all periods recorded
     * by the profiler.
     */
    public static final String TOTAL_PERIOD = "totalPeriod";

    /**
     * The code property in a proftool profile. The property holds the list of all nmethods recorded
     * by the profiler.
     */
    public static final String CODE = "code";

    /**
     * The compile ID property in a proftool profile. Compile IDs are available only in JIT
     * profiles.
     */
    public static final String COMPILE_ID = "compileId";

    /**
     * The name property in a proftool profile. The name contains the method name of the recorded
     * nmethod.
     */
    public static final String NAME = "name";

    /**
     * The period property in a proftool profile. This is the total period recorded for a single
     * nmethod.
     */
    public static final String PERIOD = "period";

    /**
     * The level property in a proftool profile.
     */
    public static final String LEVEL = "level";

    /**
     * Marks an OSR compilation in a proftool profile.
     */
    public static final String OSR_MARKER = "%";

    /**
     * Separates method names from compiled IDs in proftool profiles.
     */
    public static final String NAME_SEPARATOR = ": ";

    /**
     * A partially parsed {@link org.graalvm.profdiff.core.CompilationUnit}.
     */
    private static final class PartialCompilationUnit {
        /**
         * The compilation ID of this compilation unit.
         */
        String compilationId;

        /**
         * The name of the root-compiled method in this compilation unit.
         */
        String methodName;

        /**
         * The number of cycles spent executing this method measured by proftool.
         */
        long period;

        /**
         * The view of this compilation unit in the file from which it was read.
         */
        FileView fileView;
    }

    /**
     * A parsed proftool log.
     */
    private static final class ProftoolLog {
        /**
         * The compilation kind of the parsed experiment (JIT/AOT).
         */
        Experiment.CompilationKind compilationKind;

        /**
         * The execution ID of the experiment. {@code null} if unknown.
         */
        String executionId;

        /**
         * The total measured number of cycles spent executing the experiment.
         */
        long totalPeriod;

        /**
         * A list of sampled methods.
         */
        List<ProftoolMethod> methods = new ArrayList<>();
    }

    /**
     * The experiment files to be parsed.
     */
    private final ExperimentFiles experimentFiles;

    /**
     * A writer for warning messages.
     */
    private final Writer warningWriter;

    /**
     * The compiler level of graal-compiled methods in the output of proftool.
     */
    public static final int GRAAL_COMPILER_LEVEL = 4;

    public ExperimentParser(ExperimentFiles experimentFiles, Writer warningWriter) {
        this.experimentFiles = experimentFiles;
        this.warningWriter = warningWriter;
    }

    /**
     * Parses the experiment by combining proftool output and an optimization log. The optimization
     * log is read first and all compiled methods are parsed. The proftool output is then used to
     * add information about execution periods. Method compilations from the optimization log are
     * matched with the proftool output according to compilation IDs. Warning messages are printed
     * using the provided writer.
     *
     * @return the parsed experiment
     * @throws IOException failed to read the experiment files
     * @throws ExperimentParserTypeError the experiment files had an in incorrect format
     */
    public Experiment parse() throws IOException, ExperimentParserError {
        List<PartialCompilationUnit> partialCompilationUnits = parsePartialCompilationUnits();
        Experiment experiment;
        Optional<FileView> proftoolLogFile = experimentFiles.getProftoolOutput();
        if (proftoolLogFile.isPresent()) {
            FileView logFileView = proftoolLogFile.get();
            ProftoolLog proftoolLog = parseProftoolLog(logFileView);
            if (experimentFiles.getCompilationKind() != null && proftoolLog.compilationKind != experimentFiles.getCompilationKind()) {
                throw new ExperimentParserError(experimentFiles.getExperimentId(), logFileView.getSymbolicPath(),
                                "mismatched experiment kind: expected " + experimentFiles.getCompilationKind() + ", got" + proftoolLog.compilationKind);
            }
            switch (proftoolLog.compilationKind) {
                case JIT -> linkJITProfilesToCompilationUnits(partialCompilationUnits, proftoolLog);
                case AOT -> linkAOTProfilesToCompilationUnits(partialCompilationUnits, proftoolLog);
            }
            experiment = new Experiment(
                            proftoolLog.executionId,
                            experimentFiles.getExperimentId(),
                            proftoolLog.compilationKind,
                            proftoolLog.totalPeriod,
                            proftoolLog.methods);
        } else {
            experiment = new Experiment(experimentFiles.getExperimentId(), experimentFiles.getCompilationKind());
        }
        for (PartialCompilationUnit unit : partialCompilationUnits) {
            CompilationUnitTreeParser treeParser = new CompilationUnitTreeParser(experimentFiles.getExperimentId(), unit.fileView);
            experiment.addCompilationUnit(unit.methodName, unit.compilationId, unit.period, treeParser);
        }
        return experiment;
    }

    /**
     * Assigns the sampled execution periods from a JIT profile to partial compilation units.
     * Compilation units are linked using compilation IDs.
     *
     * @param partialCompilationUnits partial compilation units
     * @param proftoolLog the JIT profile
     */
    private void linkJITProfilesToCompilationUnits(List<PartialCompilationUnit> partialCompilationUnits, ProftoolLog proftoolLog) {
        EconomicMap<String, PartialCompilationUnit> units = EconomicMap.create();
        for (PartialCompilationUnit unit : partialCompilationUnits) {
            units.put(unit.compilationId, unit);
        }
        for (ProftoolMethod method : proftoolLog.methods) {
            if (method.getLevel() == null || method.getLevel() != GRAAL_COMPILER_LEVEL || method.getCompilationId() == null) {
                continue;
            }
            PartialCompilationUnit unit = units.get(method.getCompilationId());
            if (unit == null) {
                warningWriter.writeln("Warning: Compilation ID " + method.getCompilationId() + " not found in the optimization log");
            } else {
                unit.period = method.getPeriod();
            }
        }
    }

    /**
     * Assigns the sampled execution periods from an AOT profile to partial compilation units.
     * Compilation units are linked using their names. This is because compilation IDs are
     * unavailable in AOT profiles. If there are more compilation units with equal names, the
     * sampled periods are linked to all of them. These names are expected to be stable across
     * experiments.
     *
     * @param partialCompilationUnits partial compilation units
     * @param proftoolLog the AOT profile
     */
    private static void linkAOTProfilesToCompilationUnits(List<PartialCompilationUnit> partialCompilationUnits, ProftoolLog proftoolLog) {
        EconomicMap<String, List<PartialCompilationUnit>> units = EconomicMap.create();
        for (PartialCompilationUnit unit : partialCompilationUnits) {
            List<PartialCompilationUnit> list = units.get(unit.methodName);
            if (list == null) {
                list = new ArrayList<>();
                units.put(unit.methodName, list);
            }
            list.add(unit);
        }
        for (ProftoolMethod method : proftoolLog.methods) {
            if (method.getName() == null) {
                continue;
            }
            List<PartialCompilationUnit> list = units.get(method.getName());
            if (list != null) {
                for (PartialCompilationUnit unit : list) {
                    unit.period = method.getPeriod();
                }
            }
        }
    }

    /**
     * Parses partial compilation units from the optimization logs provided by experiment files.
     *
     * @return the parsed partial compilation units
     * @throws IOException failed to read the optimization logs
     */
    private List<PartialCompilationUnit> parsePartialCompilationUnits() throws IOException {
        List<String> allowedKeys = List.of(OptimizationLogImpl.METHOD_NAME_PROPERTY, OptimizationLogImpl.COMPILATION_ID_PROPERTY);
        List<PartialCompilationUnit> partialCompilationUnits = new ArrayList<>();
        for (FileView fileView : experimentFiles.getOptimizationLogs()) {
            fileView.forEachLine((line, lineView) -> {
                try {
                    var map = new ExperimentJSONParser(experimentFiles.getExperimentId(), lineView).parseAllowedKeys(allowedKeys, line);
                    PartialCompilationUnit compilationUnit = new PartialCompilationUnit();
                    compilationUnit.fileView = lineView;
                    compilationUnit.methodName = map.property(OptimizationLogImpl.METHOD_NAME_PROPERTY).asString();
                    compilationUnit.compilationId = map.property(OptimizationLogImpl.COMPILATION_ID_PROPERTY).asString();
                    partialCompilationUnits.add(compilationUnit);
                } catch (ExperimentParserError | IOException e) {
                    warningWriter.writeln("Warning: Invalid compilation unit: " + e.getMessage());
                }
            });
        }
        return partialCompilationUnits;
    }

    /**
     * Parses proftool logs from a file view.
     *
     * @param fileView a view of a JSON file with the proftool log
     * @return the parsed proftool logs
     * @throws IOException failed to read the file
     * @throws ExperimentParserError failed to parse the file
     */
    private ProftoolLog parseProftoolLog(FileView fileView) throws IOException, ExperimentParserError {
        ExperimentJSONParser parser = new ExperimentJSONParser(experimentFiles.getExperimentId(), fileView);
        ProftoolLog proftoolLog = new ProftoolLog();
        ExperimentJSONParser.JSONMap map = parser.parse().asMap();
        String compilationKind = map.property(COMPILATION_KIND).asNullableString();
        if (COMPILATION_AOT.equals(compilationKind)) {
            proftoolLog.compilationKind = Experiment.CompilationKind.AOT;
        } else if (compilationKind == null || COMPILATION_JIT.equals(compilationKind)) {
            proftoolLog.compilationKind = Experiment.CompilationKind.JIT;
        } else {
            throw new ExperimentParserError(experimentFiles.getExperimentId(), fileView.getSymbolicPath(), "unexpected compilation kind: " + compilationKind);
        }
        proftoolLog.executionId = map.property(EXECUTION_ID).asNullableString();
        proftoolLog.totalPeriod = map.property(TOTAL_PERIOD).asLong();
        for (ExperimentJSONParser.JSONLiteral codeObject : map.property(CODE).asList()) {
            ExperimentJSONParser.JSONMap code = codeObject.asMap();
            String compilationId = code.property(COMPILE_ID).asNullableString();
            if (compilationId != null && compilationId.endsWith(OSR_MARKER)) {
                compilationId = compilationId.substring(0, compilationId.length() - 1);
            }
            String name = code.property(NAME).asString();
            int colonIndex = name.indexOf(NAME_SEPARATOR);
            if (colonIndex != -1) {
                name = name.substring(colonIndex + NAME_SEPARATOR.length());
            }
            long period = code.property(PERIOD).asLong();
            Integer level = code.property(LEVEL).asNullableInteger();
            proftoolLog.methods.add(new ProftoolMethod(compilationId, name, level, period));
        }
        return proftoolLog;
    }

    /**
     * Parses an experiment by reading the provided logs. If anything fails, it throws an unchecked
     * exception.
     *
     * @param experimentId the ID of the parsed experiment
     * @param compilationKind the compilation kind of this experiment
     * @param proftoolPath the file path to the JSON proftool output (mx profjson), can be
     *            {@code null} if the experiment does not have any associated proftool output
     * @param optimizationLogPath the path to the directory containing optimization logs
     * @param warningWriter a writer for warning messages
     * @return an experiment parsed from the provided files
     */
    public static Experiment parseOrPanic(ExperimentId experimentId, Experiment.CompilationKind compilationKind,
                    String proftoolPath, String optimizationLogPath, Writer warningWriter) {
        ExperimentFiles files = new ExperimentFilesImpl(experimentId, compilationKind, proftoolPath, optimizationLogPath);
        ExperimentParser parser = new ExperimentParser(files, warningWriter);
        try {
            return parser.parse();
        } catch (IOException e) {
            throw new RuntimeException("Could not read the files of the experiment " + experimentId + ": " + e.getMessage());
        } catch (ExperimentParserError e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
