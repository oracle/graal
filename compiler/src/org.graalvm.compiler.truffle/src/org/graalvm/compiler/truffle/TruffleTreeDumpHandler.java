/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import static org.graalvm.compiler.debug.DebugOptions.DumpPath;
import static org.graalvm.compiler.debug.DebugOptions.PrintBinaryGraphPort;
import static org.graalvm.compiler.debug.DebugOptions.PrintBinaryGraphs;
import static org.graalvm.compiler.debug.DebugOptions.PrintGraphHost;
import static org.graalvm.compiler.debug.DebugOptions.PrintXmlGraphPort;
import org.graalvm.compiler.debug.UniquePathUtilities;
import org.graalvm.compiler.truffle.GraphPrintVisitor.GraphPrintAdapter;
import org.graalvm.compiler.truffle.GraphPrintVisitor.GraphPrintHandler;

public class TruffleTreeDumpHandler implements DebugDumpHandler {

    private final Callable<WritableByteChannel> out;
    private final OptionValues options;

    /**
     * The {@link OptimizedCallTarget} is dumped multiple times during Graal compilation, because it
     * is also a subclass of InstalledCode. To disambiguate dumping, we wrap the call target into
     * this class when we want to dump the truffle tree.
     */
    public static class TruffleTreeDump {
        final RootCallTarget callTarget;

        public TruffleTreeDump(RootCallTarget callTarget) {
            this.callTarget = callTarget;
        }
    }

    public TruffleTreeDumpHandler(OptionValues options) {
        this.options = options;
        if (DebugOptions.PrintGraphFile.getValue(options)) {
            this.out = createFilePrinter(options);
        } else {
            this.out = createNetworkPrinter(options);
        }
    }

    private static Path getFilePrinterPath(OptionValues options) throws IOException {
        // Construct the path to the file.
        // PrintGraphFileName -
        String extension = PrintBinaryGraphs.getValue(options) ? "bgv" : "gv.xml";
        return UniquePathUtilities.getPath(options, DumpPath, extension);
    }

    private static Callable<WritableByteChannel> createFilePrinter(OptionValues options) {
        return () -> {
            Path path = getFilePrinterPath(options);
            return FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        };
    }

    private static Callable<WritableByteChannel> createNetworkPrinter(OptionValues options) {
        String host = PrintGraphHost.getValue(options);
        int port = PrintBinaryGraphs.getValue(options) ? PrintBinaryGraphPort.getValue(options) : PrintXmlGraphPort.getValue(options);
        return () -> SocketChannel.open(new InetSocketAddress(host, port));
    }

    @Override
    public void dump(DebugContext debug, Object object, final String format, Object... arguments) {
        if (object instanceof TruffleTreeDump && DebugOptions.PrintGraph.getValue(options) && TruffleCompilerOptions.getValue(DebugOptions.PrintTruffleTrees)) {
            String message = String.format(format, arguments);
            try {
                dumpRootCallTarget(message, ((TruffleTreeDump) object).callTarget);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void dumpRootCallTarget(final String message, RootCallTarget callTarget) throws IOException {
        if (callTarget.getRootNode() != null) {
            final GraphPrintVisitor printer;
            try {
                printer = new GraphPrintVisitor(out.call());
            } catch (RuntimeException | IOException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IOException(ex);
            }

            printer.beginGroup(callTarget, callTarget.toString(), callTarget.getRootNode().getName(), null, 0, null);
            printer.beginGraph(message).visit(callTarget.getRootNode());
            if (callTarget instanceof OptimizedCallTarget) {
                TruffleInlining inlining = new TruffleInlining((OptimizedCallTarget) callTarget, new DefaultInliningPolicy());
                if (inlining.countInlinedCalls() > 0) {
                    dumpInlinedTrees(printer, (OptimizedCallTarget) callTarget, inlining);
                    dumpInlinedCallGraph(printer, (OptimizedCallTarget) callTarget, inlining);
                }
            }
            printer.endGroup();
        }
    }

    private static void dumpInlinedTrees(final GraphPrintVisitor printer, final OptimizedCallTarget callTarget, TruffleInlining inlining) throws IOException {
        for (DirectCallNode callNode : NodeUtil.findAllNodeInstances(callTarget.getRootNode(), DirectCallNode.class)) {
            CallTarget inlinedCallTarget = callNode.getCurrentCallTarget();
            if (inlinedCallTarget instanceof OptimizedCallTarget && callNode instanceof OptimizedDirectCallNode) {
                TruffleInliningDecision decision = inlining.findByCall((OptimizedDirectCallNode) callNode);
                if (decision != null && decision.isInline()) {
                    printer.beginGroup((RootCallTarget) inlinedCallTarget, inlinedCallTarget.toString(), null, null, 0, null);
                    printer.beginGraph(inlinedCallTarget.toString()).visit(((RootCallTarget) inlinedCallTarget).getRootNode());
                    dumpInlinedTrees(printer, (OptimizedCallTarget) inlinedCallTarget, decision);
                    printer.endGroup();
                }
            }
        }
    }

    private static void dumpInlinedCallGraph(final GraphPrintVisitor printer, final OptimizedCallTarget rootCallTarget, TruffleInlining inlining) throws IOException {
        class InliningGraphPrintHandler implements GraphPrintHandler {
            private final TruffleInlining inlining;

            InliningGraphPrintHandler(TruffleInlining inlining) {
                this.inlining = inlining;
            }

            @Override
            public void visit(Object node, GraphPrintAdapter g) {
                if (g.visited(node)) {
                    return;
                }
                g.createElementForNode(node);
                g.setNodeProperty(node, "label", node.toString());
                for (DirectCallNode callNode : NodeUtil.findAllNodeInstances(((RootCallTarget) node).getRootNode(), DirectCallNode.class)) {
                    CallTarget inlinedCallTarget = callNode.getCurrentCallTarget();
                    if (inlinedCallTarget instanceof OptimizedCallTarget && callNode instanceof OptimizedDirectCallNode) {
                        TruffleInliningDecision decision = inlining.findByCall((OptimizedDirectCallNode) callNode);
                        if (decision != null && decision.isInline()) {
                            InliningGraphPrintHandler inliningGraphPrintHandler = new InliningGraphPrintHandler(decision);
                            inliningGraphPrintHandler.visit(inlinedCallTarget, printer.new GraphPrintAdapter());
                            SourceSection sourceSection = callNode.getEncapsulatingSourceSection();
                            g.connectNodes(node, inlinedCallTarget, sourceSection != null ? sourceSection.toString() : null);
                        }
                    }
                }
            }
        }

        printer.beginGraph("inlined call graph");
        printer.visit(rootCallTarget, new InliningGraphPrintHandler(inlining));
    }

    @Override
    public void close() {
        // nothing to do
    }
}
