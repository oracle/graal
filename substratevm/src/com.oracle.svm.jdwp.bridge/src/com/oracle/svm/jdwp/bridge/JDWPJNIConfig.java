/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge;

import com.oracle.svm.jdwp.bridge.nativebridge.BinaryInput;
import com.oracle.svm.jdwp.bridge.nativebridge.BinaryMarshaller;
import com.oracle.svm.jdwp.bridge.nativebridge.BinaryOutput;
import com.oracle.svm.jdwp.bridge.nativebridge.ForeignException;
import com.oracle.svm.jdwp.bridge.nativebridge.JNIConfig;
import com.oracle.svm.jdwp.bridge.nativebridge.MarshalledException;

public class JDWPJNIConfig {

    private static final JNIConfig INSTANCE = createJNIConfig();

    public static JNIConfig getInstance() {
        return INSTANCE;
    }

    private static JNIConfig createJNIConfig() {
        JNIConfig.Builder builder = JNIConfig.newBuilder();
        builder.setAttachThreadAction(JDWPJNIConfig::attachCurrentThread);

        builder.registerMarshaller(Packet.class, new PacketMarshaller());
        EnumMarshaller<ErrorCode> errorCodeMarshaller = new EnumMarshaller<>(ErrorCode.class);
        CustomThrowableMarshaller throwableMarshaller = new CustomThrowableMarshaller(new JDWPExceptionMarshaller(errorCodeMarshaller), new DefaultThrowableMarshaller());
        builder.registerMarshaller(Throwable.class, throwableMarshaller);
        builder.registerMarshaller(StackFrame.class, new StackFrameMarshaller());

        return builder.build();
    }

    private static final class PacketMarshaller implements BinaryMarshaller<Packet> {
        @Override
        public void write(BinaryOutput output, Packet object) {
            byte[] bytes = object.toByteArray();
            output.writeInt(bytes.length);
            output.write(bytes, 0, bytes.length);
        }

        @Override
        public Packet read(BinaryInput input) {
            int length = input.readInt();
            byte[] bytes = new byte[length];
            input.read(bytes, 0, bytes.length);
            return UnmodifiablePacket.parseAndWrap(bytes);
        }
    }

    private static final class EnumMarshaller<E extends Enum<E>> implements BinaryMarshaller<E> {

        private final E[] values;

        EnumMarshaller(Class<E> enumClass) {
            values = enumClass.getEnumConstants();
            if (values.length > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("Only " + Byte.MAX_VALUE + " enum constants are supported.");
            }
        }

        @Override
        public E read(BinaryInput input) {
            return values[input.readByte()];
        }

        @Override
        public void write(BinaryOutput output, E object) {
            output.writeByte(object.ordinal());
        }

        @Override
        public int inferSize(E object) {
            return 1;
        }
    }

    static final class CustomThrowableMarshaller implements BinaryMarshaller<Throwable> {
        private final BinaryMarshaller<JDWPException> jdwpExceptionMarshaller;
        private final BinaryMarshaller<Throwable> fallbackThrowableMarshaller;

        private CustomThrowableMarshaller(BinaryMarshaller<JDWPException> jdwpExceptionMarshaller, BinaryMarshaller<Throwable> fallbackThrowableMarshaller) {
            this.jdwpExceptionMarshaller = jdwpExceptionMarshaller;
            this.fallbackThrowableMarshaller = fallbackThrowableMarshaller;
        }

        @Override
        public void write(BinaryOutput output, Throwable object) {
            boolean isJDWPException = object instanceof JDWPException;
            output.writeBoolean(isJDWPException);
            if (isJDWPException) {
                jdwpExceptionMarshaller.write(output, (JDWPException) object);
            } else {
                fallbackThrowableMarshaller.write(output, object);
            }
        }

        @Override
        public Throwable read(BinaryInput input) {
            boolean isJDWPException = input.readBoolean();
            if (isJDWPException) {
                return jdwpExceptionMarshaller.read(input);
            } else {
                return fallbackThrowableMarshaller.read(input);
            }
        }

        @Override
        public int inferSize(Throwable object) {
            if (object instanceof JDWPException jdwpException) {
                return jdwpExceptionMarshaller.inferSize(jdwpException) + 1;
            } else {
                return fallbackThrowableMarshaller.inferSize(object) + 1;
            }
        }
    }

    static final class DefaultThrowableMarshaller implements BinaryMarshaller<Throwable> {

        private static final int THROWABLE_SIZE_ESTIMATE = 1024;
        private final BinaryMarshaller<StackTraceElement[]> stackTraceMarshaller = JNIConfig.Builder.defaultStackTraceMarshaller();

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

    private static final class JDWPExceptionMarshaller implements BinaryMarshaller<JDWPException> {

        private final BinaryMarshaller<StackTraceElement[]> stackTraceMarshaller;
        private final BinaryMarshaller<ErrorCode> errorCodeMarshaller;

        private JDWPExceptionMarshaller(BinaryMarshaller<ErrorCode> errorCodeMarshaller) {
            this.errorCodeMarshaller = errorCodeMarshaller;
            this.stackTraceMarshaller = JNIConfig.Builder.defaultStackTraceMarshaller();
        }

        @Override
        public void write(BinaryOutput output, JDWPException object) {
            errorCodeMarshaller.write(output, object.getError());
            stackTraceMarshaller.write(output, object.getStackTrace());
        }

        @Override
        public JDWPException read(BinaryInput input) {
            ErrorCode error = errorCodeMarshaller.read(input);
            StackTraceElement[] stackTraceElements = stackTraceMarshaller.read(input);
            JDWPException jdwpException = new JDWPException(error);
            jdwpException.setStackTrace(stackTraceElements);
            return jdwpException;
        }

        @Override
        public int inferSize(JDWPException object) {
            return errorCodeMarshaller.inferSize(object.getError()) + stackTraceMarshaller.inferSize(object.getStackTrace());
        }
    }

    private static final class StackFrameMarshaller implements BinaryMarshaller<StackFrame> {

        private static final int SIZE_OF_STACK_FRAME = Byte.BYTES + Long.BYTES + Long.BYTES + Integer.BYTES + Integer.BYTES;

        @Override
        public void write(BinaryOutput output, StackFrame frame) {
            output.writeByte(frame.typeTag());
            output.writeLong(frame.classId());
            output.writeLong(frame.methodId());
            output.writeInt(frame.bci());
            output.writeInt(frame.frameDepth());
        }

        @Override
        public StackFrame read(BinaryInput input) {
            byte typeTag = input.readByte();
            long classId = input.readLong();
            long methodId = input.readLong();
            int bci = input.readInt();
            int frameDepth = input.readInt();
            return new StackFrame(typeTag, classId, methodId, bci, frameDepth);
        }

        @Override
        public int inferSize(StackFrame frame) {
            return SIZE_OF_STACK_FRAME;
        }
    }

    public static native long attachCurrentThread(long isolate);
}
