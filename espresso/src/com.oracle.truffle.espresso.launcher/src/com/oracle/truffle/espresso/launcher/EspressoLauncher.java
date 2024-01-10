/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.launcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/**
 * A simple emulation of the {@code java} launcher that parses the command line flags and builds and
 * Espresso context based on them. This code isn't used by anyone except Espresso developers,
 * because in a shipped Espresso the VM is started via {@code mokapot} instead (see
 * {@code hacking.md} for details).
 */
public final class EspressoLauncher extends AbstractLanguageLauncher {
    private static final String AGENT_LIB = "java.AgentLib.";
    private static final String AGENT_PATH = "java.AgentPath.";
    private static final String JAVA_AGENT = "java.JavaAgent";

    public static void main(String[] args) {
        new EspressoLauncher().launch(args);
    }

    private final ArrayList<String> mainClassArgs = new ArrayList<>();
    private String mainClassName = null;
    private LaunchMode launchMode = LaunchMode.LM_CLASS;
    private boolean pauseOnExit = false;
    private VersionAction versionAction = VersionAction.None;
    private final Map<String, String> espressoOptions = new HashMap<>();

    private final class Arguments {
        private final List<String> arguments;
        private int index = -1;
        private String currentKey;
        private String currentArgument;

        private boolean skip = false;

        Arguments(List<String> arguments) {
            this.arguments = arguments;
        }

        /**
         * Returns the raw argument at the current position.
         * <p>
         * If the option given is not of the form {@code --[option]=[value]}, then the value
         * returned by {@code getArg} is equal to the value returned by {@link #getKey()}. Else,
         * unlike {@link #getKey()}, this method does not strip the option at the {@code "="}.
         * <p>
         * For example:
         * <li>{@code -ea}:
         * <p>
         * both {@code getKey} and {@code getArg} returns "-ea".
         * <li>{@code --module=path}:
         * <p>
         * {@code getKey} returns "--module", while {@code getArg} returns "--module=path".
         */
        String getArg() {
            String val = arguments.get(index);
            assert val.startsWith(getKey());
            return val;
        }

        /**
         * Returns the key associated with the argument at the current position.
         * <p>
         * If the argument is of the form {@code --[option]=[value]} or {@code --[option] [value]},
         * returns {@code --[option]}.
         */
        String getKey() {
            if (currentKey == null) {
                String arg = arguments.get(index);
                if (arg.startsWith("--")) {
                    int eqIdx = arg.indexOf('=');
                    if (eqIdx >= 0) {
                        currentKey = arg.substring(0, eqIdx);
                        currentArgument = arg.substring(eqIdx + 1);
                    }
                }
                if (currentKey == null) {
                    currentKey = arg;
                }
            }
            return currentKey;
        }

        /**
         * Returns the value associated with the argument at the current position.
         * <p>
         * If the argument is of the form {@code --[option]=[value]} or {@code --[option] [value]},
         * returns {@code [value]}.
         */
        String getValue(String arg, String type) {
            if (currentArgument == null) {
                if (index + 1 < arguments.size()) {
                    currentArgument = arguments.get(index + 1);
                    skip = true;
                } else {
                    throw abort("Error: " + arg + " requires " + type + " specification");
                }
            }
            return currentArgument;
        }

        /**
         * Advances the position, skipping over the value associated with an option if needed.
         *
         * @return true if there are still arguments to process, false otherwise.
         */
        boolean next() {
            index++;
            if (skip) {
                index++;
            }
            currentKey = null;
            currentArgument = null;
            skip = false;
            return index < arguments.size();
        }

        int getNumberOfProcessedArgs() {
            return index + ((currentKey == null) ? 0 : 1 /* arg in processing */);
        }

        void pushLeftoversArgs() {
            if (currentKey != null) {
                // Arg in processing: start from the next one.
                next();
            }
            if (index < arguments.size()) {
                mainClassArgs.addAll(arguments.subList(index, arguments.size()));
            }
            // Finish processing args.
            index = arguments.size();
        }
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> unused) {
        String classpath = null;
        String jarFileName = null;
        ArrayList<String> unrecognized = new ArrayList<>();
        boolean isRelaxStaticObjectSafetyChecksSet = false;

        Arguments args = new Arguments(arguments);
        while (args.next()) {
            String arg = args.getKey();
            switch (arg) {
                case "-cp":
                case "-classpath":
                    classpath = args.getValue(arg, "class path");
                    break;
                case "-p":
                case "--module-path":
                    parseSpecifiedOption(args, "java.ModulePath", "module path");
                    break;
                case "--add-modules":
                    parseNumberedOption(args, "java.AddModules", "module");
                    break;
                case "--add-exports":
                    parseNumberedOption(args, "java.AddExports", "module");
                    break;
                case "--add-opens":
                    parseNumberedOption(args, "java.AddOpens", "module");
                    break;
                case "--add-reads":
                    parseNumberedOption(args, "java.AddReads", "module");
                    break;
                case "--enable-native-access":
                    parseNumberedOption(args, "java.EnableNativeAccess", "module");
                    break;
                case "-m":
                case "--module":
                    /* This arguments specifies in which module we find the main class. */
                    mainClassName = args.getValue(arg, "module path");
                    espressoOptions.put("java.Module", mainClassName);
                    launchMode = LaunchMode.LM_MODULE;
                    break;
                case "-jar":
                    jarFileName = args.getValue(arg, "jar file");
                    break;
                case "-version":
                    versionAction = VersionAction.PrintAndExit;
                    break;
                case "-showversion":
                case "--show-version":
                    versionAction = VersionAction.PrintAndContinue;
                    break;

                case "-ea":
                case "-enableassertions":
                    espressoOptions.put("java.EnableAssertions", "true");
                    break;

                case "-esa":
                case "-enablesystemassertions":
                    espressoOptions.put("java.EnableSystemAssertions", "true");
                    break;

                case "-?":
                case "-help":
                    unrecognized.add("--help");
                    break;

                case "-client":
                case "-server":
                case "-truffle":
                case "-d64":
                case "-Xdebug": // only for backward compatibility
                    // ignore
                    break;
                case "-Xcomp":
                    espressoOptions.put("engine.CompileImmediately", "true");
                    break;
                case "-Xbatch":
                    espressoOptions.put("engine.BackgroundCompilation", "false");
                    espressoOptions.put("engine.CompileImmediately", "true");
                    break;
                case "-Xint":
                    espressoOptions.put("engine.Compilation", "false");
                    break;

                case "-XX:+PauseOnExit":
                    pauseOnExit = true;
                    break;

                case "--engine.RelaxStaticObjectSafetyChecks":
                    isRelaxStaticObjectSafetyChecksSet = true;
                    unrecognized.add(args.getArg());
                    break;

                case "--enable-preview":
                    espressoOptions.put("java.EnablePreview", "true");
                    break;

                default:
                    if (arg.startsWith("-Xbootclasspath:")) {
                        espressoOptions.remove("java.BootClasspathPrepend");
                        espressoOptions.remove("java.BootClasspathAppend");
                        espressoOptions.put("java.BootClasspath", arg.substring("-Xbootclasspath:".length()));
                    } else if (arg.startsWith("-Xbootclasspath/a:")) {
                        espressoOptions.put("java.BootClasspathAppend", appendPath(espressoOptions.get("java.BootClasspathAppend"), arg.substring("-Xbootclasspath/a:".length())));
                    } else if (arg.startsWith("-Xbootclasspath/p:")) {
                        espressoOptions.put("java.BootClasspathPrepend", prependPath(arg.substring("-Xbootclasspath/p:".length()), espressoOptions.get("java.BootClasspathPrepend")));
                    } else if (arg.startsWith("-Xverify:")) {
                        String mode = arg.substring("-Xverify:".length());
                        espressoOptions.put("java.Verify", mode);
                    } else if (arg.startsWith("-Xrunjdwp:")) {
                        String value = arg.substring("-Xrunjdwp:".length());
                        espressoOptions.put("java.JDWPOptions", value);
                    } else if (arg.startsWith("-agentlib:jdwp=")) {
                        String value = arg.substring("-agentlib:jdwp=".length());
                        espressoOptions.put("java.JDWPOptions", value);
                    } else if (arg.startsWith("-javaagent:")) {
                        String value = arg.substring("-javaagent:".length());
                        espressoOptions.put(JAVA_AGENT, value);
                        mergeOption("java.AddModules", "java.instrument");
                    } else if (arg.startsWith("-agentlib:")) {
                        String[] split = splitEquals(arg.substring("-agentlib:".length()));
                        espressoOptions.put(AGENT_LIB + split[0], split[1]);
                    } else if (arg.startsWith("-agentpath:")) {
                        String[] split = splitEquals(arg.substring("-agentpath:".length()));
                        espressoOptions.put(AGENT_PATH + split[0], split[1]);
                    } else if (arg.startsWith("-Xmn") || arg.startsWith("-Xms") || arg.startsWith("-Xmx") || arg.startsWith("-Xss")) {
                        unrecognized.add("--vm." + arg.substring(1));
                    } else if (arg.startsWith("-XX:")) {
                        handleXXArg(arg, unrecognized);
                    } else
                    // -Dsystem.property=value
                    if (arg.startsWith("-D")) {
                        String key = arg.substring("-D".length());
                        int splitAt = key.indexOf("=");
                        String value = "";
                        if (splitAt >= 0) {
                            value = key.substring(splitAt + 1);
                            key = key.substring(0, splitAt);
                        }

                        switch (key) {
                            case "espresso.library.path":
                                espressoOptions.put("java.EspressoLibraryPath", value);
                                break;
                            case "java.library.path":
                                espressoOptions.put("java.JavaLibraryPath", value);
                                break;
                            case "java.class.path":
                                classpath = value;
                                break;
                            case "java.ext.dirs":
                                espressoOptions.put("java.ExtDirs", value);
                                break;
                            case "sun.boot.class.path":
                                espressoOptions.put("java.BootClasspath", value);
                                break;
                            case "sun.boot.library.path":
                                espressoOptions.put("java.BootLibraryPath", value);
                                break;
                        }

                        espressoOptions.put("java.Properties." + key, value);
                    } else if (!arg.startsWith("-")) {
                        mainClassName = arg;
                    } else {
                        unrecognized.add(args.getArg());
                    }
                    break;
            }
            if (mainClassName != null || jarFileName != null) {
                if (jarFileName != null) {
                    // Overwrite class path. For compatibility with the standard java launcher,
                    // this is done silently.
                    classpath = jarFileName;

                    mainClassName = getMainClassName(jarFileName);
                }
                buildJvmArgs(arguments, args.getNumberOfProcessedArgs());
                args.pushLeftoversArgs();
                break;
            }
        }

        // classpath provenance order:
        // (1) the -cp/-classpath command line option
        if (classpath == null) {
            // (2) the property java.class.path
            classpath = espressoOptions.get("java.Properties.java.class.path");
            if (classpath == null) {
                // (3) the environment variable CLASSPATH
                classpath = System.getenv("CLASSPATH");
                if (classpath == null) {
                    // (4) the current working directory only
                    classpath = ".";
                }
            }
        }

        espressoOptions.put("java.Classpath", classpath);

        if (!isRelaxStaticObjectSafetyChecksSet) {
            // Since Espresso has a verifier, the Static Object Model does not need to perform shape
            // checks and can use unsafe casts. Cmd line args have precedence over this default
            // value.
            espressoOptions.put("engine.RelaxStaticObjectSafetyChecks", "true");
        }

        return unrecognized;
    }

    private void handleXXArg(String fullArg, ArrayList<String> unrecognized) {
        String arg = fullArg.substring("-XX:".length());
        String name;
        String value;
        if (arg.length() >= 1 && (arg.charAt(0) == '+' || arg.charAt(0) == '-')) {
            value = Boolean.toString(arg.charAt(0) == '+');
            name = arg.substring(1);
        } else {
            int idx = arg.indexOf('=');
            if (idx < 0) {
                unrecognized.add(fullArg);
                return;
            }
            name = arg.substring(0, idx);
            value = arg.substring(idx + 1);
        }
        switch (name) {
            case "UnlockDiagnosticVMOptions", "UnlockExperimentalVMOptions" -> unrecognized.add("--experimental-options=" + value);
            case "WhiteBoxAPI" -> espressoOptions.put("java." + name, value);
            case "TieredStopAtLevel" -> {
                if ("0".equals(value)) {
                    espressoOptions.put("engine.Compilation", "false");
                } else {
                    unrecognized.add(fullArg);
                }
            }
            default -> unrecognized.add(fullArg);
        }
    }

    private void buildJvmArgs(List<String> arguments, int toBuild) {
        /*
         * Note:
         *
         * The format of the arguments passing through here is not the one expected by the java
         * world. It is actually expected that the vm arguments list is populated with arguments
         * which have been pre-formatted by the regular Java launcher when passed to the VM, ie: the
         * arguments if the VM was created through a call to JNI_CreateJavaVM.
         *
         * In particular, it expects all kay-value pairs to be equals-separated and not
         * space-separated. Furthermore, it does not expect syntactic-sugared some arguments such as
         * '-m' or '--modules', that would have been replaced by the regular java launcher as
         * '-Djdk.module.main='.
         */
        assert toBuild <= arguments.size();
        for (int i = 0; i < toBuild; i++) {
            espressoOptions.put("java.VMArguments." + i, arguments.get(i));
        }
    }

    private void parseNumberedOption(Arguments arguments, String property, String type) {
        String value = arguments.getValue(arguments.getKey(), type);
        mergeOption(property, value);
    }

    private void parseSpecifiedOption(Arguments arguments, String property, String type) {
        espressoOptions.put(property, arguments.getValue(arguments.getKey(), type));
    }

    private void mergeOption(String property, String value) {
        espressoOptions.merge(property, value, new BiFunction<String, String, String>() {
            @Override
            public String apply(String a, String b) {
                return a + File.pathSeparator + b;
            }
        });
    }

    private static String[] splitEquals(String value) {
        int eqIdx = value.indexOf('=');
        String k;
        String v;
        if (eqIdx >= 0) {
            k = value.substring(0, eqIdx);
            v = value.substring(eqIdx + 1);
        } else {
            k = value;
            v = "";
        }
        return new String[]{k, v};
    }

    private static String usage() {
        String nl = System.lineSeparator();
        // @formatter:off
        return "Usage: java [options] <mainclass> [args...]" + nl +
               "           (to execute a class)" + nl +
               "   or  java [options] -jar <jarfile> [args...]" + nl +
               "           (to execute a jar file)" + nl +
               "   or  java [options] -m <module>[/<mainclass>] [args...]" + nl +
               "       java [options] --module <module>[/<mainclass>] [args...]" + nl +
               "           (to execute the main class in a module)" + nl +
               "   or  java [options] <sourcefile> [args]" + nl +
               "           (to execute a single source-file program)" + nl + nl +
               " Arguments following the main class, source file, -jar <jarfile>," + nl +
               " -m or --module <module>/<mainclass> are passed as the arguments to" + nl +
               " main class." + nl + nl +
               " where options include:" + nl +
               "    -cp <class search path of directories and zip/jar files>" + nl +
               "    -classpath <class search path of directories and zip/jar files>" + nl +
               "                  A " + File.pathSeparator + " separated list of directories, JAR archives," + nl +
               "                  and ZIP archives to search for class files." + nl +
               "    -p <module path>" + nl +
               "    --module-path <module path>..." + nl +
               "                  A : separated list of directories, each directory" + nl +
               "                  is a directory of modules." + nl +
               "    --add-modules <module name>[,<module name>...]" + nl +
               "                  root modules to resolve in addition to the initial module." + nl +
               "                  <module name> can also be ALL-DEFAULT, ALL-SYSTEM," + nl +
               "                  ALL-MODULE-PATH." + nl +
               "    -D<name>=<value>" + nl +
               "                  set a system property" + nl +
               "    -version      print product version and exit" + nl +
               "    -showversion  print product version and continue" + nl +
               "    -ea | -enableassertions" + nl +
               "                  enable assertions" + nl +
               "    -esa | -enablesystemassertions" + nl +
               "                  enable system assertions" + nl +
               "    -? -help      print this help message";
        // @formatter:on
    }

    /**
     * Gets the values of the attribute {@code name} from the manifest in {@code jarFile}.
     *
     * @return the value of the attribute of null if not found
     * @throws IOException if error reading jar file
     */
    public static String getManifestValue(JarFile jarFile, String name) throws IOException {
        final Manifest manifest = jarFile.getManifest();
        if (manifest == null) {
            return null;
        }
        return manifest.getMainAttributes().getValue(name);
    }

    /**
     * Finds the main class name from the command line either explicitly or via the jar file.
     */
    private String getMainClassName(String jarFileName) {
        try {
            final JarFile jarFile = new JarFile(jarFileName);
            String value = getManifestValue(jarFile, "Main-Class");
            if (value == null) {
                throw abort("Could not find main class in jarfile: " + jarFileName);
            }
            return value;
        } catch (IOException e) {
            throw abort("Could not find main class in jarfile: " + jarFileName + " [cause: " + e + "]");
        }
    }

    // cf. sun.launcher.LauncherHelper
    private enum LaunchMode {
        LM_UNKNOWN,
        LM_CLASS,
        LM_JAR,
        LM_MODULE,
        // LM_SOURCE
    }

    @Override
    protected void launch(Builder contextBuilder) {
        contextBuilder.arguments(getLanguageId(), mainClassArgs.toArray(new String[0])).in(System.in).out(System.out).err(System.err);

        for (Map.Entry<String, String> entry : espressoOptions.entrySet()) {
            contextBuilder.option(entry.getKey(), entry.getValue());
        }

        contextBuilder.allowCreateThread(true);
        // We use the host system exit for compatibility with the reference implementation.
        contextBuilder.useSystemExit(true);
        contextBuilder.option("java.ExitHost", "true");

        try (Context context = contextBuilder.build()) {

            // TODO: Ensure consistency between option "java.Version" and the given "java.JavaHome".

            // runVersionAction(versionAction, context.getEngine());
            if (versionAction != VersionAction.None) {
                // The Java version is not known yet, try 8 first.
                Value version = context.getBindings("java").getMember("sun.misc.Version");
                if (version != null && !version.isNull()) {
                    // Java 8
                    version.invokeMember("print");
                } else {
                    // > Java 8
                    version = context.getBindings("java").getMember("java.lang.VersionProps");
                    if (version.hasMember("print/(Z)V")) {
                        Value printMethod = version.getMember("print/(Z)V");
                        printMethod.execute(/* print to stderr = */false);
                    } else {
                        // print is probably private
                        // fallback until we have an embedded API to call private members
                        printVersionFallback(context);
                    }
                }
                if (versionAction == VersionAction.PrintAndExit) {
                    throw exit(0);
                }
            }

            if (mainClassName == null) {
                throw abort(usage());
            }
            try {
                Value launcherHelper = context.getBindings("java").getMember("sun.launcher.LauncherHelper");
                Value mainKlass = launcherHelper //
                                .invokeMember("checkAndLoadMain", true, launchMode.ordinal(), mainClassName) //
                                .getMember("static");

                // Convert arguments to a guest String[], avoiding passing a foreign object right
                // away to Espresso.
                Value stringArray = context.getBindings("java").getMember("[Ljava.lang.String;");
                Value guestMainClassArgs = stringArray.newInstance(mainClassArgs.size());
                for (int i = 0; i < mainClassArgs.size(); i++) {
                    guestMainClassArgs.setArrayElement(i, mainClassArgs.get(i));
                }

                mainKlass.invokeMember("main/([Ljava/lang/String;)V", guestMainClassArgs);
                if (pauseOnExit) {
                    getError().print("Press any key to continue...");
                    try {
                        System.in.read();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (PolyglotException e) {
                if (e.isInternalError()) {
                    e.printStackTrace();
                    throw abort((String) null);
                } else if (!e.isExit()) {
                    handleMainUncaught(context, e);
                    throw abort((String) null);
                } else {
                    throw abort((String) null, e.getExitStatus());
                }
            }
        }
    }

    private void printVersionFallback(Context context) {
        // See java.lang.VersionProps.print
        Value system = context.getBindings("java").getMember("java.lang.System");
        String javaVersion = system.invokeMember("getProperty", "java.version").asString();
        String javaVersionDate = system.invokeMember("getProperty", "java.version.date").asString();
        String debugLevel = system.invokeMember("getProperty", "jdk.debug", "release").asString();
        String vendorVersion = system.invokeMember("getProperty", "java.vendor.version", "").asString();
        String javaRuntimeName = system.invokeMember("getProperty", "java.runtime.name").asString();
        String javaRuntimeVersion = system.invokeMember("getProperty", "java.runtime.version").asString();
        boolean isLTS = javaRuntimeVersion.contains("LTS");
        String javaVMName = system.invokeMember("getProperty", "java.vm.name").asString();
        String javaVMVersion = system.invokeMember("getProperty", "java.vm.version").asString();
        String javVMInfo = system.invokeMember("getProperty", "java.vm.info").asString();
        String launcherName = "espresso";

        /* First line: platform version. */
        /* Use a format more in line with GNU conventions */
        getOutput().println(launcherName + " " + javaVersion + " " + javaVersionDate + (isLTS ? " LTS" : ""));

        /* Second line: runtime version (ie, libraries). */
        if ("release".equals(debugLevel)) {
            /* Do not show debug level "release" builds */
            debugLevel = "";
        } else {
            debugLevel = debugLevel + " ";
        }

        vendorVersion = vendorVersion.isEmpty() ? "" : " " + vendorVersion;

        getOutput().println(javaRuntimeName + vendorVersion + " (" + debugLevel + "build " + javaRuntimeVersion + ")");

        /* Third line: JVM information. */
        getOutput().println(javaVMName + vendorVersion + " (" + debugLevel + "build " + javaVMVersion + ", " + javVMInfo + ")");
    }

    private static void handleMainUncaught(Context context, PolyglotException e) {
        Value threadClass = context.getBindings("java").getMember("java.lang.Thread");
        Value currentThread = threadClass.invokeMember("currentThread");
        Value handler = currentThread.invokeMember("getUncaughtExceptionHandler");
        handler.invokeMember("uncaughtException", currentThread, e.getGuestObject());
    }

    @Override
    protected String getLanguageId() {
        return "java";
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        getOutput().println(usage());
    }

    @Override
    protected void collectArguments(Set<String> options) {
        // This list of arguments is used when we are launched through the Polyglot
        // launcher
        options.add("-cp");
        options.add("-classpath");
        options.add("-version");
        options.add("-showversion");
        options.add("--show-version");
        options.add("-ea");
        options.add("-enableassertions");
        options.add("-esa");
        options.add("-enablesystemassertions");
        options.add("-?");
        options.add("-help");
    }

    private static String appendPath(String paths, String toAppend) {
        if (paths != null && paths.length() != 0) {
            return toAppend != null && toAppend.length() != 0 ? paths + File.pathSeparator + toAppend : paths;
        } else {
            return toAppend;
        }
    }

    private static String prependPath(String toPrepend, String paths) {
        if (paths != null && paths.length() != 0) {
            return toPrepend != null && toPrepend.length() != 0 ? toPrepend + File.pathSeparator + paths : paths;
        } else {
            return toPrepend;
        }
    }
}
