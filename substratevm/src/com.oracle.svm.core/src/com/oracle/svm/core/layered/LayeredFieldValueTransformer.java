/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.layered;

import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithReceiverBasedAvailability;
import com.oracle.svm.core.util.VMError;

/**
 * Layered Images specific field value transformer. This transformer, in addition to the behavior of
 * {@link FieldValueTransformerWithReceiverBasedAvailability}, also allows for the value to be
 * updated in a later layer.
 * <p>
 * Before a given receiver is installed in the image heap, {@link #isValueAvailable} and
 * {@link #transform} are used determine its field value. If the {@link Result} returned by
 * {@link #transform} has {@link Result#updatable()} set, then it will be possible to update this
 * field value in later layers; otherwise the value cannot be changed.
 * <p>
 * For receivers installed in prior layers with updatable field values, then
 * {@link #isUpdateAvailable} and {@link #update} are used to determine if and/or when the field
 * should be updated.
 * <p>
 * Note in a given layer and for a given receiver, either the pair ({@link #isValueAvailable},
 * {@link #transform}) or ({@link #isUpdateAvailable}, {@link #update}) will be exclusively called
 * and {@link #transform}/{@link #update} will be called at most once.
 */
public abstract class LayeredFieldValueTransformer<T> {

    /**
     * @param value result of the transformation/update.
     * @param updatable indicates whether this value can be updated in later layers via
     *            {@link #update}.
     */
    public record Result(Object value, boolean updatable) {

    }

    /**
     * Returns true if the value can be set. Note this method will only be called for receivers that
     * have yet to be installed in the image heap.
     */
    public abstract boolean isValueAvailable(T receiver);

    /**
     * Returns the non-null transformation result. Note this method will only be called for
     * receivers that have yet to be installed in the image heap.
     */
    public abstract Result transform(T receiver);

    /**
     * Returns whether an update is available for the given receiver. Note this method will only be
     * called for receivers which were installed in a prior layer.
     */
    public boolean isUpdateAvailable(@SuppressWarnings("unused") T receiver) {
        throw VMError.shouldNotReachHere("isUpdateAvailable not implemented");
    }

    /**
     * Returns the non-null update result. Note this method will only be called for receivers which
     * were installed in a prior layer.
     */
    public Result update(@SuppressWarnings("unused") T receiver) {
        throw VMError.shouldNotReachHere("update not implemented");
    }
}
