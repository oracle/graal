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

import java.util.EventListener;

/**
 * @author sdedic
 */
public interface DiagramViewerListener extends EventListener {
    /**
     * Informs that state of the view has changed. This includes selection, focus,
     * highlight etc.
     *
     * @param ev event with details
     */
    public void stateChanged(DiagramViewerEvent ev);

    /**
     * Informs that interaction behaviour of the view has changed. For example
     * panning/selection.
     *
     * @param ev event with details
     */
    public void interactionChanged(DiagramViewerEvent ev);

    /**
     * Informs that display format has changed. Zoom factor, repositioning, ...
     *
     * @param ev
     */
    public void displayChanged(DiagramViewerEvent ev);

    /**
     * The diagram has been changed. The diagram is not necessarily the final one.
     * The final diagram will be send in {@link #diagramReady}. The old diagram
     * is not displayed already.
     *
     * @param ev event with details
     */
    public void diagramChanged(DiagramViewerEvent ev);

    /**
     * The diagram became ready.
     *
     * @param ev event with details
     */
    public void diagramReady(DiagramViewerEvent ev);
}
