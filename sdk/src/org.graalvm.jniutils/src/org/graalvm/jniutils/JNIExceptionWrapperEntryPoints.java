/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.jniutils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Entry points in HotSpot for exception handling from a JNI native method.
 */
final class JNIExceptionWrapperEntryPoints {

    /**
     * Updates an exception stack trace by decoding a stack trace from a JNI native method.
     *
     * @param target the {@link Throwable} to update
     * @param serializedStackTrace byte serialized stack trace
     * @return the updated {@link Throwable}
     */
    @JNIEntryPoint
    static Throwable updateStackTrace(Throwable target, byte[] serializedStackTrace) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(serializedStackTrace))) {
            int len = in.readInt();
            StackTraceElement[] elements = new StackTraceElement[len];
            for (int i = 0; i < len; i++) {
                String className = in.readUTF();
                String methodName = in.readUTF();
                String fileName = in.readUTF();
                int lineNumber = in.readInt();
                elements[i] = new StackTraceElement(className, methodName, fileName.isEmpty() ? null : fileName, lineNumber);
            }
            target.setStackTrace(elements);
            return target;

        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Creates an exception used to throw native exception into Java code.
     *
     * @param message the exception message
     * @return exception
     */
    @JNIEntryPoint
    static Throwable createException(String message, Throwable cause) {
        if (cause != null) {
            return new RuntimeException(message, cause);
        } else {
            // Keep cause uninitialized.
            return new RuntimeException(message);
        }
    }

    @JNIEntryPoint
    static byte[] getStackTrace(Throwable throwable) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bout)) {
            StackTraceElement[] stackTraceElements = throwable.getStackTrace();
            out.writeInt(stackTraceElements.length);
            for (StackTraceElement stackTraceElement : stackTraceElements) {
                out.writeUTF(stackTraceElement.getClassName());
                out.writeUTF(stackTraceElement.getMethodName());
                String fileName = stackTraceElement.getFileName();
                out.writeUTF(fileName == null ? "" : fileName);
                out.writeInt(stackTraceElement.getLineNumber());
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        return bout.toByteArray();
    }

    @JNIEntryPoint
    static String getThrowableMessage(Throwable t) {
        return t.getMessage();
    }

    @JNIEntryPoint
    static String getClassName(Class<?> clz) {
        return clz.getName();
    }
}
