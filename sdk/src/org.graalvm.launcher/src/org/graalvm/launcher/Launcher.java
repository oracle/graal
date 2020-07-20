/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.launcher;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;

import org.graalvm.home.HomeFinder;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.RuntimeOptions;
import org.graalvm.nativeimage.RuntimeOptions.OptionClass;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;

public abstract class Launcher {
    private static final boolean STATIC_VERBOSE = Boolean.getBoolean("org.graalvm.launcher.verbose");
    private static final boolean SHELL_SCRIPT_LAUNCHER = Boolean.getBoolean("org.graalvm.launcher.shell");

    /**
     * Default option description indentation.
     */
    public static final int LAUNCHER_OPTIONS_INDENT = 45;

    static final boolean IS_AOT = Boolean.getBoolean("com.oracle.graalvm.isaot");

    public enum VMType {
        Native,
        JVM
    }

    final Native nativeAccess;
    private final boolean verbose;
    private PrintStream out = System.out;
    private PrintStream err = System.err;

    private boolean help;
    private boolean helpInternal;
    private boolean helpExpert;
    private boolean helpVM;

    /**
     * Path to the desired log file, or {@code null} if no log redirection is required.
     */
    private Path logFile;

    /**
     * Number of spaces reserved for the options column. Can be set separately for each option
     * block.
     */
    private int optionIndent = LAUNCHER_OPTIONS_INDENT;

    /**
     * Accumulates help categories and their relevant options.
     * 
     * @see #printOtherHelpCategory
     */
    private List<String> kindAndCategory = new ArrayList<>();

    protected enum VersionAction {
        None,
        PrintAndExit,
        PrintAndContinue
    }

    protected Launcher() {
        verbose = STATIC_VERBOSE || Boolean.valueOf(System.getenv("VERBOSE_GRAALVM_LAUNCHERS"));
        if (IS_AOT) {
            nativeAccess = new Native();
        } else {
            nativeAccess = null;
        }
    }

    /**
     * Provides the name of the log file, if specified on the command line.
     * 
     * @return log file Path. {@code null} if unspecified.
     * @since 20.0
     */
    protected final Path getLogFile() {
        return logFile;
    }

    /**
     * Uses the defined output to print messages.
     * 
     * @param ps printStream to use as out
     * @since 20.0
     */
    protected final void setOutput(PrintStream ps) {
        this.out = ps;
    }

    /**
     * Uses the defined output to print error messages.
     * 
     * @param ps printStream to use as err
     * @since 20.0
     */
    protected final void setError(PrintStream ps) {
        this.err = ps;
    }

    /**
     * @return the stream for regular output. Defaults to {@link System#out}
     * @since 20.0
     */
    protected final PrintStream getOutput() {
        return out;
    }

    /**
     * @return the stream for errors. Defaults to {@link System#err}
     * @since 20.0
     */
    protected final PrintStream getError() {
        return err;
    }

    void handleAbortException(AbortException e) {
        if (e.getMessage() != null) {
            err.println("ERROR: " + e.getMessage());
        }
        if (e.getCause() != null) {
            e.printStackTrace();
        }
        System.exit(e.getExitCode());
    }

    /**
     * Exception which shall abort the launcher execution. Thrown by this class in the case of
     * malformed arguments or unknown options, or deliberate exit.
     * 
     * @since 20.0
     */
    protected static final class AbortException extends RuntimeException {
        static final long serialVersionUID = 4681646279864737876L;
        private final int exitCode;

        AbortException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        AbortException(Throwable cause, int exitCode) {
            super(null, cause);
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * Exits the launcher, indicating success.
     *
     * This exits by throwing an {@link AbortException}.
     */
    protected final AbortException exit() {
        return exit(0);
    }

    /**
     * Exits the launcher with the provided exit code.
     *
     * This exits by throwing an {@link AbortException}.
     *
     * @param exitCode the exit code of the launcher process.
     */
    protected final AbortException exit(int exitCode) {
        return abort((String) null, exitCode);
    }

    /**
     * Exits the launcher, indicating failure.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param message an error message that will be printed to {@linkplain System#err stderr}. If
     *            null, nothing will be printed.
     */
    protected final AbortException abort(String message) {
        return abort(message, 1);
    }

    /**
     * Exits the launcher, with the provided exit code.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param message an error message that will be printed to {@linkplain System#err stderr}. If
     *            null, nothing will be printed.
     * @param exitCode the exit code of the launcher process.
     */
    @SuppressWarnings("static-method")
    protected final AbortException abort(String message, int exitCode) {
        throw new AbortException(message, exitCode);
    }

    /**
     * Exits the launcher, indicating failure because of the provided {@link Throwable}.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param t the exception that causes the launcher to abort.
     */
    protected final AbortException abort(Throwable t) {
        return abort(t, 255);
    }

    /**
     * Exits the launcher with the provided exit code because of the provided {@link Throwable}.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param t the exception that causes the launcher to abort.
     * @param exitCode the exit code of the launcher process.
     */
    protected final AbortException abort(Throwable t, int exitCode) {
        if (t.getCause() instanceof IOException && t.getClass() == RuntimeException.class) {
            String message = t.getMessage();
            if (message != null && !message.startsWith(t.getCause().getClass().getName() + ": ")) {
                err.println(message);
            }
            throw abort((IOException) t.getCause(), exitCode);
        }
        throw new AbortException(t, exitCode);
    }

    /**
     * Exits the launcher, indicating failure because of the provided {@link IOException}.
     *
     * This tries to build a helpful error message based on exception.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param e the exception that causes the launcher to abort.
     */
    protected final AbortException abort(IOException e) {
        return abort(e, 74);
    }

    /**
     * Exits the launcher with the provided exit code because of the provided {@link IOException}.
     *
     * This tries to build a helpful error message based on exception.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param e the exception that causes the launcher to abort.
     * @param exitCode the exit code of the launcher process
     */
    protected final AbortException abort(IOException e, int exitCode) {
        String message = e.getMessage();
        if (message != null) {
            if (e instanceof NoSuchFileException) {
                throw abort("Not such file: " + message, exitCode);
            } else if (e instanceof AccessDeniedException) {
                throw abort("Access denied: " + message, exitCode);
            } else {
                throw abort(message + " (" + e.getClass().getSimpleName() + ")", exitCode);
            }
        }
        throw abort((Throwable) e, exitCode);
    }

    /**
     * This is called to abort execution when an argument can neither be recognized by the launcher
     * or as an option for the polyglot engine.
     *
     * @param argument the argument that was not recognized.
     */
    protected AbortException abortUnrecognizedArgument(String argument) {
        throw abortInvalidArgument(argument, "Unrecognized argument: '" + argument + "'. Use --help for usage instructions.");
    }

    /**
     * Exits the launcher, indicating failure because of an invalid argument.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param argument the problematic argument.
     * @param message an error message that is printed to {@linkplain System#err stderr}.
     */
    protected final AbortException abortInvalidArgument(String argument, String message) {
        return abortInvalidArgument(argument, message, 2);
    }

    /**
     * Exits the launcher with the provided exit code because of an invalid argument.
     *
     * This aborts by throwing an {@link AbortException}.
     *
     * @param argument the problematic argument.
     * @param message an error message that is printed to {@linkplain System#err stderr}.
     * @param exitCode the exit code of the launcher process.
     */
    protected final AbortException abortInvalidArgument(String argument, String message, int exitCode) {
        Set<String> allArguments = collectAllArguments();
        int equalIndex;
        String testString = argument;
        if ((equalIndex = argument.indexOf('=')) != -1) {
            testString = argument.substring(0, equalIndex);
        }

        List<String> matches = fuzzyMatch(allArguments, testString, 0.7f);
        if (matches.isEmpty()) {
            // try even harder
            matches = fuzzyMatch(allArguments, testString, 0.5f);
        }

        StringBuilder sb = new StringBuilder();
        if (message != null) {
            sb.append(message);
        }
        if (!matches.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append("Did you mean one of the following arguments?").append(System.lineSeparator());
            Iterator<String> iterator = matches.iterator();
            while (true) {
                String match = iterator.next();
                sb.append("  ").append(match);
                if (iterator.hasNext()) {
                    sb.append(System.lineSeparator());
                } else {
                    break;
                }
            }
        }
        if (sb.length() > 0) {
            throw abort(sb.toString(), exitCode);
        } else {
            throw exit(exitCode);
        }
    }

    protected void warn(String message) {
        err.println("Warning: " + message);
    }

    protected void warn(String message, Object... args) {
        StringBuilder sb = new StringBuilder("Warning: ");
        new Formatter(sb).format(message, args);
        sb.append(System.lineSeparator());
        err.print(sb.toString());
    }

    /**
     * Sets the indentation for option descriptions. Sets number of spaces in the first column
     * reserved for option names. Defaults to {@link #LAUNCHER_OPTIONS_INDENT}.
     * 
     * @param indent the new indent.
     * @since 20.0
     */
    protected final void setOptionIndent(int indent) {
        if (indent < 0) {
            optionIndent = LAUNCHER_OPTIONS_INDENT;
        } else {
            optionIndent = indent;
        }
    }

    /**
     * Prints a help message to {@linkplain System#out stdout}. This only prints options that belong
     * to categories {@code maxCategory or less}.
     *
     * @param maxCategory the maximum category of options that should be printed.
     */
    protected abstract void printHelp(OptionCategory maxCategory);

    /**
     * Prints version information on {@linkplain System#out stdout}.
     */
    protected abstract void printVersion();

    /**
     * Add all known arguments to the {@code options} list.
     *
     * @param options list to which valid arguments must be added.
     */
    protected abstract void collectArguments(Set<String> options);

    /**
     * Finds the a descriptor for the option.
     * 
     * @param group option group
     * @param key the option name (including the group)
     * @return descriptor or {@code null}.
     * @since 20.0
     */
    protected abstract OptionDescriptor findOptionDescriptor(String group, String key);

    /**
     * Determines if the tool supports polyglot. Returns true, if {@code --polyglot} option is valid
     * for this tool and polyglot launcher works for it. The default implementation returns false
     * just when {@link #isStandalone()} is true.
     * 
     * @return {@code true}, if polyglot is relevant in this launcher.
     * @since 20.0
     */
    protected boolean canPolyglot() {
        return !isStandalone();
    }

    /**
     * Should print tool-specific help. Regular languages print info on the installed tools and
     * languages. The default implementation prints nothing.
     * 
     * @param helpCategory category of options to print
     * @since 20.0
     */
    protected void maybePrintAdditionalHelp(OptionCategory helpCategory) {
        // no op, no additional help printed.
    }

    private String executableName(String basename) {
        switch (OS.current) {
            case Linux:
            case Darwin:
            case Solaris:
                return basename;
            case Windows:
                return basename + ".exe";
            default:
                throw abort("executableName: OS not supported: " + OS.current);
        }
    }

    /**
     * Returns the name of the main class for this launcher.
     *
     * Typically:
     *
     * <pre>
     * return MyLauncher.class.getName();
     * </pre>
     */
    protected String getMainClass() {
        return this.getClass().getName();
    }

    /**
     * The return value specifies the default VM when none of --jvm, --native options is used.
     *
     * @return the default VMType
     */
    protected VMType getDefaultVMType() {
        return VMType.Native;
    }

    /**
     * Returns true if the current launcher was compiled ahead-of-time to native code.
     */
    public static boolean isAOT() {
        return IS_AOT;
    }

    private boolean isVerbose() {
        return verbose;
    }

    protected boolean isGraalVMAvailable() {
        return getGraalVMHome() != null;
    }

    protected boolean isStandalone() {
        return !isGraalVMAvailable();
    }

    private Path home;

    private OptionCategory getHelpCategory() {
        if (helpInternal) {
            return OptionCategory.INTERNAL;
        } else if (helpExpert) {
            return OptionCategory.EXPERT;
        } else {
            return OptionCategory.USER;
        }
    }

    protected Path getGraalVMHome() {
        if (home == null) {
            home = HomeFinder.getInstance().getHomeFolder();
        }
        return home;
    }

    /**
     * Returns filename of the binary, depending on OS. Binary will be searched in {@code bin} or
     * {@code jre/bin} directory.
     *
     * @param binaryName binary name, without path.
     * @return OS-dependent binary filename.
     */
    protected final Path getGraalVMBinaryPath(String binaryName) {
        String executableName = executableName(binaryName);
        Path graalVMHome = getGraalVMHome();
        if (graalVMHome == null) {
            throw abort("Can not exec to GraalVM binary: could not find GraalVM home");
        }
        Path jdkBin = graalVMHome.resolve("bin").resolve(executableName);
        if (Files.exists(jdkBin)) {
            return jdkBin;
        }
        return graalVMHome.resolve("jre").resolve("bin").resolve(executableName);
    }

    /**
     * Runs launcher's action as version print or help. Returns {@code true}, if the execution
     * should terminate, e.g. after printing help. {@link #parseCommonOption} should be called for
     * commandline argument(s) prior to this method to set up flags to display help etc.
     * 
     * @return {@code true} when execution should be terminated.
     * @since 20.0
     */
    protected boolean runLauncherAction() {
        boolean printDefaultHelp = help || ((helpExpert || helpInternal) && kindAndCategory.isEmpty() && !helpVM);
        OptionCategory hc = getHelpCategory();
        if (printDefaultHelp) {
            printDefaultHelp(hc);
        }
        maybePrintAdditionalHelp(hc);

        if (helpVM) {
            if (nativeAccess == null) {
                printJvmHelp();
            } else {
                nativeAccess.printNativeHelp();
            }
        }

        return printAllOtherHelpCategories(printDefaultHelp);
    }

    /**
     * Prints default help text. Prints options, starting with tool specific options. Launcher
     * implementations can override to provide launcher-specific intro / summary.
     * 
     * @param printCategory options category to print.
     * @since 20.0
     */
    protected void printDefaultHelp(OptionCategory printCategory) {
        final VMType defaultVMType = SHELL_SCRIPT_LAUNCHER ? VMType.JVM : this.getDefaultVMType();

        printHelp(printCategory);
        out.println();
        out.println("Runtime options:");

        setOptionIndent(45);
        if (canPolyglot()) {
            launcherOption("--polyglot", "Run with all other guest languages accessible.");
        }
        if (!SHELL_SCRIPT_LAUNCHER) {
            launcherOption("--native", "Run using the native launcher with limited Java access" + (defaultVMType == VMType.Native ? " (default)" : "") + ".");
        }
        if (!isStandalone()) {
            launcherOption("--jvm", "Run on the Java Virtual Machine with Java access" + (defaultVMType == VMType.JVM ? " (default)" : "") + ".");
        }
        // @formatter:off
        launcherOption("--vm.[option]",                 "Pass options to the host VM. To see available options, use '--help:vm'.");
        launcherOption("--log.file=<String>",           "Redirect guest languages logging into a given file.");
        launcherOption("--log.[logger].level=<String>", "Set language log level to OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST or ALL.");
        launcherOption("--help",                        "Print this help message.");
        launcherOption("--help:vm",                     "Print options for the host VM.");
        // @formatter:on
    }

    /**
     * Instructs that information about other help categories should be printed.
     * 
     * @param kind category kind name
     * @param option the option to print the category
     * @since 20.0
     */
    protected void printOtherHelpCategory(String kind, String option) {
        kindAndCategory.add(kind);
        kindAndCategory.add(option);
    }

    private boolean printAllOtherHelpCategories(boolean printHelp) {
        boolean print = printHelp || helpVM || !kindAndCategory.isEmpty();
        if (!print) {
            return false;
        }
        out.println();
        for (Iterator<String> it = kindAndCategory.iterator(); it.hasNext();) {
            String kind = it.next();
            String opt = it.next();
            printOtherHelpCategories0(kind, opt);
        }
        out.println("See http://www.graalvm.org for more information.");
        return true;
    }

    private void printOtherHelpCategories0(String kind, String option) {
        if (helpExpert || helpInternal) {
            out.println("Use '" + option + "' to list user " + kind + " options.");
        }
        if (!helpExpert) {
            out.println("Use '" + option + " --help:expert' to list expert " + kind + " options.");
        }
        if (!helpInternal) {
            out.println("Use '" + option + " --help:internal' to list internal " + kind + " options.");
        }
    }

    static String optionsTitle(String kind, OptionCategory optionCategory) {
        String category;
        switch (optionCategory) {
            case USER:
                category = "User ";
                break;
            case EXPERT:
                category = "Expert ";
                break;
            case INTERNAL:
                category = "Internal ";
                break;
            default:
                category = "";
                break;
        }
        return category + kind + " options:";
    }

    /**
     * Parses otherwise unrecognized options. Terminates the application if an option is not among
     * the generic launcher / VM ones.
     * 
     * @param defaultOptionPrefix (language) prefix for the options
     * @param polyglotOptions options being built for the polyglot launcher
     * @param unrecognizedArgs arguments (options) to evaluate
     * @since 20.0
     */
    protected final void parseUnrecognizedOptions(String defaultOptionPrefix, Map<String, String> polyglotOptions, List<String> unrecognizedArgs) {
        boolean experimentalOptions = false;
        // First, check if --experimental-options is passed
        for (String arg : unrecognizedArgs) {
            switch (arg) {
                case "--experimental-options":
                case "--experimental-options=true":
                    experimentalOptions = true;
                    break;
                case "--experimental-options=false":
                    experimentalOptions = false;
                    break;
            }
        }

        // Parse the arguments, now that we know whether experimental options are allowed
        for (String arg : unrecognizedArgs) {
            if (!parseCommonOption(defaultOptionPrefix, polyglotOptions, experimentalOptions, arg)) {
                parseJVMOptionOrFail(defaultOptionPrefix, polyglotOptions, experimentalOptions, arg);
            }
        }
    }

    /**
     * Parses an option, returning success. The method is called to parse `arg` option from the
     * commandline, not recognized by the application. The method may contribute to the
     * `polyglotOptions` (in/out parameter, modifiable) to alter polyglot behaviour. If the option
     * is recognized, the method must return {@code true}.
     * 
     * @param defaultOptionPrefix default prefix for the option names, derived from the launching
     *            application.
     * @param polyglotOptions options for polyglot engine
     * @param experimentalOptions true, if experimental options are explicitly allowed
     * @param arg argument to parse
     * @return true, if the option was recognized.
     * @since 20.0
     */
    protected boolean parseCommonOption(String defaultOptionPrefix, Map<String, String> polyglotOptions, boolean experimentalOptions, String arg) {
        switch (arg) {
            case "--help":
                help = true;
                break;
            case "--help:debug":
                warn("--help:debug is deprecated, use --help:internal instead.");
                helpInternal = true;
                break;
            case "--help:internal":
                helpInternal = true;
                break;
            case "--help:expert":
                helpExpert = true;
                break;
            case "--help:vm":
                helpVM = true;
                break;
            case "--experimental-options":
            case "--experimental-options=true":
            case "--experimental-options=false":
                // Ignore, these were already parsed before
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * Last-resort parsing. If the option is not VM/JVM one, the execution will fail. For parameter
     * description see
     * {@link #parseCommonOption(java.lang.String, java.util.Map, boolean, java.lang.String)}.
     */
    private void parseJVMOptionOrFail(String defaultOptionPrefix, Map<String, String> polyglotOptions, boolean experimentalOptions, String arg) {
        if ((arg.startsWith("--jvm.") && arg.length() > "--jvm.".length()) || arg.equals("--jvm")) {
            if (isAOT()) {
                throw abort("should not reach here: jvm option failed to switch to JVM");
            }
            return;
        } else if ((arg.startsWith("--native.") && arg.length() > "--native.".length()) || arg.equals("--native")) {
            if (!isAOT()) {
                throw abort("native options are not supported on the JVM");
            }
            return;
        } else if (arg.startsWith("--vm.") && arg.length() > "--vm.".length()) {
            return;
        }
        // getLanguageId() or null?
        if (arg.length() <= 2 || !arg.startsWith("--")) {
            throw abortUnrecognizedArgument(arg);
        }
        int eqIdx = arg.indexOf('=');
        String key;
        String value;
        if (eqIdx < 0) {
            key = arg.substring(2);
            value = null;
        } else {
            key = arg.substring(2, eqIdx);
            value = arg.substring(eqIdx + 1);
        }

        if (value == null) {
            value = "true";
        }
        int index = key.indexOf('.');
        String group = key;
        if (index >= 0) {
            group = group.substring(0, index);
        }
        if ("log".equals(group)) {
            if (key.endsWith(".level")) {
                try {
                    Level.parse(value);
                    polyglotOptions.put(key, value);
                } catch (IllegalArgumentException e) {
                    throw abort(String.format("Invalid log level %s specified. %s'", arg, e.getMessage()));
                }
                return;
            } else if (key.equals("log.file")) {
                logFile = Paths.get(value);
                return;
            }
        }
        OptionDescriptor descriptor = findOptionDescriptor(group, key);
        if (descriptor == null) {
            if (defaultOptionPrefix != null) {
                descriptor = findOptionDescriptor(defaultOptionPrefix, defaultOptionPrefix + "." + key);
            }
            if (descriptor == null) {
                throw abortUnrecognizedArgument(arg);
            }
        }
        try {
            descriptor.getKey().getType().convert(value);
        } catch (IllegalArgumentException e) {
            throw abort(String.format("Invalid argument %s specified. %s'", arg, e.getMessage()));
        }
        if (descriptor.isDeprecated()) {
            warn("Option '" + descriptor.getName() + "' is deprecated and might be removed from future versions.");
        }
        if (!experimentalOptions && descriptor.getStability() == OptionStability.EXPERIMENTAL) {
            throw abort(String.format("Option '%s' is experimental and must be enabled via '--experimental-options'%n" +
                            "Do not use experimental options in production environments.", arg));
        }
        // use the full name of the found descriptor
        polyglotOptions.put(descriptor.getName(), value);
    }

    private Set<String> collectAllArguments() {
        Set<String> options = new LinkedHashSet<>();
        collectArguments(options);
        if (canPolyglot()) {
            options.add("--polyglot");
        }
        if (!isStandalone()) {
            options.add("--jvm");
        }
        options.add("--native");
        options.add("--help");
        options.add("--help:expert");
        options.add("--help:internal");
        options.add("--help:vm");
        return options;
    }

    /**
     * Returns the set of options that fuzzy match a given option name.
     */
    private static List<String> fuzzyMatch(Set<String> arguments, String argument, float threshold) {
        List<String> matches = new ArrayList<>();
        for (String arg : arguments) {
            float score = stringSimilarity(arg, argument);
            if (score >= threshold) {
                matches.add(arg);
            }
        }
        return matches;
    }

    /**
     * Compute string similarity based on Dice's coefficient.
     *
     * Ported from str_similar() in globals.cpp.
     */
    private static float stringSimilarity(String str1, String str2) {
        int hit = 0;
        for (int i = 0; i < str1.length() - 1; ++i) {
            for (int j = 0; j < str2.length() - 1; ++j) {
                if ((str1.charAt(i) == str2.charAt(j)) && (str1.charAt(i + 1) == str2.charAt(j + 1))) {
                    ++hit;
                    break;
                }
            }
        }
        return 2.0f * hit / (str1.length() + str2.length());
    }

    /**
     * Prints a line for a launcher option. Uses indentation set by {@link #setOptionIndent} to
     * align option's description. If option name is too long, description is printed on the next
     * line, indented.
     * 
     * @param option option name, including dash(es)
     * @param description description
     * @since 20.0
     */
    protected void launcherOption(String option, String description) {
        printOption(option, description, 2, optionIndent);
    }

    private static String spaces(int length) {
        return new String(new char[length]).replace('\0', ' ');
    }

    private static String wrap(String s) {
        final int width = 120;
        StringBuilder sb = new StringBuilder(s);
        int cursor = 0;
        while (cursor + width < sb.length()) {
            int i = sb.lastIndexOf(" ", cursor + width);
            if (i == -1 || i < cursor) {
                i = sb.indexOf(" ", cursor + width);
            }
            if (i != -1) {
                sb.replace(i, i + 1, System.lineSeparator());
                cursor = i;
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private void printOption(String option, String description, int indentStart, int optionWidth) {
        String indent = spaces(indentStart);
        String desc = wrap(description != null ? description : "");
        String nl = System.lineSeparator();
        String[] descLines = desc.split(nl);
        if (option.length() >= optionWidth && description != null) {
            out.println(indent + option + nl + indent + spaces(optionWidth) + descLines[0]);
        } else {
            out.println(indent + option + spaces(optionWidth - option.length()) + descLines[0]);
        }
        for (int i = 1; i < descLines.length; i++) {
            out.println(indent + spaces(optionWidth) + descLines[i]);
        }
    }

    void printOption(PrintableOption option) {
        printOption(option, 2);
    }

    void printOption(PrintableOption option, int indentation) {
        printOption(option.option, option.description, indentation, optionIndent);
    }

    static final class PrintableOption implements Comparable<PrintableOption> {
        final String option;
        final String description;

        protected PrintableOption(String option, String description) {
            this.option = option;
            this.description = description;
        }

        @Override
        public int compareTo(PrintableOption o) {
            return this.option.compareTo(o.option);
        }
    }

    void printOptions(List<PrintableOption> options, String title, int indentation) {
        Collections.sort(options);
        out.println(title);
        for (PrintableOption option : options) {
            printOption(option, indentation);
        }
    }

    protected enum OS {
        Darwin,
        Linux,
        Solaris,
        Windows;

        private static OS findCurrent() {
            final String name = System.getProperty("os.name");
            if (name.equals("Linux")) {
                return Linux;
            }
            if (name.equals("SunOS")) {
                return Solaris;
            }
            if (name.equals("Mac OS X") || name.equals("Darwin")) {
                return Darwin;
            }
            if (name.startsWith("Windows")) {
                return Windows;
            }
            throw new IllegalArgumentException("unknown OS: " + name);
        }

        private static final OS current = findCurrent();

        public static OS getCurrent() {
            return current;
        }
    }

    private static void serializePolyglotOptions(Map<String, String> polyglotOptions, List<String> args) {
        if (polyglotOptions == null) {
            return;
        }
        for (Entry<String, String> entry : polyglotOptions.entrySet()) {
            args.add("--" + entry.getKey() + '=' + entry.getValue());
        }
    }

    /**
     * Prints a single line to the output stream, terminated with newline.
     * 
     * @param l line text.
     * @since 20.0
     */
    protected final void println(String l) {
        out.println(l);
    }

    /**
     * Prints sequence of lines to the output stream. Each argument will be printed as a whole line,
     * terminated by a newline.
     * 
     * @param lines lines
     * @since 20.0
     */
    protected final void println(String... lines) {
        for (String l : lines) {
            out.println(l);
        }
    }

    private void printJvmHelp() {
        println("JVM options:");
        launcherOption("--vm.classpath=<...>", "A " + File.pathSeparator + " separated list of classpath entries that will be added to the JVM's classpath");
        launcherOption("--vm.D<name>=<value>", "Set a system property");
        launcherOption("--vm.esa", "Enable system assertions");
        launcherOption("--vm.ea[:<packagename>...|:<classname>]", "Enable assertions with specified granularity");
        launcherOption("--vm.agentlib:<libname>[=<options>]", "Load native agent library <libname>");
        launcherOption("--vm.agentpath:<pathname>[=<options>]", "Load native agent library by full pathname");
        launcherOption("--vm.javaagent:<jarpath>[=<options>]", "Load Java programming language agent");
        launcherOption("--vm.Xbootclasspath/a:<...>", "A " + File.pathSeparator + " separated list of classpath entries that will be added to the JVM's boot classpath");
        launcherOption("--vm.Xmx<size>", "Set maximum Java heap size");
        launcherOption("--vm.Xms<size>", "Set initial Java heap size");
        launcherOption("--vm.Xss<size>", "Set java thread stack size");
    }

    private void printBasicNativeHelp() {
        launcherOption("--vm.D<property>=<value>", "Sets a system property");
        /* The default values are *copied* from com.oracle.svm.core.genscavenge.HeapPolicy */
        launcherOption("--vm.Xmn<value>", "Sets the maximum size of the young generation, in bytes. Default: 256MB.");
        launcherOption("--vm.Xmx<value>", "Sets the maximum size of the heap, in bytes. Default: MaximumHeapSizePercent * physical memory.");
        launcherOption("--vm.Xms<value>", "Sets the minimum size of the heap, in bytes. Default: 2 * maximum young generation size.");
        launcherOption("--vm.Xss<value>", "Sets the size of each thread stack, in bytes. Default: OS-dependent.");
    }

    private static final String CLASSPATH = System.getProperty("org.graalvm.launcher.classpath");

    /**
     * Possibly re-executes the launcher when JVM or polyglot mode is requested; call only if
     * {@link #isAOT()} is true.
     * 
     * The method parses VM arguments, if JVM mode is requested, it execs a Java process configured
     * with supported JVM parameters and system properties over this process - in this case, the
     * method does not return (except errors).
     * 
     * @param args
     * @param isPolyglot
     * @param polyglotOptions
     * @since 20.0
     */
    protected final void maybeNativeExec(List<String> args, boolean isPolyglot, Map<String, String> polyglotOptions) {
        if (!IS_AOT) {
            return;
        }
        maybeExec(args, isPolyglot, polyglotOptions, getDefaultVMType());
    }

    void maybeExec(List<String> args, boolean isPolyglot, Map<String, String> polyglotOptions, VMType defaultVmType) {
        assert isAOT();
        VMType vmType = null;
        boolean polyglot = false;
        List<String> jvmArgs = new ArrayList<>();
        List<String> remainingArgs = new ArrayList<>(args.size());

        // move jvm polyglot options to jvmArgs
        Iterator<Entry<String, String>> polyglotOptionsIterator = polyglotOptions.entrySet().iterator();
        while (polyglotOptionsIterator.hasNext()) {
            Map.Entry<String, String> entry = polyglotOptionsIterator.next();
            if (entry.getKey().startsWith("jvm.")) {
                jvmArgs.add('-' + entry.getKey().substring(4));
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    jvmArgs.add(entry.getValue());
                }
                vmType = VMType.JVM;
                polyglotOptionsIterator.remove();
            }
        }

        boolean jvmDotWarned = false;

        Iterator<String> iterator = args.iterator();
        List<String> vmOptions = new ArrayList<>();
        while (iterator.hasNext()) {
            String arg = iterator.next();
            if ((arg.startsWith("--jvm.") && arg.length() > "--jvm.".length()) || arg.equals("--jvm")) {
                if (vmType == VMType.Native) {
                    throw abort("'--jvm' and '--native' options can not be used together.");
                }
                if (isStandalone()) {
                    if (arg.equals("--jvm")) {
                        throw abort("'--jvm' is only supported when this launcher is part of a GraalVM.");
                    } else {
                        throw abort("'--jvm.*' options are deprecated and only supported when this launcher is part of a GraalVM.");
                    }
                }
                vmType = VMType.JVM;
                if (arg.equals("--jvm.help")) {
                    if (defaultVmType == VMType.JVM) {
                        warn("'--jvm.help' is deprecated, use '--help:vm' instead.");
                    } else {
                        warn("'--jvm.help' is deprecated, use '--jvm --help:vm' instead.");
                    }
                    remainingArgs.add("--help:vm");
                } else if (arg.startsWith("--jvm.")) {
                    if (!jvmDotWarned) {
                        warn("'--jvm.*' options are deprecated, use '--vm.*' instead.");
                        jvmDotWarned = true;
                    }
                    String jvmArg = arg.substring("--jvm.".length());
                    if (jvmArg.equals("classpath")) {
                        throw abort("'--jvm.classpath' argument must be of the form '--jvm.classpath=<classpath>', not two separate arguments");
                    }
                    if (jvmArg.equals("cp")) {
                        throw abort("'--jvm.cp' argument must be of the form '--jvm.cp=<classpath>', not two separate arguments");
                    }
                    if (jvmArg.startsWith("classpath=") || jvmArg.startsWith("cp=")) {
                        int eqIndex = jvmArg.indexOf('=');
                        jvmArgs.add('-' + jvmArg.substring(0, eqIndex));
                        jvmArgs.add(jvmArg.substring(eqIndex + 1));
                    } else {
                        jvmArgs.add('-' + jvmArg);
                    }
                }
                iterator.remove();
            } else if ((arg.startsWith("--native.") && arg.length() > "--native.".length()) || arg.equals("--native")) {
                if (vmType == VMType.JVM) {
                    throw abort("'--jvm' and '--native' options can not be used together.");
                }
                vmType = VMType.Native;
                if (arg.equals("--native.help")) {
                    if (defaultVmType == VMType.Native) {
                        warn("'--native.help' is deprecated, use '--help:vm' instead.");
                    } else {
                        warn("'--native.help' is deprecated, use '--native --help:vm' instead.");
                    }
                    remainingArgs.add("--help:vm");
                } else if (arg.startsWith("--native.")) {
                    if (!jvmDotWarned) {
                        warn("'--native.*' options are deprecated, use '--vm.*' instead.");
                        jvmDotWarned = true;
                    }
                    vmOptions.add(arg.substring("--native.".length()));
                }
                iterator.remove();
            } else if (arg.startsWith("--vm.") && arg.length() > "--vm.".length()) {
                if (arg.equals("--vm.help")) {
                    warn("'--vm.help' is deprecated, use '--help:vm' instead.");
                    remainingArgs.add("--help:vm");
                }
                String vmArg = arg.substring("--vm.".length());
                if (vmArg.equals("classpath")) {
                    throw abort("'--vm.classpath' argument must be of the form '--vm.classpath=<classpath>', not two separate arguments");
                }
                if (vmArg.equals("cp")) {
                    throw abort("'--vm.cp' argument must be of the form '--vm.cp=<classpath>', not two separate arguments");
                }
                if (vmArg.startsWith("classpath=") || vmArg.startsWith("cp=")) {
                    int eqIndex = vmArg.indexOf('=');
                    jvmArgs.add('-' + vmArg.substring(0, eqIndex));
                    jvmArgs.add(vmArg.substring(eqIndex + 1));
                } else {
                    vmOptions.add(vmArg);
                }
                iterator.remove();
            } else if (arg.equals("--polyglot")) {
                polyglot = true;
            } else {
                remainingArgs.add(arg);
            }
        }
        boolean isDefaultVMType = false;
        if (vmType == null) {
            vmType = defaultVmType;
            isDefaultVMType = true;
        }

        if (vmType == VMType.JVM) {
            for (String vmOption : vmOptions) {
                jvmArgs.add('-' + vmOption);
            }

            if (!isPolyglot && polyglot) {
                remainingArgs.add(0, "--polyglot");
            }
            assert !isStandalone();
            executeJVM(nativeAccess == null ? System.getProperty("java.class.path") : nativeAccess.getClasspath(jvmArgs), jvmArgs, remainingArgs, polyglotOptions);
        } else {
            assert vmType == VMType.Native;

            for (String vmOption : vmOptions) {
                nativeAccess.setNativeOption(vmOption);
            }
            /*
             * All options are processed, now we can run the startup hooks that can depend on the
             * option values.
             */
            VMRuntime.initialize();

            if (!isPolyglot && polyglot) {
                assert jvmArgs.isEmpty();
                if (isStandalone()) {
                    throw abort("--polyglot option is only supported when this launcher is part of a GraalVM.");
                }
                executePolyglot(remainingArgs, polyglotOptions, !isDefaultVMType);
            }
        }
    }

    /**
     * Called if a JVM has to be started instead of AOT binary. The method is only called in AOT
     * mode. Subclasses may override to apply different options or launch mechanism
     *
     * @param jvmArgs arguments for the VM
     * @param remainingArgs main arguments
     * @param polyglotOptions additional polyglot-related options
     * @param classpath class path to be used with the JVM
     */
    protected void executeJVM(String classpath, List<String> jvmArgs, List<String> remainingArgs, Map<String, String> polyglotOptions) {
        nativeAccess.execJVM(classpath, jvmArgs, remainingArgs, polyglotOptions);
    }

    /**
     * Called to execute polyglot binary with the supplied options. Subclasses may eventually
     * override and implement in a different way.
     *
     * @param mainArgs program arguments
     * @param polyglotOptions polyglot options
     */
    protected void executePolyglot(List<String> mainArgs, Map<String, String> polyglotOptions, boolean forceNative) {
        nativeAccess.executePolyglot(mainArgs, polyglotOptions, forceNative);
    }

    class Native {
        // execve() to JVM/polyglot from native if needed.
        // Only parses --jvm/--native to find the VMType and --vm.* to pass/set the VM options.
        private WeakReference<OptionDescriptors> compilerOptionDescriptors;
        private WeakReference<OptionDescriptors> vmOptionDescriptors;

        private OptionDescriptors getCompilerOptions() {
            OptionDescriptors descriptors = null;
            if (compilerOptionDescriptors != null) {
                descriptors = compilerOptionDescriptors.get();
            }
            if (descriptors == null) {
                descriptors = RuntimeOptions.getOptions(EnumSet.of(OptionClass.Compiler));
                compilerOptionDescriptors = new WeakReference<>(descriptors);
            }
            return descriptors;
        }

        private OptionDescriptors getVMOptions() {
            OptionDescriptors descriptors = null;
            if (vmOptionDescriptors != null) {
                descriptors = vmOptionDescriptors.get();
            }
            if (descriptors == null) {
                descriptors = RuntimeOptions.getOptions(EnumSet.of(OptionClass.VM));
                vmOptionDescriptors = new WeakReference<>(descriptors);
            }
            return descriptors;
        }

        private void setNativeOption(String arg) {
            if (arg.startsWith("Dgraal.")) {
                setGraalStyleRuntimeOption(arg.substring("Dgraal.".length()));
            } else if (arg.startsWith("D")) {
                setSystemProperty(arg.substring("D".length()));
            } else if (arg.startsWith("XX:")) {
                setRuntimeOption(arg.substring("XX:".length()));
            } else if (arg.startsWith("X")) {
                if (isXOption(arg)) {
                    setXOption(arg.substring("X".length()));
                } else {
                    throw abort("Unrecognized vm option: '--vm." + arg + "'. Some VM options may be only supported in --jvm mode.");
                }
            } else {
                throw abort("Unrecognized vm option: '--vm." + arg + "'. Such arguments should start with '--vm.D', '--vm.XX:', or '--vm.X'");
            }
        }

        private void setGraalStyleRuntimeOption(String arg) {
            if (arg.startsWith("+") || arg.startsWith("-")) {
                throw abort("Dgraal option must use <name>=<value> format, not +/- prefix");
            }
            int eqIdx = arg.indexOf('=');
            String key;
            String value;
            if (eqIdx < 0) {
                key = arg;
                value = "";
            } else {
                key = arg.substring(0, eqIdx);
                value = arg.substring(eqIdx + 1);
            }
            OptionDescriptor descriptor = getCompilerOptions().get(key);
            if (descriptor == null) {
                descriptor = getVMOptions().get(key);
                if (descriptor != null) {
                    if (isBooleanOption(descriptor)) {
                        warn("VM options such as '%s' should be set with '--vm.XX:\u00b1%<s'.%n" +
                                        "Support for setting them with '--vm.Dgraal.%<s=<value>' is deprecated and will be removed.%n", key);
                    } else {
                        warn("VM options such as '%s' should be set with '--vm.XX:%<s=<value>'.%n" +
                                        "Support for setting them with '--vm.Dgraal.%<s=<value>' is deprecated and will be removed.%n", key);
                    }
                }
            }
            if (descriptor == null) {
                throw unknownOption(key);
            }
            try {
                RuntimeOptions.set(key, descriptor.getKey().getType().convert(value));
            } catch (IllegalArgumentException iae) {
                throw abort("Invalid argument: '--vm." + arg + "': " + iae.getMessage());
            }
        }

        public void setSystemProperty(String arg) {
            int eqIdx = arg.indexOf('=');
            String key;
            String value;
            if (eqIdx < 0) {
                key = arg;
                value = "";
            } else {
                key = arg.substring(0, eqIdx);
                value = arg.substring(eqIdx + 1);
            }
            System.setProperty(key, value);
        }

        public void setRuntimeOption(String arg) {
            int eqIdx = arg.indexOf('=');
            String key;
            Object value;
            if (arg.startsWith("+") || arg.startsWith("-")) {
                key = arg.substring(1);
                if (eqIdx >= 0) {
                    throw abort("Invalid argument: '--vm." + arg + "': Use either +/- or =, but not both");
                }
                OptionDescriptor descriptor = getVMOptionDescriptor(key);
                if (!isBooleanOption(descriptor)) {
                    throw abort("Invalid argument: " + key + " is not a boolean option, set it with --vm.XX:" + key + "=<value>.");
                }
                value = arg.startsWith("+");
            } else if (eqIdx > 0) {
                key = arg.substring(0, eqIdx);
                OptionDescriptor descriptor = getVMOptionDescriptor(key);
                if (isBooleanOption(descriptor)) {
                    throw abort("Boolean option '" + key + "' must be set with +/- prefix, not <name>=<value> format.");
                }
                try {
                    value = descriptor.getKey().getType().convert(arg.substring(eqIdx + 1));
                } catch (IllegalArgumentException iae) {
                    throw abort("Invalid argument: '--vm." + arg + "': " + iae.getMessage());
                }
            } else {
                throw abort("Invalid argument: '--vm." + arg + "'. Prefix boolean options with + or -, suffix other options with <name>=<value>");
            }
            RuntimeOptions.set(key, value);
        }

        private OptionDescriptor getVMOptionDescriptor(String key) {
            OptionDescriptor descriptor = getVMOptions().get(key);
            if (descriptor == null) {
                descriptor = getCompilerOptions().get(key);
                if (descriptor != null) {
                    warn("compiler options such as '%s' should be set with '--vm.Dgraal.%<s=<value>'.%n" +
                                    "Support for setting them with '--vm.XX:...' is deprecated and will be removed.%n", key);
                }
            }
            if (descriptor == null) {
                throw unknownOption(key);
            }
            return descriptor;
        }

        /* Is an option that starts with an 'X' one of the recognized X options? */
        private boolean isXOption(String arg) {
            return (arg.startsWith("Xmn") || arg.startsWith("Xms") || arg.startsWith("Xmx") || arg.startsWith("Xss"));
        }

        /* Set a `-X` option, given something like "mx2g". */
        private void setXOption(String arg) {
            try {
                RuntimeOptions.set(arg, null);
            } catch (RuntimeException re) {
                throw abort("Invalid argument: '--vm.X" + arg + "' does not specify a valid number.");
            }
        }

        private boolean isBooleanOption(OptionDescriptor descriptor) {
            return descriptor.getKey().getType().equals(OptionType.defaultType(Boolean.class));
        }

        private AbortException unknownOption(String key) {
            throw abort("Unknown native option: " + key + ". Use --help:vm to list available options.");
        }

        private void printNativeHelp() {
            System.out.println("Native VM options:");
            SortedMap<String, OptionDescriptor> sortedOptions = new TreeMap<>();
            for (OptionDescriptor descriptor : getVMOptions()) {
                if (!descriptor.isDeprecated()) {
                    sortedOptions.put(descriptor.getName(), descriptor);
                }
            }
            for (Entry<String, OptionDescriptor> entry : sortedOptions.entrySet()) {
                OptionDescriptor descriptor = entry.getValue();
                String helpMsg = descriptor.getHelp();
                if (isBooleanOption(descriptor)) {
                    Boolean val = (Boolean) descriptor.getKey().getDefaultValue();
                    if (helpMsg.length() != 0) {
                        helpMsg += ' ';
                    }
                    if (val == null || !val) {
                        helpMsg += "Default: - (disabled).";
                    } else {
                        helpMsg += "Default: + (enabled).";
                    }
                    launcherOption("--vm.XX:\u00b1" + entry.getKey(), helpMsg);
                } else {
                    Object def = descriptor.getKey().getDefaultValue();
                    if (def instanceof String) {
                        def = "\"" + def + "\"";
                    }
                    launcherOption("--vm.XX:" + entry.getKey() + "=" + def, helpMsg);
                }
            }
            printCompilerOptions();
            printBasicNativeHelp();
        }

        private void printCompilerOptions() {
            System.out.println("Compiler options:");
            SortedMap<String, OptionDescriptor> sortedOptions = new TreeMap<>();
            for (OptionDescriptor descriptor : getCompilerOptions()) {
                if (!descriptor.isDeprecated()) {
                    sortedOptions.put(descriptor.getName(), descriptor);
                }
            }
            for (Entry<String, OptionDescriptor> entry : sortedOptions.entrySet()) {
                OptionDescriptor descriptor = entry.getValue();
                String helpMsg = descriptor.getHelp();
                Object def = descriptor.getKey().getDefaultValue();
                if (def instanceof String) {
                    def = '"' + (String) def + '"';
                }
                launcherOption("--vm.Dgraal." + entry.getKey() + "=" + def, helpMsg);
            }
        }

        private void executePolyglot(List<String> args, Map<String, String> polyglotOptions, boolean forceNative) {
            List<String> command = new ArrayList<>(args.size() + (polyglotOptions == null ? 0 : polyglotOptions.size()) + 3);
            Path executable = getGraalVMBinaryPath("polyglot");
            if (forceNative) {
                command.add("--native");
            }
            command.add("--use-launcher");
            command.add(getMainClass());
            serializePolyglotOptions(polyglotOptions, command);
            command.addAll(args);
            exec(executable, command);
        }

        private void execJVM(String classpath, List<String> jvmArgs, List<String> args, Map<String, String> polyglotOptions) {
            // TODO use String[] for command to avoid a copy later
            List<String> command = new ArrayList<>(jvmArgs.size() + args.size() + (polyglotOptions == null ? 0 : polyglotOptions.size()) + 4);
            Path executable = getGraalVMBinaryPath("java");
            if (classpath != null) {
                command.add("-classpath");
                command.add(classpath);
            }
            command.addAll(jvmArgs);
            command.add(getMainClass());
            serializePolyglotOptions(polyglotOptions, command);
            command.addAll(args);
            exec(executable, command);
        }

        private String getClasspath(List<String> jvmArgs) {
            assert isAOT();
            assert CLASSPATH != null;
            StringBuilder sb = new StringBuilder();
            if (!CLASSPATH.isEmpty()) {
                Path graalVMHome = getGraalVMHome();
                if (graalVMHome == null) {
                    throw abort("Can not resolve classpath: could not get GraalVM home");
                }
                for (String entry : CLASSPATH.split(File.pathSeparator)) {
                    Path resolved = graalVMHome.resolve(entry);
                    if (isVerbose() && !Files.exists(resolved)) {
                        warn("%s does not exist", resolved);
                    }
                    sb.append(resolved);
                    sb.append(File.pathSeparatorChar);
                }
            }
            String classpathFromArgs = null;
            Iterator<String> iterator = jvmArgs.iterator();
            while (iterator.hasNext()) {
                String jvmArg = iterator.next();
                if (jvmArg.equals("-cp") || jvmArg.equals("-classpath")) {
                    if (iterator.hasNext()) {
                        iterator.remove();
                        classpathFromArgs = iterator.next();
                        iterator.remove();
                        // no break, pick the last one
                    }
                }
                if (jvmArg.startsWith("-Djava.class.path=")) {
                    iterator.remove();
                    classpathFromArgs = jvmArg.substring("-Djava.class.path=".length());
                }
            }
            if (classpathFromArgs != null) {
                sb.append(classpathFromArgs);
                sb.append(File.pathSeparatorChar);
            }
            if (sb.length() == 0) {
                return null;
            }
            return sb.substring(0, sb.length() - 1);
        }

        private void exec(Path executable, List<String> command) {
            assert isAOT();
            if (isVerbose()) {
                StringBuilder sb = formatExec(executable, command);
                err.print(sb.toString());
            }
            String[] argv = new String[command.size() + 1];
            int i = 0;
            Path filename = executable.getFileName();
            if (filename == null) {
                throw abort(String.format("Cannot determine execute filename from path %s", filename));
            }
            argv[i++] = filename.toString();
            for (String arg : command) {
                argv[i++] = arg;
            }
            ProcessProperties.exec(executable, argv);
        }

        private StringBuilder formatExec(Path executable, List<String> command) {
            StringBuilder sb = new StringBuilder("exec: ");
            sb.append(executable);
            for (String arg : command) {
                sb.append(' ');
                sb.append(ShellQuotes.quote(arg));
            }
            sb.append(System.lineSeparator());
            return sb;
        }
    }

    private static final class ShellQuotes {
        private static final BitSet safeChars;
        static {
            safeChars = new BitSet();
            safeChars.set('a', 'z' + 1);
            safeChars.set('A', 'Z' + 1);
            safeChars.set('+', ':' + 1); // +,-./0..9:
            safeChars.set('@');
            safeChars.set('%');
            safeChars.set('_');
            safeChars.set('=');
        }

        private static String quote(String str) {
            if (str.isEmpty()) {
                return "''";
            }
            for (int i = 0; i < str.length(); i++) {
                if (!safeChars.get(str.charAt(i))) {
                    return "'" + str.replace("'", "'\"'\"'") + "'";
                }
            }
            return str;
        }
    }

    /**
     * Creates a new log file. The method uses a supplemental lock file to determine the file is
     * still opened for output; in that case, it creates a different file, named `path'1, `path`2,
     * ... until it finds a free name. Files not locked (actively written to) are overwritten.
     * 
     * @param path the desired output for log
     * @return the OutputStream for logging
     * @throws IOException in case of I/O error opening the file
     * @since 20.0
     */
    protected static OutputStream newLogStream(Path path) throws IOException {
        Path usedPath = path;
        Path lockFile = null;
        FileChannel lockFileChannel = null;
        for (int unique = 0;; unique++) {
            StringBuilder lockFileNameBuilder = new StringBuilder();
            lockFileNameBuilder.append(path.toString());
            if (unique > 0) {
                lockFileNameBuilder.append(unique);
                usedPath = Paths.get(lockFileNameBuilder.toString());
            }
            lockFileNameBuilder.append(".lck");
            lockFile = Paths.get(lockFileNameBuilder.toString());
            Map.Entry<FileChannel, Boolean> openResult = openChannel(lockFile);
            if (openResult != null) {
                lockFileChannel = openResult.getKey();
                if (lock(lockFileChannel, openResult.getValue())) {
                    break;
                } else {
                    // Close and try next name
                    lockFileChannel.close();
                }
            }
        }
        assert lockFile != null && lockFileChannel != null;
        boolean success = false;
        try {
            OutputStream stream = new LockableOutputStream(
                            new BufferedOutputStream(Files.newOutputStream(usedPath, WRITE, CREATE, APPEND)),
                            lockFile,
                            lockFileChannel);
            success = true;
            return stream;
        } finally {
            if (!success) {
                LockableOutputStream.unlock(lockFile, lockFileChannel);
            }
        }
    }

    private static Map.Entry<FileChannel, Boolean> openChannel(Path path) throws IOException {
        FileChannel channel = null;
        for (int retries = 0; channel == null && retries < 2; retries++) {
            try {
                channel = FileChannel.open(path, CREATE_NEW, WRITE);
                return new AbstractMap.SimpleImmutableEntry<>(channel, true);
            } catch (FileAlreadyExistsException faee) {
                // Maybe a FS race showing a zombie file, try to reuse it
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && isParentWritable(path)) {
                    try {
                        channel = FileChannel.open(path, WRITE, APPEND);
                        return new AbstractMap.SimpleImmutableEntry<>(channel, false);
                    } catch (NoSuchFileException x) {
                        // FS Race, next try we should be able to create with CREATE_NEW
                    } catch (IOException x) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    private static boolean isParentWritable(Path path) {
        Path parentPath = path.getParent();
        if (parentPath == null && !path.isAbsolute()) {
            parentPath = path.toAbsolutePath().getParent();
        }
        return parentPath != null && Files.isWritable(parentPath);
    }

    private static boolean lock(FileChannel lockFileChannel, boolean newFile) {
        boolean available = false;
        try {
            available = lockFileChannel.tryLock() != null;
        } catch (OverlappingFileLockException ofle) {
            // VM already holds lock continue with available set to false
        } catch (IOException ioe) {
            // Locking not supported by OS
            available = newFile;
        }
        return available;
    }

    private static final class LockableOutputStream extends OutputStream {

        private final OutputStream delegate;
        private final Path lockFile;
        private final FileChannel lockFileChannel;

        LockableOutputStream(OutputStream delegate, Path lockFile, FileChannel lockFileChannel) {
            this.delegate = delegate;
            this.lockFile = lockFile;
            this.lockFileChannel = lockFileChannel;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                unlock(lockFile, lockFileChannel);
            }
        }

        private static void unlock(Path lockFile, FileChannel lockFileChannel) {
            try {
                lockFileChannel.close();
            } catch (IOException ioe) {
                // Error while closing the channel, ignore.
            }
            try {
                Files.delete(lockFile);
            } catch (IOException ioe) {
                // Error while deleting the lock file, ignore.
            }
        }
    }
}
