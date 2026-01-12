/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.options.ExcludeFromJacocoGeneratedReport;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.util.EconomicHashMap;

/**
 * Provides a Java interface to hardware performance counters using the PAPI library via a JNI
 * bridge library.
 * <p>
 * Hardware performance counters are supported on the AMD64 Linux platform only and require the path
 * to the library to be passed in the {@link #LIB_PATH_PROPERTY} system property.
 * <p>
 * Thread safety: performance events can be measured from multiple threads with a dedicated
 * {@link HardwarePerformanceCounters} instance for each thread. On libgraal, every thread using
 * performance counters must be attached, e.g., using
 * {@link jdk.graal.compiler.hotspot.HotSpotGraalServiceThread} and
 * {@link LibGraalSupport#openCompilationRequestScope()}.
 */
@SuppressWarnings("restricted")
@ExcludeFromJacocoGeneratedReport("requires PAPI and building the bridge library on AMD64 Linux")
public final class HardwarePerformanceCounters implements AutoCloseable {
    /**
     * An interface for interacting with the PAPI JNI bridge library. This interface enables
     * different implementations for libgraal and jargraal.
     * <p>
     * The jargraal implementation directly loads the library and invokes its functions via JNI.
     *
     * <pre>
     * jargraal --JNI-- libpapibridge.so --dynamic link-- libpapi.so
     * </pre>
     *
     * Although this scheme works with libgraal, it requires loading the library from libgraal and
     * pulling in the {@link System#load} code in the libgraal image. Instead, libgraal piggybacks
     * HotSpot's {@link System#load} implementation:
     *
     * <pre>
     * libgraal --JNI-- HotSpot --JNI-- libpapibridge.so --dynamic link-- libpapi.so
     * </pre>
     */
    public interface PAPIBridge {
        /**
         * Delegates to {@link HardwarePerformanceCounters#linkAndInitializeOnce()}.
         */
        boolean linkAndInitializeOnce();

        /**
         * Delegates to {@link HardwarePerformanceCounters#createEventSet(String[])}.
         */
        int createEventSet(String[] eventNames);

        /**
         * Delegates to {@link HardwarePerformanceCounters#getNull()}.
         */
        int getNull();

        /**
         * Delegates to {@link HardwarePerformanceCounters#cleanAndDestroyEventSet(int)}.
         */
        boolean cleanAndDestroyEventSet(int eventset);

        /**
         * Delegates to {@link HardwarePerformanceCounters#start()}.
         */
        boolean start(int eventset);

        /**
         * Delegates to {@link HardwarePerformanceCounters#stop()}.
         */
        long[] stop(int eventset);
    }

    /**
     * The jargraal interface for directly interacting with the PAPI JNI bridge library. This
     * implementation loads the library and invokes its functions via JNI.
     */
    @LibGraalSupport.HostedOnly
    public static final class JargraalPAPIBridge implements PAPIBridge {
        @Override
        public boolean linkAndInitializeOnce() {
            return HardwarePerformanceCounters.linkAndInitializeOnce();
        }

        @Override
        public int createEventSet(String[] eventNames) {
            return HardwarePerformanceCounters.createEventSet(eventNames);
        }

        @Override
        public int getNull() {
            return HardwarePerformanceCounters.getNull();
        }

        @Override
        public boolean cleanAndDestroyEventSet(int eventset) {
            return HardwarePerformanceCounters.cleanAndDestroyEventSet(eventset);
        }

        @Override
        public boolean start(int eventset) {
            return HardwarePerformanceCounters.start(eventset);
        }

        @Override
        public long[] stop(int eventset) {
            return HardwarePerformanceCounters.stop(eventset);
        }
    }

    /**
     * The property name for the path to the PAPI JNI bridge.
     */
    @LibGraalSupport.HostedOnly //
    private static final String LIB_PATH_PROPERTY = "debug.jdk.graal.PAPIBridgePath";

    /**
     * Flag to track whether the native library has been initialized.
     */
    @LibGraalSupport.HostedOnly //
    private static boolean isInitialized = false;

    /**
     * Checks that the current platform is supported and initializes the PAPI JNI bridge library.
     */
    @LibGraalSupport.HostedOnly
    public static synchronized boolean linkAndInitializeOnce() {
        if (isInitialized) {
            return true;
        }
        String libraryPath = GraalServices.getSavedProperty(LIB_PATH_PROPERTY);
        GraalError.guarantee(libraryPath != null, "no path to the PAPI bridge library was provided");
        System.load(libraryPath);
        if (!initialize()) {
            TTY.println("Failed to initialize the PAPI bridge library");
            return false;
        }
        isInitialized = true;
        return true;
    }

    /**
     * The implementation to use for interacting with the PAPI bridge library.
     */
    private final PAPIBridge bridge;

    /**
     * The list of event names to be measured.
     */
    private final List<String> eventNames;

    /**
     * The native event set handle.
     */
    private int eventSet;

    /**
     * The PAPI_NULL constant.
     */
    private final int papiNull;

    /**
     * Flag to track whether measurements have been started.
     */
    private boolean started;

    /**
     * Creates a new HardwarePerformanceCounters instance.
     *
     * @param eventNames the list of event names to be measured
     * @param bridge the implementation to use for interacting with the PAPI bridge library
     */
    HardwarePerformanceCounters(List<String> eventNames, PAPIBridge bridge) {
        this.bridge = bridge;
        this.eventNames = List.copyOf(eventNames);
        for (String eventName : this.eventNames) {
            Objects.requireNonNull(eventName);
        }
        boolean success = bridge.linkAndInitializeOnce();
        if (!success) {
            throw new GraalError("Failed to initialize the PAPI bridge");
        }
        this.eventSet = bridge.createEventSet(this.eventNames.toArray(String[]::new));
        this.papiNull = bridge.getNull();
        GraalError.guarantee(this.eventSet != papiNull, "failed to create an event set");
    }

    /**
     * Starts measuring the specified events.
     */
    public void start() {
        boolean success = bridge.start(eventSet);
        GraalError.guarantee(success, "failed to start measurements");
        started = true;
    }

    /**
     * Stops measuring the specified events and returns the counts.
     *
     * @return a map of event names to their respective counts
     */
    public Map<String, Long> stop() {
        long[] javaCounts = bridge.stop(eventSet);
        GraalError.guarantee(javaCounts != null, "failed to stop measurements");
        started = false;
        Map<String, Long> map = new EconomicHashMap<>(eventNames.size());
        for (int i = 0; i < eventNames.size(); i++) {
            map.put(eventNames.get(i), javaCounts[i]);
        }
        return map;
    }

    /**
     * Releases native resources.
     */
    @Override
    public void close() {
        if (eventSet != papiNull) {
            if (started) {
                stop();
            }
            boolean success = bridge.cleanAndDestroyEventSet(eventSet);
            eventSet = papiNull;
            GraalError.guarantee(success, "failed to destroy an event set");
        }
    }

    /**
     * Initializes the PAPI library.
     *
     * @return true if successful, false otherwise
     */
    private static native boolean initialize();

    /**
     * Creates a new PAPI event set and adds named events.
     *
     * @param eventNames array of event names to add
     * @return event set handle or PAPI_NULL
     */
    private static native int createEventSet(String[] eventNames);

    /**
     * Gets the native PAPI_NULL constant.
     *
     * @return PAPI_NULL
     */
    private static native int getNull();

    /**
     * Cleans up and destroys the given event set.
     *
     * @param eventset the event set handle
     * @return true if successful, false otherwise
     */
    private static native boolean cleanAndDestroyEventSet(int eventset);

    /**
     * Starts counting events for the given event set.
     *
     * @param eventset the event set handle
     * @return true if successful, false otherwise
     */
    private static native boolean start(int eventset);

    /**
     * Stops counting events for the given event set and returns the counts.
     *
     * @param eventset the event set handle
     * @return an array of event counts, or null if an error occurs
     */
    private static native long[] stop(int eventset);
}
