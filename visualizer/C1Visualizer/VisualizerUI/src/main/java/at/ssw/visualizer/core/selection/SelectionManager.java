/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.core.selection;

import javax.swing.event.ChangeListener;

/**
 *
 * @author Christian Wimmer
 */
public class SelectionManager {
    private static final SelectionManager SINGLETON = new SelectionManager();

    public static SelectionManager getDefault() {
        return SINGLETON;
    }


    /** Default selection returned when no TopComponent is active.
     * It is also used to maintain listeners added to the selection manager. */
    private final Selection emptySelection;
    private Selection curSelection;

    private SelectionManager() {
        emptySelection = new Selection();
        curSelection = emptySelection;
    }

    public Selection getCurSelection() {
        return curSelection;
    }

    public void setSelection(Selection sel) {
        if (curSelection != sel) {
            curSelection = sel;
            fireChangeEvent();
        }
    }

    public void removeSelection(Selection sel) {
        if (curSelection == sel) {
            curSelection = emptySelection;
            fireChangeEvent();
        }
    }

    protected void fireChangeEvent() {
        emptySelection.fireChangeEvent();
    }

    public void addChangeListener(ChangeListener listener) {
        emptySelection.addChangeListener(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        emptySelection.removeChangeListener(listener);
    }
}
