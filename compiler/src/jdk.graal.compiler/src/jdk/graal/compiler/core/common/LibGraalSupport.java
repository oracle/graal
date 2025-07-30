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
package jdk.graal.compiler.core.common;

import org.graalvm.collections.EconomicMap;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Service provider interface (SPI) defining support needed by libgraal. Using an SPI instead of
 * directly depending on modules such as {@code org.graalvm.nativeimage.libgraal} and
 * {@code org.graalvm.jniutils} allows use of jargraal (e.g., putting {@code compiler.jar} on the
 * {@code --upgrade-module-path} for use with Truffle on a non-GraalVM JDK) without concern for
 * dependencies that are only needed when building libgraal.
 */
public interface LibGraalSupport {

    /**
     * Denotes that the annotated element (type, method, or field) is only visible when running
     * hosted on HotSpot (i.e., jargraal or while building libgraal) but not cannot be used at
     * libgraal run time. This annotation is ignored when building a non-libgraal native image.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
    @interface HostedOnly {
        /**
         * The name of a libgraal build-time system property that allows the annotated element to be
         * used at libgraal run time. The value of the property is parsed using
         * {@link Boolean#parseBoolean}.
         */
        String unlessTrue() default "";
    }

    /**
     * Prefix to use for an image runtime system property describing some aspect of the libgraal
     * image configuration. These properties are included in the output of
     * {@code -Djdk.graal.ShowConfiguration}.
     */
    String LIBGRAAL_SETTING_PROPERTY_PREFIX = "libgraal.setting.";

    /**
     * Called to signal a fatal, non-recoverable error. This method does not return or throw an
     * exception but calls the HotSpot fatal crash routine that produces an hs-err crash log.
     *
     * @param message a description of the error condition
     */
    void fatalError(String message);

    /**
     * Performs pre- and post-actions around a libgraal compilation.
     */
    AutoCloseable openCompilationRequestScope();

    /**
     * Creates a pre-allocated and pre-initialized word that is off-heap.
     *
     * @param initialValue the initial value of the off-heap word
     * @return a supplier of the address of the off-heap word
     */
    @LibGraalSupport.HostedOnly
    Supplier<Long> createGlobal(long initialValue);

    /**
     * Gets a map from the {@linkplain Class#forName(String) name} of a class to the name of its
     * enclosing module. There is one entry in the map for each class loadable via the libgraal
     * class loader.
     *
     * @return an unmodifiable map
     */
    Map<String, String> getClassModuleMap();

    /**
     * Notifies the runtime that the caller is at a point where the live set of objects is expected
     * to just have decreased significantly and now is a good time for a partial or full collection.
     *
     * @param suggestFullGC if a GC will be performed, then suggests a full GC is done. This is true
     *            when the caller believes the heap occupancy is close to the minimal set of live
     *            objects for Graal (e.g. after a compilation).
     */
    void notifyLowMemoryPoint(boolean suggestFullGC);

    /**
     * Enqueues pending {@link Reference}s into their corresponding {@link ReferenceQueue}s and
     * executes pending cleaners.
     * <p>
     * If automatic reference handling is enabled, this method is a no-op.
     */
    void processReferences();

    /**
     * Dumps the heap to {@code outputFile} in hprof format.
     *
     * @param live if true, performs a full GC first so that only live objects are dumped
     * @throws IOException if an IO error occurred during dumping
     * @throws UnsupportedOperationException if this operation is not supported.
     */
    void dumpHeap(String outputFile, boolean live) throws IOException;

    /**
     * Gets the address of the current isolate.
     */
    long getIsolateAddress();

    /**
     * Gets an identifier for the current isolate that is guaranteed to be unique for the first
     * {@code 2^64 - 1} isolates in the process.
     */
    long getIsolateID();

    /**
     * Handles the libgraal options that were parsed.
     *
     * @param settings libgraal option values
     */
    void notifyOptions(EconomicMap<String, String> settings);

    /**
     * Prints the help text for the libgraal options.
     *
     * @param out where to print
     * @param namePrefix prefix to use for each libgraal option name
     */
    void printOptions(PrintStream out, String namePrefix);

    /**
     * Performs libgraal specific logic when initializing Graal.
     */
    void initialize();

    /**
     * Performs libgraal specific logic when shutting down Graal.
     *
     * @param callbackClassName class name derived from
     *            {@link jdk.graal.compiler.hotspot.HotSpotGraalCompiler.Options#OnShutdownCallback}
     * @param callbackMethodName method name derived from
     *            {@link jdk.graal.compiler.hotspot.HotSpotGraalCompiler.Options#OnShutdownCallback}
     */
    void shutdown(String callbackClassName, String callbackMethodName);

    /**
     * Returns true if the current runtime is in the libgraal native image (i.e. SVM).
     *
     * This method is intrinsified by Native Image to return true.
     */
    static boolean inLibGraalRuntime() {
        return false;
    }

    /**
     * Non-null iff accessed in the context of the libgraal class loader or executing in the
     * libgraal runtime.
     */
    LibGraalSupport INSTANCE = Init.init();

    /**
     * Initialization support for {@link LibGraalSupport#INSTANCE}.
     */
    class Init {
        @SuppressWarnings("try")
        static LibGraalSupport init() {
            Module module = LibGraalSupport.class.getModule();
            if (module.isNamed()) {
                // The named Graal module is not loaded by the libgraal class loader
                // and is thus it must have a null LibGraalSupport instance.
                return null;
            }
            try (var ignored = new ContextClassLoaderScope(LibGraalSupport.class.getClassLoader())) {
                return ServiceLoader.load(LibGraalSupport.class).findFirst().orElseThrow(() -> new RuntimeException("No provider of " + LibGraalSupport.class.getName() + " service available"));
            }
        }
    }
}
