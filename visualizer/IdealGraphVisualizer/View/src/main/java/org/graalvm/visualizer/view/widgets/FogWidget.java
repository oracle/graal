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
package org.graalvm.visualizer.view.widgets;

import org.graalvm.visualizer.view.DiagramScene;
import org.netbeans.api.visual.widget.Widget;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Creates a "fog of war" over area that for which widgets were not yet created.
 * Paints a hatched area within the viewport excluding areas, which were already
 * populated by widgets.
 */
public class FogWidget extends Widget {
    private final TexturePaint hatchPaint;
    private Area validArea;

    public FogWidget(DiagramScene scene) {
        super(scene);
        setOpaque(false);
        setCheckClipping(false);
        hatchPaint = makeHatch();
        validArea = new Area(); // empty area
    }

    private static TexturePaint makeHatch() {
        int size = 10;
        BufferedImage hatchImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = (Graphics2D) hatchImage.getGraphics();
        g2.setColor(Color.white);
        g2.fillRect(0, 0, size, size);
        g2.setColor(Color.gray);
        g2.drawLine(0, 0, size, size);
        g2.dispose();
        return new TexturePaint(hatchImage, new Rectangle2D.Double(0, 0, size, size));
    }

    public void setValidShape(Area a) {
        this.validArea = a;
        revalidate();
    }

    public DiagramScene getDiagramScene() {
        return (DiagramScene) super.getScene();
    }

    @Override
    protected Rectangle calculateClientArea() {
        Dimension sceneSize = getDiagramScene().getSceneSize();
        int curWidth = sceneSize.width + DiagramScene.BORDER_SIZE;
        int curHeight = sceneSize.height + DiagramScene.BORDER_SIZE;
        return new Rectangle(new Dimension(curWidth, curHeight));
    }

    @Override
    protected void paintWidget() {
        float z = (float) getScene().getZoomFactor();

        // must compensate zoom factor when drawing the hatches
        Graphics2D ng = null;
        try {
            Rectangle rect = getDiagramScene().getVisibleSceneRect();
            Area view = new Area(rect);
            view.subtract(validArea);
            if (view.isEmpty()) {
                return;
            }
            // if the valid area covers the entire scene, do not paint any hatch:
            Area view2 = new Area(new Rectangle(getDiagramScene().getSceneSize()));
            view2.subtract(validArea);
            if (view2.isEmpty()) {
                return;
            }

            Graphics2D g = getGraphics();
            ng = (Graphics2D) g.create();
            Area enlarged;
            if (z != 1) {
                AffineTransform zoom = AffineTransform.getScaleInstance(z, z);
                // the view comes in scene coordinates; they need to be zoomed according to the scaling factor
                // the get the on-screen real coordinates
                enlarged = view.createTransformedArea(zoom);
                try {
                    // compensate the zoom on the graphic, so that the hatchPaint is applied in its original size
                    ng.transform(zoom.createInverse());
                } catch (NoninvertibleTransformException ex) {
                    // should not really happen, zoom is always reversible
                }
            } else {
                enlarged = view;
            }
            ng.setColor(new Color(240, 240, 240));
            ng.setPaint(hatchPaint);
            ng.fill(enlarged);
        } finally {
            if (ng != null) {
                ng.dispose();
            }
        }
    }

}
