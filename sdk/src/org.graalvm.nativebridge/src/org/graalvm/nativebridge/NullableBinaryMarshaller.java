/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

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
