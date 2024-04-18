/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.CPUSamplerData;
import com.oracle.truffle.tools.profiler.ProfilerNode;

final class SVGSamplerOutput {

    public static void printSamplingFlameGraph(PrintStream out, List<CPUSamplerData> data) {
        GraphOwner graph = new GraphOwner(new StringBuilder(), data);

        graph.addComponent(new SVGFlameGraph(graph));
        graph.addComponent(new SVGHistogram(graph));

        graph.generateSVG();

        out.print(graph.svg.toString());
    }

    private final StringBuilder output;

    SVGSamplerOutput(StringBuilder output) {
        this.output = output;
    }

    public void header(double width, double height) {
        output.append("<?xml version=\"1.0\" standalone=\"no\"?>\n");
        output.append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n");
        output.append(String.format(Locale.ROOT,
                        "<svg version=\"1.1\" width=\"%1$f\" height=\"%2$f\" onload=\"init(evt)\" viewBox=\"0 0 %1$f %2$f\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">\n",
                        width, height));
    }

    public void include(String data) {
        output.append(data);
    }

    public static String allocateColor(int r, int g, int b) {
        return String.format(Locale.ROOT, "rgb(%d, %d, %d)", r, g, b);
    }

    public static String startGroup(Map<String, String> attributes) {
        StringBuilder result = new StringBuilder();
        result.append("<g ");
        for (String key : new String[]{"class", "style", "onmouseover", "onmouseout", "onclick", "id"}) {
            if (attributes.containsKey(key)) {
                result.append(String.format(Locale.ROOT, "%s=\"%s\"", key, attributes.get(key)));
                result.append(" ");
            }
        }
        result.append(">\n");

        if (attributes.containsKey("g_extra")) {
            result.append(attributes.get("g_extra"));
        }

        if (attributes.containsKey("title")) {
            result.append(String.format(Locale.ROOT, "<title>%s</title>", attributes.get("title")));
        }

        if (attributes.containsKey("href")) {
            result.append(String.format(Locale.ROOT, "<a xlink:href=%s", attributes.get("href")));

            final String target;
            if (attributes.containsKey("target")) {
                target = attributes.get("target");
            } else {
                target = "_top";
            }
            result.append(String.format(Locale.ROOT, " target=%s", target));
        }
        return result.toString();
    }

    public static String endGroup(Map<String, String> attributes) {
        StringBuilder result = new StringBuilder();
        if (attributes.containsKey("href")) {
            result.append("</a>\n");
        }
        result.append("</g>\n");
        return result.toString();
    }

    public static String startSubDrawing(Map<String, String> attributes) {
        StringBuilder result = new StringBuilder();
        result.append("<svg ");
        for (Map.Entry<String, String> e : attributes.entrySet()) {
            result.append(String.format(Locale.ROOT, "%s=\"%s\"", e.getKey(), e.getValue()));
            result.append(" ");
        }
        result.append(">\n");

        return result.toString();
    }

    public static String endSubDrawing() {
        StringBuilder result = new StringBuilder();
        result.append("</svg>\n");
        return result.toString();
    }

    public static String fillRectangle(double x1, double y1, double w, double h, String fill, String extras, Map<String, String> attributes) {
        StringBuilder result = new StringBuilder();
        result.append(String.format(Locale.ROOT, "<rect x=\"%f\" y=\"%f\" width=\"%f\" height=\"%f\" fill=\"%s\" %s", x1, y1, w, h, fill, extras));
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            result.append(String.format(Locale.ROOT, " %s=\"%s\"", entry.getKey(), entry.getValue()));
        }
        result.append("/>\n");
        return result.toString();
    }

    public static String ttfString(String color, String font, double size, double x, double y, String text, String loc, String extras) {
        return String.format(Locale.ROOT, "<text text-anchor=\"%s\" x=\"%f\" y=\"%f\" font-size=\"%f\" font-family=\"%s\" fill=\"%s\" %s >%s</text>\n", loc == null ? "left" : loc, x, y, size, font,
                        color,
                        extras == null ? "" : extras, escape(text));
    }

    public void close() {
        output.append("</svg>");
    }

    @Override
    public String toString() {
        return output.toString();
    }

    public static String escape(String text) {
        return text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    }

    public static String black() {
        return allocateColor(0, 0, 0);
    }

    private interface SVGComponent {

        String css();

        String script();

        String initFunction(String argName);

        String resizeFunction();

        String searchFunction(String argName);

        String resetSearchFunction();

        String drawCanvas(double x, double y);

        double width();

        double height();
    }

    private enum GraphColorMap {
        FLAME,
        AQUA,
        ORANGE,
        GREEN,
        RED,
        YELLOW,
        PURPLE,
        BLUE,
        GRAY,
    }

    private static double MINWIDTH = 3;
    private static double IMAGEWIDTH = 1200.0;

    private static class GraphOwner implements SVGComponent {

        private final SVGSamplerOutput svg;
        private final List<CPUSamplerData> data;
        private ArrayList<SVGComponent> components;
        private Random random = new Random();
        private Map<GraphColorMap, String> languageColors;
        private Map<GraphColorMap, Map<SampleKey, String>> colorsForKeys = new HashMap<>();
        private int sampleId;
        public final Map<String, Integer> nameHash = new HashMap<>();
        public final Map<String, Integer> sourceHash = new HashMap<>();
        public final Map<SampleKey, Integer> keyHash = new HashMap<>();
        public final ArrayList<SampleKey> sampleKeys = new ArrayList<>();
        public int nameCounter = 0;
        public int sourceCounter = 0;
        public int keyCounter = 0;
        public final JSONArray sampleNames = new JSONArray();
        public final JSONArray sourceNames = new JSONArray();
        public final JSONArray sampleJsonKeys = new JSONArray();
        public final JSONArray sampleData = new JSONArray();

        GraphOwner(StringBuilder output, List<CPUSamplerData> data) {
            svg = new SVGSamplerOutput(output);
            this.data = data;
            components = new ArrayList<>();
            languageColors = new HashMap<>();
            languageColors.put(GraphColorMap.GRAY, "<none>");
            buildSampleData();
        }

        public String background1() {
            return "#eeeeee";
        }

        public String background2() {
            return "#eeeeb0";
        }

        public void generateSVG() {
            initSVG();
            svg.include(css());
            svg.include(script());
            svg.include(drawCanvas(0.0, 0.0));
            svg.close();
        }

        public void addComponent(SVGComponent component) {
            components.add(component);
        }

        public void initSVG() {
            svg.header(width(), height());
        }

        public String css() {
            StringBuilder css = new StringBuilder();
            css.append("<defs>\n");
            css.append("    <linearGradient id=\"background\" y1=\"0\" y2=\"1\" x1=\"0\" x2=\"0\" >\n");
            css.append(String.format(Locale.ROOT, "        <stop stop-color=\"%s\" offset=\"5%%\" />\n", background1()));
            css.append(String.format(Locale.ROOT, "        <stop stop-color=\"%s\" offset=\"95%%\" />\n", background2()));
            css.append("    </linearGradient>\n");
            css.append("</defs>\n");
            css.append("<style type=\"text/css\">\n");

            for (SVGComponent component : components) {
                css.append(component.css());
            }
            css.append("</style>\n");
            return css.toString();
        }

        public String script() {
            StringBuilder result = new StringBuilder();
            result.append("<script type=\"text/ecmascript\">\n<![CDATA[\n");
            result.append(getResource("graphowner.js"));
            result.append("'use strict';\n");
            result.append(String.format(Locale.ROOT, "var fontSize = %s;\n", fontSize()));
            result.append(String.format(Locale.ROOT, "var fontWidth = %s;\n", fontWidth()));
            result.append(samples());
            result.append(resizeFunction());
            result.append(initFunction("evt"));
            result.append(searchFunction("term"));
            result.append(resetSearchFunction());
            result.append(colorChangeFunction());
            for (SVGComponent component : components) {
                result.append(component.script());
            }
            result.append("]]>\n</script>");
            return result.toString();
        }

        private static final class Task {
            final ProfilerNode<CPUSampler.Payload> sample;
            final JSONArray siblings;
            final long x;
            final int parent;

            Task(ProfilerNode<CPUSampler.Payload> sample, JSONArray siblings, long x, int parent) {
                this.sample = sample;
                this.siblings = siblings;
                this.x = x;
                this.parent = parent;
            }
        }

        private void buildSampleData() {
            ArrayDeque<Task> tasks = new ArrayDeque<>();
            JSONObject root = new JSONObject();
            sampleData.put(root);
            root.put("k", indexForSampleKey("Thread: <top>", "<none>", 0));
            root.put("id", sampleId++);
            root.put("i", 0);
            root.put("c", 0);
            root.put("x", 0);
            root.put("l", GraphColorMap.GRAY.ordinal());
            long totalSamples = 0;
            JSONArray children = new JSONArray();
            for (CPUSamplerData value : data) {
                for (Map.Entry<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> node : value.getThreadData().entrySet()) {
                    Thread thread = node.getKey();
                    // Output the thread node itself...
                    // Optput the samples under that node...
                    totalSamples += threadSampleData(thread, node.getValue(), tasks, children, totalSamples);
                }
            }
            root.put("h", totalSamples);
            root.put("s", children);

            Task task = tasks.poll();
            while (task != null) {
                processSample(task, tasks);
                task = tasks.poll();
            }
            buildColorData();
            buildRecursiveData();
        }

        private static final class SampleKey {
            int nameId;
            int sourceId;
            int sourceLine;

            SampleKey(int nameId, int sourceId, int sourceLine) {
                this.nameId = nameId;
                this.sourceId = sourceId;
                this.sourceLine = sourceLine;
            }

            @Override
            public int hashCode() {
                final int prime = 31;
                int result = 1;
                result = prime * result + nameId;
                result = prime * result + sourceId;
                result = prime * result + sourceLine;
                return result;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                SampleKey other = (SampleKey) obj;
                if (nameId != other.nameId) {
                    return false;
                }
                if (sourceId != other.sourceId) {
                    return false;
                }
                if (sourceLine != other.sourceLine) {
                    return false;
                }
                return true;
            }
        }

        private int indexForSampleKey(String name, SourceSection section) {
            final String sourceName;
            int sourceLine = 0;
            if (section != null && section.isAvailable()) {
                sourceLine = section.getStartLine();
                Source source = section.getSource();
                if (source != null) {
                    String path = source.getPath();
                    if (path != null) {
                        sourceName = path;
                    } else {
                        sourceName = source.getName();
                    }
                } else {
                    sourceName = "<none>";
                }
            } else {
                sourceName = "<none>";
            }
            return indexForSampleKey(name, sourceName, sourceLine);
        }

        private int indexForSampleKey(String name, String sourceFile, int line) {
            int nameId = nameHash.computeIfAbsent(name, k -> {
                sampleNames.put(k);
                return nameCounter++;
            });
            int sourceId = sourceHash.computeIfAbsent(sourceFile, k -> {
                sourceNames.put(k);
                return sourceCounter++;
            });
            return keyHash.computeIfAbsent(new SampleKey(nameId, sourceId, line), k -> {
                sampleKeys.add(k);
                JSONArray jsonKey = new JSONArray();
                jsonKey.put(k.nameId);
                jsonKey.put(k.sourceId);
                jsonKey.put(k.sourceLine);
                sampleJsonKeys.put(jsonKey);
                return keyCounter++;
            });
        }

        private HashMap<Integer, HashMap<SampleKey, JSONObject>> recursiveChildMap = new HashMap<>();

        private HashMap<SampleKey, JSONObject> childrenByKeyForSample(JSONObject sample) {
            return recursiveChildMap.computeIfAbsent(sample.getInt("id"), k -> {
                HashMap<SampleKey, JSONObject> childMap = new HashMap<>();
                return childMap;
            });
        }

        private static final class RecursivePositionTask {
            JSONObject sample;
            long x;

            RecursivePositionTask(JSONObject sample, long x) {
                this.sample = sample;
                this.x = x;
            }
        }

        private void buildRecursiveData() {
            // Merge all the collapsed recursive relationships and hit counts
            for (Object sample : sampleData) {
                calculateRecursiveData((JSONObject) sample);
            }
            // Work out the collapsed recursive x positions.
            ArrayDeque<RecursivePositionTask> tasks = new ArrayDeque<>();
            RecursivePositionTask task = new RecursivePositionTask(sampleDataForId(0), 0);
            while (task != null) {
                task.sample.put("rx", task.x);
                if (task.sample.has("rs")) {
                    long offset = task.x + task.sample.getInt("ri") + task.sample.getInt("rc");
                    for (Object childId : task.sample.getJSONArray("rs")) {
                        JSONObject child = sampleDataForId((Integer) childId);
                        tasks.add(new RecursivePositionTask(child, offset));
                        offset += child.getInt("rh");
                    }
                }
                task = tasks.poll();
            }
        }

        private void calculateRecursiveData(JSONObject sample) {
            if (sample.has("p")) {
                JSONObject parent = sampleDataForId(sample.getInt("p"));
                final JSONObject owner;
                if (parent.has("ro")) {
                    owner = sampleDataForId(parent.getInt("ro"));
                } else {
                    owner = parent;
                }
                if (parent.getInt("k") == sample.getInt("k")) {
                    mergeCounts(owner, sample, true);
                } else {
                    HashMap<SampleKey, JSONObject> siblings = childrenByKeyForSample(owner);
                    SampleKey key = sampleKeys.get(sample.getInt("k"));
                    if (siblings.containsKey(key)) {
                        JSONObject sibling = siblings.get(key);
                        mergeCounts(sibling, sample, false);
                    } else {
                        siblings.put(key, sample);
                        addRecursiveChild(owner, sample);
                    }
                    sample.put("rp", owner.getInt("id"));
                }
            } else {
                // The root sample needs rc, ri, and rh set.
                sample.put("rc", sample.getInt("c"));
                sample.put("ri", sample.getInt("i"));
                sample.put("rh", sample.getInt("h"));
            }
        }

        private static void addRecursiveChild(JSONObject sample, JSONObject child) {
            final JSONArray children;
            if (!sample.has("rs")) {
                children = new JSONArray();
                sample.put("rs", children);
            } else {
                children = sample.getJSONArray("rs");
            }
            children.put(child.getInt("id"));
            child.put("rc", child.getInt("c"));
            child.put("ri", child.getInt("i"));
            child.put("rh", child.getInt("h"));
        }

        private static void mergeCounts(JSONObject a, JSONObject b, boolean child) {
            int aRI = a.has("ri") ? a.getInt("ri") : a.getInt("i");
            int aRC = a.has("rc") ? a.getInt("rc") : a.getInt("c");
            int aRH = a.has("rh") ? a.getInt("rh") : a.getInt("h");
            aRI += b.getInt("i");
            aRC += b.getInt("c");
            // Siblings should have their hit counts combined, but
            // children should not increase their parent's hit count.
            if (!child) {
                aRH += b.getInt("h");
            }
            a.put("ri", aRI);
            a.put("rc", aRC);
            a.put("rh", aRH);
            b.put("ro", a.getInt("id"));
        }

        private long threadSampleData(Thread thread, Collection<ProfilerNode<CPUSampler.Payload>> samples, ArrayDeque<Task> tasks, JSONArray siblings, long x) {
            JSONObject threadSample = new JSONObject();
            long totalSamples = 0;
            int id = sampleId++;
            threadSample.put("k", indexForSampleKey(thread.getName(), "<none>", 0));
            threadSample.put("id", id);
            threadSample.put("p", 0);
            sampleData.put(threadSample);
            threadSample.put("i", 0);
            threadSample.put("c", 0);
            threadSample.put("l", GraphColorMap.GRAY.ordinal());
            threadSample.put("x", x);
            JSONArray children = new JSONArray();
            long childCount = 0;
            for (ProfilerNode<CPUSampler.Payload> sample : samples) {
                tasks.addLast(new Task(sample, children, totalSamples + x, id));
                totalSamples += sample.getPayload().getHitCount();
                childCount++;
            }
            threadSample.put("h", totalSamples);
            if (childCount > 0) {
                threadSample.put("s", children);
            }
            siblings.put(threadSample.getInt("id"));
            return totalSamples;
        }

        private String samples() {
            StringBuilder result = new StringBuilder();

            result.append("var profileNames = ");
            result.append(sampleNames.toString());
            result.append(";\n");
            result.append("var sourceNames = ");
            result.append(sourceNames.toString());
            result.append(";\n");
            result.append("var sampleKeys = ");
            result.append(sampleJsonKeys.toString());
            result.append(";\n");
            result.append("var profileData = ");
            result.append(sampleData.toString());
            result.append(";\n");

            result.append("var colorData = ");
            JSONArray colors = new JSONArray();
            for (GraphColorMap cm : GraphColorMap.values()) {
                if (colorsForKeys.containsKey(cm)) {
                    JSONObject map = new JSONObject();
                    for (Map.Entry<SampleKey, String> e : colorsForKeys.get(cm).entrySet()) {
                        map.put(keyHash.get(e.getKey()).toString(), e.getValue());
                    }
                    colors.put(map);
                } else {
                    colors.put(new JSONObject());
                }
            }
            result.append(colors.toString());
            result.append(";\n");

            result.append("var languageNames = ");
            JSONArray languageNames = new JSONArray();
            for (GraphColorMap cm : GraphColorMap.values()) {
                if (languageColors.containsKey(cm)) {
                    languageNames.put(languageColors.get(cm));
                } else {
                    languageNames.put("");
                }
            }
            result.append(languageNames.toString());
            result.append(";\n");

            return result.toString();
        }

        private void processSample(Task task, ArrayDeque<Task> tasks) {
            JSONObject result = new JSONObject();
            result.put("k", indexForSampleKey(task.sample.getRootName(), task.sample.getSourceSection()));
            int id = sampleId++;
            result.put("id", id);
            result.put("p", task.parent);
            sampleData.put(result);
            result.put("i", task.sample.getPayload().getTierSelfCount(0));
            int compiledSelfHits = 0;
            for (int i = 1; i < task.sample.getPayload().getNumberOfTiers(); i++) {
                compiledSelfHits += task.sample.getPayload().getTierSelfCount(i);
            }
            result.put("c", compiledSelfHits);
            result.put("h", task.sample.getPayload().getHitCount());
            result.put("l", colorMapForLanguage(task.sample).ordinal());
            result.put("x", task.x);
            JSONArray children = new JSONArray();
            int childCount = 0;
            long offset = task.sample.getPayload().getSelfHitCount();
            for (ProfilerNode<CPUSampler.Payload> child : task.sample.getChildren()) {
                tasks.addLast(new Task(child, children, task.x + offset, id));
                offset += child.getPayload().getHitCount();
                childCount++;
            }
            if (childCount > 0) {
                result.put("s", children);
            }
            task.siblings.put(result.getInt("id"));
        }

        protected JSONObject sampleDataForId(int id) {
            return (JSONObject) sampleData.get(id);
        }

        protected String nameForKeyId(int keyId) {
            SampleKey key = sampleKeys.get(keyId);
            return sampleNames.getString(key.nameId);
        }

        private void buildColorData() {
            for (Object sample : sampleData) {
                buildColorDataForSample((JSONObject) sample);
            }
        }

        private void buildColorDataForSample(JSONObject sample) {
            colorForKey(sample.getInt("k"), GraphColorMap.FLAME);
            colorForKey(sample.getInt("k"), GraphColorMap.values()[sample.getInt("l")]);
        }

        public String initFunction(String argName) {
            StringBuilder result = new StringBuilder();
            result.append(String.format(Locale.ROOT, "function init(%s) {\n", argName));
            for (SVGComponent component : components) {
                result.append(component.initFunction(argName));
            }
            result.append("resize();\n");
            result.append("}\n");
            return result.toString();
        }

        public String resizeFunction() {
            StringBuilder result = new StringBuilder();
            result.append("function resize() {\n");
            result.append("owner_resize(document.firstElementChild.clientWidth);\n");
            for (SVGComponent component : components) {
                result.append(component.resizeFunction());
            }
            result.append("}\n");
            result.append("window.onresize = resize;\n");
            return result.toString();
        }

        public String searchFunction(String argName) {
            StringBuilder result = new StringBuilder();
            result.append(String.format(Locale.ROOT, "var searchColor = \"%s\";\n", searchColor()));
            result.append(getResource("search.js"));

            result.append(String.format(Locale.ROOT, "function search(%s) {\n", argName));
            for (SVGComponent component : components) {
                result.append(component.searchFunction(argName));
            }
            result.append("}\n");
            return result.toString();
        }

        public String resetSearchFunction() {
            StringBuilder result = new StringBuilder();
            result.append("    function reset_search() {\n");
            for (SVGComponent component : components) {
                result.append(component.resetSearchFunction());
            }
            result.append("}\n");
            return result.toString();
        }

        private String colorChangeFunction() {
            StringBuilder result = new StringBuilder();
            result.append(getResource("color_change.js"));

            return result.toString();
        }

        public String drawCanvas(double x, double y) {
            double offset = y;
            StringBuilder canvas = new StringBuilder();
            for (SVGComponent component : components) {
                canvas.append(component.drawCanvas(x, offset));
                offset = offset + component.height();
            }
            return canvas.toString();
        }

        public Map<SampleKey, String> colorsForType(GraphColorMap type) {
            if (colorsForKeys.containsKey(type)) {
                return colorsForKeys.get(type);
            } else {
                Map<SampleKey, String> colors = new HashMap<>();
                colorsForKeys.put(type, colors);
                return colors;
            }
        }

        public String colorForKey(int keyId, GraphColorMap type) {
            SampleKey key = sampleKeys.get(keyId);
            Map<SampleKey, String> colors = colorsForType(type);
            if (colors.containsKey(key)) {
                return colors.get(key);
            }

            double v1;
            double v2;
            double v3;
            int r = 0;
            int g = 9;
            int b = 0;
            v1 = random.nextDouble();
            v2 = random.nextDouble();
            v3 = random.nextDouble();

            switch (type) {
                case FLAME:
                    r = (int) (200 + (35 * v3));
                    g = (int) (100 + (100 * v1));
                    b = (int) (30 + (50 * v2));
                    break;
                case RED:
                    r = (int) (200 + (55 * v1));
                    g = (int) (80 * v1);
                    b = g;
                    break;
                case ORANGE:
                    r = (int) (190 + (65 * v1));
                    g = (int) (90 + (65 * v1));
                    b = 0;
                    break;
                case YELLOW:
                    r = (int) (175 + (55 * v1));
                    g = r;
                    b = (int) (50 + (20 * v1));
                    break;
                case GREEN:
                    g = (int) (200 + (55 * v1));
                    r = (int) (80 * v1);
                    b = r;
                    break;
                case AQUA:
                    r = (int) (50 + (60 * v1));
                    g = (int) (165 + (55 * v1));
                    b = (int) (165 + (55 * v1));
                    break;
                case BLUE:
                    r = (int) (80 * v1);
                    g = r;
                    b = (int) (200 + (55 * v1));
                    break;
                case PURPLE:
                    r = (int) (190 + (65 * v1));
                    g = (int) (80 + (60 * v1));
                    b = r;
                    break;
                case GRAY:
                    r = (int) (175 + (55 * v1));
                    g = r;
                    b = r;
            }
            String color = allocateColor(r, g, b);
            colors.put(key, color);
            return color;
        }

        public GraphColorMap colorMapForLanguage(ProfilerNode<CPUSampler.Payload> sample) {
            String language = "<none>";
            SourceSection section = sample.getSourceSection();
            if (section != null) {
                Source source = section.getSource();
                if (source != null) {
                    language = source.getLanguage();
                }
            }
            GraphColorMap color = GraphColorMap.GRAY;
            if (languageColors.containsValue(language)) {
                for (Map.Entry<GraphColorMap, String> entry : languageColors.entrySet()) {
                    if (language.equals(entry.getValue())) {
                        color = entry.getKey();
                        break;
                    }
                }
            } else {
                for (GraphColorMap key : GraphColorMap.values()) {
                    if (key == GraphColorMap.FLAME) {
                        continue;
                    }
                    if (!languageColors.containsKey(key)) {
                        color = key;
                        languageColors.put(key, language);
                        break;
                    }
                }
            }
            return color;
        }

        public String searchColor() {
            return "rgb(255, 0, 255)";
        }

        public double width() {
            return components.stream().mapToDouble(c -> c.width()).reduce((a, b) -> Math.max(a, b)).orElse(0.0);
        }

        public double height() {
            return components.stream().mapToDouble(c -> c.height()).reduce((a, b) -> a + b).orElse(0.0);
        }

        public String fontName() {
            return "Verdana";
        }

        public double fontSize() {
            return 12.0;
        }

        public double fontWidth() {
            return 0.5;
        }

        public String abbreviate(String fullText, double width) {
            String text;
            int textLength = (int) (width / (fontSize() * fontWidth()));
            if (textLength > fullText.length()) {
                text = fullText;
            } else if (textLength >= 3) {
                text = fullText.substring(0, textLength - 2) + "...";
            } else {
                text = "";
            }
            return text;
        }

        public String getResource(String name) {
            StringBuilder resource = new StringBuilder();
            try (
                            InputStream stream = SVGHistogram.class.getResourceAsStream("resources/" + name);
                            Scanner scanner = new Scanner(stream);) {
                while (scanner.hasNextLine()) {
                    resource.append(scanner.nextLine());
                    resource.append('\n');
                }
            } catch (IOException e) {
                throw new Error("Resources are missing from this build.");
            }
            return resource.toString();
        }
    }

    private static class SVGFlameGraph implements SVGComponent {

        private final GraphOwner owner;
        private final double bottomPadding;
        private final double topPadding;
        private final int maxDepth;
        private final double widthPerTime;
        private final long sampleCount;

        SVGFlameGraph(GraphOwner owner) {
            this.owner = owner;
            this.bottomPadding = 2 * owner.fontSize() + 10;
            this.topPadding = 3 * owner.fontSize();
            this.sampleCount = owner.sampleDataForId(0).getInt("h");
            widthPerTime = (width() - 2 * XPAD) / sampleCount;
            maxDepth = maxDepth(owner.sampleDataForId(0));
        }

        private int maxDepth(JSONObject samples) {
            double width = sampleWidth(samples);
            if (width < MINWIDTH) {
                return 0;
            } else {
                int childDepth = 0;
                if (samples.has("rs")) {
                    for (Object child : samples.getJSONArray("rs")) {
                        childDepth = Integer.max(childDepth, maxDepth(owner.sampleDataForId((Integer) child)));
                    }
                }
                return childDepth + 1;
            }
        }

        public String css() {
            return ".func_g:hover { stroke:black; stroke-width:0.5; cursor:pointer; }";
        }

        public String script() {
            StringBuilder script = new StringBuilder();
            script.append(String.format(Locale.ROOT, "var xpad = %s;\nvar fg_width = 1200;\nvar fg_bottom_padding = %s;\nvar fg_min_width = %s;\n", XPAD, bottomPadding, MINWIDTH));
            script.append(String.format(Locale.ROOT, "var fg_frameheight = %s;\nvar fg_top_padding = %s;", FRAMEHEIGHT, topPadding));
            script.append(owner.getResource("flamegraph.js"));
            return script.toString();
        }

        public String drawCanvas(double x, double y) {
            StringBuilder output = new StringBuilder();
            Map<String, String> svgattr = new HashMap<>();
            svgattr.put("x", Double.toString(x));
            svgattr.put("y", Double.toString(y));
            svgattr.put("wdith", Double.toString(width()));
            svgattr.put("height", Double.toString(height()));
            svgattr.put("viewBox", String.format(Locale.ROOT, "0.0 -%f %f %f", height(), width(), height()));
            output.append(startSubDrawing(svgattr));
            Map<String, String> attr = new HashMap<>();
            Map<String, String> canvasAttr = new HashMap<>();
            attr.put("id", "flamegraph");
            canvasAttr.put("id", "fg_canvas");
            output.append(startGroup(attr));
            output.append(fillRectangle(0, -height(), width(), height(), "url(#background)", "", canvasAttr));
            output.append(drawTree());
            output.append(ttfString(black(), owner.fontName(), owner.fontSize(), XPAD, -(bottomPadding / 2),
                            " ", "", "id=\"details\""));
            output.append(ttfString(black(), owner.fontName(), owner.fontSize(), width() - XPAD - 100, -(bottomPadding / 2),
                            " ", "", "id=\"matched\" onclick=\"search_prompt()\""));
            output.append(endGroup(attr));
            output.append(endSubDrawing());

            // We put the title and top buttons outside the main group
            // so we won't need to move them if zooming in or out
            // changes the max height of the graph.

            output.append(ttfString(black(), owner.fontName(), owner.fontSize() + 5, width() / 2, owner.fontSize() * 2,
                            "Flamegraph", "middle", "id=\"fg_title\""));
            output.append(ttfString(black(), owner.fontName(), owner.fontSize(), width() / 2, owner.fontSize() * 3,
                            "Press \"?\" for legend and help.", "middle", "id=\"fg_help\""));
            output.append(ttfString(black(), owner.fontName(), owner.fontSize(), XPAD, owner.fontSize() * 2,
                            "Reset zoom", "", "id=\"unzoom\" onclick=\"unzoom()\" style=\"opacity:0.1;cursor:pointer\""));
            output.append(ttfString(black(), owner.fontName(), owner.fontSize(), width() - XPAD, owner.fontSize() * 2,
                            "Search", "end", "id=\"search\"  onclick=\"search_prompt()\" onmouseover=\"fg_searchover()\" onmouseout=\"fg_searchout()\""));
            return output.toString();
        }

        private String drawTree() {
            StringBuilder output = new StringBuilder();
            double baseY = -FRAMEHEIGHT - bottomPadding;
            output.append(drawSamples(baseY, owner.sampleDataForId(0)));
            return output.toString();
        }

        private double sampleWidth(JSONObject sample) {
            long hitCount = sample.getInt("rh");
            return widthPerTime * hitCount;
        }

        private double sampleX(JSONObject sample) {
            long x = sample.getInt("rx");
            return widthPerTime * x + XPAD;
        }

        private static final class DrawTask {
            final JSONObject sample;
            final int depth;

            DrawTask(JSONObject sample, int depth) {
                this.sample = sample;
                this.depth = depth;
            }
        }

        private String drawSamples(double y, JSONObject root) {
            ArrayDeque<DrawTask> tasks = new ArrayDeque<>();

            StringBuilder output = new StringBuilder();
            DrawTask task = new DrawTask(root, 0);
            while (task != null) {
                output.append(drawSample(y, task, tasks));
                task = tasks.poll();
            }
            return output.toString();
        }

        private String drawSample(double baseY, DrawTask task, ArrayDeque<DrawTask> tasks) {
            // Redesign this to use a task deque.
            StringBuilder output = new StringBuilder();
            JSONObject sample = task.sample;
            int depth = task.depth;
            double y = baseY - depth * FRAMEHEIGHT;
            double width = sampleWidth(sample);
            double x = sampleX(sample);
            if (width < MINWIDTH) {
                return "";
            }
            HashMap<String, String> groupAttrs = new HashMap<>();
            int id = sample.getInt("id");
            String fullText = owner.nameForKeyId(sample.getInt("k"));
            GraphOwner.SampleKey key = owner.sampleKeys.get(sample.getInt("k"));

            groupAttrs.put("class", "func_g");
            groupAttrs.put("onclick", id == 0 ? "unzoom()" : "zoom(this)");
            groupAttrs.put("onmouseover", "s(this)");
            groupAttrs.put("onmouseout", "c(this)");
            groupAttrs.put("id", "f_" + Integer.toString(id));
            StringBuilder title = new StringBuilder();
            title.append(fullText);
            title.append("\n");
            int interpreted = sample.getInt("i");
            int compiled = sample.getInt("c");
            int total = sample.getInt("h");
            double percent = 100.0 * (compiled + interpreted) / sampleCount;
            double totalPercent = 100.0 * total / sampleCount;
            title.append(String.format(Locale.ROOT, "Self samples: %d (%.2f%%)\n", interpreted + compiled, percent));
            title.append(String.format(Locale.ROOT, "total samples:  %d (%.2f%%)\n", total, totalPercent));
            title.append(String.format(Locale.ROOT, "Source location: %s:%d\n", owner.sourceNames.get(key.sourceId), key.sourceLine));
            groupAttrs.put("title", escape(title.toString()));
            output.append(startGroup(groupAttrs));

            HashMap<String, String> rectAttrs = new HashMap<>();

            output.append(fillRectangle(x, y, width, FRAMEHEIGHT, owner.colorForKey(sample.getInt("k"), GraphColorMap.FLAME), "rx=\"2\" ry=\"2\"", rectAttrs));

            output.append(ttfString(black(), owner.fontName(), owner.fontSize(), x + 3, y - 5 + FRAMEHEIGHT, owner.abbreviate(fullText, width), null, ""));
            output.append(endGroup(groupAttrs));
            if (sample.has("rs")) {
                JSONArray children = sample.getJSONArray("rs");
                for (Object childId : children) {
                    JSONObject child = owner.sampleDataForId((Integer) childId);
                    tasks.add(new DrawTask(child, depth + 1));
                }
            }
            return output.toString();
        }

        public double width() {
            return IMAGEWIDTH;
        }

        public double height() {
            return FRAMEHEIGHT * maxDepth + topPadding + bottomPadding;
        }

        public String resizeFunction() {
            return "fg_resize(document.firstElementChild.clientWidth);\n";
        }

        public String initFunction(String argName) {
            return String.format(Locale.ROOT, "fg_init(%s)\n", argName);
        }

        public String searchFunction(String argName) {
            return String.format(Locale.ROOT, "fg_search(%s)\n", argName);
        }

        public String resetSearchFunction() {
            return "fg_reset_search()\n";
        }
    }

    private static double XPAD = 10;
    private static double FRAMEHEIGHT = 16;

    private static class SVGHistogram implements SVGComponent {

        private final GraphOwner owner;
        private final double titlePadding;
        private final double bottomPadding;
        private final double timeMax;
        private final double widthPerTime;
        private final List<JSONObject> histogram;
        private final long sampleCount;

        SVGHistogram(GraphOwner owner) {
            this.owner = owner;

            this.titlePadding = owner.fontSize() * 3.0;
            this.bottomPadding = owner.fontSize() * 2 + 10.0;
            histogram = buildHistogram(owner.sampleDataForId(0));
            timeMax = histogram.get(0).getInt("i") + histogram.get(0).getInt("c");
            widthPerTime = (width() - 2 * XPAD) / timeMax;
            double minTime = MINWIDTH / widthPerTime;
            long count = 0;
            for (JSONObject bar : histogram) {
                count += bar.getInt("i") + bar.getInt("c");
            }
            sampleCount = count;
            histogram.removeIf(x -> (x.getInt("i") + x.getInt("c")) < minTime);
        }

        private List<JSONObject> buildHistogram(JSONObject sample) {
            Map<GraphOwner.SampleKey, JSONObject> bars = new HashMap<>();
            ArrayDeque<JSONObject> samples = new ArrayDeque<>();

            JSONObject next = sample;
            while (next != null) {
                buildHistogram(next, samples, bars);
                next = samples.poll();
            }

            ArrayList<JSONObject> lines = new ArrayList<>(bars.values());
            Collections.sort(lines, (a, b) -> Integer.compare(b.getInt("i") + b.getInt("c"), a.getInt("i") + a.getInt("c")));
            return lines;
        }

        private void buildHistogram(JSONObject sample, ArrayDeque<JSONObject> samples, Map<GraphOwner.SampleKey, JSONObject> bars) {
            JSONObject bar = bars.computeIfAbsent(owner.sampleKeys.get(sample.getInt("k")),
                            k -> {
                                JSONObject entry = new JSONObject();
                                entry.put("id", sample.getInt("id"));
                                entry.put("i", 0);
                                entry.put("c", 0);
                                entry.put("l", sample.getInt("l"));
                                entry.put("k", sample.getInt("k"));
                                return entry;
                            });
            bar.put("i", bar.getInt("i") + sample.getInt("i"));
            bar.put("c", bar.getInt("c") + sample.getInt("c"));
            if (sample.has("s")) {
                JSONArray children = sample.getJSONArray("s");
                for (Object childId : children) {
                    samples.add(owner.sampleDataForId((Integer) childId));
                }
            }
        }

        public String css() {
            return ".func_h:hover { stroke:black; stroke-width:0.5; cursor:pointer; }";
        }

        public String script() {
            StringBuilder script = new StringBuilder();
            script.append(String.format(Locale.ROOT, "var h_width = %s;\n", width()));
            script.append(String.format(Locale.ROOT, "var h_minwidth = %s;\n", MINWIDTH));
            script.append(String.format(Locale.ROOT, "var h_top_padding = %s;\n", titlePadding));
            script.append(String.format(Locale.ROOT, "var h_bottom_padding = %s;\n", bottomPadding));
            script.append(String.format(Locale.ROOT, "var h_frameheight = %s;\n", FRAMEHEIGHT));
            script.append("var histogramData = ");
            JSONArray data = new JSONArray();
            for (JSONObject bar : histogram) {
                data.put(bar);
            }
            script.append(data.toString());
            script.append(";\n");
            script.append(owner.getResource("histogram.js"));
            return script.toString();
        }

        public String drawCanvas(double x, double y) {
            StringBuilder output = new StringBuilder();
            Map<String, String> svgattr = new HashMap<>();
            svgattr.put("x", Double.toString(x));
            svgattr.put("y", Double.toString(y));
            svgattr.put("wdith", Double.toString(width()));
            svgattr.put("height", Double.toString(height()));
            svgattr.put("viewBox", String.format(Locale.ROOT, "0.0 0.0 %f %f", width(), height()));
            output.append(startSubDrawing(svgattr));
            Map<String, String> attr = new HashMap<>();
            Map<String, String> canvasAttr = new HashMap<>();
            attr.put("id", "histogram");
            canvasAttr.put("id", "h_canvas");
            output.append(startGroup(attr));
            output.append(fillRectangle(0, 0, width(), height(), "url(#background)", "", canvasAttr));
            output.append(ttfString(black(), owner.fontName(), owner.fontSize() + 5, width() / 2, owner.fontSize() * 2, "Histogram", "middle", "id=\"h_title\""));

            for (int position = 0; position < histogram.size(); position++) {
                output.append(drawElement(histogram.get(position), position));
            }
            output.append(endGroup(attr));
            output.append(endSubDrawing());

            return output.toString();
        }

        private String drawElement(JSONObject bar, int position) {
            String name = owner.nameForKeyId(bar.getInt("k"));
            long selfTime = bar.getInt("c") + bar.getInt("i");
            double width = widthPerTime * selfTime;

            double x1 = XPAD;
            double y1 = titlePadding + position * FRAMEHEIGHT;
            StringBuilder output = new StringBuilder();
            if (width < MINWIDTH) {
                return "";
            }
            HashMap<String, String> groupAttrs = new HashMap<>();
            groupAttrs.put("class", "func_h");
            groupAttrs.put("id", "h_" + Integer.toString(position));
            groupAttrs.put("onclick", "h_highlight(this)");
            groupAttrs.put("onmouseover", "s(this)");
            groupAttrs.put("onmouseout", "c(this)");
            StringBuilder title = new StringBuilder();
            title.append("Function: ");
            title.append(name);
            title.append("\n");
            int interpreted = bar.getInt("i");
            int compiled = bar.getInt("c");
            title.append(String.format(Locale.ROOT, "%d samples (%d interpreted, %d compiled).\n", interpreted + compiled, interpreted, compiled));
            double percent = 100.0 * (compiled + interpreted) / sampleCount;
            title.append(String.format(Locale.ROOT, "%.2f%% of displayed samples.\n", percent));
            groupAttrs.put("title", escape(title.toString()));
            output.append(startGroup(groupAttrs));

            HashMap<String, String> rectAttrs = new HashMap<>();

            output.append(fillRectangle(x1, y1, width, FRAMEHEIGHT, owner.colorForKey(bar.getInt("k"), GraphColorMap.FLAME), "rx=\"2\" ry=\"2\"", rectAttrs));
            double afterWidth = IMAGEWIDTH - width - XPAD * 2;
            int textLength = (int) (width / (owner.fontSize() / owner.fontWidth()));
            int afterLength = (int) (afterWidth / (owner.fontSize() / owner.fontWidth()));

            double textX;
            String text = name;
            if (textLength > name.length()) {
                textX = x1 + 3;
            } else if (afterLength > name.length()) {
                textX = x1 + 3 + width;
            } else {
                if (textLength > afterLength) {
                    textX = x1 + 3;
                    text = owner.abbreviate(name, width);
                } else {
                    textX = x1 + 3 + width;
                    text = owner.abbreviate(name, afterWidth);
                }
            }

            output.append(ttfString(black(), owner.fontName(), owner.fontSize(), textX, y1 - 5 + FRAMEHEIGHT, text, null, ""));
            output.append(endGroup(groupAttrs));
            return output.toString();
        }

        public double width() {
            return IMAGEWIDTH;
        }

        public double height() {
            return histogram.size() * FRAMEHEIGHT + titlePadding + bottomPadding;
        }

        public String resizeFunction() {
            return "h_resize(document.firstElementChild.clientWidth);\n";
        }

        public String initFunction(String argName) {
            return String.format(Locale.ROOT, "h_init(%s)\n", argName);
        }

        public String searchFunction(String argName) {
            return String.format(Locale.ROOT, "h_search(%s)\n", argName);
        }

        public String resetSearchFunction() {
            return "h_reset_search()\n";
        }
    }
}
