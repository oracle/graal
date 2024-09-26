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
package org.graalvm.visualizer.search.ui.actions;

import org.graalvm.visualizer.search.SearchController;
import org.graalvm.visualizer.search.SearchEvent;
import org.graalvm.visualizer.search.SearchListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionState;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;

/**
 * Extends the search to the previous or following phases (graphs in the container).
 *
 * @author sdedic
 */
public abstract class ExtendSearchAction extends AbstractAction implements SearchListener {
    private final boolean previous;
    private final SearchController ctrl;

    ExtendSearchAction(SearchController ctrl, boolean previous) {
        this.ctrl = ctrl;
        this.previous = previous;
        if (ctrl != null) {
            ctrl.addSearchListener(WeakListeners.create(SearchListener.class, this, ctrl));
        }
    }

    @Override
    public boolean isEnabled() {
        return ctrl.canSearch(previous);
    }

    @Override
    public void finished(SearchEvent ev) {
        setEnabled(ctrl.canSearch(previous));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        boolean stopAtFirst = (e.getModifiers() & ActionEvent.SHIFT_MASK) == 0;
        ctrl.extendSearch(previous, stopAtFirst);
    }

    @NbBundle.Messages({
            "ACTION_SearchNextPhases=Search in following phases",
    })
    @ActionRegistration(displayName = "#ACTION_SearchNextPhases",
            lazy = true,
            iconBase = "org/graalvm/visualizer/search/resources/searchForward.png",
            enabledOn =
            @ActionState(
                    type = SearchController.class,
                    useActionInstance = true,
                    listenOn = SearchListener.class
            )
    )
    @ActionID(category = "Search", id = Forward.ID)
    public static final class Forward extends ExtendSearchAction {
        public static final String ID = "org.graalvm.isualizer.search.ui.ExtendSearchAction.Forward";

        public Forward(SearchController ctrl) {
            super(ctrl, false);
        }
    }

    @NbBundle.Messages({
            "ACTION_SearchPreviousPhases=Search in preceding phases",
    })
    @ActionRegistration(displayName = "#ACTION_SearchPreviousPhases",
            lazy = true,
            iconBase = "org/graalvm/visualizer/search/resources/searchBackward.png",
            enabledOn =
            @ActionState(
                    type = SearchController.class,
                    useActionInstance = true,
                    listenOn = SearchListener.class
            )
    )
    @ActionID(category = "Search", id = Backward.ID)
    public static final class Backward extends ExtendSearchAction {
        public static final String ID = "org.graalvmvisualizer.search.ui.ExtendSearchAction.Backward";

        public Backward(SearchController ctrl) {
            super(ctrl, true);
        }

    }
}
