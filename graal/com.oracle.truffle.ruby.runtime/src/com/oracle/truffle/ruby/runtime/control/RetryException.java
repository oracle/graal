/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.control;

import com.oracle.truffle.api.nodes.*;

/**
 * Controls re-trying an iteration in a control structure or method.
 */
public final class RetryException extends ControlFlowException {

    private static final long serialVersionUID = -1675586631300635765L;

}
