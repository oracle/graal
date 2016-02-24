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

/**
 * Provides the capabilities to attach {@link ExecutionEventNodeFactory} and
 * {@link ExecutionEventListener} instances for a set of source locations specified by a
 * {@link SourceSectionFilter}. The result of an attachment is a {@link EventBinding binding}.
 *
 * @see #attachFactory(SourceSectionFilter, ExecutionEventNodeFactory)
 * @see #attachListener(SourceSectionFilter, ExecutionEventListener)
 */
public abstract class Instrumenter {

    Instrumenter() {
    }

    /**
     * Starts event notification for a given {@link ExecutionEventNodeFactory factory} and returns a
     * {@link EventBinding binding} which represents a handle to dispose the notification.
     */
    public abstract <T extends ExecutionEventNodeFactory> EventBinding<T> attachFactory(SourceSectionFilter filter, T factory);

    /**
     * Starts event notification for a given {@link ExecutionEventListener listener} and returns a
     * {@link EventBinding binding} which represents a handle to dispose the notification.
     */
    public abstract <T extends ExecutionEventListener> EventBinding<T> attachListener(SourceSectionFilter filter, T listener);

}
