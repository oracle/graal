/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @since 19.0
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
     * @since 19.0
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
     * @since 19.0
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
     * @since 19.0
     */
    void fatalError();
}
