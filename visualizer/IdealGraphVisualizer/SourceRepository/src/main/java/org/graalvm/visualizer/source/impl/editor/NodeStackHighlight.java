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

package org.graalvm.visualizer.source.impl.editor;

import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.NodeLocationContext;
import org.graalvm.visualizer.source.NodeLocationEvent;
import org.graalvm.visualizer.source.NodeLocationListener;
import org.netbeans.api.editor.document.EditorDocumentUtils;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.spi.editor.highlighting.HighlightsChangeEvent;
import org.netbeans.spi.editor.highlighting.HighlightsChangeListener;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.AbstractHighlightsContainer;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Highlights the source code based on the covered locations
 */
public class NodeStackHighlight extends AbstractHighlightsContainer
        implements HighlightsChangeListener, NodeLocationListener {

    private static final String HIGHLIGHT_STACK_CURRENT = "highlight-compiled-lines"; // NOI18N
    private static final String HIGHLIGHT_NESTED_LINES = "highlight-nested-lines"; // NOI18N

    private static final String LAYER_ID = NodeStackHighlight.class.getName();

    private final OffsetsBag bag;
    private final Document document;
    private final NodeLocationContext locationContext;
    private final MimePath mimePath;

    private static RequestProcessor RP = new RequestProcessor(NodeStackHighlight.class);

    public NodeStackHighlight(JTextComponent component) {
        this.document = component.getDocument();
        this.bag = new OffsetsBag(document);
        this.locationContext = Lookup.getDefault().lookup(NodeLocationContext.class);

        String mimeType = DocumentUtilities.getMimeType(document);
        this.mimePath = mimeType == null ? MimePath.EMPTY : MimePath.parse(mimeType);

        bag.addHighlightsChangeListener(this);
        this.locationContext.addNodeLocationListener(WeakListeners.create(NodeLocationListener.class, this, this.locationContext));
        refresh();
    }

    @Override
    public HighlightsSequence getHighlights(int startOffset, int endOffset) {
        return bag.getHighlights(startOffset, endOffset);
    }

    @Override
    public void highlightChanged(HighlightsChangeEvent event) {
        fireHighlightsChange(event.getStartOffset(), event.getEndOffset());
    }

    @Override
    public void selectedNodeChanged(NodeLocationEvent evt) {
    }

    @Override
    public void nodesChanged(NodeLocationEvent evt) {
    }

    @Override
    public void locationsResolved(NodeLocationEvent evt) {
        refresh();
    }

    @Override
    public void selectedLocationChanged(NodeLocationEvent evt) {
        FileObject file = EditorDocumentUtils.getFileObject(document);
        if (file == null) {
            return;
        }
        Location loc = evt.getContext().getSelectedLocation();
        if (loc == null || !file.equals(loc.getOriginFile())) {
            return;
        }

        refresh();
    }

    private void refresh() {
        RP.post(() -> document.render(this::doRefresh));
    }

    private void doRefresh() {
        FileObject file = EditorDocumentUtils.getFileObject(document);
        if (file == null) {
            return;
        }
        LineDocument ld = LineDocumentUtils.as(document, LineDocument.class);
        if (ld == null) {
            return;
        }
        Location current = locationContext.getSelectedLocation();
        if (current == null) {
            return;
        }
        FontColorSettings fcs = MimeLookup.getLookup(mimePath).lookup(FontColorSettings.class);
        AttributeSet attribCurrent = fcs.getFontColors(HIGHLIGHT_STACK_CURRENT);
        AttributeSet attribNested = fcs.getFontColors(HIGHLIGHT_NESTED_LINES);
        OffsetsBag newBag = new OffsetsBag(document);
        List<Location> locs = new ArrayList<>(locationContext.getFileLocations(file, false));
        Set<Location> locs2 = new HashSet<>(locationContext.getFileLocations(file, true));
        Collections.sort(locs, (a, b) -> a.getLine() - b.getLine());
        Element paragraphParent = ld.getParagraphElement(0).getParentElement();
        int count = paragraphParent.getElementCount();

        for (Location l : locs) {
            int line = l.getLine();
            if (line < 1 || line >= count) {
                continue;
            }
            final Element lineEl = paragraphParent.getElement(line - 1);
            final AttributeSet as = locs2.contains(l) ? attribCurrent : attribNested;
            int s = lineEl.getStartOffset();
            int e = lineEl.getEndOffset();
            newBag.addHighlight(s, e, as);
        }
        this.bag.setHighlights(newBag);
    }

    @MimeRegistration(mimeType = "", service = HighlightsLayerFactory.class)
    public static final class Factory implements HighlightsLayerFactory {
        @Override
        public HighlightsLayer[] createLayers(Context context) {
            return new HighlightsLayer[]{
                    HighlightsLayer.create(
                            NodeStackHighlight.LAYER_ID,
                            ZOrder.BOTTOM_RACK,
                            true,
                            new NodeStackHighlight(context.getComponent())
                    )
            };
        }
    }
}
