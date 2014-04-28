/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.hotspot;

import com.oracle.graal.compiler.common.*;
import com.oracle.truffle.api.frame.*;

class ReadOnlyFrame implements Frame {
    private final Frame delegate;

    public ReadOnlyFrame(Frame delegate) {
        this.delegate = delegate;
    }

    public FrameDescriptor getFrameDescriptor() {
        return delegate.getFrameDescriptor();
    }

    public Object[] getArguments() {
        return delegate.getArguments().clone();
    }

    public Object getObject(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getObject(slot);
    }

    public void setObject(FrameSlot slot, Object value) {
        throw GraalInternalError.shouldNotReachHere();
    }

    public byte getByte(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getByte(slot);
    }

    public void setByte(FrameSlot slot, byte value) {
        throw GraalInternalError.shouldNotReachHere();
    }

    public boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getBoolean(slot);
    }

    public void setBoolean(FrameSlot slot, boolean value) {
        throw GraalInternalError.shouldNotReachHere();
    }

    public int getInt(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getInt(slot);
    }

    public void setInt(FrameSlot slot, int value) {
        throw GraalInternalError.shouldNotReachHere();
    }

    public long getLong(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getLong(slot);
    }

    public void setLong(FrameSlot slot, long value) {
        throw GraalInternalError.shouldNotReachHere();
    }

    public float getFloat(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getFloat(slot);
    }

    public void setFloat(FrameSlot slot, float value) {
        throw GraalInternalError.shouldNotReachHere();
    }

    public double getDouble(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getDouble(slot);
    }

    public void setDouble(FrameSlot slot, double value) {
        throw GraalInternalError.shouldNotReachHere();
    }

    public Object getValue(FrameSlot slot) {
        return delegate.getValue(slot);
    }

    public MaterializedFrame materialize() {
        throw GraalInternalError.shouldNotReachHere();
    }

    public boolean isObject(FrameSlot slot) {
        return delegate.isObject(slot);
    }

    public boolean isByte(FrameSlot slot) {
        return delegate.isByte(slot);
    }

    public boolean isBoolean(FrameSlot slot) {
        return delegate.isBoolean(slot);
    }

    public boolean isInt(FrameSlot slot) {
        return delegate.isInt(slot);
    }

    public boolean isLong(FrameSlot slot) {
        return delegate.isLong(slot);
    }

    public boolean isFloat(FrameSlot slot) {
        return delegate.isFloat(slot);
    }

    public boolean isDouble(FrameSlot slot) {
        return delegate.isDouble(slot);
    }
}