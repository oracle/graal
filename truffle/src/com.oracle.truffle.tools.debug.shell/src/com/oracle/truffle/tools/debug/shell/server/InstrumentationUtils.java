/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.tools.debug.shell.server;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A language-agnostic for printing out various pieces of a Truffle AST.
 */
final class InstrumentationUtils {

    private InstrumentationUtils() {
    }

    static String getShortDescription(SourceSection sourceSection) {
        StringBuilder b = new StringBuilder();
        b.append(sourceSection.getSource().getName());
        b.append(":");
        if (sourceSection.getStartLine() == sourceSection.getEndLine()) {
            b.append(sourceSection.getStartLine());
        } else {
            b.append(sourceSection.getStartLine()).append("-").append(sourceSection.getEndLine());
        }
        return b.toString();
    }

    /**
     * Language-agnostic textual display of source location information.
     */
    static class LocationPrinter {

        public String displaySourceLocation(Node node) {

            if (node == null) {
                return "<unknown>";
            }
            SourceSection section = node.getSourceSection();
            boolean estimated = false;
            if (section == null) {
                section = node.getEncapsulatingSourceSection();
                estimated = true;
            }
            if (section == null) {
                return "<unknown source location>";
            }
            return getShortDescription(section) + (estimated ? "~" : "");
        }
    }
}
