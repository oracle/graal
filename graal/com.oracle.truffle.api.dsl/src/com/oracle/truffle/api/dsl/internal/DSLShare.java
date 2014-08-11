package com.oracle.truffle.api.dsl.internal;

import java.util.*;

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

    public static <T extends Node & DSLNode> T rewrite(Node thisNode, T newNode, String message) {
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

    public static <T extends Node & DSLNode> T rewriteUninitialized(Node uninitialized, T newNode) {
        Node prev = getPrevious(uninitialized);
        if (prev == null) {
            newNode.adoptChildren0(uninitialized, null);
            return uninitialized.replace(newNode, "Uninitialized monomorphic");
        } else {
            return appendPolymorphic(uninitialized, newNode);
        }
    }

    public static <T extends Node & DSLNode> T rewriteToPolymorphic(Node oldNode, DSLNode uninitialized, T polymorphic, DSLNode currentCopy, DSLNode newNode, String message) {
        assert getNext(oldNode) == null;
        assert getPrevious(oldNode) == null;

        polymorphic.adoptChildren0(oldNode, (Node) currentCopy);

        if (newNode == null) {
            // fallback
            currentCopy.adoptChildren0(null, (Node) uninitialized);
        } else {
            // new specialization
            newNode.adoptChildren0(null, (Node) uninitialized);
            currentCopy.adoptChildren0(null, (Node) newNode);
        }

        oldNode.replace(polymorphic, message);

        assert polymorphic.getNext0() == currentCopy;
        assert newNode != null ? currentCopy.getNext0() == newNode : currentCopy.getNext0() == uninitialized;
        assert uninitialized.getNext0() == null;
        return polymorphic;
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

        if (depth == 0) {
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