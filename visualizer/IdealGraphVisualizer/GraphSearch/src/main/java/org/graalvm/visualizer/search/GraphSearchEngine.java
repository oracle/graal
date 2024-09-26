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
package org.graalvm.visualizer.search;

import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.graphio.parsing.model.Properties.PropertyMatcher;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * @author sdedic
 */
public class GraphSearchEngine implements SearchController {
    private static final RequestProcessor SEARCH_RP = new RequestProcessor("Graph Searches", 10);

    private final GraphContainer graphList;
    private final InputGraph initialGraph;
    private final NodesProvider provider;

    private BlockingQueue<InputGraph> toSearch = new LinkedBlockingQueue<>();
    private Set<InputGraph> searched = new HashSet<>();

    private SearchResultsModel model;
    private Criteria searchCriteria;
    private List<SearchListener> listeners = new ArrayList<>();

    private SearchTask pending;
    private SearchRunnable pendingRunnable;

    public GraphSearchEngine(GraphContainer graphList, InputGraph initialGraph, NodesProvider provider) {
        this.graphList = graphList;
        this.initialGraph = initialGraph;
        this.provider = provider;

        // initialize with empty model
        model = new SearchResultsModel();
        pending = SearchTask.finished(model);
        // FIXME:
        searchCriteria = new Criteria().setMatcher(PropertyMatcher.ALL);
    }

    @Override
    public void addSearchListener(SearchListener l) {
        listeners.add(l);
    }

    @Override
    public void removeSearchListener(SearchListener l) {
        listeners.remove(l);
    }

    @Override
    public SearchResultsModel getResults() {
        return model;
    }

    public GraphContainer getGraphContainer() {
        return graphList;
    }

    public Set<InputGraph> getSearchedGraphs() {
        Set<InputGraph> result = new HashSet<>();
        synchronized (this) {
            result.addAll(searched);
            toSearch.drainTo(result);
        }
        return result;
    }

    @Override
    public boolean canSearch(boolean previous) {
        List<InputGraph> gr = new ArrayList<>(graphList.getGraphs());
        if (gr.size() < 2) {
            return false;
        }
        InputGraph mark = gr.get(previous ? 0 : gr.size() - 1);
        if (toSearch.contains(mark)) {
            return false;
        }
        return !searched.contains(mark);
    }

    @Override
    public SearchTask extendSearch(boolean previous, boolean stopFirst) {
        SearchTask t = null;
        if (!canSearch(previous)) {
            // TODO: notify listeners that the search has ended.
            return null;
        }
        synchronized (this) {
            List<InputGraph> known = new ArrayList<>(toSearch);
            known.addAll(searched);

            List<InputGraph> contents = graphList.getGraphs();
            if (known.isEmpty()) {
                known.add(initialGraph);
            }
            int randomIndex = contents.indexOf(known.get(0));
            List<InputGraph> extend = new ArrayList<>(
                    previous ?
                            contents.subList(0, randomIndex) :
                            contents.subList(randomIndex + 1, contents.size())
            );
            extend.removeAll(known);
            toSearch.addAll(extend);
            if (pending != null && !pending.isFinished()) {
                t = pending;
                pendingRunnable.stopAfterFirstFound &= stopFirst;
            }
        }
        if (t == null) {
            t = doNewSearch(model, searchCriteria, stopFirst);
        }
        return t;
    }

    @Override
    public SearchTask pendingSearch() {
        return pending;
    }

    @Override
    public Criteria getCriteria() {
        return searchCriteria;
    }

    @Override
    public SearchTask newSearch(Criteria crit, boolean replace) {
        SearchResultsModel m = model;
        if (m != null && !replace) {
            m.clear();
        } else {
            m = new SearchResultsModel();
        }
        return doNewSearch(m, crit, true);
    }

    private SearchTask doNewSearch(SearchResultsModel m, Criteria crit, boolean stop) {
        SearchRunnable run = new SearchRunnable(toSearch, m, crit.getMatcher());
        run.stopAfterFirstFound = stop;
        SearchTask st;
        synchronized (this) {
            toSearch.add(initialGraph);
            this.searchCriteria = crit;
            if (this.model != m) {
                searched.clear();
            }
            this.model = m;
            if (pending != null) {
                pending.cancel();
            }
            RequestProcessor.Task t = SEARCH_RP.post(run);
            st = new SearchTask(t, run, model);
            run.attachTask(st);
            this.pendingRunnable = run;
            this.pending = st;
        }
        fireListeners(SearchListener::searchStarted, () -> new SearchEvent(
                this, st, null, model
        ));
        return st;
    }

    private boolean fireListeners(BiConsumer<SearchListener, SearchEvent> method, Supplier<SearchEvent> p) {
        List<SearchListener> ll;
        synchronized (this) {
            if (listeners.isEmpty()) {
                return false;
            }
            ll = new ArrayList<>(listeners);
        }
        SearchEvent e = p.get();
        ll.forEach(l -> method.accept(l, e));
        return e.isTerminate();
    }

    private void notifyGraphFinished(InputGraph g) {
        synchronized (this) {
            this.searched.add(g);
        }
    }

    private void notifyFinished(SearchRunnable r) {
        synchronized (this) {
            toSearch.clear();
            if (pendingRunnable == r) {
                pendingRunnable = null;
            }
        }
    }

    @NbBundle.Messages({
            "# {0} - graph name",
            "# {1} - search spec",
            "TITLE_SearchInGraph={0}: {1}"
    })
    @Override
    public String getTitle() {
        return Bundle.TITLE_SearchInGraph(initialGraph.getName(), getCriteria().toDisplayString(false));
    }

    @Override
    public NodesProvider getNodesProvider() {
        return provider;
    }

    class SearchRunnable implements Runnable, Cancellable {
        private final BlockingQueue<InputGraph> toSearch;
        private final SearchResultsModel target;
        private final Properties.PropertyMatcher selector;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private volatile boolean stopAfterFirstFound = true;
        private int found;
        private SearchTask myTask;
        private NodesList nl;

        private List<InputNode> selected;
        private long lastPublished;
        private GraphItem ownerItem;

        public SearchRunnable(BlockingQueue<InputGraph> toSearch, SearchResultsModel target, Properties.PropertyMatcher selector) {
            this.toSearch = toSearch;
            this.target = target;
            this.selector = selector;
        }

        public SearchRunnable setStopAfterFirstFound(boolean stopAfterFirstFound) {
            this.stopAfterFirstFound = stopAfterFirstFound;
            return this;
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        Collection<InputGraph> getPendingGraphs() {
            Set<InputGraph> pending = new HashSet<>();
            toSearch.drainTo(pending);
            return pending;
        }

        @Override
        public boolean cancel() {
            synchronized (GraphSearchEngine.this) {
                cancelled.set(true);
                return !myTask.isFinished();
            }
        }

        void attachTask(SearchTask t) {
            synchronized (GraphSearchEngine.this) {
                this.myTask = t;
            }
        }

        @Override
        public void run() {
            InputGraph g;

            // race between the creator and run(), task may not be yet attached.
            synchronized (GraphSearchEngine.this) {
                g = toSearch.poll();
            }

            try {
                InputGraph next;

                while (g != null && !cancelled.get()) {
                    synchronized (GraphSearchEngine.this) {
                        if (pending != myTask) {
                            break;
                        }
                    }
                    try {
                        ownerItem = new GraphItem(g.getGraphType(), g);
                        found = 0;
                        selected = new ArrayList<>();
                        lastPublished = System.currentTimeMillis();
                        processGraph(g);
                    } finally {
                        synchronized (GraphSearchEngine.this) {
                            if (!selected.isEmpty() && stopAfterFirstFound) {
                                next = null;
                            } else {
                                next = toSearch.poll();
                            }
                            if (next == null) {
                                notifyFinished(this);
                            }
                        }
                        searched.add(g);
                        publishSelected(g, true, next != null);
                        nl = null;
                    }
                    if (g == next) {
                        break;
                    } else {
                        g = next;
                    }
                }
            } finally {
                fireListeners(SearchListener::searchFinished, () -> new SearchEvent(GraphSearchEngine.this,
                        myTask, target, true));
            }
        }

        private void publishSelected(InputGraph g, boolean finished, boolean allFinished) {
            List<NodeResultItem> items = new ArrayList<>();
            int traversed;
            if (nl != null) {
                traversed = nl.visitedCount();
            } else {
                traversed = -1;
            }
            for (InputNode in : selected) {
                items.add(new NodeResultItem(ownerItem, in));
            }
            selected.clear();
            target.addAll(items);
            if (finished) {
                notifyGraphFinished(g);
                fireListeners(SearchListener::finished,
                        () -> new SearchEvent(GraphSearchEngine.this, myTask, g, target, traversed, found, true, allFinished)
                );
            } else {
                boolean term = fireListeners(SearchListener::searchProgress,
                        () -> new SearchEvent(GraphSearchEngine.this, myTask, g, target, traversed, found, false, false)
                );
                if (term) {
                    cancelled.set(true);
                }
            }
        }

        private void processGraph(InputGraph g) {
            if (fireListeners(SearchListener::loading, () -> new SearchEvent(GraphSearchEngine.this, myTask, g, target))) {
                cancelled.set(true);
                return;
            }

            Collection<InputNode> nodes = g.getNodes();
            if (fireListeners(SearchListener::started, () -> new SearchEvent(GraphSearchEngine.this, myTask, g, target))) {
                cancelled.set(true);
                return;
            }

            nl = provider.nodes(g);
            int cnt = 0;
            while (nl.hasNext() && !cancelled.get()) {
                InputNode n = nl.next();

                Properties np = n.getProperties();
                if (selector.matchProperties(np) != null) {
                    selected.add(n);
                    found++;
                }
                cnt++;
                if (cnt % 100 == 0 && (System.currentTimeMillis() - lastPublished) > 500) {
                    publishSelected(g, false, false);
                }
            }
        }
    }

    public InputGraph getInitialGraph() {
        return initialGraph;
    }

    public Collection<InputGraph> getToSearch() {
        Collection<InputGraph> c = new ArrayList<>();
        toSearch.drainTo(c);
        return c;
    }

    public synchronized Set<InputGraph> getSearched() {
        return new HashSet<>(searched);
    }
}
