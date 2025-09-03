/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge.nativebridge;

final class DefaultThrowableMarshaller implements BinaryMarshaller<Throwable> {

    private static final int THROWABLE_SIZE_ESTIMATE = 1024;
    private final DefaultStackTraceMarshaller stackTraceMarshaller = DefaultStackTraceMarshaller.INSTANCE;

    @Override
    public Throwable read(BinaryInput in) {
        String foreignExceptionClassName = in.readUTF();
        String foreignExceptionMessage = (String) in.readTypedValue();
        StackTraceElement[] foreignExceptionStack = stackTraceMarshaller.read(in);
        return new MarshalledException(foreignExceptionClassName, foreignExceptionMessage, ForeignException.mergeStackTrace(foreignExceptionStack));
    }

    @Override
    public void write(BinaryOutput out, Throwable object) {
        out.writeUTF(object instanceof MarshalledException ? ((MarshalledException) object).getForeignExceptionClassName() : object.getClass().getName());
        out.writeTypedValue(object.getMessage());
        stackTraceMarshaller.write(out, object.getStackTrace());
    }

    @Override
    public int inferSize(Throwable object) {
        // We don't use Throwable#getStackTrace as it allocates.
        return THROWABLE_SIZE_ESTIMATE;
    }
}
