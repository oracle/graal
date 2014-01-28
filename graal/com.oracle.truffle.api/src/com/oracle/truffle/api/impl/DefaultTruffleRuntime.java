/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Default implementation of the Truffle runtime if the virtual machine does not provide a better
 * performing alternative.
 * <p>
 * This is an implementation-specific class. Do not use or instantiate it. Instead, use
 * {@link Truffle#getRuntime()} to retrieve the current {@link TruffleRuntime}.
 */
public final class DefaultTruffleRuntime implements TruffleRuntime {

    public DefaultTruffleRuntime() {
        if (Truffle.getRuntime() != null) {
            throw new IllegalArgumentException("Cannot instantiate DefaultTruffleRuntime. Use Truffle.getRuntime() instead.");
        }
    }

    @Override
    public String getName() {
        return "Default Truffle Runtime";
    }

    @Override
    public RootCallTarget createCallTarget(RootNode rootNode) {
        return new DefaultCallTarget(rootNode);
    }

    @Override
    public VirtualFrame createVirtualFrame(PackedFrame caller, Arguments arguments, FrameDescriptor frameDescriptor) {
        return new DefaultVirtualFrame(frameDescriptor, caller, arguments);
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Arguments arguments) {
        return createMaterializedFrame(arguments, new FrameDescriptor());
    }

    @Override
    public MaterializedFrame createMaterializedFrame(Arguments arguments, FrameDescriptor frameDescriptor) {
        return new DefaultMaterializedFrame(new DefaultVirtualFrame(frameDescriptor, null, arguments));
    }

    @Override
    public Assumption createAssumption() {
        return createAssumption(null);
    }

    @Override
    public Assumption createAssumption(String name) {
        return new DefaultAssumption(name);
    }
}
