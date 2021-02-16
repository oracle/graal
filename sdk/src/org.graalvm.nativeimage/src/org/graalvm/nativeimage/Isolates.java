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

import java.util.Objects;

import org.graalvm.nativeimage.impl.IsolateSupport;
import org.graalvm.word.UnsignedWord;

/**
 * Support for the creation, access to, and tear-down of isolates.
 *
 * @since 19.0
 */
public final class Isolates {
    private Isolates() {
    }

    /**
     * An exception thrown in the context of managing isolates.
     *
     * @since 19.0
     */
    public static final class IsolateException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new exception with the specified detail message.
         *
         * @since 19.0
         */
        public IsolateException(String message) {
            super(message);
        }
    }

    /**
     * Parameters for the creation of an isolate.
     *
     * @see CreateIsolateParameters.Builder
     *
     * @since 19.0
     */
    public static final class CreateIsolateParameters {

        /**
         * Builder for a {@link CreateIsolateParameters} instance.
         *
         * @since 19.0
         */
        public static final class Builder {
            private UnsignedWord reservedAddressSpaceSize;
            private String auxiliaryImagePath;
            private UnsignedWord auxiliaryImageReservedSpaceSize;

            /**
             * Creates a new builder with default values.
             *
             * @since 19.0
             */
            public Builder() {
            }

            /**
             * Sets the size in bytes for the reserved virtual address space of the new isolate.
             *
             * @since 19.0
             */
            public Builder reservedAddressSpaceSize(UnsignedWord size) {
                this.reservedAddressSpaceSize = size;
                return this;
            }

            /**
             * Sets the file path to an auxiliary image which should be loaded in addition to the
             * main image, or {@code null} if no such image should be loaded.
             *
             * @since 20.1
             */
            public Builder auxiliaryImagePath(String filePath) {
                this.auxiliaryImagePath = filePath;
                return this;
            }

            /**
             * Sets the size in bytes of an address space to reserve for loading an auxiliary image
             * in addition to the main image, or 0 if no space should be reserved.
             *
             * @since 20.1
             */
            public Builder auxiliaryImageReservedSpaceSize(UnsignedWord size) {
                this.auxiliaryImageReservedSpaceSize = size;
                return this;
            }

            /**
             * Produces the final {@link CreateIsolateParameters} with the values set previously by
             * the builder methods.
             *
             * @since 19.0
             */
            public CreateIsolateParameters build() {
                return new CreateIsolateParameters(reservedAddressSpaceSize, auxiliaryImagePath, auxiliaryImageReservedSpaceSize);
            }
        }

        private static final CreateIsolateParameters DEFAULT = new Builder().build();

        /**
         * Returns a {@link CreateIsolateParameters} with all default values.
         *
         * @since 19.0
         */
        public static CreateIsolateParameters getDefault() {
            return DEFAULT;
        }

        private final UnsignedWord reservedAddressSpaceSize;
        private final String auxiliaryImagePath;
        private final UnsignedWord auxiliaryImageReservedSpaceSize;

        private CreateIsolateParameters(UnsignedWord reservedAddressSpaceSize, String auxiliaryImagePath, UnsignedWord auxiliaryImageReservedSpaceSize) {
            this.reservedAddressSpaceSize = reservedAddressSpaceSize;
            this.auxiliaryImagePath = auxiliaryImagePath;
            this.auxiliaryImageReservedSpaceSize = auxiliaryImageReservedSpaceSize;
        }

        /**
         * Returns the size in bytes for the reserved virtual address space of the new isolate.
         *
         * @since 19.0
         */
        public UnsignedWord getReservedAddressSpaceSize() {
            return reservedAddressSpaceSize;
        }

        /**
         * Returns the file path to an auxiliary image which should be loaded in addition to the
         * main image, or {@code null} if no such image should be loaded.
         *
         * @since 20.1
         */
        public String getAuxiliaryImagePath() {
            return auxiliaryImagePath;
        }

        /**
         * Returns the size in bytes of an address space to reserve for loading an auxiliary image
         * in addition to the main image, or 0 if no space should be reserved.
         *
         * @since 20.1
         */
        public UnsignedWord getAuxiliaryImageReservedSpaceSize() {
            return auxiliaryImageReservedSpaceSize;
        }
    }

    /**
     * Creates a new isolate with the passed {@linkplain CreateIsolateParameters parameters}. On
     * success, the current thread is attached to the created isolate, and a pointer to its
     * associated {@link IsolateThread} structure is returned.
     *
     * @param parameters Parameters for the creation of the isolate.
     * @return A pointer to the structure that represents the current thread in the new isolate.
     * @throws IsolateException on error.
     *
     * @since 19.0
     */
    public static IsolateThread createIsolate(CreateIsolateParameters parameters) throws IsolateException {
        Objects.requireNonNull(parameters);
        return ImageSingletons.lookup(IsolateSupport.class).createIsolate(parameters);
    }

    /**
     * Attaches the current thread to the passed isolate. If the thread has already been attached,
     * the call provides the thread's existing isolate thread structure.
     *
     * @param isolate The isolate to which to attach the current thread.
     * @return A pointer to the structure representing the newly attached isolate thread.
     * @throws IsolateException on error.
     *
     * @since 19.0
     */
    public static IsolateThread attachCurrentThread(Isolate isolate) throws IsolateException {
        return ImageSingletons.lookup(IsolateSupport.class).attachCurrentThread(isolate);
    }

    /**
     * Given an isolate to which the current thread is attached, returns the address of the thread's
     * associated isolate thread structure. If the current thread is not attached to the passed
     * isolate, returns {@code null}.
     *
     * @param isolate The isolate for which to retrieve the current thread's corresponding structure
     * @return A pointer to the current thread's structure in the specified isolate or {@code null}
     *         if the thread is not attached to that isolate.
     * @throws IsolateException on error.
     *
     * @since 19.0
     */
    public static IsolateThread getCurrentThread(Isolate isolate) throws IsolateException {
        return ImageSingletons.lookup(IsolateSupport.class).getCurrentThread(isolate);
    }

    /**
     * Given an isolate thread structure, determines to which isolate it belongs and returns the
     * address of the isolate structure. May return {@code null} if the specified isolate thread
     * structure is no longer valid.
     *
     * @param thread The isolate thread for which to retrieve the isolate.
     * @return A pointer to the isolate, or {@code null}.
     * @throws IsolateException on error.
     *
     * @since 19.0
     */
    public static Isolate getIsolate(IsolateThread thread) throws IsolateException {
        return ImageSingletons.lookup(IsolateSupport.class).getIsolate(thread);
    }

    /**
     * Detaches the passed isolate thread from its isolate and discards any state or context that is
     * associated with it. At the time of the call, no code may still be executing in the isolate
     * thread's context. The passed pointer is no longer valid after the method returns.
     *
     * @param thread The isolate thread to detach from its isolate.
     * @throws IsolateException on error.
     *
     * @since 19.0
     */
    public static void detachThread(IsolateThread thread) throws IsolateException {
        ImageSingletons.lookup(IsolateSupport.class).detachThread(thread);
    }

    /**
     * Tears down an isolate. Given an {@link IsolateThread} for the current thread which must be
     * attached to the isolate to be torn down, waits for any other attached threads to detach from
     * the isolate, then discards the isolate's objects, threads, and any other state or context
     * that is associated with it. The passed pointer is no longer valid after the method returns.
     *
     * @param thread {@link IsolateThread} of the current thread that is attached to the isolate
     *            which is to be torn down.
     * @throws IsolateException on error.
     *
     * @since 19.0
     */
    public static void tearDownIsolate(IsolateThread thread) throws IsolateException {
        ImageSingletons.lookup(IsolateSupport.class).tearDownIsolate(thread);
    }
}
