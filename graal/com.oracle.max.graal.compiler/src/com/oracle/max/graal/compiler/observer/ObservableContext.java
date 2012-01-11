/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.observer;

import java.util.*;

/**
 * Base class for compilers that notify subscribed {@link CompilationObserver CompilationObservers} of
 * {@link CompilationEvent CompilationEvents} that occur during their compilations.
 */
public class ObservableContext {

    private List<CompilationObserver> observers;

    private ThreadLocal<StringBuilder> scopeName = new ThreadLocal<StringBuilder>() {
        @Override
        protected StringBuilder initialValue() {
            return new StringBuilder();
        }
    };

    private ThreadLocal<ArrayList<Object>> debugObjects = new ThreadLocal<ArrayList<Object>>() {
        @Override
        protected ArrayList<Object> initialValue() {
            return new ArrayList<>();
        }
    };

    /**
     * @return {@code true} if one or more observers are subscribed to receive notifications from this compiler,
     *         {@code false} otherwise.
     */
    public boolean isObserved() {
        return observers != null;
    }

    /**
     * Add the specified observer to receive events from this compiler.
     *
     * @param observer The observer to add.
     */
    public void addCompilationObserver(CompilationObserver observer) {
        assert observer != null;

        if (observers == null) {
            observers = new LinkedList<>();
        }
        observers.add(observer);
    }

    public void fireCompilationStarted(Object... additionalDebugObjects) {
        if (isObserved()) {
            addDebugObjects(null, additionalDebugObjects);
            CompilationEvent event = new CompilationEvent("started", debugObjects.get());
            for (CompilationObserver observer : observers) {
                observer.compilationStarted(event);
            }
            removeDebugObjects(null, additionalDebugObjects);
        }
    }

    public void fireCompilationEvent(String label, Object... additionalDebugObjects) {
        if (isObserved()) {
            addDebugObjects(null, additionalDebugObjects);
            CompilationEvent event = new CompilationEvent(label, debugObjects.get());
            for (CompilationObserver observer : observers) {
                observer.compilationEvent(event);
            }
            removeDebugObjects(null, additionalDebugObjects);
        }
    }

    public void fireCompilationFinished(Object... additionalDebugObjects) {
        if (isObserved()) {
            addDebugObjects(null, additionalDebugObjects);
            CompilationEvent event = new CompilationEvent("finished", debugObjects.get());
            for (CompilationObserver observer : observers) {
                observer.compilationFinished(event);
            }
            removeDebugObjects(null, additionalDebugObjects);
        }
    }

    /**
     * Remove the specified observer so that it no longer receives events from this compiler.
     *
     * @param observer The observer to remove.
     */
    public void removeCompilationObserver(CompilationObserver observer) {
        if (observers != null) {
            observers.remove(observer);
            if (observers.size() == 0) {
                observers = null;
            }
        }
    }

    public void clear() {
        if (observers != null) {
            observers = null;
        }
    }

    public void addDebugObjects(String name, Object[] additionalDebugObjects) {
        if (name != null) {
            if (scopeName.get().length() > 0) {
                scopeName.get().append('.');
            }
            scopeName.get().append(name);
        }
        for (Object obj : additionalDebugObjects) {
            debugObjects.get().add(obj);
        }
    }

    public void removeDebugObjects(String name, Object[] additionalDebugObjects) {
        if (name != null) {
            scopeName.get().setLength(Math.max(0, scopeName.get().length() - name.length()));
        }
        for (int i = 0; i < additionalDebugObjects.length; i++) {
            debugObjects.get().remove(debugObjects.get().size() - 1);
        }
    }
}
