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

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.truffle.runtime.CompilationTask;

public class TraversingBlockingQueue<E> implements BlockingQueue<E> {
    final BlockingQueue<E> entries = new LinkedBlockingDeque<>();

    @SuppressWarnings("unchecked")
    private CompilationTask task(E entry) {
        return ((CompilationTask.ExecutorServiceWrapper) entry).getCompileTask();
    }

    @Override
    public boolean add(E e) {
        return entries.add(e);
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public synchronized E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E max = takeMax();
        if (max != null) {
            return max;
        }
        return entries.poll(timeout, unit);
    }

    @Override
    public synchronized E poll() {
        E max = takeMax();
        if (max != null) {
            return max;
        }
        return entries.poll();
    }

    private E takeMax() {
        if (entries.isEmpty()) {
            return null;
        }
        long time = System.nanoTime();
        Iterator<E> it = entries.iterator();
        E max = null;
        while (it.hasNext()) {
            E entry = it.next();
            CompilationTask task = task(entry);
            if (task.isCancelled() || task.updateWeight(time) < 0) {
                it.remove();
                continue;
            }
            if (task.isLastTier()) {
                if (task.targetHighestCompiledTier() == 2) {
                    return removeAndReturn(entry);
                }
                continue;
            }
            if (max == null || task.greaterThan(task(max))) {
                max = entry;
            }
        }
        return removeAndReturn(max);
    }

    private E removeAndReturn(E max) {
        entries.remove(max);
        return max;
    }

    @Override
    public boolean offer(E e) {
        return add(e);
    }

    @Override
    public E remove() {
        return entries.remove();
    }

    @Override
    public E element() {
        return entries.element();
    }

    @Override
    public E peek() {
        return entries.peek();
    }

    @Override
    public void put(E e) throws InterruptedException {
        entries.put(e);
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        return entries.offer(e, timeout, unit);
    }

    @Override
    public E take() throws InterruptedException {
        return entries.take();
    }

    @Override
    public int remainingCapacity() {
        return entries.remainingCapacity();
    }

    @Override
    public boolean remove(Object o) {
        return entries.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return entries.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return entries.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return entries.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return entries.retainAll(c);
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public boolean contains(Object o) {
        return entries.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return entries.iterator();
    }

    @Override
    public Object[] toArray() {
        return entries.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return entries.toArray(a);
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        return entries.drainTo(c);
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        return entries.drainTo(c, maxElements);
    }
}
