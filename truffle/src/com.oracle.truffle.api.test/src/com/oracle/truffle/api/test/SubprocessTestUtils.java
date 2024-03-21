/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * Support for executing Truffle tests in a sub-process with filtered compilation failure options.
 * This support is useful for tests that explicitly set
 * {@code PolyglotCompilerOptions#CompilationFailureAction}, and can be affected by junit arguments.
 * It's also useful for tests expecting compiler failure, VM crash or {@link OutOfMemoryError}.
 *
 * Usage example:
 *
 * <pre>
 * &#64;Test
 * public void testCompilationFailure() throws Exception {
 *     Runnable testToExecuteInSubprocess = () -> {
 *         setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "true").option("engine.BackgroundCompilation", "false").option(
 *                         "engine.CompilationFailureAction", "Throw"));
 *         testCallTarget.call();
 *     };
 *     SubprocessTestUtils.newBuilder(getClass(), testToExecuteInSubprocess).run();
 * }
 * </pre>
 */
public final class SubprocessTestUtils {

    /**
     * Recommended value of the subprocess timeout. After exceeding it, the process is forcibly
     * terminated.
     *
     * @see Builder#timeout(Duration)
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private static final String CONFIGURED_PROPERTY = SubprocessTestUtils.class.getSimpleName() + ".configured";

    private static final String TO_REMOVE_PREFIX = "~~";

    private SubprocessTestUtils() {
    }

    /**
     * Marks the provided VM option for removal by adding a {@link #TO_REMOVE_PREFIX} prefix.
     *
     * @param option The VM option to be marked for removal.
     */
    public static String markForRemoval(String option) {
        return TO_REMOVE_PREFIX + option;
    }

    /**
     * Executes action in a sub-process with filtered compilation failure options.
     *
     * @param testClass the test enclosing class.
     * @param action the test to execute.
     * @param prependedVmOptions VM options to prepend to {@link #getVMCommandLine}. Any element in
     *            this list that is {@link #markForRemoval(String) marked for removal} will be
     *            omitted from the command line instead. For example,
     *            {@code markForRemoval("-Dfoo=bar")} will ensure {@code "-Dfoo=bar"} is not present
     *            on the command line (unless {@code "-Dfoo=bar"} {@code prependedVmOptions}).
     * @return {@link Subprocess} if it's called by a test that is not executing in a sub-process.
     *         Returns {@code null} for a caller run in a sub-process.
     * @see SubprocessTestUtils
     * @see #markForRemoval(String)
     */
    public static Subprocess executeInSubprocess(Class<?> testClass, Runnable action, String... prependedVmOptions) throws IOException, InterruptedException {
        return executeInSubprocess(testClass, action, true, prependedVmOptions);
    }

    /**
     * Executes action in a sub-process with filtered compilation failure options.
     *
     * @param testClass the test enclosing class.
     * @param action the test to execute.
     * @param failOnNonZeroExitCode if {@code true}, the test fails if the sub-process ends with a
     *            non-zero return value.
     * @param prependedVmOptions VM options to prepend to {@link #getVMCommandLine}. Any element in
     *            this list that is {@link #markForRemoval(String) marked for removal} will be
     *            omitted from the command line instead. For example,
     *            {@code markForRemoval("-Dfoo=bar")} will ensure {@code "-Dfoo=bar"} is not present
     *            on the command line (unless {@code "-Dfoo=bar"} {@code prependedVmOptions}).
     * @return {@link Subprocess} if it's called by a test that is not executing in a sub-process.
     *         Returns {@code null} for a caller run in a sub-process.
     * @see SubprocessTestUtils
     * @see #markForRemoval(String)
     */
    public static Subprocess executeInSubprocess(Class<?> testClass, Runnable action, boolean failOnNonZeroExitCode, String... prependedVmOptions) throws IOException, InterruptedException {
        AtomicReference<Subprocess> process = new AtomicReference<>();
        newBuilder(testClass, action).failOnNonZeroExit(failOnNonZeroExitCode).prefixVmOption(prependedVmOptions).onExit((p) -> process.set(p)).run();
        return process.get();
    }

    /**
     * Executes action in a sub-process with filtered compilation failure options. Also disables
     * assertions for the classes in {@code daClasses}
     *
     * @param testClass the test enclosing class.
     * @param action the test to execute.
     * @param daClasses the classes whose assertions should be disabled.
     * @param additionalVmOptions additional vm option added to java arguments. Prepend
     *            {@link #TO_REMOVE_PREFIX} to remove item from existing vm options.
     * @return {@link Subprocess} if it's called by a test that is not executing in a sub-process.
     *         Returns {@code null} for a caller run in a sub-process.
     * @see SubprocessTestUtils
     */
    public static Subprocess executeInSubprocessWithAssertionsDisabled(Class<?> testClass, Runnable action, boolean failOnNonZeroExitCode, List<Class<?>> daClasses, String... additionalVmOptions)
                    throws IOException, InterruptedException {
        String[] vmOptionsWithAssertionsDisabled = getAssertionsDisabledOptions(daClasses);
        AtomicReference<Subprocess> process = new AtomicReference<>();
        newBuilder(testClass, action).failOnNonZeroExit(failOnNonZeroExitCode).prefixVmOption(additionalVmOptions).postfixVmOption(vmOptionsWithAssertionsDisabled).onExit((p) -> process.set(p)).run();
        return process.get();
    }

    private static String[] getAssertionsDisabledOptions(List<Class<?>> daClasses) {
        return daClasses.stream().map((c) -> "-da:" + c.getName()).toArray(String[]::new);
    }

    /**
     * Returns {@code true} if it's called by a test that is already executing in a sub-process.
     */
    public static boolean isSubprocess() {
        return Boolean.getBoolean(CONFIGURED_PROPERTY);
    }

    private static Method findTestMethod(Class<?> testClass) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack != null) {
            for (int i = stack.length - 1; i >= 0; i--) {
                StackTraceElement element = stack[i];
                if (testClass.getName().equals(element.getClassName())) {
                    try {
                        Method method = testClass.getDeclaredMethod(element.getMethodName());
                        if (method.getAnnotation(Test.class) != null) {
                            return method;
                        }
                    } catch (NoSuchMethodException noSuchMethodException) {
                        // skip methods with arguments.
                    }
                }
            }
        }
        throw new IllegalStateException("Failed to find current test method in class " + testClass);
    }

    private static Subprocess execute(Method testMethod, boolean failOnNonZeroExitCode, List<String> prefixVMOptions,
                    List<String> postfixVmOptions, Duration timeout) throws IOException, InterruptedException {
        String enclosingElement = testMethod.getDeclaringClass().getName();
        String testName = testMethod.getName();
        Subprocess subprocess = javaHelper(
                        configure(getVmArgs(), prefixVMOptions, postfixVmOptions),
                        null, null,
                        List.of("com.oracle.mxtool.junit.MxJUnitWrapper", String.format("%s#%s", enclosingElement, testName)),
                        timeout);
        if (failOnNonZeroExitCode && subprocess.exitCode != 0) {
            Assert.fail(String.join("\n", subprocess.output));
        }
        return subprocess;
    }

    private static List<String> configure(List<String> vmArgs, List<String> prefixVMOptions, List<String> postfixVmOptions) {
        List<String> newVmArgs = new ArrayList<>();
        newVmArgs.addAll(vmArgs.stream().filter(vmArg -> {
            for (String toRemove : getForbiddenVmOptions()) {
                if (vmArg.startsWith(toRemove)) {
                    return false;
                }
            }
            for (String additionalVmOption : prefixVMOptions) {
                if (additionalVmOption.startsWith(TO_REMOVE_PREFIX) && vmArg.startsWith(additionalVmOption.substring(2))) {
                    return false;
                }
            }
            for (String additionalVmOption : postfixVmOptions) {
                if (additionalVmOption.startsWith(TO_REMOVE_PREFIX) && vmArg.startsWith(additionalVmOption.substring(2))) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList()));
        for (String additionalVmOption : prefixVMOptions) {
            if (!additionalVmOption.startsWith(TO_REMOVE_PREFIX)) {
                newVmArgs.add(1, additionalVmOption);
            }
        }
        for (String additionalVmOption : postfixVmOptions) {
            if (!additionalVmOption.startsWith(TO_REMOVE_PREFIX)) {
                newVmArgs.add(additionalVmOption);
            }
        }
        newVmArgs.add(1, String.format("-D%s=%s", CONFIGURED_PROPERTY, "true"));
        return newVmArgs;
    }

    private static List<String> getVmArgs() {
        List<String> vmArgs = getVMCommandLine(true);
        vmArgs.add(PACKAGE_OPENING_OPTIONS);
        return vmArgs;
    }

    private static String[] getForbiddenVmOptions() {
        return new String[]{
                        graalOption("CompilationFailureAction"),
                        graalOption("CompilationBailoutAsFailure"),
                        graalOption("CrashAt"),
                        graalOption("DumpOnError"),
                        // Filter out the LogFile option to prevent overriding of the unit tests log
                        // file by a sub-process.
                        graalOption("LogFile"), // HotSpotTTYStreamProvider.Options#LogFile
                        "-Dpolyglot.log.file",
                        engineOption("CompilationFailureAction"),
                        engineOption("TraceCompilation"),
                        engineOption("TraceCompilationDetails")
        };
    }

    private static String graalOption(String optionName) {
        return "-Djdk.graal." + optionName;
    }

    private static String engineOption(String optionName) {
        return "-Dpolyglot.engine." + optionName;
    }

    public static Builder newBuilder(Class<?> testClass, Runnable inProcess) {
        return new Builder(testClass, inProcess);
    }

    public static final class Subprocess {

        /**
         * The command line of the subprocess.
         */
        public final List<String> command;

        /**
         * Exit code of the subprocess.
         */
        public final int exitCode;

        /**
         * Output from the subprocess broken into lines.
         */
        public final List<String> output;

        /**
         * Whether the subprocess execution exceeded the supplied timeout.
         */
        public final boolean timedOut;

        /**
         * Explicit environment variables.
         */
        private Map<String, String> env;

        private Subprocess(List<String> command, Map<String, String> env, int exitCode, List<String> output, boolean timedOut) {
            this.command = command;
            this.env = env;
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
        }

        private static final String DASHES_DELIMITER = "-------------------------------------------------------";

        /**
         * Returns the command followed by the output as a string.
         *
         * @param delimiter if non-null, the returned string has this value as a prefix and suffix
         */
        public String toString(String delimiter) {
            Formatter msg = new Formatter();
            if (delimiter != null) {
                msg.format("%s%n", delimiter);
            }
            if (env != null && !env.isEmpty()) {
                msg.format("env");
                for (Map.Entry<String, String> e : env.entrySet()) {
                    msg.format(" %s=%s", e.getKey(), quoteShellArg(e.getValue()));
                }
                msg.format("\\%n");
            }
            msg.format("%s%n", command.stream().map(e -> quoteShellArg(String.valueOf(e))).collect(Collectors.joining(" ")));
            for (String line : output) {
                msg.format("%s%n", line);
            }
            msg.format("exit code: %s%n", exitCode);
            if (delimiter != null) {
                msg.format("%s%n", delimiter);
            }
            return msg.toString();
        }

        /**
         * Returns the command followed by the output as a string delimited by
         * {@value #DASHES_DELIMITER}.
         */
        @Override
        public String toString() {
            return toString(DASHES_DELIMITER);
        }
    }

    public static final class Builder {

        private final Class<?> testClass;
        private final Runnable runnable;

        private final List<String> prefixVmArgs = new ArrayList<>();
        private final List<String> postfixVmArgs = new ArrayList<>();
        private boolean failOnNonZeroExit = true;
        private Duration timeout;
        private Consumer<Subprocess> onExit;

        private Builder(Class<?> testClass, Runnable run) {
            this.testClass = testClass;
            this.runnable = run;
        }

        /**
         * Prepends VM options to {@link #getVMCommandLine}. Any element in this list that is
         * {@link #markForRemoval(String) marked for removal} will be omitted from the command line
         * instead. For example, {@code markForRemoval("-Dfoo=bar")} will ensure {@code "-Dfoo=bar"}
         * is not present on the command line, unless {@code "-Dfoo=bar"} was specifically passed to
         * {@link #prefixVmOption(String...)} or {@link #postfixVmOption(String...)}.
         */
        public Builder prefixVmOption(String... options) {
            prefixVmArgs.addAll(List.of(options));
            return this;
        }

        /**
         * Appends VM options to {@link #getVMCommandLine}. Any element in this list that is
         * {@link #markForRemoval(String) marked for removal} will be omitted from the command line
         * instead. For example, {@code markForRemoval("-Dfoo=bar")} will ensure {@code "-Dfoo=bar"}
         * is not present on the command line, unless {@code "-Dfoo=bar"} was specifically passed to
         * {@link #prefixVmOption(String...)} or {@link #postfixVmOption(String...)}.
         */
        public Builder postfixVmOption(String... options) {
            postfixVmArgs.addAll(List.of(options));
            return this;
        }

        public Builder failOnNonZeroExit(boolean b) {
            failOnNonZeroExit = b;
            return this;
        }

        /**
         * Sets the subprocess timeout. After its expiration, the subprocess is forcibly terminated.
         * By default, there is no timeout and the subprocess execution time is not limited.
         *
         * @see SubprocessTestUtils#DEFAULT_TIMEOUT
         *
         */
        public Builder timeout(Duration duration) {
            this.timeout = Objects.requireNonNull(duration, "duration must be non null");
            return this;
        }

        public Builder onExit(Consumer<Subprocess> exit) {
            this.onExit = exit;
            return this;
        }

        public void run() throws IOException, InterruptedException {
            if (isSubprocess()) {
                runnable.run();
            } else {
                Subprocess process = execute(findTestMethod(testClass), failOnNonZeroExit, prefixVmArgs, postfixVmArgs, timeout);
                if (onExit != null) {
                    onExit.accept(process);
                }
            }
        }

    }

    // Methods and constants copied from jdk.graal.compiler.test.SubprocessUtil

    /**
     * The name of the boolean system property that can be set to preserve temporary files created
     * as arguments files passed to the java launcher.
     */
    private static final String KEEP_TEMPORARY_ARGUMENT_FILES_PROPERTY_NAME = "test.SubprocessTestUtil.keepTempArgumentFiles";

    /**
     * A sentinel value which when present in the {@code vmArgs} parameter for any of the
     * {@code java(...)} methods in this class is replaced with a temporary argument file containing
     * the contents of {@link #getPackageOpeningOptions}. The argument file is preserved if the
     * {@link #KEEP_TEMPORARY_ARGUMENT_FILES_PROPERTY_NAME} system property is true.
     */
    private static final String PACKAGE_OPENING_OPTIONS = ";:PACKAGE_OPENING_OPTIONS_IN_TEMPORARY_ARGUMENTS_FILE:;";

    /**
     * Pattern for a single shell command argument that does not need to quoted.
     */
    private static final Pattern SAFE_SHELL_ARG = Pattern.compile("[A-Za-z0-9@%_\\-\\+=:,\\./]+");

    /**
     * Reliably quote a string as a single shell command argument.
     */
    private static String quoteShellArg(String arg) {
        if (arg.isEmpty()) {
            return "\"\"";
        }
        Matcher m = SAFE_SHELL_ARG.matcher(arg);
        if (m.matches()) {
            return arg;
        }
        // See http://stackoverflow.com/a/1250279
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }

    /**
     * Returns a new copy {@code args} with debugger arguments removed.
     */
    private static List<String> withoutDebuggerArguments(List<String> args) {
        List<String> result = new ArrayList<>(args.size());
        for (String arg : args) {
            if (!(arg.equals("-Xdebug") || arg.startsWith("-Xrunjdwp:") || arg.startsWith("-agentlib:jdwp"))) {
                result.add(arg);
            }
        }
        return result;
    }

    /**
     * Gets the command line for the current process.
     *
     * @return the command line arguments for the current process or {@code null} if they are not
     *         available
     */
    private static List<String> getProcessCommandLine() {
        String processArgsFile = System.getenv().get("MX_SUBPROCESS_COMMAND_FILE");
        if (processArgsFile != null) {
            try {
                return Files.readAllLines(new File(processArgsFile).toPath());
            } catch (IOException e) {
            }
        } else {
            Assume.assumeTrue("Process command line unavailable", false);
        }
        return null;
    }

    /**
     * Gets the command line used to start the current Java VM, including all VM arguments, but not
     * including the main class or any Java arguments. This can be used to spawn an identical VM,
     * but running different Java code.
     *
     * @param removeDebuggerArguments if {@code true}, pre-process the returned list with
     *            {@link #withoutDebuggerArguments(List)}
     * @return {@code null} if the command line for the current process cannot be accessed
     */
    private static List<String> getVMCommandLine(boolean removeDebuggerArguments) {
        List<String> args = getProcessCommandLine();
        if (args == null) {
            return null;
        } else {
            int index = findMainClassIndex(args);
            args = args.subList(0, index);
            if (removeDebuggerArguments) {
                args = withoutDebuggerArguments(args);
            }
            return args;
        }
    }

    /**
     * Executes a Java subprocess.
     *
     * @param vmArgs the VM arguments. If {@code vmArgs} contains {@link #PACKAGE_OPENING_OPTIONS},
     *            the argument is replaced with arguments to do package opening (see
     *            {@link #getPackageOpeningOptions()}
     * @param env the environment variables
     * @param workingDir the working directory of the subprocess. If null, the working directory of
     *            the current process is used.
     * @param mainClassAndArgs the main class and its arguments
     * @param timeout the duration to wait for the process to finish. If null, the calling thread
     *            waits for the process indefinitely.
     */
    private static Subprocess javaHelper(List<String> vmArgs, Map<String, String> env, File workingDir, List<String> mainClassAndArgs, Duration timeout) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(vmArgs.size());
        Path packageOpeningOptionsArgumentsFile = null;
        for (String vmArg : vmArgs) {
            if (vmArg == PACKAGE_OPENING_OPTIONS) {
                if (packageOpeningOptionsArgumentsFile == null) {
                    List<String> packageOpeningOptions = getPackageOpeningOptions();
                    if (!packageOpeningOptions.isEmpty()) {
                        packageOpeningOptionsArgumentsFile = Files.createTempFile(Paths.get("."), "package-opening-options-arguments-file", ".txt").toAbsolutePath();
                        Files.write(packageOpeningOptionsArgumentsFile, packageOpeningOptions);
                        command.add("@" + packageOpeningOptionsArgumentsFile);
                    }
                }
            } else {
                command.add(vmArg);
            }
        }
        command.addAll(mainClassAndArgs);
        try {
            return process(command, env, workingDir, timeout);
        } finally {
            if (packageOpeningOptionsArgumentsFile != null) {
                if (!Boolean.getBoolean(KEEP_TEMPORARY_ARGUMENT_FILES_PROPERTY_NAME)) {
                    Files.delete(packageOpeningOptionsArgumentsFile);
                }
            }
        }
    }

    /**
     * Executes a command in a subprocess.
     *
     * @param command the command to be executed in a separate process.
     * @param env environment variables of the subprocess. If null, no environment variables are
     *            passed.
     * @param workingDir the working directory of the subprocess. If null, the working directory of
     *            the current process is used.
     * @param timeout the duration to wait for the process to finish. When the timeout is reached,
     *            the subprocess is terminated forcefully. If the timeout is null, the calling
     *            thread waits for the process indefinitely.
     */
    private static Subprocess process(List<String> command, Map<String, String> env, File workingDir, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDir != null) {
            processBuilder.directory(workingDir);
        }
        if (env != null) {
            Map<String, String> processBuilderEnv = processBuilder.environment();
            processBuilderEnv.putAll(env);
        }
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        List<String> output = new ArrayList<>();
        if (timeout == null) {
            String line;
            while ((line = stdout.readLine()) != null) {
                output.add(line);
            }
            return new Subprocess(command, env, process.waitFor(), output, false);
        } else {
            // The subprocess might produce output forever. We need to grab the output in a
            // separate thread, so we can terminate the process after the timeout if necessary.
            Thread outputReader = new Thread(() -> {
                try {
                    String line;
                    while ((line = stdout.readLine()) != null) {
                        output.add(line);
                    }
                } catch (IOException e) {
                    // happens when the process ends
                }
            });
            outputReader.start();
            boolean finishedOnTime = process.waitFor(timeout.getSeconds(), TimeUnit.SECONDS);
            if (!finishedOnTime) {
                dumpThreads(process.toHandle());
                process.destroyForcibly().waitFor();
            }
            outputReader.join();
            return new Subprocess(command, env, process.exitValue(), output, !finishedOnTime);
        }
    }

    private static void dumpThreads(ProcessHandle process) {
        if (ImageInfo.inImageCode()) {
            // The attach API is not supported by substratevm.
            return;
        }
        Optional<VirtualMachineDescriptor> vmDescriptor = VirtualMachine.list().stream().filter((d) -> {
            try {
                return Long.parseLong(d.id()) == process.pid();
            } catch (NumberFormatException e) {
                return false;
            }
        }).findAny();
        if (vmDescriptor.isPresent()) {
            try {
                VirtualMachine vm = VirtualMachine.attach(vmDescriptor.get());
                try {
                    Properties props = vm.getAgentProperties();
                    String connectorAddress = props.getProperty("com.sun.management.jmxremote.localConnectorAddress");
                    if (connectorAddress == null) {
                        connectorAddress = vm.startLocalManagementAgent();
                    }
                    JMXServiceURL url = new JMXServiceURL(connectorAddress);
                    try (JMXConnector connector = JMXConnectorFactory.connect(url)) {
                        MBeanServerConnection mbeanConnection = connector.getMBeanServerConnection();
                        CompositeData[] result = (CompositeData[]) mbeanConnection.invoke(new ObjectName("java.lang:type=Threading"), "dumpAllThreads",
                                        new Object[]{true, true}, new String[]{boolean.class.getName(), boolean.class.getName()});
                        PrintStream out = System.err;
                        out.printf("%nDumping subprocess threads on timeout%n");
                        for (CompositeData element : result) {
                            dumpThread(ThreadInfo.from(element), out);
                        }
                    }
                } finally {
                    vm.detach();
                }
            } catch (Exception e) {
                // thread dump is an optional operation, just log the error
                System.err.println("Failed to generate timed out subprocess thread dump due to");
                e.printStackTrace(System.err);
            }
        }
    }

    private static void dumpThread(ThreadInfo ti, PrintStream out) {
        long id = ti.getThreadId();
        Thread.State state = ti.getThreadState();
        out.printf("""
                        "%s" %s prio=%d tid=%d %s
                           java.lang.Thread.State: %s
                        """,
                        ti.getThreadName(),
                        ti.isDaemon() ? "daemon" : "",
                        ti.getPriority(),
                        id,
                        state.name().toLowerCase(),
                        state.name());
        StackTraceElement[] stackTrace = ti.getStackTrace();
        MonitorInfo[] monitors = ti.getLockedMonitors();
        LockInfo[] synchronizers = ti.getLockedSynchronizers();
        for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement stackTraceElement = stackTrace[i];
            out.printf("\tat %s%n", stackTraceElement);
            for (MonitorInfo mi : monitors) {
                if (mi.getLockedStackDepth() == i) {
                    out.printf("\t- locked %s%n", mi);
                }
            }
        }
        if (synchronizers.length > 0) {
            out.printf("%n   Locked ownable synchronizers:%n");
            for (LockInfo li : synchronizers) {
                out.printf("\t- %s%n", li);
            }
        }
        out.println();
    }

    private static boolean hasArg(String optionName) {
        if (optionName.equals("-cp") || optionName.equals("-classpath") || optionName.equals("-p")) {
            return true;
        }
        if (optionName.equals("--version") ||
                        optionName.equals("--show-version") ||
                        optionName.equals("--dry-run") ||
                        optionName.equals("--disable-@files") ||
                        optionName.equals("--dry-run") ||
                        optionName.equals("--help") ||
                        optionName.equals("--help-extra")) {
            return false;
        }
        if (optionName.startsWith("--")) {
            return optionName.indexOf('=') == -1;
        }
        return false;
    }

    /**
     * Gets the command line options to do the same package opening and exporting specified by the
     * {@code --open-packages} option to the {@code mx unittest} command.
     *
     * Properties defined in {@code com.oracle.mxtool.junit.MxJUnitWrapper}.
     */
    private static List<String> getPackageOpeningOptions() {
        List<String> result = new ArrayList<>();
        String[] actions = {"opens", "exports"};
        for (String action : actions) {
            String opens = System.getProperty("com.oracle.mxtool.junit." + action);
            if (opens != null && !opens.isEmpty()) {
                for (String value : opens.split(System.lineSeparator())) {
                    result.add("--add-" + action + "=" + value);
                }
            }
        }
        return result;
    }

    private static int findMainClassIndex(List<String> commandLine) {
        int i = 1; // Skip the java executable

        while (i < commandLine.size()) {
            String s = commandLine.get(i);
            if (s.charAt(0) != '-') {
                // https://bugs.openjdk.java.net/browse/JDK-8027634
                if (s.charAt(0) != '@') {
                    return i;
                }
                i++;
            } else if (hasArg(s)) {
                i += 2;
            } else {
                i++;
            }
        }
        throw new InternalError();
    }
}
