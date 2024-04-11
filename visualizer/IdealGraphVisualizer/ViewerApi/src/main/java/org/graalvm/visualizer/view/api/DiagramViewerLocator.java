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
package org.graalvm.visualizer.view.api;

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.data.services.GraphViewer;

import java.util.List;

/**
 * Locates graph viewers
 *
 * @author sdedic
 */
public interface DiagramViewerLocator extends GraphViewer {
    /**
     * Returns the model the currently active model. May return {@code null} if no
     * diagram is opened at all
     *
     * @return the current diagram model, or {@code null}
     */
    @Override
    public DiagramViewer getActiveViewer();

    /**
     * Returns the active viewer's model
     *
     * @return the active model
     */
    public default DiagramModel getActiveModel() {
        DiagramViewer v = getActiveViewer();
        return v == null ? null : v.getModel();
    }

    @Override
    public default InputGraph getActiveGraph() {
        DiagramModel vm = getActiveModel();
        return vm == null ? null : vm.getGraphToView();
    }

    /**
     * Returns all available viewers
     *
     * @return
     */
    public List<DiagramViewer> getViewers();

    /**
     * Finds diagram model that represents the group. All viewers that
     * display graphs from the data group will be returned; the viewers
     * may display different graph types.
     *
     * @param g group which the viewer should display
     * @return list of viewers
     */
    public List<DiagramViewer> find(Group g);


    @Override
    public List<DiagramViewer> findCompatible(InputGraph g);

    @Override
    public List<DiagramViewer> find(InputGraph graph);
}
