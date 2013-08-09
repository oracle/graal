/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl;

import java.util.*;

import com.oracle.truffle.api.nodes.*;

/**
 * Enables the dynamic creation of generated nodes. It provides an convenient way to instantiate
 * generated node classes without using reflection.
 */
public interface NodeFactory<T> {

    /**
     * Instantiates the node using the arguments array. The arguments length and types must suffice
     * one of the returned signatures in {@link #getNodeSignatures()}. If the arguments array does
     * not suffice one of the node signatures an {@link IllegalArgumentException} is thrown.
     * 
     * @param arguments the argument values
     * @return the instantiated node
     * @throws IllegalArgumentException
     */
    T createNode(Object... arguments);

    /**
     * Instantiates a new generic variant of the node. This is an optional method and throws an
     * {@link UnsupportedOperationException} if not supported.
     * 
     * @param thisNode the current node
     * @return the specialized node
     */
    T createNodeGeneric(T thisNode);

    /**
     * Returns the node class that will get created by {@link #createNode(Object...)}. The node
     * class does not match exactly to the instantiated object but they are guaranteed to be
     * assignable.
     */
    Class<T> getNodeClass();

    /**
     * Returns a list of signatures that can be used to invoke {@link #createNode(Object...)}.
     */
    List<List<Class<?>>> getNodeSignatures();

    /**
     * Returns a list of children that will be executed by the created node. This is useful for base
     * nodes that can execute a variable amount of nodes.
     */
    List<Class<? extends Node>> getExecutionSignature();

}
