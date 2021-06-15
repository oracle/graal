/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.collection;

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
