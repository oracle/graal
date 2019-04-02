/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.ProcessHandler;
import org.graalvm.polyglot.io.ProcessHandler.Redirect;

/**
 * A builder used to create an external subprocess. The {@ TruffleProcessBuilder} instance allows to
 * set subprocess attributes. The {@link #start()} method creates a new {@link Process} instance
 * with those attributes. The {@link #start()} method can be invoked repeatedly from the same
 * instance to create new subprocesses with the same attributes.
 *
 * @since 1.0
 */
public class TruffleProcessBuilder {

    private final Object polylgotLanguageContext;
    private final FileSystem fileSystem;
    private List<String> cmd;
    private TruffleFile cwd;
    private boolean inheritIO;
    private boolean clearEnvironment;
    private Map<String, String> env;
    private boolean redirectErrorStream;
    private Redirect[] redirects;

    TruffleProcessBuilder(Object polylgotLanguageContext, FileSystem fileSystem, List<String> command) {
        Objects.requireNonNull(polylgotLanguageContext, "PolylgotLanguageContext must be non null.");
        Objects.requireNonNull(fileSystem, "FileSystem must be non null.");
        Objects.requireNonNull(command, "Command must be non null.");
        this.polylgotLanguageContext = polylgotLanguageContext;
        this.fileSystem = fileSystem;
        this.cmd = command;
        this.redirects = new Redirect[]{Redirect.PIPE, Redirect.PIPE, Redirect.PIPE};
    }

    /**
     * Sets the executable and arguments.
     *
     * @param command the list containing the executable and its arguments
     * @return this {@link TruffleProcessBuilder builder}
     * @since 1.0
     */
    public TruffleProcessBuilder command(List<String> command) {
        Objects.requireNonNull(command, "Command must be non null.");
        this.cmd = new ArrayList<>(command);
        return this;
    }

    /**
     * Sets the executable and arguments.
     *
     * @param command the string array containing the executable and its arguments
     * @return this {@link TruffleProcessBuilder builder}
     * @since 1.0
     */
    public TruffleProcessBuilder command(String... command) {
        Objects.requireNonNull(command, "Command must be non null.");
        this.cmd = new ArrayList<>(command.length);
        Collections.addAll(cmd, command);
        return this;
    }

    /**
     * Sets this process current working directory. The {@code currentWorkingDirectory} may be
     * {@code null}, in this case the subprocess current working directory is set to
     * {@link TruffleLanguage.Env#getCurrentWorkingDirectory() file system current working
     * directory}.
     *
     * @param currentWorkingDirectory the new current working directory
     * @return this {@link TruffleProcessBuilder builder}
     * @since 1.0
     */
    public TruffleProcessBuilder directory(TruffleFile currentWorkingDirectory) {
        this.cwd = currentWorkingDirectory;
        return this;
    }

    /**
     * If {@code true} the standard error output is merged into standard output.
     *
     * @param enabled enables merging of standard error output into standard output
     * @return this {@link TruffleProcessBuilder builder}
     * @since 1.0
     */
    public TruffleProcessBuilder redirectErrorStream(boolean enabled) {
        this.redirectErrorStream = enabled;
        return this;
    }

    /**
     * Sets the standard input source. Process started by the {@link #start()} method obtain its
     * standard input from this source.
     * <p>
     * If the source is {@link Redirect#PIPE PIPE}, the default value, then the standard input of a
     * subprocess can be written to using the output stream returned by
     * {@link Process#getOutputStream()}. If the source is set to {@link Redirect#INHERIT INHERIT},
     * then the {@link Process#getOutputStream()} returns a closed output stream.
     *
     * @param source the new standard input source
     * @return this {@link TruffleProcessBuilder builder}
     * @since 1.0
     */
    public TruffleProcessBuilder redirectInput(Redirect source) {
        Objects.requireNonNull(source, "Source must be non null.");
        redirects[0] = source;
        return this;
    }

    /**
     * Sets the standard output destination. Process started by the {@link #start()} method send its
     * standard output to this destination.
     * <p>
     * If the destination is {@link Redirect#PIPE PIPE}, the default value, then the standard output
     * of a subprocess can be read using the input stream returned by
     * {@link Process#getInputStream()}. If the destination is set to is set to
     * {@link Redirect#INHERIT INHERIT}, then {@link Process#getInputStream()} returns a closed
     * input stream.
     *
     * @param destination the new standard output destination
     * @return this {@link TruffleProcessBuilder builder}
     * @since 1.0
     */
    public TruffleProcessBuilder redirectOutput(Redirect destination) {
        Objects.requireNonNull(destination, "Destination must be non null.");
        redirects[1] = destination;
        return this;
    }

    /**
     * Sets the standard error output destination. Process started by the {@link #start()} method
     * send its error output to this destination.
     * <p>
     * If the destination is {@link Redirect#PIPE PIPE}, the default value, then the standard error
     * of a subprocess can be read using the input stream returned by
     * {@link Process#getErrorStream()}. If the destination is set to is set to
     * {@link Redirect#INHERIT INHERIT}, then {@link Process#getErrorStream()} returns a closed
     * input stream.
     *
     * @param destination the new error output destination
     * @return this {@link TruffleProcessBuilder builder}
     * @since 1.0
     */
    public TruffleProcessBuilder redirectError(Redirect destination) {
        Objects.requireNonNull(destination, "Destination must be non null.");
        redirects[2] = destination;
        return this;
    }

    /**
     * If {@code true} the subprocess standard input, output and error output are the same as those
     * of the current Java process.
     *
     * @param enabled enables standard I/O inheritance
     * @return this {@link TruffleProcessBuilder builder}
     * @since 1.0
     */
    public TruffleProcessBuilder inheritIO(boolean enabled) {
        this.inheritIO = enabled;
        return this;
    }

    /**
     * If {@code true} the environment variables are not inherited by the subprocess.
     *
     * @param clear disables inheritance of environment variables
     * @return this {@link TruffleProcessBuilder builder}
     * @since 1.0
     */
    public TruffleProcessBuilder clearEnvironment(boolean clear) {
        this.clearEnvironment = clear;
        return this;
    }

    /**
     * Sets the subprocess environment variable.
     *
     * @param name the variable name
     * @param value the value
     * @return this {@link TruffleProcessBuilder builder}
     * @since 1.0
     */
    public TruffleProcessBuilder environment(String name, String value) {
        Objects.requireNonNull(name, "Name must be non null.");
        Objects.requireNonNull(value, "Value must be non null.");
        if (this.env == null) {
            this.env = new HashMap<>();
        }
        this.env.put(name, value);
        return this;
    }

    /**
     * Shortcut for setting multiple {@link #environment(String, String) environment variables}
     * using a map. All values of the provided map must be non-null.
     *
     * @param environment environment variables
     * @see #environment(String, String) To set a single environment variable.
     * @return this {@link TruffleProcessBuilder builder}
     * @since 1.0
     */
    public TruffleProcessBuilder environment(Map<String, String> environment) {
        for (Map.Entry<String, String> e : environment.entrySet()) {
            environment(e.getKey(), e.getValue());
        }
        return this;
    }

    /**
     * Starts a new subprocess using the attributes of this builder. The new process invokes the
     * command with arguments given by {@link #command()}, in a working directory given by
     * {@link #directory()}, with a process environment inherited from {@link Context} and possibly
     * extended by {@link #environment(java.lang.String, java.lang.String)}.
     *
     * @return a new {@link Process} instance
     * @throws NullPointerException if an element of the command list is null
     * @throws IndexOutOfBoundsException if the command is an empty list
     * @throws SecurityException when either the process creation is not allowed or the environment
     *             was modified by this builder but environment modification is not allowed.
     * @throws IOException if the process fails to execute
     * @since 1.0
     */
    public Process start() throws IOException {
        List<String> useCmd = new ArrayList<>();
        for (String item : cmd) {
            if (item == null) {
                throw new NullPointerException("Command contains null.");
            }
            useCmd.add(item);
        }
        if (useCmd.isEmpty()) {
            throw new IndexOutOfBoundsException("Command is empty");
        }
        if (inheritIO) {
            Arrays.fill(redirects, Redirect.INHERIT);
        }
        EnvironmentAccess envAccess = TruffleLanguage.AccessAPI.engineAccess().getEnvironmentAccess(polylgotLanguageContext);
        Map<String, String> useEnv;
        if (envAccess == EnvironmentAccess.ALL) {
            useEnv = clearEnvironment ? new HashMap<>() : new HashMap<>(TruffleLanguage.AccessAPI.engineAccess().getProcessEnvironment(polylgotLanguageContext));
            if (env != null) {
                useEnv.putAll(env);
            }
        } else if (env != null || clearEnvironment) {
            throw new SecurityException("Environment modification is not allowed.");
        } else {
            useEnv = new HashMap<>(TruffleLanguage.AccessAPI.engineAccess().getProcessEnvironment(polylgotLanguageContext));
        }
        String useCwd;
        if (cwd != null) {
            useCwd = cwd.getSPIPath().toString();
        } else {
            useCwd = fileSystem.toAbsolutePath(fileSystem.parsePath("")).toString();
        }
        ProcessHandler.ProcessCommand processCommand = TruffleLanguage.AccessAPI.engineAccess().newProcessCommand(
                        polylgotLanguageContext,
                        useCmd,
                        useCwd,
                        useEnv,
                        redirectErrorStream,
                        redirects);
        return TruffleLanguage.AccessAPI.engineAccess().startProcess(polylgotLanguageContext, processCommand);
    }
}
