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

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.search.GraphSearchEngine.SearchRunnable;
import org.openide.util.RequestProcessor;
import org.openide.util.Task;
import org.openide.util.TaskListener;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author sdedic
 */
public final class SearchTask {
    private final RequestProcessor.Task task;
    private final SearchRunnable worker;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final SearchResultsModel model;

    SearchTask(SearchResultsModel model) {
        this.task = null;
        this.worker = null;
        this.model = model;
    }

    SearchTask(RequestProcessor.Task task, SearchRunnable worker, SearchResultsModel model) {
        this.task = task;
        this.worker = worker;
        this.model = model;
    }

    public static SearchTask finished(SearchResultsModel model) {
        return new SearchTask(model);
    }

    public Collection<InputGraph> pendingGraphs() {
        if (worker == null) {
            return Collections.emptySet();
        } else {
            return worker.getPendingGraphs();
        }
    }

    public boolean cancel() {
        if (task == null) {
            return false;
        }
        if (cancelled.get()) {
            return true;
        }
        cancelled.compareAndSet(false, task.cancel() || worker.cancel());
        return cancelled.get();
    }

    public Task getTask() {
        return task == null ? Task.EMPTY : task;
    }

    public boolean isFinished() {
        return getTask().isFinished();
    }

    public boolean isCancelled() {
        return task != null && (cancelled.get() || worker.isCancelled());
    }

    public void addTaskListener(TaskListener tl) {
        if (task != null) {
            task.addTaskListener(tl);
        }
    }

    public void removeTaskListener(TaskListener tl) {
        if (task != null) {
            task.removeTaskListener(tl);
        }
    }

    public SearchResultsModel getModel() {
        return model;
    }
}
