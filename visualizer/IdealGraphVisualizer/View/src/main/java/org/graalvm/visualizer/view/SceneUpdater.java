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

import java.awt.*;
import java.awt.geom.Area;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.graalvm.visualizer.data.Pair;
import org.graalvm.visualizer.graph.Diagram;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;
import org.openide.windows.TopComponent;

/**
 * Organizes updates of the Scene. Each Updater maintains its own
 * RequestProcessor where it computes layout and performs all work which is not
 * necessary to do in AWT.
 *
 * @author sdedic
 */
class SceneUpdater {
    private static final Logger LOG = Logger.getLogger(SceneUpdater.class.getName());

    /**
     * Delay for scheduling a viewport update task; mainly to update the
     * viewport when the swing layout is stable.
     */
    private static final int VIEWPORT_UPDATE_DELAY = 50;

    /**
     * The delay after which the widgets outside the 'extended' region will be
     * cleared out
     */
    private static final int VIEWPORT_CLEAN_DELAY = 500;

    /**
     * Request processor which serializes off-screen updates
     */
    private final RequestProcessor processor;

    /**
     * The scene
     */
    private final DiagramScene scene;

    private final List<ChangeListener> changeListeners = new ArrayList<>();

    /**
     * Rectangle bounding the updated/created widgets. Some widgets may be
     * created even outside of the rectangle.
     */
    private Rectangle updatedRectangle = new Rectangle(0, 0, 0, 0);

    /**
     * Pending task to update the viewport and create the widgets
     */
    private SceneUpdaterTask.DisplayWidgets viewportUpdate;

    /**
     * Task to clean up widgets outside of the visible area
     */
    private SceneUpdaterTask.Cleaner viewportCleanup;

    /**
     * The diagram model
     */
    private final DiagramViewModel model;

    /**
     * The last-seen diagram
     */
    private Diagram lastDiagram;

    /**
     * Tasks, which should be executed after the layout task completes
     * successfully.
     */
    private List<Runnable> doWhenLayoutCompletes = new ArrayList<>();

    /**
     * The shape, possibly several disjoint ones, which has been validated.
     */
    private Area validatedShape = new Area(new Rectangle(0, 0, 0, 0));

    private Set<Pair<Point, Point>> lineCache = new HashSet<>();

    private SceneUpdaterTask displayBlocker;
    /**
     * Just GC-prevention for listener
     */
    private final PropertyChangeListener topRegistryListener;

    public SceneUpdater(DiagramScene scene) {
        this.scene = scene;
        this.model = scene.getModel();
        lastDiagram = model.getDiagramToView();
        // PENDING: remove listener, if the scene (topcomponent) closes
        scene.getScrollPane().getViewport().addChangeListener((e) -> refreshView(false));
        model.getDiagramChangedEvent().addListener((e) -> SwingUtilities.invokeLater(this::forceViewRefresh));
        String sceneName = model.getContainer().getName();
        processor = new RequestProcessor("SceneUpdater - " + sceneName);

        // register listener on component close - will cancel all the tasks. Saves CPU time
        // when the user closes the diagram and then reopens another one.
        TopComponent.getRegistry().addPropertyChangeListener(
                WeakListeners.propertyChange(
                        topRegistryListener = (e) -> {
                            if (!TopComponent.Registry.PROP_TC_CLOSED.equals(e.getPropertyName())) {
                                return;
                            }
                            if (e.getNewValue() == scene.getTopComponent()) {
                                cancelAll();
                            }
                        },
                        TopComponent.getRegistry())
        );
    }

    public void addChangeListener(ChangeListener l) {
        synchronized (this) {
            changeListeners.add(l);
        }
    }

    public void removeChangeListener(ChangeListener l) {
        synchronized (this) {
            changeListeners.remove(l);
        }
    }

    protected void fireChangeListeners() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::fireChangeListeners);
            return;
        }
        ChangeListener[] ll;
        synchronized (this) {
            if (changeListeners.isEmpty()) {
                return;
            }
            ll = changeListeners.toArray(new ChangeListener[changeListeners.size()]);
        }
        ChangeEvent ev = new ChangeEvent(this);
        for (ChangeListener l : ll) {
            l.stateChanged(ev);
        }
    }

    public boolean whenDiagramShown(Runnable r) {
        synchronized (this) {
            if (isStubDiagram() || displayBlocker != null || lastDiagram != model.getDiagramToView()) {
                LOG.log(Level.FINE, "Diagram is stub or layout is blocked, sheduling for later execution.");
                doWhenLayoutCompletes.add(r);
                return true;
            }
        }
        LOG.log(Level.FINE, "Diagram is shown, executing immediately.");
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
        return false;
    }

    private boolean isStubDiagram() {
        return model.isStubDiagram(lastDiagram);
    }

    public void cancelAll() {
        LOG.log(Level.FINE, "Scene component closed, cancelling tasks");
        synchronized (this) {
            // cancel pending viewport update
            cancelUpdateTask(viewportUpdate);
            cancelUpdateTask(viewportCleanup);

            viewportUpdate = null;
            viewportCleanup = null;
        }
    }

    private void forceViewRefresh() {
        Diagram d = model.getDiagramToView();
        synchronized (this) {
            if (lastDiagram == d) {
                LOG.log(Level.FINE, "Don't react on same Diagram.");
                return;
            }
            boolean wasStub = isStubDiagram();
            lastDiagram = d;
            if (isStubDiagram()) {
                LOG.log(Level.FINE, "Diagram is stub, preparing scene.");
                updatedRectangle = new Rectangle(0, 0, 0, 0);
                validatedShape = new Area(new Rectangle(0, 0, 0, 0));
                scene.prepareUpdate();
                fireChangeListeners();
                return;
            } else if (!wasStub) {
                LOG.log(Level.FINE, "Diagram isn't stub, preparing scene.");
                updatedRectangle = new Rectangle(0, 0, 0, 0);
                validatedShape = new Area(new Rectangle(0, 0, 0, 0));
                scene.prepareUpdate();
                fireChangeListeners();
            }
            LOG.log(Level.FINE, makeDiagramLog());
            if (viewportUpdate != null) {
                cancelUpdateTask(viewportUpdate);
                viewportUpdate = null;
            }
        }
        refreshView(true);
    }

    private String makeDiagramLog() {
        String sb = "New Diagram for scene update:\n" + "Diagram: " + lastDiagram + ".\n" +
                "\tGraph name: " + lastDiagram.getGraph().getName() + ".\n" +
                "\tnodes count: " + lastDiagram.getGraph().getNodeCount() + ".\n" +
                "\tfigures count: " + lastDiagram.getFigures().size() + ".\n" +
                "\tvisible figures count: " + lastDiagram.getFigures().stream().filter((n) -> n.isVisible()).count() + ".\n" +
                "\tboundary figures count: " + lastDiagram.getFigures().stream().filter((n) -> n.isBoundary()).count() + ".";
        return sb;
    }

    private void processLayoutTasks(SceneUpdaterTask blocker) {
        List<Runnable> completionTasks;
        synchronized (this) {
            assert !isStubDiagram();
            if (displayBlocker != null && this.displayBlocker != blocker) {
                return;
            }
            completionTasks = doWhenLayoutCompletes;
            doWhenLayoutCompletes = new ArrayList<>();
            this.displayBlocker = null;
        }
        Runnable r = () -> {
            LOG.log(Level.FINE, "Running after-display tasks.");
            scene.finishUpdate();
            for (Runnable x : completionTasks) {
                x.run();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            LOG.log(Level.FINE, "Scheduling post-display tasks to AWT");
            SwingUtilities.invokeLater(r);
        }
    }

    private void cancelUpdateTask(SceneUpdaterTask t) {
        if (t == null) {
            return;
        }
        t.cancel();
        if (displayBlocker == t) {
            displayBlocker = null;
        }
    }

    public boolean refreshView(boolean blockDisplay) {
        assert SwingUtilities.isEventDispatchThread();
        if (!scene.getTopComponent().isOpened() || isStubDiagram() || lastDiagram != model.getDiagramToView()) {
            LOG.log(Level.FINE, "Nothing to do with unopened TopComponent, stub or old Diagram.");
            // do not bother
            return false;
        }
        Rectangle viewRect = scene.getVisibleSceneRect();
        // compute using zoom factor
        synchronized (this) {
            LOG.log(Level.FINE, "Potential viewport update for extent: {0} ", viewRect);
            if (updatedRectangle.contains(viewRect)) {
                Dimension d = updatedRectangle.getSize();
                Dimension d2 = viewRect.getSize();
                if (d.width > 5 * d2.width || d.height > 5 * d2.height) {
                    LOG.log(Level.FINE, "The updated area {0} is extremely large, cleaning excess widgets and keeping for {1}", new Object[]{updatedRectangle, viewRect});
                    SwingUtilities.invokeLater(() -> cleanViewport());
                }
                return true;
            }
            if (viewportUpdate != null) {
                if (viewportUpdate.getPhase() != SceneUpdaterTask.Phase.OBSOLETE && viewportUpdate.getExtendedBounds().contains(viewRect)) {
                    LOG.log(Level.FINE, "Viewport update is alredy scheduled for {0}, view bounds {1} still inside.", new Object[]{viewportUpdate.getExtendedBounds(), viewRect});
                    return true;
                }
                // do not schedule the task
                LOG.log(Level.FINE, "Old viewport update cancelled");
                cancelUpdateTask(viewportUpdate);
                viewportUpdate = null; // will be immediately replaced
            }
            if (viewportCleanup != null) {
                LOG.log(Level.FINE, "Old viewport cleanup cancelled");
                cancelUpdateTask(viewportCleanup);
                viewportCleanup = null;
            }

            SceneUpdaterTask.DisplayWidgets upd = new SceneUpdaterTask.DisplayWidgets(scene, model.getLayoutSetting(), processor, lineCache);
            if (blockDisplay) {
                displayBlocker = upd;
            }
            LOG.log(Level.FINE, "New viewport update task {2}, for rectangle {0}, current view rect {1}", new Object[]{upd.getExtendedBounds(), viewRect, upd});
            viewportUpdate = upd;
            viewportUpdate.schedule(VIEWPORT_UPDATE_DELAY).onCompletion((e) -> {
                synchronized (this) {
                    if (upd != viewportUpdate) {
                        return;
                    }
                    if (!isStubDiagram()) {
                        updatedRectangle = upd.getExtendedBounds();
                        validatedShape.add(new Area(updatedRectangle));
                        fireChangeListeners();
                    }
                    viewportUpdate = null;
                    this.lineCache = upd.getLineCache();
                }
                processLayoutTasks(upd);
                SwingUtilities.invokeLater(() -> {
                    scene.fireSceneUpdated(true);
                    // must be run in AWT
                    cleanViewport();
                });
            });
        }
        return false;
    }

    /**
     * Executes the cleaning task.
     */
    void cleanViewport() {
        synchronized (this) {
            if (viewportCleanup != null) {
                return;
            }
            LOG.log(Level.FINE, "Scheduling viewport cleanup task");
            SceneUpdaterTask.Cleaner cl = new SceneUpdaterTask.Cleaner(scene, processor, this::limitUpdatedRectangle);
            cl.schedule(VIEWPORT_CLEAN_DELAY).onCompletion((e) -> {
                synchronized (this) {
                    if (viewportCleanup != cl) {
                        return;
                    }
                    viewportCleanup = null;
                }
            });
            viewportCleanup = cl;
        }
    }

    void limitUpdatedRectangle(Rectangle r) {
        Rectangle ints;
        Rectangle viewRect = scene.getVisibleSceneRect();
        synchronized (this) {
            if (!updatedRectangle.contains(viewRect)) {
                // the rectangle is probably being laid out
                return;
            }
            updatedRectangle = ints = updatedRectangle.intersection(r);
        }
        if (!ints.contains(viewRect)) {
            // refresh if the cleaner did something in the visible area
            refreshView(false);
        }
    }

    public Area getValidatedShape() {
        synchronized (this) {
            return new Area(validatedShape);
        }
    }

    public Rectangle getValidatedRectangle() {
        return updatedRectangle;
    }

}
