/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graphio.parsing;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_DUPLICATE;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NAME;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_SHORT_NAME;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_TYPE;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.graal.compiler.graphio.GraphOutput;
import jdk.graal.compiler.graphio.parsing.BinaryReader.Method;
import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputMethod;

/**
 * @author Ondrej Douda <ondrej.douda@oracle.com>
 */
public final class DataBinaryWriter {
    private static final Set<String> GROUP_PROPERTY_EXCLUDE = new HashSet<>(Arrays.asList(PROPNAME_NAME, PROPNAME_TYPE));
    public static final Set<String> NODE_PROPERTY_EXCLUDE = new HashSet<>(Stream.concat(ModelBuilder.SYSTEM_PROPERTIES.stream(), Stream.of(PROPNAME_SHORT_NAME)).collect(Collectors.toList()));
    private static final Set<String> GRAPH_PROPERTY_EXCLUDE = new HashSet<>(Arrays.asList(PROPNAME_NAME, PROPNAME_DUPLICATE));

    private final GraphOutput<InputGraph, Method> target;
    private final AtomicBoolean cancel;
    private final Consumer<FolderElement> progressCallback;

    private DataBinaryWriter(GraphOutput<InputGraph, Method> target, AtomicBoolean cancel, Consumer<FolderElement> progressCallback) {
        this.target = target;
        this.cancel = cancel;
        this.progressCallback = progressCallback;
    }

    public static void export(File file, GraphDocument doc, Consumer<FolderElement> progressCallback, AtomicBoolean cancel) throws IOException {
        if (!(doc == null || file == null) && file.getName().endsWith(".bgv")) {
            try (GraphOutput<InputGraph, Method> target = DataBinaryPrinter.createOutput(
                            FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
                            doc.getProperties().toMap(new LinkedHashMap<>(), GROUP_PROPERTY_EXCLUDE))) {
                new DataBinaryWriter(target, cancel, progressCallback).export(doc);
            }
        }
    }

    private void export(GraphDocument doc) throws IOException {
        try {
            for (FolderElement e : doc.getElements()) {
                if (cancel != null && cancel.get()) {
                    throw new CancellationException();
                }
                if (e instanceof Group) {
                    writeGroup((Group) e);
                }
            }
        } catch (InterruptedException ex) {
            throw new InterruptedIOException();
        }
    }

    private void completeContent(Group.LazyContent<?> content) throws InterruptedException, IOException {
        Future<?> fut = content.completeContents(null);
        try {
            fut.get();
        } catch (ExecutionException e) {
            throw new IOException();
        } catch (InterruptedException e) {
            if (cancel != null && cancel.get()) {
                throw new CancellationException();
            }
            throw e;
        }
    }

    private void writeGroup(Group group) throws IOException, InterruptedException {
        if (cancel != null && cancel.get()) {
            throw new CancellationException();
        }
        if (group instanceof Group.LazyContent) {
            completeContent((Group.LazyContent<?>) group);
        }
        final InputMethod method = group.getMethod();
        final String shortName = method != null ? method.getShortName() : group.getName();
        final Method resolvedMethod = method != null ? method.getMethod() : null;
        final int bci = method != null ? method.getBci() : -1;
        final String name = group.getName();
        target.beginGroup(null, name, shortName, resolvedMethod, bci, group.getProperties().toMap(new LinkedHashMap<>(), GROUP_PROPERTY_EXCLUDE));

        for (FolderElement element : group.getElements()) {
            if (element instanceof Group) {
                writeGroup((Group) element);
            } else if (element instanceof InputGraph) {
                writeGraph((InputGraph) element);
            }
        }

        target.endGroup();
    }

    private void writeGraph(InputGraph graph) throws IOException, InterruptedException {
        if (cancel != null && cancel.get()) {
            throw new CancellationException();
        }
        if (progressCallback != null) {
            progressCallback.accept(graph);
        }
        if (graph instanceof Group.LazyContent) {
            completeContent((Group.LazyContent<?>) graph);
        }
        target.print(graph, graph.getProperties().toMap(new LinkedHashMap<>(), GRAPH_PROPERTY_EXCLUDE), graph.getDumpId(), graph.getFormat(), graph.getArgs());
    }
}
