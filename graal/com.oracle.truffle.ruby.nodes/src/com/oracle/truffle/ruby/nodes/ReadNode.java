/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes;

/**
 * Interface for all nodes which read something, providing a method to transform them to write the
 * same thing.
 */
public interface ReadNode {

    /**
     * Return a new node that performs the equivalent write operation to this node's read, using the
     * supplied node for the right-hand-side.
     */
    RubyNode makeWriteNode(RubyNode rhs);

}
