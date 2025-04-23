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

import org.netbeans.api.visual.widget.Scene;

import javax.swing.event.ChangeListener;
import java.awt.Rectangle;

/**
 * Supplemental interface for clients which interface with lazy Scene.
 * If a client displays a different view on the {@link Scene}, it may need
 * to display a different portion of the scene than the main scene component.
 * Such client should expose SceneViewport interface to the Scene. The Scene
 * will then consult the Viewports to get the bounding rectangle for which the
 * model widgets should be created.
 * <p/>
 * The client should inform the Scene, using the {@link ChangeListener} interface
 * whenever its requirements change, i.e. when the client's are is scrolled, zoom
 * factor changed etc.
 *
 * @author sdedic
 */
public interface SceneViewport {
    /**
     * Returns the visible rectangle.
     *
     * @return Returns the view rectangle
     */
    public Rectangle getSceneViewRect();

    public Rectangle getViewportRect();

    /**
     * Informs the viewport, that the contents of the scene contents
     * has been updated. The method is called when the scene materializes
     * widgets taking account into {@link #getSceneViewRect()} values.
     * <p/>
     * The viewport should then refresh its presentation, using scene
     * Widgets, if necessary.
     * <p/>
     * Interim updates are also sent using this method. If the update is "interim",
     * the scene will continue updating itself, the 'finished' parameter will be set
     * to {@code false}. If 'finished' is {@code true}, the scene was considered
     * valid at some point in time, but there may be additional updates underway.
     *
     * @param validRectangle the rectangle which is being populated
     * @param finished       whether the scene update is complete
     */
    public void sceneContentsUpdated(boolean finished, Rectangle validRectangle);

    /**
     * Registers a listener that will be informed about change events.
     *
     * @param l the listener to register
     */
    public void addChangeListener(ChangeListener l);

    /**
     * Removes change listener
     *
     * @param l the listener to remove
     */
    public void removeChangeListener(ChangeListener l);
}
