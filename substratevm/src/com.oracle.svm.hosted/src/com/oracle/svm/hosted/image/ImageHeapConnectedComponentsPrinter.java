/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ByteFormattingUtil;
import com.oracle.svm.hosted.image.NativeImageHeap.HeapInclusionReason;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectReachabilityGroup;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectReachabilityInfo;
import com.oracle.svm.hosted.meta.HostedField;

import jdk.graal.compiler.util.json.JsonWriter;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class ImageHeapConnectedComponentsPrinter {
    private final NativeImageHeap heap;
    private final long totalHeapSizeInBytes;
    private final List<ConnectedComponent> connectedComponents;
    private final BigBang bb;
    private final String imageName;
    private final EnumMap<ObjectReachabilityGroup, GroupEntry> groups;

    private static class GroupEntry {
        final Set<ObjectInfo> objects;
        long sizeInBytes;

        GroupEntry() {
            this.objects = Collections.newSetFromMap(new IdentityHashMap<>());
        }

        void addObject(ObjectInfo object) {
            objects.add(object);
            this.sizeInBytes += object.getSize();
        }
    }

    public ImageHeapConnectedComponentsPrinter(NativeImageHeap heap, BigBang bigBang, AbstractImage image, String imageName) {
        this.heap = heap;
        this.imageName = imageName;
        this.totalHeapSizeInBytes = image.getImageHeapSize();
        this.bb = bigBang;
        this.groups = groupObjectsByReachability(heap);

        ObjectInfoGraph objectInfoGraph = constructGraph(groups.get(ObjectReachabilityGroup.MethodOrStaticField).objects);
        this.connectedComponents = computeConnectedComponents(objectInfoGraph);
    }

    private static boolean shouldIncludeObjectInTheReport(ObjectInfo objectInfo) {
        return !objectInfo.getMainReason().equals(HeapInclusionReason.FillerObject);
    }

    /**
     * Objects can be reachable from multiple object reachability groups. When generating a report
     * we `claim` objects in the order specified below. Object can only appear (i.e. be claimed) by
     * exactly one group. We first remove Resources, InternedStringsTable, DynamicHubs and
     * ImageCodeInfo, whatever objects are left were added because a static field references them or
     * a method accesses that object as a constant. We then construct a objectInfoGraph of objects
     * that belong to {@link ObjectReachabilityGroup#MethodOrStaticField} and compute the connected
     * components of that objectInfoGraph.
     */
    private static EnumMap<ObjectReachabilityGroup, GroupEntry> groupObjectsByReachability(NativeImageHeap heap) {
        ObjectReachabilityGroup[] objectReachabilityGroupOrder = {
                        ObjectReachabilityGroup.Resources,
                        ObjectReachabilityGroup.DynamicHubs,
                        ObjectReachabilityGroup.ImageCodeInfo,
                        ObjectReachabilityGroup.MethodOrStaticField
        };

        EnumMap<ObjectReachabilityGroup, GroupEntry> groups = new EnumMap<>(ObjectReachabilityGroup.class);
        for (ObjectReachabilityGroup group : objectReachabilityGroupOrder) {
            groups.put(group, new GroupEntry());
        }
        Set<ObjectInfo> objects = Collections.newSetFromMap(new IdentityHashMap<>());
        heap.getObjects().stream()
                        .filter(ImageHeapConnectedComponentsPrinter::shouldIncludeObjectInTheReport)
                        .forEach(objects::add);

        /*
         * The interned strings table is treated specially because we don't want all interned
         * strings to be merged into a single component, that's why we remove it first and then
         * compute connected components.
         */
        Optional<ObjectInfo> internedStringsTable = objects.stream().filter(o -> o.getMainReason().equals(HeapInclusionReason.InternedStringsTable)).findFirst();
        if (internedStringsTable.isPresent()) {
            GroupEntry entry = new GroupEntry();
            entry.addObject(internedStringsTable.get());
            groups.put(ObjectReachabilityGroup.InternedStringsTable, entry);
            objects.remove(internedStringsTable.get());
        }

        for (ObjectReachabilityGroup group : objectReachabilityGroupOrder) {
            for (ObjectInfo object : objects) {
                ObjectReachabilityInfo reachabilityInfo = heap.objectReachabilityInfo.get(object);
                if (reachabilityInfo.objectReachableFrom(group)) {
                    groups.get(group).addObject(object);
                }
            }
            objects.removeAll(groups.get(group).objects);
        }
        return groups;
    }

    private ObjectInfoGraph constructGraph(Set<ObjectInfo> objects) {
        ObjectInfoGraph objectInfoGraph = new ObjectInfoGraph();
        for (ObjectInfo objectInfo : objects) {
            objectInfoGraph.addNode(objectInfo);
            ObjectReachabilityInfo reachabilityInfo = heap.objectReachabilityInfo.get(objectInfo);
            for (Object referencesToThisObject : reachabilityInfo.getAllReasons()) {
                if (referencesToThisObject instanceof ObjectInfo && objects.contains(referencesToThisObject)) {
                    objectInfoGraph.connect((ObjectInfo) referencesToThisObject, objectInfo);
                }
            }
        }
        return objectInfoGraph;
    }

    public void printAccessPointsForConnectedComponents(PrintWriter out) {
        try (JsonWriter writer = new JsonWriter(new BufferedWriter(out))) {
            writer.append('[').newline();
            for (Iterator<ConnectedComponent> connectedComponentIterator = connectedComponents.iterator(); connectedComponentIterator.hasNext();) {
                ConnectedComponent connectedComponent = connectedComponentIterator.next();
                writer.append('{').newline();
                writer.quote("componentId").append(':').append(String.valueOf(connectedComponent.getId())).append(',').newline();

                writer.quote("methods").append(":[").newline();
                for (Iterator<String> methodIterator = getMethodAccesses(connectedComponent.getObjects()).iterator(); methodIterator.hasNext();) {
                    String methodAccess = methodIterator.next();
                    writer.quote(methodAccess);
                    if (methodIterator.hasNext()) {
                        writer.append(',').newline();
                    }
                }
                writer.append("],").newline();

                writer.quote("staticFields").append(":[");
                for (Iterator<String> staticFieldIterator = getHostedFieldsAccess(connectedComponent.getObjects()).iterator(); staticFieldIterator.hasNext();) {
                    String hostedFieldsAccess = staticFieldIterator.next();
                    writer.quote(hostedFieldsAccess);
                    if (staticFieldIterator.hasNext()) {
                        writer.append(',').newline();
                    }
                }
                writer.append(']').newline();
                writer.append('}');
                if (connectedComponentIterator.hasNext()) {
                    writer.append(',').newline();
                }
            }
            writer.append(']').newline();
        } catch (IOException e) {
            out.write("{\"Error\":\"Failed to generate the report\"");
        }
    }

    public void printSummaryInfoForEveryObjectInConnectedComponents(PrintWriter out) {
        try (JsonWriter writer = new JsonWriter(new BufferedWriter(out))) {
            writer.append('{').newline();
            writer.quote("connectedComponents").append(":[").newline();
            for (Iterator<ConnectedComponent> iterator = connectedComponents.iterator(); iterator.hasNext();) {
                ConnectedComponent connectedComponent = iterator.next();
                writer.append('{').newline();
                writer.quote("componentId").append(':').printValue(connectedComponent.getId()).append(',').newline();
                writer.quote("sizeInBytes").append(':').append(String.valueOf(connectedComponent.getSizeInBytes())).append(',').newline();
                writer.quote("objects").append(":[");
                List<ObjectInfo> objects = connectedComponent.getObjects();
                printObjectsToJson(writer, objects);
                writer.append(']').newline();
                writer.append('}');
                if (iterator.hasNext()) {
                    writer.append(',').newline();
                }
            }
            writer.newline().append("],").newline();

            ObjectReachabilityGroup[] groupsToPrint = {
                            ObjectReachabilityGroup.DynamicHubs,
                            ObjectReachabilityGroup.ImageCodeInfo,
                            ObjectReachabilityGroup.InternedStringsTable,
                            ObjectReachabilityGroup.Resources
            };

            for (int i = 0; i < groupsToPrint.length; i++) {
                ObjectReachabilityGroup group = groupsToPrint[i];
                writer.quote(group.name).append(":{").newline();
                writer.quote("sizeInBytes").append(':').append(String.valueOf(groups.get(group).sizeInBytes)).append(',').newline();
                writer.quote("objects").append(":[").newline();
                printObjectsToJson(writer, groups.get(group).objects);
                writer.append(']').newline().append('}');
                if (i != groupsToPrint.length - 1) {
                    writer.append(',').newline();
                }
            }
            writer.newline().append('}').newline();
        } catch (IOException e) {
            out.write("{\"Error\":\"Failed to generate the report\"");
        }
    }

    public void printConnectedComponentsObjectHistogramReport(PrintWriter out) {
        String title = "Native image heap connected components report";
        out.println(fillHeading(title));
        out.println(fillHeading(imageName));
        out.printf("Total Heap Size: %s%n", ByteFormattingUtil.bytesToHuman(totalHeapSizeInBytes));
        ObjectReachabilityGroup[] headerGroups = {
                        ObjectReachabilityGroup.ImageCodeInfo,
                        ObjectReachabilityGroup.DynamicHubs,
                        ObjectReachabilityGroup.InternedStringsTable,
                        ObjectReachabilityGroup.Resources,
        };
        long totalHeaderGroupSize = 0;
        for (ObjectReachabilityGroup headerGroup : headerGroups) {
            long groupSize = groups.get(headerGroup).sizeInBytes;
            out.printf("\t%s size: %s%n", headerGroup.description, ByteFormattingUtil.bytesToHuman(groupSize));
            totalHeaderGroupSize += groupSize;
        }
        out.printf("\tIn connected components report: %s%n", ByteFormattingUtil.bytesToHuman(totalHeapSizeInBytes - totalHeaderGroupSize));
        out.printf("Total number of objects in the heap: %d%n", this.heap.getObjects().size());
        out.printf("Number of connected components in the report %d", this.connectedComponents.size());
        for (int i = 0; i < connectedComponents.size(); i++) {
            ConnectedComponent connectedComponent = connectedComponents.get(i);
            float percentageOfTotalHeapSize = 100.0f * connectedComponent.getSizeInBytes() /
                            this.totalHeapSizeInBytes;
            HeapHistogram objectHistogram = new HeapHistogram(out);
            connectedComponent.getObjects().forEach(o -> objectHistogram.add(o, o.getSize()));
            String headingInfo = String.format("ComponentId=%d | Size=%s | Percentage of total image heap size=%.4f%%", i,
                            ByteFormattingUtil.bytesToHuman(connectedComponent.getSizeInBytes()),
                            percentageOfTotalHeapSize);

            out.println();
            String fullHeading = fillHeading(headingInfo);
            objectHistogram.printHeadings(String.format("%s%n%s", "=".repeat(fullHeading.length()), fullHeading));
            objectHistogram.print();

            Collection<ObjectInfo> roots = connectedComponent.getObjects();
            Collection<String> methods = getMethodAccesses(roots);
            Collection<String> staticFields = getHostedFieldsAccess(roots);
            int entryPointLimit = 10;
            if (!staticFields.isEmpty()) {
                out.printf("%nStatic fields accessing component %d:%n", i);
                for (String field : staticFields.stream().limit(entryPointLimit).collect(Collectors.toList())) {
                    out.printf("\t%s%n", field);
                }
                if (staticFields.size() > entryPointLimit) {
                    out.printf("\t... %d more in the access_points report%n", staticFields.size() - entryPointLimit);
                }
            }
            if (!methods.isEmpty()) {
                out.printf("%nMethods accessing connected component %d:%n", i);
                for (String methodName : methods.stream().limit(entryPointLimit).collect(Collectors.toList())) {
                    out.printf("\t%s%n", formatMethodAsLink(methodName));
                }
                if (methods.size() > entryPointLimit) {
                    out.printf("\t... %d more in the access_points report%n", methods.size() - entryPointLimit);
                }
            }
        }

        for (ObjectReachabilityGroup groupType : headerGroups) {
            HeapHistogram objectHistogram = new HeapHistogram(out);
            GroupEntry groupEntry = groups.get(groupType);
            groupEntry.objects.forEach(o -> objectHistogram.add(o, o.getSize()));
            float percentageOfTotalHeapSize = 100.0f * groupEntry.sizeInBytes / this.totalHeapSizeInBytes;
            String headingInfo = String.format("Group=%s | Size=%s | Percentage of total image heap size=%.4f%%", groupType,
                            ByteFormattingUtil.bytesToHuman(groups.get(groupType).sizeInBytes),
                            percentageOfTotalHeapSize);
            out.println();
            String fullHeading = fillHeading(headingInfo);
            objectHistogram.printHeadings(String.format("%s%n%s", "=".repeat(fullHeading.length()), fullHeading));
            objectHistogram.print();
        }
    }

    private static final int HEADING_WIDTH = 140;

    private static String fillHeading(String title) {
        String fill = "=".repeat(Math.max(HEADING_WIDTH - title.length(), 8) / 2);
        return String.format("%s %s %s%s", fill, title, fill, title.length() % 2 == 0 ? "" : "=");
    }

    private Collection<String> getMethodAccesses(Collection<ObjectInfo> objects) {
        List<String> methods = new ArrayList<>();
        for (ObjectInfo object : objects) {
            ObjectReachabilityInfo reachabilityInfo = heap.objectReachabilityInfo.get(object);
            for (Object reason : reachabilityInfo.getAllReasons()) {
                if (reason instanceof String) {
                    methods.add((String) reason);
                }
            }
        }
        return methods;
    }

    private static String formatMethodAsLink(String method) {
        int lastDot = method.lastIndexOf(".");
        if (lastDot != -1) {
            return method.substring(0, lastDot) + '#' + method.substring(lastDot + 1);
        } else {
            return method;
        }
    }

    private Collection<String> getHostedFieldsAccess(Collection<ObjectInfo> objects) {
        List<String> hostedFields = new ArrayList<>();
        for (ObjectInfo object : objects) {
            ObjectReachabilityInfo reachabilityInfo = heap.objectReachabilityInfo.get(object);
            for (Object reason : reachabilityInfo.getAllReasons()) {
                if (reason instanceof HostedField) {
                    HostedField field = (HostedField) reason;
                    hostedFields.add(field.format("%H#%n"));
                }
            }
        }
        return hostedFields;
    }

    private void printObjectsToJson(JsonWriter writer, Collection<ObjectInfo> objects) throws IOException {
        for (Iterator<ObjectInfo> iterator = objects.iterator(); iterator.hasNext();) {
            ObjectInfo objectInfo = iterator.next();
            writer.append('{');
            writer.quote("className").append(':').quote(objectInfo.getObjectClass().getName()).append(',');
            writer.quote("identityHashCode").append(':').quote(String.valueOf(objectInfo.getIdentityHashCode())).append(',');
            writer.quote("constantValue").append(':').quote(constantAsRawString(bb, objectInfo.getConstant())).append(',');
            printReasonToJson(objectInfo.getMainReason(), writer);
            writer.append('}');
            if (iterator.hasNext()) {
                writer.append(',').newline();
            }
        }
    }

    private static void printReasonToJson(Object reason, JsonWriter writer) throws IOException {
        String kind = null;
        String value = null;
        if (reason instanceof String) {
            kind = "method";
            value = reason.toString();
        } else if (reason instanceof ObjectInfo) {
            ObjectInfo r = (ObjectInfo) reason;
            kind = "object";
            value = String.valueOf(r.getIdentityHashCode());
        } else if (reason instanceof HostedField) {
            HostedField r = (HostedField) reason;
            kind = "staticField";
            value = r.format("%H#%n");
        } else if (reason instanceof HeapInclusionReason) {
            kind = "svmInternal";
            value = reason.toString();
        } else {
            VMError.shouldNotReachHere("Unhandled type");
        }

        writer.quote("reason").append(":{");
        writer.quote("kind").append(':').quote(kind).append(',');
        writer.quote("value").append(':').quote(value);
        writer.append('}');
    }

    private static Object constantAsObject(BigBang bb, JavaConstant constant) {
        return bb.getSnippetReflectionProvider().asObject(Object.class, constant);
    }

    private static String constantAsRawString(BigBang bb, JavaConstant constant) {
        Object object = constantAsObject(bb, constant);
        if (object instanceof String) {
            return (String) object;
        } else {
            return JavaKind.Object.format(object);
        }
    }

    /**
     * Do DFS visit of a graph from a start node and add all reachable objects into a single
     * {@link ConnectedComponent}. Returns a List<ConnectedComponent> sorted by the size in bytes in
     * descending order
     */
    private static List<ConnectedComponent> computeConnectedComponents(ObjectInfoGraph objectInfoGraph) {
        ArrayList<ConnectedComponent> result = new ArrayList<>();
        boolean[] visited = new boolean[objectInfoGraph.getNumberOfNodes()];
        ArrayList<ObjectInfo> stack = new ArrayList<>();

        for (ObjectInfo start : objectInfoGraph.getNodesSet()) {
            if (visited[objectInfoGraph.getNodeId(start)]) {
                continue;
            }
            ConnectedComponent currentConnectedComponent = new ConnectedComponent();
            stack.add(start);
            while (!stack.isEmpty()) {
                ObjectInfo currentObject = stack.remove(stack.size() - 1);
                int currentNodeId = objectInfoGraph.getNodeId(currentObject);
                if (visited[currentNodeId]) {
                    continue;
                }
                visited[currentNodeId] = true;
                currentConnectedComponent.addObject(currentObject);
                for (ObjectInfo neighbour : objectInfoGraph.getNeighbours(currentObject)) {
                    if (!visited[objectInfoGraph.getNodeId(neighbour)]) {
                        stack.add(neighbour);
                    }
                }
            }
            result.add(currentConnectedComponent);
        }
        result.sort(Comparator.comparing(ConnectedComponent::getSizeInBytes).reversed());

        int id = 0;
        for (ConnectedComponent connectedComponent : result) {
            connectedComponent.setId(id++);
        }
        return result;
    }

    static final class ConnectedComponent {
        int id = -1;
        private final List<ObjectInfo> objects;
        private long sizeInBytes;

        ConnectedComponent() {
            this.objects = new ArrayList<>();
            this.sizeInBytes = 0;
        }

        public void addObject(ObjectInfo object) {
            this.objects.add(object);
            this.sizeInBytes += object.getSize();
        }

        public long getSizeInBytes() {
            return sizeInBytes;
        }

        public List<ObjectInfo> getObjects() {
            return objects;
        }

        void setId(int id) {
            this.id = id;
        }

        int getId() {
            return id;
        }
    }
}

/* Undirected graph of objects in a native image heap */
class ObjectInfoGraph {
    protected final Map<ObjectInfo, NodeData> nodes = new IdentityHashMap<>();

    private void doConnect(ObjectInfo from, ObjectInfo to) {
        if (from == null || to == null) {
            throw VMError.shouldNotReachHere("Trying to connect null");
        }
        NodeData fromNodeData = addNode(from);
        addNode(to);
        fromNodeData.getNeighbours().add(to);
    }

    void connect(ObjectInfo a, ObjectInfo b) {
        doConnect(a, b);
        doConnect(b, a);
    }

    NodeData addNode(ObjectInfo a) {
        if (nodes.containsKey(a)) {
            return nodes.get(a);
        }
        return nodes.computeIfAbsent(a, objectInfo -> new NodeData(nodes.size()));
    }

    Set<ObjectInfo> getNodesSet() {
        return nodes.keySet();
    }

    int getNodeId(ObjectInfo objectInfo) {
        NodeData nodeData = nodes.get(objectInfo);
        if (nodeData == null) {
            return -1;
        }
        return nodeData.getNodeId();
    }

    Set<ObjectInfo> getNeighbours(ObjectInfo a) {
        NodeData nodeData = nodes.get(a);
        if (nodeData == null) {
            return Collections.emptySet();
        }
        return nodeData.getNeighbours();
    }

    int getNumberOfNodes() {
        return nodes.size();
    }

    private static final class NodeData {
        private final Set<ObjectInfo> neighbours;
        private final int nodeId;

        private NodeData(int nodeId) {
            this.neighbours = Collections.newSetFromMap(new IdentityHashMap<>());
            this.nodeId = nodeId;
        }

        private Set<ObjectInfo> getNeighbours() {
            return neighbours;
        }

        private int getNodeId() {
            return nodeId;
        }
    }
}
