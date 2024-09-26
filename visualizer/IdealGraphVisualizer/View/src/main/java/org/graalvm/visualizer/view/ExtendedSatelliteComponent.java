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
import org.openide.util.RequestProcessor;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ExtendedSatelliteComponent extends JComponent implements MouseListener, MouseMotionListener, Scene.SceneListener, ComponentListener {
    private static final Logger LOG = Logger.getLogger(ExtendedSatelliteComponent.class.getName());
    private static final RequestProcessor DELAY_RP = new RequestProcessor(ExtendedSatelliteComponent.class);

    private final DiagramScene scene;

    /**
     * Bridge between scene and satellite component
     */
    private final SVImpl sceneViewportImpl = new SVImpl();

    /**
     * Cached image, built / updated when {@link SceneViewport#sceneContentsUpdated} receives
     * notification.
     */
    private Image image;
    private int imageWidth;
    private int imageHeight;
    private double scale;

    /**
     * Flag set to true, when the view is reported to be final.
     */
    private boolean viewUpdated;

    /**
     * Scene size remembered during the update. If scene size changes, the image
     * is invalidated.
     */
    private Dimension sceneSize;

    /**
     * Pending update
     */
    // @GuardedBy(this)
    private RequestProcessor.Task pendingUpdate;

    public ExtendedSatelliteComponent(DiagramScene scene) {
        this.scene = scene;
        setDoubleBuffered(true);
        setPreferredSize(new Dimension(128, 128));
        addMouseListener(this);
        addMouseMotionListener(this);
        // react on viewport 
        scene.getScrollPane().getViewport().addChangeListener((e) -> sceneViewportChanged());
        // listen for extraction, compilation phase changes
        scene.getModel().getChangedEvent().addListener((e) -> SwingUtilities.invokeLater(this::update));
        sceneSize = scene.getSceneSize();
    }

    /**
     * Receives view update events from SceneUpdater and forces render & repaint
     * of the image. Serves as plug-in into SceneUpdater and provides an "extended viewport"
     * computed from the satellite view size, in scene coordinates/resolution.
     * <p/>
     * Reports to view updater that the scale / whatever has changed and that view
     * should be recomputed.
     */
    class SVImpl implements SceneViewport, Runnable {
        private final List<ChangeListener> listeners = new ArrayList<>();

        @Override
        public Rectangle getSceneViewRect() {
            assert SwingUtilities.isEventDispatchThread();
            return getSceneRectangle();
        }

        @Override
        public synchronized void addChangeListener(ChangeListener l) {
            listeners.add(l);
        }

        @Override
        public synchronized void removeChangeListener(ChangeListener l) {
            listeners.remove(l);
        }

        void fireChange() {
            ChangeListener[] ll;
            synchronized (this) {
                if (listeners.isEmpty()) {
                    return;
                }
                ll = listeners.toArray(new ChangeListener[listeners.size()]);
            }
            ChangeEvent chg = new ChangeEvent(ExtendedSatelliteComponent.this);
            for (ChangeListener l : ll) {
                l.stateChanged(chg);
            }
        }

        @Override
        public void sceneContentsUpdated(boolean finished, Rectangle validRectangle) {
            if (viewUpdated && image != null) {
                // if view is updated ignore the scene playing 'not visible' games,
                // since we have our image
                LOG.log(Level.FINE, "Scene update ignored, view image is ready");
                return;
            }
            synchronized (ExtendedSatelliteComponent.this) {
                if (finished) {
                    // attempt to cancel unnecessary scheduled update
                    if (pendingUpdate != null) {
                        LOG.log(Level.FINE, "Final update, trying to cancel scheduled partial one");
                        pendingUpdate.cancel();
                        pendingUpdate = null;
                    }
                }
                if (!isVisible()) {
                    return;
                }
                if (finished) {
                    // let other event handlers to finish
                    SwingUtilities.invokeLater(ExtendedSatelliteComponent.this::finalUpdate);
                } else {
                    if (pendingUpdate == null) {
                        pendingUpdate = DELAY_RP.post(this, 500);
                    }
                }
            }
        }

        @Override
        public void run() {
            synchronized (this) {
                pendingUpdate = null;
            }
            LOG.log(Level.FINE, "Satellite: perform repaint");
            SwingUtilities.invokeLater(ExtendedSatelliteComponent.this::update);
        }

        @Override
        public Rectangle getViewportRect() {
            return getBounds();
        }
    }

    /**
     * Scene viewport fires event when the scene dimensions/size changes. Also when
     * the viewport is resized - must be ignored.
     */
    private void sceneViewportChanged() {
        Dimension newSize = scene.getSceneSize();
        if (newSize.equals(sceneSize)) {
            return;
        }
        LOG.log(Level.FINE, "Scene size changed to {0}, invalidating", sceneSize);
        sceneSize = newSize;
        invalidateImage();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        verifyImageScale();
    }

    /**
     * Invalidates the image, initiates redraw in DiagramScene.
     */
    private void invalidateImage() {
        assert SwingUtilities.isEventDispatchThread();
        if (!viewUpdated) {
            // just redraw/repaint
            image = null;
            return;
        }
        image = null;
        imageHeight = imageWidth = -1;
        viewUpdated = false;
        sceneViewportImpl.fireChange();
    }


    @Override
    public void addNotify() {
        super.addNotify();
        scene.addSceneListener(this);
        JComponent viewComponent = scene.getView();
        if (viewComponent == null) {
            viewComponent = scene.createView();
        }
        viewComponent.addComponentListener(this);
        registerIfVisible();
    }

    @Override
    public void setVisible(boolean aFlag) {
        boolean oFlag = isVisible();
        super.setVisible(aFlag);
        if (getParent() == null || oFlag == aFlag) {
            return;
        }
        registerIfVisible();
        if (aFlag) {
            repaint();
        }
    }

    private void registerIfVisible() {
        if (isVisible()) {
            LOG.log(Level.FINE, "Satellite view visible, computed bounds: {0}, current number of objects: {1}", new Object[]{getSceneRectangle(), scene.getObjects().size()});
            scene.addSceneViewport(sceneViewportImpl);
        } else {
            scene.removeSceneViewport(sceneViewportImpl);
            LOG.log(Level.FINE, "Satellite view hidden");
        }
    }

    @Override
    public void removeNotify() {
        scene.getView().removeComponentListener(this);
        scene.removeSceneListener(this);
        registerIfVisible();
        super.removeNotify();
    }

    void finalUpdate() {
        update0(true);
    }

    void update() {
        update0(false);
    }

    void update0(boolean finalUpdate) {
        this.image = null;
        verifyImageScale();
        viewUpdated |= finalUpdate;
        if (this.isVisible()) {
            repaint();
            revalidate();
            validate();
        }
    }

    private Rectangle getSceneRectangle() {
        if (viewUpdated) {
            return scene.getSceneViewportSize();
        }
        verifyImageScale();
        Dimension size = getSize();
        int vw = (int) (size.width / scale);
        int vh = (int) (size.height / scale);
        return new Rectangle(0, 0, vh, vw);
    }

    private boolean verifyImageScale() {
        return verifyImageScale(true);
    }

    private boolean verifyImageScale(boolean invalidate) {
        if (!isDisplayable() || !isVisible()) {
            return false;
        }
        Dimension sSize = scene.getSceneSize();
        Dimension size = getSize();

        double sx = sSize.width > 0 ? Math.min((double) size.width / sSize.width, 1.0) : 1.0;
        double sy = sSize.width > 0 ? Math.min((double) size.height / sSize.height, 1.0) : 1.0;
        scale = Math.min(sx, sy);
        int vw = (int) (scale * sSize.width);
        int vh = (int) (scale * sSize.height);

        if (vw <= 0 || vh <= 0) {
            return false;
        }

        boolean inv = (imageWidth != vw || imageHeight != vh);
        if (image == null || inv) {
            // do not invalidate during repaint
            if (inv && invalidate) {
                invalidateImage();
            }
            imageWidth = vw;
            imageHeight = vh;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D gr = (Graphics2D) g;
        super.paint(g);

        if (!isVisible()) {
            return;
        }
        // do not invalidate
        if (verifyImageScale(false)) {
            LOG.log(Level.FINER, "Satellite view: paint, scene object count: {0}, dim = {1} x {2}", new Object[]{scene.getObjects().size(), imageWidth, imageHeight});
            image = this.createImage(imageWidth, imageHeight);
            Graphics2D ig = (Graphics2D) image.getGraphics();
            ig.scale(scale, scale);
            double oldFactor = scene.getZoomFactor();

            // bypass the DiagramScene zoom logic
            try {
                scene.setZoomFactor(scale);
                scene.paintOnViewport(sceneViewportImpl, ig);
            } finally {
                scene.setZoomFactor(oldFactor);
//                ig.dispose();
            }
        }
        if (image == null) {
            return;
        }

        Dimension size = getSize();
        int vx = (size.width - imageWidth) / 2;
        int vy = (size.height - imageHeight) / 2;
        gr.drawImage(image, vx, vy, this);

        JComponent component = scene.getView();
        double zoomFactor = scene.getZoomFactor();
        Rectangle viewRectangle = component != null ? component.getVisibleRect() : null;
        if (viewRectangle != null) {
            Rectangle window = new Rectangle(
                    (int) (viewRectangle.x * scale / zoomFactor),
                    (int) (viewRectangle.y * scale / zoomFactor),
                    (int) (viewRectangle.width * scale / zoomFactor),
                    (int) (viewRectangle.height * scale / zoomFactor));
            window.translate(vx, vy);
            gr.setColor(new Color(200, 200, 200, 128));
            gr.fill(window);
            gr.setColor(Color.BLACK);
            gr.drawRect(window.x, window.y, window.width - 1, window.height - 1);
        }
        gr.setColor(Color.BLACK);
        gr.drawRect(vx, vy, imageWidth, imageHeight);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        moveVisibleRect(e.getPoint());
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        moveVisibleRect(e.getPoint());
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        moveVisibleRect(e.getPoint());
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    private void moveVisibleRect(Point center) {
        JComponent component = scene.getView();
        if (component == null) {
            return;
        }
        double zoomFactor = scene.getZoomFactor();
        Dimension size = getSize();
        verifyImageScale();
        int vx = (size.width - imageWidth) / 2;
        int vy = (size.height - imageHeight) / 2;

        int cx = (int) ((center.x - vx) / scale * zoomFactor);
        int cy = (int) ((center.y - vy) / scale * zoomFactor);

        Rectangle visibleRect = component.getVisibleRect();
        visibleRect.x = cx - visibleRect.width / 2;
        visibleRect.y = cy - visibleRect.height / 2;
        component.scrollRectToVisible(visibleRect);

        this.repaint();
    }

    @Override
    public void sceneRepaint() {
        // repaint ();
    }

    @Override
    public void sceneValidating() {
    }

    @Override
    public void sceneValidated() {
    }

    @Override
    public void componentResized(ComponentEvent e) {
        repaint();
    }

    @Override
    public void componentMoved(ComponentEvent e) {
        repaint();
    }

    @Override
    public void componentShown(ComponentEvent e) {
    }

    @Override
    public void componentHidden(ComponentEvent e) {
    }
}
