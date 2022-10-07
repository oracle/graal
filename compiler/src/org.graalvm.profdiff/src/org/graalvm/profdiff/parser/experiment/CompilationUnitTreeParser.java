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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.core.optimization.OptimizationTree;

/**
 * Parses {@link CompilationUnit.TreePair the trees of a compilation unit} from its source file.
 */
public class CompilationUnitTreeParser implements CompilationUnit.TreeLoader {
    /**
     * The file containing the serialized compilation unit, and possibly several other compilation
     * units separated by {@code '\n'}.
     */
    private final File file;

    /**
     * The byte index where this compilation unit starts in its source {@link #file}.
     */
    private final long offset;

    /**
     * The experiment ID to which this compilation unit belongs.
     */
    private final ExperimentId experimentId;

    public CompilationUnitTreeParser(ExperimentId experimentId, File file, long offset) {
        this.file = file;
        this.experimentId = experimentId;
        this.offset = offset;
    }

    @Override
    public CompilationUnit.TreePair load() throws ExperimentParserError {
        try (FileInputStream inputStream = new FileInputStream(file);
                        FileChannel fileChannel = inputStream.getChannel();
                        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                        BufferedReader reader = new BufferedReader(inputStreamReader)) {
            fileChannel.position(offset);
            String line = reader.readLine();
            ExperimentJSONParser parser = new ExperimentJSONParser(experimentId, file, line);
            ExperimentJSONParser.JSONMap map = parser.parse().asMap();
            ExperimentJSONParser.JSONLiteral inliningTreeNode = map.property("inliningTreeRoot");
            InliningTree inliningTree = new InliningTree(inliningTreeNode.isNull() ? null : parseInliningTreeNode(inliningTreeNode.asMap()));
            OptimizationTree optimizationTree = new OptimizationTree(parseOptimizationPhase(map.property("rootPhase").asMap()));
            return new CompilationUnit.TreePair(optimizationTree, inliningTree);
        } catch (IOException e) {
            throw new ExperimentParserError(experimentId, "compilation unit", e.getMessage());
        }
    }

    private static InliningTreeNode parseInliningTreeNode(ExperimentJSONParser.JSONMap map) throws ExperimentParserTypeError {
        String methodName = map.property("targetMethodName").asNullableString();
        int bci = map.property("bci").asInt();
        boolean positive = map.property("positive").asBoolean();
        List<String> reason = new ArrayList<>();
        ExperimentJSONParser.JSONLiteral reasonObject = map.property("reason");
        if (!reasonObject.isNull()) {
            for (ExperimentJSONParser.JSONLiteral reasonItem : reasonObject.asList()) {
                reason.add(reasonItem.asString());
            }
        }
        InliningTreeNode inliningTreeNode = new InliningTreeNode(methodName, bci, positive, reason);
        ExperimentJSONParser.JSONLiteral inlinees = map.property("inlinees");
        if (inlinees.isNull()) {
            return inliningTreeNode;
        }
        for (ExperimentJSONParser.JSONLiteral inlinee : inlinees.asList()) {
            inliningTreeNode.addChild(parseInliningTreeNode(inlinee.asMap()));
        }
        return inliningTreeNode;
    }

    private OptimizationPhase parseOptimizationPhase(ExperimentJSONParser.JSONMap map) throws ExperimentParserTypeError {
        String phaseName = map.property("phaseName").asString();
        OptimizationPhase optimizationPhase = new OptimizationPhase(phaseName);
        ExperimentJSONParser.JSONLiteral optimizations = map.property("optimizations");
        if (optimizations.isNull()) {
            return optimizationPhase;
        }
        for (ExperimentJSONParser.JSONLiteral child : optimizations.asList()) {
            ExperimentJSONParser.JSONMap childMap = child.asMap();
            ExperimentJSONParser.JSONLiteral subphaseName = childMap.property("phaseName");
            if (subphaseName.isNull()) {
                optimizationPhase.addChild(parseOptimization(childMap));
            } else {
                optimizationPhase.addChild(parseOptimizationPhase(childMap));
            }
        }
        return optimizationPhase;
    }

    private Optimization parseOptimization(ExperimentJSONParser.JSONMap optimization) throws ExperimentParserTypeError {
        String optimizationName = optimization.property("optimizationName").asString();
        String eventName = optimization.property("eventName").asString();
        ExperimentJSONParser.JSONLiteral positionObject = optimization.property("position");
        EconomicMap<String, Integer> position = null;
        if (!positionObject.isNull()) {
            MapCursor<String, Object> cursor = positionObject.asMap().getInnerMap().getEntries();
            position = EconomicMap.create();
            while (cursor.advance()) {
                if (!(cursor.getValue() instanceof Integer)) {
                    throw new ExperimentParserTypeError(experimentId, file.getPath(), "bci", Integer.class, cursor.getValue());
                }
                position.put(cursor.getKey(), (Integer) cursor.getValue());
            }
        }
        EconomicMap<String, Object> properties = optimization.getInnerMap();
        properties.removeKey("optimizationName");
        properties.removeKey("eventName");
        properties.removeKey("position");
        return new Optimization(optimizationName, eventName, position, properties.isEmpty() ? null : properties);
    }
}
