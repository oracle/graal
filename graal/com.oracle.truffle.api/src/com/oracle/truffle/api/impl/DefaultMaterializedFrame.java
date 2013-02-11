/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;

final class DefaultMaterializedFrame implements MaterializedFrame, PackedFrame {

    private final DefaultVirtualFrame wrapped;

    protected DefaultMaterializedFrame(DefaultVirtualFrame wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public Arguments getArguments() {
        return wrapped.getArguments();
    }

    @Override
    public Object getObject(FrameSlot slot) {
        return wrapped.getObject(slot);
    }

    @Override
    public void setObject(FrameSlot slot, Object value) {
        wrapped.setObject(slot, value);
    }

    @Override
    public boolean getBoolean(FrameSlot slot) {
        return wrapped.getBoolean(slot);
    }

    @Override
    public void setBoolean(FrameSlot slot, boolean value) {
        wrapped.setBoolean(slot, value);
    }

    @Override
    public int getInt(FrameSlot slot) {
        return wrapped.getInt(slot);
    }

    @Override
    public void setInt(FrameSlot slot, int value) {
        wrapped.setInt(slot, value);
    }

    @Override
    public long getLong(FrameSlot slot) {
        return wrapped.getLong(slot);
    }

    @Override
    public void setLong(FrameSlot slot, long value) {
        wrapped.setLong(slot, value);
    }

    @Override
    public float getFloat(FrameSlot slot) {
        return wrapped.getFloat(slot);
    }

    @Override
    public void setFloat(FrameSlot slot, float value) {
        wrapped.setFloat(slot, value);
    }

    @Override
    public double getDouble(FrameSlot slot) {
        return wrapped.getDouble(slot);
    }

    @Override
    public void setDouble(FrameSlot slot, double value) {
        wrapped.setDouble(slot, value);
    }

    @Override
    public void updateToLatestVersion() {
        wrapped.updateToLatestVersion();
    }

    @Override
    public PackedFrame pack() {
        return this;
    }

    @Override
    public MaterializedFrame materialize() {
        return this;
    }

    @Override
    public Frame unpack() {
        return this;
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return wrapped.getFrameDescriptor();
    }
}
