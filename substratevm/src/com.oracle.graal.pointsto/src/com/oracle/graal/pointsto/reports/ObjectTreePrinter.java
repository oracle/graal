/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.reports;

import static com.oracle.graal.pointsto.reports.ReportUtils.CHILD;
import static com.oracle.graal.pointsto.reports.ReportUtils.CONNECTING_INDENT;
import static com.oracle.graal.pointsto.reports.ReportUtils.EMPTY_INDENT;
import static com.oracle.graal.pointsto.reports.ReportUtils.LAST_CHILD;
import static com.oracle.graal.pointsto.reports.ReportUtils.fieldComparator;
import static com.oracle.graal.pointsto.reports.ReportUtils.positionComparator;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class ObjectTreePrinter extends ObjectScanner {

    public static void print(BigBang bigbang, String path, String reportName) {
        ReportUtils.report("object tree", path + File.separatorChar + "reports", "object_tree_" + reportName, "txt",
                        writer -> ObjectTreePrinter.doPrint(writer, bigbang));
    }

    private static void doPrint(PrintWriter out, BigBang bigbang) {
        if (!PointstoOptions.ExhaustiveHeapScan.getValue(bigbang.getOptions())) {
            String types = Arrays.stream(bigbang.skippedHeapTypes()).map(t -> t.toJavaName()).collect(Collectors.joining(", "));
            System.out.println("Exhaustive heap scanning is disabled. The object tree will not contain all instances of types: " + types);
            System.out.println("Exhaustive heap scanning can be turned on using -H:+ExhaustiveHeapScan.");
        }
        ObjectTreePrinter printer = new ObjectTreePrinter(bigbang);
        printer.scanBootImageHeapRoots(fieldComparator, positionComparator);
        printer.printTypeHierarchy(out);
    }

    static class RootSource {
        final Object source;

        RootSource(Object source) {
            this.source = source;
        }

        String format() {
            if (source instanceof ResolvedJavaField) {
                ResolvedJavaField field = (ResolvedJavaField) source;
                return field.format("%H.%n:%T");
            } else if (source instanceof ResolvedJavaMethod) {
                ResolvedJavaMethod method = (ResolvedJavaMethod) source;
                return method.format("%H.%n(%p)");
            } else {
                throw JVMCIError.shouldNotReachHere("unknown source: " + source);
            }
        }
    }

    static class ObjectNodeBase {
        final RootSource source;
        final AnalysisType type;
        final JavaConstant constant;

        ObjectNodeBase(AnalysisType type, JavaConstant constant) {
            this(type, null, constant);
        }

        ObjectNodeBase(AnalysisType type, RootSource rootSource, JavaConstant constant) {
            this.type = type;
            this.source = rootSource;
            this.constant = constant;
        }

        boolean isRoot() {
            return source != null;
        }

        String typeFormat() {
            return type.toJavaName(true);
        }

        String constantFormat(BigBang bb) {
            return constantAsString(bb, constant);
        }

        boolean isNull() {
            return this == NullValue.INSTANCE;
        }

        @Override
        public int hashCode() {
            return type.hashCode() ^ constant.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ObjectNodeBase) {
                ObjectNodeBase that = (ObjectNodeBase) obj;
                return this.type.equals(that.type) && this.constant.equals(that.constant);
            }
            return false;
        }

        static ObjectNodeBase forNull() {
            return NullValue.INSTANCE;
        }

        static ObjectNodeBase fromConstant(BigBang bb, JavaConstant constant) {
            return fromConstant(bb, constant, null);
        }

        static ObjectNodeBase fromConstant(BigBang bb, JavaConstant constant, RootSource rootSource) {
            AnalysisType type = constantType(bb, constant);
            if (type == null) {
                return ObjectNode.forNull();
            } else if (type.isArray()) {
                return new ArrayObjectNode(type, rootSource, constant);
            } else {
                return new ObjectNode(type, rootSource, constant);
            }
        }
    }

    static class ObjectNode extends ObjectNodeBase {
        final Map<AnalysisField, FieldNode> fields;

        ObjectNode(AnalysisType type, RootSource rootSource, JavaConstant constant) {
            super(type, rootSource, constant);
            this.fields = new LinkedHashMap<>();
        }

        public Collection<FieldNode> fields() {
            return fields.values();
        }

        void addField(AnalysisField field, ObjectNodeBase typeNode) {
            assert !fields.containsKey(field);
            fields.put(field, new FieldNode(field, typeNode));
        }
    }

    static class FieldNode {
        final AnalysisField field;
        final ObjectNodeBase value;

        FieldNode(AnalysisField field, ObjectNodeBase value) {
            this.field = field;
            this.value = value;
        }

        public Object format() {
            return field.format("%n:%T");
        }
    }

    static class ArrayObjectNode extends ObjectNodeBase {
        final Map<Integer, ElementNode> elements;

        ArrayObjectNode(AnalysisType type, RootSource rootSource, JavaConstant constant) {
            super(type, rootSource, constant);
            this.elements = new LinkedHashMap<>();
        }

        public Collection<ElementNode> elements() {
            return elements.values();
        }

        void addElement(Integer index, ObjectNodeBase object) {
            assert !elements.containsKey(index);
            elements.put(index, new ElementNode(index, object));
        }
    }

    static class ElementNode {
        final Integer index;
        final ObjectNodeBase value;

        ElementNode(Integer index, ObjectNodeBase value) {
            this.index = index;
            this.value = value;
        }

        public Object format() {
            return "[" + index + "]";
        }
    }

    static final class NullValue extends ObjectNodeBase {

        static final ObjectNodeBase INSTANCE = new NullValue(null);

        private NullValue(AnalysisType type) {
            super(type, null);
        }

        @Override
        public int hashCode() {
            return -1;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }
    }

    static class SimpleMatcher {
        private final String[] patterns;

        SimpleMatcher(String[] patterns) {
            this.patterns = patterns;
        }

        public boolean matches(String input) {
            for (String pattern : patterns) {
                if (pattern.startsWith("*") && input.endsWith(pattern.substring(1, pattern.length()))) {
                    return true;
                } else if (pattern.endsWith("*") && input.startsWith(pattern.substring(0, pattern.length() - 1))) {
                    return true;
                } else if (pattern.startsWith("*") && pattern.endsWith("*") && input.contains(pattern.substring(1, pattern.length() - 1))) {
                    return true;
                } else if (input.equals(pattern)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final String[] suppressTypesDefault = new String[]{
                    "java.lang.String", "java.math.BigInteger", "java.lang.Character$UnicodeScript",
                    "sun.misc.FDBigInteger",
                    "boolean", "char", "byte", "int", "long", "double", "float"};

    private static final String[] suppressRootsDefault = new String[]{
                    "java.lang.Character$UnicodeBlock.map*",
                    "java.lang.Character$UnicodeScript.aliases*",
                    "java.lang.ConditionalSpecialCasing.entryTable*",
                    "java.util.Formatter.fsPattern*",
                    "java.lang.Character$UnicodeBlock.of(int)",
                    "java.util.ResourceBundle.getBundle(String, Locale, ResourceBundle$Control)",
                    "com.ibm.icu.impl.ICUResourceBundle.BUNDLE_CACHE*",
                    "com.ibm.icu.impl.ICUResourceBundle.GET_AVAILABLE_CACHE*",
                    "com.ibm.icu.impl.ICUResourceBundleReader.CACHE*",
                    "ibm.icu.impl.DayPeriodRules$DayPeriodRulesData*",
                    "com.ibm.icu.util.ULocale.nameCache*",
                    "com.oracle.svm.core.option.RuntimeOptionsSupportImpl.set(String, Object)"};

    private final Map<JavaConstant, ObjectNodeBase> constantToNode;
    private final SimpleMatcher suppressTypeMatcher;
    private final SimpleMatcher expandTypeMatcher;
    private final SimpleMatcher defaultSuppressTypeMatcher;
    private final SimpleMatcher suppressRootMatcher;
    private final SimpleMatcher expandRootMatcher;
    private final SimpleMatcher defaultSuppressRootMatcher;

    private ObjectTreePrinter(BigBang bigbang) {
        super(bigbang, new ReusableSet());

        /* Use linked hash map for predictable iteration order. */
        this.constantToNode = new LinkedHashMap<>();

        OptionValues options = bigbang.getOptions();

        this.suppressTypeMatcher = new SimpleMatcher(AnalysisReportsOptions.ImageObjectTreeSuppressTypes.getValue(options).trim().split(","));
        this.expandTypeMatcher = new SimpleMatcher(AnalysisReportsOptions.ImageObjectTreeExpandTypes.getValue(options).trim().split(","));
        this.defaultSuppressTypeMatcher = new SimpleMatcher(suppressTypesDefault);

        this.suppressRootMatcher = new SimpleMatcher(AnalysisReportsOptions.ImageObjectTreeSuppressRoots.getValue(options).trim().split(","));
        this.expandRootMatcher = new SimpleMatcher(AnalysisReportsOptions.ImageObjectTreeExpandRoots.getValue(options).trim().split(","));
        this.defaultSuppressRootMatcher = new SimpleMatcher(suppressRootsDefault);
    }

    @Override
    public void forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue) {
    }

    @Override
    public void forNullFieldValue(JavaConstant receiver, AnalysisField field) {
        if (receiver == null) {
            // static field
            return;
        }

        assert constantToNode.containsKey(receiver);

        ObjectNode receiverNode = (ObjectNode) constantToNode.get(receiver);
        receiverNode.addField(field, ObjectNodeBase.forNull());
    }

    @Override
    public void forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue) {

        if (receiver == null) {
            // static field
            return;
        }

        if (constantToNode.containsKey(receiver) && constantToNode.containsKey(fieldValue)) {
            ObjectNode receiverNode = (ObjectNode) constantToNode.get(receiver);
            ObjectNodeBase valueNode = constantToNode.get(fieldValue);
            receiverNode.addField(field, valueNode);
        }
    }

    @Override
    public void forNullArrayElement(JavaConstant array, AnalysisType arrayType, int index) {
        assert constantToNode.containsKey(array);

        ArrayObjectNode arrayNode = (ArrayObjectNode) constantToNode.get(array);
        arrayNode.addElement(index, ObjectNodeBase.forNull());
    }

    @Override
    public void forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementConstant, AnalysisType elementType, int index) {
        if (constantToNode.containsKey(array) && constantToNode.containsKey(elementConstant)) {
            ArrayObjectNode arrayNode = (ArrayObjectNode) constantToNode.get(array);
            ObjectNodeBase valueNode = constantToNode.get(elementConstant);
            arrayNode.addElement(index, valueNode);
        }
    }

    @Override
    protected void forScannedConstant(JavaConstant scannedValue, ScanReason reason) {
        JVMCIError.guarantee(scannedValue != null, "scannedValue is null");
        constantToNode.computeIfAbsent(scannedValue, c -> {
            ObjectNodeBase node;
            if (reason instanceof FieldScan) {
                ResolvedJavaField field = ((FieldScan) reason).getField();
                if (field.isStatic()) {
                    node = ObjectNodeBase.fromConstant(bb, scannedValue, new RootSource(field));
                } else {
                    node = ObjectNodeBase.fromConstant(bb, scannedValue);
                }
            } else if (reason instanceof MethodScan) {
                ResolvedJavaMethod method = ((MethodScan) reason).getMethod();
                node = ObjectNodeBase.fromConstant(bb, scannedValue, new RootSource(method));
            } else {
                node = ObjectNodeBase.fromConstant(bb, scannedValue);
            }

            return node;
        });
    }

    static String constantAsString(BigBang bb, JavaConstant constant) {
        Object object = constantAsObject(bb, constant);
        if (object instanceof String) {
            String str = (String) object;
            str = escape(str);
            if (str.length() > 10) {
                str = str.substring(0, 10);
                str = str + "...";
            }
            return "\"" + str + "\"";
        } else {
            return escape(JavaKind.Object.format(object));
        }
    }

    private static String escape(String str) {
        return str.replace("\n", "\\n").replace("\r", "\\r");
    }

    static class WorkListEntry {
        final String prefix;
        final String marker;
        final Object node;
        final Set<ObjectNodeBase> trail;
        final boolean lastProperty;

        WorkListEntry(String prefix, String marker, Object node, boolean lastProperty, Set<ObjectNodeBase> parentTrail) {
            this.prefix = prefix;
            this.marker = marker;
            this.node = node;
            this.lastProperty = lastProperty;
            this.trail = new LinkedHashSet<>(parentTrail);
        }
    }

    private boolean suppressType(AnalysisType type) {
        AnalysisType elementalType = (AnalysisType) type.getElementalType();
        String elementalTypeName = elementalType.toJavaName(true);

        if (expandTypeMatcher.matches(elementalTypeName)) {
            return false;
        }

        if (suppressTypeMatcher.matches(elementalTypeName)) {
            return true;
        }

        if (defaultSuppressTypeMatcher.matches(elementalTypeName)) {
            return true;
        }

        return false;

    }

    private boolean suppressRoot(RootSource source) {
        if (expandRootMatcher.matches(source.format())) {
            return false;
        }

        if (suppressRootMatcher.matches(source.format())) {
            return true;
        }

        if (defaultSuppressRootMatcher.matches(source.format())) {
            return true;
        }

        return false;
    }

    private void printTypeHierarchy(PrintWriter out) {
        out.println("Heap roots");
        Iterator<ObjectNodeBase> iterator = constantToNode.values().stream().filter(n -> n.isRoot()).iterator();
        while (iterator.hasNext()) {
            ObjectNodeBase node = iterator.next();
            boolean lastRoot = !iterator.hasNext();
            printTypeHierarchyNode(out, node, lastRoot);
        }
        out.println();
    }

    private Map<ObjectNodeBase, Integer> expandedNodes = new LinkedHashMap<>();
    private int nodeId = 0;

    private void printTypeHierarchyNode(PrintWriter out, ObjectNodeBase root, boolean lastRoot) {

        Deque<WorkListEntry> workList = new ArrayDeque<>();
        if (expandedNodes.containsKey(root)) {
            out.format("%s%s %s id-ref=%s toString=%s%n", lastRoot ? LAST_CHILD : CHILD, "root",
                            root.source.format(), expandedNodes.get(root), root.constantFormat(bb));
        } else {
            if (suppressRoot(root.source)) {
                out.format("%s%s %s toString=%s (expansion suppressed)%n", lastRoot ? LAST_CHILD : CHILD, "root",
                                root.source.format(), root.constantFormat(bb));
            } else {
                out.format("%s%s %s value:%n", lastRoot ? LAST_CHILD : CHILD, "root", root.source.format());
                if (suppressType(root.type)) {
                    out.format("%s%s%s toString=%s (expansion suppressed)%n", lastRoot ? EMPTY_INDENT : CONNECTING_INDENT, LAST_CHILD,
                                    root.typeFormat(), root.constantFormat(bb));
                } else {
                    workList.push(new WorkListEntry(lastRoot ? EMPTY_INDENT : CONNECTING_INDENT, LAST_CHILD, root, true, new LinkedHashSet<>()));
                }
            }
        }

        while (!workList.isEmpty()) {
            WorkListEntry entry = workList.pop();
            if (entry.node instanceof ObjectNode) {
                ObjectNode objectNode = (ObjectNode) entry.node;
                if (expandedNodes.containsKey(objectNode)) {
                    out.format("%s%s%s id-ref=%s toString=%s%n", entry.prefix, entry.marker,
                                    objectNode.typeFormat(), expandedNodes.get(objectNode), objectNode.constantFormat(bb));
                } else {
                    int id = nodeId++;
                    expandedNodes.put(objectNode, id);
                    out.format("%s%s%s id=%s toString=%s ", entry.prefix, entry.marker,
                                    objectNode.typeFormat(), id, objectNode.constantFormat(bb));

                    if (hasProperties(objectNode)) {
                        out.format("fields:%n");
                        String prefix = entry.prefix + (entry.lastProperty ? EMPTY_INDENT : CONNECTING_INDENT);
                        explore(entry.trail, workList, prefix, objectNode);
                    } else {
                        out.format("(no fields)%n");
                    }
                }
            } else if (entry.node instanceof ArrayObjectNode) {
                ArrayObjectNode arrayObjectNode = (ArrayObjectNode) entry.node;
                if (expandedNodes.containsKey(arrayObjectNode)) {
                    out.format("%s%s%s id-ref=%s toString=%s%n", entry.prefix, entry.marker,
                                    arrayObjectNode.typeFormat(), expandedNodes.get(arrayObjectNode), arrayObjectNode.constantFormat(bb));
                } else {
                    int id = nodeId++;
                    expandedNodes.put(arrayObjectNode, id);
                    out.format("%s%s%s id=%s toString=%s ", entry.prefix, entry.marker,
                                    arrayObjectNode.typeFormat(), id, arrayObjectNode.constantFormat(bb));

                    if (hasProperties(arrayObjectNode)) {
                        out.format("elements (excluding null):%n");
                        String prefix = entry.prefix + (entry.lastProperty ? EMPTY_INDENT : CONNECTING_INDENT);
                        explore(entry.trail, workList, prefix, arrayObjectNode);
                    } else {
                        out.format("(no elements)%n");
                    }
                }
            } else if (entry.node instanceof FieldNode) {
                FieldNode fieldNode = (FieldNode) entry.node;
                if (fieldNode.value.isNull()) {
                    out.format("%s%s%s value=null%n", entry.prefix, entry.marker, fieldNode.format());
                } else {
                    if (suppressType(fieldNode.value.type)) {
                        out.format("%s%s%s toString=%s (expansion suppressed)%n", entry.prefix, entry.marker,
                                        fieldNode.format(), fieldNode.value.constantFormat(bb));
                    } else {
                        out.format("%s%s%s value:%n", entry.prefix, entry.marker, fieldNode.format());
                        String prefix = entry.prefix + (entry.lastProperty ? EMPTY_INDENT : CONNECTING_INDENT);
                        workList.push(new WorkListEntry(prefix, LAST_CHILD, fieldNode.value, true, entry.trail));
                    }
                }
            } else if (entry.node instanceof ElementNode) {
                ElementNode elementNode = (ElementNode) entry.node;
                if (!elementNode.value.isNull()) {
                    if (suppressType(elementNode.value.type)) {
                        out.format("%s%s%s toString=%s (expansion suppressed)%n", entry.prefix, entry.marker,
                                        elementNode.format(), elementNode.value.constantFormat(bb));
                    } else {
                        workList.push(new WorkListEntry(entry.prefix, (entry.lastProperty ? LAST_CHILD : CHILD) + "[" + elementNode.index + "] ",
                                        elementNode.value, entry.lastProperty, entry.trail));
                    }
                }
            } else {
                out.format("%s%s %s%n", entry.prefix, entry.marker, entry.node);
            }
        }

    }

    private static boolean hasProperties(ObjectNodeBase value) {
        if (value instanceof ObjectNode) {
            ObjectNode typeNode = (ObjectNode) value;
            return typeNode.fields().size() > 0;
        } else if (value instanceof ArrayObjectNode) {
            ArrayObjectNode arrayTypeNode = (ArrayObjectNode) value;
            return arrayTypeNode.elements().stream().filter(e -> !e.value.isNull()).count() > 0;
        } else {
            throw JVMCIError.shouldNotReachHere("unknown object");
        }
    }

    private static void explore(Set<ObjectNodeBase> trail, Deque<WorkListEntry> workList, String prefix, ObjectNodeBase value) {
        if (trail.contains(value)) {
            return;
        }
        trail.add(value);
        if (value instanceof ObjectNode) {
            ObjectNode typeNode = (ObjectNode) value;
            boolean firstProperty = true;
            for (FieldNode fieldNode : typeNode.fields()) {
                workList.push(new WorkListEntry(prefix, (firstProperty ? LAST_CHILD : CHILD), fieldNode, firstProperty, trail));
                firstProperty = false;
            }
        } else if (value instanceof ArrayObjectNode) {
            ArrayObjectNode arrayTypeNode = (ArrayObjectNode) value;
            boolean firstProperty = true;
            for (int idx = arrayTypeNode.elements().size() - 1; idx >= 0; idx--) {
                ElementNode elementNode = arrayTypeNode.elements.get(idx);
                workList.push(new WorkListEntry(prefix, (firstProperty ? LAST_CHILD : CHILD), elementNode, firstProperty, trail));
                firstProperty = false;
            }
        }
    }
}
