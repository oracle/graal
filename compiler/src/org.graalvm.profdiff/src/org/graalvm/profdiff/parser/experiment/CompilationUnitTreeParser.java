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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.nodes.OptimizationLogImpl;
import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.core.optimization.OptimizationTree;

/**
 * Parses the trees of a compilation unit from its source file.
 */
public class CompilationUnitTreeParser implements CompilationUnit.TreeLoader {

    /**
     * The experiment ID to which this compilation unit belongs.
     */
    private final ExperimentId experimentId;

    /**
     * The file view containing the serialized compilation unit.
     */
    private final FileView fileView;

    public CompilationUnitTreeParser(ExperimentId experimentId, FileView fileView) {
        this.experimentId = experimentId;
        this.fileView = fileView;
    }

    @Override
    public CompilationUnit.TreePair load() throws ExperimentParserError {
        try {
            ExperimentJSONParser parser = new ExperimentJSONParser(experimentId, fileView);
            ExperimentJSONParser.JSONMap map = parser.parse().asMap();
            ExperimentJSONParser.JSONLiteral inliningTreeNode = map.property(OptimizationLogImpl.INLINING_TREE_PROPERTY);
            InliningTree inliningTree = new InliningTree(inliningTreeNode.isNull() ? null : parseInliningTreeNode(inliningTreeNode.asMap()));
            OptimizationTree optimizationTree = new OptimizationTree(parseOptimizationPhase(map.property(OptimizationLogImpl.OPTIMIZATION_TREE_PROPERTY).asMap()));
            return new CompilationUnit.TreePair(optimizationTree, inliningTree);
        } catch (IOException e) {
            throw new ExperimentParserError(experimentId, "compilation unit", e.getMessage());
        }
    }

    private static InliningTreeNode parseInliningTreeNode(ExperimentJSONParser.JSONMap map) throws ExperimentParserTypeError {
        String methodName = map.property(OptimizationLogImpl.METHOD_NAME_PROPERTY).asNullableString();
        int bci = map.property(OptimizationLogImpl.CALLSITE_BCI_PROPERTY).asInt();
        boolean positive = map.property(OptimizationLogImpl.INLINED_PROPERTY).asBoolean();
        List<String> reason = new ArrayList<>();
        ExperimentJSONParser.JSONLiteral reasonObject = map.property(OptimizationLogImpl.REASON_PROPERTY);
        if (!reasonObject.isNull()) {
            for (ExperimentJSONParser.JSONLiteral reasonItem : reasonObject.asList()) {
                reason.add(reasonItem.asString());
            }
        }
        InliningTreeNode inliningTreeNode = new InliningTreeNode(methodName, bci, positive, reason);
        ExperimentJSONParser.JSONLiteral invokes = map.property(OptimizationLogImpl.INVOKES_PROPERTY);
        if (invokes.isNull()) {
            return inliningTreeNode;
        }
        for (ExperimentJSONParser.JSONLiteral invoke : invokes.asList()) {
            inliningTreeNode.addChild(parseInliningTreeNode(invoke.asMap()));
        }
        return inliningTreeNode;
    }

    private OptimizationPhase parseOptimizationPhase(ExperimentJSONParser.JSONMap map) throws ExperimentParserTypeError {
        String phaseName = map.property(OptimizationLogImpl.PHASE_NAME_PROPERTY).asString();
        OptimizationPhase optimizationPhase = new OptimizationPhase(phaseName);
        ExperimentJSONParser.JSONLiteral optimizations = map.property(OptimizationLogImpl.OPTIMIZATIONS_PROPERTY);
        if (optimizations.isNull()) {
            return optimizationPhase;
        }
        for (ExperimentJSONParser.JSONLiteral child : optimizations.asList()) {
            ExperimentJSONParser.JSONMap childMap = child.asMap();
            ExperimentJSONParser.JSONLiteral subphaseName = childMap.property(OptimizationLogImpl.PHASE_NAME_PROPERTY);
            if (subphaseName.isNull()) {
                optimizationPhase.addChild(parseOptimization(childMap));
            } else {
                optimizationPhase.addChild(parseOptimizationPhase(childMap));
            }
        }
        return optimizationPhase;
    }

    private Optimization parseOptimization(ExperimentJSONParser.JSONMap optimization) throws ExperimentParserTypeError {
        String optimizationName = optimization.property(OptimizationLogImpl.OPTIMIZATION_NAME_PROPERTY).asString();
        String eventName = optimization.property(OptimizationLogImpl.EVENT_NAME_PROPERTY).asString();
        ExperimentJSONParser.JSONLiteral positionObject = optimization.property(OptimizationLogImpl.POSITION_PROPERTY);
        EconomicMap<String, Integer> position = null;
        if (!positionObject.isNull()) {
            MapCursor<String, Object> cursor = positionObject.asMap().getInnerMap().getEntries();
            position = EconomicMap.create();
            while (cursor.advance()) {
                if (!(cursor.getValue() instanceof Integer)) {
                    throw new ExperimentParserTypeError(experimentId, fileView.getSymbolicPath(), OptimizationLogImpl.POSITION_PROPERTY, Integer.class, cursor.getValue());
                }
                position.put(cursor.getKey(), (Integer) cursor.getValue());
            }
        }
        EconomicMap<String, Object> properties = optimization.getInnerMap();
        properties.removeKey(OptimizationLogImpl.OPTIMIZATION_NAME_PROPERTY);
        properties.removeKey(OptimizationLogImpl.EVENT_NAME_PROPERTY);
        properties.removeKey(OptimizationLogImpl.POSITION_PROPERTY);
        return new Optimization(optimizationName, eventName, position, properties.isEmpty() ? null : properties);
    }
}
