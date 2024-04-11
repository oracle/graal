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
package org.graalvm.visualizer.data.serialization.lazy;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;

import jdk.graal.compiler.graphio.parsing.*;
import jdk.graal.compiler.graphio.parsing.BinaryReader.Method;
import jdk.graal.compiler.graphio.parsing.model.*;
import jdk.graal.compiler.graphio.parsing.model.Group.Feedback;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.graphio.parsing.model.Properties.ArrayProperties;

/**
 * Builder to build a single Group. The Builder creates offspring Builders to
 * load a lazy graph (will ignore contents) or load full information. The
 * delegating wrapper is responsible for restoring appropriate delegate when the
 * nested block (Group, Graph) finishes. Also maintains context stack.
 */
public class SingleGroupBuilder extends DelegatingBuilder {

    private static final Logger LOG = Logger.getLogger(SingleGroupBuilder.class.getName());

    private static int largeGraphThreshold = StreamEntry.LARGE_ENTRY_THRESHOLD;

    /**
     * Result items to be reported
     */
    private final List<FolderElement> items = new ArrayList<>();

    /**
     * Group to complete
     */
    private final Group toComplete;

    /**
     * The current constant pool
     */
    private ConstantPool pool;
    private final Env env;
    private final GraphDocument rootDocument;
    private final StreamIndex streamIndex;
    private final BinarySource dataSource;
    private final long startOffset;
    private final boolean firstExpand;
    private final Root rootBuilder;
    private final StreamEntry rootEntry;
    private final Feedback feedback;
    private GroupCompleter completer;

    private GraphMetadata gInfo;

    private long rootStartOffset;

    // diagnostics only
    private int graphIndex = -1;

    /**
     * Graph nesting level. Group children have level 1. Graphs with level > 1
     * are nested in outer graph' node properties.
     */
    private int graphLevel;

    /**
     * Nesting group level. The expanding group has level 1. Graphs at level 1
     * can be lazy-loaded or fully read.
     */
    private int groupLevel;

    /**
     * Current graph builder for the opened direct child.
     */
    private ModelBuilder rootGraphBuilder;

    /**
     * Properties of individual InputNodes in the current compilation. Used to
     * detect property changes between graphs/phases
     */
    private Map<Integer, Properties> nodeProperties = new HashMap<>();

    private final Map<Integer, List<ModelBuilder.EdgeInfo>> nodeEdges = new HashMap<>();

    private void registerNodeEdges(int nodeId, List<ModelBuilder.EdgeInfo> props) {
        List<ModelBuilder.EdgeInfo> oldProps = nodeEdges.get(nodeId);
        if (oldProps != null && !oldProps.equals(props)) {
            gInfo.nodeChanged(nodeId);
        }
        nodeEdges.put(nodeId, props);
    }

    private Map<String, byte[]> lastDigests = new HashMap<>();

    private Map<String, Map<Integer, Properties>> lastTypeProperties = new HashMap<>();

    /**
     * Saved context
     */
    private final Deque<NestedData> levels = new LinkedList<>();

    /**
     * If true, counts nodes and edges in graph. Valid only for graphLevel == 1
     */
    private boolean collectCounts;
    /**
     * If true, counts nodes and edges in graph. Valid only for graphLevel == 1
     */
    private boolean collectChanges;

    private StreamEntry entry;

    private final Consumer<List<? extends FolderElement>> partialCallback;

    /**
     * Helper class, saves one level info on creation + clears out the data.
     * Restores data in its restore. Used to save context for group/graph.
     */
    class NestedData {

        Map<Integer, Properties> props;
        Map<String, Map<Integer, Properties>> typeProps;
        Map<String, byte[]> dgs;
        GraphMetadata meta;
        StreamEntry e;
        GroupCompleter c;
        boolean changes;
        boolean counts;
        int gIndex;

        public NestedData() {
            this.props = nodeProperties;
            this.typeProps = lastTypeProperties;
            this.meta = gInfo;
            this.e = entry;
            this.changes = collectChanges;
            this.counts = collectCounts;
            this.gIndex = graphIndex;
            this.c = completer;
            this.dgs = lastDigests;

            if (graphLevel > 1) {
                nodeProperties = new HashMap<>();
                lastTypeProperties = new HashMap<>();
                lastDigests = new HashMap<>();
            }
            gInfo = null;
            entry = null;
            completer = null;
        }

        void restore() {
            lastDigests = dgs;
            completer = c;
            nodeProperties = props;
            lastTypeProperties = typeProps;
            gInfo = meta;
            entry = e;
            collectChanges = changes;
            collectCounts = counts;
            graphIndex = gIndex;
        }
    }

    private final Logger instLog;

    public SingleGroupBuilder(Group toComplete,
                              Env env,
                              BinarySource dataSource,
                              StreamIndex streamIndex,
                              StreamEntry entry,
                              Feedback feedback,
                              boolean firstExpand,
                              Consumer<List<? extends FolderElement>> partialCallback) {
        this.env = env;
        this.toComplete = toComplete;
        this.pool = entry.getInitialPool().copy();
        this.rootDocument = new GraphDocument();
        this.streamIndex = streamIndex;
        this.dataSource = dataSource;
        this.startOffset = entry.getStart();
        this.firstExpand = firstExpand;
        this.feedback = feedback;
        this.rootEntry = entry;
        this.partialCallback = partialCallback;
        this.entry = rootEntry;

        instLog = Logger.getLogger(LOG.getName() + "." + Integer.toHexString(System.identityHashCode(this)));
        instLog.log(Level.FINE, "Reading group {0}, entry {1}, dataSource {2}", new Object[]{toComplete.getName(), entry, dataSource});
        rootBuilder = new Root();
        delegateTo(rootBuilder);
    }

    // test only
    static void setLargeEntryThreshold(int size) {
        largeGraphThreshold = size;
    }

    public List<? extends FolderElement> getItems() {
        synchronized (items) {
            return new ArrayList<>(items);
        }
    }

    @Override
    public void endGroup() {
        super.endGroup();
        levels.pop().restore();
        groupLevel--;
    }

    @Override
    @CheckForNull
    public Group startGroup() {
        levels.push(new NestedData());
        groupLevel++;
        return super.startGroup();
    }

    @Override
    public void graphContentDigest(byte[] digest) {
        InputGraph g;
        if (rootGraphBuilder == null) {
            g = null;
        } else {
            g = rootGraphBuilder.graph();
        }
        if (g == null) {
            return;
        }
        String t = g.getGraphType();
        if (t == null) {
            t = ""; // NOI18N
        }
        byte[] prevDigest = lastDigests.put(t, digest);
        if (Arrays.equals(prevDigest, digest)) {
            markGraphDuplicate();
        }
    }

    boolean isRootChildGraph() {
        return groupLevel == 1 && graphLevel == 1;
    }

    @Override
    public void startGraphContents(InputGraph g) {
        if (g == null) {
            super.startGraphContents(g);
            return;
        }
        super.startGraphContents(g);
        nodeProperties = lastTypeProperties.computeIfAbsent(g.getGraphType(), (gt) -> new HashMap<>());
    }

    @Override
    @CheckForNull
    public InputGraph startGraph(int dumpId, String format, Object[] args) {
        graphIndex++;
        levels.push(new NestedData());
        graphLevel++;
        return super.startGraph(dumpId, format, args);
    }

    private void registerNodeProperties(int nodeId, Properties props) {
        Properties oldProps = nodeProperties.get(nodeId);
        if (oldProps != null && !oldProps.equals(props)) {
            gInfo.nodeChanged(nodeId);
        }
        nodeProperties.put(nodeId, props);
    }

    @Override
    @CheckForNull
    public InputGraph endGraph() {
        if (instLog.isLoggable(Level.FINE)) {
            if (entry == null) {
                instLog.log(Level.FINE, "endSubGraph, level = {0}", graphLevel);
            } else {
                instLog.log(Level.FINE, "endGraph, range = {0}-{1}", new Object[]{entry.getStart(), entry.getEnd()});
            }
        }

        switch (graphLevel) {
            case 1:
                instLog.log(Level.FINER, "Switch to root builder");
                delegateTo(rootBuilder);
                break;
            case 2:
                // time to switch from a subgraph to the group's child builder
                instLog.log(Level.FINER, "Switch to lazy builder");
                delegateTo(rootGraphBuilder);
            default:
        }
        levels.pop().restore();
        graphLevel--;
        return super.endGraph();
    }

    @Override
    public void startRoot() {
        rootStartOffset = dataSource.getMark();
        streamIndex.waitOffset(rootStartOffset);
        long end = rootEntry.getEnd();
        if (end > -1 && rootStartOffset > end) {
            instLog.log(Level.FINER, "Encountered root after rootEntry {0} at {1}, skip rest of stream", new Object[]{rootEntry, rootStartOffset});
            throw new SkipRootException(entry.getStart(), -1, null);
        }
        super.startRoot();
    }

    private void waitEntryFinished() {
        synchronized (entry) {
            while (!entry.isFinished()) {
                try {
                    instLog.log(Level.FINER, "Waiting for entry {0}", entry);
                    entry.beforeWait();
                    entry.wait();
                } catch (InterruptedException ex) {
                    Logger.getLogger(SingleGroupBuilder.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    /**
     * Root builder, builds full model. Identifies large graphs and loads them
     * as lazy. PENDING - maybe identify also large groups and load them as
     * lazy.
     */
    class Root extends LazyModelBuilder {

        private final Properties trash = new ArrayProperties();

        public Root() {
            super(rootDocument, new ParseMonitorBridge(entry, feedback, dataSource));
        }

        @Override
        public ParseMonitor getMonitor() {
            return super.getMonitor();
        }

        @Override
        public void startRoot() {
            //rootStartPos = dataSource.getMark();
            super.startRoot();
        }

        @Override
        public void startGroupContent() {
            if (groupLevel > 1) {
                instLog.log(Level.FINE, "Starting group {0}, ", folder().getName());
            }
            super.startGroupContent();
            if (groupLevel > 1 && folder() instanceof LazyGroup) {
                skipRoot();
            }
        }

        @Override
        protected Properties getProperties() {
            if (getEntity() == toComplete) {
                return trash;
            } else {
                return super.getProperties();
            }
        }

        @Override
        protected Group createGroup(Folder parent) {
            if (parent == rootDocument) {
                // do not create a Group for the completed instance, use the actual object
                entry = rootEntry;
                return toComplete;
            }
            entry = streamIndex.get(rootStartOffset);
            instLog.log(Level.FINER, "Create group entry {0}", entry);
            // unfinished entries have size MAX_VALUE
            if (entry.size() < largeGraphThreshold) {
                return super.createGroup(parent);
            }
            assert completer == null;
            GroupCompleter grc = new GroupCompleter(env, streamIndex, entry);
            completer = grc;
            LazyGroup g = new LazyGroup(folder(), grc, entry);
            completer.attachTo(g, null);
            return g;
        }

        @Override
        public void endGroup() {
            waitEntryFinished();
            super.endGroup();
        }

        @Override
        protected void registerToParent(Folder parent, FolderElement item) {
            if (item == toComplete) {
                return;
            }
            if (parent == toComplete) {
                // do not put items into the completed group, fill them all at once at the end
                item.setParent(parent);
                synchronized (items) {
                    items.add(item);
                }
                if (partialCallback != null) {
                    partialCallback.accept(getItems());
                }
            } else {
                // ignore threading, the item should have no listeners yet.
                parent.addElement(item);
            }
        }

        @Override
        protected InputGraph createGraph(Properties.Entity parent, int dumpId, String format, Object[] args) {
            if (rootEntry.size() < largeGraphThreshold && (entry == null || entry.size() < largeGraphThreshold)) {
                // let Graph to keep entire group data in memory
                InputGraph g = new LazyGroup.LoadedGraph(rootEntry, graphLevel == 1 ? gInfo : null, dumpId, format, args);
                instLog.log(Level.FINE, "Create full graph {0}, entry {1}", new Object[]{g.getName(), entry});
                rootGraphBuilder = new FullGraphBuilder(node(), g, folder());
                delegateTo(rootGraphBuilder);
                return g;
            }
            GraphCompleter completer = new GraphCompleter(env, entry);
            LazyGraph g = new LazyGraph(entry, gInfo, completer, dumpId, format, args);
            completer.attachTo(g, g.getName());
            instLog.log(Level.FINE, "Create lazy graph {0}, positions {1}-{2}",
                    new Object[]{g.getName(), entry.getStart(), entry.getEnd()});
            // switch to lazygraph builder
            ChildGraphBuilder gb = new ChildGraphBuilder(g, folder());
            rootGraphBuilder = gb;
            delegateTo(gb);
            return g;
        }

        // XXX pro lazy graph se prepina do child builderu; test na graphLevel patri TAM
        @Override
        public void startGraphContents(InputGraph g) {
            if (graphLevel == 1 && !collectChanges && !(g instanceof LazyGroup.LoadedGraph)) {
                // the graph was already seen, we can safely skip it if it is lazy-loaded, metadata was already collected
                skipRoot();
            }
            super.startGraphContents(g);
        }

        @Override
        @CheckForNull
        public InputGraph startGraph(int dumpId, String format, Object[] args) {
            long pos = rootStartOffset;
            entry = streamIndex.get(pos);
            instLog.log(Level.FINER, "Start contents graph entry {0}", entry);
            waitEntryFinished();
            if (entry.size() > 1024 * 1024) {
                reportState(ModelBuilder.makeGraphName(dumpId, format, args));
            }
            gInfo = entry.getGraphMeta();
            collectCounts = false;
            collectChanges = firstExpand;
            return super.startGraph(dumpId, format, args);
        }

        @Override
        @CheckForNull
        public InputGraph endGraph() {
            InputGraph g = graph();
            if (g instanceof LazyGraph) {
                // avoid call to getNodes() in super
                popContext();
                registerToParent(folder(), g);
            } else {
                g = super.endGraph();
            }
            rootGraphBuilder = null;
            return g;
        }

        @Override
        protected void replacePool(ConstantPool newPool) {
            pool = newPool;
            super.replacePool(newPool);
        }

        @Override
        @NonNull
        public ConstantPool getConstantPool() {
            return pool;
        }

        @Override
        public void markGraphDuplicate() {
            gInfo.markDuplicate();
        }

        @Override
        public void makeBlockEdges() {
            super.makeBlockEdges();
            if (collectChanges) {
                // hook to register graph node's properties, after they were
                // updated with node-to-block properties
                InputGraph g = graph();
                assert !(g instanceof Group.LazyContent);
                for (InputNode n : g.getNodes()) {
                    int nodeId = n.getId();
                    gInfo.addNode(nodeId);
                    registerNodeProperties(nodeId, n.getProperties());
                }
            }
        }

        private void skipRoot() {
            waitEntryFinished();
            ConstantPool nextPool = entry.getSkipPool().copy();
            replacePool(nextPool);
            instLog.log(Level.FINER, "Skipping entry {0}, restart from pool {1}", new Object[]{entry, Integer.toHexString(System.identityHashCode(nextPool))});
            throw new SkipRootException(entry.getStart(), entry.getEnd(), nextPool);
        }
    }

    /**
     * Reads full graphs including the nested ones. Used to read small group
     * child graphs, or their nested children.
     */
    class FullGraphBuilder extends LazyModelBuilder {

        private final InputGraph parentGraph;
        private final InputNode parent;

        public FullGraphBuilder(InputNode parent, InputGraph parentGraph, Folder folder) {
            super(rootDocument, rootBuilder.getMonitor());
            this.parent = parent;
            this.parentGraph = parentGraph;
            if (folder instanceof Group) {
                pushGroup(toComplete, false);
            }
            if (parent != null) {
                pushNode(parent);
            }
            pushGraph(parentGraph);
            // synchronize on the constant pool
            replacePool(SingleGroupBuilder.this.getConstantPool());
        }

        @Override
        protected InputGraph createGraph(Properties.Entity parent, int dumpId, String format, Object[] args) {
            if (instLog.isLoggable(Level.FINE)) {
                instLog.log(Level.FINE, "Create subgraph {0}:{1}, parent {2}, pos {3}", new Object[]{graphLevel, ModelBuilder.makeGraphName(dumpId, format, args), node().getId(), dataSource.getMark()});
            }
            if (parent instanceof InputNode) {
                // nested graph
                return super.doCreateGraph(parent,
                        new GraphBuilder.NestedGraphId(entry, dumpId, format),
                        dumpId, format, args);
            }
            // toplevel graph
            long pos = rootStartOffset;
            StreamEntry graphEntry = streamIndex.get(pos);
            if (graphEntry == null) {
                throw new IllegalStateException();
            }
            return super.doCreateGraph(parent, graphEntry, dumpId, format, args);
        }

        @Override
        @CheckForNull
        public InputGraph endGraph() {
            if (instLog.isLoggable(Level.FINE)) {
                instLog.log(Level.FINE, "End subgraph {0}:{1}, pos {2}", new Object[]{graphLevel + 1, graph().getName(), dataSource.getMark()});
            }
            return super.endGraph();
        }

        @Override
        public void makeBlockEdges() {
            super.makeBlockEdges();
            InputGraph g = parentGraph;
            if (parent != null || g == null) {
                return;
            }
            if (collectChanges && gInfo != null) {
                // hook to register graph node's properties, after they were
                // updated with node-to-block properties
                assert !(g instanceof Group.LazyContent);
                for (InputNode n : g.getNodes()) {
                    int nodeId = n.getId();
                    gInfo.addNode(nodeId);
                    registerNodeProperties(nodeId, n.getProperties());
                }
            }
        }

    }

    /**
     * Builds direct graph child of a group. If this group is processed for the
     * first time, {@link StreamEntry#getGraphMeta()} does not have full
     * information, just number of nodes and edges from the initial scan. In
     * this mode ({@link #collectChanges} == true) the ChildGraphBuilder
     * processes node properties incl. block presence and compares them against
     * previous graph's meta. The preceding graph is either full (data
     * immediately available) or lazy, but its
     * {@link StreamEntry#getGraphMeta()} is already filled in.
     * <p/>
     * In subsequent reads, entire graphs are skipped just after their
     * properties are read.
     */
    class ChildGraphBuilder extends LazyModelBuilder {

        private InputGraph nested;
        private final LazyGraph g;
        private final Map<Integer, Properties> stageProperties = new HashMap<>();
        private String blockName;
        private List<InputGraph> nestedStack;

        public ChildGraphBuilder(LazyGraph g, Folder folder) {
            super(rootDocument, rootBuilder.getMonitor());
            this.g = g;
            pushGroup(toComplete, false);
            if (folder instanceof Group) {
                pushGroup((Group) folder, false);
            }
            pushGraph(g);
            replacePool(SingleGroupBuilder.this.getConstantPool());
        }

        @Override
        @CheckForNull
        public Properties getNodeProperties(int nodeId) {
            return stageProperties.get(nodeId);
            /*
            if (nodeId == 1435) {
                System.err.println("Properties query for node #1435");
                Thread.dumpStack();
            }
            Properties p = stageProperties.get(nodeId);
            return p != null ? p : new Properties();
             */
        }

        @Override
        protected void registerToParent(InputGraph g, InputNode n) {
            stageProperties.put(n.getId(), n.getProperties());
        }

        @Override
        public void addNodeToBlock(int nodeId) {
            updateNodeBlock(nodeId, blockName);
        }

        @Override
        public void addBlockEdge(int from, int to) {
        }

        @Override
        public void startRoot() {
            throw new IllegalStateException();
        }

        @Override
        protected void replacePool(ConstantPool newPool) {
            pool = newPool;
            super.replacePool(newPool);
        }

        @Override
        @NonNull
        public ConstantPool getConstantPool() {
            return pool;
        }

        @Override
        @CheckForNull
        public Group startGroup() {
            throw new IllegalStateException("Unexpected group inside graph");
        }

        // ignored data
        @Override
        public void makeBlockEdges() {
            if (collectChanges) {
                // update after node-to-block assignment
                for (Map.Entry<Integer, Properties> en : stageProperties.entrySet()) {
                    int nodeId = en.getKey();
                    Properties props = en.getValue();
                    registerNodeProperties(nodeId, props);
                }
                HashMap<Integer, ArrayList<EdgeInfo>> newNodeEdges = new HashMap<>();
                if (getInputEdges() != null) {
                    for (EdgeInfo edge : getInputEdges()) {
                        ArrayList<EdgeInfo> edges = newNodeEdges.get(edge.getFrom());
                        if (edges == null) {
                            edges = new ArrayList<>();
                            newNodeEdges.put(edge.getFrom(), edges);
                        }
                        edges.add(edge);
                    }
                }
                if (getSuccessorEdges() != null) {
                    for (EdgeInfo edge : getSuccessorEdges()) {
                        ArrayList<EdgeInfo> edges = newNodeEdges.get(edge.getFrom());
                        if (edges == null) {
                            edges = new ArrayList<>();
                            newNodeEdges.put(edge.getFrom(), edges);
                        }
                        edges.add(edge);
                    }
                }
                for (Integer nodeId : nodeProperties.keySet()) {
                    ArrayList<EdgeInfo> edges = newNodeEdges.get(nodeId);
                    registerNodeEdges(nodeId, edges);
                }
            }
        }

        @Override
        public void endBlock(int id) {
        }

        @Override
        @CheckForNull
        public InputBlock startBlock(String name) {
            blockName = name;
            return null;
        }

        @Override
        public void makeGraphEdges() {
        }

        @Override
        public void startNode(int nodeId, boolean hasPredecessors, NodeClass nodeClass) {
            if (collectCounts) {
                gInfo.addNode(nodeId);
            }
            super.startNode(nodeId, hasPredecessors, nodeClass);
        }

        @Override
        public void markGraphDuplicate() {
            gInfo.markDuplicate();
        }

        @Override
        @CheckForNull
        public InputGraph endGraph() {
            assert nested != null;
            InputGraph n = nested;
            nested = nestedStack == null || nestedStack.isEmpty() ? null : nestedStack.remove(nestedStack.size() - 1);
            instLog.log(Level.FINE, "Resuming operation after property {0}", getNestedProperty());
            popContext();
            return n;
        }

        @Override
        @CheckForNull
        public InputGraph startGraph(int dumpId, String format, Object[] args) {
            assert getNestedProperty() != null;
            instLog.log(Level.FINE, "Ignoring nested graph in property {0}", getNestedProperty());
            if (nested != null) {
                nestedStack = new ArrayList<>();
                nestedStack.add(nested);
            }
            nested = super.startGraph(dumpId, format, args);
            Builder sub = new Ignore(rootDocument(), rootBuilder);
            delegateTo(sub);
            return nested;
        }
    }

    /**
     * Builder which throws everything away, used for PROPERTY_GRAPHs.
     */
    static class Ignore implements Builder {

        final ModelBuilder delegate;
        ConstantPool pool;
        final GraphDocument rootDocument;

        public Ignore(GraphDocument rootDocument, ModelBuilder delegate) {
            this.rootDocument = rootDocument;
            this.delegate = delegate;
        }

        @Override
        public void startDocumentHeader() {
        }

        @Override
        public void endDocumentHeader() {
        }

        @Override
        public void graphContentDigest(byte[] dg) {
        }

        @Override
        public void addBlockEdge(int from, int to) {
        }

        @Override
        public void addNodeToBlock(int nodeId) {
        }

        @Override
        public void end() {
        }

        @Override
        public void endBlock(int id) {
        }

        @Override
        public InputGraph endGraph() {
            return null;
        }

        @Override
        public void endGroup() {
        }

        @Override
        public void endNode(int nodeId) {
        }

        @Override
        @NonNull
        public ConstantPool getConstantPool() {
            return pool;
        }

        @Override
        @CheckForNull
        public Properties getNodeProperties(int nodeId) {
            return null;
        }

        @Override
        public void setPropertySize(int size) {
        }

        @Override
        public void inputEdge(Builder.Port p, int from, int to, char num, int index) {
        }

        @Override
        public void makeBlockEdges() {
        }

        @Override
        public void makeGraphEdges() {
        }

        @Override
        public void markGraphDuplicate() {
        }

        @Override
        public void resetStreamData() {
        }

        @Override
        public GraphDocument rootDocument() {
            return rootDocument;
        }

        @Override
        public void setGroupName(String name, String shortName) {
        }

        @Override
        public void setMethod(String name, String shortName, int bci, Method method) {
        }

        @Override
        public void setNodeName(Builder.NodeClass nodeClass) {
        }

        @Override
        public void setNodeProperty(String key, Object value) {
        }

        @Override
        public void setProperty(String key, Object value) {
        }

        @Override
        public void start() {
        }

        @Override
        @CheckForNull
        public InputBlock startBlock(int id) {
            return null;
        }

        @Override
        @CheckForNull
        public InputBlock startBlock(String name) {
            return null;
        }

        @Override
        @CheckForNull
        public InputGraph startGraph(int dumpId, String format, Object[] args) {
            return null;
        }

        @Override
        public void startGraphContents(InputGraph g) {
        }

        @Override
        @CheckForNull
        public Group startGroup() {
            return null;
        }

        @Override
        public void startGroupContent() {
        }

        @Override
        public void startNestedProperty(String propertyKey) {
        }

        @Override
        public void startNode(int nodeId, boolean hasPredecessors, NodeClass nodeClass) {
        }

        @Override
        public void startRoot() {
        }

        @Override
        public void successorEdge(Builder.Port p, int from, int to, char num, int index) {
        }

        @Override
        public void setModelControl(ModelControl exchg) {
            pool = exchg.getConstantPool();
        }

        @Override
        public NameTranslator prepareNameTranslator() {
            return null;
        }

        @Override
        public void reportLoadingError(String logMessage, List<String> parentNames) {
            if (delegate != null) {
                delegate.reportLoadingError(logMessage, parentNames);
            }
        }
    }
}
