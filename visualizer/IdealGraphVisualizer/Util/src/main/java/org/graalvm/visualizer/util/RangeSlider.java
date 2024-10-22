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
package org.graalvm.visualizer.util;

import jdk.graal.compiler.graphio.parsing.model.ChangedListener;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

public class RangeSlider extends JComponent implements ChangedListener<RangeSliderModel>, MouseListener, MouseMotionListener, Scrollable {

    public static final float BAR_HEIGHT = 22;
    public static final float BAR_SELECTION_ENDING_HEIGHT = 16;
    public static final float BAR_SELECTION_HEIGHT = 10;
    public static final float BAR_THICKNESS = 2;
    public static final float BAR_CIRCLE_SIZE = 9;
    public static final float BAR_CIRCLE_CONNECTOR_SIZE = 6;
    public static final int MOUSE_ENDING_OFFSET = 3;
    public static final Color BACKGROUND_COLOR = Color.white;
    public static final Color BAR_COLOR = Color.black;
    public static final Color BAR_INACTIVE_COLOR = Color.lightGray;

    public static final Color BAR_SELECTION_COLOR = new Color(255, 0, 0, 120);
    public static final Color BAR_SELECTION_COLOR_ROLLOVER = new Color(255, 0, 255, 120);
    public static final Color BAR_SELECTION_COLOR_DRAG = new Color(0, 0, 255, 120);

    public static final int HEIGHT = (int) BAR_HEIGHT;
    private RangeSliderModel model;
    private State state;
    private Point startPoint;
    private RangeSliderModel tempModel;
    private boolean isOverBar;

    private Color basicBarColor = BAR_COLOR;

    /**
     * Shows gaps in the slider
     */
    private boolean showGaps;

    private enum State {

        Initial,
        DragBar,
        DragFirstPosition,
        DragSecondPosition
    }

    public RangeSlider() {
        state = State.Initial;
        this.addMouseMotionListener(this);
        this.addMouseListener(this);
    }

    public Color getBasicBarColor() {
        return basicBarColor;
    }

    public void setBasicBarColor(Color basicBarColor) {
        this.basicBarColor = basicBarColor;
    }

    public void setModel(RangeSliderModel newModel) {
        if (model != null) {
            model.getChangedEvent().removeListener(this);
            model.getColorChangedEvent().removeListener(this);
        }
        if (newModel != null) {
            newModel.getChangedEvent().addListener(this);
            newModel.getColorChangedEvent().addListener(this);
        }
        this.model = newModel;
        update();
    }

    private RangeSliderModel getPaintingModel() {
        if (tempModel != null) {
            return tempModel;
        }
        return model;
    }

    /**
     * Returns the preferred size of the viewport for a view component. For example, the preferred
     * size of a <code>JList</code> component is the size required to accommodate all of the cells
     * in its list. However, the value of <code>preferredScrollableViewportSize</code> is the size
     * required for <code>JList.getVisibleRowCount</code> rows. A component without any properties
     * that would affect the viewport size should just return <code>getPreferredSize</code> here.
     *
     * @return the preferredSize of a <code>JViewport</code> whose view is this
     * <code>Scrollable</code>
     * @see JViewport#getPreferredSize
     */
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        if (orientation == SwingConstants.VERTICAL) {
            return 1;
        }

        return (int) (BAR_CIRCLE_SIZE + BAR_CIRCLE_CONNECTOR_SIZE);
    }

    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height / 2 : visibleRect.width / 2;
    }

    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    public boolean getScrollableTracksViewportHeight() {
        return true;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = HEIGHT;
        d.width = Math.max(d.width, (int) (2 * BAR_CIRCLE_CONNECTOR_SIZE + getPaintingModel().getPositionCount(isShowGaps()) * (BAR_CIRCLE_SIZE + BAR_CIRCLE_CONNECTOR_SIZE)));
        return d;
    }

    private int origStartX = -1;
    private int origEndX = -1;

    @Override
    public void changed(RangeSliderModel source) {
        refresh();
    }

    /**
     * Always replans refresh to EDT.
     */
    private void refresh() {
        if (SwingUtilities.isEventDispatchThread()) {
            refreshInAWT();
        } else {
            SwingUtilities.invokeLater(this::refreshInAWT);
        }
    }

    private void refreshInAWT() {
        revalidate();

        float barStartY = getBarStartY();
        int circleCenterY = (int) (barStartY + BAR_HEIGHT / 2);
        int startX = (int) getStartXPosition(model.getFirstPosition());
        int endX = (int) getEndXPosition(model.getSecondPosition());
        if (startX != origStartX || endX != origEndX) {
            origStartX = startX;
            origEndX = endX;
            Rectangle r = new Rectangle(startX, circleCenterY, endX - startX, 1);
            scrollRectToVisible(r);
        }
        update();
    }

    private void update() {
        this.repaint();
    }

    private float getSlotXPosition(int index) {
        return getXPosition(showGaps ? getPaintingModel().getSlot(index) : index);
    }

    private float getXPosition(int index) {
        assert index >= 0 && index < getPaintingModel().getPositionCount(true);
        return getXOffset() * (index + 1);
    }

    private int getSlots() {
        RangeSliderModel m = getPaintingModel();
        return m == null ? 0 : m.getPositionCount(showGaps);
    }

    private float getXOffset() {
        int size = getSlots();
        float width = (float) getPreferredSize().width;
        return (width / (size + 1));
    }

    private float getEndXPosition(int index) {
        return getSlotXPosition(index) + getXOffset() / 2;
    }

    private float getStartXPosition(int index) {
        return getSlotXPosition(index) - getXOffset() / 2;
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();

        g2.setColor(BACKGROUND_COLOR);
        g2.fill(new Rectangle2D.Float(0, 0, width, height));

        // Nothing to paint?
        if (getSlots() == 0) {
            return;
        }

        int firstPos = getPaintingModel().getFirstPosition();
        int secondPos = getPaintingModel().getSecondPosition();
        paintSelected(g2, firstPos, secondPos);
        paintBar(g2);

    }

    private float getBarStartY() {
        return getHeight() / 2 - BAR_HEIGHT / 2;
    }

    private Color lighten(Color color) {
        if (color == null || Color.black.equals(basicBarColor)) {
            return color;
        }

        float amount = 0.5f;

        int red = (int) ((color.getRed() * (1 - amount) / 255 + amount) * 255);
        int green = (int) ((color.getGreen() * (1 - amount) / 255 + amount) * 255);
        int blue = (int) ((color.getBlue() * (1 - amount) / 255 + amount) * 255);


        int alpha = color.getAlpha();

        return new Color(red, green, blue, alpha);
    }

    private static TexturePaint makeHatch(Color one, Color two) {
        int size = 4;
        BufferedImage hatchImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = (Graphics2D) hatchImage.getGraphics();
        g2.setColor(one);
        g2.fillRect(0, 0, size, size);
        g2.setColor(two);
        g2.drawLine(0, 0, size, size);
        g2.dispose();
        return new TexturePaint(hatchImage, new Rectangle2D.Double(0, 0, size, size));
    }

    private void paintBar(Graphics2D g) {
        List<String> list = getPaintingModel().getPositions();
        float barStartY = getBarStartY();

        g.setColor(basicBarColor);
        g.fill(new Rectangle2D.Float(getSlotXPosition(0), barStartY + BAR_HEIGHT / 2 - BAR_THICKNESS / 2,
                getSlotXPosition(list.size() - 1) - getSlotXPosition(0), BAR_THICKNESS));

        float circleCenterY = barStartY + BAR_HEIGHT / 2;
        List<Color> colors = getPaintingModel().getColors();
        List<Color> hatches = getPaintingModel().getHatchColors();
        if (list.size() != colors.size()) {
            // just a safeguard, a repaing request should be already underway.
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            float curX = getSlotXPosition(i);
            Color hatch = i < hatches.size() ? lighten(hatches.get(i)) : null;
            Color color = lighten(colors.get(i));
            if (hatch == null) {
                g.setColor(color);
            } else {
                g.setPaint(makeHatch(color, hatch));
            }
            Shape e = new Ellipse2D.Float(curX - BAR_CIRCLE_SIZE / 2, circleCenterY - BAR_CIRCLE_SIZE / 2, BAR_CIRCLE_SIZE, BAR_CIRCLE_SIZE);
            g.fill(e);
            g.setColor(basicBarColor);
            g.draw(e);

            String curS = list.get(i);
            if (curS != null && curS.length() > 0) {
                float startX = getStartXPosition(i);
                float endX = getEndXPosition(i);
                FontMetrics metrics = g.getFontMetrics();
                Rectangle bounds = metrics.getStringBounds(curS, g).getBounds();
                if (bounds.width < endX - startX && bounds.height < barStartY) {
                    g.setColor(basicBarColor);
                    g.drawString(curS, startX + (endX - startX) / 2 - bounds.width / 2, barStartY / 2 + bounds.height / 2);
                }
            }
        }

    }

    private void paintSelected(Graphics2D g, int start, int end) {

        float startX = getStartXPosition(start);
        float endX = getEndXPosition(end);
        float barStartY = getBarStartY();
        float barSelectionEndingStartY = barStartY + BAR_HEIGHT / 2 - BAR_SELECTION_ENDING_HEIGHT / 2;
        paintSelectedEnding(g, startX, barSelectionEndingStartY);
        paintSelectedEnding(g, endX, barSelectionEndingStartY);

        g.setColor(BAR_SELECTION_COLOR);
        if (state == State.DragBar) {
            g.setColor(BAR_SELECTION_COLOR_DRAG);
        } else if (isOverBar) {
            g.setColor(BAR_SELECTION_COLOR_ROLLOVER);
        }
        g.fill(new Rectangle2D.Float(startX, barStartY + BAR_HEIGHT / 2 - BAR_SELECTION_HEIGHT / 2, endX - startX, BAR_SELECTION_HEIGHT));
    }

    private void paintSelectedEnding(Graphics2D g, float x, float y) {
        g.setColor(basicBarColor);
        g.fill(new Rectangle2D.Float(x - BAR_THICKNESS / 2, y, BAR_THICKNESS, BAR_SELECTION_ENDING_HEIGHT));
    }

    private boolean isOverSecondPosition(Point p) {
        if (p.y >= getBarStartY()) {
            float destX = getEndXPosition(getPaintingModel().getSecondPosition());
            float off = Math.abs(destX - p.x);
            return off <= MOUSE_ENDING_OFFSET;
        }
        return false;
    }

    private boolean isOverFirstPosition(Point p) {
        if (p.y >= getBarStartY()) {
            float destX = getStartXPosition(getPaintingModel().getFirstPosition());
            float off = Math.abs(destX - p.x);
            return off <= MOUSE_ENDING_OFFSET;
        }
        return false;
    }

    private boolean isOverSelection(Point p) {
        if (p.y >= getBarStartY() && !isOverFirstPosition(p) && !isOverSecondPosition(p)) {
            return p.x > getStartXPosition(getPaintingModel().getFirstPosition()) && p.x < getEndXPosition(getPaintingModel().getSecondPosition());
        }
        return false;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        Rectangle r = new Rectangle(e.getX(), e.getY(), 1, 1);
        scrollRectToVisible(r);

        if (state == State.DragBar) {
            float firstX = this.getStartXPosition(model.getFirstPosition());
            float newFirstX = firstX + e.getPoint().x - startPoint.x;
            int newIndex = getIndexFromPosition(newFirstX) + 1;
            int delta = model.getSecondPosition() - model.getFirstPosition();
            if (newIndex + delta >= model.getPositions().size()) {
                newIndex = model.getPositions().size() - delta - 1;
            }
            int secondPosition = newIndex + delta;
            tempModel.setPositions(newIndex, secondPosition);
            update();
        } else if (state == State.DragFirstPosition) {
            int firstPosition = getIndexFromPosition(e.getPoint().x) + 1;
            int secondPosition = model.getSecondPosition();
            if (firstPosition > secondPosition) {
                firstPosition = secondPosition;
            }
            tempModel.setPositions(firstPosition, secondPosition);
            update();
        } else if (state == State.DragSecondPosition) {
            int firstPosition = model.getFirstPosition();
            int secondPosition = getIndexFromPosition(e.getPoint().x);
            if (secondPosition < firstPosition) {
                secondPosition = firstPosition;
            }
            tempModel.setPositions(firstPosition, secondPosition);
            update();
        }
    }

    private int getIndexFromPosition(float x) {
        if (x < getSlotXPosition(0)) {
            return -1;
        }
        int maxP = getPaintingModel().getPositionCount(false) - 1;
        for (int i = 0; i < maxP; i++) {
            float startX = getSlotXPosition(i);
            float endX = getSlotXPosition(i + 1);
            if (x >= startX && x <= endX) {
                return i;
            }
        }
        return maxP;
    }

    private int getCircleIndexFromPosition(int x) {
        int result = 0;
        for (int i = 1; i < getPaintingModel().getPositions().size(); i++) {
            if (x > getStartXPosition(i)) {
                result = i;
            }
        }
        return result;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        isOverBar = false;
        if (model == null) {
            return;
        }

        Point p = e.getPoint();
        if (isOverFirstPosition(p) || isOverSecondPosition(p)) {
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        } else if (isOverSelection(p)) {
            isOverBar = true;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            this.setCursor(Cursor.getDefaultCursor());
        }
        if (p.y >= getBarStartY()) {
            int pos = getIndexFromPosition(e.getPoint().x);
            if (pos > -1) {
                setToolTipText(getPaintingModel().getPositions().get(pos));
            } else {
                setToolTipText(null);
            }
        }
        repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() > 1) {
            // Double click
            int index = getCircleIndexFromPosition(e.getPoint().x);
            model.setPositions(index, index);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (model == null) {
            return;
        }

        Point p = e.getPoint();
        if (isOverFirstPosition(p)) {
            state = State.DragFirstPosition;
        } else if (isOverSecondPosition(p)) {
            state = State.DragSecondPosition;
        } else if (isOverSelection(p)) {
            state = State.DragBar;
        } else {
            return;
        }

        startPoint = e.getPoint();
        tempModel = model.copy();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (model == null || tempModel == null) {
            return;
        }
        state = State.Initial;
        model.setPositions(tempModel.getFirstPosition(), tempModel.getSecondPosition());
        tempModel = null;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
        isOverBar = false;
        repaint();
    }

    public boolean isShowGaps() {
        return showGaps;
    }

    public void setShowGaps(boolean showGaps) {
        this.showGaps = showGaps;
    }

    public RangeSliderModel getModel() {
        return model;
    }
}
