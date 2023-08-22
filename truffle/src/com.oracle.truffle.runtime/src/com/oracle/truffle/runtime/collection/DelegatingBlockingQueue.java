/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime.collection;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class DelegatingBlockingQueue<E> implements BlockingQueue<E> {
    private final ReentrantLock lock;
    private final Condition notEmpty;
    private final SerialQueue<E> delegate;

    public DelegatingBlockingQueue(SerialQueue<E> delegate) {
        this.lock = new ReentrantLock();
        this.notEmpty = this.lock.newCondition();
        this.delegate = delegate;
    }

    @Override
    public boolean add(E x) {
        lock.lock();
        try {
            final boolean wasEmpty = isEmpty();
            delegate.add(x);
            if (wasEmpty) {
                notEmpty.signalAll();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public int addIndexOf(E x) {
        lock.lock();
        try {
            final boolean wasEmpty = isEmpty();
            final int index = delegate.addIndexOf(x);
            if (wasEmpty) {
                notEmpty.signalAll();
            }
            return index;
        } finally {
            lock.unlock();
        }
    }

    public int indexOf(E x) {
        lock.lock();
        try {
            return delegate.indexOf(x);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean offer(E x) {
        return add(x);
    }

    @Override
    public void put(E x) throws InterruptedException {
        add(x);
    }

    @Override
    public boolean offer(E x, long l, TimeUnit unit) throws InterruptedException {
        return add(x);
    }

    @SuppressWarnings("unchecked")
    private E lockedPoll() {
        return delegate.poll();
    }

    @Override
    public E poll() {
        lock.lock();
        try {
            return lockedPoll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock localLock = this.lock;
        localLock.lockInterruptibly();
        E result;
        try {
            while ((result = lockedPoll()) == null && nanos > 0) {
                nanos = notEmpty.awaitNanos(nanos);
            }
        } finally {
            localLock.unlock();
        }
        return result;
    }

    @Override
    public E remove() {
        lock.lock();
        try {
            final E result = lockedPoll();
            if (result == null) {
                throw new NoSuchElementException();
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    @Override
    public E peek() {
        lock.lock();
        try {
            return delegate.peek();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E element() {
        final E result = peek();
        if (result == null) {
            throw new NoSuchElementException();
        }
        return result;
    }

    @Override
    public E take() throws InterruptedException {
        lock.lockInterruptibly();
        E result;
        try {
            while ((result = lockedPoll()) == null) {
                notEmpty.await();
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            delegate.clear();
            // Note: no need to awake waiting threads, because no item was added.
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return delegate.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<E> iterator() {
        final Object[] result = toArray();
        return (Iterator<E>) Arrays.asList(result).iterator();
    }

    @Override
    public Object[] toArray() {
        lock.lock();
        try {
            return delegate.toArray();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        lock.lock();
        try {
            return delegate.toArray(a);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int drainTo(Collection<? super E> collection) {
        int count = 0;
        final Iterator<E> iterator = iterator();
        while (iterator.hasNext()) {
            collection.add(iterator.next());
            count++;
        }
        return count;
    }

    @Override
    public int drainTo(Collection<? super E> collection, int i) {
        throw new UnsupportedOperationException();
    }

    public int internalCapacity() {
        lock.lock();
        try {
            return delegate.internalCapacity();
        } finally {
            lock.unlock();
        }
    }
}
