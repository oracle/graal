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

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.source.FileRegistry.FileRegistryListener;
import org.netbeans.api.actions.Openable;
import org.openide.filesystems.FileObject;
import org.openide.util.NbPreferences;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.ServiceProvider;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Represents source location(s) of a Node or a set of nodes. Maintains the current
 * frame information, fires change events when set of nodes, or the selected
 * frame changes. Service is registered in the default Lookup.
 * <p/>
 * The graph and graphSource properties are automatically derived from selected
 * Nodes. It is an error to make context using nodes from different InputGraphs.
 */
@ServiceProvider(service = NodeLocationContext.class)
public class NodeLocationContext {
    public static final String PROP_PREFER_GUEST_LANGUAGE = "preferGuestLanguage"; // NOI18N
    public static final String PROP_SELECTED_LANGUAGE = "selectedLanguage"; // NOI18N
    private InputGraph graph;
    private GraphSource graphSource;
    private String mimeType;

    /**
     * The selected Nodes.
     */
    private Collection<InputNode> nodes = Collections.emptyList();

    /**
     * Context nodes, in a graph
     */
    private Collection<InputNode> contextNodes = Collections.emptyList();

    /**
     * The current node.
     * If {@link #nodes} is replaced, the {@link #currentNode} may change if
     * the current value is not among the new value for {@link #nodes}.
     */
    private InputNode currentNode;

    private final Collection<NodeLocationListener> listeners = new ArrayList<>();

    /**
     * The selected / active frame. Must be for one of the selected nodes.
     */
    private NodeStack.Frame selectedFrame;

    /**
     * Frames for individual languages for the selected node and frame level.
     */
    private Map<String, NodeStack.Frame> langFrames = new HashMap<>();

    private final PropertyChangeSupport supp = new PropertyChangeSupport(this);

    /**
     * If true, will try to select the guest language, if it is present
     * at the frame level.
     */
    private boolean preferGuestLanguage;

    private FileRegistryListener resolveL = new FileRegistryListener() {
        @Override
        public void filesResolved(FileRegistry.FileRegistryEvent ev) {
            fireStackResolved(ev.getResolvedKeys());
        }
    };

    private Preferences prefs = NbPreferences.forModule(NodeLocationContext.class);

    public void addPropertyChangeListener(PropertyChangeListener l) {
        supp.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        supp.removePropertyChangeListener(listener);
    }

    public boolean isPreferGuestLanguage() {
        return preferGuestLanguage;
    }

    public void setPreferGuestLanguage(boolean preferGuestLanguage) {
        boolean ch = this.preferGuestLanguage != preferGuestLanguage;
        this.preferGuestLanguage = preferGuestLanguage;
        if (ch) {
            supp.firePropertyChange(PROP_PREFER_GUEST_LANGUAGE, !preferGuestLanguage, preferGuestLanguage);
        }
    }

    private void fireStackResolved(Collection<FileKey> keys) {
        Set<NodeStack.Frame> frames = new HashSet<>();
        Set<InputNode> nodes;
        GraphSource src;
        synchronized (this) {
            src = getGraphSource();
            nodes = new HashSet<>(this.nodes);
        }
        if (src == null) {
            return;
        }
        for (InputNode n : nodes) {
            NodeStack st = src.getNodeStack(n);
            if (st != null) {
                for (NodeStack.Frame f : st) {
                    if (keys.contains(f.getLocation().getFile())) {
                        frames.add(f);
                    }
                }
            }
        }
        fireWithListeners(false, (NodeLocationListener[] ll) -> {
            NodeLocationEvent ev = new NodeLocationEvent(this, frames);
            for (NodeLocationListener l : ll) {
                l.locationsResolved(ev);
            }
        });
    }

    public NodeLocationContext() {
        FileRegistry.getInstance().addFileRegistryListener(
                WeakListeners.create(FileRegistryListener.class,
                        resolveL, FileRegistry.getInstance())
        );
    }

    public void addNodeLocationListener(NodeLocationListener l) {
        synchronized (this) {
            listeners.add(l);
        }
    }

    public synchronized InputGraph getGraph() {
        return graph;
    }

    public synchronized GraphSource getGraphSource() {
        return graphSource;
    }

    public void removeNodeLocationListener(NodeLocationListener l) {
        synchronized (this) {
            listeners.remove(l);
        }
    }

    public void setSelectedLanguage(String mime) {
        String oldMime;
        synchronized (this) {
            if (Objects.equals(this.mimeType, mime)) {
                return;
            }
            oldMime = this.mimeType;
            this.mimeType = mime;
        }
        supp.firePropertyChange(PROP_SELECTED_LANGUAGE, oldMime, mime);
    }

    public synchronized String getSelectedLanguage() {
        return mimeType;
    }

    public List<Location> getFileLocations(FileObject f, boolean nodePresent) {
        GraphSource s;
        synchronized (this) {
            if ((s = graphSource) == null) {
                return Collections.emptyList();
            }
        }
        return s.getFileLocations(f, nodePresent);
    }

    public NodeStack getStack(InputNode n) {
        return getStack(n, getSelectedLanguage());
    }

    public NodeStack getStack(InputNode n, String lng) {
        GraphSource gs;
        synchronized (this) {
            if (graph == null || !graph.getNodes().contains(n)) {
                return null;
            }
            gs = graphSource;
        }
        return gs.getNodeStack(n, lng);
    }

    public synchronized Collection<InputNode> getGraphNodes() {
        return Collections.unmodifiableCollection(nodes);
    }

    public synchronized NodeStack.Frame getSelectedFrame() {
        return selectedFrame;
    }

    public synchronized Location getSelectedLocation() {
        return selectedFrame == null ? null : selectedFrame.getLocation();
    }

    public void setSelectedLocation(NodeStack.Frame location) {
        InputNode n = null;
        if (location != null) {
            n = location.getNode();
            if (!getGraphNodes().contains(n)) {
                // not within the current context
                return;
            }
        }

        synchronized (this) {
            if (n != null && !nodes.contains(n) || (selectedFrame == location)) {
                return;
            }
            this.selectedFrame = location;
        }
        if (location == null) {
            return;
        }
        setSelectedLanguage(location.getLocation().getMimeType());
        fireWithListeners(false, (NodeLocationListener[] ll) -> {
            NodeLocationEvent ev = new NodeLocationEvent(this, location);
            for (NodeLocationListener l : ll) {
                l.selectedLocationChanged(ev);
            }

        });

        InputNode node = location.getNode();
        setCurrentNode(node, location);
        // temp:
        Openable o = location.getLookup().lookup(Openable.class);
        if (o != null) {
            o.open();
        }
    }

    private void fireWithListeners(boolean always, Consumer<NodeLocationListener[]> firer) {
        NodeLocationListener[] ll = null;
        synchronized (this) {
            if (listeners.isEmpty()) {
                if (!always) {
                    return;
                }
            } else {
                ll = listeners.toArray(new NodeLocationListener[listeners.size()]);
            }
        }
        firer.accept(ll);
    }

    public synchronized InputNode getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(InputNode node, NodeStack.Frame nFrame) {
        setNodes0(node, nFrame, null);
    }

    private void setNodes0(InputNode node, NodeStack.Frame nFrame, Collection<InputNode> selNodes) {
        assert nFrame == null || nFrame.getNode() == node;
        InputNode previousNode;
        NodeStack.Frame oldFrame;
        GraphSource s;
        synchronized (this) {
            s = graphSource;
            if (s == null) {
                return;
            }
            oldFrame = selectedFrame;
        }
        if (nFrame == null) {
            NodeStack ns = s.getNodeStack(node, getSelectedLanguage());
            if (ns == null) {
                ns = s.getNodeStack(node, null);
            }
            if (oldFrame != null) {
                nFrame = oldFrame.findPeerFrame(ns);
            }
            if (nFrame == null && ns != null) {
                nFrame = ns.top();
            }
        }
        synchronized (this) {
            if (node == currentNode && nFrame == selectedFrame) {
                return;
            }
            selectedFrame = nFrame;
            previousNode = currentNode;
            if (!this.nodes.contains(node)) {
                assert false;
                return;
            }
            if (node == previousNode) {
                return;
            }
            this.currentNode = node;
        }
        final NodeStack.Frame fNewFrame = nFrame;
        fireWithListeners(false, (NodeLocationListener[] ll) -> {
            if (fNewFrame != null) {
                setSelectedLanguage(fNewFrame.getStack().getMime());
            }
            NodeLocationEvent evt = new NodeLocationEvent(this, nodes, node, selectedFrame);
            if (selNodes != null) {
                for (NodeLocationListener l : ll) {
                    l.nodesChanged(evt);
                }

            }
            if (node != previousNode) {
                for (NodeLocationListener l : ll) {
                    l.selectedNodeChanged(evt);
                }
            }
            if (oldFrame != fNewFrame) {
                evt = new NodeLocationEvent(this, fNewFrame);
                for (NodeLocationListener l : ll) {
                    l.selectedLocationChanged(evt);
                }
            }
        });
    }

    public void setSelectedNodes(Collection<InputNode> nodes) {
        setSelectedNodes0(nodes, false);
    }

    private void setSelectedNodes0(Collection<InputNode> nodes, boolean fireGraph) {
        Set<InputNode> newNodes = new HashSet<>(nodes);
        NodeStack.Frame oldFrame;
        NodeStack.Frame nFrame;
        InputNode selNode;
        boolean reselectNode = false;
        synchronized (this) {
            GraphSource src;
            if (graph == null) {
                newNodes = Collections.emptySet();
                src = null;
            } else if (!fireGraph && newNodes.equals(this.nodes)) {
                return;
            } else {
                src = GraphSource.getGraphSource(graph);
            }
            oldFrame = selectedFrame;
            this.nodes = newNodes;
            this.graphSource = src;

            if (!newNodes.isEmpty()) {
                if (oldFrame == null || !newNodes.contains(oldFrame.getNode())) {
                    reselectNode = true;
                }
            } else {
                selectedFrame = null;
                currentNode = null;
            }
            selNode = currentNode;
            nFrame = selectedFrame;
        }
        if (reselectNode && !newNodes.isEmpty()) {
            setNodes0(nodes.iterator().next(), null, nodes);
            return;
        }
        // the following applies only if not reselecting a node (= new nodes contains
        // the old one) OR newNodes is empty.
        final Collection<InputNode> fNodes = newNodes;
        fireWithListeners(false, (NodeLocationListener[] ll) -> {
            NodeLocationEvent evt = new NodeLocationEvent(this, fNodes, selNode, nFrame);
            for (NodeLocationListener l : ll) {
                l.nodesChanged(evt);
            }
        });
    }

    public void setGraphContext(Collection<InputNode> nodes) {
        setGraphContext(this.graph, nodes);
    }

    public void setGraphContext(InputGraph graph, Collection<InputNode> nodes) {
        Set<InputNode> newNodes = new HashSet<>(nodes);
        synchronized (this) {
            if ((this.graph == graph) && newNodes.equals(this.nodes)) {
                return;
            }
            this.graph = graph;
            this.graphSource = null;
            this.contextNodes = new HashSet<>(nodes);
        }
        setSelectedNodes0(nodes, true);
    }

    public synchronized Collection<InputNode> getContextNodes() {
        return Collections.unmodifiableCollection(contextNodes);
    }
}
