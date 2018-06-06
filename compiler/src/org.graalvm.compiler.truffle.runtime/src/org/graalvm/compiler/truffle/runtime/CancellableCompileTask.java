/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Future;

import org.graalvm.compiler.nodes.Cancellable;

public class CancellableCompileTask implements Cancellable {
    Future<?> future = null;
    boolean cancelled = false;

    // This cannot be done in the constructor because the CancellableCompileTask needs to be
    // passed down to the compiler through a Runnable inner class.
    // This means it must be final and initialized before the future can be set.
    public synchronized void setFuture(Future<?> future) {
        if (this.future == null) {
            this.future = future;
        } else {
            throw new IllegalStateException("The future should not be re-set.");
        }
    }

    public synchronized Future<?> getFuture() {
        return future;
    }

    @Override
    public synchronized boolean isCancelled() {
        assert future != null;
        assert !cancelled || future.isCancelled();
        return cancelled;
    }

    public synchronized void cancel() {
        if (!cancelled) {
            assert future != null;
            cancelled = true;
            if (future != null) {
                assert !future.isCancelled();
                // should assert future.cancel(false)=true but future might already finished between
                // the cancelled=true write and the call to cancel(false)
                future.cancel(false);
            }
        }
    }

    public boolean isRunning() {
        assert future != null;
        return !(future.isDone() || future.isCancelled());
    }
}
