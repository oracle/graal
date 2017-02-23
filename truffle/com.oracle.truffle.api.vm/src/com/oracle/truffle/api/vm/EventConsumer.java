/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

/**
 * Obsoleted.
 *
 * @param <Event> type of event to observe and handle
 * @since 0.9
 * @deprecated use {@link com.oracle.truffle.api.debug.Debugger Debugger}
 *             .find(engine).startSession(new SuspendedCallback(){...}).suspendNext() instead
 */
@Deprecated
public abstract class EventConsumer<Event> {
    final Class<Event> type;

    /**
     * Creates new handler for specified event type.
     *
     * @param eventType type of events to handle
     * @since 0.9
     */
    public EventConsumer(Class<Event> eventType) {
        this.type = eventType;
    }

    /**
     * Called by the {@link PolyglotEngine} when event of requested type appears.
     *
     * @param event the instance of an event of the request type
     * @since 0.9
     */
    protected abstract void on(Event event);
}
