/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import java.nio.file.Path;

import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.impl.ProcessPropertiesSupport;

/**
 * Utility class to get and set properties of the OS process at run time.
 *
 * @since 19.0
 */
public final class ProcessProperties {
    /**
     * Return the canonicalized absolute pathname of the executable.
     *
     * @since 19.0
     */
    public static String getExecutableName() {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).getExecutableName();
    }

    /**
     * Get the Process ID of the process executing the image.
     *
     * @since 19.0
     */
    public static long getProcessID() {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).getProcessID();
    }

    /**
     * Get the Process ID of the given process object.
     *
     * @since 19.0
     */
    public static long getProcessID(Process process) {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).getProcessID(process);
    }

    /**
     * Wait for process termination and return its exit status.
     *
     * @since 19.0
     */
    public static int waitForProcessExit(long processID) {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).waitForProcessExit(processID);
    }

    /**
     * Kills the process. Whether the process represented by the given Process ID is normally
     * terminated or not is implementation dependent.
     *
     * @since 19.0
     */
    public static boolean destroy(long processID) {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).destroy(processID);
    }

    /**
     * Kills the process forcibly. The process represented by the given Process ID is forcibly
     * terminated. Forcible process destruction is defined as the immediate termination of a
     * process, whereas normal termination allows the process to shut down cleanly.
     *
     * @since 19.0
     */
    public static boolean destroyForcibly(long processID) {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).destroyForcibly(processID);
    }

    /**
     * Tests whether the process represented by the given Process ID is alive.
     *
     * @return true if the process represented by the given Process ID object has not yet
     *         terminated.
     *
     * @since 19.0
     */
    public static boolean isAlive(long processID) {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).isAlive(processID);
    }

    /**
     * Return the path of the object file defining the symbol specified as a {@link String}
     * containing the symbol name.
     *
     * @since 19.0
     */
    public static String getObjectFile(String symbol) {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).getObjectFile(symbol);
    }

    /**
     * Return the path of the object file defining the symbol specified as a
     * {@link CEntryPointLiteral} containing a function pointer to symbol.
     *
     * @since 19.0
     */
    public static String getObjectFile(CEntryPointLiteral<?> symbol) {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).getObjectFile(symbol);
    }

    /**
     * Set the program locale.
     *
     * @since 19.0
     */
    public static String setLocale(String category, String locale) {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).setLocale(category, locale);
    }

    /**
     * Replaces the current process image with the process image specified by the given path invoked
     * with the given args. This method does not return if the call is successful.
     *
     * @since 19.0
     */
    public static void exec(Path executable, String... args) {
        ImageSingletons.lookup(ProcessPropertiesSupport.class).exec(executable, args);
    }

    /**
     * If the running image is an executable the program name that is stored in the argument vector
     * of the running process gets returned.
     *
     * @throws UnsupportedOperationException if called from a platform that does not support
     *             argument vector manipulation (Windows) or if called from a shared library image.
     *
     * @since 20.1
     */
    public static String getArgumentVectorProgramName() {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).getArgumentVectorProgramName();
    }

    /**
     * If the running image is an executable the program name that is stored in the argument vector
     * of the running process gets replaced with the given name. If the size of the argument vector
     * is too small for the given name it gets truncated so that the environment vector next to the
     * argument vector does not get corrupted.
     *
     * @return true, if given name had to be truncated to fit in the argument vector
     * @throws UnsupportedOperationException if called from a platform that does not support
     *             argument vector manipulation (Windows) or if called from a shared library image.
     *
     * @since 20.1
     */
    public static boolean setArgumentVectorProgramName(String name) {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).setArgumentVectorProgramName(name);
    }

    /**
     * If the running image is an executable the total size of the argument vector of the running
     * process gets returned.
     *
     * @return the total size of the argument vector. Returns -1 if not supported on platform or
     *         called from a shared library image.
     *
     * @since 20.1
     */
    public static int getArgumentVectorBlockSize() {
        return ImageSingletons.lookup(ProcessPropertiesSupport.class).getArgumentVectorBlockSize();
    }

    private ProcessProperties() {
    }
}
