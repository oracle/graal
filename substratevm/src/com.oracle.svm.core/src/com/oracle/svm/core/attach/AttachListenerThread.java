/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.attach;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.dcmd.DCmd;
import com.oracle.svm.core.dcmd.DCmdSupport;
import com.oracle.svm.core.jni.headers.JNIErrors;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.StringUtil;

import jdk.graal.compiler.options.Option;

/**
 * A dedicated listener thread that accepts client connections and that handles diagnostic command
 * requests. At the moment, only jcmd is supported.
 */
public abstract class AttachListenerThread extends Thread {
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.hpp#L176-L178")//
    protected static final int ATTACH_ERROR_BAD_VERSION = 101;

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.hpp#L171")//
    protected static final int NAME_LENGTH_MAX_V1 = 16;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.hpp#L172")//
    protected static final int ARG_LENGTH_MAX_V1 = 1024;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.hpp#L173")//
    protected static final int ARG_COUNT_MAX_V1 = 3;

    private static final String JCMD_COMMAND_STRING = "jcmd";

    @SuppressWarnings("this-escape")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.cpp#L668-L682")
    public AttachListenerThread() {
        super(PlatformThreads.singleton().systemGroup, "Attach Listener");
        this.setDaemon(true);
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.cpp#L586-L651")
    public void run() {
        try {
            while (true) {
                AttachOperation op = dequeue();
                if (op == null) {
                    /* Dequeue failed or shutdown. */
                    AttachApiSupport.singleton().shutdown(false);
                    return;
                }

                if (JCMD_COMMAND_STRING.equals(op.name)) {
                    handleJcmd(op);
                } else {
                    op.complete(JNIErrors.JNI_ERR(), "Invalid Operation. Only jcmd is supported currently.");
                }
            }
        } catch (Throwable e) {
            VMError.shouldNotReachHere("Exception in attach listener thread", e);
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.cpp#L364-L410")
    private static void handleJcmd(AttachOperation op) {
        try {
            /* jcmd only uses the first argument. */
            String response = parseAndExecute(op.getArguments().getFirst());
            op.complete(JNIErrors.JNI_OK(), response);
        } catch (Throwable e) {
            handleException(op, e);
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/diagnosticFramework.cpp#L382-L418")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/diagnosticFramework.cpp#L429-L446")
    private static String parseAndExecute(String input) throws Throwable {
        String[] args = StringUtil.split(input, " ");
        String cmdName = args[0];

        /* Redirect to the help command if there is a corresponding argument in the input. */
        for (int i = 1; i < args.length; i++) {
            String v = args[i];
            if ("-h".equals(v) || "--help".equals(v) || "-help".equals(v)) {
                DCmd cmd = DCmdSupport.singleton().getCommand("help");
                return cmd.parseAndExecute("help " + cmdName);
            }
        }

        /* Pass the input to the diagnostic command. */
        DCmd cmd = DCmdSupport.singleton().getCommand(cmdName);
        if (cmd == null) {
            throw new IllegalArgumentException("Unknown diagnostic command '" + cmdName + "'");
        }
        return cmd.parseAndExecute(input);
    }

    private static void handleException(AttachOperation op, Throwable e) {
        if (!Options.JCmdExceptionStackTrace.getValue()) {
            op.complete(JNIErrors.JNI_ERR(), e.toString());
            return;
        }

        StringWriter s = new StringWriter();
        e.printStackTrace(new PrintWriter(s));

        /* jcmd swallows line breaks if JNI_ERR() is used, so use JNI_OK() instead. */
        op.complete(JNIErrors.JNI_OK(), s.toString());
    }

    protected abstract AttachOperation dequeue();

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.cpp#L845-L906")
    protected static AttachOperation readRequest(AttachSocketChannel channel) {
        int ver = readInt(channel);
        if (ver < 0) {
            return null;
        }

        int bufferSize;
        int minStrCount;
        int minReadSize = 1;
        switch (ver) {
            case Version.V1:
                /*
                 * Each request consists of a fixed number of zero-terminated UTF-8 strings:
                 * <version><commandName><arg0><arg1><arg2>
                 */
                bufferSize = (NAME_LENGTH_MAX_V1 + 1) + ARG_COUNT_MAX_V1 * (ARG_LENGTH_MAX_V1 + 1);
                minStrCount = 1 /* name */ + ARG_COUNT_MAX_V1;
                break;
            case Version.V2:
                /*
                 * Each request consists of a variable number of zero-terminated UTF-8 strings:
                 * <version><size><commandName>(<arg>)*
                 */
                channel.writeReply(ATTACH_ERROR_BAD_VERSION, "v2 is unsupported");
                return null;
            default:
                /* Failed to read request: unknown version. */
                channel.writeReply(ATTACH_ERROR_BAD_VERSION, "unknown version");
                return null;
        }

        AttachOperation op = readRequestData(channel, bufferSize, minStrCount, minReadSize);
        if (op != null && ver == Version.V1) {
            /*
             * The whole request does not exceed bufferSize, but for v1 also name/arguments should
             * not exceed their respective max. length.
             */
            if (op.getName().length() > NAME_LENGTH_MAX_V1) {
                /* Failed to read request: operation name is too long. */
                return null;
            }
            for (String arg : op.getArguments()) {
                if (arg.length() > ARG_LENGTH_MAX_V1) {
                    /* Failed to read request: operation argument is too long. */
                    return null;
                }
            }
        }
        return op;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.cpp#L735-L803")
    private static AttachOperation readRequestData(AttachSocketChannel channel, int bufferSize, int minStringCount, int minReadSize) {
        Pointer buffer = NativeMemory.malloc(bufferSize, NmtCategory.Serviceability);
        try {
            return readRequestData0(channel, buffer, bufferSize, minStringCount, minReadSize);
        } finally {
            NativeMemory.free(buffer);
        }
    }

    private static AttachOperation readRequestData0(AttachSocketChannel channel, Pointer buffer, int bufferSize, int minStringCount, int minReadSize) {
        int strCount = 0;
        int offset = 0;
        int left = bufferSize;

        /*
         * Read until all (expected) strings or expected bytes have been read, the buffer is full,
         * or EOF.
         */
        do {
            int n = channel.read(buffer.add(offset), left);
            if (n < 0) {
                return null;
            } else if (n == 0) {
                break;
            }

            if (minStringCount > 0) {
                /* Need to count arguments. */
                for (int i = 0; i < n; i++) {
                    if (buffer.readByte(offset + i) == 0) {
                        strCount++;
                    }
                }
            }
            offset += n;
            left -= n;
        } while (left > 0 && (offset < minReadSize || strCount < minStringCount));

        if (offset < minReadSize || strCount < minStringCount) {
            /* Unexpected EOF. */
            return null;
        } else if (buffer.readByte(offset - 1) != 0) {
            /* Request must end with '\0'. */
            return null;
        }

        /* Parse all strings. This part is very different from HotSpot. */
        ArrayList<String> values = decodeStrings(buffer, buffer.add(offset));
        return createAttachOperation(channel, values);
    }

    private static ArrayList<String> decodeStrings(Pointer dataStart, Pointer dataEnd) {
        ArrayList<String> result = new ArrayList<>(4);

        Pointer currentStart = dataStart;
        Pointer pos = dataStart;
        while (pos.belowThan(dataEnd)) {
            if (pos.readByte(0) == 0) {
                String s = decodeString(currentStart, pos);
                result.add(s);

                currentStart = pos.add(1);
            }

            pos = pos.add(1);
        }

        return result;
    }

    private static String decodeString(Pointer start, Pointer end) {
        assert end.aboveOrEqual(start);
        UnsignedWord length = end.subtract(start);
        return CTypeConversion.toJavaString((CCharPointer) start, length, StandardCharsets.UTF_8);
    }

    private static AttachOperation createAttachOperation(AttachSocketChannel channel, ArrayList<String> values) {
        /* Parse the name and the options. */
        String name;
        String nameAndOptions = values.getFirst();

        int optionStart = nameAndOptions.indexOf(' ');
        if (optionStart != -1) {
            name = nameAndOptions.substring(0, optionStart);
            parseOptions();
        } else {
            name = nameAndOptions;
        }

        List<String> args = values.subList(1, values.size());
        return new AttachOperation(channel, name, args);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.cpp#L805-L820")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.cpp#L822-L843")
    private static void parseOptions() {
        /* "streaming" is the only option at the moment, and we don't support it. */
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.cpp#L704-L733")
    private static int readInt(AttachSocketChannel channel) {
        int maxValue = Integer.MAX_VALUE / 20;
        CCharPointer chPtr = StackValue.get(CCharPointer.class);
        int value = 0;
        while (true) {
            int n = channel.read(chPtr, 1);
            if (n != 1) {
                return -1;
            }
            byte ch = chPtr.read();
            if (ch == 0) {
                return value;
            }
            if (ch < '0' || ch > '9') {
                return -1;
            }
            /* Ensure there is no integer overflow. */
            if (value >= maxValue) {
                return -1;
            }
            value = value * 10 + (ch - '0');
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.hpp#L71-L74")
    private interface Version {
        int V1 = 1;
        int V2 = 2;
    }

    public abstract static class AttachSocketChannel {
        public abstract int read(PointerBase buffer, int size);

        /**
         * This method should only be called directly if we don't have an {@link AttachOperation}
         * yet.
         */
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.cpp#L921-L934")
        public void writeReply(int result, String message) {
            /* Send the return code. */
            byte[] lineBreak = System.lineSeparator().getBytes(StandardCharsets.UTF_8);
            byte[] returnCodeData = Integer.toString(result).getBytes(StandardCharsets.UTF_8);
            write(returnCodeData);
            write(lineBreak);

            /* Send the actual response message. */
            if (message != null && !message.isEmpty()) {
                byte[] responseBytes = message.getBytes(StandardCharsets.UTF_8);
                write(responseBytes);
                write(lineBreak);
            }
        }

        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.cpp#L908-L919")
        protected abstract void write(byte[] data);

        public abstract void close();
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/share/services/attachListener.hpp#L167-L304")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/os/posix/attachListener_posix.cpp#L141-L158")
    public static class AttachOperation {
        private final AttachSocketChannel channel;
        private final String name;
        private final List<String> args;

        public AttachOperation(AttachSocketChannel channel, String name, List<String> args) {
            this.channel = channel;
            this.name = name;
            this.args = args;
        }

        public String getName() {
            return name;
        }

        public List<String> getArguments() {
            return args;
        }

        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/os/posix/attachListener_posix.cpp#L323-L325")
        @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/os/posix/attachListener_posix.cpp#L107-L109")
        public void complete(int result, String message) {
            channel.writeReply(result, message);
            channel.close();
        }
    }

    static class Options {
        @Option(help = "Determines if stack traces are shown if exceptions occur in diagnostic commands that were triggered via jcmd.")//
        public static final RuntimeOptionKey<Boolean> JCmdExceptionStackTrace = new RuntimeOptionKey<>(false);
    }
}
