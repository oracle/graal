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
import java.util.*;

import com.oracle.truffle.ruby.runtime.configuration.*;

/**
 * MRI-compatible command line parser, producing {@link CommandLineOptions}.
 */
public abstract class CommandLineParser {

    /**
     * Parse an MRI-compatible command line.
     */
    public static CommandLineOptions parse(String[] args) {
        assert args != null;

        final List<String> preRequires = new ArrayList<>();
        String preChangeDirectory = null;

        final List<String> extraLoadPath = new ArrayList<>();

        String programFile = null;
        final List<String> commandLineScripts = new ArrayList<>();
        final List<String> programArgs = new ArrayList<>();
        final List<String> switchArgs = new ArrayList<>();

        int recordSeparator = -1;
        boolean autosplit = false;
        String autosplitPattern = null;
        boolean checkSyntaxOnly = false;
        boolean lineEndingProcessing = false;
        boolean inPlaceEdit = false;
        String inPlaceBackupExtension = null;
        boolean implicitLoop = false;
        boolean implicitSedLoop = false;
        boolean importFromPath = false;
        boolean messageStrip = false;
        boolean useJLine = true;

        final ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();

        final List<String> normalizedArgs = normalizeArgs(args);

        boolean stillRubyArgs = true;
        boolean collectingSwitchArgs = false;
        int n = 0;

        while (n < normalizedArgs.size()) {
            final String arg = normalizedArgs.get(n);

            if (stillRubyArgs && arg.startsWith("-")) {
                if (collectingSwitchArgs) {
                    switchArgs.add(arg);
                } else if (arg.startsWith("-0")) {
                    if (arg.length() == 2) {
                        recordSeparator = 0;
                    } else {
                        recordSeparator = Integer.parseInt(arg.substring(2), 8);
                    }
                } else if (arg.startsWith("-i")) {
                    inPlaceEdit = true;

                    if (arg.length() > 2) {
                        inPlaceBackupExtension = arg.substring(2);
                    }
                } else if (arg.startsWith("-W")) {
                    if (arg.length() == 2) {
                        configurationBuilder.setWarningLevel(2);
                    } else if (arg.startsWith("-Wlevel=")) {
                        configurationBuilder.setWarningLevel(Integer.parseInt(arg.substring("-Wlevel=".length())));
                    } else {
                        throw new IllegalArgumentException("bad flag " + arg);
                    }
                } else if (arg.startsWith("-T")) {
                    if (arg.length() == 2) {
                        configurationBuilder.setTaintCheckLevel(1);
                    } else if (arg.startsWith("-Tlevel=")) {
                        configurationBuilder.setTaintCheckLevel(Integer.parseInt(arg.substring("-Tlevel=".length())));
                    } else {
                        throw new IllegalArgumentException("bad flag " + arg);
                    }
                } else if (arg.startsWith("-x")) {
                    messageStrip = true;

                    if (arg.length() > 2) {
                        preChangeDirectory = arg.substring(2);
                    }
                } else {
                    switch (arg) {
                        case "-C":
                        case "-e":
                        case "-E":
                        case "-F":
                        case "-I":
                        case "-r":
                        case "--home":
                            if (n + 1 >= normalizedArgs.size()) {
                                throw new RuntimeException("Expecting value after " + arg);
                            }
                    }

                    switch (arg) {
                        case "--":
                            stillRubyArgs = false;
                            break;
                        case "-a":
                            autosplit = true;
                            break;
                        case "-c":
                            checkSyntaxOnly = true;
                            break;
                        case "-C":
                            if (n + 1 >= normalizedArgs.size()) {
                                throw new RuntimeException("Need a directory path after -C");
                            }
                            preChangeDirectory = normalizedArgs.get(n + 1);
                            n++;
                            break;
                        case "-d":
                        case "--debug":
                            configurationBuilder.setDebug(true);
                            break;
                        case "--no-debug":
                            configurationBuilder.setDebug(false);
                            break;
                        case "--trace":
                            configurationBuilder.setTrace(true);
                            break;
                        case "--no-trace":
                            configurationBuilder.setTrace(false);
                            break;
                        case "-e":
                            commandLineScripts.add(normalizedArgs.get(n + 1));
                            n++;
                            break;
                        case "-E":
                            final String[] encodings = normalizedArgs.get(n + 1).split(":");
                            configurationBuilder.setDefaultExternalEncoding(encodings[0]);

                            if (encodings.length == 2) {
                                configurationBuilder.setDefaultInternalEncoding(encodings[1]);
                            } else {
                                throw new IllegalArgumentException("bad flag " + arg);
                            }

                            n++;
                            break;
                        case "-F":
                            autosplitPattern = normalizedArgs.get(n + 1);
                            n++;
                            break;
                        case "-I":
                            extraLoadPath.add(normalizedArgs.get(n + 1));
                            n++;
                            break;
                        case "-l":
                            lineEndingProcessing = true;
                            break;
                        case "-n":
                            implicitLoop = true;
                            break;
                        case "-r":
                            preRequires.add(normalizedArgs.get(n + 1));
                            n++;
                            break;
                        case "-s":
                            collectingSwitchArgs = true;
                            break;
                        case "-p":
                            implicitSedLoop = true;
                            break;
                        case "-S":
                            importFromPath = true;
                            break;
                        case "-w":
                            configurationBuilder.setWarningLevel(1);
                            break;
                        case "-h":
                        case "--help":
                            help(System.out);
                            return null;
                        case "-v":
                            version(System.out);
                            configurationBuilder.setVerbose(true);
                            break;
                        case "--version":
                            version(System.out);
                            return null;
                        case "--copyright":
                            copyright(System.out);
                            return null;
                        case "--home":
                            configurationBuilder.setStandardLibrary(normalizedArgs.get(n + 1) + "/" + ConfigurationBuilder.JRUBY_STDLIB_JAR);
                            n++;
                            break;
                        case "--stdlib":
                            configurationBuilder.setStandardLibrary(normalizedArgs.get(n + 1));
                            n++;
                            break;
                        case "--full-object-space":
                            configurationBuilder.setFullObjectSpace(true);
                            break;
                        case "--no-jline":
                            useJLine = false;
                            break;
                        case "--print-parse-tree":
                            configurationBuilder.setPrintParseTree(true);
                            break;
                        case "--print-uninitialized-calls":
                            configurationBuilder.setPrintUninitializedCalls(true);
                            break;
                        case "--print-java-exceptions":
                            configurationBuilder.setPrintJavaExceptions(true);
                            break;
                        default:
                            throw new IllegalArgumentException("unknown flag " + arg);
                    }
                }
            } else if (programFile == null) {
                programFile = arg;
                stillRubyArgs = false;
            } else {
                programArgs.add(arg);
            }

            n++;
        }

        return new CommandLineOptions(preRequires, preChangeDirectory, extraLoadPath, programFile, commandLineScripts, programArgs, switchArgs, recordSeparator, autosplit, autosplitPattern,
                        checkSyntaxOnly, lineEndingProcessing, inPlaceEdit, inPlaceBackupExtension, implicitLoop, implicitSedLoop, importFromPath, messageStrip, useJLine, new Configuration(
                                        configurationBuilder));
    }

    /**
     * Produce a canonical set of arguments that includes {@code $RUBYOPT} and has contractions such
     * as a {@code -rdir} replaced with separate arguments {@code -r} and {@code dir} for simpler
     * processing.
     */
    private static List<String> normalizeArgs(String[] args) {
        assert args != null;

        // Arguments come from the main method arguments parameter and $RUBYOPT

        final List<String> inputArgs = new ArrayList<>();

        final String rubyoptVar = System.getenv("RUBYOPT");

        if (rubyoptVar != null) {
            /*
             * TODO(cs): what we've got here is a string that we are supposed to treat as if it was
             * an extra part of the command line, including more Ruby options. However, we just get
             * the string and have to split it into arguments ourselves. Normally the shell does
             * that, including lots of fancy quoting styles. Are we supposed to re-implement all of
             * that? Otherwise arguments in RUBYOPT will be parsed differently to if they were
             * actually on the command line. JRuby just splits like we do. I also think that's what
             * MRI does, but is this correct?
             */

            inputArgs.addAll(Arrays.asList(rubyoptVar.split("\\s+")));
        }

        inputArgs.addAll(Arrays.asList(args));

        // Replace some contractions such as -rdir with -r dir

        final List<String> outputArgs = new ArrayList<>();

        for (String arg : inputArgs) {
            if (arg.startsWith("-C") || arg.startsWith("-E") || arg.startsWith("-F") || arg.startsWith("-I") || arg.startsWith("-r")) {
                outputArgs.add(arg.substring(0, 2));
                outputArgs.add(arg.substring(2));
            } else {
                outputArgs.add(arg);
            }
        }

        return outputArgs;
    }

    /**
     * Print help information.
     */
    private static void help(PrintStream out) {
        out.println("Usage: ruby [switches] [--] [programfile] [arguments]");
        out.println("  -0[octal]       specify record separator (\0, if no argument)");
        out.println("  -a              autosplit mode with -n or -p (splits $_ into $F)");
        out.println("  -c              check syntax only");
        out.println("  -Cdirectory     cd to directory, before executing your script");
        out.println("  -d, --debug     set debugging flags (set $DEBUG to true) and enable Debug module");
        out.println("  -e 'command'    one line of script. Several -e's allowed. Omit [programfile]");
        out.println("  -Eex[:in]       specify the default external and internal character encodings");
        out.println("  -Fpattern       split() pattern for autosplit (-a)");
        out.println("  -i[extension]   edit ARGV files in place (make backup if extension supplied)");
        out.println("  -Idirectory     specify $LOAD_PATH directory (may be used more than once)");
        out.println("  -l              enable line ending processing");
        out.println("  -n              assume 'while gets(); ... end' loop around your script");
        out.println("  -p              assume loop like -n but print line also like sed");
        out.println("  -rlibrary       require the library, before executing your script");
        out.println("  -s              enable some switch parsing for switches after script name");
        out.println("  -S              look for the script using PATH environment variable");
        out.println("  -T[level=1]     turn on tainting checks");
        out.println("  -v              print version number, then turn on verbose mode");
        out.println("  -w              turn warnings on for your script");
        out.println("  -W[level=2]     set warning level; 0=silence, 1=medium, 2=verbose");
        out.println("  -x[directory]   strip off text before #!ruby line and perhaps cd to directory");
        out.println("  --copyright     print the copyright");
        out.println("  --version       print the version");
        out.println("Extra rubytruffle switches:");
        out.println("  --home dir                        set the location of the Ruby Truffle installation (default . or $RUBY_TRUFFLE_HOME)");
        out.println("  --stdlib dir                      use a directory for the Ruby standard library");
        out.println("  --full-object-space               enable full ObjectSpace#each_object and similar");
        out.println("  --no-debug                        disable debugging");
        out.println("  --no-trace                        disable tracing");
        out.println("Debugging rubytruffle switches:");
        out.println("  --no-cache-constant-lookup        don't cache constant lookups");
        out.println("  --no-cache-method-calls           don't cache method lookups");
        out.println("  --no-intrinsic-method-calls       don't turn method calls into intrinsic nodes");
        out.println("  --no-jline                        don't use JLine");
        out.println("  --print-parse-tree                print the result of parsing");
        out.println("  --print-uninitialized-calls       print each time a method call is uninitialized");
        out.println("  --print-java-exceptions           print Java exception back traces at the point of translating them to Ruby exceptions");
        out.println("Relevant environment variables:");
        out.println("  RUBYHOME                          location of the Ruby Truffle installation");
        out.println("  RUBYOPT                           extra command line arguments");
        out.println("  RUBYLIB                           list of colon separated paths to add to $LOAD_PATH");
        out.println("  PATH                              as RUBYLIB, if -S is used");
    }

    /**
     * Print version information.
     */
    private static void version(PrintStream out) {
        out.printf("ruby (rubytruffle) dev [JVM %s]\n", System.getProperty("java.version"));
    }

    /**
     * Print copyright information.
     */
    private static void copyright(PrintStream out) {
        out.println("Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This");
        out.println("code is released under a tri EPL/GPL/LGPL license. You can use it,");
        out.println("redistribute it and/or modify it under the terms of the:");
        out.println();
        out.println("Eclipse Public License version 1.0");
        out.println("GNU General Public License version 2");
        out.println("GNU Lesser General Public License version 2.1");
    }

}
