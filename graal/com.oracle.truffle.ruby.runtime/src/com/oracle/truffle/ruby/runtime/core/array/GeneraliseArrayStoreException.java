/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core.array;

import com.oracle.truffle.api.nodes.*;

/**
 * An exception that signals that an ArrayStore cannot store a given object because of its type, and
 * that the store must be generalized to accommodate it.
 */
public class GeneraliseArrayStoreException extends SlowPathException {

    private static final long serialVersionUID = -7648655548414168177L;

}
