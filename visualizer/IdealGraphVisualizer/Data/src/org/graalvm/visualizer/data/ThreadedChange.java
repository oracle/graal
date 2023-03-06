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

package org.graalvm.visualizer.data;

import java.util.List;
import java.util.concurrent.Executor;


/**
 * Specific subclass of event, which sends itself in a dedicate {@link #runner} executor.
 * Used by document/group model to send out content change events, so that the delivery
 * is synchronized and potentially asynchronous to the mutation operation itself.
 * @author sdedic
 */
public final class ThreadedChange<T> extends ChangedEvent<T> {
    private final Executor runner;
    
    public ThreadedChange(T object, Executor runner) {
        super(object);
        this.runner = runner;
    }
    
    @Override
    public synchronized void addListener(final ChangedListener<T> l) {
        super.addListener(l);
    }

    @Override
    public synchronized void removeListener(final ChangedListener<T> l) {
        super.removeListener(l);
    }

    @Override
    protected synchronized List<ChangedListener<T>> getListeners() {
        return super.getListeners();
    }

    @Override
    public void fire() {
        fireWith(runner);
    }
    
    public void fireWith(Executor e) {
        e.execute(super::fire);
    }
}
