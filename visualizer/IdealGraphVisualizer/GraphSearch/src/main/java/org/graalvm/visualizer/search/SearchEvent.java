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

import jdk.graal.compiler.graphio.parsing.model.FolderElement;

import java.util.EventObject;

/**
 * @author sdedic
 */
public final class SearchEvent extends EventObject {
    private final SearchTask searchTask;
    private final FolderElement searchedElement;
    private final SearchResultsModel model;

    private final int traversed;
    private final int matched;
    private final boolean completed;
    private final boolean itemCompleted;

    private boolean terminate;

    public SearchEvent(SearchController source, SearchTask searchTask, FolderElement entered, SearchResultsModel model) {
        super(source);
        this.searchTask = searchTask;
        this.searchedElement = entered;
        this.model = model;

        traversed = matched = 0;
        completed = false;
        itemCompleted = false;
    }

    public SearchEvent(SearchController source, SearchTask searchTask, FolderElement entered, SearchResultsModel model,
                       int traversed, int matched, boolean completed, boolean completedAll) {
        super(source);
        this.searchTask = searchTask;
        this.searchedElement = entered;
        this.model = model;
        this.traversed = traversed;
        this.matched = matched;
        this.completed = completedAll;
        this.itemCompleted = completed;
    }

    public SearchEvent(SearchController source, SearchTask searchTask, SearchResultsModel model,
                       boolean completed) {
        super(source);
        this.searchTask = searchTask;
        this.model = model;
        this.searchedElement = null;

        traversed = matched = 0;
        itemCompleted = false;

        this.completed = completed;
    }

    public SearchController getSource() {
        return (SearchController) super.getSource();
    }

    public void terminate() {
        terminate = true;
    }

    public boolean isTerminate() {
        return terminate;
    }

    public SearchTask getSearchTask() {
        return searchTask;
    }

    public FolderElement getSearchedElement() {
        return searchedElement;
    }

    public SearchResultsModel getModel() {
        return model;
    }

    public int getTraversed() {
        return traversed;
    }

    public int getMatched() {
        return matched;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isItemCompleted() {
        return itemCompleted;
    }


}
