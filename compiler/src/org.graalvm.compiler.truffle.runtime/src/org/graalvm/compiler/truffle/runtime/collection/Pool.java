package org.graalvm.compiler.truffle.runtime.collection;

public interface Pool<E> {
    void add(E x);

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
