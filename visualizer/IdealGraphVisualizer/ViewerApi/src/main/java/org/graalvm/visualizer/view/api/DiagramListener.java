/*
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
 * Informs about changes of a {@link DiagramModel}.
 *
 * @author sdedic
 */
public interface DiagramListener extends EventListener {
    /**
     * Informs that state of the view has changed. This includes selection, hidden nodes.
     *
     * @param ev event with details
     */
    public default void stateChanged(DiagramEvent ev) {
    }

    /**
     * The diagram has been changed. The diagram is not necessarily the final one.
     * The final diagram will be send in {@link #diagramReady}. The old diagram
     * may not be displayed already.
     *
     * @param ev event with details
     */
    public default void diagramChanged(DiagramEvent ev) {
    }

    /**
     * The diagram was replaced and became ready: it is filtered, extracted.
     *
     * @param ev event with details
     */
    public default void diagramReady(DiagramEvent ev) {
    }

}
