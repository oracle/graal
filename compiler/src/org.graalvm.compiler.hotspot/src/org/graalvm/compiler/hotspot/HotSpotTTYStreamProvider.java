/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.hotspot.HotSpotGraalOptionValues.GRAAL_OPTION_PROPERTY_PREFIX;
import static org.graalvm.compiler.hotspot.HotSpotGraalOptionValues.defaultOptions;
import static org.graalvm.compiler.hotspot.HotSpotTTYStreamProvider.Locker.UNLOCKED;
import static org.graalvm.compiler.hotspot.HotSpotTTYStreamProvider.Locker.UNLOCKED_AFTER_LOCKED;
import static org.graalvm.word.LocationIdentity.ANY_LOCATION;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Random;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.debug.TTYStreamProvider;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.IsolateUtil;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

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
                       "Using %o as filename sends logging to System.out whereas %e sends logging to System.err.", type = OptionType.Expert)
        public static final LogStreamOptionKey LogFile = new LogStreamOptionKey();
        // @formatter:on
    }

    @Override
    public PrintStream getStream() {
        return Options.LogFile.getStream();
    }

    /**
     * Gets a pointer to a word that will be used to synchronize write operations to the log file.
     */
    private static Pointer getLockPointer() {
        // Substituted by Target_org_graalvm_compiler_hotspot_HotSpotTTYStreamProvider
        return WordFactory.nullPointer();
    }

    /**
     * Provides mutual exclusion using a pointer to a lock word. Locking is implemented by spinning
     * with a sleep in between each spin.
     */
    public static class Locker implements AutoCloseable {
        /**
         * Initial max milliseconds to sleep in each spin loop iteration while waiting for the lock
         * to be unlocked.
         */
        private static final int INITIAL_SLEEP = 5;

        /**
         * Absolute max milliseconds to sleep in each spin loop iteration while waiting for the lock
         * to be unlocked.
         */
        private static final int MAX_SLEEP = 2000;

        /**
         * Pointer to a word used in conjunctions with CAS operations to implement a lock. The word
         * must have been initialized to {@link #UNLOCKED}.
         */
        private final Pointer lock;

        /**
         * Value for the lock word denoting that it is unlocked.
         */
        public static final long UNLOCKED = 0;

        /**
         * Value for the lock word denoting that it is locked.
         */
        static final long LOCKED = 1;

        /**
         * Value for the lock word denoting that it is unlocked after being locked.
         */
        public static final long UNLOCKED_AFTER_LOCKED = 2;

        /**
         * The value of the lock word prior to this object acquiring the lock.
         */
        long initialValue;

        /**
         * Value to set the lock word to when this object releases the lock.
         */
        private final long unlockValue;

        /**
         * Opens a scope which locks {@code lock}. The lock is released by {@link #close()}.
         *
         * @param lock pointer to lock word
         * @param rand RNG to mitigate threads waiting for the lock from clashing with each other
         */
        public Locker(Pointer lock, Random rand) {
            this(lock, rand, UNLOCKED);
        }

        /**
         * Opens a scope which locks {@code lock}. The lock is released by {@link #close()}.
         *
         * @param lock pointer to lock word
         * @param rand RNG to mitigate threads waiting for the lock from clashing with each other
         * @param unlockValue the value to write to the lock word to release the lock
         */
        public Locker(Pointer lock, Random rand, long unlockValue) {
            this.unlockValue = unlockValue;
            this.lock = lock;
            int sleepLimit = INITIAL_SLEEP;
            while (true) {
                long value = lock.readLong(0);
                if (value == LOCKED) {
                    try {
                        // Randomize sleep time to mitigate waiting threads
                        // performing in lock-step with each other.
                        int sleep = rand.nextInt(sleepLimit);
                        Thread.sleep(sleep);
                        // Exponential back-off up to MAX_SLEEP ms
                        sleepLimit = Math.min(MAX_SLEEP, sleepLimit * 2);
                    } catch (InterruptedException e) {
                    }
                    continue;
                }
                if (lock.compareAndSwapLong(0, value, LOCKED, ANY_LOCATION) == value) {
                    initialValue = value;
                    break;
                }
            }
        }

        @Override
        public void close() {
            lock.writeLong(0, unlockValue);
        }
    }

    /**
     * An output stream for writing to a file where each write operation is synchronized across the
     * whole process, not just the threads within the current isolate.
     */
    static class ProcessSynchronizedOutputStream extends OutputStream {
        private final Pointer lock;
        private final String name;
        private FileOutputStream out;
        private final Random rand;

        /**
         * Creates a global output stream for writing the file denoted by {@code name} where each
         * operation is serialized on {@code lock}.
         */
        ProcessSynchronizedOutputStream(Pointer lock, String name) {
            this.lock = lock;
            this.name = name;
            this.rand = new Random();
        }

        private FileOutputStream out() throws FileNotFoundException {
            if (out == null) {
                out = new FileOutputStream(name, true);
            }
            return out;
        }

        @SuppressWarnings("try")
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            try (Locker l = new Locker(lock, rand)) {
                out().write(b, off, len);
            }
        }

        @SuppressWarnings("try")
        @Override
        public void write(int b) throws IOException {
            try (Locker l = new Locker(lock, rand)) {
                out().write(b);
            }
        }

        @Override
        public void flush() throws IOException {
            out().flush();
        }

        @Override
        public void close() throws IOException {
            out().close();
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

            private OutputStream lazy() throws FileNotFoundException {
                if (lazy == null) {
                    synchronized (this) {
                        if (lazy == null) {
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
                                        boolean isolateSpecific = nameTemplate.contains("%i") || nameTemplate.contains("%I");
                                        Pointer lock = getLockPointer();
                                        if (!isolateSpecific && lock.isNonNull()) {
                                            lazy = new ProcessSynchronizedOutputStream(lock, name);
                                        } else {
                                            final boolean enableAutoflush = true;
                                            FileOutputStream result = new FileOutputStream(name);
                                            printVMConfig(enableAutoflush, result);
                                            lazy = result;
                                        }
                                }
                                return lazy;
                            }

                            lazy = HotSpotJVMCIRuntime.runtime().getLogStream();
                            PrintStream ps = new PrintStream(lazy);
                            Pointer lock = getLockPointer();
                            try (Locker l = lock.isNull() ? null : new Locker(lock, new Random(), UNLOCKED_AFTER_LOCKED)) {
                                if (l == null || l.initialValue == UNLOCKED) {
                                    // Use Locker to ensure the following message is printed once
                                    // per process instead of once per isolate.
                                    ps.printf("[Use -D%sLogFile=<path> to redirect Graal log output to a file.]%n", GRAAL_OPTION_PROPERTY_PREFIX);
                                    ps.flush();
                                }
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
                String cmd = Services.getSavedProperties().get("sun.java.command");
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
