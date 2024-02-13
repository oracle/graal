/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.hotspot;

import static jdk.graal.compiler.hotspot.HotSpotGraalOptionValues.GRAAL_OPTION_PROPERTY_PREFIX;
import static jdk.graal.compiler.hotspot.HotSpotGraalOptionValues.defaultOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.TTYStreamProvider;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.serviceprovider.IsolateUtil;
import jdk.graal.compiler.serviceprovider.ServiceProvider;
import jdk.graal.compiler.word.Word;

import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.services.Services;

@ServiceProvider(TTYStreamProvider.class)
public class HotSpotTTYStreamProvider implements TTYStreamProvider {

    public static class Options {

        // @formatter:off
        @Option(help = "File to which logging is sent.  A %p in the name will be replaced with a string identifying " +
                       "the process, usually the process id and %t will be replaced by System.currentTimeMillis(). " +
                       "If the current runtime is in an isolate, then %i will be replaced by '<isolate id>' " +
                       "otherwise %i is removed. An %I is the same as %i except that the replacement is " +
                       "'<isolate id>@<isolate address>'. " +
                       "Using %o as filename sends logging to System.out whereas %e sends logging to System.err.", type = OptionType.Debug)
        public static final LogStreamOptionKey LogFile = new LogStreamOptionKey();
        // @formatter:on
    }

    @Override
    public PrintStream getStream() {
        return Options.LogFile.getStream();
    }

    static {
        Word.ensureInitialized();
    }

    /**
     * Gets a pointer to a global word initialized to 0.
     */
    private static final GlobalAtomicLong BARRIER = new GlobalAtomicLong(0L);

    /**
     * Executes {@code action}. {@link #BARRIER} is used to ensure the action is executed exactly
     * once in the process (i.e. synchronized across all threads and isolates) and that threads will
     * block here until the action is guaranteed to have been executed.
     */
    private static boolean execute(Runnable action) {
        final long initial = 0L;
        final long executing = 1L;
        final long executed = 2L;

        while (true) { // TERMINATION ARGUMENT: busy wait loop
            long value = BARRIER.get();
            if (value == initial) {
                if (BARRIER.compareAndSet(value, executing)) {
                    action.run();
                    BARRIER.set(executed);
                    return true;
                }
            } else {
                if (value == executed) {
                    return false;
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * An option for a configurable file name that can also open a {@link PrintStream} on the file.
     * If no value is given for the option, the stream will output to HotSpot's
     * {@link HotSpotJVMCIRuntime#getLogStream() log} stream
     */
    private static class LogStreamOptionKey extends OptionKey<String> {

        LogStreamOptionKey() {
            super(null);
        }

        /**
         * @return {@code nameTemplate} with all instances of %p replaced by
         *         {@link GraalServices#getExecutionID()} and %t by
         *         {@link System#currentTimeMillis()}. Checks %o and %e are not combined with any
         *         other characters.
         */
        private static String makeFilename(String nameTemplate) {
            String name = nameTemplate;
            if (name.contains("%p")) {
                name = name.replace("%p", GraalServices.getExecutionID());
            }
            if (name.contains("%i")) {
                name = name.replace("%i", IsolateUtil.getIsolateID(false));
            }
            if (name.contains("%I")) {
                name = name.replace("%I", IsolateUtil.getIsolateID(true));
            }
            if (name.contains("%t")) {
                name = name.replace("%t", String.valueOf(System.currentTimeMillis()));
            }

            for (String subst : new String[]{"%o", "%e"}) {
                if (name.contains(subst) && !name.equals(subst)) {
                    throw new IllegalArgumentException("LogFile substitution " + subst + " cannot be combined with any other characters");
                }
            }

            return name;
        }

        /**
         * An output stream that redirects to {@link HotSpotJVMCIRuntime#getLogStream()}. The
         * {@link HotSpotJVMCIRuntime#getLogStream()} value is only accessed the first time an IO
         * operation is performed on the stream. This is required to break a deadlock in early JVMCI
         * initialization.
         */
        class DelayedOutputStream extends OutputStream {
            @NativeImageReinitialize private volatile OutputStream lazy;

            private OutputStream lazy() {
                if (lazy == null) {
                    synchronized (this) {
                        if (lazy == null) {
                            try {
                                String nameTemplate = LogStreamOptionKey.this.getValue(defaultOptions());
                                if (nameTemplate != null) {
                                    String name = makeFilename(nameTemplate);
                                    switch (name) {
                                        case "%o":
                                            lazy = System.out;
                                            break;
                                        case "%e":
                                            lazy = System.err;
                                            break;
                                        default:
                                            boolean executed = execute(() -> {
                                                File file = new File(name);
                                                if (file.exists()) {
                                                    file.delete();
                                                }
                                            });
                                            final boolean enableAutoflush = true;
                                            FileOutputStream result = new FileOutputStream(name, true);
                                            if (executed) {
                                                printVMConfig(enableAutoflush, result);
                                            }
                                            lazy = result;
                                    }
                                    return lazy;
                                }

                                lazy = HotSpotJVMCIRuntime.runtime().getLogStream();
                                execute(() -> {
                                    PrintStream ps = new PrintStream(lazy);
                                    ps.printf("[Use -D%sLogFile=<path> to redirect Graal log output to a file.]%n", GRAAL_OPTION_PROPERTY_PREFIX);
                                    ps.flush();

                                });
                            } catch (Throwable t) {
                                /*
                                 * Since this will typically happen on a compiler thread, the
                                 * exception would be silently swallowed by the CompilationWrapper.
                                 * Instead, it should result in a VM exit like handling of all other
                                 * malformed Graal options does.
                                 */
                                System.err.println("Error initializing Graal log output:");
                                t.printStackTrace();
                                HotSpotGraalServices.exit(1, HotSpotJVMCIRuntime.runtime());
                            }
                        }
                    }
                }
                return lazy;
            }

            @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "false positive on dead store to `ps`")
            private void printVMConfig(final boolean enableAutoflush, FileOutputStream result) {
                /*
                 * Add the JVM and Java arguments to the log file to help identity it.
                 */
                PrintStream ps = new PrintStream(result, enableAutoflush);
                List<String> inputArguments = GraalServices.getInputArguments();
                if (inputArguments != null) {
                    ps.println("VM Arguments: " + String.join(" ", inputArguments));
                }
                String cmd = Services.getSavedProperty("sun.java.command");
                if (cmd != null) {
                    ps.println("sun.java.command=" + cmd);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                lazy().write(b, off, len);
            }

            @Override
            public void write(int b) throws IOException {
                lazy().write(b);
            }

            @Override
            public void flush() throws IOException {
                lazy().flush();
            }

            @Override
            public void close() throws IOException {
                lazy().close();
            }
        }

        /**
         * Gets the print stream configured by this option. If no file is configured, the print
         * stream will output to HotSpot's {@link HotSpotJVMCIRuntime#getLogStream() log} stream.
         */
        public PrintStream getStream() {
            return new PrintStream(new DelayedOutputStream());
        }
    }
}
