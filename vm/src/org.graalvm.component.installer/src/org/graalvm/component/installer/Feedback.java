/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

import java.net.URL;
import java.nio.file.Path;

/**
 * Centralizes feedback from individual commands. Allows to centrally handle verbosity, stacktraces
 * of exceptions etc. Allows to work with different Bundles.
 */
public interface Feedback {
    /**
     * Returned from {@link #acceptLine} for an automatic accept.
     */
    String AUTO_YES = "<automatic-yes>";

    /**
     * Formats a message on stderr.
     * 
     * @param bundleKey key into the bundle
     * @param params optional positional arguments for the message
     */
    void message(String bundleKey, Object... params);

    /**
     * Formats a message on stdout.
     * 
     * @param bundleKey key into the bundle
     * @param params optional positional arguments for the message
     */
    void output(String bundleKey, Object... params);

    /**
     * Formats a message on stdout; will not print a newline.
     * 
     * @param bundleKey key into the bundle
     * @param params optional positional arguments for the message
     */
    void outputPart(String bundleKey, Object... params);

    /**
     * Formats a verbosePart-level message on stderr. Returns a flag indicating the verbosePart
     * level is on - use to bypass verbosePart messages.
     * <p/>
     * {code null} bundle key can be used to test verbose flag
     * 
     * @param bundleKey key into the bundle, or {@code null} to just return the verbose flag
     * @param params optional positional arguments for the message
     * @return {@code true}, if the verbosePart message level is on.
     */
    boolean verbosePart(String bundleKey, Object... params);

    /**
     * Formats a verbosePart-level message on stdout. Returns a flag indicating the verbosePart
     * level is on - use to bypass verbosePart messages.
     * <p/>
     * {code null} bundle key can be used to test verbose flag
     * 
     * @param bundleKey key into the bundle, or {@code null} to just return the verbose flag
     * @param params optional positional arguments for the message
     * @return {@code true}, if the verbosePart message level is on.
     */
    boolean verboseOutput(String bundleKey, Object... params);

    /**
     * Formats an error message on stderr. Depending on settings, the exception stacktrace may be
     * printed.
     * 
     * @param bundleKey key into the bundle
     * @param params optional positional arguments for the message
     * @param t thrown exception.
     */
    void error(String bundleKey, Throwable t, Object... params);

    /**
     * Formats a message using the bundle.
     * 
     * @param bundleKey key into the bundle
     * @param params optional positional arguments for the message
     * @return formatted message
     */
    String l10n(String bundleKey, Object... params);

    /**
     * Reports a message with possible Throwable and wraps it into a
     * {@link FailedOperationException}. The caller may directly {@code throw} the result.
     * 
     * @param bundleKey key into the bundle
     * @param params optional positional arguments for the message
     * @param t thrown exception.
     */
    RuntimeException failure(String bundleKey, Throwable t, Object... params);

    /**
     * Produces a Feedback instance, which uses different Bundle. Bundle is taken from the package
     * of the "clazz".
     * 
     * @param clazz reference class to locate the budle
     * @return Feedback which translates through the Bundle
     */
    <T> Feedback withBundle(Class<T> clazz);

    /**
     * Prints a verbatim message.
     * 
     * @param msg prints the message verbatim
     * @param verbose true, if this is only verbosePart
     */
    boolean verbatimOut(String msg, boolean verbose);

    boolean verbatimPart(String msg, boolean verbose);

    boolean verbatimPart(String msg, boolean error, boolean verbose);

    boolean backspace(int chars, boolean beVerbose);

    /**
     * Waits for user input confirmed by ENTER.
     * 
     * @param autoYes returns the {@link #AUTO_YES} if yes-to-all was specified on commandline.
     * @return accepted line.
     */
    String acceptLine(boolean autoYes);

    /**
     * Allows to enter password using console services.
     * 
     * @return password
     */
    char[] acceptPassword();

    /**
     * Provides a cache for remote files. The URL contents should be downloaded and stored to the
     * `local` file. The file should be marked with {@link java.io.File#deleteOnExit()}.
     * 
     * @param location remote location
     * @param local locally cached content
     */
    void addLocalFileCache(URL location, Path local);

    /**
     * Returns a local cache for the location. Returns {@code null}, if the content is not locally
     * available.
     * 
     * @param location the remote location
     * @return locally stored content or {@code null}.
     */
    Path getLocalCache(URL location);
}
