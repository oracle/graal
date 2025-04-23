/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.search.ui;

import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;
import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.data.services.NodeContext;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.search.ContextNode;
import org.graalvm.visualizer.search.GraphItem;
import org.graalvm.visualizer.search.NodeResultItem;
import org.graalvm.visualizer.search.SearchResultsModel;
import org.graalvm.visualizer.util.PropertiesSheet;
import org.graalvm.visualizer.util.swing.ActionUtils;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.netbeans.api.actions.Openable;
import org.openide.awt.Actions;
import org.openide.awt.StatusDisplayer;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.PropertySupport;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.TopComponent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author sdedic
 */
public class ItemNode extends AbstractNode {
    private final SearchResultsModel results;
    private final Lookup context;
    private final NodeResultItem result;
    private final InstanceContent content;
    private InputNode node;

    public static Node createNode(SearchResultsModel model, NodeResultItem result, Lookup lkp) {
        return new ItemNode(model, result, lkp, new InstanceContent());
    }

    public ItemNode(SearchResultsModel model, NodeResultItem result) {
        this(model, result, Lookup.EMPTY, new InstanceContent());
    }

    private static Lookup combine(Lookup lkp, InstanceContent content) {
        AbstractLookup ll = new AbstractLookup(content);
        if (lkp == Lookup.EMPTY) {
            return ll;
        }
        return new ProxyLookup(lkp, ll);
    }

    @NbBundle.Messages({
            "# {0} - node Id",
            "# {1} - node name property",
            "FMT_NodeName={0}: {1}",
            "PROPSET_Structure=Structure",
            "PROPNAME_Graph=Graph",
            "PROPDESC_Graph=The containing graph",
            "PROPDESC_Group=The containing group",
    })
    public ItemNode(SearchResultsModel model, NodeResultItem result, Lookup lkp, InstanceContent content) {
        super(Children.LEAF, combine(lkp, content));
        this.results = model;
        this.content = content;
        this.result = result;
        this.context = lkp;
        setIconBaseWithExtension("org/graalvm/visualizer/search/resources/singleitem.png");

        Sheet sheet = new Sheet();
        PropertiesSheet.initializeSheet(result.getItemProperties(), sheet);
        setSheet(sheet);
        Sheet.Set propSet = new Sheet.Set();
        propSet.setName("structure");
        propSet.setDisplayName(Bundle.PROPSET_Structure());
        sheet.put(propSet);

        propSet.put(new PropertySupport.ReadOnly<String>(
                "graph", String.class, Bundle.PROPNAME_Graph(), Bundle.PROPDESC_Graph()
        ) {
            @Override
            public String getValue() throws IllegalAccessException, InvocationTargetException {
                return result.getOwner().getDisplayName();
            }
        });
        /*
        propSet.put(new PropertySupport.ReadOnly<String>(
                "group", String.class, Bundle.PROPNAME_Group(), Bundle.PROPDESC_Group()
        ) {
            @Override
            public String getValue() throws IllegalAccessException, InvocationTargetException {
                return result.getOwner().getData().getParent().getName();
            }
        });
        */

        InputGraph parent = (InputGraph) result.getOwner().getData();
        if (parent != null) {
            content.add(parent);
        }
        content.add(result);
        content.add(new OpenAndSelect());
        updateNodeCookie();
        setName(Bundle.FMT_NodeName(result.getNodeId(), result.getItemProperties().get(KnownPropertyNames.PROPNAME_NAME)));
    }

    private void updateNodeCookie() {
        GraphItem ow = result.getOwner();
        InputGraph in = (InputGraph) ow.getData();
        if (in != null) {
            InputNode n = in.getNode(result.getNodeId());
            if (n != null) {
                node = new ContextNode(new NodeContext(n, in, in.getGroup()));
                content.add(node);
            }
        }
    }

    private static final RequestProcessor OPEN_RP = new RequestProcessor("open search item", 1);

    @Override
    public Action getPreferredAction() {
        return ActionUtils.findAction("System", "org.openide.actions.OpenAction", Lookup.EMPTY);
    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> aa = new ArrayList<>(Arrays.asList(super.getActions(context)));
        aa.addAll(Arrays.asList(
                Actions.forID("Diagram", "org.graalvm.visualizer.search.gotonode"),
                Actions.forID("Diagram", "org.graalvm.visualizer.search.extractnodes")
        ));
        return aa.toArray(new Action[aa.size()]);

    }

    @Override
    public boolean canDestroy() {
        return true;
    }

    @Override
    public void destroy() throws IOException {
        results.remove(result);
    }

    @NbBundle.Messages({
            "# {0} - node Id",
            "WARNING_NodeNotVisible=Node {0} is not visible in the target graph. Check filters."
    })
    class OpenAndSelect extends AbstractAction implements Openable {

        public OpenAndSelect() {
        }

        @Override
        public void open() {
            GraphItem owner = result.getOwner();
            owner.resolve(null).thenAcceptAsync(this::openTargetGraph, OPEN_RP);
        }

        private void openTargetGraph(FolderElement fe) {
            if (!(fe instanceof InputGraph)) {
                return;
            }
            InputGraph ig = (InputGraph) fe;

            int id = result.getNodeId();
            InputNode node = ig.getNode(id);
            if (node == null) {
                return;
            }
            GraphViewer vwr = Lookup.getDefault().lookup(GraphViewer.class);
            vwr.view((s, g) -> navigateInGraph(g, node), ig, false, true);
        }

        private void navigateInGraph(InputGraphProvider g, InputNode node) {
            DiagramViewer dgw = (DiagramViewer) g;
            Collection<InputNode> col = Collections.singleton(node);
            Collection<Figure> figs = dgw.figuresForNodes(col);
            if (figs.isEmpty()) {
                StatusDisplayer.getDefault().setStatusText(Bundle.WARNING_NodeNotVisible(node.getId()), 3 /* ??? */);
            }
            g.setSelectedNodes(Collections.singleton(node));
            TopComponent tc = dgw.getLookup().lookup(TopComponent.class);
            if (tc != null) {
                tc.requestFocus();
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            open();
        }
    }
}
