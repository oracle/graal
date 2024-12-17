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

import com.oracle.svm.core.dcmd.DCmd;
import com.oracle.svm.core.dcmd.DCmdSupport;
import com.oracle.svm.core.jni.headers.JNIErrors;
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
    private static final String JCMD_COMMAND_STRING = "jcmd";
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/attachListener.hpp#L142") //
    protected static final int NAME_LENGTH_MAX = 16;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/attachListener.hpp#L143") //
    protected static final int ARG_LENGTH_MAX = 1024;
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/attachListener.hpp#L144") //
    protected static final int ARG_COUNT_MAX = 3;

    @SuppressWarnings("this-escape")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/attachListener.cpp#L453-L467")
    public AttachListenerThread() {
        super(PlatformThreads.singleton().systemGroup, "Attach Listener");
        this.setDaemon(true);
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/attachListener.cpp#L377-L436")
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

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/attachListener.cpp#L205-L217")
    private static void handleJcmd(AttachOperation op) {
        try {
            /* jcmd only uses the first argument. */
            String response = parseAndExecute(op.arg0);
            op.complete(JNIErrors.JNI_OK(), response);
        } catch (Throwable e) {
            handleException(op, e);
        }
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/diagnosticFramework.cpp#L383-L420")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+24/src/hotspot/share/services/diagnosticFramework.cpp#L422-L439")
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

    public abstract static class AttachOperation {
        private final String name;
        private final String arg0;
        @SuppressWarnings("unused") private final String arg1;
        @SuppressWarnings("unused") private final String arg2;

        public AttachOperation(String name, String arg0, String arg1, String arg2) {
            this.name = name;
            this.arg0 = arg0;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        public abstract void complete(int code, String response);
    }

    static class Options {
        @Option(help = "Determines if stack traces are shown if exceptions occur in diagnostic commands that were triggered via jcmd.")//
        public static final RuntimeOptionKey<Boolean> JCmdExceptionStackTrace = new RuntimeOptionKey<>(false);
    }
}
