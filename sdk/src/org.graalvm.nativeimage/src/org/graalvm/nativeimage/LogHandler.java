/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;

/**
 * Low-level handler for log messages of native images. Implement this interface to create a custom
 * log handler, i.e., redirect log messages. The custom log handler must be installed in the
 * {@code ImageSingletons}, using this interface as the key, at {@code Feature.duringSetup()} time:
 *
 * <pre>
 *    class LogHandlerFeature implements Feature {
 *        {@literal @}Override
 *        public void duringSetup(DuringSetupAccess access) {
 *            ImageSingletons.add(LogHandler.class, new CustomLogHandler());
 *        }
 *    }
 * </pre>
 *
 * If no custom log handler is installed, a default is installed
 * {@code Feature.beforeAnalysis() before the static analysis}. The default handler prints log
 * messages to the standard output. Installing a custom log handler at a later time, after the
 * default one has been installed, results in an error.
 * <p>
 * The methods defined in this interface are called from places where Java object allocation is not
 * possible, e.g., during a garbage collection or before the heap is set up. If an implementation of
 * the interface allocates a Java object or array, an error is reported during image generation.
 *
 * @since 1.0
 */
public interface LogHandler {

    /**
     * Write raw bytes to the log.
     * <p>
     * The methods defined in this interface are called from places where Java object allocation is
     * not possible, e.g., during a garbage collection or before the heap is set up. If an
     * implementation of the interface allocates a Java object or array, an error is reported during
     * image generation.
     *
     * @since 1.0
     */
    void log(CCharPointer bytes, UnsignedWord length);

    /**
     * Flush the log to its destination.
     * <p>
     * The methods defined in this interface are called from places where Java object allocation is
     * not possible, e.g., during a garbage collection or before the heap is set up. If an
     * implementation of the interface allocates a Java object or array, an error is reported during
     * image generation.
     *
     * @since 1.0
     */
    void flush();

    /**
     * Exit the VM because a fatal, non-recoverable error situation has been detected. The
     * implementation of this method must not return, and it must not throw a Java exception. A
     * valid implementation is, e.g., to ask the OS to kill the process.
     * <p>
     * The methods defined in this interface are called from places where Java object allocation is
     * not possible, e.g., during a garbage collection or before the heap is set up. If an
     * implementation of the interface allocates a Java object or array, an error is reported during
     * image generation.
     *
     * @since 1.0
     */
    void fatalError();
}
