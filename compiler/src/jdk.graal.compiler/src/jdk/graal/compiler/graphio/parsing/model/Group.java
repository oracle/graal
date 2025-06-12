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
package jdk.graal.compiler.graphio.parsing.model;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NAME;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_TYPE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class Group extends AbstractMutableDocumentItem<Group> implements
                ChangedEventProvider<Group>, GraphContainer, Folder, FolderElement, DumpedElement {

    private final List<FolderElement> elements;
    private final List<InputGraph> graphs;

    private InputMethod method;
    private final transient Object id;

    private final transient ChangedEvent<Group> changedEvent;
    private final transient ChangedEvent<Group> propertyChangedEvent;
    private Folder parent;

    static final AtomicLong uniqueIDGenerator = new AtomicLong(1);

    public Group(Folder parent) {
        this(parent, null);
    }

    @SuppressWarnings("this-escape")
    public Group(Folder parent, Object id) {
        // Ensure that name and type are never null
        super(Properties.newProperties(PROPNAME_NAME, "", PROPNAME_TYPE, ""));
        if (id == null) {
            this.id = uniqueIDGenerator.getAndIncrement();
        } else {
            this.id = id;
        }
        elements = new ArrayList<>();
        graphs = new ArrayList<>();
        this.parent = parent;
        this.changedEvent = new ChangedEvent<>(this);
        this.propertyChangedEvent = new ChangedEvent<>(this);
    }

    @Override
    public ChangedEvent<Group> getPropertyChangedEvent() {
        return propertyChangedEvent;
    }

    @Override
    public Group getContentOwner() {
        return this;
    }

    /**
     * @return true for placeholder groups created during load
     */
    boolean isPlaceholderGroup() {
        return parent == null;
    }

    @Override
    public Object getID() {
        return id;
    }

    public void fireChangedEvent() {
        changedEvent.fire();
    }

    public void setMethod(InputMethod method) {
        this.method = method;
    }

    public InputMethod getMethod() {
        return method;
    }

    @Override
    public ChangedEvent<Group> getChangedEvent() {
        return changedEvent;
    }

    @Override
    public List<FolderElement> getElements() {
        synchronized (this) {
            return List.copyOf(getElementsInternal());
        }
    }

    @Override
    public int getGraphsCount() {
        synchronized (this) {
            return graphs.size();
        }
    }

    @Override
    public void addElement(FolderElement element) {
        synchronized (this) {
            elements.add(element);
            if (element instanceof InputGraph) {
                graphs.add((InputGraph) element);
            }
            if (element.getParent() == null) {
                element.setParent(this);
            }
        }
        changedEvent.fire();
    }

    public void addElements(List<? extends FolderElement> newElements) {
        if (newElements.isEmpty()) {
            return;
        }
        synchronized (this) {
            for (FolderElement element : newElements) {
                elements.add(element);
                if (element instanceof InputGraph) {
                    graphs.add((InputGraph) element);
                }
                if (element.getParent() == null) {
                    element.setParent(this);
                }
            }
        }
        changedEvent.fire();
        GraphDocument owner = getOwner();
        if (owner != null) {
            owner.fireDataAdded(new ArrayList<>(newElements));
        }
    }

    /**
     * Returns the current elements list. Important note: this implementation is not synchronized
     * and does NOT copy the elements collection; caller must synchronize the operation, or make a
     * safe copy
     *
     * @return child elements.
     */
    protected List<? extends FolderElement> getElementsInternal() {
        return elements;
    }

    /**
     * Returns IDs of all nodes in all contained graphs. May return a value without loading all the
     * graph data.
     *
     * @return set of node IDs.
     */
    @Override
    public Set<Integer> getChildNodeIds() {
        Set<Integer> res = new LinkedHashSet<>();
        for (InputGraph g : getGraphs()) {
            res.addAll(g.getNodeIds());
        }
        return res;
    }

    /**
     * Returns nodes of all child graphs. Loads full graph data, if necessary; use with care.
     *
     * @return set of all nodes.
     */
    @Override
    public Set<InputNode> getChildNodes() {
        Set<InputNode> res = new LinkedHashSet<>();
        for (InputGraph g : getGraphs()) {
            res.addAll(g.getNodes());
        }
        return res;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Group@").append(Integer.toHexString(System.identityHashCode(this))).append(getProperties()).append("\n");
        synchronized (this) {
            for (FolderElement g : getElementsInternal()) {
                sb.append(g.toString());
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public String getName() {
        return getProperties().get(PROPNAME_NAME, String.class);
    }

    @Override
    public String getType() {
        return getProperties().get(PROPNAME_TYPE, String.class);

    }

    @Override
    public boolean accept(InputGraph g) {
        return true;
    }

    InputGraph getPrev(InputGraph graph) {
        InputGraph lastGraph = null;
        synchronized (this) {
            for (FolderElement e : getElementsInternal()) {
                if (e == graph) {
                    return lastGraph;
                }
                if (e instanceof InputGraph) {
                    InputGraph candidate = (InputGraph) e;
                    if (Objects.equals(candidate.getGraphType(), graph.getGraphType())) {
                        lastGraph = candidate;
                    }
                }
            }
        }
        return null;
    }

    InputGraph getNext(InputGraph graph) {
        boolean found = false;
        synchronized (this) {
            for (FolderElement e : getElementsInternal()) {
                if (e == graph) {
                    found = true;
                } else if (found && e instanceof InputGraph) {
                    InputGraph candidate = (InputGraph) e;
                    if (Objects.equals(candidate.getGraphType(), graph.getGraphType())) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public InputGraph getLastGraph() {
        List<InputGraph> l = getGraphs();
        return l.get(l.size() - 1);
    }

    @Override
    public Folder getParent() {
        return parent;
    }

    @Override
    public void removeElement(FolderElement element) {
        synchronized (this) {
            if (!elements.remove(element)) {
                return;
            }
            if (element instanceof InputGraph) {
                graphs.remove(element);
            }
        }
        changedEvent.fire();
        notifyContentRemoved(Collections.singleton(element));
    }

    protected void notifyContentRemoved(Collection<? extends FolderElement> removed) {
        GraphDocument gd = getOwner();
        if (gd != null) {
            gd.fireDataRemoved(removed);
        }
    }

    public void removeAll() {
        synchronized (this) {
            if (elements.isEmpty()) {
                return;
            }
            elements.clear();
            graphs.clear();
        }
        changedEvent.fire();
    }

    /**
     * Returns graph children. Note that if a subclass overrides this method, it may need also
     * override {@link #getGraphsCount()}, which directly accesses the graph collection.
     *
     * @return child graphs (not groups)
     */
    @Override
    public synchronized List<InputGraph> getGraphs() {
        return new ArrayList<>(graphs);
    }

    @Override
    public void setParent(Folder parent) {
        this.parent = parent;
    }

    /**
     * Determines whether the InputNode changed between the base and specific InputGraph. Both
     * graphs must belong to this Group.
     * <p/>
     * For detailed description, see {@link InputGraph#isNodeChanged(int)}.
     *
     * @param base baseline
     * @param to the target graph
     * @param nodeId ID of the node
     * @return true, if the node has been changed between base and 'to' graph.s
     */
    @Override
    public boolean isNodeChanged(InputGraph base, InputGraph to, int nodeId) {
        List<InputGraph> inputGraphs = getGraphs();
        int fromIndex = inputGraphs.indexOf(base);
        int toIndex = inputGraphs.indexOf(to);
        assert fromIndex != -1 && toIndex >= fromIndex;
        if (fromIndex == toIndex) {
            return false;
        }
        for (int i = fromIndex + 1; i <= toIndex; i++) {
            InputGraph g = inputGraphs.get(i);
            if (g.isDuplicate()) {
                continue;
            }
            if (g.isNodeChanged(nodeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if this group is a parent of the other element.
     *
     * @param other child element to test
     * @return true, if this Group is the parent.
     */
    @Override
    public final boolean isParentOf(FolderElement other) {
        FolderElement f = other;
        while (f != null) {
            if (f == this) {
                return true;
            }
            f = f.getParent();
        }
        return false;
    }

    /**
     * Special mixin interface, which indicates the Group contents may not be fetched. The
     * LazyContent object has two states:
     * <ul>
     * <li>incomplete, when it serves only partial or no nested data. Properties for the object
     * should be all available.
     * <li>complete, when it contains complete set of directly nested data
     * </ul>
     * Contents of the LazyContent may be eventually released, reverting the state into incomplete;
     * an {@link ChangedEvent} must be fired in such case.
     *
     * @param <T> Type of lazy content
     */
    public interface LazyContent<T> {
        /**
         * Indicates that whether the contents was loaded.
         *
         * @return if true, the contents was loaded fully
         */
        boolean isComplete();

        /**
         * Fills the content, and returns the resulting data. Note that potentially the content may
         * contain another LazyContent implementations.
         * <p/>
         * In addition to returning the contents, the implementation must fire a
         * {@link ChangedEvent} upon completing the data, <b>after</b> {@link #isComplete} changes
         * to true. If the implementation supports release of the nested data, the data must not be
         * released until after event is delivered to all listeners.
         * <p/>
         * If {@link Feedback#isCancelled()} becomes true, the implementation should terminate as
         * soon as possible, returning a Future whose {@link Future#isCancelled} is true.
         *
         * @param feedback optional callback to be invoked to report progress/
         * @return handle to contents of the group.
         */
        Future<T> completeContents(Feedback feedback);

        T partialData();
    }

    /**
     * Progress feedback from loading content lazily.
     */
    public interface Feedback {
        /**
         * Reports progress. WorkDone represents the amount of work done so far, totalWork is the
         * total work known to be done at this time. Note that totalWork can change throghough the
         * computation. Optional description provides additional message to the user.
         *
         * @param workDone work already done
         * @param totalWork total work
         * @param description message, possibly {@code null}
         */
        void reportProgress(int workDone, int totalWork, String description);

        /**
         * Signals that the computation should be cancelled.
         *
         * @return true, if the work should be aborted
         */
        boolean isCancelled();

        /**
         * Notifies that the work has completed. Must be called for both successful or aborted work.
         */
        void finish();

        /**
         * Reports loading error. Since the error may occur in the middle of construction of a Group
         * or Graph, and the item may not be (yet) consistent, the error item is only represented by
         * its name. Parents are reported up to (not including) the GraphDocument.
         *
         * @param parents parents of the error item.
         * @param name name (id) of the erroneous item
         * @param errorMessage error report
         */
        void reportError(List<FolderElement> parents, List<String> parentNames, String name, String errorMessage);
    }
}
