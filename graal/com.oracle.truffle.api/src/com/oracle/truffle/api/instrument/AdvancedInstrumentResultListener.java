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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Listener for receiving the result a client-provided {@linkplain AdvancedInstrumentRoot AST
 * fragment}, when executed by a
 * {@linkplain Instrument#create(AdvancedInstrumentRootFactory, String) Advanced Instrument}.
 *
 * @see Instrument
 * @see AdvancedInstrumentRoot
 * @see AdvancedInstrumentRootFactory
 */
public interface AdvancedInstrumentResultListener {

    /**
     * Notifies listener that a client-provided {@linkplain AdvancedInstrumentRoot AST fragment} has
     * been executed by an {@linkplain Instrument#create(AdvancedInstrumentRootFactory, String)
     * Advanced Instrument} with the specified result, possibly {@code null}.
     * <p>
     * <strong>Note: </strong> Truffle will attempt to optimize implementations through partial
     * evaluation; annotate with {@link TruffleBoundary} if this should not be permitted.
     * 
     * @param node the guest-language AST node to which the host Instrument's {@link Probe} is
     *            attached
     * @param vFrame execution frame at the guest-language AST node
     * @param result the result of this AST fragment's execution
     */
    void notifyResult(Node node, VirtualFrame vFrame, Object result);

    /**
     * Notifies listener that execution of client-provided {@linkplain AdvancedInstrumentRoot AST
     * fragment} filed during execution by a @linkplain
     * Instrument#create(AdvancedInstrumentRootFactory, String) Advanced Instrument}.
     * <p>
     * <strong>Note: </strong> Truffle will attempt to optimize implementations through partial
     * evaluation; annotate with {@link TruffleBoundary} if this should not be permitted.
     *
     * @param node the guest-language AST node to which the host Instrument's {@link Probe} is
     *            attached
     * @param vFrame execution frame at the guest-language AST node
     * @param ex the exception
     */
    void notifyFailure(Node node, VirtualFrame vFrame, RuntimeException ex);

}
