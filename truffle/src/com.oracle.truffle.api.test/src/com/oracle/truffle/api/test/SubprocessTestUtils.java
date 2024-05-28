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
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
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
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

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

    private static final Level DEBUG_LEVEL = Level.parse(System.getProperty("debug." + SubprocessTestUtils.class.getName(), "INFO"));

    /**
     * Directory in which argfiles will be {@linkplain #createArgFile created}.
     *
     * Keep in sync with the {@code catch_files} array in {@code ci/common.jsonnet}.
     */
    private static final Path ARGFILES_DIRECTORY = initArgFilesDirectory();

    /**
     * Records whether {@link #ARGFILES_DIRECTORY} existed before the JVM started.
     */
    private static final boolean ARGFILE_DIRECTORY_EXISTED = Files.exists(ARGFILES_DIRECTORY);

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
            Assert.fail(String.format("Subprocess produced non-0 exit code %d%n%s", subprocess.exitCode, subprocess.preserveArgFile()));
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

        private static final List<Subprocess> subprocessesWithArgfiles = Collections.synchronizedList(new ArrayList<>());
        static {
            Runtime.getRuntime().addShutdownHook(new Thread("SubprocessArgFileCleanUp") {
                @Override
                public void run() {
                    synchronized (subprocessesWithArgfiles) {
                        int preserved = 0;
                        for (Subprocess s : subprocessesWithArgfiles) {
                            if (s.argFile != null && Files.exists(s.argFile)) {
                                if (!s.preserveArgFileOnExit) {
                                    try {
                                        Files.delete(s.argFile);
                                        log(Level.FINE, "deleted %s", s.argFile);
                                    } catch (IOException e) {
                                        printError("Error deleting %s: %s", s.argFile, e);
                                    }
                                } else {
                                    preserved++;
                                }
                            }
                        }
                        if (preserved != 0) {
                            print("Preserved %d argfile(s) in %s", preserved, ARGFILES_DIRECTORY);
                        } else if (!ARGFILE_DIRECTORY_EXISTED && Files.exists(ARGFILES_DIRECTORY)) {
                            try {
                                Files.delete(ARGFILES_DIRECTORY);
                            } catch (IOException e) {
                                printError("Error deleting %s: %s", ARGFILES_DIRECTORY, e);
                            }
                        }
                    }
                }
            });
        }

        /**
         * The command line of the subprocess.
         */
        public final List<String> command;

        /**
         * OS level pid.
         */
        public final long pid;

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
        public final Map<String, String> env;

        /**
         * Path to the argument file used to execute the process, or {@code null} if the executable
         * does not support argument files.
         */
        private final Path argFile;

        /**
         * if set tro {@code true} the {@code argFile} is preserved. Otherwise, the {@code argFile}
         * is deleted on exit.
         */
        private volatile boolean preserveArgFileOnExit;

        private Subprocess(List<String> command, Map<String, String> env, long pid, int exitCode, List<String> output, boolean timedOut,
                        Path argFile) {
            this.command = command;
            this.env = env;
            this.pid = pid;
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
            this.argFile = argFile;
            subprocessesWithArgfiles.add(this);
        }

        /**
         * Returns the process execution as a string with a header line followed by one or more body
         * lines followed by a trailer with a new line.
         *
         * The header is {@code "----------subprocess[<pid>]:(<lines>/<chars>)----------"} where
         * {@code pid} is the id of the process and {@code chars} and {@code lines} provide the
         * dimensions of the body.
         *
         * The sections in the body are the environment variables {@link Section#ENVIRONMENT}, the
         * command line {@link Section#COMMAND}, the lines of output produced {@link Section#OUTPUT}
         * and the exit code {@link Section#EXIT_CODE}.
         *
         * The trailer is {@code "==========subprocess[<pid>]=========="}
         *
         * @param sections selects which sections are in the body.
         */
        private String asString(Set<Section> sections) {
            Formatter msg = new Formatter();
            if (sections.contains(Section.ENVIRONMENT)) {
                if (env != null && !env.isEmpty()) {
                    msg.format("env");
                    for (Map.Entry<String, String> e : env.entrySet()) {
                        msg.format(" %s=%s", e.getKey(), quoteShellArg(e.getValue()));
                    }
                    msg.format("\\%n");
                }
            }
            if (sections.contains(Section.COMMAND)) {
                msg.format("%s%n", command.stream().map((e) -> quoteShellArg(String.valueOf(e))).collect(Collectors.joining(" ")));
            }
            if (sections.contains(Section.OUTPUT)) {
                for (String line : output) {
                    msg.format("%s%n", line);
                }
            }
            if (sections.contains(Section.EXIT_CODE)) {
                msg.format("exit code: %s%n", exitCode);
            }
            String body = msg.toString();
            if (!body.endsWith(System.lineSeparator())) {
                body = body + System.lineSeparator();
            }
            long lines = body.chars().filter(ch -> ch == '\n').count();
            int chars = body.length();
            String head = String.format("----------subprocess[%d]:(%d/%d)----------", pid, lines, chars);
            String tail = String.format("==========subprocess[%d]==========", pid);
            return String.format("%s%n%s%s%n", head, body, tail);
        }

        /**
         * Returns the process execution as a string with a header line followed by one or more body
         * lines followed by a trailer with a new line.
         *
         * @see #asString(Set)
         */
        @Override
        public String toString() {
            return asString(EnumSet.allOf(Section.class));
        }

        /**
         * Preserves this subprocess's argfile after the JVM exits.
         */
        private Subprocess preserveArgFile() {
            if (argFile != null) {
                log(Level.FINE, "preserving %s", argFile);
            }
            preserveArgFileOnExit = true;
            return this;
        }

        /**
         * Enum representing different sections in a string representation of process execution.
         */
        private enum Section {
            /**
             * Explicit process environment section.
             */
            ENVIRONMENT,
            /**
             * Command line section.
             */
            COMMAND,
            /**
             * Process output section.
             */
            OUTPUT,
            /**
             * Process exit code section.
             */
            EXIT_CODE
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

        /**
         * Disables assertions in {@code forClass}.
         */
        public Builder disableAssertions(Class<?> forClass) {
            String disabledAssertionsOptions = "-da:" + forClass.getName();
            return postfixVmOption(disabledAssertionsOptions);
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
                    try {
                        onExit.accept(process);
                    } catch (Throwable t) {
                        throw new AssertionError(String.format("Subprocess result validation failed with %s%n%s", t, process.preserveArgFile()), t);
                    }
                }
                if (isLoggable(Level.FINEST)) {
                    // With DEBUG_LEVEL == FINEST log successful runs.
                    log(Level.FINEST, "%s%n", process.preserveArgFile());
                }
            }
        }

    }

    /**
     * A marker annotation for test methods that create subprocesses. This annotation is necessary
     * for the proper functioning of {@link #disableForParentProcess(TestRule) disabling test rules
     * in the parent process}.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface WithSubprocess {
    }

    /**
     * Creates a {@link TestRule} that enables you to disable certain rules for a test method when
     * creating a subprocess. One notable use case is for tests employing the {@link Timeout} rule.
     * When a test method creates a subprocess, the {@link Timeout} rule must not be enabled in the
     * parent process. Otherwise, the parent process may be terminated prematurely before the
     * subprocess completes the actual test.
     * <p>
     * To ensure proper functionality, it's crucial to annotate test methods creating subprocesses
     * with {@link WithSubprocess}.
     * <p>
     * Example usage:
     *
     * <pre>
     * public static class ForkedTest {
     *
     *     &#064;Rule public TestRule timeout = disableForParentProcess(new Timeout(20));
     *
     *     &#064;Test
     *     &#064;WithSubprocess
     *     public void test() {
     *         SubprocessTestUtils.newBuilder(BytecodeOSRNodeTest.class, () -> {
     *             assertTrue(performTest());
     *         }).run();
     *     }
     * }
     * </pre>
     *
     * @param base the {@link TestRule} to disable in the parent process.
     */
    public static TestRule disableForParentProcess(TestRule base) {
        return new DisableForSubprocess(base);
    }

    private static final class DisableForSubprocess implements TestRule {

        private final TestRule rule;

        DisableForSubprocess(TestRule rule) {
            this.rule = rule;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            if (!isSubprocess() && description.getAnnotation(WithSubprocess.class) != null) {
                return base;
            } else {
                return rule.apply(base, description);
            }
        }
    }

    // Methods and constants copied from jdk.graal.compiler.test.SubprocessUtil
    /**
     * A sentinel value which when present in the {@code vmArgs} parameter for any of the
     * {@code java(...)} methods in this class is replaced with the contents of
     * {@link #getPackageOpeningOptions}.
     */
    public static final String PACKAGE_OPENING_OPTIONS = ";:PACKAGE_OPENING_OPTIONS:;";

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
        for (String vmArg : vmArgs) {
            if (vmArg == PACKAGE_OPENING_OPTIONS) {
                command.addAll(getPackageOpeningOptions());
            } else {
                command.add(vmArg);
            }
        }
        command.addAll(mainClassAndArgs);
        return process(command, env, workingDir, timeout);
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
        Path argfile = makeArgfile(command);
        ProcessBuilder processBuilder = new ProcessBuilder(argfile == null ? command : List.of(command.get(0), "@" + argfile));
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
            return new Subprocess(processBuilder.command(), env, process.pid(), process.waitFor(), output, false, argfile);
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
            return new Subprocess(processBuilder.command(), env, process.pid(), process.exitValue(), output, !finishedOnTime, argfile);
        }
    }

    private static final Set<String> EXECUTABLES_USING_ARGFILES = Set.of("java", "javac");

    /**
     * Creates an argfile for {@code command} if {@code command.get(0)} denotes an
     * {@linkplain #EXECUTABLES_USING_ARGFILES executable supporting argfiles}.
     *
     * @see "https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html#java-command-line-argument-files"
     * @return the created argfile or {@code null}
     */
    private static Path makeArgfile(List<String> command) {
        Path exe = Path.of(command.get(0));
        if (EXECUTABLES_USING_ARGFILES.contains(removeExecutableExtension(exe.getFileName().toString()))) {
            try {
                List<String> expandedArgs = expandArgFileArgs(command);
                // Keep in sync with the {@code catch_files} array in {@code ci/common.jsonnet}.
                Path argfile = createArgFile();
                String content = expandedArgs.stream().map(SubprocessTestUtils::quoteArgFileArgument).collect(Collectors.joining("\n"));
                Files.writeString(argfile, "# " + content);
                return argfile;
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        return null;
    }

    private static String removeExecutableExtension(String fileName) {
        return OSUtils.isWindows() && fileName.toLowerCase().endsWith(".exe") ? fileName.substring(0, fileName.length() - ".exe".length()) : fileName;
    }

    private static Path createArgFile() throws IOException {
        Path dir = Files.createDirectories(ARGFILES_DIRECTORY);
        Path argfile;
        while (true) {
            try {
                // Sanitize for Windows by replacing ':' with '_'
                argfile = Files.createFile(dir.resolve(String.valueOf(Instant.now()).replace(':', '_') + ".argfile"));
                break;
            } catch (FileAlreadyExistsException e) {
                // try again
            }
        }
        return argfile;
    }

    private static Path initArgFilesDirectory() {
        Path outputDir = Path.of("mxbuild");
        if (!Files.isDirectory(outputDir)) {
            outputDir = Path.of(".");
        }
        return outputDir.resolve(String.format("%s-argfiles", SubprocessTestUtils.class.getSimpleName()));
    }

    /**
     * Expands Java argument files. Arguments denoting Java argument files (prefixed with {@code @})
     * are replaced by the tokenized content of these argument files. Non-argument file arguments
     * are copied as they are.
     */
    private static List<String> expandArgFileArgs(List<String> args) throws IOException {
        List<String> expandedArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.length() > 1 && arg.charAt(0) == '@') {
                String argWithoutMark = arg.substring(1);
                if (argWithoutMark.charAt(0) == '@') {
                    expandedArgs.add(argWithoutMark);
                } else {
                    expandedArgs.addAll(loadArgFile(Path.of(argWithoutMark)));
                }
            } else {
                expandedArgs.add(arg);
            }
        }
        return expandedArgs;
    }

    /**
     * Loads and tokenizes the Java argument file.
     */
    private static List<String> loadArgFile(Path argFile) throws IOException {
        List<String> args = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(argFile, Charset.defaultCharset())) {
            Tokenizer tokenizer = new Tokenizer(reader);
            String token;
            while ((token = tokenizer.nextToken()) != null) {
                args.add(token);
            }
        }
        return args;
    }

    /**
     * Quote an argument for use in an argfile.
     */
    private static String quoteArgFileArgument(String arg) {
        if (arg.isEmpty()) {
            return "\"\"";
        }
        if (arg.chars().anyMatch(ch -> Character.isWhitespace(ch) || ch == '#')) {
            if (arg.indexOf('"') != -1) {
                assert arg.indexOf('\'') == -1 : "unquotable: " + arg;
                return "'" + arg + "'";
            }
            return '"' + arg + '"';
        }
        return arg;
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
                        StringWriter messageBuilder = new StringWriter();
                        PrintWriter out = new PrintWriter(new StringWriter());
                        out.printf("%nDumping subprocess threads on timeout%n");
                        for (CompositeData element : result) {
                            dumpThread(ThreadInfo.from(element), out);
                        }
                        printError(messageBuilder.toString());
                    }
                } finally {
                    vm.detach();
                }
            } catch (Exception e) {
                // thread dump is an optional operation, just log the error
                StringWriter message = new StringWriter();
                try (PrintWriter out = new PrintWriter(message)) {
                    out.println("Failed to generate timed out subprocess thread dump due to");
                    e.printStackTrace(out);
                }
                printError(message.toString());
            }
        }
    }

    private static void dumpThread(ThreadInfo ti, PrintWriter out) {
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

    private static void log(Level level, String format, Object... args) {
        if (isLoggable(level)) {
            print(format, args);
        }
    }

    private static boolean isLoggable(Level level) {
        return level.intValue() >= DEBUG_LEVEL.intValue();
    }

    private static void print(String format, Object... args) {
        printImpl(System.out, format, args);
    }

    private static void printError(String format, Object... args) {
        printImpl(System.err, format, args);
    }

    private static void printImpl(PrintStream out, String format, Object... args) {
        String formattedMessage = args.length == 0 ? format : String.format(format, args);
        out.printf("[%s] %s%n", SubprocessTestUtils.class.getSimpleName(), formattedMessage);
    }

    /**
     * Lexer for Java argument files. This class is copied from
     * {@code jdk.graal.compiler.test.CommandLine}. Ensure that any updates to this class are kept
     * in sync with the original source.
     *
     * @see "https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html#java-command-line-argument-files"
     */
    private static class Tokenizer {
        private final Reader in;
        private int ch;

        Tokenizer(Reader in) throws IOException {
            this.in = in;
            ch = in.read();
        }

        String nextToken() throws IOException {
            skipWhite();
            if (ch == -1) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            char quoteChar = 0;

            while (ch != -1) {
                switch (ch) {
                    case ' ':
                    case '\t':
                    case '\f':
                        if (quoteChar == 0) {
                            return sb.toString();
                        }
                        sb.append((char) ch);
                        break;

                    case '\n':
                    case '\r':
                        return sb.toString();

                    case '\'':
                    case '"':
                        if (quoteChar == 0) {
                            quoteChar = (char) ch;
                        } else if (quoteChar == ch) {
                            quoteChar = 0;
                        } else {
                            sb.append((char) ch);
                        }
                        break;

                    case '\\':
                        if (quoteChar != 0) {
                            ch = in.read();
                            switch (ch) {
                                case '\n':
                                case '\r':
                                    while (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\f') {
                                        ch = in.read();
                                    }
                                    continue;

                                case 'n':
                                    ch = '\n';
                                    break;
                                case 'r':
                                    ch = '\r';
                                    break;
                                case 't':
                                    ch = '\t';
                                    break;
                                case 'f':
                                    ch = '\f';
                                    break;
                            }
                        }
                        sb.append((char) ch);
                        break;

                    default:
                        sb.append((char) ch);
                }

                ch = in.read();
            }

            return sb.toString();
        }

        private void skipWhite() throws IOException {
            while (ch != -1) {
                switch (ch) {
                    case ' ':
                    case '\t':
                    case '\n':
                    case '\r':
                    case '\f':
                        break;

                    case '#':
                        ch = in.read();
                        while (ch != '\n' && ch != '\r' && ch != -1) {
                            ch = in.read();
                        }
                        break;

                    default:
                        return;
                }

                ch = in.read();
            }
        }
    }
}
