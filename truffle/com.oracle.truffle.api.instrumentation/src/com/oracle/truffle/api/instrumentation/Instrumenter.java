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
 * Interface to the runtime system to attach {@link EventBinding bindings} from
 * {@link EventNodeFactory} and {@link EventListener} instances to a set of runtime events specified
 * by a {@link SourceSectionFilter}.
 *
 * When the {@link TruffleInstrument instrument} which has passed the {@link Instrumenter
 * instrumenter} is {@link TruffleInstrument#onDispose(TruffleInstrument.Env) disposed}, then all
 * bindings it has created are disposed automatically.
 */
public abstract class Instrumenter {

    Instrumenter() {
    }

    /**
     * Attaches a {@link EventNodeFactory factory} to the current runtime system and returns a
     * {@link EventBinding binding} which represents a handle to the attachment.
     */
    public abstract <T extends EventNodeFactory> EventBinding<T> attachFactory(SourceSectionFilter filter, T factory);

    /**
     * Attaches a {@link EventListener listener} to the current runtime system and returns a
     * {@link EventBinding binding} which represents a handle to the attachment.
     */
    public abstract <T extends EventListener> EventBinding<T> attachListener(SourceSectionFilter filter, T listener);

}
