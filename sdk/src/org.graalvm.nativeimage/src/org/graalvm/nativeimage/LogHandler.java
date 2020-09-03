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
     * This method gets called if the VM finds itself in a fatal, non-recoverable error situation.
     * The callee receives a CCharPointer string that describes the error. Based on this string the
     * implementor can decide if it wants to get more specific error related information via
     * subsequent calls to {@link #log(CCharPointer, UnsignedWord)}. This is requested by returning
     * {@code true}. Returning {@code false} on the other hand will let the VM know that it can skip
     * providing this information and immediately proceed with calling {@link #fatalError()} from
     * where it is expected to never return to the VM.
     * <p>
     * Providing this method allows to implement flood control for fatal errors. The implementor can
     * rely on {@link #fatalError()} getting called soon after this method is called.
     *
     * @param context provides a CCharPointer string that describes the error
     * @param length provides the length of the error string
     *
     * @return if {@code false} is returned the VM will skip providing more specific error related
     *         information before calling {@link #fatalError()}.
     *
     * @since 20.3
     */
    default boolean fatalContext(CCharPointer context, UnsignedWord length) {
        return true;
    }

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
