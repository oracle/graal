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
package at.ssw.dataflow.layout;

import at.ssw.positionmanager.LayoutGraph;
import at.ssw.positionmanager.LayoutManager;
import at.ssw.dataflow.options.Validator;

/**
 * Wrapper class for LayoutManager implementations that do not support the
 * ExternalGraphLayouter Interface used within the project.
 * This class can be used to test implementations of the LayoutManager interface
 * on data-flow graphs. In advanved cases it is better to implement the
 * ExternalGraphLayouter interface because then the full features of the
 * optionprovider are released.
 *
 * @author Stefan Loidl
 */
public class ExternalGraphLayoutWrapper implements ExternalGraphLayouter{

    private boolean clustering=false;
    private LayoutManager layout=null;
    private boolean movement=false;
    private boolean animation=false;

    /**
     * Creates a new instance of ExternalGraphLayoutWrapper
     * layout: the LayoutManager
     * clustering: is clustering supported?
     * movement: is node movement supported?
     * animation: is node animation supported?
     *
     * Note: Movement is supported if the routing can be done indepenent from
     *       the layout step.
     * Note: Node animation is supported if the routing consumes low time.
     */
    public ExternalGraphLayoutWrapper(LayoutManager layout, boolean clustering, boolean movement, boolean animation) {
        this.clustering=clustering;
        this.layout=layout;
        this.animation=animation;
        this.movement=movement;
    }

    public boolean isClusteringSupported() {
        return clustering;
    }

    public void doLayout(LayoutGraph graph) {
        if(layout!=null) layout.doLayout(graph);
    }

    public void doRouting(LayoutGraph graph) {
        if(layout!=null) layout.doRouting(graph);
    }


    public String[] getOptionKeys() {
        return new String[0];
    }

    public boolean setOption(String key, Object value) {
        return false;
    }

    public Object getOption(String key) {
        return null;
    }

    public Class getOptionClass(String key) {
        return null;
    }

    public Validator getOptionValidator(String key) {
        return null;
    }

    public String getOptionDescription(String key) {
        return null;
    }

    public boolean isAnimationSupported() {
        return animation;
    }

    public boolean isMovementSupported() {
        return movement;
    }

    public void setUseCurrentNodePositions(boolean b) {}

}
