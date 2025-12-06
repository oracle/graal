/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.compilation.view;

import at.ssw.visualizer.compilation.view.icons.Icons;
import at.ssw.visualizer.model.Compilation;
import at.ssw.visualizer.model.CompilationElement;
import at.ssw.visualizer.model.CompilationModel;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import java.awt.Image;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.swing.Action;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.ImageUtilities;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Christian Wimmer
 */
public class CompilationModelNode extends AbstractNode {
    protected Image ImageFolder = ImageUtilities.loadImage(Icons.FOLDER);
    protected Image ImageCfg = ImageUtilities.loadImage(Icons.CFG);
    protected Image ImageIntervals = ImageUtilities.loadImage(Icons.INTERVALS);
    protected boolean sortCompilations;
    protected boolean filterCfg;
    protected boolean shortNames;

    public CompilationModelNode(CompilationModel model) {
        super(Children.LEAF);
        setChildren(new CompilationModelChildren(model));
    }

    public void doRefresh(boolean sortCompilations, boolean filterCfg, boolean shortNames) {
        this.sortCompilations = sortCompilations;
        this.filterCfg = filterCfg;
        this.shortNames = shortNames;

        ((CompilationModelChildren) getChildren()).doRefresh();
    }

    @Override
    public Action[] getActions(boolean context) {
        return new Action[0];
    }

    class CompilationModelChildren extends Children.Keys<Compilation> {
        private CompilationModel model;
        private ChangeListener changeListener = new ChangeListener() {
            public void stateChanged(ChangeEvent event) {
                addNotify();
            }

        };

        public CompilationModelChildren(CompilationModel model) {
            this.model = model;
            model.addChangedListener(changeListener);
        }

        public void doRefresh() {
            addNotify();
            for (Node n : getNodes()) {
                ((CompilationNode) n).doRefresh();
            }
        }

        @Override
        protected void addNotify() {
            Compilation[] compilations = model.getCompilations().toArray(new Compilation[0]);
            if (sortCompilations) {
                Arrays.sort(compilations, new Comparator<Compilation>() {
                    public int compare(Compilation o1, Compilation o2) {
                        if (shortNames) {
                            return o1.getShortName().compareToIgnoreCase(o2.getShortName());
                        }
                        return o1.getName().compareToIgnoreCase(o2.getName());
                    }

                });
            }
            setKeys(compilations);
        }

        protected Node[] createNodes(Compilation key) {
            return new Node[]{new CompilationNode(key)};
        }

    }

    class CompilationElementNode extends AbstractNode {
        private CompilationElement element;

        public CompilationElementNode(CompilationElement element) {
            super(element.getElements().size() > 0 ? new CompilationElementChildren(element) : Children.LEAF, Lookups.singleton(element));
            this.element = element;
        }

        public void doRefresh() {
            fireNameChange(null, null);
            if (getChildren() instanceof CompilationElementChildren) {
                ((CompilationElementChildren) getChildren()).doRefresh();
            }
        }

        @Override
        public String getName() {
            if (shortNames) {
                return element.getShortName();
            }
            return element.getName();
        }

        @Override
        public String getShortDescription() {
            return element.getName();
        }
        
        @Override
        public Image getIcon(int type) {
            return element instanceof ControlFlowGraph ? ImageCfg : ImageIntervals;
        }

        @Override
        public Image getOpenedIcon(int type) {
            return getIcon(type);
        }

        @Override
        public Action[] getActions(boolean context) {
            String path = element instanceof ControlFlowGraph ? CompilationViewTopComponent.ACTIONS_CFG : CompilationViewTopComponent.ACTIONS_INTERVALS;
            return Lookups.forPath(path).lookupAll(Action.class).toArray(new Action[0]);
        }

        @Override
        public Action getPreferredAction() {
            for (Action action : getActions(false)) {
                if (action.isEnabled()) {
                    return action;
                }
            }
            return null;
        }

    }

    class CompilationNode extends CompilationElementNode {
        public CompilationNode(Compilation compilation) {
            super(compilation);
        }

        @Override
        public Image getIcon(int type) {
            return ImageFolder;
        }

        @Override
        public Action[] getActions(boolean context) {
            return Lookups.forPath(CompilationViewTopComponent.ACTIONS_COMPILATION).lookupAll(Action.class).toArray(new Action[0]);
        }

        @Override
        public Action getPreferredAction() {
            return null;
        }

    }

    class CompilationElementChildren extends Children.Keys<CompilationElement> {
        private CompilationElement compilation;

        public CompilationElementChildren(CompilationElement compilation) {
            this.compilation = compilation;
        }

        public void doRefresh() {
            addNotify();
            for (Node n : getNodes()) {
                ((CompilationElementNode) n).doRefresh();
            }
        }

        @Override
        protected void addNotify() {
            List<CompilationElement> elements = new ArrayList<CompilationElement>();
            for (CompilationElement element : compilation.getElements()) {
                if (!filterCfg || element.getCompilation() != element.getParent() || !(element instanceof ControlFlowGraph) || ((ControlFlowGraph) element).hasHir() || ((ControlFlowGraph) element).hasLir()|| ((ControlFlowGraph) element).getNativeMethod() != null) {
                    elements.add(element);
                }
            }
            setKeys(elements);
        }

        protected Node[] createNodes(CompilationElement key) {
            return new Node[]{new CompilationElementNode(key)};
        }

    }
}
