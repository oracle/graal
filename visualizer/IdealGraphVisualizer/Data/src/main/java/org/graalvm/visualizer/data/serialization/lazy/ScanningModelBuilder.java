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
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.netbeans.api.annotations.common.CheckForNull;
import org.openide.util.WeakSet;

import jdk.graal.compiler.graphio.parsing.*;
import jdk.graal.compiler.graphio.parsing.model.*;
import jdk.graal.compiler.graphio.parsing.model.Group.LazyContent;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.graphio.parsing.model.Properties.ArrayProperties;

/**
 * ModelBuilder which only scans the incoming stream and creates lazy-loaded Groups which implement
 * {@link LazyContent} interface. The groups are initially empty, but can be asked to load its
 * data.
 * <p/>
 * Data may load in a separate thread defined by `fetchExecutor', but since the whole data model is
 * single-threaded, modelExecutor is then used to attach the loaded data to the LazyContent group.
 * <p/>
 * This class blocks most of the {@link ModelBuilder} functionality so it creates only a few objects
 * during initial stream reading. It loads just toplevel groups.
 */
public class ScanningModelBuilder extends LazyModelBuilder {
    private static final Logger LOG = Logger.getLogger(ScanningModelBuilder.class.getName());

    private final CachedContent streamContent;
    private final BinarySource dataSource;
    private final Map<Group, GroupCompleter> completors = new LinkedHashMap<>();
    private final ScheduledExecutorService fetchExecutor;
    private final Properties dummyProperties = new ArrayProperties() {
        @Override
        protected void setPropertyInternal(String name, Object value) {

        }
    };

    // for testing
    int groupLevel;
    private int graphLevel;
    private GroupCompleter completer;

    /**
     * Index information for Groups and Graphs in the stream.
     */
    protected final StreamIndex index = new StreamIndex();

    private final Deque<StreamEntry> entryStack = new LinkedList<>();

    private StreamEntry entry;

    private Cleaner cleaner;

    public ScanningModelBuilder(
            BinarySource dataSource,
            CachedContent content,
            GraphDocument rootDocument,
            ParseMonitor monitor,
            ScheduledExecutorService fetchExecutor) {
        this(dataSource, content, rootDocument, monitor, fetchExecutor,
                new StreamPool());
    }

    public ScanningModelBuilder(
            BinarySource dataSource,
            CachedContent content,
            DocumentFactory rootDocumentFactory,
            ParseMonitor monitor,
            ScheduledExecutorService fetchExecutor) {
        this(dataSource, content, rootDocumentFactory, monitor, fetchExecutor, new StreamPool());
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    ScanningModelBuilder(
            BinarySource dataSource,
            CachedContent content,
            GraphDocument rootDocument,
            ParseMonitor monitor,
            ScheduledExecutorService fetchExecutor,
            StreamPool initialPool) {
        super(rootDocument, monitor);
        this.dataSource = dataSource;
        this.streamContent = content;
        replacePool(initialPool);
        this.fetchExecutor = fetchExecutor;
        initCleaner();
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    ScanningModelBuilder(
            BinarySource dataSource,
            CachedContent content,
            DocumentFactory rootDocumentFactory,
            ParseMonitor monitor,
            ScheduledExecutorService fetchExecutor,
            StreamPool initialPool) {
        super(rootDocumentFactory, monitor);
        this.dataSource = dataSource;
        this.streamContent = content;
        replacePool(initialPool);
        this.fetchExecutor = fetchExecutor;
        initCleaner();
    }

    private void initCleaner() {
        if (cleaner == null && rootDocument() != null) {
            rootDocument().getChangedEvent().addListener(cleaner = new Cleaner(rootDocument(), streamContent));
        }
    }

    /**
     * Listener that will clean the cached content when items are removed
     * from the Document.
     * The ScanningModelBuilder contains garbage which should go away after the input
     * stream terminates, but items may be still be present in the document. The cleaner
     * should not reference any data from the reading process except the result
     * toplevel groups.
     */
    private static class Cleaner implements ChangedListener<GraphDocument> {
        private final GraphDocument rootDocument;
        private final CachedContent streamContent;
        private final Set<GraphDocument> documents = new WeakSet<>();

        // @GuardedBy(streamContent)
        final Map<FolderElement, Boolean> scannedGroups = new WeakHashMap<>();

        // @GuardedBy(streamContent)
        long lastGroupOffset;

        // @GuardedBy(streamContent)
        boolean finished;

        public Cleaner(GraphDocument doc, CachedContent streamContent) {
            this.rootDocument = doc;
            this.streamContent = streamContent;
            documents.add(doc);
        }

        @Override
        public void changed(GraphDocument source) {
            Set<? extends FolderElement> c;
            Collection<GraphDocument> docs;
            synchronized (streamContent) {
                c = new HashSet<>(scannedGroups.keySet());
                docs = new ArrayList<>(documents);
            }
            // if true, at least some document still registers some of the elements
            boolean someRemoved = false;
            for (GraphDocument rd : docs) {
                List<? extends FolderElement> els = rd.getElements();
                if (c.removeAll(els)) {
                    someRemoved = true;
                } else {
                    // document has no elements that could interest us:
                    synchronized (streamContent) {
                        rd.getChangedEvent().removeListener(this);
                        documents.remove(rd);
                    }
                }
            }
            Set<GraphDocument> newParents = new HashSet<>();
            for (Iterator<? extends FolderElement> itfe = c.iterator(); itfe.hasNext(); ) {
                FolderElement fe = itfe.next();
                Folder f = fe.getParent();
                if (!docs.contains(f)) {
                    // cross check that the element was not removed from its
                    // new parent either
                    if (f.getElements().contains(fe)) {
                        if (f instanceof GraphDocument) {
                            newParents.add((GraphDocument) f);
                        }
                        // items reparented to other than documents will lock
                        // the content forever. Also retain elements that were re-parented
                        itfe.remove();
                    }
                }
            }
            synchronized (streamContent) {
                scannedGroups.keySet().removeAll(c);
                for (GraphDocument gd : newParents) {
                    if (documents.add(gd)) {
                        gd.getChangedEvent().addListener(this);
                    }
                }
                if (someRemoved || !(scannedGroups.isEmpty() && newParents.isEmpty())) {
                    return;
                }
                // we can now reset the stream content
                streamContent.resetCache(lastGroupOffset);
                if (!streamContent.isOpen() && finished) {
                    rootDocument.getChangedEvent().removeListener(this);
                }
            }
        }

        void bumpOffset(long s) {
            lastGroupOffset = Math.max(s, lastGroupOffset);
        }

    }

    protected StreamPool pool() {
        return (StreamPool) getConstantPool();
    }

    private void documentUpdatedExternally() {
    }

    @Override
    protected void registerToParent(Folder parent, FolderElement element) {
        if (!(parent instanceof GraphDocument)) {
            return;
        }
        if (element instanceof LazyGroup) {
            synchronized (streamContent) {
                cleaner.scannedGroups.put(element, Boolean.TRUE);
                long s = entry.getStart();
                cleaner.bumpOffset(s);
            }
        }
        super.registerToParent(parent, element);
    }

    @Override
    public void makeBlockEdges() {
        // no op
    }

    @Override
    public void addBlockEdge(int from, int to) {
        // no op
    }

    @Override
    public void addNodeToBlock(int nodeId) {
        // no op
    }

    @Override
    @CheckForNull
    public Properties getNodeProperties(int nodeId) {
        return dummyProperties;
    }

    @Override
    @CheckForNull
    public InputBlock startBlock(int id) {
        return null;
    }

    @Override
    public void makeGraphEdges() {
    }

    @Override
    public InputEdge immutableEdge(char fromIndex, char toIndex, int from, int to, int listIndex, String label, String type) {
        return null;
    }

    @Override
    public void successorEdge(Port p, int from, int to, char num, int index) {
        if (scanGraph && from >= 0) {
            entry.getGraphMeta().addEdge(from, to);
        }
    }

    @Override
    public void inputEdge(Port p, int from, int to, char num, int index) {
        if (scanGraph && from >= 0) {
            entry.getGraphMeta().addEdge(from, to);
        }
    }

    @Override
    public void setNodeProperty(String key, Object value) {
    }

    @Override
    public void setNodeName(NodeClass nodeClass) {
    }

    private String currentGroupName;

    @Override
    public void setGroupName(String name, String shortName) {
        if (groupLevel == 1) {
            super.setGroupName(name, shortName);
            completer.attachTo((LazyGroup) folder(), name);
        }
        currentGroupName = name;
        reportState(name);
    }

    @Override
    public void endNode(int nodeId) {
    }

    private long rootStartPos;

    @Override
    public void startRoot() {
        super.startRoot();
        rootStartPos = dataSource.getMark();
    }

    @Override
    public void startNode(int nodeId, boolean hasPredecessors, NodeClass nodeClass) {
        if (scanGraph) {
            entry.getGraphMeta().addNode(nodeId);
        }
    }

    @Override
    public void markGraphDuplicate() {
        entry.getGraphMeta().markDuplicate();
    }

    protected void registerEntry(StreamEntry en, long pos) {
        StreamPool n = pool().forkIfNeeded();
        if (LOG.isLoggable(Level.FINER)) {
            if (n != pool()) {
                LOG.log(Level.FINER, "Replaced pool {0} for {1}", new Object[]{
                        Integer.toHexString(System.identityHashCode(pool())),
                        Integer.toHexString(System.identityHashCode(n))
                });
            }
        }
        replacePool(n);
        en.end(pos, n);
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "{0} Entry {1}:{2}", new Object[]{logIndent(), en.getStart(), en.getEnd()});
        }
    }

    @Override
    public void endGroup() {
        // register end as rootStartPos, which is the offset of the 'close group'
        registerEntry(entry, rootStartPos);
        if (--groupLevel == 0) {
            LazyGroup g = (LazyGroup) folder();
            completer.end(entry.getEnd());
            super.endGroup();
            completer = null;
            synchronized (streamContent) {
                if (cleaner.scannedGroups.containsKey(g)) {
                    // update to the end of the group
                    long e = entry.getEnd();
                    cleaner.bumpOffset(e);
                }
            }
        }
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "{2} Group {0}, start = {3}, start = {1}", new Object[]{currentGroupName, entry.getEnd(), logIndent(), entry.getStart()});
        }
        entry = entryStack.pop();
    }

    @Override
    protected void rootDocumentResolved(GraphDocument doc) {
        super.rootDocumentResolved(doc);
        initCleaner();
    }


    @Override
    public void startGroupContent() {
        if (groupLevel == 1) {
            super.startGroupContent();
            if (LOG.isLoggable(Level.FINER)) {
                LOG.log(Level.FINER, "{2} Group {0}, start = {1}", new Object[]{currentGroupName, rootStartPos, logIndent()});
            }
        }
    }

    @Override
    @CheckForNull
    public Group startGroup() {
        checkConstantPool();
        entryStack.push(entry);
        entry = addEntry(new StreamEntry(
                streamContent.id(),
                dataSource.getMajorVersion(), dataSource.getMinorVersion(),
                rootStartPos, forkPool()
        ));
        if (groupLevel++ > 0) {
            return null;
        }
        assert completer == null;
        GroupCompleter grc = createCompleter(rootStartPos);
        completer = grc;
        LazyGroup g = new LazyGroup(folder(), grc, entry);
        completer.attachTo(g, null);
        completors.put(g, grc);
        return pushGroup(g, true);
    }

    private String tlGraphName;

    private boolean scanGraph;

    @Override
    @CheckForNull
    public InputGraph startGraph(int dumpId, String format, Object[] args) {
        checkConstantPool();
        entryStack.push(entry);
        entry = addEntry(new StreamEntry(
                streamContent.id(),
                dataSource.getMajorVersion(), dataSource.getMinorVersion(),
                rootStartPos, forkPool()
        ).setMetadata(new GraphMetadata()));
        graphLevel++;
        scanGraph = false;
        if (graphLevel == 1) {
            tlGraphName = ModelBuilder.makeGraphName(dumpId, format, args);
            LOG.log(Level.FINER, "Starting graph {0} at {1}", new Object[]{tlGraphName, rootStartPos});

            scanGraph = true;
        }
        reportProgress();
        return null;
    }

    @Override
    public void end() {
        super.end();
        index.close();
        synchronized (streamContent) {
            cleaner.finished = true;
        }
        LOG.log(Level.FINE, "Scan terminated");
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    @CheckForNull
    public InputGraph endGraph() {
        registerEntry(entry, dataSource.getMark());
        graphLevel--;
        if (graphLevel == 0) {
            LOG.log(Level.FINER, "Graph {0} ends at {1}, contains {2} nodes", new Object[]{
                    tlGraphName, dataSource.getMark(), entry.getGraphMeta().getNodeCount()
            });
        }
        scanGraph = graphLevel == 1;
        entry = entryStack.pop();
        return null;
    }

    @Override
    public void startNestedProperty(String propertyKey) {
    }

    @Override
    public void setProperty(String key, Object value) {
        Folder f = folder();
        if (f == rootDocument() || ((f instanceof LazyGroup) && graphLevel == 0)) {
            super.setProperty(key, value);
        }
    }

    private void checkConstantPool() {
        assert getReaderPool() == getConstantPool();
    }

    // user by tests
    private String logIndent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < groupLevel; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    public ConstantPool forkPool() {
        StreamPool sp = pool();
        ConstantPool np = sp.forkIfNeeded();
        replacePool(np);
        return np;
    }

    protected StreamEntry addEntry(StreamEntry m) {
        StreamEntry e = index.addEntry(m);
        return e;
    }

    GroupCompleter getCompleter(Group g) {
        return completors.get(g);
    }

    GroupCompleter createCompleter(long start) {
        return new GroupCompleter(
                new Env(streamContent, fetchExecutor),
                index, entry);
    }
}
