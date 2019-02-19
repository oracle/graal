/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.graalvm.polyglot.io.FileSystem;

/**
 * A MIME type detector for finding file's MIME type and encoding.
 *
 * <p>
 * The means by which the detector determines the MIME is highly implementation specific. A simple
 * implementation might detect the MIME type using {@link TruffleFile file} extension. In other
 * cases, the content of {@link TruffleFile file} needs to be examined to guess its file type.
 *
 * @see TruffleFile#getMimeType()
 * @since 1.0
 */
public interface TruffleFileTypeDetector {

    /**
     * Finds a MIME type for given {@link TruffleFile}.
     *
     * @param file the {@link TruffleFile file} to find a MIME type for
     * @return the MIME type or {@code null} if the MIME type is not recognized
     * @throws IOException of an I/O error occurs
     * @throws SecurityException if the implementation requires an access the file and the
     *             {@link FileSystem} denies the operation
     * @since 1.0
     */
    String findMimeType(TruffleFile file) throws IOException;

    /**
     * For a file containing an encoding information returns the encoding.
     *
     * @param file the {@link TruffleFile file} to find an encoding for
     * @return the file encoding or {@code null} if the file does not provide encoding
     * @throws IOException of an I/O error occurs
     * @throws SecurityException if the {@link FileSystem} denies the file access
     * @since 1.0
     */
    String findEncoding(TruffleFile file) throws IOException;

    /**
     * The annotation to use to register {@link TruffleFileTypeDetector}. By annotating your
     * implementation of {@link TruffleFileTypeDetector} by this annotation the detector will be
     * used by {@link TruffleFile}.
     *
     * @since 1.0
     */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    @interface Registration {
    }
}
