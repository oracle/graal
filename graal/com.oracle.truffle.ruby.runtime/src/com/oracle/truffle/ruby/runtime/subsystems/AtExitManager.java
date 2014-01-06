/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.subsystems;

import java.util.*;

import com.oracle.truffle.ruby.runtime.core.*;

/**
 * Manages at_exit callbacks.
 */
public class AtExitManager {

    private final List<RubyProc> blocks = new ArrayList<>();

    public void add(RubyProc block) {
        blocks.add(block);
    }

    public void run() {
        final ListIterator<RubyProc> iterator = blocks.listIterator(blocks.size());

        while (iterator.hasPrevious()) {
            iterator.previous().call(null);
        }
    }
}
