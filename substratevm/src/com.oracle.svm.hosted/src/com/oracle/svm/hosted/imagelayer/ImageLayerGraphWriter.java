/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import static com.oracle.svm.hosted.imagelayer.SnapshotWriters.initInts;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.hosted.snapshot.elements.PersistedAnalysisMethodData;
import com.oracle.svm.hosted.snapshot.layer.SharedLayerSnapshotData;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.NodeClassMap;
import jdk.graal.compiler.util.ObjectCopier;

final class ImageLayerGraphWriter {
    private final SVMImageLayerSnapshotUtil imageLayerSnapshotUtil;
    private final ImageLayerGraphStore graphStore;
    private final boolean useSharedLayerGraphs;
    private final boolean useSharedLayerStrengthenedGraphs;
    private final Map<AnalysisMethod, MethodGraphsInfo> methodsMap = new ConcurrentHashMap<>();

    /**
     * Used to encode {@link NodeClass} ids in {@link #persistGraph}.
     */
    private final NodeClassMap nodeClassMap = GraphEncoder.GLOBAL_NODE_CLASS_MAP;

    private record MethodGraphsInfo(String analysisGraphLocation, boolean analysisGraphIsIntrinsic,
                    List<AnalysisField> closedFieldWrites, String strengthenedGraphLocation) {

        static final MethodGraphsInfo NO_GRAPHS = new MethodGraphsInfo(null, false, List.of(), null);

        MethodGraphsInfo withAnalysisGraph(AnalysisMethod method, String location, boolean isIntrinsic, List<AnalysisField> newClosedFieldWrites) {
            assert analysisGraphLocation == null && !analysisGraphIsIntrinsic : "Only one analysis graph can be persisted for a given method: " + method;
            return new MethodGraphsInfo(location, isIntrinsic, newClosedFieldWrites, strengthenedGraphLocation);
        }

        MethodGraphsInfo withStrengthenedGraph(AnalysisMethod method, String location) {
            assert strengthenedGraphLocation == null : "Only one strengthened graph can be persisted for a given method: " + method;
            return new MethodGraphsInfo(analysisGraphLocation, analysisGraphIsIntrinsic, closedFieldWrites, location);
        }
    }

    ImageLayerGraphWriter(SVMImageLayerSnapshotUtil imageLayerSnapshotUtil, ImageLayerGraphStore graphStore, boolean useSharedLayerGraphs, boolean useSharedLayerStrengthenedGraphs) {
        this.imageLayerSnapshotUtil = imageLayerSnapshotUtil;
        this.graphStore = graphStore;
        this.useSharedLayerGraphs = useSharedLayerGraphs;
        this.useSharedLayerStrengthenedGraphs = useSharedLayerStrengthenedGraphs;
    }

    void writeNodeClassMap(SharedLayerSnapshotData.Writer snapshotWriter) {
        SVMImageLayerSnapshotUtil.SVMGraphEncoder graphEncoder = imageLayerSnapshotUtil.getGraphEncoder(null);
        byte[] encodedNodeClassMap = ObjectCopier.encode(graphEncoder, nodeClassMap);
        String location = graphStore.write(encodedNodeClassMap);
        snapshotWriter.setNodeClassMapLocation(location);
        graphStore.close();
    }

    void writeMethodGraphs(AnalysisMethod method, PersistedAnalysisMethodData.Writer builder) {
        MethodGraphsInfo graphsInfo = methodsMap.putIfAbsent(method, MethodGraphsInfo.NO_GRAPHS);
        if (graphsInfo != null && graphsInfo.analysisGraphLocation != null) {
            assert !method.isDelayed() : "The method " + method + " has an analysis graph, but is delayed to the application layer";
            builder.setAnalysisGraphLocation(graphsInfo.analysisGraphLocation);
            builder.setAnalysisGraphIsIntrinsic(graphsInfo.analysisGraphIsIntrinsic);
            initInts(builder::initClosedFieldWrites, graphsInfo.closedFieldWrites.stream().mapToInt(AnalysisField::getId).sorted());
        }
        if (graphsInfo != null && graphsInfo.strengthenedGraphLocation != null) {
            assert !method.isDelayed() : "The method " + method + " has a strengthened graph, but is delayed to the application layer";
            builder.setStrengthenedGraphLocation(graphsInfo.strengthenedGraphLocation);
        }
    }

    void persistAnalysisParsedGraph(AnalysisMethod method, AnalysisParsedGraph analysisParsedGraph) {
        List<AnalysisField> closedFieldWrites = analysisParsedGraph.getMetadata().closedFieldWrites();
        String location = persistGraph(method, analysisParsedGraph.getEncodedGraph());
        if (location != null) {
            /*
             * A store can disappear from the encoded graph after closed-field writes are collected,
             * so its field may no longer be referenced by the graph. Track it before the universe is sealed.
             */
            for (AnalysisField field : closedFieldWrites) {
                field.registerAsTrackedAcrossLayers("closed-field write in persisted graph of " + method);
            }
            /*
             * This method should only be called once for each method. This check is performed by
             * withAnalysisGraph as it will throw if the MethodGraphsInfo already has an analysis
             * graph.
             */
            methodsMap.compute(method, (_, methodGraphsInfo) -> (methodGraphsInfo != null ? methodGraphsInfo : MethodGraphsInfo.NO_GRAPHS)
                            .withAnalysisGraph(method, location, analysisParsedGraph.isIntrinsic(), closedFieldWrites));
        }
    }

    void persistMethodStrengthenedGraph(AnalysisMethod method) {
        if (!useSharedLayerStrengthenedGraphs) {
            return;
        }
        EncodedGraph analyzedGraph = method.getAnalyzedGraph();
        String location = persistGraph(method, analyzedGraph);
        /*
         * This method can be called twice for the same method. However the check is performed by
         * withStrengthenedGraph as it will throw if the MethodGraphsInfo already has a strengthened
         * graph.
         */
        methodsMap.compute(method, (_, methodGraphsInfo) -> (methodGraphsInfo != null ? methodGraphsInfo : MethodGraphsInfo.NO_GRAPHS).withStrengthenedGraph(method, location));
    }

    private String persistGraph(AnalysisMethod method, EncodedGraph analyzedGraph) {
        if (!useSharedLayerGraphs) {
            return null;
        }
        if (analyzedGraph == null) {
            return null;
        }
        if (Arrays.stream(analyzedGraph.getObjects()).anyMatch(o -> o instanceof AnalysisFuture<?>)) {
            /*
             * GR-61103: After the AnalysisFuture in this node is handled, this check can be
             * removed.
             */
            return null;
        }
        byte[] encodedGraph = ObjectCopier.encode(imageLayerSnapshotUtil.getGraphEncoder(nodeClassMap), analyzedGraph);
        if (contains(encodedGraph, LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING.getBytes(StandardCharsets.UTF_8))) {
            throw AnalysisError.shouldNotReachHere("The graph for the method %s contains a reference to a lambda type, which cannot be decoded: %s".formatted(method, encodedGraph));
        }
        return graphStore.write(encodedGraph);
    }

    private static boolean contains(byte[] data, byte[] seq) {
        outer: for (int i = 0; i <= data.length - seq.length; i++) {
            for (int j = 0; j < seq.length; j++) {
                if (data[i + j] != seq[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
