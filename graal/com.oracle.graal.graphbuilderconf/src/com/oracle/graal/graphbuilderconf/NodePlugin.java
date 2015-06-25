/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graphbuilderconf;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.nodes.*;

public interface NodePlugin extends GraphBuilderPlugin {
    /**
     * Handle the parsing of a method invocation bytecode to a method that can be bound statically.
     * If the method returns true, it must {@link GraphBuilderContext#push push} a value as the
     * result of the method invocation using the {@link Signature#getReturnKind return kind} of the
     * method.
     *
     * @param b the context
     * @param method the statically bound, invoked method
     * @param args the arguments of the method invocation
     * @return true if the plugin handles the invocation, false otherwise
     */
    default boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        return false;
    }

    /**
     * Handle the parsing of a GETFIELD bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value using the {@link ResolvedJavaField#getKind()
     * kind} of the field.
     *
     * @param b the context
     * @param object the receiver object for the field access
     * @param field the accessed field
     * @return true if the plugin handles the field access, false otherwise
     */
    default boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
        return false;
    }

    /**
     * Handle the parsing of a GETSTATIC bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value using the {@link ResolvedJavaField#getKind()
     * kind} of the field.
     *
     * @param b the context
     * @param field the accessed field
     * @return true if the plugin handles the field access, false otherwise
     */
    default boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField field) {
        return false;
    }

    /**
     * Handle the parsing of a PUTFIELD bytecode.
     *
     * @param b the context
     * @param object the receiver object for the field access
     * @param field the accessed field
     * @param value the value to be stored into the field
     * @return true if the plugin handles the field access, false otherwise
     */
    default boolean handleStoreField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field, ValueNode value) {
        return false;
    }

    /**
     * Handle the parsing of a PUTSTATIC bytecode.
     *
     * @param b the context
     * @param field the accessed field
     * @param value the value to be stored into the field
     * @return true if the plugin handles the field access, false otherwise.
     */
    default boolean handleStoreStaticField(GraphBuilderContext b, ResolvedJavaField field, ValueNode value) {
        return false;
    }

    /**
     * Handle the parsing of an array load bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value using the provided elementKind.
     *
     * @param b the context
     * @param array the accessed array
     * @param index the index for the array access
     * @param elementKind the element kind of the accessed array
     * @return true if the plugin handles the array access, false otherwise.
     */
    default boolean handleLoadIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, Kind elementKind) {
        return false;
    }

    /**
     * Handle the parsing of an array store bytecode.
     *
     * @param b the context
     * @param array the accessed array
     * @param index the index for the array access
     * @param elementKind the element kind of the accessed array
     * @param value the value to be stored into the array
     * @return true if the plugin handles the array access, false otherwise.
     */
    default boolean handleStoreIndexed(GraphBuilderContext b, ValueNode array, ValueNode index, Kind elementKind, ValueNode value) {
        return false;
    }

    /**
     * Handle the parsing of a CHECKCAST bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value with the result of the cast using
     * {@link Kind#Object}.
     *
     * @param b the context
     * @param object the object to be type checked
     * @param type the type that the object is checked against
     * @param profile the profiling information for the type check, or null if no profiling
     *            information is available
     * @return true if the plugin handles the cast, false otherwise
     */
    default boolean handleCheckCast(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        return false;
    }

    /**
     * Handle the parsing of a INSTANCEOF bytecode. If the method returns true, it must
     * {@link GraphBuilderContext#push push} a value with the result of the instanceof using
     * {@link Kind#Int}.
     *
     * @param b the context
     * @param object the object to be type checked
     * @param type the type that the object is checked against
     * @param profile the profiling information for the type check, or null if no profiling
     *            information is available
     * @return true if the plugin handles the instanceof, false otherwise
     */
    default boolean handleInstanceOf(GraphBuilderContext b, ValueNode object, ResolvedJavaType type, JavaTypeProfile profile) {
        return false;
    }

    /**
     * If the plugin {@link GraphBuilderContext#push pushes} a value with a different {@link Kind}
     * than specified by the bytecode, it must override this method and return {@code true}. This
     * disables assertion checking for value kinds.
     *
     * @param b the context
     */
    default boolean canChangeStackKind(GraphBuilderContext b) {
        return false;
    }
}
