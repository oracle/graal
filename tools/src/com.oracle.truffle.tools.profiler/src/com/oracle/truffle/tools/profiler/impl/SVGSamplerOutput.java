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

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.CPUSamplerData;
import com.oracle.truffle.tools.profiler.ProfilerNode;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import java.io.InputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

final class SVGSamplerOutput {

    public static void printSamplingFlameGraph(PrintStream out, Map<TruffleContext, CPUSamplerData> data) {
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
        output.append(String.format(
                        "<svg version=\"1.1\" width=\"%1$f\" height=\"%2$f\" onload=\"init(evt)\" viewBox=\"0 0 %1$f %2$f\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">\n",
                        width, height));
    }

    public void include(String data) {
        output.append(data);
    }

    public static String allocateColor(int r, int g, int b) {
        return String.format("rgb(%d, %d, %d)", r, g, b);
    }

    public static String startGroup(Map<String, String> attributes) {
        StringBuilder result = new StringBuilder();
        result.append("<g ");
        for (String key : new String[]{"class", "style", "onmouseover", "onmouseout", "onclick", "id"}) {
            if (attributes.containsKey(key)) {
                result.append(String.format("%s=\"%s\"", key, attributes.get(key)));
                result.append(" ");
            }
        }
        result.append(">\n");

        if (attributes.containsKey("g_extra")) {
            result.append(attributes.get("g_extra"));
        }

        if (attributes.containsKey("title")) {
            result.append(String.format("<title>%s</title>", attributes.get("title")));
        }

        if (attributes.containsKey("href")) {
            result.append(String.format("<a xlink:href=%s", attributes.get("href")));

            final String target;
            if (attributes.containsKey("target")) {
                target = attributes.get("target");
            } else {
                target = "_top";
            }
            result.append(String.format(" target=%s", target));
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
            result.append(String.format("%s=\"%s\"", e.getKey(), e.getValue()));
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
        result.append(String.format("<rect x=\"%f\" y=\"%f\" width=\"%f\" height=\"%f\" fill=\"%s\" %s", x1, y1, w, h, fill, extras));
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            result.append(String.format(" %s=\"%s\"", entry.getKey(), entry.getValue()));
        }
        result.append("/>\n");
        return result.toString();
    }

    public static String ttfString(String color, String font, double size, double x, double y, String text, String loc, String extras) {
        return String.format("<text text-anchor=\"%s\" x=\"%f\" y=\"%f\" font-size=\"%f\" font-family=\"%s\" fill=\"%s\" %s >%s</text>\n", loc == null ? "left" : loc, x, y, size, font, color,
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
        private final Map<TruffleContext, CPUSamplerData> data;
        private ArrayList<SVGComponent> components;
        private Random random = new Random();
        private Map<GraphColorMap, String> languageColors;
        private Map<GraphColorMap, Map<String, String>> colorsForNames = new HashMap<>();
        private int sampleId;
        public final Map<String, Integer> nameHash = new HashMap<>();
        public int nameCounter = 0;
        public final JSONArray sampleNames = new JSONArray();
        public final JSONObject sampleData = new JSONObject();

        GraphOwner(StringBuilder output, Map<TruffleContext, CPUSamplerData> data) {
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
            css.append(String.format("        <stop stop-color=\"%s\" offset=\"5%%\" />\n", background1()));
            css.append(String.format("        <stop stop-color=\"%s\" offset=\"95%%\" />\n", background2()));
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
            result.append(String.format("var fontSize = %s;\n", fontSize()));
            result.append(String.format("var fontWidth = %s;\n", fontWidth()));
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

        private void buildSampleData() {
            sampleData.put("n", nameCounter);
            nameHash.put("<top>", nameCounter++);
            sampleNames.put("<top>");
            sampleData.put("id", sampleId++);
            sampleData.put("i", 0);
            sampleData.put("c", 0);
            sampleData.put("x", 0);
            sampleData.put("l", GraphColorMap.GRAY.ordinal());
            long totalSamples = 0;
            JSONArray children = new JSONArray();
            for (CPUSamplerData value : data.values()) {
                for (Map.Entry<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> node : value.getThreadData().entrySet()) {
                    Thread thread = node.getKey();
                    // Output the thread node itself...
                    // Optput the samples under that node...
                    List<ProfilerNode<CPUSampler.Payload>> samples = new ArrayList<>(node.getValue());
                    children.put(threadSampelData(thread, samples, totalSamples));
                    for (ProfilerNode<CPUSampler.Payload> sample : samples) {
                        totalSamples += sample.getPayload().getHitCount();
                    }
                }
            }
            sampleData.put("h", totalSamples);
            sampleData.put("s", children);
            buildColorData(sampleData);
        }

        private JSONObject threadSampelData(Thread thread, List<ProfilerNode<CPUSampler.Payload>> samples, long x) {
            JSONObject result = new JSONObject();
            result.put("n", nameHash.computeIfAbsent(thread.getName(), k -> {
                sampleNames.put(thread.getName());
                return nameCounter++;
            }));
            result.put("id", sampleId++);
            result.put("i", 0);
            result.put("c", 0);
            result.put("l", GraphColorMap.GRAY.ordinal());
            result.put("x", x);
            long totalSamples = 0;
            JSONArray children = new JSONArray();
            for (ProfilerNode<CPUSampler.Payload> sample : samples) {
                children.put(sampleData(sample, totalSamples + x));
                totalSamples += sample.getPayload().getHitCount();
            }
            result.put("h", totalSamples);
            if (children.length() > 0) {
                result.put("s", children);
            }
            return result;
        }

        public String samples() {
            StringBuilder result = new StringBuilder();

            result.append("var profileNames = ");
            result.append(sampleNames.toString());
            result.append(";\n");
            result.append("var profileData = ");
            result.append(sampleData.toString());
            result.append(";\n");

            result.append("var colorData = ");
            JSONArray colors = new JSONArray();
            for (GraphColorMap cm : GraphColorMap.values()) {
                if (colorsForNames.containsKey(cm)) {
                    JSONObject map = new JSONObject();
                    for (Map.Entry<String, String> e : colorsForNames.get(cm).entrySet()) {
                        map.put(e.getKey(), e.getValue());
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

        public JSONObject sampleData(ProfilerNode<CPUSampler.Payload> sample, long x) {
            JSONObject result = new JSONObject();
            final int nameId = nameHash.computeIfAbsent(sample.getRootName(), k -> {
                sampleNames.put(sample.getRootName());
                return nameCounter++;
            });
            result.put("n", nameId);
            result.put("id", sampleId++);
            result.put("i", sample.getPayload().getTierSelfCount(0));
            int compiledSelfHits = 0;
            for (int i = 0; i < sample.getPayload().getNumberOfTiers(); i++) {
                compiledSelfHits += sample.getPayload().getTierSelfCount(i);
            }
            result.put("c", compiledSelfHits);
            result.put("h", sample.getPayload().getHitCount());
            result.put("l", colorMapForLanguage(sample).ordinal());
            result.put("x", x);
            JSONArray children = new JSONArray();
            long offset = sample.getPayload().getSelfHitCount();
            for (ProfilerNode<CPUSampler.Payload> child : sample.getChildren()) {
                children.put(sampleData(child, x + offset));
                offset += child.getPayload().getHitCount();
            }
            if (children.length() > 0) {
                result.put("s", children);
            }
            return result;
        }

        private JSONObject findSampleInTree(JSONObject tree, int id) {
            if (tree.getInt("id") == id) {
                return tree;
            } else if (tree.has("s")) {
                JSONArray children = tree.getJSONArray("s");
                JSONObject lastChild = null;
                for (Object c : children) {
                    JSONObject child = (JSONObject) c;
                    if (child.getInt("id") == id) {
                        return child;
                    } else if (child.getInt("id") > id) {
                        return findSampleInTree(lastChild, id);
                    } else {
                        lastChild = child;
                    }
                }
                return findSampleInTree(lastChild, id);
            }
            return null;
        }

        protected JSONObject sampleDataForId(int id) {
            return findSampleInTree(sampleData, id);
        }

        private void buildColorData(JSONObject sample) {
            colorForName(sample.getInt("n"), GraphColorMap.FLAME);
            colorForName(sample.getInt("n"), GraphColorMap.values()[sample.getInt("l")]);
            if (sample.has("s")) {
                for (Object child : sample.getJSONArray("s")) {
                    buildColorData((JSONObject) child);
                }
            }

        }

        public String initFunction(String argName) {
            StringBuilder result = new StringBuilder();
            result.append(String.format("function init(%s) {\n", argName));
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
            result.append("owner_resize(window.innerWidth);\n");
            for (SVGComponent component : components) {
                result.append(component.resizeFunction());
            }
            result.append("}\n");
            result.append("window.onresize = resize;\n");
            return result.toString();
        }

        public String searchFunction(String argName) {
            StringBuilder result = new StringBuilder();
            result.append(String.format("var searchColor = \"%s\";\n", searchColor()));
            result.append(getResource("search.js"));

            result.append(String.format("function search(%s) {\n", argName));
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

        public Map<String, String> colorsForType(GraphColorMap type) {
            if (colorsForNames.containsKey(type)) {
                return colorsForNames.get(type);
            } else {
                Map<String, String> colors = new HashMap<>();
                colorsForNames.put(type, colors);
                return colors;
            }
        }

        public String colorForName(int nameId, GraphColorMap type) {
            String name = sampleNames.getString(nameId);
            Map<String, String> colors = colorsForType(type);
            if (colors.containsKey(name)) {
                return colors.get(name);
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
            colors.put(name, color);
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
            this.sampleCount = owner.sampleData.getInt("h");
            widthPerTime = (width() - 2 * XPAD) / sampleCount;
            maxDepth = maxDepth(owner.sampleData);
        }

        private int maxDepth(JSONObject samples) {
            double width = sampleWidth(samples);
            if (width < MINWIDTH) {
                return 0;
            } else {
                int childDepth = 0;
                if (samples.has("s")) {
                    for (Object child : samples.getJSONArray("s")) {
                        childDepth = Integer.max(childDepth, maxDepth((JSONObject) child));
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
            script.append(String.format("var xpad = %s;\nvar fg_width = 1200;\nvar fg_bottom_padding = %s;\nvar fg_min_width = %s;\n", XPAD, bottomPadding, MINWIDTH));
            script.append(String.format("var fg_frameheight = %s;\nvar fg_top_padding = %s;", FRAMEHEIGHT, topPadding));
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
            svgattr.put("viewBox", String.format("0.0 -%f %f %f", height(), width(), height()));
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
                            "Press \"?\" for help", "middle", "id=\"fg_help\""));
            output.append(ttfString(black(), owner.fontName(), owner.fontSize(), XPAD, owner.fontSize() * 2,
                            "Reset zoom", "", "id=\"unzoom\" onclick=\"unzoom()\" style=\"opacity:0.1;cursor:pointer\""));
            output.append(ttfString(black(), owner.fontName(), owner.fontSize(), width() - XPAD, owner.fontSize() * 2,
                            "Search", "end", "id=\"search\"  onclick=\"search_prompt()\" onmouseover=\"fg_searchover()\" onmouseout=\"fg_searchout()\""));
            return output.toString();
        }

        private String drawTree() {
            StringBuilder output = new StringBuilder();
            double baseY = -FRAMEHEIGHT - bottomPadding;
            output.append(drawSample(baseY, owner.sampleData));
            return output.toString();
        }

        private double sampleWidth(JSONObject sample) {
            long hitCount = sample.getInt("h");
            return widthPerTime * hitCount;
        }

        private double sampleX(JSONObject sample) {
            long x = sample.getInt("x");
            return widthPerTime * x + XPAD;
        }

        private String drawSample(double y, JSONObject sample) {
            StringBuilder output = new StringBuilder();
            double width = sampleWidth(sample);
            double x = sampleX(sample);
            if (width < MINWIDTH) {
                return "";
            }
            HashMap<String, String> groupAttrs = new HashMap<>();
            int id = sample.getInt("id");
            String fullText = owner.sampleNames.getString(sample.getInt("n"));

            groupAttrs.put("class", "func_g");
            groupAttrs.put("onclick", id == 0 ? "unzoom()" : "zoom(this)");
            groupAttrs.put("onmouseover", "s(this)");
            groupAttrs.put("onmouseout", "c(this)");
            groupAttrs.put("id", "f_" + Integer.toString(id));
            StringBuilder title = new StringBuilder();
            title.append("Function: ");
            title.append(fullText);
            title.append("\n");
            int interpreted = sample.getInt("i");
            int compiled = sample.getInt("c");
            title.append(String.format("%d samples (%d interpreted, %d compiled).\n", interpreted + compiled, interpreted, compiled));
            double percent = 100.0 * (compiled + interpreted) / sampleCount;
            title.append(String.format("%.2f%% of displayed samples.\n", percent));
            groupAttrs.put("title", escape(title.toString()));
            output.append(startGroup(groupAttrs));

            HashMap<String, String> rectAttrs = new HashMap<>();

            output.append(fillRectangle(x, y, width, FRAMEHEIGHT, owner.colorForName(sample.getInt("n"), GraphColorMap.FLAME), "rx=\"2\" ry=\"2\"", rectAttrs));

            output.append(ttfString(black(), owner.fontName(), owner.fontSize(), x + 3, y - 5 + FRAMEHEIGHT, owner.abbreviate(fullText, width), null, ""));
            output.append(endGroup(groupAttrs));
            if (sample.has("s")) {
                JSONArray children = sample.getJSONArray("s");
                for (Object child : children) {
                    output.append(drawSample(y - FRAMEHEIGHT, (JSONObject) child));
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
            return "fg_resize(window.innerWidth);\n";
        }

        public String initFunction(String argName) {
            return String.format("fg_init(%s)\n", argName);
        }

        public String searchFunction(String argName) {
            return String.format("fg_search(%s)\n", argName);
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
            histogram = buildHistogram(owner.sampleData);
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
            Map<String, JSONObject> bars = new HashMap<>();
            buildHistogram(sample, bars);
            ArrayList<JSONObject> lines = new ArrayList<>(bars.values());
            Collections.sort(lines, (a, b) -> Integer.compare(b.getInt("i") + b.getInt("c"), a.getInt("i") + a.getInt("c")));
            return lines;
        }

        private void buildHistogram(JSONObject sample, Map<String, JSONObject> bars) {
            JSONObject bar = bars.computeIfAbsent(owner.sampleNames.getString(sample.getInt("n")),
                            k -> {
                                JSONObject entry = new JSONObject();
                                entry.put("id", sample.getInt("id"));
                                entry.put("i", 0);
                                entry.put("c", 0);
                                entry.put("l", sample.getInt("l"));
                                entry.put("n", sample.getInt("n"));
                                return entry;
                            });
            bar.put("i", bar.getInt("i") + sample.getInt("i"));
            bar.put("c", bar.getInt("c") + sample.getInt("c"));
            if (sample.has("s")) {
                JSONArray children = sample.getJSONArray("s");
                for (Object child : children) {
                    buildHistogram((JSONObject) child, bars);
                }
            }
        }

        public String css() {
            return ".func_h:hover { stroke:black; stroke-width:0.5; cursor:pointer; }";
        }

        public String script() {
            StringBuilder script = new StringBuilder();
            script.append(String.format("var h_width = %s;\n", width()));
            script.append(String.format("var h_minwidth = %s;\n", MINWIDTH));
            script.append(String.format("var h_top_padding = %s;\n", titlePadding));
            script.append(String.format("var h_bottom_padding = %s;\n", bottomPadding));
            script.append(String.format("var h_frameheight = %s;\n", FRAMEHEIGHT));
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
            svgattr.put("viewBox", String.format("0.0 0.0 %f %f", width(), height()));
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
            int nameId = owner.sampleDataForId(bar.getInt("id")).getInt("n");
            String name = owner.sampleNames.getString(nameId);
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
            title.append(String.format("%d samples (%d interpreted, %d compiled).\n", interpreted + compiled, interpreted, compiled));
            double percent = 100.0 * (compiled + interpreted) / sampleCount;
            title.append(String.format("%.2f%% of displayed samples.\n", percent));
            groupAttrs.put("title", escape(title.toString()));
            output.append(startGroup(groupAttrs));

            HashMap<String, String> rectAttrs = new HashMap<>();

            output.append(fillRectangle(x1, y1, width, FRAMEHEIGHT, owner.colorForName(nameId, GraphColorMap.FLAME), "rx=\"2\" ry=\"2\"", rectAttrs));
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
            return "h_resize(window.innerWidth);\n";
        }

        public String initFunction(String argName) {
            return String.format("h_init(%s)\n", argName);
        }

        public String searchFunction(String argName) {
            return String.format("h_search(%s)\n", argName);
        }

        public String resetSearchFunction() {
            return "h_reset_search()\n";
        }
    }
}
