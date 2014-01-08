/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes;

import com.oracle.truffle.api.*;

/**
 * Source sections used for core method nodes.
 */
public final class CoreSourceSection implements NullSourceSection {

    private final String name;

    public CoreSourceSection(String name) {
        this.name = name;
    }

    public Source getSource() {
        return new CoreSource(name);
    }

    public int getStartLine() {
        return 0;
    }

    public int getStartColumn() {
        return 0;
    }

    public int getCharIndex() {
        return 0;
    }

    @Override
    public int getCharLength() {
        return 0;
    }

    public int getCharEndIndex() {
        return 0;
    }

    public String getIdentifier() {
        return null;
    }

    public String getCode() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

}
