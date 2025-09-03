/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.benchmark;

import org.graalvm.nativebridge.BinaryInput;
import org.graalvm.nativebridge.BinaryMarshaller;
import org.graalvm.nativebridge.BinaryOutput;
import org.graalvm.nativebridge.Isolate;
import org.graalvm.nativebridge.MarshallerConfig;
import org.graalvm.nativebridge.MarshallerConfig.Builder;
import org.graalvm.nativebridge.TypeLiteral;

import java.util.ArrayList;
import java.util.List;

final class BenchmarkMarshallerConfig {

    private static final MarshallerConfig INSTANCE = createMarshallerConfig();

    private BenchmarkMarshallerConfig() {
    }

    public static MarshallerConfig getInstance() {
        return INSTANCE;
    }

    private static MarshallerConfig createMarshallerConfig() {
        Builder builder = MarshallerConfig.newBuilder();
        builder.registerMarshaller(Point.class, BinaryMarshaller.nullable(new PointMarshaller()));
        builder.registerMarshaller(new TypeLiteral<List<Point>>() {
        }, BinaryMarshaller.nullable(new PointsMarshaller()));
        return builder.build();
    }

    private static final class PointMarshaller implements BinaryMarshaller<Point> {

        @Override
        public int inferSize(Point point) {
            return 3 * Integer.BYTES;
        }

        @Override
        public void write(BinaryOutput output, Point point) {
            output.writeInt(point.x());
            output.writeInt(point.y());
            output.writeInt(point.z());
        }

        @Override
        public Point read(Isolate<?> isolate, BinaryInput input) {
            int x = input.readInt();
            int y = input.readInt();
            int z = input.readInt();
            return new Point(x, y, z);
        }
    }

    private static final class PointsMarshaller implements BinaryMarshaller<List<Point>> {

        @Override
        public int inferSize(List<Point> points) {
            return Integer.BYTES + points.size() * 3 * Integer.BYTES;
        }

        @Override
        public void write(BinaryOutput output, List<Point> points) {
            output.writeInt(points.size());
            for (Point point : points) {
                output.writeInt(point.x());
                output.writeInt(point.y());
                output.writeInt(point.z());
            }
        }

        @Override
        public List<Point> read(Isolate<?> isolate, BinaryInput input) {
            int size = input.readInt();
            List<Point> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int x = input.readInt();
                int y = input.readInt();
                int z = input.readInt();
                result.add(new Point(x, y, z));
            }
            return result;
        }
    }
}
