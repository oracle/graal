/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
/**
 * <p>This package contains basic tests of the Truffle API and serves at the same
 * time as an introduction to the Truffle API for language implementors. Every test gives an example on how to use the construct explained in the class description.</p>
 *
 * <p>
 * Truffle is a language implementation framework. A guest language method is represented as a tree of executable nodes.
 * The framework provides mechanisms for those trees to call each other. Additionally it contains dedicated data structures for storing data local to a tree invocation.
 * </p>
 *
 * <p>
 * This introduction to Truffle contains items in the following recommended order:
 *
 * <ul>
 * <li>How to get access to the Truffle runtime? {@link com.oracle.truffle.api.test.TruffleRuntimeTest}</li>
 * <li>How to create a root node? {@link com.oracle.truffle.api.test.RootNodeTest}</li>
 * <li>How to create a child node and link it with its parent? {@link com.oracle.truffle.api.test.ChildNodeTest}</li>
 * <li>How to create an array of child nodes? {@link com.oracle.truffle.api.test.ChildrenNodesTest}</li>
 * <li>Why are final fields in node classes important? {@link com.oracle.truffle.api.test.FinalFieldTest}</li>
 * <li>How to replace one node with another node and what for? {@link com.oracle.truffle.api.test.ReplaceTest}</li>
 * <li>How to let one Truffle tree invoke another one? {@link com.oracle.truffle.api.test.CallTest}</li>
 * <li>How to pass arguments when executing a tree? {@link com.oracle.truffle.api.test.ArgumentsTest}</li>
 * <li>How to use frames and frame slots to store values local to an activation? {@link com.oracle.truffle.api.test.FrameTest}</li>
 * <li>How to use type specialization and speculation for frame slots? {@link com.oracle.truffle.api.test.FrameSlotTypeSpecializationTest}</li>
 * <li>How to use type specialization and speculation for node return values? {@link com.oracle.truffle.api.test.ReturnTypeSpecializationTest}</li>
 * </ul>
 *
 *
 */
package com.oracle.truffle.api.test;

