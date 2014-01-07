/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.core;

import java.io.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;

/**
 * Represents an expression that is evaluated by running it as a system command via forking and
 * execing, and then taking stdout as a string.
 */
@NodeInfo(shortName = "system")
public class SystemNode extends RubyNode {

    @Child protected RubyNode child;

    public SystemNode(RubyContext context, SourceSection sourceSection, RubyNode child) {
        super(context, sourceSection);
        this.child = adoptChild(child);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final RubyContext context = getContext();

        final String command = child.execute(frame).toString();

        Process process;

        try {
            // We need to run via bash to get the variable and other expansion we expect
            process = Runtime.getRuntime().exec(new String[]{"bash", "-c", command});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final InputStream stdout = process.getInputStream();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));

        final StringBuilder resultBuilder = new StringBuilder();

        String line;

        // TODO(cs): this isn't great for binary output

        try {
            while ((line = reader.readLine()) != null) {
                resultBuilder.append(line);
                resultBuilder.append("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return context.makeString(resultBuilder.toString());
    }
}
