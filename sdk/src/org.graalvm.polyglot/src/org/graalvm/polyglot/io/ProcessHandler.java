/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.graalvm.polyglot.Context.Builder;

/**
 * Service-provider for guest languages process builder. This interface allows embedder to intercept
 * subprocess creation done by guest languages.
 *
 * @since 19.1.0
 */
public interface ProcessHandler {

    /**
     * A request to start a new subprocess with given attributes.
     * <p>
     * The default implementation uses {@link ProcessBuilder} to create the new subprocess. The
     * subprocess current working directory is set to {@link ProcessCommand#getDirectory()}. The
     * {@link ProcessCommand#getDirectory()} value was either explicitely set by the guest language
     * or the {@link FileSystem}'s current working directory is used. The subprocess environment is
     * set to {@link ProcessCommand#getEnvironment()}, the initial value of
     * {@link ProcessBuilder#environment()} is cleaned. The {@link ProcessCommand#getEnvironment()}
     * contains the environment variables set by guest language and possibly also the JVM process
     * environment depending on value of
     * {@link Builder#allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess)}.
     * <p>
     * Implementation example: {@link ProcessHandlerSnippets#example}
     *
     * @param command the subprocess attributes
     * @throws SecurityException if the process creation was forbidden by this handler
     * @throws IOException if the process fails to execute
     * @since 19.1.0
     */
    Process start(ProcessCommand command) throws IOException;

    /**
     * Subprocess attributes passed to
     * {@link #start(org.graalvm.polyglot.io.ProcessHandler.ProcessCommand) start} method.
     *
     * @since 19.1.0
     */
    final class ProcessCommand {

        private List<String> cmd;
        private String cwd;
        private Map<String, String> environment;
        private boolean redirectErrorStream;
        private Redirect inputRedirect;
        private Redirect outputRedirect;
        private Redirect errorRedirect;

        ProcessCommand(List<String> command, String cwd, Map<String, String> environment, boolean redirectErrorStream,
                        Redirect inputRedirect, Redirect outputRedirect, Redirect errorRedirect) {
            Objects.requireNonNull(command, "Command must be non null.");
            Objects.requireNonNull(environment, "Environment must be non null.");
            Objects.requireNonNull(inputRedirect, "InputRedirect must be non null.");
            Objects.requireNonNull(outputRedirect, "OutputRedirect must be non null.");
            Objects.requireNonNull(errorRedirect, "ErrorRedirect must be non null.");
            this.cmd = Collections.unmodifiableList(new ArrayList<>(command));
            this.cwd = cwd;
            this.environment = Collections.unmodifiableMap(new HashMap<>(environment));
            this.redirectErrorStream = redirectErrorStream;
            this.inputRedirect = inputRedirect;
            this.outputRedirect = outputRedirect;
            this.errorRedirect = errorRedirect;
        }

        /**
         * Returns the subprocess executable and arguments as an immutable list.
         *
         * @since 19.1.0
         */
        public List<String> getCommand() {
            return cmd;
        }

        /**
         * Returns the subprocess working directory.
         *
         * @since 19.1.0
         */
        public String getDirectory() {
            return cwd;
        }

        /**
         * Returns the subprocess environment as an immutable map.
         *
         * @since 19.1.0
         */
        public Map<String, String> getEnvironment() {
            return environment;
        }

        /**
         * Return whether the standard error output should be merged into standard output.
         *
         * @since 19.1.0
         */
        public boolean isRedirectErrorStream() {
            return redirectErrorStream;
        }

        /**
         * Returns the standard input source.
         *
         * @since 19.1.0
         */
        public Redirect getInputRedirect() {
            return inputRedirect;
        }

        /**
         * Returns the standard output destination.
         *
         * @since 19.1.0
         */
        public Redirect getOutputRedirect() {
            return outputRedirect;
        }

        /**
         * Returns the standard error output destination.
         *
         * @since 19.1.0
         */
        public Redirect getErrorRedirect() {
            return errorRedirect;
        }
    }

    /**
     * Represents a source of subprocess input or a destination of subprocess output.
     *
     * @since 19.1.0
     */
    final class Redirect {

        /**
         * Indicates that subprocess I/O will be connected to the current Java process using a pipe.
         *
         * @since 19.1.0
         */
        public static final Redirect PIPE = new Redirect(Type.PIPE, null);

        /**
         * Indicates that subprocess I/O source or destination will be the same as those of the
         * current process.
         *
         * @since 19.1.0
         */
        public static final Redirect INHERIT = new Redirect(Type.INHERIT, null);

        private final Type type;
        private final OutputStream stream;

        Redirect(Type type, OutputStream stream) {
            Objects.requireNonNull(type, "Type must be non null.");
            this.type = type;
            this.stream = stream;
        }

        OutputStream getOutputStream() {
            return stream;
        }

        /**
         * {@inheritDoc}
         *
         * @since 19.1.0
         */
        @Override
        public String toString() {
            return type.toString();
        }

        /**
         * {@inheritDoc}
         *
         * @since 19.1.0
         */
        @Override
        public int hashCode() {
            return type.hashCode();
        }

        /**
         * {@inheritDoc}
         *
         * @since 19.1.0
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != Redirect.class) {
                return false;
            }
            return type.equals(((Redirect) obj).type);
        }

        enum Type {
            /**
             * The type of {@link Redirect#PIPE Redirect.PIPE}.
             */
            PIPE,
            /**
             * The type of {@link Redirect#INHERIT Redirect.INHERIT}.
             */
            INHERIT,
            /**
             * The type of {@link Redirect#stream(java.io.OutputStream) Redirect.stream}.
             */
            STREAM
        }
    }
}

final class ProcessHandlerSnippets implements ProcessHandler {

    @Override
    // @formatter:off
    // BEGIN: ProcessHandlerSnippets#example
    public Process start(ProcessCommand command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command.getCommand())
            .redirectErrorStream(command.isRedirectErrorStream())
            .redirectInput(asProcessBuilderRedirect(command.getInputRedirect()))
            .redirectOutput(asProcessBuilderRedirect(command.getOutputRedirect()))
            .redirectError(asProcessBuilderRedirect(command.getErrorRedirect()));
        Map<String, String> env = builder.environment();
        env.clear();
        env.putAll(command.getEnvironment());
        String cwd = command.getDirectory();
        if (cwd != null) {
            builder.directory(Paths.get(cwd).toFile());
        }
        return builder.start();
    }
    // END: ProcessHandlerSnippets#example
    // @formatter:on

    private static java.lang.ProcessBuilder.Redirect asProcessBuilderRedirect(ProcessHandler.Redirect redirect) {
        if (redirect == ProcessHandler.Redirect.PIPE) {
            return java.lang.ProcessBuilder.Redirect.PIPE;
        } else if (redirect == ProcessHandler.Redirect.INHERIT) {
            return java.lang.ProcessBuilder.Redirect.INHERIT;
        } else {
            throw new IllegalStateException("Unsupported redirect: " + redirect);
        }
    }
}
