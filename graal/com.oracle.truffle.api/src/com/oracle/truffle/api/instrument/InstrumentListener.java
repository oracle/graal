/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument;

/**
 * A listener of Truffle execution events that can collect information on behalf of an external
 * tool. Contextual information about the source of the event, if not stored in the implementation
 * of the listener, can be obtained via access to the {@link Probe} that generates the event.
 */
public interface InstrumentListener {

    /**
     * Receive notification that an AST node's execute method is about to be called.
     */
    void enter(Probe probe);

    /**
     * Receive notification that an AST Node's {@code void}-valued execute method has just returned.
     */
    void returnVoid(Probe probe);

    /**
     * Receive notification that an AST Node's execute method has just returned a value (boxed if
     * primitive).
     */
    void returnValue(Probe probe, Object result);

    /**
     * Receive notification that an AST Node's execute method has just thrown an exception.
     */
    void returnExceptional(Probe probe, Exception exception);
}
