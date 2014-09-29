/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.internal;

import java.util.*;
import java.util.concurrent.*;

import com.oracle.truffle.api.nodes.*;

/** Contains utility classes shared across generated DSLNode implementations. */
public class DSLShare {

    public static boolean isExcluded(Node currentNode, DSLMetadata otherMetadata) {
        assert otherMetadata.getExcludedBy().length > 0 : "At least one exclude must be defined for isIncluded.";
        Node cur = findRoot(currentNode);
        while (cur != null) {
            Class<?> curClass = cur.getClass();
            if (curClass == otherMetadata.getSpecializationClass()) {
                return true;
            } else if (containsClass(otherMetadata.getExcludedBy(), cur)) {
                return true;
            }
            cur = getNext(cur);
        }
        return false;
    }

    private static boolean includes(Node oldNode, DSLNode newNode) {
        return containsClass(newNode.getMetadata0().getIncludes(), oldNode);
    }

    public static <T extends Node & DSLNode> T rewrite(final Node thisNode, final T newNode, final String message) {
        return thisNode.atomic(new Callable<T>() {
            public T call() {
                assert newNode != null;
                if (getNext(thisNode) != null || getPrevious(thisNode) != null) {
                    // already polymorphic -> append
                    return appendPolymorphic(findUninitialized(thisNode), newNode);
                } else if (includes(thisNode, newNode)) {
                    // included -> remains monomorphic
                    newNode.adoptChildren0(thisNode, null);
                    return thisNode.replace(newNode, message);
                } else {
                    // goto polymorphic
                    return null;
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static <T extends Node> T findRoot(T node) {
        Node prev = node;
        Node cur;
        do {
            cur = prev;
            prev = getPrevious(cur);
        } while (prev != null);
        return (T) cur;
    }

    private static Node findUninitialized(Node node) {
        Node next = node;
        Node cur;
        do {
            cur = next;
            next = getNext(cur);
        } while (next != null);
        return cur;
    }

    public static <T extends Node & DSLNode> T rewriteUninitialized(final Node uninitialized, final T newNode) {
        return uninitialized.atomic(new Callable<T>() {
            public T call() {
                Node prev = getPrevious(uninitialized);
                if (prev == null) {
                    newNode.adoptChildren0(uninitialized, null);
                    return uninitialized.replace(newNode, "Uninitialized monomorphic");
                } else {
                    return appendPolymorphic(uninitialized, newNode);
                }
            }
        });

    }

    public static <T extends Node & DSLNode> T rewriteToPolymorphic(final Node oldNode, final DSLNode uninitializedDSL, final T polymorphic, final DSLNode currentCopy, final DSLNode newNodeDSL,
                    final String message) {
        return oldNode.atomic(new Callable<T>() {
            public T call() {
                assert getNext(oldNode) == null;
                assert getPrevious(oldNode) == null;
                assert newNodeDSL != null;

                Node uninitialized = (Node) uninitializedDSL;
                Node newNode = (Node) newNodeDSL;
                polymorphic.adoptChildren0(oldNode, (Node) currentCopy);

                updateSourceSection(oldNode, uninitialized);
                // new specialization
                updateSourceSection(oldNode, newNode);
                newNodeDSL.adoptChildren0(null, uninitialized);
                currentCopy.adoptChildren0(null, newNode);

                oldNode.replace(polymorphic, message);

                assert polymorphic.getNext0() == currentCopy;
                assert newNode != null ? currentCopy.getNext0() == newNode : currentCopy.getNext0() == uninitialized;
                assert uninitializedDSL.getNext0() == null;
                return polymorphic;
            }
        });
    }

    private static void updateSourceSection(Node oldNode, Node newNode) {
        if (newNode.getSourceSection() == null) {
            newNode.assignSourceSection(oldNode.getSourceSection());
        }
    }

    private static Class<?>[] mergeTypes(DSLNode node, Class<?>[] types) {
        Class<?>[] specializedTypes = node.getMetadata0().getSpecializedTypes();
        if (specializedTypes.length == 0) {
            return null;
        } else if (types == null) {
            return Arrays.copyOf(specializedTypes, specializedTypes.length);
        } else {
            for (int i = 0; i < specializedTypes.length; i++) {
                if (specializedTypes[i] != types[i]) {
                    types[i] = Object.class;
                }
            }
            return types;
        }
    }

    private static <T extends Node & DSLNode> T appendPolymorphic(Node uninitialized, T newNode) {
        Class<?>[] includes = newNode.getMetadata0().getIncludes();
        Node cur = getPrevious(uninitialized);
        Node prev = uninitialized;
        int depth = 0;
        Class<?>[] types = null;
        while (cur != null) {
            if (containsClass(includes, cur)) {
                cur.replace(prev, "Included in other specialization");
                cur = prev;
            } else {
                depth++;
                types = mergeTypes((DSLNode) cur, types);
            }
            prev = cur;
            cur = getPrevious(cur);
        }
        assert prev.getCost() == NodeCost.POLYMORPHIC;

        updateSourceSection(prev, newNode);
        if (depth <= 1) {
            newNode.adoptChildren0(prev, null);
            return prev.replace(newNode, "Polymorphic to monomorphic.");
        } else {
            newNode.adoptChildren0(null, uninitialized);
            ((DSLNode) prev).updateTypes0(mergeTypes(newNode, types));
            return uninitialized.replace(newNode, "Appended polymorphic");
        }
    }

    private static boolean containsClass(Class<?>[] classList, Node node) {
        Class<?> nodeClass = node.getClass();
        for (Class<?> toCheck : classList) {
            if (nodeClass == toCheck) {
                if (node.getCost() == NodeCost.UNINITIALIZED) {
                    /*
                     * In case a specialization is excluded by the fallback specialization the
                     * uninitialized class is used as exclusion class. Because the fallback field in
                     * the uninitialized specialization is not accessible we use the costs to check
                     * if the fallback was reached or not. In case the fallback was reached in the
                     * uninitialized version the cost is MONOMORPHIC, otherwise it is UNINITIALIZED.
                     */
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private static Node getNext(Node node) {
        return ((DSLNode) node).getNext0();
    }

    private static Node getPrevious(Node node) {
        Node parent = node.getParent();
        if (parent instanceof DSLNode && getNext(parent) == node) {
            return parent;
        } else {
            return null;
        }
    }

}