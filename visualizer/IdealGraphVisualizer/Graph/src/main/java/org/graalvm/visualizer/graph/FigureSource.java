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

package org.graalvm.visualizer.graph;

import org.graalvm.visualizer.data.Source;

import jdk.graal.compiler.graphio.parsing.model.InputNode;

/**
 * Specialized version of Source, which invalidates data in Figure and/or Diagram upon modification.
 */
class FigureSource extends Source {
    /**
     * Backreference to the Figure.
     */
    private final Figure fig;

    public FigureSource(Figure fig) {
        this.fig = fig;
    }

    @Override
    public void addSourceNodes(Source s) {
        for (InputNode n : s.getSourceNodes()) {
            super.addSourceNode(n);
        }
        fig.sourcesChanged(this);
    }

    @Override
    public void addSourceNode(InputNode n) {
        super.addSourceNode(n);
        fig.sourcesChanged(this);
    }
}
