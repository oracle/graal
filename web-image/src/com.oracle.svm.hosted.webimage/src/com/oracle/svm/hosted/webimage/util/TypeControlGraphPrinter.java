/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.util;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.Pair;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.codegen.WebImageTypeControl;
import com.oracle.svm.hosted.webimage.metrickeys.MethodMetricKeys;
import com.oracle.svm.hosted.webimage.util.metrics.MethodMetricsCollector;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.webimage.object.ConstantIdentityMapping.IdentityNode;
import com.oracle.svm.webimage.object.ObjectInspector.ObjectDefinition;

import jdk.graal.compiler.debug.GraalError;

/**
 * Dumps the type control graph as a set of CSV files (fields separated by tabs).
 *
 * The graph is made up of multiple files:
 *
 * <ol>
 * <li>types: A description of all emitted types</li>
 * <li>methods: A description of all methods listed as an emission reason for a type or object</li>
 * <li>rels: Edges between types and methods</li>
 * <li>objects: A description of all inspected objects</li>
 * <li>object_rels: Edges involving objects</li>
 * </ol>
 *
 * Because inspected objects make up the majority of nodes and can slow down analysis efforts (e.g.
 * loading the graph), the first three files can be loaded on their own and describe the graph
 * without objects. If object information is required, the last two files can be included as well.
 */
public class TypeControlGraphPrinter {

    abstract static class Node<T> {
        static int ID = 0;

        final int id;

        final T obj;

        /**
         * Reasons why this node is in the graph.
         *
         * Represents incoming edges.
         */
        private final Set<Node<?>> reasons = new HashSet<>();

        Node(T obj) {
            id = ID++;
            this.obj = obj;
        }

        void addReason(Node<?> t) {
            Objects.requireNonNull(t);
            reasons.add(t);
        }

        Set<Node<?>> getReasons() {
            return Collections.unmodifiableSet(reasons);
        }

        /**
         * Column values for the CSV file.
         */
        public abstract String[] getValues();
    }

    /**
     * Represents a type in the graph.
     */
    static class TypeNode extends Node<HostedType> {
        TypeNode(HostedType t) {
            super(t);
        }

        public static String[] getHeaderNames() {
            return new String[]{"Id", "SimpleName", "Name"};
        }

        @Override
        public String[] getValues() {
            Class<?> clazz = obj.getJavaClass();
            return new String[]{String.valueOf(id), ClassUtil.getUnqualifiedName(clazz), clazz.getName()};
        }
    }

    /**
     * Represents a method in the graph.
     */
    static class MethodNode extends Node<HostedMethod> {

        public final TypeNode declaringType;

        /**
         * Size collected in {@link MethodMetricKeys#METHOD_SIZE} metric.
         */
        private int methodSize = -1;

        MethodNode(HostedMethod obj, TypeNode declaringType) {
            super(obj);
            this.declaringType = Objects.requireNonNull(declaringType);
        }

        public static String[] getHeaderNames() {
            return new String[]{"Id", "SimpleName", "Name", "Size", "TypeId"};
        }

        @Override
        public String[] getValues() {
            return new String[]{
                            String.valueOf(id),
                            obj.getName(),
                            obj.format("%H.%n(%p)%r"),
                            String.valueOf(methodSize),
                            String.valueOf(declaringType.id)
            };
        }

        public void setMethodSize(int methodSize) {
            this.methodSize = methodSize;
        }
    }

    /**
     * Represents an inspected object in the graph.
     */
    static class ObjectNode extends Node<ObjectDefinition> {

        public final IdentityNode identityNode;

        ObjectNode(IdentityNode node) {
            super(node.getDefinition());

            this.identityNode = node;
        }

        public static String[] getHeaderNames() {
            return new String[]{"Id", "SimpleName", "Name", "Size", "IdentityNodeName", "IdentityNodeNum"};
        }

        @Override
        public String[] getValues() {
            return new String[]{
                            String.valueOf(id),
                            ClassUtil.getUnqualifiedName(obj.getClass()),
                            obj.toString(),
                            String.valueOf(obj.getSize()),
                            identityNode.hasName() ? identityNode.getName() : "",
                            String.valueOf(identityNode.getNum())
            };
        }
    }

    public static void print(WebImageTypeControl tc, MethodMetricsCollector methodMetricsCollector, String reportsPath, String reportName) {
        Collection<HostedType> types = tc.queryEmittedTypes();

        Map<Object, Node<?>> nodes = new HashMap<>();

        for (HostedType t : types) {
            Node<?> typeNode = getOrCreateNode(nodes, t);

            for (Object reason : tc.getReasons(t)) {
                if (!isValidReason(reason, t)) {
                    continue;
                }

                Node<?> node = getOrCreateNode(nodes, reason);
                typeNode.addReason(node);
            }
        }

        Iterable<IdentityNode> identityNodes = tc.getConstantMap().identityMapping.identityNodes();

        // First, create all object nodes so that they exist when adding reasons below
        identityNodes.forEach(node -> nodes.put(node.getDefinition(), new ObjectNode(node)));

        for (IdentityNode identityNode : identityNodes) {
            ObjectDefinition odef = identityNode.getDefinition();
            Node<?> node = nodes.get(odef);

            for (Object reason : odef.getReasons()) {
                if (!isValidReason(reason, null)) {
                    continue;
                }

                Node<?> reasonNode = getOrCreateNode(nodes, reason);
                node.addReason(reasonNode);
            }
        }

        nodes.values().stream().filter(e -> e instanceof MethodNode).forEach(n -> {
            MethodNode methodNode = (MethodNode) n;
            methodNode.setMethodSize(methodMetricsCollector.getMethodMetric(methodNode.obj, MethodMetricKeys.METHOD_SIZE).intValue());
        });

        toCsvFile("TypeNodes", reportsPath, "types", reportName, writer -> printNodeCsv(writer, TypeNode.getHeaderNames(), nodes.values().stream().filter(e -> e instanceof TypeNode)));
        toCsvFile("MethodNodes", reportsPath, "methods", reportName, writer -> printNodeCsv(writer, MethodNode.getHeaderNames(), nodes.values().stream().filter(e -> e instanceof MethodNode)));
        toCsvFile("ObjectNodes", reportsPath, "objects", reportName, writer -> printNodeCsv(writer, ObjectNode.getHeaderNames(), nodes.values().stream().filter(e -> e instanceof ObjectNode)));

        Stream<Pair<Node<?>, Node<?>>> relsStream = nodes.values().stream().flatMap(end -> end.getReasons().stream().map(start -> Pair.create(start, end)));

        /*
         * Lists of relations between nodes. The 'true' key contains relations involving at least
         * one ObjectNode, the 'false' key contains the remaining relations.
         */
        Map<Boolean, List<Pair<Node<?>, Node<?>>>> rels = relsStream.collect(Collectors.partitioningBy(pair -> pair.getLeft() instanceof ObjectNode || pair.getRight() instanceof ObjectNode));

        /*
         * Relations involving objects are dumped separately to enable not loading data about
         * objects into a database (all the object nodes and relations take a substantial time to
         * load)
         */
        toCsvFile("Reasons", reportsPath, "rels", reportName, writer -> printRels(writer, rels.get(false)));
        toCsvFile("Reasons (Objects)", reportsPath, "object_rels", reportName, writer -> printRels(writer, rels.get(true)));
    }

    /**
     * Prints the given node relations to the given {@link PrintWriter}.
     */
    private static void printRels(PrintWriter writer, List<Pair<Node<?>, Node<?>>> rels) {
        printCsvLine(writer, new String[]{"StartId", "EndId"});
        rels.forEach(p -> printCsvLine(writer, new String[]{String.valueOf(p.getLeft().id), String.valueOf(p.getRight().id)}));
    }

    private static void toCsvFile(String description, String reportsPath, String prefix, String reportName, Consumer<PrintWriter> reporter) {
        final String name = prefix + "_" + reportName;
        ReportUtils.report(description, reportsPath, name, "csv", reporter);
    }

    /**
     * Prints a CSV file using the given nodes.
     *
     * @param writer CSV file content is written to this {@link PrintWriter}
     * @param headers Column names
     * @param stream Stream of nodes. Each node produces one line using {@link Node#getValues()}
     */
    private static void printNodeCsv(PrintWriter writer, String[] headers, Stream<Node<?>> stream) {
        printCsvLine(writer, headers);

        stream.forEach(node -> {
            String[] values = node.getValues();
            assert headers.length == values.length : Arrays.toString(headers) + " vs. " + Arrays.toString(values);
            printCsvLine(writer, values);
        });
    }

    /**
     * Prints the given values as a CSV line separated by tab characters.
     *
     * Sanitizes the values by replacing all characters except ASCII 0x20-0x7E with '?'.
     *
     * @param writer Line is written to this {@link PrintWriter}
     * @param values Value for all columns
     */
    private static void printCsvLine(PrintWriter writer, String[] values) {
        // Replace non-printable and non-ascii characters
        writer.println(Arrays.stream(values).map(v -> v.replaceAll("[^\\x20-\\x7E]", "?")).collect(Collectors.joining("\t")));
    }

    /**
     * Predicate to determine whether the given reason should be valid for the given type.
     *
     * The following reasons are not valid:
     * <ul>
     * <li>The type itself</li>
     * <li>A method defined on {@code type}</li>
     * <li>Anything that is not a type or method</li>
     * </ul>
     */
    private static boolean isValidReason(Object reason, HostedType type) {
        if (reason instanceof HostedType) {
            return !reason.equals(type);
        } else if (reason instanceof HostedMethod) {
            return !((HostedMethod) reason).getDeclaringClass().equals(type);
        } else if (reason instanceof ObjectDefinition) {
            return true;
        } else {
            return false;
        }
    }

    private static Node<?> getOrCreateNode(Map<Object, Node<?>> map, Object key) {
        Node<?> node = map.get(key);
        if (node == null) {
            node = createNode(key, map);
            map.put(key, node);
        }

        return node;
    }

    private static Node<?> createNode(Object reason, Map<Object, Node<?>> map) {
        if (reason instanceof HostedType) {
            return new TypeNode((HostedType) reason);
        } else if (reason instanceof HostedMethod) {
            HostedType m = ((HostedMethod) reason).getDeclaringClass();
            TypeNode typeNode = (TypeNode) getOrCreateNode(map, m);
            return new MethodNode((HostedMethod) reason, typeNode);
        } else {
            throw GraalError.shouldNotReachHere(String.valueOf(reason)); // ExcludeFromJacocoGeneratedReport
        }
    }
}
