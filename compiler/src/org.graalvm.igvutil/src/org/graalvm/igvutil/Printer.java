/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.igvutil;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;

/**
 * Prints the contents of a {@link GraphDocument} in a tree-like representation.
 */
public final class Printer {
    private final PrintWriter writer;

    public Printer(PrintWriter writer) {
        this.writer = writer;
    }

    /**
     * Prints the contents of {@code document}.
     *
     * @param name name to print for the outermost element in the document, needed as
     *            {@code document.getName()} can be {@code null}.
     */
    public void print(GraphDocument document, String name) {
        writer.println(name);
        List<Integer> indentStack = new ArrayList<>();
        indentStack.add(document.getElements().size());
        for (FolderElement f : document.getElements()) {
            print(f, indentStack);
        }
    }

    void printIndent(List<Integer> indentStack) {
        for (int i = 0; i < indentStack.size() - 1; ++i) {
            writer.print(indentStack.get(i) > 0 ? "\u2502  " : "   ");
        }
        writer.print(switch (indentStack.getLast()) {
            case 0 -> "   ";
            case 1 -> "\u2514\u2500 ";
            default -> "\u251C\u2500 ";
        });
    }

    private void print(FolderElement folder, List<Integer> indentStack) {
        printIndent(indentStack);
        indentStack.set(indentStack.size() - 1, indentStack.getLast() - 1);
        if (folder instanceof InputGraph graph) {
            writer.println(graph.getName());
        } else if (folder instanceof Group group) {
            writer.println(group.getName());
            indentStack.add(group.getElements().size());
            for (FolderElement f : group.getElements()) {
                print(f, indentStack);
            }
            indentStack.removeLast();
        } else {
            throw new InternalError("Unexpected folder type " + folder.getClass());
        }
    }
}
