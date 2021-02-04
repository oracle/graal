package org.graalvm.compiler.truffle.runtime.collection;

public interface Pool<E> {
    void add(E x);

    default int addIndexOf(E x) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the position in the pool, or -1 if the element is not present.
     */
    default int indexOf(E x) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    E poll();

    @SuppressWarnings("unchecked")
    E peek();

    void clear();

    int size();

    Object[] toArray();

    @SuppressWarnings("unchecked")
    <T> T[] toArray(T[] a);

    int internalCapacity();
}
