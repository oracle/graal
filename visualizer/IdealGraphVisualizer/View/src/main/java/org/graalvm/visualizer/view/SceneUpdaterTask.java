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
package org.graalvm.visualizer.view;

import static org.graalvm.visualizer.view.DiagramScene.ANIMATION_LIMIT;
import static org.graalvm.visualizer.view.DiagramScene.BORDER_SIZE;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.*;

import org.graalvm.visualizer.data.Pair;
import org.graalvm.visualizer.graph.*;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;
import org.graalvm.visualizer.view.widgets.BlockWidget;
import org.graalvm.visualizer.view.widgets.FigureWidget;
import org.graalvm.visualizer.view.widgets.LineWidget;
import org.netbeans.api.visual.animator.SceneAnimator;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.LayerWidget;
import org.netbeans.api.visual.widget.Widget;
import org.openide.util.RequestProcessor;

import jdk.graal.compiler.graphio.parsing.model.InputBlock;

/**
 * Task which recomputes the layout. Creates a snapshot of the original Diagram
 * at first, executes layouting algorithm on it. After performing the layout, it
 * finally updates the coordinates <b>and</b> visibility to the main layout,
 * under AWT lock.
 */
abstract class SceneUpdaterTask implements Runnable {
    private static final Logger LOG = Logger.getLogger(SceneUpdaterTask.class.getName());

    /**
     * How far past the screen boundary should be the widgets created in X-axis
     */
    private static final float VIEWPORT_EXTENSION_FACTOR_X = 1.0f;

    /**
     * How far past the screen boundary should be the widgets created in Y-axis
     */
    private static final float VIEWPORT_EXTENSION_FACTOR_Y = 2.0f;

    /**
     * Limit for processing inside the UI thread. After this timeout elapses,
     * the iterators should refuse to provide additional elements or throw
     * InterruptException to indicate the processing should be stopped.
     */
    private static final int UI_PROCESSING_LIMIT = 150;

    /**
     * Test the elapsed time after this amount of items is processed.
     */
    private static final int UI_PROCESSING_CHUNK = 150;

    /**
     * How many widgets are moved during each UI partial update round, <b>at
     * minimum</b>
     */
    private static final int UI_MINIMUM_VISIBLE_CHUNK = 2000;

    /**
     * Number of milliseconds to delay the next UI update round. The time will
     * be used to process user input
     */
    private static final int UI_THREAD_INTERLEAVE = 200;

    /**
     * The request processor used to schedule the tasks
     */
    protected final RequestProcessor processor;

    /**
     * Listeners
     */
    protected Consumer<SceneUpdaterTask> completionListener;

    /**
     * Visible items materialized into Figures in the diagram. Does contain only
     * items for which widgets were created.
     */
    private final Set<Integer> oldMaterializedFigures = new HashSet<>();
    private final Set<InputBlock> oldMaterializedBlocks = new HashSet<>();

    private static class CancelException extends RuntimeException {
        public CancelException() {
        }
    }

    enum Phase {
        PREPARE,
        COMPUTE(false),
        VIEWPORT(true),
        FIND_WIDGETS(false),
        UPDATE_A,
        UPDATE_B,
        COMPLETE(false),
        OBSOLETE;

        private final boolean runInAWT;

        Phase() {
            this(true);
        }

        Phase(boolean runInAWT) {
            this.runInAWT = runInAWT;
        }

        public boolean runsInAWT() {
            return runInAWT;
        }
    }

    /**
     * Collects widgets, which were visible at the time the Updater started.
     */
    private Collection<Widget> oldVisibleWidgets;

    /**
     * Reference to the original diagram. Copied here, because the user might
     * change the phase, causing {@link #getDiagram} value change.
     */
    protected final Diagram diagram;

    protected final DiagramScene scene;

    private int maxX = -BORDER_SIZE;
    private int maxY = -BORDER_SIZE;

    /**
     * The current processing phase
     */
    private Phase phase = Phase.PREPARE;

    private final DiagramViewModel model;

    /**
     * Bounds of the viewport, extended to each direction by a factor of
     * viewport's size.
     */
    protected Rectangle extendedViewportBounds;

    /**
     * Size of the viewport
     */
    protected Rectangle viewportBounds;

    private final Set<Integer> reachableFigureIDs = new HashSet<>();

    private int visibleFigureCount;

    private final AtomicBoolean cancelled = new AtomicBoolean();

    protected RequestProcessor.Task scheduled;

    private SceneAnimator sceneAnimator;
    protected final Set<Pair<Point, Point>> lastLineCache;
    protected final Set<Pair<Point, Point>> lineCache = new HashSet<>();

    protected SceneUpdaterTask(DiagramScene scene, LayoutSettingBean settings, RequestProcessor processor, Phase initialPhase, Set<Pair<Point, Point>> lastLineCache) {
        this(scene, settings, false, processor, lastLineCache);
        this.phase = initialPhase;
    }

    SceneUpdaterTask(DiagramScene scene, LayoutSettingBean settings, boolean relayout, RequestProcessor processor, Set<Pair<Point, Point>> lastLineCache) {
        this.processor = processor;
        this.scene = scene;
        this.model = scene.getModel();
        this.diagram = model.getDiagramToView();
        this.lastLineCache = lastLineCache;
        setupViewportBounds();
    }

    protected void setupViewportBounds() {
        assert SwingUtilities.isEventDispatchThread();
        JViewport viewport = scene.getScrollPane().getViewport();
        Rectangle tmp = scene.convertViewToScene(viewport.getViewRect());
        Dimension viewSize = tmp.getSize();
        // compute extended viewport, viewport enlarged by some factor, so small
        // scrolling will not immediately go out of the created figures.
        Rectangle enlarged = new Rectangle(
                Math.max(-DiagramScene.BORDER_SIZE, tmp.x - (int) (VIEWPORT_EXTENSION_FACTOR_X * viewSize.width)),
                Math.max(-DiagramScene.BORDER_SIZE, tmp.y - (int) (VIEWPORT_EXTENSION_FACTOR_Y * viewSize.height)),
                tmp.width + 2 * (int) (VIEWPORT_EXTENSION_FACTOR_X * viewSize.width),
                tmp.height + 2 * (int) (VIEWPORT_EXTENSION_FACTOR_Y * viewSize.height)
        );
        enlarged.add(scene.getVisibleSceneRect());
        synchronized (this) {
            this.viewportBounds = tmp;
            this.extendedViewportBounds = enlarged;
        }
        LOG.log(Level.FINE, "Setting up viewport viewport:{2}, bounds:{0}, extended:{1}", new Object[]{viewportBounds, extendedViewportBounds, viewport.getViewRect()});
    }

    /**
     * Iterable which is time-constrained. It will stop producing items after
     * {@link #UI_PROCESSING_LIMIT} milliseconds pass. The Iterable may be
     * chained with some former one, and if the former times-out, the subsequent
     * will also.
     *
     * @param <T>
     */
    class TimedIterable<T> implements Iterable<T> {
        private final long t = System.currentTimeMillis();
        private final Iterable<T> collection;
        private final int maxChunk;
        private final TimedIterable predecessor;

        private int reachedStop = Integer.MAX_VALUE;
        private boolean timeout;

        TimedIterable(Iterable<T> collection, int maxChunk, TimedIterable predecessor) {
            this.collection = collection;
            this.maxChunk = maxChunk;
            this.predecessor = predecessor;

            if (predecessor != null && predecessor.reachedStop < Integer.MAX_VALUE) {
                reachedStop = 0;
            }
        }

        TimedIterable(Iterable<T> collection, int maxChunk) {
            this(collection, maxChunk, null);
        }

        public int getStopIndex() {
            return reachedStop == Integer.MAX_VALUE ? 0 : reachedStop;
        }

        @Override
        public Iterator<T> iterator() {
            return new It(collection.iterator());
        }

        void stopAt(int index, boolean time) {
            if (reachedStop < Integer.MAX_VALUE) {
                return;
            }
            this.reachedStop = index;
            this.timeout = time;
        }

        boolean isTimeOut() {
            return timeout;
        }

        boolean isTimeUp(int index) {
            long l = System.currentTimeMillis();
            if (predecessor != null && predecessor.isTimeOut()) {
                stopAt(index, true);
                return true;
            }
            if (index < maxChunk || l - t < UI_PROCESSING_LIMIT) {
                return false;
            }
            stopAt(index, true);
            return true;
        }

        class It<T> implements Iterator<T> {
            private final Iterator<T> delegate;
            private int cnt;
            private boolean stop;
            private T item;

            public It(Iterator<T> delegate) {
                this.delegate = delegate;
            }

            private void nextItem() {
                if (item != null || stop) {
                    return;
                }
                if (!delegate.hasNext()) {
                    stop = true;
                    stopAt(cnt, false);
                }

                if (cnt >= reachedStop || cancelled.get()) {
                    stop = true;
                } else if (((cnt % UI_PROCESSING_CHUNK) == 0) && isTimeUp(cnt)) {
                    stop = true;
                } else {
                    item = delegate.next();
                }
            }

            @Override
            public boolean hasNext() {
                nextItem();
                return item != null;
            }

            @Override
            public T next() {
                nextItem();
                if (item == null) {
                    throw new NoSuchElementException();
                }
                cnt++;
                T tmp = item;
                item = null;
                return tmp;
            }
        }
    }

    protected Phase nextPhase = Phase.PREPARE;

    public Phase getPhase() {
        return phase;
    }

    protected abstract void processPhase(Phase phase);

    @Override
    public void run() {
        do {
            LOG.log(Level.FINE, "Executing task {0}, phase {1}", new Object[]{this, phase});
            if (cancelled.get()) {
                phase = Phase.OBSOLETE;
            }
            if (phase == Phase.OBSOLETE) {
                LOG.log(Level.FINE, "Final terminate task {0}, phase {1}", new Object[]{this, phase});
                return;
            }
            if (phase.ordinal() <= Phase.COMPLETE.ordinal()) {
                nextPhase = Phase.values()[phase.ordinal() + 1];
            } else {
                nextPhase = Phase.OBSOLETE;
            }
            if (phase.runsInAWT() && !SwingUtilities.isEventDispatchThread()) {
                LOG.log(Level.FINE, "Phase shall run in AWT, but does not; rescheduling to AWT");
                SwingUtilities.invokeLater(this);
                return;
            }
            try {
                processPhase(phase);
            } catch (CancelException ex) {
                // no op
                nextPhase = Phase.OBSOLETE;
            }
            if (phase == Phase.COMPLETE) {
                if (completionListener != null) {
                    completionListener.accept(this);
                }
                nextPhase = Phase.OBSOLETE;
            }
            if (SwingUtilities.isEventDispatchThread()) {
                // validate on termination
                scene.validate();
            }
            LOG.log(Level.FINE, "Rescheduling task {0}, old phase {2}, next phase {1}", new Object[]{this, nextPhase, phase});
        } while (schedule());
        LOG.log(Level.FINE, "Pausing task {0}, next phase {1}", new Object[]{this, phase});
    }

    protected final void complete() {
        nextPhase = Phase.COMPLETE;
    }

    private boolean schedule() {
        boolean samePhase = nextPhase == null;
        Phase oldPhase = this.phase;
        if (!samePhase) {
            this.phase = nextPhase;
        }
        if (oldPhase.ordinal() >= Phase.COMPLETE.ordinal()) {
            return false;
        }
        if (phase != Phase.PREPARE) {
            synchronized (this) {
                if (samePhase) {
                    scheduled = processor.post(() -> SwingUtilities.invokeLater(this), UI_THREAD_INTERLEAVE);
                    return false;
                }
            }
            if (phase.runsInAWT() == oldPhase.runsInAWT()) {
                return true;
            }
        }
        switch (phase) {
            case PREPARE:
                SwingUtilities.invokeLater(this);
                break;
            case VIEWPORT:
            case UPDATE_A:
            case UPDATE_B:
                SwingUtilities.invokeLater(this);
                return false;

            case FIND_WIDGETS:
            case COMPUTE:
                synchronized (this) {
                    scheduled = processor.post(this);
                }
                return false;
            case COMPLETE:
                // fall through
                processor.post(this);
                break;
            case OBSOLETE:
            default:
                break;
        }
        return false;
    }

    private int figIndex;
    private int blockIndex;

    /**
     * Figures to update visually
     */
    protected List<Figure> updateFigures;

    /**
     * Blocks to update visually
     */
    protected List<Block> updateBlocks;

    protected boolean isConnectionReachable(Connection c) {
        Figure sf = c.getOutputSlot().getFigure();
        Figure tf = c.getInputSlot().getFigure();
        if (isReachable(sf) || isReachable(tf)) {
            return true;
        }
        for (Point pt : c.getControlPoints()) {
            if (pt == null) {
                continue;
            }
            if (extendedViewportBounds.contains(pt)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Searches through widgets to find figures to update. Records figure IDs,
     * so that figures in the original and the layout diagram could be found.
     */
    protected void findWidgetsToUpdate(boolean updateAllMaterialized) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "Find widgets to update, materialized:{0}, viewport:{1}, extendedViewportBounds:{2}", new Object[]{updateAllMaterialized, viewportBounds, extendedViewportBounds});
        }
        Set<Connection> inspectedConnections = new HashSet<>();
        updateFigures = new ArrayList<>(diagram.getFigures().size());
        for (Figure f : diagram.getFigures()) {
            boolean reachable = isReachable(f);
            if (reachable) {
                for (OutputSlot os : f.getOutputSlots()) {
                    for (Connection c : os.getConnections()) {
                        if (!inspectedConnections.add(c)) {
                            continue;
                        }
                        if (isConnectionReachable(c)) {
                            reachableFigureIDs.add(c.getInputSlot().getFigure().getId());
                        }
                    }
                }
                for (Slot is : f.getInputSlots()) {
                    for (Connection c : is.getConnections()) {
                        if (!inspectedConnections.add(c)) {
                            continue;
                        }
                        if (isConnectionReachable(c)) {
                            reachableFigureIDs.add(c.getOutputSlot().getFigure().getId());
                        }
                    }
                }
                reachableFigureIDs.add(f.getId());

                if (isVisible(f)) {
                    visibleFigureCount++;
                }
            }
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "Find widgets: reachable figures:{0}, visibleFigures:{1}", new Object[]{reachableFigureIDs.size(), visibleFigureCount});
        }
        // do not update figures which are outside of the visible boundary + something
        if (model.getShowBlocks()) {
            Collection<Block> blocks = diagram.getBlocks();
            List<Block> bls = new ArrayList<>(blocks);
            for (Block b : blocks) {
                if (isReachable(b) || oldMaterializedBlocks.contains(b.getInputBlock())) {
                    bls.add(b);
                }
            }
            updateBlocks = bls;
        } else {
            updateBlocks = Collections.emptyList();
        }
        if (updateAllMaterialized) {
            reachableFigureIDs.addAll(this.oldMaterializedFigures);
        }
        for (int i : reachableFigureIDs) {
            Figure f = diagram.getFigureById(i);
            if (f != null) {
                updateFigures.add(f);
            }
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "Added {0} already materialized, total to update:{1}", new Object[]{updateAllMaterialized ? oldMaterializedFigures.size() : -1, updateFigures.size()});
        }
    }

    /**
     * Attempts to incrementally update visibility for widgets. Potentially
     * creates non-existing widgets and associates them with their objects.
     *
     * @return true, if the phase is not complete and should be rescheduled
     */
    protected boolean updateVisibleWidgetsIncrementally() {
        TimedIterable<Figure> figs = null;
        int widgetCount = 0;
        if (figIndex < updateFigures.size()) {
            figs = new TimedIterable(updateFigures.subList(figIndex, updateFigures.size()),
                    UI_MINIMUM_VISIBLE_CHUNK);
            for (Figure f : figs) {
                FigureWidget w = scene.createFigureWidget(f);
                if (w != null) {
                    w.setVisible(f.isVisible());
                    widgetCount++;
                }
            }
            figIndex += figs.getStopIndex();
        }
        if (blockIndex < updateBlocks.size()) {
            TimedIterable<Block> blocks = new TimedIterable(updateBlocks.subList(blockIndex, updateBlocks.size()),
                    UI_MINIMUM_VISIBLE_CHUNK, figs);
            for (Block f : blocks) {
                BlockWidget w = scene.createBlockWidget(f.getInputBlock());
                w.setVisible(f.isVisible());
            }
            blockIndex += blocks.getStopIndex();
        }
        LOG.log(Level.FINE, "Updated {0} widgets", widgetCount);
        scene.validate();
        return figIndex < updateFigures.size() || blockIndex < updateBlocks.size();
    }

    /**
     * Obtain size of Diagram.
     */
    protected void findDimensions() {
        assert !SwingUtilities.isEventDispatchThread();
        Dimension size = diagram.getSize();
        if (size == null) {
            throw new IllegalStateException("Diagram size has to be set after layouting phase and before display phase.");
        }
        maxX = size.width;
        maxY = size.height;
        extendedViewportBounds = new Rectangle(extendedViewportBounds.x, extendedViewportBounds.y,
                Math.min(extendedViewportBounds.width, maxX),
                Math.min(extendedViewportBounds.height, maxY));
        LOG.log(Level.FINE, "findDimensions: New dimensions of scene: {0}:{1}", new Object[]{maxX, maxY});
    }

    private boolean firstMove = true;

    /**
     * Attempts to move figures, in chunks. If all the figures/blocks are not
     * processed, returns true to indicate the task phase should be rescheduled.
     * Maintains an internal index where to start with the next chunk. Operates
     * on {@link #updateFigures} and {@link #updateBlocks}
     *
     * @return true, if the move should continue in next UI chunk.
     */
    protected boolean moveFiguresIncrementally() {
        if (firstMove) {
            figIndex = blockIndex = 0;
            firstMove = false;
        }
        // the indexes should have been already reset.
        LOG.log(Level.FINE, "Moving figures from {0}, top = {1}, viewport = {2}", new Object[]{figIndex, updateFigures.size(), viewportBounds});
        TimedIterable<Figure> figs = new TimedIterable<>(updateFigures.subList(figIndex, updateFigures.size()), UI_THREAD_INTERLEAVE);
        TimedIterable<Block> blocks = new TimedIterable<>(updateBlocks.subList(blockIndex, updateBlocks.size()), UI_THREAD_INTERLEAVE, figs);
        moveFigures(figs, blocks);
        figIndex += figs.getStopIndex();
        blockIndex += blocks.getStopIndex();
        LOG.log(Level.FINE, "Figure move stopped, new start = {0}, top = {1}", new Object[]{figIndex, updateFigures.size()});
        boolean cont = figIndex < updateFigures.size() || (model.getShowBlocks() && blockIndex < updateBlocks.size());
        LOG.log(Level.FINE, "Move will continue: {0}", cont);
        return cont;
    }

    private boolean isReachable(DiagramItem f) {
        if (!f.isVisible()) {
            return false;
        }
        Rectangle b = f.getBounds();
        if (b == null) {
            return false;
        }
        return extendedViewportBounds.intersects(b);
    }

    private boolean isVisible(Figure f) {
        if (!f.isVisible()) {
            return false;
        }
        Rectangle b = f.getBounds();
        if (b.x == 0 && b.y == 0) {
            return true;
        }
        return viewportBounds.intersects(b);
    }

    SceneUpdaterTask execute() {
        schedule();
        return this;
    }

    private static class LineEntry {
        final int index;
        final Point last;
        final LineWidget pred;
        final List<Connection> conns;

        public LineEntry(List<Connection> connections, int controlPointIndex, Point lastPoint, LineWidget predecessor) {
            index = controlPointIndex;
            conns = connections;
            last = lastPoint;
            pred = predecessor;
        }
    }

    private void processOutputSlot(OutputSlot s) {
        List<Connection> connections = s.getConnections().stream().filter(c -> c.getInputSlot().getFigure().isVisible() && c.getControlPoints().size() > 1).collect(Collectors.toList());
        if (connections.isEmpty()) {
            return;
        }
        List<Point> points = connections.get(0).getControlPoints();
        Queue<LineEntry> entries = new ArrayDeque<>();
        entries.addAll(prepareLineEntries(connections, 1, points.get(0), null));
        while (!entries.isEmpty()) {
            entries.addAll(processLineEntry(s, entries.remove()));
        }
    }

    private List<LineEntry> prepareLineEntries(List<Connection> connections, int nextPointIndex, Point lastPoint, LineWidget predecessor) {
        Map<Point, List<Connection>> nexts = new HashMap<>();
        for (Connection connection : connections) {
            List<Point> controlPoints = connection.getControlPoints();
            if (controlPoints.size() > nextPointIndex) {
                nexts.computeIfAbsent(controlPoints.get(nextPointIndex), p -> new ArrayList<>()).add(connection);
            }
        }
        ArrayList<LineEntry> entries = new ArrayList<>();
        nexts.values().forEach(c -> {
            if (c.size() == 1) {
                finishSingleConnection(c, predecessor, lastPoint, nextPointIndex);
            } else {
                entries.add(new LineEntry(c, nextPointIndex, lastPoint, predecessor));
            }
        });
        return entries;
    }

    private List<LineEntry> processLineEntry(OutputSlot s, LineEntry task) {
        return processLineEntry(s, task.conns, task.pred, task.last, task.index);
    }

    private List<LineEntry> processLineEntry(OutputSlot s, List<Connection> connections, LineWidget predecessor, Point lastPoint, int controlPointIndex) {
        Point nextPoint = connections.get(0).getControlPoints().get(controlPointIndex);
        if (lastPoint == null || nextPoint == null) {
            return prepareLineEntries(connections, controlPointIndex + 1, nextPoint, predecessor);
        }

        boolean isBold = false;
        boolean isDashed = true;

        for (Connection c : connections) {
            if (!isBold && c.getStyle() == Connection.ConnectionStyle.BOLD) {
                isBold = true;
            }

            if (isDashed && c.getStyle() != Connection.ConnectionStyle.DASHED) {
                isDashed = false;
            }
        }

        LineWidget newPredecessor = processLineWidget(s, lastPoint, nextPoint, connections, predecessor, isBold, isDashed);

        return prepareLineEntries(connections, controlPointIndex + 1, nextPoint, newPredecessor);
    }

    private void finishSingleConnection(List<Connection> connections, LineWidget predecessor, Point lastPoint, int controlPointIndex) {
        Connection connection = connections.get(0);
        OutputSlot outputSlot = connection.getOutputSlot();
        List<Point> points = connection.getControlPoints();
        boolean isBold = connection.getStyle() == Connection.ConnectionStyle.BOLD;
        boolean isDashed = connection.getStyle() == Connection.ConnectionStyle.DASHED;
        Point nextPoint;
        while (points.size() > controlPointIndex) {
            nextPoint = points.get(controlPointIndex);
            if (lastPoint != null && nextPoint != null) {
                predecessor = processLineWidget(outputSlot, lastPoint, nextPoint, connections, predecessor, isBold, isDashed);
            }
            controlPointIndex++;
            lastPoint = nextPoint;
        }
    }

    private LineWidget processLineWidget(OutputSlot s, Point lastPoint, Point nextPoint, List<Connection> connections, LineWidget predecessor, boolean isBold, boolean isDashed) {
        Point p1 = lastPoint.getLocation();
        Point p2 = nextPoint.getLocation();

        Pair<Point, Point> curPair = new Pair<>(p1, p2);
        SceneAnimator curAnimator = sceneAnimator;
        if (lastLineCache.contains(curPair)) {
            curAnimator = null;
        } else {
            lineCache.add(curPair);
        }
        ConnectionSet key = new ConnectionSet(connections, lastPoint.x, lastPoint.y, nextPoint.x, nextPoint.y);
        LineWidget w = scene.getWidget(key);
        if (w == null) {
            w = new LineWidget(scene, s, connections, p1, p2, predecessor, curAnimator, isBold, isDashed);
            w.getActions().addAction(scene.getHoverAction());
            scene.addObject(key, w);
        } else {
            w.setPredecessor(predecessor);
            w.setPoints(p1, p2);
        }
        if (w.getParentWidget() == null) {
            scene.addConnection(w);
        }
        return w;
    }

    public Set<Pair<Point, Point>> getLineCache() {
        return this.lineCache;
    }

    static class ConnectionSet {
        private final int hash;
        private final int x1, y1, x2, y2;
        private final Set<Connection> connections;

        public ConnectionSet(Collection<Connection> connections, int x1, int y1, int x2, int y2) {
            this.connections = new HashSet<>(connections);
            int h = 0;
            for (Connection c : connections) {
                h = h * 37 + c.hashCode();
            }
            h = h * 83 + x1;
            h = h * 83 + y1;
            h = h * 83 + x2;
            this.hash = h * 83 + y2;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ConnectionSet)) {
                return false;
            } else if (obj == this) {
                return true;
            }
            ConnectionSet o = (ConnectionSet) obj;
            return o.x1 == this.x1 && o.y1 == this.y1 && o.x2 == this.x2 && o.y2 == this.y2 && this.connections.equals(o.connections);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        public Set<Connection> getConnectionSet() {
            return Collections.unmodifiableSet(connections);
        }
    }

    /**
     * Finds widgets, which were already created AND visible. Their Figures and
     * Blocks will be collected so if they hide, the relevant widgets are
     * updated to invisible.
     */
    protected void findOldWidgets() {
        // XXX maybe called late, after all objects have been removed during relayout.
        oldVisibleWidgets = new HashSet<>();
        for (Object o : scene.getObjects()) {
            if (o instanceof DiagramItem) {
                DiagramItem di = (DiagramItem) o;
                Collection<Widget> ow = scene.findWidgets(o);
                if (ow != null) {
                    if (di.isVisible()) {
                        if (di instanceof Figure) {
                            oldMaterializedFigures.add(((Figure) di).getId());
                        } else if (di instanceof Block) {
                            oldMaterializedBlocks.add(((Block) di).getInputBlock());
                        }
                        oldVisibleWidgets.addAll(ow);
                    }
                }
            }
        }
        LOG.log(Level.FINE, "findOldWidgets: old visible count = {0}", oldVisibleWidgets.size());
    }

    private void moveFigures(Iterable<Figure> figs, Iterable<Block> blocks) {
        if (model.getDiagramToView() != diagram) {
            phase = Phase.OBSOLETE;
            return;
        }
        sceneAnimator = scene.getSceneAnimator();
        boolean diagramEmpty = diagram.getFigures().isEmpty();
        scene.setBottomRightLocation(new Point(maxX + BORDER_SIZE, maxY + BORDER_SIZE));
        int count = 0;
        for (Figure f : figs) {
            assert diagramEmpty || f.getDiagram() == diagram : "Only figures created by UI thread can be moved";
            FigureWidget w = scene.createFigureWidget(f);
            if (w == null) {
                continue;
            }
            count++;
            Point p = f.getPosition();
            w.setVisible(f.isVisible() && p != null);
            if (w.isVisible()) {
                w.setBoundary(f.isBoundary());
                Point p2 = p.getLocation();
                if ((visibleFigureCount <= ANIMATION_LIMIT && oldVisibleWidgets != null && oldVisibleWidgets.contains(w))) {
                    scene.animateMoveWidget(w, p2);
                } else {
                    scene.moveWidget(w, p2);
                }

                for (OutputSlot s : f.getOutputSlots()) {
                    processOutputSlot(s);
                }
            }
        }
        LOG.log(Level.FINE, "moveFigures: moved {0} figures, number of objects: {1}", new Object[]{count, scene.getObjects().size()});
        if (model.getShowBlocks()) {
            for (Block b : blocks) {
                BlockWidget w = scene.createBlockWidget(b.getInputBlock());
                Rectangle bounds = b.getBounds();
                // do not show invisible blocks
                w.setVisible(b.isVisible() && bounds != null);
                if (w.isVisible()) {
                    // make a copy
                    Rectangle r = new Rectangle(bounds.getLocation(), bounds.getSize());
                    if ((visibleFigureCount <= ANIMATION_LIMIT && oldVisibleWidgets != null && oldVisibleWidgets.contains(w))) {
                        scene.animateBounds(w, r);
                    } else {
                        scene.moveWidget(w, r);
                    }
                }
            }
        }
        scene.validate();
    }

    protected boolean checkVisibleRectangle() {
        Rectangle r = scene.convertViewToScene(scene.getScrollPane().getViewport().getViewRect());
        return extendedViewportBounds.contains(r);
    }

    /**
     * Executes the scene task delayed.
     *
     * @param delay delay to wait before executing
     * @return the task (itself)
     */
    synchronized SceneUpdaterTask schedule(int delay) {
        scheduled = processor.post(this, delay);
        return this;
    }

    /**
     * Attempts to cancel the task
     */
    synchronized void cancel() {
        if (scheduled != null) {
            scheduled.cancel();
        }
        LOG.log(Level.FINE, "Task cancelled: {0}", this);
        this.cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Attaches a task to be executed after this scene task finishes. Called
     * only when the task completes successfully (is not canceled).
     *
     * @param l consumer to be called after task completes.
     */
    public void onCompletion(Consumer<SceneUpdaterTask> l) {
        synchronized (this) {
            assert completionListener == null;
            this.completionListener = l;
            if (phase != Phase.COMPLETE) {
                return;
            }
        }
        // run immediately, if the updater task already successfully completed.
        l.accept(this);
    }

    /**
     * Returns extended bounds, for which the widgets are computed.
     *
     * @return bounds to compute widgets
     */
    public synchronized Rectangle getExtendedBounds() {
        return new Rectangle(extendedViewportBounds);
    }

    static class DisplayWidgets extends SceneUpdaterTask {
        public DisplayWidgets(DiagramScene scene, LayoutSettingBean settings, RequestProcessor processor, Set<Pair<Point, Point>> lastLineCache) {
            super(scene, settings, processor, Phase.COMPUTE, lastLineCache);
        }

        @Override
        protected void processPhase(Phase phase) {
            Rectangle rect = scene.convertViewToScene(
                    scene.getScrollPane().getViewport().getViewRect()
            );
            if (!extendedViewportBounds.intersects(rect)) {
                nextPhase = Phase.OBSOLETE;
                return;
            }
            switch (phase) {
                case COMPUTE:
                    LOG.log(Level.FINE, "Display update for exviewport: {0} ", new Object[]{extendedViewportBounds});
                    diagram.render(this::findDimensions);
                    break;
                case VIEWPORT:
                    setupViewportBounds();
                    break;
                case FIND_WIDGETS:
                    findWidgetsToUpdate(false);
                    break;
                case UPDATE_A:
                    LOG.log(Level.FINE, "Display update visible for viewport: {0} ", new Object[]{extendedViewportBounds});
                    if (updateVisibleWidgetsIncrementally()) {
                        nextPhase = null;
                        // send out an interim update event
                        scene.fireSceneUpdated(false);
                    } else {
                        LOG.log(Level.FINE, "Visible updated, count = {0} ", new Object[]{updateFigures.size()});
                    }
                    break;
                case UPDATE_B:
                    diagram.render(() -> {
                        if (moveFiguresIncrementally()) {
                            nextPhase = null;
                        } else {
                            complete();
                        }
                    });
                    break;
            }
        }
    }

    /**
     * Clean-up task: Attempts to remove all widgets outside of the extended
     * viewport rectangle. Starts with layer children, removes both widget AND
     * its object from the scene, but retains object's state, if it differs from
     * the default. Objects for all children of the removed widgets are removed
     * as well, so the entire Figure/Block/Connection mapping fully removed and
     * can be created from scratch on next view/layout.
     */
    static class Cleaner extends SceneUpdaterTask {
        private List<Widget> allWidgets;
        private int from = 0;
        private final Consumer<Rectangle> updatedRectangleCallback;

        public Cleaner(DiagramScene scene, RequestProcessor processor, Consumer<Rectangle> callback) {
            super(scene, null, processor, Phase.PREPARE, Collections.emptySet());
            this.updatedRectangleCallback = callback;
        }

        private int collectObjects(Collection out, Widget w) {
            int cnt = 0;
            Object o = scene.findObject(w);
            if (o != null) {
                cnt++;
                out.add(o);
            }
            for (Widget ch : w.getChildren()) {
                cnt += collectObjects(out, ch);
            }
            return cnt;
        }

        private boolean freeWidgets() {
            TimedIterable<Widget> ti = new TimedIterable(allWidgets.subList(from, allWidgets.size()), 400);
            Set<Object> objectsToRemove = new HashSet<>();
            int cleanedObjects = 0;
            int cleanedWidgets = 0;
            OUTER:
            for (Widget w : ti) {
                if (w == null) {
                    continue;
                }
                if (isCancelled()) {
                    return false;
                }
                Widget parent = w.getParentWidget();
                if (!(parent instanceof LayerWidget) || !w.isVisible()) {
                    // do not clear child widgets, they will be removed along with its parent
                    continue;
                }
                Rectangle ob = w.getBounds();
                if (ob == null) {
                    continue;
                }
                Rectangle wb = w.convertLocalToScene(ob);
                if (extendedViewportBounds.intersects(wb)) {
                    continue;
                }
                Object o = scene.findObject(w);
                if (o == null) {
                    continue;
                }
                if (w instanceof LineWidget) {
                    if (o instanceof ConnectionSet) {
                        ConnectionSet cs = (ConnectionSet) o;
                        for (Connection c : cs.getConnectionSet()) {
                            if (isConnectionReachable(c)) {
                                continue OUTER;
                            }
                        }
                    }
                }
                Collection<Widget> allW = scene.findWidgets(o);
                if (allW == null) {
                    continue;
                }
                cleanedObjects++;
                for (Widget x : allW) {
                    Widget pw = x.getParentWidget();
                    if (pw != null) {
                        pw.removeChild(x);
                        cleanedWidgets++;
                    }
                    int c = collectObjects(objectsToRemove, w);
                    if (pw != null) {
                        cleanedWidgets += c;
                    }
                }
            }
            for (Object o : objectsToRemove) {
                ObjectState s = scene.getObjectState(o);
                if (s != ObjectState.createNormal()) {
                    // retain potential object state
                    scene.removeObjectMapping(o);
                } else {
                    scene.removeObject(o);
                }
            }
            from += ti.getStopIndex();
            LOG.log(Level.FINE, "Cleaned objects: {0}, widgets: {1}", new Object[]{cleanedObjects, cleanedWidgets});
            return from < allWidgets.size();
        }

        @Override
        protected void processPhase(Phase phase) {
            switch (phase) {
                case PREPARE:
                    allWidgets = scene.getObjects().stream().parallel().map((o) -> scene.<Widget>getWidget(o)).
                            collect(Collectors.toList());
                    nextPhase = Phase.UPDATE_A;
                    return;
                case UPDATE_A:
                    Rectangle rect = scene.getVisibleSceneRect();
                    if (!extendedViewportBounds.contains(rect)) {
                        nextPhase = Phase.COMPLETE;
                        return;
                    }
                    // trim the updated rectangle, as objects will be removed from outside
                    // the extended viewport bounds and the scene may have to be refreshed
                    updatedRectangleCallback.accept(extendedViewportBounds);
                    if (freeWidgets()) {
                        nextPhase = null;
                    } else {
                        complete();
                    }
                    break;
            }
        }
    }
}
