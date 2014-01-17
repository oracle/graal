/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.configuration;

import java.io.*;

import com.oracle.truffle.ruby.runtime.*;

/**
 * Configurable, immutable global parameters for Ruby.
 */
public class Configuration {

    private final String standardLibrary;

    private final boolean verbose;
    private final int warningLevel;
    private final int taintCheckLevel;

    private final String defaultExternalEncoding;
    private final String defaultInternalEncoding;

    private final boolean debug;
    private final boolean trace;
    private final boolean fullObjectSpace;

    private final boolean printParseTree;
    private final boolean printUninitializedCalls;
    private final boolean printJavaExceptions;

    private final PrintStream standardOut;
    private final InputReader inputReader;

    public Configuration(ConfigurationBuilder builder) {
        assert builder != null;

        standardLibrary = builder.getStandardLibrary();

        verbose = builder.getVerbose();
        warningLevel = builder.getWarningLevel();
        taintCheckLevel = builder.getTaintCheckLevel();

        defaultExternalEncoding = builder.getDefaultExternalEncoding();
        defaultInternalEncoding = builder.getDefaultInternalEncoding();

        debug = builder.getDebug();
        trace = builder.getTrace();
        fullObjectSpace = builder.getFullObjectSpace();

        printParseTree = builder.getPrintParseTree();
        printUninitializedCalls = builder.getPrintUninitializedCalls();
        printJavaExceptions = builder.getPrintJavaExceptions();

        standardOut = builder.getStandardOut();
        inputReader = builder.getInputReader();
    }

    public String getStandardLibrary() {
        return standardLibrary;
    }

    public boolean getDebug() {
        return debug;
    }

    public boolean getVerbose() {
        return verbose;
    }

    public int getWarningLevel() {
        return warningLevel;
    }

    public int getTaintCheckLevel() {
        return taintCheckLevel;
    }

    public String getDefaultExternalEncoding() {
        return defaultExternalEncoding;
    }

    public String getDefaultInternalEncoding() {
        return defaultInternalEncoding;
    }

    public boolean getTrace() {
        return trace;
    }

    public boolean getFullObjectSpace() {
        return fullObjectSpace;
    }

    public boolean getPrintParseTree() {
        return printParseTree;
    }

    public boolean getPrintUninitializedCalls() {
        return printUninitializedCalls;
    }

    public boolean getPrintJavaExceptions() {
        return printJavaExceptions;
    }

    public PrintStream getStandardOut() {
        return standardOut;
    }

    public InputReader getInputReader() {
        return inputReader;
    }

}
