/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime;

import com.oracle.truffle.api.nodes.*;

/**
 * The result of parsing Ruby code is a root node and a frame descriptor for the method in that
 * root. The root node will always be a {@code RubyRootNode}, but this package is below the nodes
 * package so currently cannot refer to it.
 */
public class RubyParserResult {

    private final RootNode rootNode;

    public RubyParserResult(RootNode rootNode) {
        assert rootNode != null;
        this.rootNode = rootNode;
    }

    public RootNode getRootNode() {
        return rootNode;
    }

}
