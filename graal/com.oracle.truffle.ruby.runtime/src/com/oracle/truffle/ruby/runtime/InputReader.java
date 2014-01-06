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

import java.io.*;

/**
 * Interface allowing Ruby {@code Kernel#gets} to be configured to use the standard Java readLine,
 * some library like JLine, or to be mocked for testing.
 */
public interface InputReader {

    /**
     * Show a prompt and read one line of input.
     */
    String readLine(String prompt) throws IOException;

}
