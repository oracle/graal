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
package com.oracle.truffle.api.frame;

import com.oracle.truffle.api.*;

/**
 * Represents a frame containing values of local variables of the guest language. Instances of this
 * type must not be stored in a field or cast to {@link java.lang.Object}.
 */
public interface Frame {

    /**
     * @return the arguments used when calling this method
     */
    Arguments getArguments();

    /**
     * Read access to a local variable of type {@link Object}.
     * 
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     */
    Object getObject(FrameSlot slot);

    /**
     * Write access to a local variable of type {@link Object}.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    void setObject(FrameSlot slot, Object value);

    /**
     * Read access to a local variable of type boolean.
     * 
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     */
    boolean getBoolean(FrameSlot slot);

    /**
     * Write access to a local variable of type boolean.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    void setBoolean(FrameSlot slot, boolean value);

    /**
     * Read access to a local variable of type int.
     * 
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     */
    int getInt(FrameSlot slot);

    /**
     * Write access to a local variable of type int.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    void setInt(FrameSlot slot, int value);

    /**
     * Read access to a local variable of type long.
     * 
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     */
    long getLong(FrameSlot slot);

    /**
     * Write access to a local variable of type long.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    void setLong(FrameSlot slot, long value);

    /**
     * Read access to a local variable of type float.
     * 
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     */
    float getFloat(FrameSlot slot);

    /**
     * Write access to a local variable of type float.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    void setFloat(FrameSlot slot, float value);

    /**
     * Read access to a local variable of type double.
     * 
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     */
    double getDouble(FrameSlot slot);

    /**
     * Write access to a local variable of type double.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    void setDouble(FrameSlot slot, double value);

    void updateToLatestVersion();

    /**
     * Converts this virtual frame into a packed frame that has no longer direct access to the local
     * variables. This packing is an important hint to the Truffle optimizer and therefore passing
     * around a {@link PackedFrame} should be preferred over passing around a {@link VirtualFrame}
     * when the probability that an unpacking will occur is low.
     * 
     * @return the packed frame
     */
    PackedFrame pack();

    /**
     * Materializes this frame, which allows it to be stored in a field or cast to
     * {@link java.lang.Object}. The frame however looses the ability to be packed or to access the
     * caller frame.
     * 
     * @return the new materialized frame
     */
    MaterializedFrame materialize();
}
