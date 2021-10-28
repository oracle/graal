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
package org.graalvm.compiler.truffle.runtime;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

class TraversingBlockingQueue implements BlockingQueue<Runnable> {
    final BlockingQueue<Runnable> entries = new LinkedBlockingDeque<>();

    @SuppressWarnings("unchecked")
    private static CompilationTask task(Runnable entry) {
        return ((CompilationTask.ExecutorServiceWrapper) entry).getCompileTask();
    }

    @Override
    public boolean add(Runnable e) {
        return entries.add(e);
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
        Runnable max = takeMax();
        if (max != null) {
            return max;
        }
        return entries.poll(timeout, unit);
    }

    @Override
    public Runnable poll() {
        Runnable max = takeMax();
        if (max != null) {
            return max;
        }
        return entries.poll();
    }

    /*
     * This method traverses the entries and picks the best task according to {@link
     * CompilationTask#isHigherPriorityThan(CompilationTask)}. The method is synchronized to ensure
     * that only 1 thread at a time traverses and picks the max entry. It is still possible that the
     * {@link #entries} gets modified during the execution of this method (e.g. add, but that's
     * fine, because the iterator is weakly consistent). This allows the queue to not block
     * interpreter threads from adding entries to the queue while a compiler thread is looking for
     * the best task.
     */
    private synchronized Runnable takeMax() {
        if (entries.isEmpty()) {
            return null;
        }
        Runnable max = null;
        long time = System.nanoTime();
        int removed = 0;
        try {
            Iterator<Runnable> it = entries.iterator();
            while (it.hasNext()) {
                Runnable entry = it.next();
                CompilationTask task = task(entry);
                // updateWeight returns false only if the task's target does not exist
                if (task.isCancelled() || !task.updateWeight(time)) {
                    it.remove();
                    removed--;
                    continue;
                }
                if (max == null || task.isHigherPriorityThan(task(max))) {
                    max = entry;
                }
            }
            // entries.remove can only return false if a sleeping thread takes the only element
            return entries.remove(max) ? max : null;
        } finally {
            if (max != null) {
                CompilationTask task = task(max);
                task.setTime(System.nanoTime() - time);
                task.setQueueChange(removed - 1);
            }
        }
    }

    @Override
    public boolean offer(Runnable e) {
        return entries.offer(e);
    }

    @Override
    public Runnable remove() {
        return entries.remove();
    }

    @Override
    public Runnable element() {
        return entries.element();
    }

    @Override
    public Runnable peek() {
        return entries.peek();
    }

    @Override
    public void put(Runnable e) throws InterruptedException {
        entries.put(e);
    }

    @Override
    public boolean offer(Runnable e, long timeout, TimeUnit unit) throws InterruptedException {
        return entries.offer(e, timeout, unit);
    }

    @Override
    public Runnable take() throws InterruptedException {
        Runnable max = takeMax();
        if (max != null) {
            return max;
        }
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
    public boolean addAll(Collection<? extends Runnable> c) {
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
    public Iterator<Runnable> iterator() {
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
    public int drainTo(Collection<? super Runnable> c) {
        return entries.drainTo(c);
    }

    @Override
    public int drainTo(Collection<? super Runnable> c, int maxElements) {
        return entries.drainTo(c, maxElements);
    }
}
