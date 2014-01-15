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

import java.io.*;

import jline.console.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.ruby.nodes.core.*;
import com.oracle.truffle.ruby.parser.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.configuration.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

/**
 * The entry point class for RubyTruffle. Implements the MRI command line interface.
 */
public class Shell {

    /**
     * Entry point method for Ruby both in batch and interactive mode.
     */
    public static void main(String[] args) throws IOException {
        // Parse the command line

        final CommandLineOptions options = CommandLineParser.parse(args);

        if (options == null) {
            return;
        }

        // Setup JLine

        ConsoleReader console = null;

        if (options.useJLine()) {
            System.setProperty("jline.shutdownhook", "true");
            console = new ConsoleReader();
            console.setExpandEvents(false);
        }

        // Override the home directory if RUBYHOME is set

        final ConfigurationBuilder configurationBuilder = new ConfigurationBuilder(options.getConfiguration());

        if (System.getenv("RUBYHOME") != null) {
            configurationBuilder.setStandardLibrary(System.getenv("RUBYHOME") + "/" + ConfigurationBuilder.JRUBY_STDLIB_JAR);
        }

        // Use JLine for console input

        final ConsoleReader finalConsole = console;

        if (options.useJLine()) {
            configurationBuilder.setInputReader(new InputReader() {

                @Override
                public String readLine(String prompt) throws IOException {
                    return finalConsole.readLine(prompt);
                }

            });
        }

        // Set up a context

        final RubyContext context = new RubyContext(new Configuration(configurationBuilder), new JRubyParser());

        // Bring in core method nodes

        CoreMethodNodeManager.addMethods(context.getCoreLibrary().getObjectClass());

        // Give the core library manager a chance to tweak some of those methods

        context.getCoreLibrary().initializeAfterMethodsAdded();

        // Set program arguments

        for (String arg : options.getProgramArgs()) {
            context.getCoreLibrary().getArgv().push(context.makeString(arg));
        }

        if (!options.getSwitchArgs().isEmpty()) {
            context.implementationMessage("can't set -s switch arguments yet");
        }

        // Set the load path

        final RubyArray loadPath = (RubyArray) context.getCoreLibrary().getGlobalVariablesObject().getInstanceVariable("$:");

        final String pathVar = System.getenv("PATH");

        if (options.isImportFromPath() && pathVar != null) {
            for (String path : pathVar.split(File.pathSeparator)) {
                loadPath.push(context.makeString(path));
            }
        }

        for (String path : options.getExtraLoadPath()) {
            loadPath.push(context.makeString(path));
        }

        final String rubylibVar = System.getenv("RUBYLIB");

        if (rubylibVar != null) {
            for (String path : rubylibVar.split(File.pathSeparator)) {
                loadPath.push(context.makeString(path));
            }
        }

        if (context.getConfiguration().getStandardLibrary().endsWith(".jar")) {
            /*
             * Use the 1.9 library, even though we're emulating 2.1, as there are some bugs running
             * the 2.1 library at the moment.
             */
            loadPath.push(context.makeString("jar:file:" + context.getConfiguration().getStandardLibrary() + "!/META-INF/jruby.home/lib/ruby/1.9"));
        } else {
            loadPath.push(context.makeString(context.getConfiguration().getStandardLibrary()));
        }

        // Pre-required modules

        for (String feature : options.getPreRequires()) {
            context.getFeatureManager().require(feature);
        }

        // Check for other options that are not implemented yet

        if (options.getRecordSeparator() != -1) {
            context.implementationMessage("record separator not implemented");
        }

        if (options.isAutosplit()) {
            context.implementationMessage("autosplit not implemented");
        }

        if (options.getPreChangeDirectory() != null) {
            context.implementationMessage("not able to change directory");
        }

        if (options.isLineEndingProcessing()) {
            context.implementationMessage("line end processing not implemented");
        }

        if (options.isInPlaceEdit()) {
            context.implementationMessage("in place editing not implemented");
        }

        if (options.isImplicitLoop() || options.isImplicitSedLoop()) {
            context.implementationMessage("implicit loops not implemented");
        }

        if (options.isStripMessage()) {
            context.implementationMessage("strip message -x option not implemented");
        }

        // Run the scripts, program file, or run the temporary version of IRB

        try {
            if (!options.getCommandLineScripts().isEmpty()) {
                final StringBuilder combinedScript = new StringBuilder();

                for (String script : options.getCommandLineScripts()) {
                    combinedScript.append(script);
                    combinedScript.append("\n");
                }

                try {
                    final Source source = context.getSourceManager().get("-e", combinedScript.toString());
                    if (options.isCheckSyntaxOnly()) {
                        context.getParser().parse(context, source, RubyParser.ParserContext.TOP_LEVEL, null);
                        System.out.println("Syntax OK");
                    } else {
                        context.execute(context, source, RubyParser.ParserContext.TOP_LEVEL, context.getCoreLibrary().getMainObject(), null);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (options.getProgramFile() != null) {
                try {
                    if (options.isCheckSyntaxOnly()) {
                        final Source source = context.getSourceManager().get(options.getProgramFile());
                        context.getParser().parse(context, source, RubyParser.ParserContext.TOP_LEVEL, null);
                        System.out.println("Syntax OK");
                    } else {
                        context.loadFile(options.getProgramFile());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                if (options.isCheckSyntaxOnly()) {
                    System.err.println("Can't check syntax in IRB mode");
                    return;
                }

                context.runShell(null, null);
            }
        } finally {
            context.shutdown();
        }
    }

}
