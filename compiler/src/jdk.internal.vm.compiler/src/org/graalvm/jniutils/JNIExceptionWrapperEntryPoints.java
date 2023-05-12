/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
    static Throwable createException(String message) {
        return new RuntimeException(message);
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
