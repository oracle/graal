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

package org.graalvm.visualizer.source.impl.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.ui.Trackable;
import org.netbeans.api.actions.Openable;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.editor.document.EditorDocumentUtils;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.text.Line;
import org.openide.util.NbBundle;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.openide.text.Annotatable;
import org.openide.text.Annotation;
import org.openide.text.NbDocument;
import org.openide.util.Mutex;
import org.openide.util.Task;
import org.openide.util.TaskListener;

/**
 *
 */
public final class LocationOpener implements Openable, Trackable {
    private final Location location;

    public LocationOpener(Location location) {
        this.location = location;
    }

    @NbBundle.Messages({
            "ERR_LineNotFound=Referenced line does not exist"
    })
    @Override
    public void open() {
        openOrView(true);
    }

    private void openOrView(boolean focus) {
        FileObject toOpen = location.getOriginFile();
        if (toOpen == null) {
            return;
        }
        EditorCookie cake = toOpen.getLookup().lookup(EditorCookie.class);
        if (cake == null) {
            return;
        }

        Task task = cake.prepareDocument();
        class WhenShowing implements TaskListener, Runnable {
            @Override
            public void taskFinished(Task task) {
                task.removeTaskListener(this);
                Mutex.EVENT.postReadRequest(this);
            }

            @Override
            public void run() {
                final StyledDocument doc = cake.getDocument();
                if (doc == null) {
                    return;
                }
                List<Annotatable> select = findLinesOrParts(cake.getLineSet(), doc, location.getLine() - 1, location.getOffsetsOrNull());
                if (select.size() == 1 && select.get(0) instanceof Line) {
                    Line line = (Line) select.get(0);
                    line.show(Line.ShowOpenType.REUSE, focus ? Line.ShowVisibilityType.FRONT : Line.ShowVisibilityType.FRONT);
                    CurrentNodeAnnotation.highlight(select);
                } else if (select.size() >= 1 && select.get(0) instanceof Line.Part) {
                    Line.Part part = (Line.Part) select.get(0);
                    part.getLine().show(Line.ShowOpenType.REUSE, focus ? Line.ShowVisibilityType.FRONT : Line.ShowVisibilityType.FRONT, part.getColumn());
                    CurrentNodeAnnotation.highlight(select);
                } else {
                    // neither line nor offsets
                    cake.open();
                    StatusDisplayer.getDefault().setStatusText(Bundle.ERR_LineNotFound());
                }
            }

        }
        WhenShowing select = new WhenShowing();
        task.addTaskListener(select);
    }

    @Override
    public void viewIfOpened() {
        FileObject toOpen;
        toOpen = location.getOriginFile();
        if (toOpen == null) {
            return;
        }
        for (JTextComponent comp : EditorRegistry.componentList()) {
            Document doc = comp.getDocument();
            FileObject fo = EditorDocumentUtils.getFileObject(doc);
            if (toOpen == fo) {
                view();
            }
        }
    }

    @Override
    public void view() {
        openOrView(false);
    }

    /**
     * Find {@link Line} or {@link Line.Part} to select.
     *
     * @param lines lines of the document to search in
     * @param doc document to search in
     * @param lineNumber line number (counting from zero} or value less then zero when there is no line info
     * @param offsetsOrNull {@code int[] { startOffset, endOffset }} or {@code null}
     * @return found {@link Line} or {@link Line.Part} or {@code null}
     */
    static List<Annotatable> findLinesOrParts(Line.Set lines, StyledDocument doc, int lineNumber, int[] offsetsOrNull) {
        assert doc != null;

        Line exactLine = findLine(lines, lineNumber);
        Line startLine = null;
        Line endLine = null;
        if (offsetsOrNull != null) {
            int startOffsetLineNumber = NbDocument.findLineNumber(doc, offsetsOrNull[0]);
            startLine = findLine(lines, startOffsetLineNumber);
            int endOffsetLineNumber = NbDocument.findLineNumber(doc, offsetsOrNull[1]);
            endLine = findLine(lines, endOffsetLineNumber);
        }

        if (startLine == null || (exactLine != null && startLine != exactLine)) {
            // prefer exact line
            return Collections.singletonList(exactLine);
        } else {
            // use offset line
            int startLineOffset = NbDocument.findLineOffset(doc, startLine.getLineNumber());
            int startColumn = offsetsOrNull[0] - startLineOffset;
            if (startLine == endLine || endLine == null) {
                int len = offsetsOrNull[1] - offsetsOrNull[0];
                Line.Part linePart = startLine.createPart(startColumn, len);
                return Collections.singletonList(linePart);
            } else {
                var multiple = new ArrayList<Annotatable>();
                Line.Part firstLinePart = startLine.createPart(startColumn, startLine.getText().length() - startColumn);
                multiple.add(firstLinePart);
                for (var between = startLine.getLineNumber() + 1; between < endLine.getLineNumber(); between++) {
                    var lineBetween = findLine(lines, between);
                    if (lineBetween != null) {
                        multiple.add(lineBetween);
                    }
                }
                int endLineOffset = NbDocument.findLineOffset(doc, endLine.getLineNumber());
                int endColumn = offsetsOrNull[1] - endLineOffset;
                Line.Part lastLinePart = endLine.createPart(0, endColumn);
                multiple.add(lastLinePart);
                return multiple;
            }
        }
    }

    private static Line findLine(Line.Set lines, int line) {
        try {
            return lines.getOriginal(line);
        } catch (IndexOutOfBoundsException ex) {
            // expected, the source has changed
            // just open the file
            return null;
        }
    }

    @NbBundle.Messages({
        "CTL_CurrentNode=Current node"
    })
    private static final class CurrentNodeAnnotation extends Annotation {
        private static List<CurrentNodeAnnotation> previous = Collections.emptyList();

        private CurrentNodeAnnotation() {
        }

        private static void highlight(List<Annotatable> select) {
            List<CurrentNodeAnnotation> newOnes = new ArrayList<>();
            for (var l : select) {
                var a = new CurrentNodeAnnotation();
                newOnes.add(a);
                a.attach(l);
            }
            List<CurrentNodeAnnotation> toClear;
            synchronized (CurrentNodeAnnotation.class) {
                toClear = previous;
                previous = newOnes;
            }
            for (var a : toClear) {
                a.detach();
            }
        }

        @Override
        public String getAnnotationType() {
            return "NodePositionOffset";
        }

        @Override
        public String getShortDescription() {
            return Bundle.CTL_CurrentNode();
        }
    }
}
