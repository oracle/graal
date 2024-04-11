/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.graalvm.visualizer.data.serialization.lazy;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import jdk.graal.compiler.graphio.parsing.model.ChangedEventProvider;
import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.Group.Feedback;

import jdk.graal.compiler.graphio.parsing.ConstantPool;

/**
 * Loads lazy-load contents. Loading runs in {@link #fetchExecutor}, after load process completes,
 * change event is fired in {@link #notifyExecutor}. The Feature implemented as completion callback
 * hooks onto loaded data, so <b>all</b> the data remain as long as at least one loaded item is
 * reachable. If the Group to be completed is not yet scanned (end == -1), the Completer postpones
 * the loading for {@link #RESCHEDULE_DELAY} millis, gives up after {@link #ATTEMPT_COUNT} attempts
 * providing empty content for the group.
 */
abstract class BaseCompleter<T, E extends Group.LazyContent & ChangedEventProvider> implements Completer<T>, Runnable {
    private static final Logger LOG = Logger.getLogger(BaseCompleter.class.getName());

    /**
     * Delay before the next attempt to read and complete the group. In milliseconds.
     */
    public static final int RESCHEDULE_DELAY = 5000;

    /**
     * Maximum attempts to complete the group.
     */
    public static final int ATTEMPT_COUNT = 10;

    protected final ConstantPool initialPool;
    protected final StreamEntry entry;
    private final Env env;

    protected E toComplete;

    private volatile KeepDataFuture future;
    private Feedback feedbackToFinish;

    /**
     * Partial data. Data is held during completion, and released when the completer
     * finishes - they should be weakreferenced after through {@link KeepDataFuture}
     */
    private T partialData = createEmpty();

    /**
     * Will keep the currently resolved elements until the events are delivered by the executor.
     */
    private T keepElements;

    // for diagnostics
    private String name;

    BaseCompleter(Env env, StreamEntry entry) {
        this.env = env;
        // intentionally NOT a clone, so that it has the same hash
        this.initialPool = entry.getInitialPool();
        this.entry = entry;
    }

    protected T filter(T data) {
        return data;
    }

    protected synchronized void attachTo(E group, String name) {
        this.toComplete = group;
        this.name = name;
        if (LOG.isLoggable(Level.FINER)) {
            LOG.log(Level.FINER, "Created completer for {0}, pool id {1}, start = {2}, end = {3}",
                    new Object[]{
                            name,
                            Integer.toHexString(System.identityHashCode(initialPool)),
                            entry.getStart(), entry.getEnd()
                    });
        }
    }

    protected final Env env() {
        return env;
    }

    E getModel() {
        return toComplete;
    }

    protected final E element() {
        return toComplete;
    }

    protected long size() {
        return entry.size();
    }

    public synchronized void end(long end) {
        LOG.log(Level.FINER, "End mark for group {0}", name);
    }

    @Override
    public synchronized Future<T> completeContents(Feedback feedback) {
        if (future != null) {
            return future;
        }
        feedbackToFinish = feedback;
        return future = new KeepDataFuture(scheduleFetch(feedback));
    }

    /**
     * Sends changed event from the completed group. This method runs first in the
     * {@link #EXPAND_RP} - it is posted so that the code executes <b>after</b> the computing task
     * finishes, and the {@link Future#isDone} turns true. The actual event delivery is replanned
     * into EDT, to maintain IGV threading model.
     */
    @Override
    public void run() {
        Feedback f;
        synchronized (this) {
            partialData = null;
        }
        // must fire BEFORE keepElements is cleared, so that change event client
        // will really get the contents.
        // THREAD: the event should synchronize into the correct model thread
        toComplete.getChangedEvent().fire();

        synchronized (BaseCompleter.this) {
            keepElements = null;
            f = feedbackToFinish;
            feedbackToFinish = null;
        }
        if (f != null) {
            f.finish();
        }
    }

    protected T load(ReadableByteChannel channel, int majorVersion, int minorVersion, Feedback feedback) throws IOException {
        return null;
    }

    protected Future<T> future() {
        return future;
    }

    Future<T> scheduleFetch(Feedback feedback) {
        LOG.log(Level.FINER, "Scheduling completion for {0}", name);
        return env.getFetchExecutor().schedule(new Worker(feedback), 0, TimeUnit.MILLISECONDS);
    }

    protected T createEmpty() {
        return null;
    }

    protected T hookData(T data) {
        return data;
    }

    @Override
    public boolean canComplete() {
        return !completingThread.get();
    }

    private final ThreadLocal<Boolean> completingThread = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    private class Worker implements Callable<T> {
        private final Feedback feedback;

        public Worker(Feedback feedback) {
            this.feedback = feedback;
        }

        private T invokeEvent(T newElements) {
            newElements = filter(newElements);
            future.complete(newElements);
            future = null;
            env.getFetchExecutor().schedule(BaseCompleter.this, 0, TimeUnit.MILLISECONDS);
            completingThread.remove();
            return newElements;
        }

        @Override
        @SuppressWarnings("UseSpecificCatch")
        public T call() throws Exception {
            T newElements;
            newElements = createEmpty();
            LOG.log(Level.FINER, "Reading group {0}, range {1}-{2}", new Object[]{name, entry.getStart(), entry.getEnd()});
            completingThread.set(true);
            try {
                newElements = load(env.getContent().subChannel(entry.getStart(), entry.getEnd()), entry.getMajorVersion(), entry.getMinorVersion(), feedback);
            } catch (InterruptedIOException ex) {
                future.cancel();
            } catch (ThreadDeath ex) {
                throw ex;
            } catch (Throwable ex) {
                LOG.log(Level.WARNING, "Error during completion of group " + name, ex);
                newElements = partialData();
            } finally {
                synchronized (BaseCompleter.this) {
                    keepElements = newElements;
                    LOG.log(Level.FINER, "Scheduling expansion of group  {0}", name);
                    invokeEvent(newElements);
                }
            }
            return newElements;
        }
    }

    /**
     * Wrapper for the Future, which keeps the whole FolderElement list in memory as long as at
     * least some item is alive.
     */
    class KeepDataFuture implements Future<T>, ChangedListener {
        private final Future<T> delegate;
        private volatile boolean done;
        private volatile T items;
        private volatile boolean cancel;

        public KeepDataFuture(Future<T> delegate) {
            this.delegate = delegate;
        }

        void complete(T data) {
            this.done = true;
            hookData(data);
            this.items = data;
        }

        void cancel() {
            this.cancel = true;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return cancel || delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return done && delegate.isDone();
        }

        public synchronized T tryGet() {
            if (isDone()) {
                return items;
            } else {
                return null;
            }
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            T res = delegate.get();
            synchronized (this) {
                items = res;
            }
            return res;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }

        @Override
        public void changed(Object source) {
        }
    }

    protected void setPartialData(T partialData) {
        synchronized (this) {
            this.partialData = partialData;
        }
        toComplete.getChangedEvent().fire();
    }

    @Override
    public T partialData() {
        KeepDataFuture f;
        T p;
        synchronized (this) {
            f = this.future;
            p = partialData;
        }
        if (f != null) {
            T x = future.tryGet();
            if (x != null) {
                return x;
            }
        }
        return p;
    }
}
