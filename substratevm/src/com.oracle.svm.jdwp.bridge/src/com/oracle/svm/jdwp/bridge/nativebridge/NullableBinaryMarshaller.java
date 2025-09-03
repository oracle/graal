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

final class NullableBinaryMarshaller<T> implements BinaryMarshaller<T> {

    private static final byte NULL = 0;
    private static final byte NON_NULL = 1;

    private final BinaryMarshaller<T> delegate;

    NullableBinaryMarshaller(BinaryMarshaller<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T read(BinaryInput input) {
        byte nullStatus = input.readByte();
        switch (nullStatus) {
            case NULL:
                return null;
            case NON_NULL:
                return delegate.read(input);
            default:
                throw new IllegalArgumentException("Unexpected input " + nullStatus);
        }
    }

    @Override
    public void write(BinaryOutput output, T object) {
        if (object != null) {
            output.writeByte(NON_NULL);
            delegate.write(output, object);
        } else {
            output.writeByte(NULL);
        }
    }

    @Override
    public void readUpdate(BinaryInput input, T object) {
        byte nullStatus = input.readByte();
        switch (nullStatus) {
            case NULL:
                assert object == null;
                break;
            case NON_NULL:
                assert object != null;
                delegate.readUpdate(input, object);
                break;
            default:
                throw new IllegalArgumentException("Unexpected input " + nullStatus);
        }
    }

    @Override
    public void writeUpdate(BinaryOutput output, T object) {
        if (object != null) {
            output.writeByte(NON_NULL);
            delegate.writeUpdate(output, object);
        } else {
            output.writeByte(NULL);
        }
    }

    @Override
    public int inferSize(T object) {
        if (object != null) {
            return 1 + delegate.inferSize(object);
        } else {
            return 1;
        }
    }

    @Override
    public int inferUpdateSize(T object) {
        if (object != null) {
            return 1 + delegate.inferUpdateSize(object);
        } else {
            return 1;
        }
    }
}
