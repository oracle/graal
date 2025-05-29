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
package org.graalvm.visualizer.view.widgets.actions;

import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.widget.Scene;
import org.netbeans.api.visual.widget.Widget;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;

public final class CustomizablePanAction extends WidgetAction.LockedAdapter {
    private boolean enabled = true;

    private Scene scene;
    private JScrollPane scrollPane;
    private Point lastLocation;

    private final int modifiersExMask;
    private final int modifiersEx;

    public CustomizablePanAction(int modifiersExMask, int modifiersEx) {
        this.modifiersExMask = modifiersExMask;
        this.modifiersEx = modifiersEx;
    }

    @Override
    protected boolean isLocked() {
        return scrollPane != null;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            if (isLocked())
                throw new IllegalStateException();

            this.enabled = enabled;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public State mousePressed(Widget widget, WidgetMouseEvent event) {
        if (isLocked())
            return State.createLocked(widget, this);
        if (enabled && (event.getModifiersEx() & modifiersExMask) == modifiersEx) {
            scene = widget.getScene();
            scrollPane = findScrollPane(scene.getView());
            if (scrollPane != null) {
                lastLocation = scene.convertSceneToView(widget.convertLocalToScene(event.getPoint()));
                SwingUtilities.convertPointToScreen(lastLocation, scene.getView());
                return State.createLocked(widget, this);
            }
        }
        return State.REJECTED;
    }

    private JScrollPane findScrollPane(JComponent component) {
        for (; ; ) {
            if (component == null)
                return null;
            if (component instanceof JScrollPane)
                return ((JScrollPane) component);
            Container parent = component.getParent();
            if (!(parent instanceof JComponent))
                return null;
            component = (JComponent) parent;
        }
    }

    @Override
    public State mouseReleased(Widget widget, WidgetMouseEvent event) {
        boolean state = pan(widget, event.getPoint());
        if (state)
            scrollPane = null;
        return state ? State.createLocked(widget, this) : State.REJECTED;
    }

    @Override
    public State mouseDragged(Widget widget, WidgetMouseEvent event) {
        return pan(widget, event.getPoint()) ? State.createLocked(widget, this) : State.REJECTED;
    }

    private boolean pan(Widget widget, Point newLocation) {
        if (scrollPane == null || scene != widget.getScene())
            return false;
        newLocation = scene.convertSceneToView(widget.convertLocalToScene(newLocation));
        SwingUtilities.convertPointToScreen(newLocation, scene.getView());
        JComponent view = scene.getView();
        Rectangle rectangle = view.getVisibleRect();
        rectangle.x += lastLocation.x - newLocation.x;
        rectangle.y += lastLocation.y - newLocation.y;
        view.scrollRectToVisible(rectangle);
        lastLocation = newLocation;
        return true;
    }
}
