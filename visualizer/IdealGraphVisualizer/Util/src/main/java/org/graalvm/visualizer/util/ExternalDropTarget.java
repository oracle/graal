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

import org.openide.util.Lookup;
import org.openide.windows.ExternalDropHandler;

import javax.swing.JComponent;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;

/**
 * Helper class that craetes {@link DropTarget} that accepts
 * files.
 *
 * @author sdedic
 */
public class ExternalDropTarget {

    /**
     * Creates DropTarget accepting files for the component.
     * Use with {@link JComponent#setDropTarget}.
     *
     * @param comp the target component.
     * @return the DropTarget instance.
     */
    public static DropTarget createDropTarget(JComponent comp) {
        return new DropTarget(comp, new ExternalDT());
    }

    static class ExternalDT implements DropTargetListener {
        @Override
        public void dragEnter(DropTargetDragEvent dtde) {
        }

        @Override
        public void dragExit(DropTargetEvent dte) {
        }

        @Override
        public void dragOver(DropTargetDragEvent dtde) {
            for (ExternalDropHandler handler : Lookup.getDefault().lookupAll(ExternalDropHandler.class)) {
                //check if a file is being dragged over and if anybody can process it
                if (handler.canDrop(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    return;
                }
            }
            dtde.rejectDrag();
        }

        @Override
        public void drop(DropTargetDropEvent dtde) {
            boolean dropRes = false;
            try {
                for (ExternalDropHandler handler : Lookup.getDefault().lookupAll(ExternalDropHandler.class)) {
                    if (handler.canDrop(dtde)) {
                        //file is being dragged over
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        //let the handler to take care of it
                        dropRes = handler.handleDrop(dtde);
                        break;
                    }
                }
            } finally {
                dtde.dropComplete(dropRes);
            }
        }

        @Override
        public void dropActionChanged(DropTargetDragEvent dtde) {
        }
    }

}
