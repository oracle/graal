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

package org.graalvm.visualizer.data.serialization.lazy;

import java.util.*;

import jdk.graal.compiler.graphio.parsing.model.*;

/**
 * Collects errors reported by the reader. Errors are kept off the actual data, and
 * should be processed separately.
 * <p/>
 * {@link InputGraph}s are either fully read and the error information is available
 * at the time they are added to their parent group. Or they are lazy and will fire
 * a change event to indicate their state has been changed.
 *
 * @author sdedic
 */
public final class ReaderErrors {
    private static final ReaderErrors INSTANCE = new ReaderErrors();

    /**
     * For each element, holds list of errors. For group, also references
     * the directly contained graphs and their errors.
     */
    private static final Map<FolderElement, ElementData> errorList = new WeakHashMap<>();

    static class ElementData {
        final List<String> messages = new ArrayList<>(2);
        final List<String> pathFromRoot;
        Map<String, ElementData> graphErrors;

        public ElementData(FolderElement el, Folder folder) {
            this.pathFromRoot = pathFromRoot(el, new ArrayList<>());
        }

        void addGraphError(ElementData e) {
            if (graphErrors == null) {
                graphErrors = new HashMap<>();
            } else {
                int i = e.pathFromRoot.size();
                graphErrors.put(e.pathFromRoot.get(i - 1), e);
            }
        }

        void addError(String s) {
            messages.add(s);
        }
    }

    private static List<String> pathFromRoot(FolderElement el, List storage) {
        if (el == null) {
            return storage;
        }
        List<String> ret;
        Folder parent = el.getParent();
        ret = pathFromRoot(parent, storage);
        ret.add(el.getName());
        return ret;
    }

    static void addError(Object el, Folder folder, String error) {
        if (el instanceof FolderElement) {
            addError((FolderElement) el, folder, error);
        }
    }

    static void addError(FolderElement el, Folder folder, String error) {
        synchronized (errorList) {
            ElementData data = errorList.computeIfAbsent(el, (e) -> {
                ElementData ne = new ElementData(e, folder);
                if (e instanceof InputGraph) {
                    Folder f = el.getParent();
                    if (f == null) {
                        f = folder;
                    }
                    if (f != null) {
                        FolderElement fe = f;
                        ElementData fed = errorList.computeIfAbsent(fe, (e2) -> new ElementData(fe, null));
                        fed.addGraphError(ne);
                    }
                }
                return ne;
            });
            data.addError(error);

        }
        if (el instanceof ChangedEventProvider) {
            ((ChangedEventProvider) el).getChangedEvent().fire();
        }
        Folder f = el.getParent() == null ? folder : el.getParent();
        if (f instanceof ChangedEventProvider) {
            ((ChangedEventProvider) f).getChangedEvent().fire();
        } else {
            // report status change on the document
            GraphDocument doc = el.getOwner();
            if (doc == null) {
                doc = folder.getOwner();
            }
            if (doc != null) {
                doc.getChangedEvent().fire();
            }

        }
    }

    /**
     * Checks that the element contains an error. If this method returns true, then {@link #getReaderErrors}
     * should return non-{@code null} and non-empty list.
     *
     * @param fe          loaded item
     * @param childrenToo if true, then contained graphs will be checked as well
     * @return true, if there are some errors
     */
    public static boolean containsError(FolderElement fe, boolean childrenToo) {
        ElementData ed = errorList.get(fe);
        return ed != null && (!ed.messages.isEmpty() || (childrenToo && ed.graphErrors != null));
    }

    /**
     * Retrieves errors collected during reading of an element. If {@code childrenToo} is true,
     * it will also present errors for direct children. Errors of subgraphs are always reported
     * with the topleve graph.
     *
     * @param fe          the element
     * @param childrenToo if true, errors for group's children will be reported as well.
     * @return errors for the element
     */
    public static List<String> getReaderErrors(FolderElement fe, boolean childrenToo) {
        synchronized (errorList) {
            ElementData e = errorList.get(fe);
            if (e == null) {
                return Collections.emptyList();
            }
            List<String> msg = new ArrayList<>(e.messages);
            if (childrenToo && e.graphErrors != null) {
                for (ElementData nested : e.graphErrors.values()) {
                    msg.addAll(nested.messages);
                }
            }
            return msg;
        }
    }
}
