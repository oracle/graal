/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Node that simulates espresso statements for debugging support.
 */
public final class EspressoStatementNode extends EspressoBaseStatementNode implements BciProvider {

    private final int startBci;
    private final int lineNumber;

    EspressoStatementNode(int startBci, int lineNumber) {
        this.lineNumber = lineNumber;
        this.startBci = startBci;
    }

    @Override
    public Node getLeafNode(int bci) {
        assert bci == startBci;
        return this;
    }

    @Override
    public SourceSection getSourceSection() {
        Source s = getBytecodeNode().getSource();
        // when there is a line number table we also have a source
        assert s != null;
        return s.createSection(lineNumber);
    }

    @Override
    public int getBci(@SuppressWarnings("unused") Frame frame) {
        return startBci;
    }
}
