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
package org.graalvm.visualizer.view.impl;

import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.filter.FilterExecution;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.view.DiagramViewModel;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class represents an 'exported API' of the diagram viewer. The functions
 * are intended to be called during preparation of Diagram by filters (scripts).
 * Changes could be batched, since different (old) {@link Diagram} instance is
 * displayed during filter processing.
 * <p/>
 * After the layouting process finishes, the implementation applies batched
 * changes so they are applied on the final diagram.
 * <p/>
 *
 * @author sdedic
 */
public final class FilterView {
    private final DiagramViewModel model;

    private Collection<Figure> selection;
    private Collection<Figure> toExtract;
    private Diagram diagram;
    private boolean showAll;

    private boolean changed;

    FilterView(DiagramViewModel model) {
        this.model = model;
    }

    private void clear() {
        changed = false;
        selection = null;
        toExtract = null;
        showAll = false;
    }

    private Optional<Figure> find(int id) {
        if (diagram == null) {
            return Optional.empty();
        } else {
            return diagram.getFigure(id);
        }
    }

    public void select(int nodeID) {
        find(nodeID).ifPresent((f) -> select(f));

    }

    public void select(Collection<Figure> figures) {
        selection = new HashSet<>(figures);
        changed = true;
    }

    public void select(Figure figure) {
        select(Collections.singletonList(figure));
    }

    public Collection<Figure> getSelected() {
        if (selection == null) {
            return model.getSelectedFigures();
        } else {
            return Collections.unmodifiableCollection(selection);
        }
    }

    public void extract(int nodeID) {
        find(nodeID).ifPresent((f) -> extract(f));

    }

    public void addExtract(int nodeID) {
        find(nodeID).ifPresent((f) -> addExtract(f));

    }

    public void extract(Figure figure) {
        extract(Collections.singleton(figure));
    }

    private Collection<Figure> findExtractedFigures() {
        if (diagram == null) {
            return new HashSet<>();
        }
        Collection<Integer> hidden = model.getHiddenNodes();
        Diagram dg = diagram;
        Collection<Figure> figures = dg.forSources(hidden, Figure.class).stream().filter(
                (f) -> f.isVisible()).collect(Collectors.toList());
        return new HashSet<>(figures);
    }

    public void addExtract(Figure f) {
        if (toExtract == null) {
            toExtract = findExtractedFigures();
        }
        toExtract.add(f);
        changed = true;
    }

    public void extract(Collection<Figure> figures) {
        toExtract = new HashSet<>(figures);
        changed = true;
    }

    private void doSelection() {
        if (selection == null) {
            return;
        }
        model.setSelectedFigures(selection);
    }

    private void doExtract() {
        if (showAll) {
            model.showNot(Collections.<Integer>emptySet());
            return;
        }
        if (toExtract == null) {
            return;
        }
        Set<InputNode> nodesToExtract = new HashSet<>();
        for (Figure f : toExtract) {
            nodesToExtract.addAll(f.getSource().getSourceNodes());
        }
        model.showOnly(nodesToExtract.stream().map(n -> n.getId()).collect(Collectors.toSet()));
    }

    public void showAll() {
        showAll = true;
        changed = true;
    }

    void setExecution(FilterExecution exec) {
        if (diagram != null && diagram != exec.getDiagram()) {
            clear();
        }
        this.diagram = exec.getDiagram();
    }

    void perform() {
        // flush potential caches
        synchronized (this) {
            if (!changed || model.getGraphToView() != diagram.getGraph()) {
                return;
            }
            doExtract();
            doSelection();
        }
    }
}
