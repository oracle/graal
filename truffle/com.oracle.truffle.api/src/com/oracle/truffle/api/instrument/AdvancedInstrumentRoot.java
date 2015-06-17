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

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Root of a client-provided AST fragment that can be executed efficiently, subject to full Truffle
 * optimization, by an
 * {@linkplain Instrument#create(AdvancedInstrumentResultListener, AdvancedInstrumentRootFactory, Class, String)
 * Advanced Instrument}.
 *
 * @see Instrument
 * @see AdvancedInstrumentRootFactory
 * @see AdvancedInstrumentResultListener
 */
public abstract class AdvancedInstrumentRoot extends Node implements InstrumentationNode {

    /**
     * Executes this AST fragment on behalf of a client {@link Instrument}, just before the
     * guest-language AST node to which the {@link Probe} holding the Instrument is executed.
     *
     * @param node the guest-language AST node to which the host Instrument's Probe is attached
     * @param vFrame execution frame at the guest-language AST node
     * @return the result of this AST fragment's execution
     */
    public abstract Object executeRoot(Node node, VirtualFrame vFrame);

}
