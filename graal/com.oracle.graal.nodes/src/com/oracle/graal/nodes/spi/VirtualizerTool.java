/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.spi;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * This tool can be used to query the current state (normal/virtualized/re-materialized) of values and to describe the
 * actions that would be taken for this state.
 *
 * See also {@link Virtualizable}.
 */
public interface VirtualizerTool {

    /**
     * @return the {@link MetaAccessProvider} associated with the current compilation, which might be required for
     *         creating constants, etc.
     */
    MetaAccessProvider getMetaAccessProvider();

    // methods working on virtualized/materialized objects

    /**
     * Queries the current state of the given value: if it is virtualized (thread-local and the compiler knows all
     * entries) or not.
     *
     * @param value the value whose state should be queried.
     * @return the {@link VirtualObjectNode} representing the value if it is virtualized, null otherwise.
     */
    VirtualObjectNode getVirtualState(ValueNode value);

    /**
     * Retrieves the entry (field or array element) with the given index in the virtualized object.
     *
     * @param virtual the virtualized object
     * @param index the index to be queried.
     * @return the entry at the given index.
     */
    ValueNode getVirtualEntry(VirtualObjectNode virtual, int index);

    /**
     * Sets the entry (field or array element) with the given index in the virtualized object.
     *
     * @param virtual the virtualized object.
     * @param index the index to be set.
     * @param value the new value for the given index.
     */
    void setVirtualEntry(VirtualObjectNode virtual, int index, ValueNode value);

    /**
     * Retrieves the lock count of the given virtualized object.
     *
     * @param virtual the virtualized object.
     * @return the number of locks.
     */
    int getVirtualLockCount(VirtualObjectNode virtual);

    /**
     * Sets the lock count of the given virtualized object.
     *
     * @param virtual the virtualized object.
     * @param lockCount the new lock count.
     */
    void setVirtualLockCount(VirtualObjectNode virtual, int lockCount);

    /**
     * Queries the current state of the given value: if it was materialized or not.
     *
     * @param value the value whose state should be queried.
     * @return the materialized value (usually a MaterializeObjectNode or a {@link PhiNode}) if it was materialized,
     *         null otherwise.
     */
    ValueNode getMaterializedValue(ValueNode value);

    // scalar replacement

    /**
     * Replacements via {@link #replaceWithValue(ValueNode)} are not immediately committed. This method can be used to
     * determine if a value was replaced by another one (e.g., a load field by the loaded value).
     *
     * @param original the original input value.
     * @return the replacement value, or the original value if there is no replacement.
     */
    ValueNode getReplacedValue(ValueNode original);

    // operations on the current node

    /**
     * Deletes the current node and replaces it with the given virtualized object.
     *
     * @param virtual the virtualized object that should replace the current node.
     */
    void replaceWithVirtual(VirtualObjectNode virtual);

    /**
     * Deletes the current node and replaces it with the given value.
     *
     * @param replacement the value that should replace the current node.
     */
    void replaceWithValue(ValueNode replacement);

    /**
     * Deletes the current node.
     */
    void delete();

    /**
     * Replaces an input of the current node.
     *
     * @param oldInput the old input value.
     * @param replacement the new input value.
     */
    void replaceFirstInput(Node oldInput, Node replacement);

    /**
     * Performs a custom action on the current node. This action will only be performed when, and if, the changes are
     * committed. Custom actions must not modify inputs of nodes.
     *
     * @param action the custom action.
     */
    void customAction(Runnable action);
}
