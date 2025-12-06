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
package at.ssw.visualizer.interval;

import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.model.cfg.IRInstruction;
import at.ssw.visualizer.model.interval.ChildInterval;
import at.ssw.visualizer.model.interval.Interval;
import at.ssw.visualizer.model.interval.IntervalList;
import at.ssw.visualizer.model.interval.Range;
import at.ssw.visualizer.model.interval.UsePosition;
import at.ssw.visualizer.texteditor.model.HoverParser;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * A Viewport showing an interval chart. Intended to be used in conjunction
 * with a JScrollPane.
 *
 * @author Christian Wimmer
 * @author Bernhard Stiftner
 */
public class IntervalCanvas extends JViewport {

    class ViewData {

        /** number of instructions/intervals hidden on left/top */
        public int offsetCol;
        public int offsetRow;
        /** number of instructions/intervals visible */
        public int sizeCol;
        public int sizeRow;
        /** total number of instructions/intervals */
        public int totalRow;
        public int totalCol;
        public int fontHeight;
        public int fontAscent;
        public int mouseX;
        public int mouseY;
        public int selectedCol;
        public int selectedRow;
        /** offset in pixels where first visible col/row starts */
        public int offsetX;
        public int offsetY;
        /** size in pixels of viewable area */
        public int sizeX;
        public int sizeY;
        public Interval selectedInterval;
        public ChildInterval selectedChild;
        public BasicBlock selectedBlock;
        public IRInstruction selectedOperation;

        public int colToX(int col) {
            return (col - offsetCol) * viewSettings.colWidth + offsetX;
        }

        public int rowToY(int row) {
            return (row - offsetRow) * viewSettings.rowHeight + offsetY;
        }
    }

    /**
     * Just a dummy component which is used as the view of this viewport.
     * It's just here to perform some layout calculations; it never gets painted.
     */
    class PlaceHolder extends JComponent implements Scrollable {

        public PlaceHolder() {
            setEnabled(false);
        }

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            switch (orientation) {
                case SwingConstants.HORIZONTAL:
                    return 2 * viewSettings.colWidth;
                case SwingConstants.VERTICAL:
                    return viewSettings.rowHeight;
            }
            throw new RuntimeException("illegal orientation");
        }

        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            switch (orientation) {
                case SwingConstants.HORIZONTAL:
                    return viewSettings.colWidth * viewData.sizeCol * 3 / 8;
                case SwingConstants.VERTICAL:
                    return viewSettings.rowHeight * viewData.sizeRow * 3 / 4;
            }
            throw new RuntimeException("illegal orientation");
        }

        public boolean getScrollableTracksViewportWidth() {
            return false;
        }

        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
    private IntervalEditorTopComponent editor;
    private IntervalList intervals;
    private ViewSettings viewSettings;
    private ViewData viewData = new ViewData();
    /** inserted as view, just used for layout purposes */
    private PlaceHolder placeholder = new PlaceHolder();
    private MouseMotionListener mouseMotionListener = new MouseMotionAdapter() {

        @Override
        public void mouseMoved(MouseEvent e) {
            calcSelection(e.getX(), e.getY());
        }
    };
    private MouseListener mouseListener = new MouseAdapter() {

        @Override
        public void mouseEntered(MouseEvent e) {
            calcSelection(e.getX(), e.getY());
        }

        @Override
        public void mouseExited(MouseEvent e) {
            calcSelection(-1, -1);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            editor.updateCanvasSelection();
        }
    };
    private ComponentListener componentListener = new ComponentAdapter() {

        @Override
        public void componentResized(ComponentEvent e) {
            calcViewData();
        }

        @Override
        public void componentShown(ComponentEvent e) {
            calcViewData();
        }
    };

    public IntervalCanvas(IntervalEditorTopComponent editor, ViewSettings viewSettings, IntervalList intervals) {
        this.editor = editor;
        this.intervals = intervals;
        this.viewSettings = viewSettings;

        setView(placeholder);
        setOpaque(true);
        setBackground(UIManager.getColor("TextPane.background"));
        setFocusable(true);

        addMouseListener(mouseListener);
        addMouseMotionListener(mouseMotionListener);
        addComponentListener(componentListener);
    }

    public IntervalList getIntervals() {
        return intervals;
    }

    public BasicBlock getSelectedBlock() {
        return viewData.selectedBlock;
    }

    public ChildInterval getSelectedInterval() {
        return viewData.selectedChild;
    }

    public void ensureColumnVisible(int column) {
        if (column < viewData.offsetCol) {
            setViewPosition(new Point(column * viewSettings.colWidth, getViewPosition().y));
        } else if (column >= viewData.offsetCol + viewData.sizeCol - 4) {
            setViewPosition(new Point((column - viewData.sizeCol + 4) * viewSettings.colWidth, getViewPosition().y));
        }
    }

    public void ensureRowVisible(int row) {
        if (row < viewData.offsetRow) {
            setViewPosition(new Point(getViewPosition().x, row * viewSettings.rowHeight));
        } else if (row >= viewData.offsetRow + viewData.sizeRow - 1) {
            setViewPosition(new Point(getViewPosition().x, (row - viewData.sizeRow + 1) * viewSettings.rowHeight));
        }
    }

    public void calcViewData() {

        Graphics gc = getGraphics();
        if (gc == null) {
            return;
        }

        Dimension ca = getExtentSize();

        viewData.totalRow = intervals.getIntervals().size();
        viewData.totalCol = intervals.getNumLIROperations();

        gc.setFont(viewSettings.textFont);
        FontMetrics fm = gc.getFontMetrics();
        viewData.fontHeight = fm.getHeight();
        viewData.fontAscent = fm.getAscent();
        viewData.offsetX = fm.stringWidth("v" + viewData.totalRow + "|a") + 15;

        viewData.offsetY = viewData.fontHeight * 2;

        placeholder.setPreferredSize(new Dimension(viewData.offsetX + (viewData.totalCol + 2) * viewSettings.colWidth + 2 + viewSettings.thickLineWidth, viewData.offsetY + (viewData.totalRow + 1) * viewSettings.rowHeight + 2));
        placeholder.setSize(placeholder.getPreferredSize());

        viewData.offsetCol = getViewPosition().x / viewSettings.colWidth / 2 * 2;
        viewData.offsetRow = getViewPosition().y / viewSettings.rowHeight;

        viewData.sizeCol = Math.min((ca.width - viewData.offsetX - 3) / viewSettings.colWidth / 2 * 2, viewData.totalCol - viewData.offsetCol);
        viewData.sizeRow = Math.min((ca.height - viewData.offsetY - 3) / viewSettings.rowHeight, viewData.totalRow - viewData.offsetRow);

        viewData.sizeX = viewData.sizeCol * viewSettings.colWidth;
        viewData.sizeY = viewData.sizeRow * viewSettings.rowHeight;

        calcSelection();
        repaint();
    }

    public void calcSelection(int mouseX, int mouseY) {
        viewData.mouseX = mouseX;
        viewData.mouseY = mouseY;
        calcSelection();
    }

    public void calcSelection() {
        int newCol;
        int newRow;
        if (viewData.mouseX != -1 && viewData.mouseY != -1) {
            newCol = (viewData.mouseX - viewData.offsetX + 2 + viewSettings.colWidth * 2) / viewSettings.colWidth / 2 * 2 + viewData.offsetCol - 2;
            newRow = (viewData.mouseY - viewData.offsetY + 2 + viewSettings.rowHeight) / viewSettings.rowHeight + viewData.offsetRow - 1;
        } else {
            newCol = -1;
            newRow = -1;
        }

        if (newCol < viewData.offsetCol || newCol >= viewData.offsetCol + viewData.sizeCol) {
            newCol = -1;
        }
        if (newRow < viewData.offsetRow || newRow >= viewData.offsetRow + viewData.sizeRow) {
            newRow = -1;
        }

        if (viewData.selectedCol != newCol || viewData.selectedRow != newRow) {
            viewData.selectedCol = newCol;
            viewData.selectedRow = newRow;

            if (newRow != -1) {
                viewData.selectedInterval = intervals.getIntervals().get(newRow);
                viewData.selectedChild = getChildByLirId(viewData.selectedInterval, newCol);
            } else {
                viewData.selectedInterval = null;
                viewData.selectedChild = null;
            }

            if (newCol != -1) {
                viewData.selectedBlock = getBlockByLirId(intervals.getControlFlowGraph(), newCol);
                viewData.selectedOperation = getOperationByLirId(viewData.selectedBlock, newCol);
            } else {
                viewData.selectedBlock = null;
                viewData.selectedOperation = null;
            }

            repaint();
            updateStatusLine();
        }
    }

    private BasicBlock getBlockByLirId(ControlFlowGraph cfg, int lirId) {
        // TODO could do binary search
        for (BasicBlock basicBlock : cfg.getBasicBlocks()) {
            if (basicBlock.getFirstLirId() <= lirId && lirId <= basicBlock.getLastLirId()) {
                return basicBlock;
            }
        }
        return null;
    }

    private IRInstruction getOperationByLirId(BasicBlock block, int lirId) {
        if (block == null) {
            return null;
        }
        // TODO could do binary search
        for (IRInstruction operation : block.getLirOperations()) {
            try {
                if (Integer.parseInt(operation.getValue(IRInstruction.LIR_NUMBER)) == lirId) {
                    return operation;
                }
            } catch (NumberFormatException ex) {
                // Silently ignore wrong numbers.
            }
        }
        return null;
    }

    public ChildInterval getChildByLirId(Interval interval, int lirId) {
        if (interval == null) {
            return null;
        }
        // TODO could do binary search
        List<ChildInterval> children = interval.getChildren();
        for (ChildInterval child : children) {
            for (Range range : child.getRanges()) {
                if (range.getFrom() <= lirId && range.getTo() >= lirId) {
                    return child;
                }
            }
        }
        return children.get(0);
    }

    private void updateStatusLine() {
        String intervalText = "";
        String instructionText = "";
        String blockText = "";

        ChildInterval child = viewData.selectedChild;
        if (child != null) {
            intervalText = child.getRegNum() + "  " + child.getType() + "  " + child.getOperand();
        }

        IRInstruction operation = viewData.selectedOperation;
        if (operation != null) {
            for (String name : operation.getNames()) {
                instructionText = instructionText + HoverParser.firstLine(operation.getValue(name)) + "  ";
            }
        }

        BasicBlock block = viewData.selectedBlock;
        if (block != null) {
            if (block.getLoopDepth() > 0) {
                blockText = block.getName() + " (loop " + block.getLoopIndex() + " depth " + block.getLoopDepth() + ")";
            } else {
                blockText = block.getName();
            }
        }

        editor.setIntervalStatusText(intervalText);
        editor.setInstructionStatusText(instructionText);
        editor.setBlockStatusText(blockText);
    }

    private int gridStart(int start, int grid) {
        return grid - 1 - (start + grid - 1) % grid;
    }

    private void drawXGrid(Graphics gc, int grid, Color color) {
        gc.setColor(color);
        int y1 = viewData.offsetY;
        int y2 = viewData.offsetY + viewData.sizeY;

        for (int i = gridStart(viewData.offsetCol, grid); i < viewData.sizeCol; i += grid) {
            int x = viewData.offsetX + i * viewSettings.colWidth;
            gc.drawLine(x, y1, x, y2);
        }
    }

    private void drawXText(Graphics gc, int grid, Color color) {
        gc.setColor(color);
        int y = 0;

        for (int i = gridStart(viewData.offsetCol, grid); i < viewData.sizeCol; i += grid) {
            int x = viewData.offsetX + i * viewSettings.colWidth + viewSettings.thickLineWidth + 2;
            gc.drawString(String.valueOf(viewData.offsetCol + i), x, viewData.fontAscent + y);
        }
    }

    private void drawYGrid(Graphics gc, int grid, Color color) {
        gc.setColor(color);
        int x1 = viewData.offsetX;
        int x2 = viewData.offsetX + viewData.sizeX;

        for (int i = gridStart(viewData.offsetRow, grid); i < viewData.sizeRow; i += grid) {
            int y = viewData.offsetY + i * viewSettings.rowHeight;
            gc.drawLine(x1, y, x2, y);
        }
    }

    private void drawYText(Graphics gc, int grid, Color color) {
        gc.setColor(color);
        int x = 5;

        for (int i = gridStart(viewData.offsetRow, grid); i < viewData.sizeRow; i += grid) {
            Interval interval = intervals.getIntervals().get(i + viewData.offsetRow);
            if (interval != null) {
                int y = viewData.offsetY + i * viewSettings.rowHeight;
                gc.drawString(String.valueOf(interval.getRegNum()), x, viewData.fontAscent + y);
            }
        }
    }

    private void drawBorder(Graphics gc) {
        gc.setColor(viewSettings.darkGridColor);
        gc.drawRect(viewData.offsetX, viewData.offsetY, viewData.sizeX, viewData.sizeY);
    }

    private void drawBlocks(Graphics gc) {
        int y1 = viewData.fontHeight;
        int y2 = viewData.offsetY + viewData.sizeY;

        for (BasicBlock basicBlock : intervals.getControlFlowGraph().getBasicBlocks()) {
            if (basicBlock.getFirstLirId() >= viewData.offsetCol && basicBlock.getFirstLirId() <= viewData.offsetCol + viewData.sizeCol) {
                int x = viewData.colToX(basicBlock.getFirstLirId());
                gc.setColor(viewSettings.darkGridColor);
                gc.fillRect(x, y1, viewSettings.thickLineWidth, y2 - y1);

                if (basicBlock.getFirstLirId() < viewData.offsetCol + viewData.sizeCol) {
                    String text = basicBlock.getName();
                    if (basicBlock.getLoopDepth() > 0) {
                        text += " (" + basicBlock.getLoopDepth() + ")";
                    }
                    gc.setColor(getBackground());
                    Rectangle2D stringBounds = gc.getFontMetrics().getStringBounds(text, gc);
                    gc.fillRect(x + viewSettings.thickLineWidth + 2, y1, (int) stringBounds.getWidth(), (int) stringBounds.getHeight());
                    gc.setColor(viewSettings.textColor);
                    gc.drawString(text, x + viewSettings.thickLineWidth + 2, viewData.fontAscent + y1);
                }
            }
        }

        gc.setColor(viewSettings.darkGridColor);
        if (intervals.getNumLIROperations() <= viewData.offsetCol + viewData.sizeCol) {
            int x = viewData.colToX(intervals.getNumLIROperations());
            gc.fillRect(x, y1, viewSettings.thickLineWidth, y2 - y1);
        }
    }

    public void drawInterval(Graphics gc, ChildInterval interval, int barY, int barHeight, int textY) {
        gc.setColor(viewSettings.getIntervalColor(interval));

        int textX = -1;
        for (Range range : interval.getRanges()) {
            if (range.getTo() > viewData.offsetCol && range.getFrom() < viewData.offsetCol + viewData.sizeCol) {
                int x1 = Math.max(viewData.colToX(range.getFrom()) + viewSettings.thickLineWidth, viewData.offsetX);
                int x2 = Math.min(viewData.colToX(range.getTo()), viewData.offsetX + viewData.sizeX);
                gc.fillRect(x1, barY, x2 - x1, barHeight);

                if (textX == -1) {
                    textX = x1;
                }
            }
        }

        for (UsePosition usePosition : interval.getUsePositions()) {
            if (usePosition.getPosition() >= viewData.offsetCol && usePosition.getPosition() < viewData.offsetCol + viewData.sizeCol) {
                gc.setColor(viewSettings.getUsePosColor(usePosition));

                int x = viewData.colToX(usePosition.getPosition());
                gc.fillRect(x, barY, viewSettings.thickLineWidth, barHeight);
            }
        }

        gc.setColor(viewSettings.textColor);
        if (viewSettings.showIntervalText && textX != -1) {
            textX = Math.max(textX, viewData.offsetX + viewSettings.thickLineWidth);
            String text = String.valueOf(interval.getRegNum()) + " " + interval.getOperand();
            gc.drawString(text, textX + 1, viewData.fontAscent + textY);
        }
    }

    public void drawIntervals(Graphics gc) {
        gc.setColor(viewSettings.textColor);
        int barHeight = viewSettings.rowHeight - viewSettings.barSeparation * 2 - 1;

        for (int i = 0; i < viewData.sizeRow; i++) {
            Interval interval = intervals.getIntervals().get(i + viewData.offsetRow);
            int textY = viewData.offsetY + i * viewSettings.rowHeight;
            int barY = textY + 1 + viewSettings.barSeparation;

            for (ChildInterval child : interval.getChildren()) {
                drawInterval(gc, child, barY, barHeight, textY);
            }
        }
    }

    private void drawSelection(Graphics gc) {
        gc.setColor(Color.RED);
        Dimension ca = getExtentSize();

        if (viewData.selectedCol != -1) {
            int x = viewData.colToX(viewData.selectedCol);
            gc.fillRect(x, 0, 3, ca.height);
        }
        if (viewData.selectedRow != -1) {
            int y = viewData.rowToY(viewData.selectedRow);
            gc.fillRect(0, y - 1, ca.width, 3);
        }
    }

    public void drawAll(Graphics gc) {
        gc.setColor(getBackground());
        gc.fillRect(0, 0, getWidth(), getHeight());

        gc.setFont(viewSettings.textFont);

        if (viewSettings.lightGridX > 0) {
            drawXGrid(gc, viewSettings.lightGridX, viewSettings.lightGridColor);
        }
        if (viewSettings.lightGridY > 0) {
            drawYGrid(gc, viewSettings.lightGridY, viewSettings.lightGridColor);
        }
        if (viewSettings.darkGridX > 0) {
            drawXGrid(gc, viewSettings.darkGridX, viewSettings.darkGridColor);
        }
        if (viewSettings.darkGridY > 0) {
            drawYGrid(gc, viewSettings.darkGridY, viewSettings.darkGridColor);
        }
        if (viewSettings.textGridX > 0) {
            drawXText(gc, viewSettings.textGridX, viewSettings.textColor);
        }
        if (viewSettings.textGridY > 0) {
            drawYText(gc, viewSettings.textGridY, viewSettings.textColor);
        }
        drawBorder(gc);
        drawBlocks(gc);
        drawIntervals(gc);
        drawSelection(gc);
    }

    @Override
    public void paint(Graphics g) {
        g.clearRect(0, 0, getSize().width, getSize().height);
        drawAll(g);
    }

    @Override
    protected void fireStateChanged() {
        calcViewData();
        super.fireStateChanged();
    }
}
