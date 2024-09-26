/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.source;

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.source.spi.LocationResolver;
import org.graalvm.visualizer.source.spi.StackProcessor;
import org.openide.filesystems.FileObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Context that accumulates info from the processor, builds NodeCallStack from
 * the Locations.
 */
public final class ProcessorContext {
    private final GraphSource source;
    private final InputGraph graph;
    private final FileRegistry fileRegistry;
    private final Language language;
    private final String mime;
    private List<LocationResolver> resolvers;

    /**
     * Stacks for individual Nodes
     */
    Map<InputNode, StackData> stacks = new HashMap<>();

    /**
     * For a given Location, contains children -this.mime = mime; nested Locations, next level of the stack. This information
     * is cached here before it is recorded into the Locations themselves.
     */
    Map<Location, Set<Location>> successors = new HashMap<>();

    /**
     * Locations, which are "final", they correspond to a Node. Other locations
     * are non-final, they just represent some frame on the stack.
     */
    Map<Location, Collection<StackData>> finalLocations = new HashMap<>();

    /**
     * Cached locations, so we do not need to synchronize on the GraphSource lock.
     * All locations recorded here are also entered into the master map.
     */
    private Map<Location, Location> locations = new HashMap<>();

    Set<FileKey> seenFiles = new HashSet<>();

    Map<FileKey, Collection<Location>> keyLocations = new HashMap<>();

    Map<FileObject, Collection<Location>> fileLocations = new HashMap<>();

    /**
     * List of processors to call for stack processing.
     */
    private List<StackProcessor> processors = new ArrayList<>();

    ProcessorContext(GraphSource source, InputGraph graph, FileRegistry fileRegistry, String langID) {
        this.graph = graph;
        this.fileRegistry = fileRegistry;
        this.mime = langID;
        this.source = source;
        this.language = Language.getRegistry().makeLanguage(langID);
    }

    public String getLangID() {
        return language.getGraalID();
    }

    public String getMimeType() {
        return mime;
    }

    Map<Location, Set<Location>> succ() {
        return successors;
    }

    void addProcessor(StackProcessor processor) {
        this.processors.add(processor);
        processor.attach(this);
    }

    void processNode(InputNode n) {
        for (StackProcessor p : processors) {
            List<Location> locs = p.processStack(n);
            if (locs != null) {
                addNodeStack(locs, n);
                break;
            }
        }
    }

    public InputGraph getGraph() {
        return graph;
    }

    public boolean isGraphNested() {
        return !(graph.getParent() instanceof Group);
    }

    private Location finishNewLocation(Location l) {
        if (l.isResolved()) {
            fileLocations.computeIfAbsent(l.getOriginFile(), (x) -> new ArrayList<>()).add(l);
        } else {
            keyLocations.computeIfAbsent(l.getFile(), (x) -> new ArrayList<>()).add(l);
        }
        return l;
    }

    private Location unique(Location l) {
        // first look in cache here, since it is unsynchronized. Then look/enter 
        // in the source cache.
        return locations.computeIfAbsent(l, (loc) -> {
            Location srcLoc = source.uniqueLocation(l);
            return finishNewLocation(srcLoc);
        });
    }

    private Collection<StackData> addOrCreateStack(Location s, Collection<StackData> existing) {
        if (existing == null) {
            existing = new HashSet<>();
        }
        return existing;
    }

    private FileObject attemptResolve(Location l) {
        if (l.isResolved() || !seenFiles.add(l.getFile())) {
            return l.getOriginFile();
        }
        if (resolvers == null) {
            resolvers = fileRegistry.createResolvers(mime, getGraph());
        }
        for (LocationResolver r : resolvers) {
            FileObject f = r.resolve(l.getFile());
            if (f != null) {
                fileRegistry.resolve(l.getFile(), f);
                return f;
            }
        }
        return null;
    }

    private void addNodeStack(List<Location> currentStack, InputNode node) {
        int len = currentStack.size();
        // compute from the end, since the last 
        if (len == 0) {
            return;
        }
        // cannonicalize the last element
        Location parent = null;
        for (int i = len - 1; i >= 0; i--) {
            Location l = currentStack.get(i);
            if (!l.isResolved()) {
                attemptResolve(l);
            }
            l.setParent(parent);
            if (parent != null) {
                successors.compute(parent, this::createOrAdd).add(l);
            }
            l = unique(l);
            currentStack.set(i, l);
            parent = l;
        }
        StackData stack = new StackData(node.getId(), mime, currentStack);
        stacks.put(node, stack);
        finalLocations.compute(parent, this::addOrCreateStack).add(stack);
    }

    public void attachInfo(Location l, SpecificLocationInfo i) {
        l.attach(i);
    }

    private Set<Location> createOrAdd(Location l, Set<Location> c) {
        if (c == null) {
            c = new HashSet<>();
        }
        return c;
    }

    public FileKey file(String filename, FileObject resolved) {
        return uniqueKey(
                resolved == null ? new FileKey(mime, filename) : new FileKey(filename, resolved)
        );
    }

    public FileKey uniqueKey(FileKey k) {
        return fileRegistry.enter(k, getGraph());
    }
}
