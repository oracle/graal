/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.printer;

import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintBinaryGraphPort;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintBinaryGraphs;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintGraphHost;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintXmlGraphPort;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.ShowDumpFiles;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugConfigCustomizer;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugVerifyHandler;
import org.graalvm.compiler.debug.GraalDebugConfig;
import org.graalvm.compiler.debug.GraalDebugConfig.Options;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.UniquePathUtilities;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

@ServiceProvider(DebugConfigCustomizer.class)
public class GraalDebugConfigCustomizer implements DebugConfigCustomizer {

    private static SnippetReflectionProvider extractSnippetReflection(Object... capabilites) {
        if (capabilites != null) {
            for (Object capability : capabilites) {
                if (capability instanceof SnippetReflectionProvider) {
                    return (SnippetReflectionProvider) capability;
                }
            }
        }
        return null;
    }

    @Override
    public void addDumpHandlersTo(OptionValues options, Collection<DebugDumpHandler> dumpHandlers, Object... capabilites) {
        SnippetReflectionProvider snippetReflection = extractSnippetReflection(capabilites);
        if (Options.PrintGraphFile.getValue(options)) {
            dumpHandlers.add(new GraphPrinterDumpHandler((graph) -> createFilePrinter(graph, options, snippetReflection)));
        } else {
            dumpHandlers.add(new GraphPrinterDumpHandler((graph) -> createNetworkPrinter(graph, options, snippetReflection)));
        }
        if (Options.PrintCanonicalGraphStrings.getValue(options)) {
            dumpHandlers.add(new GraphPrinterDumpHandler((graph) -> createStringPrinter(snippetReflection)));
        }
        dumpHandlers.add(new NodeDumper());
        if (Options.PrintCFG.getValue(options) || Options.PrintBackendCFG.getValue(options)) {
            if (Options.PrintBinaryGraphs.getValue(options) && Options.PrintCFG.getValue(options)) {
                TTY.out.println("Complete C1Visualizer dumping slows down PrintBinaryGraphs: use -Dgraal.PrintCFG=false to disable it");
            }
            dumpHandlers.add(new CFGPrinterObserver());
        }
    }

    @Override
    public void addVerifyHandlersTo(OptionValues options, Collection<DebugVerifyHandler> verifyHandlers) {
        verifyHandlers.add(new NoDeadCodeVerifyHandler());
    }

    private static class NodeDumper implements DebugDumpHandler {
        @Override
        public void dump(DebugContext debug, Object object, String format, Object... arguments) {
            if (object instanceof Node) {
                Node node = (Node) object;
                String location = GraphUtil.approxSourceLocation(node);
                String nodeName = node.toString(Verbosity.Debugger);
                if (location != null) {
                    debug.log("Context obj %s (approx. location: %s)", nodeName, location);
                } else {
                    debug.log("Context obj %s", nodeName);
                }
            }
        }
    }

    private static CanonicalStringGraphPrinter createStringPrinter(SnippetReflectionProvider snippetReflection) {
        // Construct the path to the directory.
        return new CanonicalStringGraphPrinter(snippetReflection);
    }

    public static String sanitizedFileName(String name) {
        try {
            Paths.get(name);
            return name;
        } catch (InvalidPathException e) {
            // fall through
        }
        StringBuilder buf = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            try {
                Paths.get(String.valueOf(c));
            } catch (InvalidPathException e) {
                buf.append('_');
            }
            buf.append(c);
        }
        return buf.toString();
    }

    private static GraphPrinter createNetworkPrinter(Graph graph, OptionValues options, SnippetReflectionProvider snippetReflection) throws IOException {
        String host = PrintGraphHost.getValue(options);
        int port = PrintBinaryGraphs.getValue(options) ? PrintBinaryGraphPort.getValue(options) : PrintXmlGraphPort.getValue(options);
        try {
            GraphPrinter printer;
            if (Options.PrintBinaryGraphs.getValue(options)) {
                printer = new BinaryGraphPrinter(SocketChannel.open(new InetSocketAddress(host, port)), snippetReflection);
            } else {
                printer = new IdealGraphPrinter(new Socket(host, port).getOutputStream(), true, snippetReflection);
            }
            TTY.println("Connected to the IGV on %s:%d", host, port);
            return printer;
        } catch (ClosedByInterruptException | InterruptedIOException e) {
            /*
             * Interrupts should not count as errors because they may be caused by a cancelled Graal
             * compilation. ClosedByInterruptException occurs if the SocketChannel could not be
             * opened. InterruptedIOException occurs if new Socket(..) was interrupted.
             */
            return null;
        } catch (IOException e) {
            if (!Options.PrintGraphFile.hasBeenSet(options)) {
                return createFilePrinter(graph, options, snippetReflection);
            } else {
                throw new IOException(String.format("Could not connect to the IGV on %s:%d", host, port), e);
            }
        }
    }

    private static final AtomicInteger unknownCompilationId = new AtomicInteger();

    /**
     * Creates a unique path for dumping based on a given graph and a file extension.
     *
     * @param graph a base path name is derived from {@code graph}
     * @param extension a suffix which if non-null and non-empty added to the end of the returned
     *            path separated by a {@code "."}
     * @param isDirectory specifies if the returned path will be used as a directory
     * @throws IOException if there was an error creating a unique path
     */
    static Path createDumpFilePath(OptionValues options, Graph graph, String extension, boolean isDirectory) throws IOException {
        String baseName;
        CompilationIdentifier compilationId = CompilationIdentifier.INVALID_COMPILATION_ID;
        if (graph instanceof StructuredGraph) {
            StructuredGraph sgraph = (StructuredGraph) graph;
            compilationId = sgraph.compilationId();
            if (compilationId == CompilationIdentifier.INVALID_COMPILATION_ID) {
                String graphName;
                if (graph.name != null) {
                    graphName = graph.name;
                } else if (sgraph.method() != null) {
                    graphName = sgraph.method().format("%H.%n(%p)");
                } else {
                    graphName = sgraph.toString();
                }
                compilationId = new CompilationIdentifier() {

                    @Override
                    public String toString(Verbosity verbosity) {
                        return toString();
                    }

                    @Override
                    public String toString() {
                        return graph.getClass().getSimpleName() + "-" + sgraph.graphId() + '[' + graphName + ']';
                    }
                };
            }
        }
        if (compilationId == CompilationIdentifier.INVALID_COMPILATION_ID) {
            String graphName = graph.name != null ? graph.name : graph.toString();
            baseName = "UnknownCompilation-" + unknownCompilationId.incrementAndGet() + '[' + graphName + ']';
        } else {
            baseName = compilationId.toString(CompilationIdentifier.Verbosity.DETAILED);
        }
        String ext = UniquePathUtilities.formatExtension(extension);
        baseName = sanitizedFileName(baseName);
        Path dumpDir = GraalDebugConfig.getDumpDirectory(options);
        Path result = dumpDir.resolve(baseName + ext);
        if (Files.exists(result)) {
            if (isDirectory) {
                Files.createTempDirectory(dumpDir, baseName + ext);
            } else {
                Files.createTempFile(dumpDir, baseName, ext);
            }
        } else if (isDirectory) {
            Files.createDirectories(result);
        }
        if (ShowDumpFiles.getValue(options)) {
            TTY.println("Dumping debug output to %s", result.toAbsolutePath().toString());
        }
        return result;
    }

    private static GraphPrinter createFilePrinter(Graph graph, OptionValues options, SnippetReflectionProvider snippetReflection) throws IOException {
        Path path = createDumpFilePath(options, graph, PrintBinaryGraphs.getValue(options) ? "bgv" : "gv.xml", false);
        try {
            GraphPrinter printer;
            if (Options.PrintBinaryGraphs.getValue(options)) {
                printer = new BinaryGraphPrinter(FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW), snippetReflection);
            } else {
                printer = new IdealGraphPrinter(Files.newOutputStream(path), true, snippetReflection);
            }
            return printer;
        } catch (IOException e) {
            throw new IOException(String.format("Failed to open %s to dump IGV graphs", path), e);
        }
    }
}
