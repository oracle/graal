/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graphio.parsing.model;

import java.util.function.Consumer;

/**
 * Visitor interface to traverse the various elements of the {@link GraphDocument} tree. Traversal
 * can be stopped early by returning {@code false} from any of the {@code visit} methods.
 */
public interface GraphDocumentVisitor {

    static void visitAll(GraphDocument doc, Consumer<InputGraph> visit) {
        GraphDocumentVisitor visitor = inputGraph -> {
            visit.accept(inputGraph);
            return true;
        };
        visitor.visit(doc);
    }

    /**
     * Visits a {@link Folder} in the graph document, which could be the top-level
     * {@link GraphDocument} or a nested {@link Group}.
     * <p>
     * The default implementation visits all elements in order, stopping if
     * {@link #visit(FolderElement)} returns false for any of them.
     */
    default boolean visit(Folder folder) {
        for (FolderElement elem : folder.getElements()) {
            if (!visit(elem)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Visits an {@link InputGraph}. Implementors can choose whether to stop at the outermost level
     * or also consider nested subgraphs.
     */
    boolean visit(InputGraph inputGraph);

    /**
     * Visits a {@link Group}. The default implementation delegates to {@link #visit(Folder)}.
     */
    default boolean visit(Group g) {
        return visit((Folder) g);
    }

    /**
     * Visits a {@link GraphDocument}. The default implementation delegates to
     * {@link #visit(Folder)}.
     */
    default boolean visit(GraphDocument document) {
        return visit((Folder) document);
    }

    /**
     * Visits a {@link FolderElement}. The default implementation delegates to the {@code visit}
     * version corresponding to the concrete type of {@code element}.
     */
    default boolean visit(FolderElement element) {
        if (element instanceof Group group) {
            return visit(group);
        }
        if (element instanceof InputGraph inputGraph) {
            return visit(inputGraph);
        }
        if (element instanceof Folder folder) {
            return visit(folder);
        }
        return true;
    }
}
