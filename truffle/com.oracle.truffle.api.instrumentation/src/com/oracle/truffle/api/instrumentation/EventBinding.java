/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.AbstractInstrumenter;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.LanguageInstrumenter;

/**
 * Represents a binding from a {@link SourceSectionFilter} instance for a particular
 * {@link EventListener} or {@link EventNodeFactory}.
 *
 * A binding is active until disposed, either:
 * <ul>
 * <li>
 * by a call to dispose();</li>
 * <li>the instrumenter that created the binding is disposed; or</li>
 * <li>the PolyglotEngine that contains the instrumenter is disposed.</li>
 * </ul>
 * If all bindings of a listener or factory are disposed then their methods are not invoked again by
 * the instrumentation framework.
 *
 * @param <T> represents the concrete type of the element bound. Either an implementation of
 *            {@link EventListener} or {@link EventNodeFactory}.
 */
public final class EventBinding<T> {

    private final AbstractInstrumenter instrumenter;
    private final SourceSectionFilter filter;
    private final T element;

    /* language bindings needs special treatment. */
    private boolean disposed;

    EventBinding(AbstractInstrumenter instrumenter, SourceSectionFilter query, T element) {
        this.instrumenter = instrumenter;
        this.filter = query;
        this.element = element;
    }

    boolean isLanguageBinding() {
        return instrumenter instanceof LanguageInstrumenter;
    }

    AbstractInstrumenter getInstrumenter() {
        return instrumenter;
    }

    /**
     * Returns the bound element, either a {@link EventNodeFactory factory} or a
     * {@link EventListener listener} implementation.
     */
    public T getElement() {
        return element;
    }

    /**
     * Returns the bound filter for this binding.
     *
     * @return the filter never null
     */
    public SourceSectionFilter getFilter() {
        return filter;
    }

    /**
     * Returns <code>true</code> if the binding was already disposed, otherwise <code>false</code>.
     */
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Disposes this binding. If a binding of a listener or factory is disposed then their methods
     * are not invoked again by the instrumentation framework.
     */
    public void dispose() throws IllegalStateException {
        CompilerAsserts.neverPartOfCompilation();
        if (disposed) {
            throw new IllegalStateException("Bindings can only be disposed once");
        }
        instrumenter.disposeBinding(this);
        this.disposed = true;
    }

}
