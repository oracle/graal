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
package org.graalvm.visualizer.search.ui;

import javax.swing.JPanel;
import org.graalvm.visualizer.search.SearchController;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author sdedic
 */
public class LiveSearchEntry implements SearchResultsEntry {
    private final SearchController controller;
    private ResultViewPanel resultsPanel;

    public LiveSearchEntry(SearchController controller) {
        this.controller = controller;
    }
    
    @Override
    public Lookup getLookup() {
        return Lookups.proxy(() -> resultsPanel == null ? Lookup.EMPTY : resultsPanel.getLookup());
    }

    @Override
    public JPanel getResultsPanel() {
        if (resultsPanel == null) {
            resultsPanel = new ResultViewPanel(controller);
        }
        return resultsPanel;
    }

    @Override
    public void cancelSearch() {
        controller.pendingSearch().cancel();
    }

    @Override
    public void viewOpened() {
    }

    @Override
    public void viewClosed() {
        controller.pendingSearch().cancel();
    }
}
