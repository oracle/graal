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

/**
 * The {@link UndefinedPlaceholder} is a value that represents an undefined value in Ruby. This is
 * used to differentiate between nil and the true absence of a value, such as an argument that has
 * not been passed.
 */
public final class UndefinedPlaceholder {

    public static final UndefinedPlaceholder INSTANCE = new UndefinedPlaceholder();

    private UndefinedPlaceholder() {
    }

}
