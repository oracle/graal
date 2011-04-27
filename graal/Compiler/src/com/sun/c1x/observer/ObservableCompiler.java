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
package com.sun.c1x.observer;

import java.util.*;

import com.sun.c1x.debug.*;

/**
 * Base class for compilers that notify subscribed {@link CompilationObserver CompilationObservers} of
 * {@link CompilationEvent CompilationEvents} that occur during their compilations.
 *
 * @author Peter Hofer
 */
public class ObservableCompiler {

    private List<CompilationObserver> observers;

    /**
     * @return {@code true} if one or more observers are subscribed to receive notifications from this compiler,
     *         {@code false} otherwise.
     */
    public boolean isObserved() {
        return observers != null && !TTY.isSuppressed();
    }

    /**
     * Add the specified observer to receive events from this compiler.
     *
     * @param observer The observer to add.
     */
    public void addCompilationObserver(CompilationObserver observer) {
        assert observer != null;

        if (observers == null) {
            observers = new LinkedList<CompilationObserver>();
        }
        observers.add(observer);
    }

    public void fireCompilationStarted(CompilationEvent event) {
        if (isObserved()) {
            for (CompilationObserver observer : observers) {
                observer.compilationStarted(event);
            }
        }
    }

    public void fireCompilationEvent(CompilationEvent event) {
        if (isObserved()) {
            for (CompilationObserver observer : observers) {
                observer.compilationEvent(event);
            }
        }
    }

    public void fireCompilationFinished(CompilationEvent event) {
        if (isObserved()) {
            for (CompilationObserver observer : observers) {
                observer.compilationFinished(event);
            }
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

}
