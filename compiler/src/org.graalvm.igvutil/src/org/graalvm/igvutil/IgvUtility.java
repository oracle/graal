/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.igvutil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.graalvm.igvutil.args.Command;
import org.graalvm.igvutil.args.CommandGroup;
import org.graalvm.igvutil.args.OptionValue;
import org.graalvm.igvutil.args.Program;
import org.graalvm.igvutil.args.StringValue;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.DataBinaryWriter;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.StreamSource;
import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonPrettyWriter;
import jdk.graal.compiler.util.json.JsonWriter;

public class IgvUtility {
    abstract static class GraphCommand extends Command {
        protected final OptionValue<List<String>> inputFiles;

        public GraphCommand(String name, String description) {
            super(name, description);
            inputFiles = addPositional(new StringValue("FILES", "List of input BGV files").repeated());
        }

        public void apply() throws IOException {
            for (String file : inputFiles.getValue()) {
                visitFile(file);
            }
        }

        public void visitFile(String path) throws IOException {
            ModelBuilder mb = new ModelBuilder(new GraphDocument(), null) {
                @Override
                public InputGraph endGraph() {
                    InputGraph graph = super.endGraph();
                    visit(graph);
                    return graph;
                }
            };
            InputStream stream = openBGVStream(path);
            new BinaryReader(new StreamSource(stream), mb).parse();
        }

        abstract void visit(InputGraph graph);
    }

    /**
     * List the contents of a BGV file.
     */
    static final class Printer extends GraphCommand {
        public Printer() {
            super("list", "print IGV file contents");
        }

        @Override
        public void visitFile(String path) throws IOException {
            GraphDocument doc = openDocument(path);
            String name = doc.getName();
            if (name == null) {
                name = path;
            }
            System.out.println(name);
            List<Integer> indentStack = new ArrayList<>();
            indentStack.add(doc.getElements().size());
            for (FolderElement f : doc.getElements()) {
                print(f, indentStack);
            }
        }

        @Override
        void visit(InputGraph graph) {
            GraalError.unimplementedOverride();
        }

        void printIndent(List<Integer> indentStack) {
            for (int i = 0; i < indentStack.size() - 1; ++i) {
                System.out.print(indentStack.get(i) > 0 ? "│  " : "   ");
            }
            System.out.print(switch (indentStack.getLast()) {
                case 0 -> "   ";
                case 1 -> "└─ ";
                default -> "├─ ";
            });
        }

        private void print(FolderElement folder, List<Integer> indentStack) {
            printIndent(indentStack);
            indentStack.set(indentStack.size() - 1, indentStack.getLast() - 1);
            if (folder instanceof InputGraph graph) {
                System.out.println(graph.getName());
            } else if (folder instanceof Group group) {
                System.out.println(group.getName());
                indentStack.add(group.getElements().size());
                for (FolderElement f : group.getElements()) {
                    print(f, indentStack);
                }
                indentStack.removeLast();
            } else {
                throw new InternalError("Unexpected folder type " + folder.getClass());
            }
        }
    }

    static final class Flatten extends GraphCommand {
        private final OptionValue<String> outputFile;
        private final OptionValue<String> flattenKey;

        private final GraphDocument newDoc = new GraphDocument();
        private final Map<String, Group> groups = new HashMap<>();

        public Flatten() {
            super("flatten", """
                    Reorders graphs in the given input files so that they are grouped according
                    to a specified property, such as their name.""");
            outputFile = addOption("--output-file", new StringValue("PATH",
                    "Path that the flattened BGV file will be saved under"));
            flattenKey = addOption("--by", new StringValue("PROPERTY",
                    "graph", "Graph property that graphs will be grouped by"));
        }

        void save(String filename) {
            try {
                DataBinaryWriter.export(new File(filename), newDoc, null, null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void apply() throws IOException {
            super.apply();
            save(outputFile.getValue());
        }

        @Override
        void visit(InputGraph graph) {
            Group group = (Group) graph.getParent();
            String id = graph.getProperties().get(flattenKey.getValue(), String.class);
            Group newGroup = groups.get(id);
            if (newGroup == null) {
                System.out.printf("Creating new group with key '%s'%n", id);
                newGroup = new Group(newDoc, group.getID());
                newGroup.setMethod(group.getMethod());
                newGroup.updateProperties(group.getProperties());
                newGroup.getProperties().setProperty(KnownPropertyNames.PROPNAME_NAME, id);
                newDoc.addElement(newGroup);
                groups.put(id, newGroup);
            }
            newGroup.addElement(graph);
        }
    }

    static final class Filter extends GraphCommand {
        private final OptionValue<String> nodePropertyFilter;
        private final OptionValue<String> graphPropertyFilter;

        private JsonExporter exporter = null;
        private final JsonWriter writer;

        public Filter() {
            super("filter", "filter nodes and graphs according to properties and export to JSON");
            nodePropertyFilter = addOption("--node-properties", new StringValue("PROPERTIES", "", "comma-separated list of node properties"));
            graphPropertyFilter = addOption("--graph-properties", new StringValue("PROPERTIES", "", "comma-separated list of graph properties"));
            writer = new JsonPrettyWriter(new PrintWriter(System.out));
        }

        @Override
        public void apply() throws IOException {
            Set<String> nodeProperties = nodePropertyFilter.isSet() ? Set.of(nodePropertyFilter.getValue().split(",")) : null;
            Set<String> graphProperties = graphPropertyFilter.isSet() ? Set.of(graphPropertyFilter.getValue().split(",")) : null;

            exporter = new JsonExporter(graphProperties, nodeProperties);

            List<String> files = inputFiles.getValue();
            JsonBuilder.ArrayBuilder arrayBuilder = files.size() > 1 ? writer.arrayBuilder() : null;
            for (String file : inputFiles.getValue()) {
                try (JsonBuilder.ObjectBuilder documentBuilder = arrayBuilder == null ? writer.objectBuilder() : arrayBuilder.nextEntry().object()) {
                    GraphDocument document = openDocument(file);
                    exporter.writeElement(document, documentBuilder);
                }
            }
            writer.close();
            System.out.println();
        }

        @Override
        void visit(InputGraph graph) {
            GraalError.unimplementedOverride();
        }
    }


    private static InputStream openBGVStream(String filename) {
        try {
            InputStream stream = Files.newInputStream(Path.of(filename));
            if (filename.endsWith(".bgv.gz")) {
                return new GZIPInputStream(stream, 8192);
            } else {
                return new BufferedInputStream(stream);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static GraphDocument openDocument(String filename) {
        InputStream stream = openBGVStream(filename);
        GraphDocument doc = new GraphDocument();
        ModelBuilder mb = new ModelBuilder(doc, null);
        try {
            new BinaryReader(new StreamSource(stream), mb).parse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return doc;
    }

    public static void main(String[] args) throws IOException {
        Program program = new Program("mx igvutil", "Various utilities to inspect and manipulate IGV dump files");
        CommandGroup<GraphCommand> subcommand = program.addCommandGroup(new CommandGroup<>());
        subcommand.addCommand(new Flatten());
        subcommand.addCommand(new Filter());
        subcommand.addCommand(new Printer());

        program.parseAndValidate(args, true);
        subcommand.getSelectedCommand().apply();
    }

}
