/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.shell;

import java.util.*;

import com.oracle.truffle.ruby.runtime.configuration.*;

/**
 * Options resulting from parsing a command line by {@link CommandLineParser}.
 */
public class CommandLineOptions {

    private final List<String> preRequires;
    private final String preChangeDirectory;

    private final List<String> extraLoadPath;

    private final String programFile;
    private final List<String> commandLineScripts;
    private final List<String> programArgs;
    private final List<String> switchArgs;

    private final int recordSeparator;
    private final boolean autosplit;
    private final String autosplitPattern;
    private final boolean checkSyntaxOnly;
    private final boolean lineEndingProcessing;
    private final boolean inPlaceEdit;
    private final String inPlaceBackupExtension;
    private final boolean implicitLoop;
    private final boolean implicitSedLoop;
    private final boolean importFromPath;
    private final boolean stripMessage;
    private final boolean useJLine;

    private final Configuration configuration;

    public CommandLineOptions(List<String> preRequires, String preChangeDirectory, List<String> extraLoadPath, String programFile, List<String> commandLineScripts, List<String> programArgs,
                    List<String> switchArgs, int recordSeparator, boolean autosplit, String autosplitPattern, boolean checkSyntaxOnly, boolean lineEndingProcessing, boolean inPlaceEdit,
                    String inPlaceBackupExtension, boolean implicitLoop, boolean implicitSedLoop, boolean importFromPath, boolean stripMessage, boolean useJLine, Configuration configuration) {
        this.preRequires = preRequires;
        this.preChangeDirectory = preChangeDirectory;
        this.extraLoadPath = extraLoadPath;
        this.programFile = programFile;
        this.commandLineScripts = commandLineScripts;
        this.programArgs = programArgs;
        this.switchArgs = switchArgs;
        this.recordSeparator = recordSeparator;
        this.autosplit = autosplit;
        this.autosplitPattern = autosplitPattern;
        this.checkSyntaxOnly = checkSyntaxOnly;
        this.lineEndingProcessing = lineEndingProcessing;
        this.inPlaceEdit = inPlaceEdit;
        this.inPlaceBackupExtension = inPlaceBackupExtension;
        this.implicitLoop = implicitLoop;
        this.implicitSedLoop = implicitSedLoop;
        this.importFromPath = importFromPath;
        this.stripMessage = stripMessage;
        this.useJLine = useJLine;
        this.configuration = configuration;
    }

    public List<String> getPreRequires() {
        return preRequires;
    }

    public String getPreChangeDirectory() {
        return preChangeDirectory;
    }

    public List<String> getExtraLoadPath() {
        return extraLoadPath;
    }

    public String getProgramFile() {
        return programFile;
    }

    public List<String> getCommandLineScripts() {
        return commandLineScripts;
    }

    public List<String> getProgramArgs() {
        return programArgs;
    }

    public List<String> getSwitchArgs() {
        return switchArgs;
    }

    public int getRecordSeparator() {
        return recordSeparator;
    }

    public String getAutosplitPattern() {
        return autosplitPattern;
    }

    public boolean isAutosplit() {
        return autosplit;
    }

    public boolean isCheckSyntaxOnly() {
        return checkSyntaxOnly;
    }

    public boolean isLineEndingProcessing() {
        return lineEndingProcessing;
    }

    public boolean isInPlaceEdit() {
        return inPlaceEdit;
    }

    public String getInPlaceBackupExtension() {
        return inPlaceBackupExtension;
    }

    public boolean isImplicitLoop() {
        return implicitLoop;
    }

    public boolean isImplicitSedLoop() {
        return implicitSedLoop;
    }

    public boolean isImportFromPath() {
        return importFromPath;
    }

    public boolean isStripMessage() {
        return stripMessage;
    }

    public boolean useJLine() {
        return useJLine;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

}
