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
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.graalvm.igvutil.args.Command;
import org.graalvm.igvutil.args.CommandGroup;
import org.graalvm.igvutil.args.OptionValue;
import org.graalvm.igvutil.args.Program;
import org.graalvm.igvutil.args.StringValue;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.StreamSource;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonPrettyWriter;
import jdk.graal.compiler.util.json.JsonWriter;

/**
 * Various utility programs to inspect and manipulate igv dumps. Specifically, this program offers
 * three subcommands:
 * <ul>
 * <li>{@code list}: show the contents of a .bgv file in a tree-like format</li>
 * <li>{@code flatten}: group graphs across multiple files/dumps by name or other properties.</li>
 * <li>{@code filter}: export graph data to JSON, optionally selecting a subset of graph/node
 * properties.</li>
 * </ul>
 */
public class IgvUtility {
    abstract static class GraphCommand extends Command {
        protected final OptionValue<List<String>> inputFiles;

        GraphCommand(String name, String description) {
            super(name, description);
            inputFiles = addPositional(new StringValue("FILES", "List of input BGV files").repeated());
        }

        abstract void apply() throws IOException;
    }

    static final class ListCommand extends GraphCommand {
        ListCommand() {
            super("list", "print IGV file contents in a tree-like format");
        }

        @Override
        void apply() throws IOException {
            try (PrintWriter writer = new PrintWriter(System.out)) {
                Printer printer = new Printer(writer);
                for (String path : inputFiles.getValue()) {
                    GraphDocument doc = openDocument(path);
                    String name = doc.getName();
                    if (name == null) {
                        name = path;
                    }
                    printer.print(doc, name);
                }
            }
        }
    }

    static final class FlattenCommand extends GraphCommand {
        private final OptionValue<String> outputFile;
        private final OptionValue<String> flattenKey;

        FlattenCommand() {
            super("flatten", "group graphs across multiple files/dumps by name or some other graph property, e.g. date, type or compilationId.");
            outputFile = addNamed("--output-file", new StringValue("PATH",
                            "Path that the flattened BGV file will be saved under"));
            flattenKey = addNamed("--by", new StringValue("PROPERTY",
                            "graph", "Graph property that graphs will be grouped by"));
        }

        @Override
        void apply() throws IOException {
            Flattener flattener = new Flattener(flattenKey.getValue());
            for (String file : inputFiles.getValue()) {
                flattener.visitDump(openBGVStream(file));
            }
            flattener.save(outputFile.getValue());
        }
    }

    static final class FilterCommand extends GraphCommand {
        private final OptionValue<String> nodePropertyFilter;
        private final OptionValue<String> graphPropertyFilter;

        private JsonExporter exporter = null;
        private final JsonWriter writer;

        FilterCommand() {
            super("filter", "export graph data to JSON, optionally selecting a subset of graph/node properties");
            nodePropertyFilter = addNamed("--node-properties", new StringValue("PROPERTIES", "", "comma-separated list of node properties"));
            graphPropertyFilter = addNamed("--graph-properties", new StringValue("PROPERTIES", "", "comma-separated list of graph properties"));
            writer = new JsonPrettyWriter(new PrintWriter(System.out));
        }

        @Override
        void apply() throws IOException {
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
        CommandGroup<GraphCommand> subcommand = program.addCommandGroup(new CommandGroup<>("SUBCOMMAND", "Choose a subcommand"));
        subcommand.addCommand(new FlattenCommand());
        subcommand.addCommand(new FilterCommand());
        subcommand.addCommand(new ListCommand());

        program.parseAndValidate(args, true);
        subcommand.getSelectedCommand().apply();
    }

}
