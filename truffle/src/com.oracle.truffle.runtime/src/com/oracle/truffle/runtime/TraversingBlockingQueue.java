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
package com.oracle.truffle.runtime;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * See https://github.com/oracle/graal/blob/master/truffle/docs/TraversingCompilationQueue.md .
 */
class TraversingBlockingQueue implements BlockingQueue<Runnable> {

    final BlockingQueue<Runnable> entries;

    TraversingBlockingQueue(BlockingQueue<Runnable> entries) {
        this.entries = entries;
    }

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
