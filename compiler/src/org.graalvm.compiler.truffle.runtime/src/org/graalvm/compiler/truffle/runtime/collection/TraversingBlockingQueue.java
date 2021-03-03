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
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.truffle.runtime.CompilationTask;

public class TraversingBlockingQueue<E> implements BlockingQueue<E> {
    BlockingQueue<E> firstTierEntries = new LinkedBlockingDeque<>();
    BlockingQueue<E> lastTierEntries = new LinkedBlockingDeque<>();

    private static boolean greater(double maxWeight, boolean maxCompiled, double weight, boolean compiled) {
        if (compiled && !maxCompiled) {
            return true;
        }
        if (maxCompiled && !compiled) {
            return false;
        }
        return weight > maxWeight;
    }

    @SuppressWarnings("unchecked")
    private CompilationTask task(E entry) {
        return ((CompilationTask.ExecutorServiceWrapper) entry).getCompileTask();
    }

    @Override
    public boolean add(E e) {
        if (task(e).isFirstTier()) {
            return firstTierEntries.add(e);
        } else {
            return lastTierEntries.add(e);
        }
    }

    @Override
    public boolean isEmpty() {
        return firstTierEntries.isEmpty() && lastTierEntries.isEmpty();
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        // We don't wait for elements.
        return poll();
    }

    @Override
    public E poll() {
        if (firstTierEntries.isEmpty()) {
            return lastTierEntries.poll();
        }
        return poll(firstTierEntries);
    }

    private E poll(Queue<E> entries) {
        if (entries.isEmpty()) {
            return null;
        }
        long time = System.nanoTime();
        Iterator<E> it = entries.iterator();
        E max = it.next();
        double maxWeight = task(max).weight(time);
        boolean maxCompiled = task(max).targetPreviouslyCompiled();
        while (it.hasNext()) {
            E entry = it.next();
            CompilationTask task = task(entry);
            double weight = task.weight(time);
            if (task.isCancelled() || weight < 0) {
                it.remove();
                continue;
            }
            boolean compiled = task.targetPreviouslyCompiled();
            if (greater(maxWeight, maxCompiled, weight, compiled)) {
                maxWeight = weight;
                maxCompiled = compiled;
                max = entry;
            }
        }
        return remove(max, entries);
    }

    private E remove(E max, Queue<E> entries) {
        entries.remove(max);
        return max;
    }

    @Override
    public boolean offer(E e) {
        return add(e);
    }

    @Override
    public E remove() {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public E element() {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public E peek() {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public void put(E e) throws InterruptedException {
        add(e);
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public E take() throws InterruptedException {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public int remainingCapacity() {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        throw new UnsupportedOperationException("Not supported!");
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        throw new UnsupportedOperationException("Not supported!");
    }
}
