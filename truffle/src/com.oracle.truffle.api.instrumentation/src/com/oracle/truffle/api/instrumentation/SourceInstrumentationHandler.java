/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;

import com.oracle.truffle.api.source.Source;

/*
 * Lists of sources (executed or loaded, this description further mentions ony loaded sources,
 * the same holds for executed) are initialized lazily, and, to prevent deadlocks, new sources
 * can be loaded during this lazy initialization and also new bindings can be added at any time.
 * In order to guarantee that each binding is notified about each source at most once without
 * having to keep track of which binding was notified about which source, the notifications are
 * added to a queue and resolving the sources is postponed to a time the notification is
 * dequeued. The thread that adds the first notification to the queue is the one that will process them.
 * Therefore, the notifications are serialized and the same order of sources for each binding is
 * guaranteed. It is also guaranteed that each binding is notified about each source at most
 * once and if the binding is added with notify=true, it is notified about each binding
 * exactly once.
 */
final class SourceInstrumentationHandler {
    /*
     * Bindings can only be changed while holding the bindingsLock.writeLock() and read while
     * holding the bindingsLock.readLock(); The only exceptions are the hasBindings and
     * getBindingsArray methods which can be called without any locks.
     */
    private final InstrumentationHandler.CopyOnWriteList<EventBinding.Source<?>> bindings = new InstrumentationHandler.CopyOnWriteList<>(new EventBinding.Source<?>[0]);
    /*
     * sources, sourcesList, notifications can only be accessed while holding the
     * bindingsLock.writeLock() or while holding the bindingsLock.readLock() and sources lock.
     */
    private final WeakHashMap<Source, Void> sources = new WeakHashMap<>();
    private final InstrumentationHandler.WeakAsyncList<Source> sourcesList = new InstrumentationHandler.WeakAsyncList<>(16);
    private final AtomicBoolean sourcesInitialized = new AtomicBoolean();
    private final ReentrantReadWriteLock bindingsLock = new ReentrantReadWriteLock();
    private final BiConsumer<EventBinding.Source<?>[], Source> notificationConsumer;
    private SourcesNotificationQueue notifications = new SourcesNotificationQueue();

    SourceInstrumentationHandler(BiConsumer<EventBinding.Source<?>[], Source> notificationConsumer) {
        this.notificationConsumer = notificationConsumer;
    }

    private SourcesNotificationQueue addInitializeSourcesNotification() {
        assert bindingsLock.getWriteHoldCount() > 0;
        assert bindings.size() == 1;

        notifications.enqueue(new InitializeSourcesNotification());

        assert notifications.shouldProcess() : "Thread that added InitializeSourcesNotification is not the one to process the notification queue.";
        assert notifications.isSourcesInitializationRequired();

        return notifications;
    }

    private SourcesNotificationQueue addAllSourcesNotification(EventBinding.Source<?> binding) {
        assert bindingsLock.getWriteHoldCount() > 0;

        notifications.enqueue(new AllSourcesNotification(new EventBinding.Source<?>[]{binding}));
        if (notifications.shouldProcess()) {
            assert (!notifications.isSourcesInitializationRequired() && bindings.size() > 1) || (notifications.isSourcesInitializationRequired() && bindings.size() == 1);
            return notifications;
        } else {
            return null;
        }
    }

    private SourcesNotificationQueue addNotification(Map<Source, Void> collectedSources, EventBinding.Source<?>[] bindingsToNotify) {
        assert bindingsLock.getReadHoldCount() > 0;
        assert Thread.holdsLock(sources);
        assert !bindings.isEmpty();

        notifications.enqueue(new NewSourcesNotification(bindingsToNotify, collectedSources.keySet()));
        if (notifications.shouldProcess()) {
            assert !notifications.isSourcesInitializationRequired();
            return notifications;
        } else {
            return null;
        }
    }

    void setInitialized() {
        sourcesInitialized.set(true);
    }

    boolean hasBindings() {
        return !bindings.isEmpty();
    }

    EventBinding.Source<?>[] getBindingsArray() {
        return bindings.getArray();
    }

    void clearForDisposedBinding(EventBinding.Source<?> disposedBinding) {
        Lock lock = bindingsLock.writeLock();
        lock.lock();
        try {
            bindings.remove(disposedBinding);
            if (bindings.isEmpty()) {
                clearAllInternal();
            }
        } finally {
            lock.unlock();
        }
    }

    void clearForDisposedInstrumenter(InstrumentationHandler.AbstractInstrumenter disposedInstrumenter) {
        Lock lock = bindingsLock.writeLock();
        lock.lock();
        try {
            Collection<EventBinding<?>> disposedSourceLoadedBindings = InstrumentationHandler.filterBindingsForInstrumenter(bindings, disposedInstrumenter);
            InstrumentationHandler.disposeBindingsBulk(disposedSourceLoadedBindings);
            bindings.removeAll(disposedSourceLoadedBindings);
            if (bindings.isEmpty()) {
                clearAllInternal();
            }
        } finally {
            lock.unlock();
        }
    }

    void clearAll() {
        Lock lock = bindingsLock.writeLock();
        lock.lock();
        try {
            clearAllInternal();
        } finally {
            lock.unlock();
        }
    }

    private void clearAllInternal() {
        assert bindingsLock.getWriteHoldCount() > 0;
        bindings.clear();
        sources.clear();
        sourcesList.clear();
        sourcesInitialized.set(false);
        notifications.clear();
        notifications.invalidate();
        notifications = new SourcesNotificationQueue();
    }

    SourcesNotificationQueue addBinding(EventBinding.Source<?> binding, boolean notify) {
        SourcesNotificationQueue notificationsToProcess = null;
        Lock lock = bindingsLock.writeLock();
        lock.lock();
        try {
            boolean initializeSources = false;
            if (bindings.isEmpty()) {
                initializeSources = true;
            }
            bindings.add(binding);
            binding.attachedSemaphore.release();
            if (notify) {
                notificationsToProcess = addAllSourcesNotification(binding);
            } else if (initializeSources) {
                notificationsToProcess = addInitializeSourcesNotification();
            }
        } finally {
            lock.unlock();
        }

        return notificationsToProcess;
    }

    SourcesNotificationQueue addNewSources(Map<Source, Void> newSources, boolean notify) {
        SourcesNotificationQueue notificationsToProcess = null;
        Lock lock = bindingsLock.readLock();
        lock.lock();
        try {
            if (!bindings.isEmpty()) {
                synchronized (sources) {
                    if (notify) {
                        notificationsToProcess = addNotification(newSources, bindings.getArray());
                    } else {
                        for (Source src : newSources.keySet()) {
                            if (!sources.containsKey(src)) {
                                sources.put(src, null);
                                sourcesList.add(src);
                            }
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }

        return notificationsToProcess;
    }

    final class SourcesNotificationQueue {
        private boolean sourcesInitializationRequired;
        private boolean valid = true;
        private final Deque<SourcesNotification> notificationQueue = new ArrayDeque<>();

        SourcesNotificationQueue() {
            this.sourcesInitializationRequired = true;
        }

        private boolean shouldProcess() {
            assert bindingsLock.getWriteHoldCount() > 0 || (bindingsLock.getReadHoldCount() > 0 && Thread.holdsLock(sources));
            return notificationQueue.size() == 1;
        }

        private void enqueue(SourcesNotification notification) {
            assert bindingsLock.getWriteHoldCount() > 0 || (bindingsLock.getReadHoldCount() > 0 && Thread.holdsLock(sources));
            notificationQueue.add(notification);
        }

        private SourcesNotification resolveFirst() {
            SourcesNotification notification = null;
            Lock lock = bindingsLock.readLock();
            lock.lock();
            try {
                if (valid) {
                    synchronized (sources) {
                        notification = notificationQueue.peekFirst();
                        sourcesInitializationRequired = false;
                        if (notification != null) {
                            notification.resolveSources();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
            return notification;
        }

        private boolean removeFirst() {
            boolean queueNotEmpty = false;
            Lock lock = bindingsLock.readLock();
            lock.lock();
            try {
                if (valid) {
                    synchronized (sources) {
                        notificationQueue.removeFirst();
                        if (!notificationQueue.isEmpty()) {
                            queueNotEmpty = true;
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
            return queueNotEmpty;
        }

        private void clear() {
            assert bindingsLock.getWriteHoldCount() > 0;
            notificationQueue.clear();
        }

        private void invalidate() {
            assert bindingsLock.getWriteHoldCount() > 0;
            valid = false;
        }

        void process() {
            do {
                SourcesNotification notification = resolveFirst();
                if (notification != null) {
                    try {
                        notification.runNotifications();
                    } catch (Throwable t) {
                        Lock writeLock = bindingsLock.writeLock();
                        writeLock.lock();
                        try {
                            clear();
                        } finally {
                            writeLock.unlock();
                        }
                        throw t;
                    }
                }
                /*
                 * The iteration has to be stopped as soon as the last element of the queue is
                 * removed and the element that is being processed has to be kept in the queue until
                 * its processing is complete, otherwise other threads could start processing the
                 * queue, because the thread that adds the first element to the queue processes the
                 * queue, and this thread could continue processing it too, if it tried to call
                 * resolveFirst after it removed the last element and other thread added another
                 * element.
                 */
            } while (removeFirst());
        }

        boolean isSourcesInitializationRequired() {
            return sourcesInitializationRequired;
        }
    }

    private abstract static class SourcesNotification {
        protected final AtomicBoolean sourcesResolved = new AtomicBoolean();
        protected final AtomicBoolean notificationsRun = new AtomicBoolean();

        protected abstract void resolveSources();

        protected abstract void runNotifications();
    }

    /*
     * This notification does not notify about any sources, by being in the queue (it is always the
     * first element in the queue) it makes sure the thread that added it is the one to process the
     * notifications. If by that time there are more notifications in the queue, sources will be
     * already initialized when these notifications are processed.
     */
    private class InitializeSourcesNotification extends SourcesNotification {
        @Override
        protected void runNotifications() {
            boolean firstCall = notificationsRun.compareAndSet(false, true);
            assert firstCall : "runNotifications called more than once.";
        }

        @Override
        protected void resolveSources() {
            assert bindingsLock.getReadHoldCount() > 0;
            assert Thread.holdsLock(sources);
            assert sourcesInitialized.get();
            boolean firstCall = sourcesResolved.compareAndSet(false, true);
            assert firstCall : "resolveSources called more than once.";
        }
    }

    /*
     * This notification notifies about all sources in the sourcesList at the time the sources for
     * this notification are resolved.
     */
    private class AllSourcesNotification extends InitializeSourcesNotification {
        protected final EventBinding.Source<?>[] bindingsToNotify;
        protected Collection<Source> sourcesForNotification;

        AllSourcesNotification(EventBinding.Source<?>[] bindingsToNotify) {
            this.bindingsToNotify = bindingsToNotify;
        }

        @Override
        protected void resolveSources() {
            assert bindingsLock.getReadHoldCount() > 0;
            assert Thread.holdsLock(sources);
            assert sourcesInitialized.get();
            boolean firstCall = sourcesResolved.compareAndSet(false, true);
            assert firstCall : "resolveSources called more than once.";

            sourcesForNotification = new ArrayList<>(sourcesList.getNextInsertionIndex());
            for (Source source : sourcesList) {
                sourcesForNotification.add(source);
            }
        }

        @Override
        protected final void runNotifications() {
            assert bindingsLock.getReadHoldCount() + bindingsLock.getWriteHoldCount() == 0;
            boolean firstCall = notificationsRun.compareAndSet(false, true);
            assert firstCall : "runNotifications called more than once.";

            if (sourcesForNotification != null) {
                for (Source src : sourcesForNotification) {
                    notificationConsumer.accept(bindingsToNotify, src);
                }
            }
        }

    }

    /*
     * This notification notifies about all sources in the collectedSources that are not in the
     * sourcesList already at the time sources for this notification are resolved.
     */
    private class NewSourcesNotification extends AllSourcesNotification {
        protected final Collection<Source> collectedSources;

        NewSourcesNotification(EventBinding.Source<?>[] bindingsToNotify, Collection<Source> collectedSources) {
            super(bindingsToNotify);
            this.collectedSources = collectedSources;
        }

        @Override
        protected void resolveSources() {
            assert bindingsLock.getReadHoldCount() > 0;
            assert Thread.holdsLock(sources);
            assert sourcesInitialized.get();
            boolean firstCall = sourcesResolved.compareAndSet(false, true);
            assert firstCall : "resolveSources called more than once.";

            sourcesForNotification = new ArrayList<>();
            for (Source src : collectedSources) {
                if (!sources.containsKey(src)) {
                    sources.put(src, null);
                    sourcesList.add(src);
                    sourcesForNotification.add(src);
                }
            }
        }
    }
}
