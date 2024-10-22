/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.visualizer;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;

import jdk.graal.compiler.graphio.parsing.*;
import jdk.graal.compiler.graphio.parsing.model.*;

public class JSONExporter {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String GRAPH_TYPE = "graph_type";
    private static final String PART = "part";

    private static final String NODES = "nodes";
    private static final String EDGES = "edges";
    private static final String BLOCKS = "blocks";

    private static final String JAVA = "Java";

    private static final int JSON_SIZE_LIMIT = (int) Math.pow(2, 28); // ~270 MB per json file

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || !args[0].endsWith(".bgv") || args[0].contentEquals("-h") || args[0].contentEquals("--help")) {
            printHelp();
            return;
        }
        boolean addBlocks = false;
        List<String> graphNames = new ArrayList<>();
        if (args.length > 1) {
            int i = 1;
            while (i < args.length) {
                switch (args[i]) {
                    case "-b":
                        addBlocks = true;
                        break;
                    case "-n":
                        if (++i == args.length) {
                            printHelp();
                            return;
                        }
                        String gn = args[i];
                        if (gn.length() > 3 && (gn.charAt(0) == '"' || gn.charAt(0) == '\'')) {
                            gn = gn.substring(1, gn.length() - 1);
                        }
                        graphNames.add(gn.toLowerCase());
                        break;
                    default:
                        printHelp();
                        return;
                }
                i++;
            }
        }
        exportToJSON(args[0], graphNames, addBlocks);
    }

    private static void printHelp() {
        System.out.println("usage:");
        System.out.println("   mx igv-json <file.bgv> [-b][-n GRAPH_NAME]");
    }

    @SuppressWarnings("deprecation")
    private static void fillGraphsList(URL url, List<InputGraph> graphs) throws IOException {
        ModelBuilder mb = new ModelBuilder(new GraphDocument(), null) {
            @Override
            public InputGraph startGraph(int dumpId, String format, Object[] args) {
                InputGraph g = super.startGraph(dumpId, format, args);
                graphs.add(g);
                return g;
            }
        };
        new BinaryReader(new StreamSource(url.openStream()), mb).parse();
    }

    private static boolean isGraphNameAccepted(List<String> graphNames, String n) {
        for (String name : graphNames) {
            if (n.toLowerCase().contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static JSONHelper.JSONObjectBuilder createRoot(int dumpID, String graphType, String graphName) {
        JSONHelper.JSONObjectBuilder root = JSONHelper.object();
        root.add(ID, dumpID);
        root.add(NAME, graphName);
        root.add(GRAPH_TYPE, graphType);
        return root;
    }

    public static void exportToJSON(String in, List<String> graphNames, boolean addBlocks) throws IOException {
        URL url = new URL(new URL("file:"), in);
        List<InputGraph> graphs = new ArrayList<>();
        fillGraphsList(url, graphs);
        for (InputGraph graph : graphs) {
            if (graph.getProperties().size() <= 1) {
                // This is most likely an IR graph that is not needed
                continue;
            }
            String graphName = graph.getProperties().get(NAME, String.class);
            if (graphNames.size() > 0 && !isGraphNameAccepted(graphNames, graphName)) {
                continue;
            }
            String graphType = graph.getGraphType();
            int dumpID = graph.getDumpId();
            int currentPart = -1;
            JSONHelper.JSONObjectBuilder root = createRoot(dumpID, graphType, graphName);
            JSONHelper.JSONReadyObject nodesJson = JSONHelper.readyObject(0);
            for (InputNode node : graph.getNodes()) {
                if (nodesJson.getSize() > JSON_SIZE_LIMIT) {
                    root.add(NODES, nodesJson);
                    root.add(PART, ++currentPart);
                    write(in, graphType, graphName, currentPart, root.toString());
                    root = createRoot(dumpID, graphType, graphName);
                    nodesJson = JSONHelper.readyObject(0);
                }
                nodesJson.add(node.getId() + "", getNode(node));
            }
            root.add(NODES, nodesJson);

            JSONHelper.JSONReadyArray edgesJson = JSONHelper.readyArray(nodesJson.getSize());
            for (InputEdge e : graph.getEdges()) {
                if (edgesJson.getSize() > JSON_SIZE_LIMIT) {
                    root.add(EDGES, edgesJson);
                    root.add(PART, ++currentPart);
                    write(in, graphType, graphName, currentPart, root.toString());
                    root = createRoot(dumpID, graphType, graphName);
                    edgesJson = JSONHelper.readyArray(0);
                }
                edgesJson.add(getEdge(e));
            }
            root.add(EDGES, edgesJson);

            if (addBlocks) {
                /* Blocks can be reconstructed using nodes properties */
                JSONHelper.JSONReadyObject blocksJson = JSONHelper.readyObject(edgesJson.getSize());
                for (InputBlock b : graph.getBlocks()) {
                    if (edgesJson.getSize() > JSON_SIZE_LIMIT) {
                        root.add(BLOCKS, blocksJson);
                        root.add(PART, ++currentPart);
                        write(in, graphType, graphName, currentPart, root.toString());
                        root = createRoot(dumpID, graphType, graphName);
                        edgesJson = JSONHelper.readyArray(0);
                    }
                    blocksJson.add(b.getName(), getBlock(b));
                }
                root.add(BLOCKS, blocksJson);
            }

            if (currentPart > -1) {
                root.add(PART, ++currentPart);
            }
            write(in, graphType, graphName, currentPart, root.toString());
        }
    }

    private static void write(String in, String graphType, String graphName, int part, String content) throws IOException {
        String out = in.substring(0, in.lastIndexOf('.')) + '_';
        String outputPath = Paths.get(out + createFileName(graphType, graphName, part)).toString();
        System.out.println("Exporting to " + outputPath);
        try (PrintWriter writer = new PrintWriter(new File(outputPath))) {
            writer.write(content);
        }
    }

    private static JSONHelper.JSONObjectBuilder getNode(InputNode node) {
        JSONHelper.JSONObjectBuilder nodeJson = JSONHelper.object();
        for (Property<?> p : node.getProperties()) {
            Object o = p.getValue();
            if (o != null) {
                String key = p.getName();
                if (o instanceof LocationStackFrame) {
                    nodeJson.add(key, stacktrace(((LocationStackFrame) o)));
                } else {
                    nodeJson.add(key, o.toString());
                }
            }
        }
        return nodeJson;
    }

    public static JSONHelper.JSONArrayBuilder getEdge(InputEdge edge) {
        JSONHelper.JSONArrayBuilder e = JSONHelper.array();
        e.add(edge.getFrom()).add(edge.getTo());
        e.add(edge.getLabel()).add(edge.getType());
        return e;
    }

    public static JSONHelper.JSONObjectBuilder getBlock(InputBlock b) {
        JSONHelper.JSONObjectBuilder block = JSONHelper.object();
        JSONHelper.JSONArrayBuilder ids = JSONHelper.array();
        b.getNodes().forEach((bb) -> ids.add(bb.getId()));
        block.add(NODES, ids);
        JSONHelper.JSONArrayBuilder edges = JSONHelper.array();
        b.getSuccessors().forEach((ss) -> edges.add(ss.getName()));
        block.add(EDGES, edges);
        return block;
    }

    private static String createFileName(String graphType, String graphName, int part) {
        String p = (part == -1) ? "" : ("." + part);
        String gt = graphType.replaceAll("[^\\p{Alnum}]", "") + '_';
        String gn = graphName.replaceAll("[^\\p{Alnum}]", "_");
        return gt + gn + p + ".json";
    }

    private static JSONHelper.JSONObjectBuilder stacktrace(LocationStackFrame lsf) {
        HashMap<String, ArrayList<String>> langStack = new HashMap<>();
        HashSet<LocationStratum> memo = new HashSet<>();

        for (LocationStackFrame t = lsf; t != null; t = t.getParent()) {
            for (LocationStratum s : t.getStrata()) {
                if (!memo.contains(s)) {
                    memo.add(s);
                    String lang = s.language;
                    String methodName = t.getFullMethodName();
                    boolean isJava = lang.contentEquals(JAVA);
                    ArrayList<String> stack = langStack.getOrDefault(lang, new ArrayList<>());
                    methodName = (isJava && methodName != null) ? ("(" + methodName + ") ") : "";
                    stack.add(methodName + stratumToString(s));
                    langStack.put(lang, stack);
                }
            }
        }
        JSONHelper.JSONObjectBuilder st = JSONHelper.object();
        for (String s : langStack.keySet()) {
            JSONHelper.JSONArrayBuilder stack = JSONHelper.array();
            langStack.get(s).forEach(stack::add);
            st.add(s, stack);
        }

        return st;
    }

    private static String stratumToString(LocationStratum s) {
        String str = (s.uri != null ? s.uri : s.file) + ":" + s.line;
        // bci needed ?
        return str + stratumPosToString(s);
    }

    private static String stratumPosToString(LocationStratum s) {
        if (s.startOffset > -1 && s.endOffset > -1) {
            return "(" + s.startOffset + "-" + s.endOffset + ")";
        }
        if (s.startOffset > -1) {
            return "(" + s.startOffset + "-?)";
        }
        if (s.endOffset > -1) {
            return "(?-" + s.endOffset + ")";
        }
        return "";
    }
}

// modified version of com.oracle.truffle.api.utilities.JSONHelper

final class JSONHelper {

    private JSONHelper() {
    }

    private static String quote(CharSequence value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default: {
                    if (c < ' ') {
                        builder.append("\\u00");
                        builder.append(Character.forDigit((c >> 4) & 0xF, 16));
                        builder.append(Character.forDigit(c & 0xF, 16));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }

    public static JSONObjectBuilder object() {
        return new JSONObjectBuilder();
    }

    public static JSONReadyObject readyObject(int startSize) {
        return new JSONReadyObject(startSize);
    }

    public static JSONArrayBuilder array() {
        return new JSONArrayBuilder();
    }

    public static JSONReadyArray readyArray(int startSize) {
        return new JSONReadyArray(startSize);
    }

    public abstract static class JSONStringBuilder {

        private JSONStringBuilder() {
        }

        @Override
        public final String toString() {
            StringBuilder sb = new StringBuilder();
            appendTo(sb);
            return sb.toString();
        }

        protected abstract void appendTo(StringBuilder sb);

        protected static void appendValue(StringBuilder sb, Object value) {
            if (value instanceof JSONStringBuilder) {
                ((JSONStringBuilder) value).appendTo(sb);
            } else if (value instanceof Integer || value instanceof Boolean || value == null) {
                sb.append(value);
            } else {
                sb.append(quote(String.valueOf(value)));
            }
        }
    }

    public static final class JSONObjectBuilder extends JSONStringBuilder {
        private final Map<String, Object> contents = new LinkedHashMap<>();

        private JSONObjectBuilder() {
        }

        public JSONObjectBuilder add(String key, String value) {
            contents.put(key, value);
            return this;
        }

        public JSONObjectBuilder add(String key, Number value) {
            contents.put(key, value);
            return this;
        }

        public JSONObjectBuilder add(String key, JSONStringBuilder value) {
            contents.put(key, value);
            return this;
        }

        @Override
        protected void appendTo(StringBuilder sb) {
            sb.append("{");
            boolean comma = false;
            for (Map.Entry<String, Object> entry : contents.entrySet()) {
                if (comma) {
                    sb.append(", ");
                }
                sb.append(quote(entry.getKey()));
                sb.append(": ");
                appendValue(sb, entry.getValue());
                comma = true;
            }
            sb.append("}");
        }
    }

    public static final class JSONReadyObject extends JSONStringBuilder {
        private final Map<String, Object> contents = new LinkedHashMap<>();
        private int size;

        private JSONReadyObject(int startSize) {
            size = startSize + 2 /* '{' '}' */;
        }

        public JSONReadyObject add(String key, String value) {
            put(key, quote(value));
            return this;
        }

        public JSONReadyObject add(String key, Number value) {
            put(key, value.toString());
            return this;
        }

        public JSONReadyObject add(String key, Boolean value) {
            put(key, value.toString());
            return this;
        }

        public JSONReadyObject add(String key, JSONStringBuilder value) {
            put(key, value.toString());
            return this;
        }

        private void put(String key, String v) {
            size += key.length() + v.length() + 4 /* ", " ": " */;
            contents.put(key, v);
        }

        public int getSize() {
            return size;
        }

        @Override
        protected void appendTo(StringBuilder sb) {
            sb.append("{");
            boolean comma = false;
            for (Map.Entry<String, Object> entry : contents.entrySet()) {
                if (comma) {
                    sb.append(", ");
                }
                sb.append(quote(entry.getKey()));
                sb.append(": ");
                sb.append(entry.getValue());
                comma = true;
            }
            sb.append("}");
        }
    }

    public static final class JSONArrayBuilder extends JSONStringBuilder {
        private final List<Object> contents = new ArrayList<>();

        private JSONArrayBuilder() {
        }

        public JSONArrayBuilder add(String value) {
            contents.add(value);
            return this;
        }

        public JSONArrayBuilder add(Number value) {
            contents.add(value);
            return this;
        }

        public JSONArrayBuilder add(Boolean value) {
            contents.add(value);
            return this;
        }

        public JSONArrayBuilder add(JSONStringBuilder value) {
            contents.add(value);
            return this;
        }

        @Override
        protected void appendTo(StringBuilder sb) {
            sb.append("[");
            boolean comma = false;
            for (Object value : contents) {
                if (comma) {
                    sb.append(", ");
                }
                appendValue(sb, value);
                comma = true;
            }
            sb.append("]");
        }
    }

    public static final class JSONReadyArray extends JSONStringBuilder {
        private final List<Object> contents = new ArrayList<>();
        private int size;

        private JSONReadyArray(int startSize) {
            size = startSize + 2 /* '[' ']' */;
        }

        public JSONReadyArray add(String value) {
            addElement(quote(value));
            return this;
        }

        public JSONReadyArray add(Number value) {
            addElement(value.toString());
            return this;
        }

        public JSONReadyArray add(Boolean value) {
            addElement(value.toString());
            return this;
        }

        public JSONReadyArray add(JSONStringBuilder value) {
            addElement(value.toString());
            return this;
        }

        private void addElement(String value) {
            size += value.length() + 2 /* ", " */;
            contents.add(value);
        }

        public int getSize() {
            return size;
        }

        @Override
        protected void appendTo(StringBuilder sb) {
            sb.append("[");
            boolean comma = false;
            for (Object value : contents) {
                if (comma) {
                    sb.append(", ");
                }
                sb.append(value);
                comma = true;
            }
            sb.append("]");
        }
    }

}
