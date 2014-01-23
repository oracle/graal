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
 * The mutable counterpart to {@link Configuration}.
 */
public class ConfigurationBuilder {

    /**
     * The path of the JRuby packaging of the Ruby standard library within our source tree.
     */
    public static final String JRUBY_STDLIB_JAR = "lib/jruby-stdlib-1.7.4.jar";

    private String standardLibrary = JRUBY_STDLIB_JAR;

    private boolean debug = true;
    private boolean verbose = false;
    private int warningLevel = 0;
    private int taintCheckLevel = 0;

    private String defaultExternalEncoding = null;
    private String defaultInternalEncoding = null;

    private boolean trace = true;
    private boolean fullObjectSpace = false;

    private boolean printParseTree = false;
    private boolean printUninitializedCalls = false;
    private boolean printJavaExceptions = false;

    private PrintStream standardOut = System.out;

    private InputReader inputReader = new InputReader() {

        private final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        @Override
        public String readLine(String prompt) throws IOException {
            System.err.print(prompt);
            return reader.readLine();
        }

    };

    public ConfigurationBuilder() {
    }

    public ConfigurationBuilder(Configuration configuration) {
        assert configuration != null;

        standardLibrary = configuration.getStandardLibrary();

        debug = configuration.getDebug();
        verbose = configuration.getVerbose();
        warningLevel = configuration.getWarningLevel();
        taintCheckLevel = configuration.getTaintCheckLevel();

        defaultExternalEncoding = configuration.getDefaultExternalEncoding();
        defaultInternalEncoding = configuration.getDefaultInternalEncoding();

        trace = configuration.getTrace();
        fullObjectSpace = configuration.getFullObjectSpace();

        printParseTree = configuration.getPrintParseTree();
        printUninitializedCalls = configuration.getPrintUninitializedCalls();
        printJavaExceptions = configuration.getPrintJavaExceptions();

        standardOut = configuration.getStandardOut();
    }

    public String getStandardLibrary() {
        return standardLibrary;
    }

    public void setStandardLibrary(String standardLibrary) {
        assert standardLibrary != null;
        this.standardLibrary = standardLibrary;
    }

    public boolean getDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean getVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public int getWarningLevel() {
        return warningLevel;
    }

    public void setWarningLevel(int warningLevel) {
        this.warningLevel = warningLevel;
    }

    public int getTaintCheckLevel() {
        return taintCheckLevel;
    }

    public void setTaintCheckLevel(int taintCheckLevel) {
        this.taintCheckLevel = taintCheckLevel;
    }

    public String getDefaultExternalEncoding() {
        return defaultExternalEncoding;
    }

    public void setDefaultExternalEncoding(String defaultExternalEncoding) {
        assert defaultExternalEncoding != null;
        this.defaultExternalEncoding = defaultExternalEncoding;
    }

    public String getDefaultInternalEncoding() {
        return defaultInternalEncoding;
    }

    public void setDefaultInternalEncoding(String defaultInternalEncoding) {
        assert defaultInternalEncoding != null;
        this.defaultInternalEncoding = defaultInternalEncoding;
    }

    public boolean getTrace() {
        return trace;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    public boolean getFullObjectSpace() {
        return fullObjectSpace;
    }

    public void setFullObjectSpace(boolean fullObjectSpace) {
        this.fullObjectSpace = fullObjectSpace;
    }

    public boolean getPrintParseTree() {
        return printParseTree;
    }

    public void setPrintParseTree(boolean printParseTree) {
        this.printParseTree = printParseTree;
    }

    public boolean getPrintUninitializedCalls() {
        return printUninitializedCalls;
    }

    public void setPrintUninitializedCalls(boolean printUninitializedCalls) {
        this.printUninitializedCalls = printUninitializedCalls;
    }

    public boolean getPrintJavaExceptions() {
        return printJavaExceptions;
    }

    public void setPrintJavaExceptions(boolean printJavaExceptions) {
        this.printJavaExceptions = printJavaExceptions;
    }

    public PrintStream getStandardOut() {
        return standardOut;
    }

    public void setStandardOut(PrintStream standardOut) {
        assert standardOut != null;
        this.standardOut = standardOut;
    }

    public InputReader getInputReader() {
        return inputReader;
    }

    public void setInputReader(InputReader lineReader) {
        assert lineReader != null;
        this.inputReader = lineReader;
    }

}
