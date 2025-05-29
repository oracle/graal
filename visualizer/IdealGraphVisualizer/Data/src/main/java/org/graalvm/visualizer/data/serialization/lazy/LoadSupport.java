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

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.Group.Feedback;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Support class handling synchronization and caching of the obtained result.
 */
class LoadSupport<T> implements Group.LazyContent<T> {
    private static final Logger LOG = Logger.getLogger(LoadSupport.class.getName());
    private static final Reference EMPTY = new WeakReference(null);

    // access by testing code
    final Completer<T> completer;

    /**
     * For testing only, prevents nondeterministic behaviour of softrefs
     */
    static boolean _testUseWeakRefs;

    private volatile Reference<Future<T>> processing = EMPTY;
    private String name;

    public LoadSupport(Completer<T> completer) {
        this.completer = completer;
    }

    /**
     * Sets name of the completed object. Used for diagnostic purposes.
     *
     * @param name display name
     */
    void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean isComplete() {
        if (completer == null) {
            return true;
        }
        // pretend we're done
        if (!completer.canComplete()) {
            return true;
        }
        Future<T> f = processing.get();
        return f != null && f.isDone();
    }

    @Override
    public T partialData() {
        Future<T> f = processing.get();
        T data;
        if (completer == null) {
            data = emptyData();
        } else {
            data = completer.partialData();
            if (data == null) {
                data = emptyData();
            }
        }
        if (f != null && f.isDone()) {
            // rather get future's final result, check for .isDone() must be done after
            // partial query
            try {
                return f.get();
            } catch (InterruptedException | ExecutionException | CancellationException ex) {
                Logger.getLogger(LoadSupport.class.getName()).log(Level.SEVERE, null, ex);
                return emptyData();
            }
        }
        return data;
    }

    private Reference<Future> getContentsRef = new WeakReference<>(null);

    /**
     * Returns the lazy-loaded contents, or partial contents. The first call to
     * getContents will block. Subsequent calls will yield partial results; clients
     * are required to observe change event to receive additional results.
     *
     * @return possibly partial contents
     */
    public T getContents() {
        try {
            Future<T> wait;
            synchronized (this) {
                if (completer != null && !completer.canComplete()) {
                    // if cannot complete, e.g. it's in the completion thread itself,
                    // attempt to get at least partial data
                    T x = completer.partialData();
                    if (x != null) {
                        return x;
                    } else {
                        return emptyData();
                    }
                }
                Future<T> cur = processing.get();
                wait = completeContents(null);
                if (!wait.isDone()) {
                    // HACK: first attempt to blindly getContents will block on the future.
                    // After computation launches (cur == wait), other attempts will try to return at least
                    // partial data, if the completer is willing to produce it.
                    if (cur == wait) {
                        if (completer != null) {
                            T x = completer.partialData();
                            if (x != null) {
                                return x;
                            }
                        }
//                    } else if (cur == null) {
//                        getContentsRef = new WeakReference<>(wait);
                    }
                }
            }
            return wait.get();
        } catch (InterruptedException | ExecutionException ex) {
            LOG.log(Level.WARNING, "Exception during expansion of group " + name, ex);
        }
        LOG.log(Level.FINE, "Group " + name + " contents incomplete, return empty");
        return emptyData();
    }

    @Override
    public synchronized Future<T> completeContents(Feedback feedback) {
        Future<T> f = processing.get();
        if (f == null) {
            if (completer == null) {
                CompletableFuture<T> c = new CompletableFuture<>();
                c.complete(emptyData());
                f = c;
                processing = new SoftReference(processing) {
                    // keep forever
                    Future x = c;
                };
                LOG.log(Level.FINE, "No completer, provide empty contents");
            } else {
                if (completer.canComplete()) {
                    f = completer.completeContents(feedback);
                } else {
                    CompletableFuture<T> c = new CompletableFuture<>();
                    c.complete(emptyData());
                    // do not cache
                    return c;
                }
            }
            LOG.log(Level.FINE, "Contents of group " + name + " not available, scheduling fetch");
            this.processing = _testUseWeakRefs ? new WeakReference<>(f) : new SoftReference<>(f);
        }
        return f;
    }

    /**
     * Provides empty data for the case the completer cannot complete, or an error occurs. The
     * method may provide an immutable shared instance.
     *
     * @return empty data object instance
     */
    protected T emptyData() {
        return null;
    }
}
