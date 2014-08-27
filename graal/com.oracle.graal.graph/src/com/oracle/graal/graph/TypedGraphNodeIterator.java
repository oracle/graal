package com.oracle.graal.graph;

import java.util.*;

class TypedGraphNodeIterator<T extends IterableNodeType> implements Iterator<T> {

    private final Graph graph;
    private final int[] ids;
    private final Node[] current;

    private int currentIdIndex;
    private boolean needsForward;

    public TypedGraphNodeIterator(NodeClass clazz, Graph graph) {
        this.graph = graph;
        ids = clazz.iterableIds();
        currentIdIndex = 0;
        current = new Node[ids.length];
        Arrays.fill(current, Graph.PLACE_HOLDER);
        needsForward = true;
    }

    private Node findNext() {
        if (needsForward) {
            forward();
        } else {
            Node c = current();
            Node afterDeleted = skipDeleted(c);
            if (afterDeleted == null) {
                needsForward = true;
            } else if (c != afterDeleted) {
                setCurrent(afterDeleted);
            }
        }
        if (needsForward) {
            return null;
        }
        return current();
    }

    private static Node skipDeleted(Node node) {
        Node n = node;
        while (n != null && n.isDeleted()) {
            n = n.typeCacheNext;
        }
        return n;
    }

    private void forward() {
        needsForward = false;
        int startIdx = currentIdIndex;
        while (true) {
            Node next;
            if (current() == Graph.PLACE_HOLDER) {
                next = graph.getStartNode(ids[currentIdIndex]);
            } else {
                next = current().typeCacheNext;
            }
            next = skipDeleted(next);
            if (next == null) {
                currentIdIndex++;
                if (currentIdIndex >= ids.length) {
                    currentIdIndex = 0;
                }
                if (currentIdIndex == startIdx) {
                    needsForward = true;
                    return;
                }
            } else {
                setCurrent(next);
                break;
            }
        }
    }

    private Node current() {
        return current[currentIdIndex];
    }

    private void setCurrent(Node n) {
        current[currentIdIndex] = n;
    }

    @Override
    public boolean hasNext() {
        return findNext() != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T next() {
        Node result = findNext();
        if (result == null) {
            throw new NoSuchElementException();
        }
        needsForward = true;
        return (T) result;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}