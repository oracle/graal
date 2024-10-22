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

package org.graalvm.visualizer.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import jdk.graal.compiler.graphio.parsing.model.Folder;
import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.Group.Feedback;

/**
 * Represents handle to a dumped data element. The handle identifies a specific
 * element without holding its instance in the memory. The element and possibly
 * its parents can be GCed.
 * <p/>
 * The Handle can resolve to the element, which may take some time, and may
 * fail as well.
 */
public class DataElementHandle {
    /**
     * The owning document
     */
    private final GraphDocument document;

    /**
     * Path of element IDs, starting from the document's direct children.
     */
    private final List<Object> path;

    DataElementHandle(GraphDocument document, List<Object> path) {
        this.document = document;
        this.path = new ArrayList<>(path);
    }

    DataElementHandle(GraphDocument document, FolderElement fe) {
        this.document = document;
        List<Object> ll = new ArrayList<>();
        ll.add(fe.getID());
        for (Folder ff = fe.getParent(); ff != null && ff != document; ) {
            FolderElement fc = ff;
            ll.add(0, fc.getID());
            ff = fc.getParent();
        }
        this.path = ll;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.document);
        hash = 79 * hash + Objects.hashCode(this.path);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DataElementHandle other = (DataElementHandle) obj;
        if (!Objects.equals(this.document, other.document)) {
            return false;
        }
        return Objects.equals(this.path, other.path);
    }

    /**
     * Returns the owner document.
     *
     * @return owner document.
     */
    public GraphDocument getOwner() {
        return document;
    }

    /**
     * Provides the target element's ID.
     *
     * @return
     */
    public Object getID() {
        return path.get(path.size() - 1);
    }

    /**
     * Creates a handle to an existing place in GraphDocument
     *
     * @param document the owner document
     * @param path     path from the document down to the element
     * @return handle object
     */
    public static DataElementHandle create(GraphDocument document, List<Object> path) {
        return new DataElementHandle(document, path);
    }

    /**
     * Creates a handle to an existing item in a GraphDocument.
     * The method will thrown an exception, if the element is not inside the document.
     *
     * @param document the owner document
     * @param e        the concrete item
     * @return handle object
     */
    public static DataElementHandle create(GraphDocument document, FolderElement e) {
        if (!document.isParentOf(e)) {
            throw new IllegalArgumentException(e + " is not owned by " + document);
        }
        return new DataElementHandle(document, e);
    }

    /**
     * Attempts to resolve the handle back to an object. The object will
     * be potentially loaded back into the memory, if it was already
     * GCed (i.e. large graph in a group that is not displayed anywhere).
     * <p>
     * The function returns async completion handle, that can be waited on or
     * chained after.
     * <p>
     * The Future may return {@code null} in the case the element cannot be
     * located.
     *
     * @param feedback feedback to report loading progress.
     * @return completion handle
     */
    public CompletableFuture<FolderElement> resolve(Feedback feedback) {
        Folder f = document;
        return CompletableFuture.supplyAsync(() -> {
            Folder c = f;
            FolderElement result = null;
            O:
            for (Object s : path) {
                if (c instanceof Group.LazyContent) {
                    Group.LazyContent<Collection<FolderElement>> lazy = (Group.LazyContent<Collection<FolderElement>>) c;
                    Future<Collection<FolderElement>> fut = lazy.completeContents(feedback);
                    try {
                        for (FolderElement fe : fut.get()) {
                            if (s.equals(fe.getID())) {
                                if (fe instanceof Folder) {
                                    c = (Folder) fe;
                                    continue O;
                                } else {
                                    result = fe;
                                    break O;
                                }
                            }
                        }
                    } catch (InterruptedException | ExecutionException ex) {
                        throw new CompletionException(ex);
                    }
                } else {
                    for (FolderElement fe : c.getElements()) {
                        if (s.equals(fe.getID())) {
                            result = fe;
                            break;
                        }
                    }
                }
                if (!(result instanceof Folder)) {
                    break;
                }
                c = (Folder) result;
            }
            if (result == null) {
                return c;
            }
            return result;
        });
    }
}
