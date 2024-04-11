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

import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.data.services.GraphSelections;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.view.DiagramViewModel;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.netbeans.api.editor.document.EditorDocumentUtils;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author sdedic
 */
public class SourceUtils {
    public static Collection<InputNode> findLineNodes(JTextComponent target, GraphSource src, Collection<Location> outLocs, boolean direct) {
        FileObject fo = EditorDocumentUtils.getFileObject(target.getDocument());
        Caret c = target.getCaret();
        Document doc = target.getDocument();
        if (fo == null || c == null | doc == null) {
            return null;
        }

        int caretOffset = c.getDot();
        int line;
        try {
            line = LineDocumentUtils.getLineIndex(LineDocumentUtils.asRequired(doc, LineDocument.class), caretOffset) + 1;
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
        if (line == -1) {
            return null;
        }
        return findLineNodes(fo, line, line, src, outLocs, direct);
    }

    public static Collection<InputNode> findLineNodes(FileObject fo, int lineFrom, int lineTo, GraphSource src, Collection<Location> outLocs, boolean direct) {
        NodeLocationContext context = Lookup.getDefault().lookup(NodeLocationContext.class);
        List<Location> locs = src == null ? context.getFileLocations(fo, direct) : src.getFileLocations(fo, direct);
        Collection<Location> lineLocs = SourceLocationUtils.atLine(locs, lineFrom, lineTo);
        if (outLocs != null) {
            outLocs.addAll(lineLocs);
        }
        GraphSource gs = src != null ? src : context.getGraphSource();
        if (gs == null || lineLocs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<InputNode> nodes = new HashSet<>();
        for (Location l : lineLocs) {
            nodes.addAll(gs.getNodesAt(l));
        }

        if (nodes.isEmpty()) {
            Set<Location> parents = new HashSet<>();
            for (Location l : lineLocs) {
                Iterable<NodeStack> i = gs.getNodesPassingThrough(l);
                if (i == null) {
                    continue;
                }
                Iterator<NodeStack> nsI = i.iterator();
                while (nsI.hasNext()) {
                    boolean add = true;
                    NodeStack stack = nsI.next();
                    for (Iterator<NodeStack.Frame> fit = stack.iterator(); fit.hasNext(); ) {
                        if (parents.contains(fit.next().getLocation())) {
                            add = false;
                            break;
                        }
                    }
                    if (add) {
                        nodes.add(stack.getNode());
                        parents.add(stack.bottom().getLocation());
                    }
                }
            }
        }
        return nodes;
    }

    /**
     * Trasnforms graph nodes into nodes that represent selectable items, whole Figures.
     * The diagram may not be ready yet; {@link DiagramViewModel#withDiagramToView}
     * must be called and the nodes processed in a callback.
     *
     * @param nodes
     * @param viewer
     * @param callback
     * @param all
     */
    public static void resolveSelectableNodes(Collection<InputNode> nodes, DiagramViewer viewer,
                                              Consumer<Collection<InputNode>> callback, boolean all) {
        viewer.getModel().withDiagramToView((dg) -> {
            SwingUtilities.invokeLater(() -> {
                if (nodes == null) {
                    callback.accept(Collections.emptySet());
                    return;
                }
                if (all) {
                    callback.accept(findTopNodes(viewer, nodes));
                } else {
                    Collection<InputNode> in = selectNext(dg, viewer, nodes, false);
                    if (in != null) {
                        callback.accept(in);
                    }
                }
            });
        });
    }

    /**
     * Finds top-level Nodes in the viewer for the given set of initial nodes. The viewer may merge nodes somehow, because
     * of Filters. This method will find top-level nodes that represent or contain the passed ones.
     *
     * @param view  viewer
     * @param nodes nodes to find
     * @return top level nodes, which contain or represent the input nodes.
     */
    public static Collection<InputNode> findTopNodes(DiagramViewer view, Collection<InputNode> nodes) {
        Collection<InputNode> newSel = new ArrayList<>(nodes.size());
        for (InputNode in : nodes) {
            Collection<Figure> figs = view.figuresForNodes(Collections.singletonList(in));
            if (figs.size() != 1) {
                continue;
            } else {
                Collection<Integer> ints = figs.iterator().next().getSource().getSourceNodeIds();
                if (ints.size() != 1 || (ints.iterator().next() != in.getId())) {
                    continue;
                }
            }
            newSel.add(in);
        }
        return newSel;
    }

    /**
     * Selects next nodes in the graph. Searches in the current selection for a Figure that corresponds
     * to the selection; then returns set of nodes that correspond to the <b>next</b> figure in the selection. Cycles
     * around if the selection is exhausted, so repeating {@link #selectNext} will
     *
     * @param dg                  diagram
     * @param view                viewer to use
     * @param doNothingIfComplete
     * @param selection           nodes currently selected, as an anchor
     * @return nodes to select, which represent the next figure in viewer's selection
     */
    public static Collection<InputNode> selectNext(Diagram dg, DiagramViewer view, Collection<InputNode> selection,
                                                   boolean doNothingIfComplete) {
        GraphSelections gs = view.getSelections();
        Collection<Figure> figs = view.figuresForNodes(selection);
        Collection<InputNode> newSel = null;
        Collection<InputNode> curSel = gs.getSelectedNodes();

        Collection<InputNode> firstSel = null;

        if (doNothingIfComplete) {
            Collection<Figure> curFig = new HashSet<>(view.figuresForNodes(curSel));
            if (curFig.removeAll(figs)) {
                return null;
            }
        }

        boolean useNext = false;
        for (Figure f : figs) {
            Collection<InputNode> nn = view.nodesForFigure(f);
            if (firstSel == null) {
                firstSel = nn;
            }
            if (curSel.containsAll(nn)) {
                useNext = true;
            } else if (useNext) {
                newSel = nn;
                break;
            }

            if (curSel.isEmpty()) {
                break;
            }
        }
        if (firstSel == null) {
            return null;
        }
        if (newSel == null) {
            newSel = firstSel; // loop around
        }
        return newSel;
    }
}
