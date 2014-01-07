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
 * Interface for all nodes which write something, providing a method to transform them to read the
 * same thing.
 */
public interface WriteNode {

    /**
     * Return a new node that performs the equivalent read operation to this node's write.
     */
    RubyNode makeReadNode();

}
