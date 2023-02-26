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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.nodes.OptimizationLogImpl;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.ProftoolMethod;
import org.graalvm.profdiff.util.Writer;

/**
 * Parses an experiment from its files.
 */
public class ExperimentParser {
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
    private static class ProftoolLog {
        /**
         * The execution ID of the experiment.
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
     * A regex capturing the method name and the compilation ID of a serialized compilation unit.
     */
    private static final String COMPILATION_UNIT_REGEX = String.format("\\{\"%s\": \"([^\"]*)\", \"%s\": \"([^\"]*)\"",
                    OptimizationLogImpl.METHOD_NAME_PROPERTY, OptimizationLogImpl.COMPILATION_ID_PROPERTY);

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
    private static final int GRAAL_COMPILER_LEVEL = 4;

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
        EconomicMap<String, PartialCompilationUnit> partialCompilationUnits = parsePartialCompilationUnits();
        Experiment experiment;
        Optional<FileView> proftoolLogFile = experimentFiles.getProftoolOutput();
        if (proftoolLogFile.isPresent()) {
            ProftoolLog proftoolLog = parseProftoolLog(proftoolLogFile.get());
            for (ProftoolMethod method : proftoolLog.methods) {
                if (method.getLevel() == null || method.getLevel() != GRAAL_COMPILER_LEVEL || method.getCompilationId() == null) {
                    continue;
                }
                PartialCompilationUnit unit = partialCompilationUnits.get(method.getCompilationId());
                if (unit == null) {
                    warningWriter.writeln("Warning: Compilation ID " + method.getCompilationId() + " not found in the optimization log");
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
            CompilationUnitTreeParser treeParser = new CompilationUnitTreeParser(experimentFiles.getExperimentId(), unit.fileView);
            experiment.addCompilationUnit(unit.methodName, unit.compilationId, unit.period, treeParser);
        }
        return experiment;
    }

    /**
     * Parses partial compilation units from the optimization logs provided by experiment files.
     *
     * @return the parsed partial compilation units
     * @throws IOException failed to read the optimization logs
     */
    private EconomicMap<String, PartialCompilationUnit> parsePartialCompilationUnits() throws IOException {
        EconomicMap<String, PartialCompilationUnit> partialCompilationUnits = EconomicMap.create();
        Pattern compilationUnitPattern = Pattern.compile(COMPILATION_UNIT_REGEX);
        for (FileView fileView : experimentFiles.getOptimizationLogs()) {
            final long[] lineNumber = {0};
            fileView.forEachLine((line, lineView) -> {
                ++lineNumber[0];
                // assume that the beginning of the JSON has the exact format as described by
                // the regex
                Matcher matcher = compilationUnitPattern.matcher(line);
                if (matcher.find()) {
                    PartialCompilationUnit compilationUnit = new PartialCompilationUnit();
                    compilationUnit.fileView = lineView;
                    compilationUnit.methodName = matcher.group(1);
                    compilationUnit.compilationId = matcher.group(2);
                    partialCompilationUnits.put(compilationUnit.compilationId, compilationUnit);
                } else {
                    warningWriter.writeln("Warning: Invalid compilation unit in file " + fileView.getSymbolicPath() + " on line " + lineNumber[0]);
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
        proftoolLog.executionId = map.property("executionId").asString();
        proftoolLog.totalPeriod = map.property("totalPeriod").asLong();
        for (ExperimentJSONParser.JSONLiteral codeObject : map.property("code").asList()) {
            ExperimentJSONParser.JSONMap code = codeObject.asMap();
            String compilationId = code.property("compileId").asNullableString();
            if (compilationId != null && compilationId.endsWith("%")) {
                compilationId = compilationId.substring(0, compilationId.length() - 1);
            }
            String name = code.property("name").asString();
            int colonIndex = name.indexOf(':');
            if (colonIndex != -1 && colonIndex + 2 <= name.length()) {
                name = name.substring(colonIndex + 2);
            }
            long period = code.property("period").asLong();
            Integer level = code.property("level").asNullableInteger();
            proftoolLog.methods.add(new ProftoolMethod(compilationId, name, level, period));
        }
        return proftoolLog;
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
     * @param warningWriter a writer for warning messages
     * @return an experiment parsed from the provided files
     */
    public static Experiment parseOrExit(ExperimentId experimentId, Experiment.CompilationKind compilationKind,
                    String proftoolPath, String optimizationLogPath, Writer warningWriter) {
        ExperimentFiles files = new ExperimentFilesImpl(experimentId, compilationKind, proftoolPath, optimizationLogPath);
        ExperimentParser parser = new ExperimentParser(files, warningWriter);
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
