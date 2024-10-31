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

package org.graalvm.visualizer.view.impl;

import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.view.DiagramViewModel;
import org.openide.util.RequestProcessor;

import java.beans.PropertyChangeEvent;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author odouda
 */
public class DiagramCacheUpdater {
    private static final Logger LOG = Logger.getLogger(DiagramCacheUpdater.class.getName());

    private Consumer<Diagram> diagramReadyCallback;

    private final RequestProcessor processor;
    private final WeakReference<DiagramViewModel> originModel;
    private DiagramCacheBase currentCache;

    private RequestProcessor.Task scheduledTask;
    private DiagramCacheTask diagramTask;
    private volatile Phase phase = Phase.DONE;

    public DiagramCacheUpdater(DiagramViewModel model) {
        LOG.log(Level.FINE, "Created for model: {0}", model);
        processor = new RequestProcessor(DiagramCacheUpdater.class);
        this.originModel = new WeakReference(model);
        model.addPropertyChangeListener(this::modelChanged);
    }

    public enum Phase {
        BUILD,
        FILTER,
        LAYOUT,
        EXTRACT,
        DONE
    }

    public void scheduleUpdate(DiagramCacheBase cache, Consumer<Diagram> diagramReadyCallback) {
        DiagramViewModel model;
        Phase nextPhase;
        synchronized (this) {
            model = originModel.get();
            nextPhase = cache.nextPhase(model);
            if (model == null) {
                cancelPendingTask();
                LOG.log(Level.FINE, "Model was GCed, ending.");
                return;
            } else if (phase != nextPhase && diagramTask != null) {
                cancelPendingTask();
                LOG.log(Level.WARNING, "Phase changed resheduling.");
            }
            if (diagramTask != null) {
                LOG.log(Level.FINE, "Diagram {0} task {1} already scheduled.", new Object[]{phase, diagramTask});
                return;
            }
            this.diagramReadyCallback = diagramReadyCallback;
        }
        makeUpdateTask(cache, model, nextPhase);
    }

    private void makeUpdateTask(DiagramCacheBase cache, DiagramViewModel model, Phase nextPhase) {
        synchronized (this) {
            phase = nextPhase;
            if (phase != Phase.DONE) {
                currentCache = cache;
                diagramTask = DiagramCacheTask.makeTask(cache.get(), model, this, nextPhase);
                scheduledTask = processor.post(diagramTask);
                LOG.log(Level.FINE, "Scheduled Diagram {0} task: {1}.", new Object[]{phase, diagramTask});
                return;
            }
            currentCache = null;
            diagramTask = null;
            scheduledTask = null;
        }
        fireDiagramDone(cache.get());
    }

    void afterTask(DiagramCacheTask t) {
        DiagramCacheBase newCache;
        DiagramViewModel origin;
        synchronized (this) {
            LOG.log(Level.FINE, "Finished Diagram {1} task: {0}.", new Object[]{t, phase});
            if (diagramTask != t || t.cancelled()) {
                return;
            }
            origin = originModel.get();
            if (origin == null) {
                return;
            }
            newCache = currentCache.makeCache(origin, t.outputDiagram);
        }
        makeUpdateTask(newCache, origin, newCache.nextPhase(origin));
    }

    private void fireDiagramDone(Diagram diagram) {
        LOG.log(Level.FINE, "Diagram is done.");
        Consumer<Diagram> callback;
        synchronized (this) {
            assert diagramReadyCallback != null;
            callback = diagramReadyCallback;
            diagramReadyCallback = null;
        }
        callback.accept(diagram);
    }

    private void modelChanged(PropertyChangeEvent event) {
        Phase currentPhase = phase;
        switch (event.getPropertyName()) {
            case DiagramViewModel.PROP_LAYOUT_SETTING:
            case DiagramViewModel.PROP_SHOW_BLOCKS:
                if (currentPhase != Phase.EXTRACT && currentPhase != Phase.LAYOUT) {
                    return;
                }
                break;
            case DiagramViewModel.PROP_SELECTED_GRAPH:
                break;
            case DiagramViewModel.PROP_HIDDEN_NODES:
            case DiagramViewModel.PROP_SHOW_NODE_HULL:
                if (currentPhase != Phase.EXTRACT) {
                    return;
                }
                break;
            case DiagramViewModel.PROP_FILTERS:
                if (currentPhase == Phase.BUILD) {
                    return;
                }
                break;
            default:
                return;
        }
        cancelPendingTask();
    }

    private synchronized void cancelPendingTask() {
        if (diagramTask == null && scheduledTask == null) {
            return;
        }
        LOG.log(Level.FINE, "Cancelling task {0}.", diagramTask);
        diagramReadyCallback = null;
        if (diagramTask != null) {
            diagramTask.cancel();
            diagramTask = null;
        }
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }
}
