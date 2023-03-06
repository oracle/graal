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
import org.openide.util.Lookup;

/**
 * Interface between SearchREsults tab and the container.
 * @author sdedic
 */
public interface SearchResultsEntry {
    /**
     * @return Returns Lookup of the Search Results. Should reflect the selected nodes.
     */
    public Lookup getLookup();
    
    /**
     * @return the actual UI to be embedded into the result view
     */
    public JPanel getResultsPanel();
    
    /**
     * Cancels the search, if pending
     */
    public void cancelSearch();
    
    /**
     * Informs the view was opened.
     */
    public void viewOpened();
    
    /**
     * Informs the view was closed.
     */
    public void viewClosed();
}
