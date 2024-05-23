/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graphio.parsing.model;

import java.util.ArrayList;
import java.util.List;

public abstract class Event<L> {

    protected final List<L> listener;
    private volatile boolean fireEvents;
    private volatile boolean eventWasFired;

    public Event() {
        listener = new ArrayList<>();
        fireEvents = true;
    }

    public void addListener(L l) {
        listener.add(l);
    }

    public void removeListener(final L l) {
        listener.remove(l);
    }

    protected synchronized List<L> getListeners() {
        return new ArrayList<>(listener);
    }

    public void fire() {
        if (fireEvents) {
            List<L> tmpList = getListeners();
            for (L l : tmpList) {
                fire(l);
            }
        } else {
            eventWasFired = true;
        }
    }

    public synchronized void beginAtomic() {
        assert fireEvents : "endAtomic has to be called before another beginAtomic may be called";
        this.fireEvents = false;
        this.eventWasFired = false;
    }

    public synchronized void endAtomic() {
        assert !fireEvents : "beginAtomic has to be called first";
        this.fireEvents = true;
        if (eventWasFired) {
            fire();
        }
    }

    protected abstract void fire(L l);
}
