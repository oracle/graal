/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Pure delegate implementation to ForkJoinPool.commonPool().
 */
public class DeferredCommonPool extends ForkJoinPool {

    public DeferredCommonPool() {
        super(1, new DisallowingForkJoinWorkerThreadFactory(), null, false);
    }

    @Override
    public <T> T invoke(ForkJoinTask<T> task) {
        return ForkJoinPool.commonPool().invoke(task);
    }

    @Override
    public void execute(ForkJoinTask<?> task) {
        ForkJoinPool.commonPool().execute(task);
    }

    @Override
    public void execute(Runnable task) {
        ForkJoinPool.commonPool().execute(task);
    }

    @Override
    public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
        return ForkJoinPool.commonPool().submit(task);
    }

    @Override
    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        return ForkJoinPool.commonPool().submit(task);
    }

    @Override
    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        return ForkJoinPool.commonPool().submit(task, result);
    }

    @Override
    public ForkJoinTask<?> submit(Runnable task) {
        return ForkJoinPool.commonPool().submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        return ForkJoinPool.commonPool().invokeAll(tasks);
    }

    @Override
    public ForkJoinWorkerThreadFactory getFactory() {
        return ForkJoinPool.commonPool().getFactory();
    }

    @Override
    public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return ForkJoinPool.commonPool().getUncaughtExceptionHandler();
    }

    @Override
    public int getParallelism() {
        return ForkJoinPool.commonPool().getParallelism();
    }

    @Override
    public int getPoolSize() {
        return ForkJoinPool.commonPool().getPoolSize();
    }

    @Override
    public boolean getAsyncMode() {
        return ForkJoinPool.commonPool().getAsyncMode();
    }

    @Override
    public int getRunningThreadCount() {
        return ForkJoinPool.commonPool().getRunningThreadCount();
    }

    @Override
    public int getActiveThreadCount() {
        return ForkJoinPool.commonPool().getActiveThreadCount();
    }

    @Override
    public boolean isQuiescent() {
        return ForkJoinPool.commonPool().isQuiescent();
    }

    @Override
    public long getStealCount() {
        return ForkJoinPool.commonPool().getStealCount();
    }

    @Override
    public long getQueuedTaskCount() {
        return ForkJoinPool.commonPool().getQueuedTaskCount();
    }

    @Override
    public int getQueuedSubmissionCount() {
        return ForkJoinPool.commonPool().getQueuedSubmissionCount();
    }

    @Override
    public boolean hasQueuedSubmissions() {
        return ForkJoinPool.commonPool().hasQueuedSubmissions();
    }

    @Override
    public String toString() {
        return ForkJoinPool.commonPool().toString();
    }

    @Override
    public void shutdown() {
        ForkJoinPool.commonPool().shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return ForkJoinPool.commonPool().shutdownNow();
    }

    @Override
    public boolean isTerminated() {
        return ForkJoinPool.commonPool().isTerminated();
    }

    @Override
    public boolean isTerminating() {
        return ForkJoinPool.commonPool().isTerminating();
    }

    @Override
    public boolean isShutdown() {
        return ForkJoinPool.commonPool().isShutdown();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return ForkJoinPool.commonPool().awaitTermination(timeout, unit);
    }

    @Override
    public boolean awaitQuiescence(long timeout, TimeUnit unit) {
        return ForkJoinPool.commonPool().awaitQuiescence(timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return ForkJoinPool.commonPool().invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return ForkJoinPool.commonPool().invokeAny(tasks, timeout, unit);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return ForkJoinPool.commonPool().invokeAll(tasks, timeout, unit);
    }

    static class DisallowingForkJoinWorkerThreadFactory implements ForkJoinWorkerThreadFactory {
        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            throw new Error("Deferred ForkJoin.commonPool() delegate should not start its own threads.");
        }
    }
}
