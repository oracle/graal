/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.log;

import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.headers.LibC;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.UnsignedWord;

/**
 * A {@link LogHandler} that can use provided function pointers for each operation. If a function
 * pointer is missing, it forwards the operation to the delegate set in the constructor.
 */
public class FunctionPointerLogHandler implements LogHandlerExtension {
    private static final CGlobalData<CCharPointer> LOG_OPTION = CGlobalDataFactory.createCString("_log");
    private static final CGlobalData<CCharPointer> FATAL_LOG_OPTION = CGlobalDataFactory.createCString("_fatal_log");
    private static final CGlobalData<CCharPointer> FLUSH_LOG_OPTION = CGlobalDataFactory.createCString("_flush_log");
    private static final CGlobalData<CCharPointer> FATAL_OPTION = CGlobalDataFactory.createCString("_fatal");

    private final LogHandler delegate;

    private LogFunctionPointer logFunctionPointer;
    private LogFunctionPointer fatalLogFunctionPointer;
    private VoidFunctionPointer flushFunctionPointer;
    private VoidFunctionPointer fatalErrorFunctionPointer;

    public FunctionPointerLogHandler(LogHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void log(CCharPointer bytes, UnsignedWord length) {
        if (logFunctionPointer.isNonNull()) {
            logFunctionPointer.invoke(bytes, length);
        } else if (delegate != null) {
            delegate.log(bytes, length);
        }
    }

    @Override
    public void flush() {
        if (flushFunctionPointer.isNonNull()) {
            flushFunctionPointer.invoke();
        } else if (delegate != null) {
            delegate.flush();
        }
    }

    @Override
    public Log enterFatalContext(CodePointer callerIP, String msg, Throwable ex) {
        if (delegate instanceof LogHandlerExtension) {
            return ((LogHandlerExtension) delegate).enterFatalContext(callerIP, msg, ex);
        }
        return fatalLog;
    }

    /**
     * Sends output to {@link FunctionPointerLogHandler#fatalLogFunctionPointer} if it is non-null.
     */
    class FatalLog extends RealLog {
        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
        protected Log rawBytes(CCharPointer bytes, UnsignedWord length) {
            if (fatalLogFunctionPointer.isNonNull()) {
                fatalLogFunctionPointer.invoke(bytes, length);
            } else {
                FunctionPointerLogHandler.this.log(bytes, length);
            }
            return this;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
        public Log flush() {
            if (fatalLogFunctionPointer.isNull()) {
                FunctionPointerLogHandler.this.flush();
            }
            return this;
        }
    }

    private final FatalLog fatalLog = new FatalLog();

    @Override
    public void fatalError() {
        if (fatalErrorFunctionPointer.isNonNull()) {
            fatalErrorFunctionPointer.invoke();
        } else if (delegate != null) {
            delegate.fatalError();
        }
    }

    public CFunctionPointer getFatalErrorFunctionPointer() {
        return fatalErrorFunctionPointer;
    }

    interface LogFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(CCharPointer bytes, UnsignedWord length);
    }

    interface VoidFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke();
    }

    interface FatalContextFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        boolean invoke(CodePointer callerIP, String msg, Throwable ex);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public static boolean isJniVMOption(CCharPointer optionString) {
        return LibC.strcmp(optionString, LOG_OPTION.get()) == 0 ||
                        LibC.strcmp(optionString, FATAL_LOG_OPTION.get()) == 0 ||
                        LibC.strcmp(optionString, FLUSH_LOG_OPTION.get()) == 0 ||
                        LibC.strcmp(optionString, FATAL_OPTION.get()) == 0;
    }

    /**
     * Parses a {@code JavaVMOption} passed to {@code JNI_CreateJavaVM}.
     *
     * @param optionString value of the {@code javaVMOption.optionString} field
     * @param extraInfo value of the {@code javaVMOption.extraInfo} field
     * @return {@code true} iff the option was consumed by this method
     */
    public static boolean parseJniVMOption(CCharPointer optionString, WordPointer extraInfo) {
        if (LibC.strcmp(optionString, LOG_OPTION.get()) == 0) {
            handler(optionString).logFunctionPointer = (LogFunctionPointer) extraInfo;
            return true;
        } else if (LibC.strcmp(optionString, FATAL_LOG_OPTION.get()) == 0) {
            handler(optionString).fatalLogFunctionPointer = (LogFunctionPointer) extraInfo;
            return true;
        } else if (LibC.strcmp(optionString, FLUSH_LOG_OPTION.get()) == 0) {
            handler(optionString).flushFunctionPointer = (VoidFunctionPointer) extraInfo;
            return true;
        } else if (LibC.strcmp(optionString, FATAL_OPTION.get()) == 0) {
            handler(optionString).fatalErrorFunctionPointer = (VoidFunctionPointer) extraInfo;
            return true;
        }
        return false;
    }

    private static FunctionPointerLogHandler handler(CCharPointer optionString) {
        LogHandler handler = ImageSingletons.lookup(LogHandler.class);
        if (handler == null || !(handler instanceof FunctionPointerLogHandler)) {
            String str = CTypeConversion.toJavaString(optionString);
            throw new IllegalArgumentException("The " + str + " option is not supported by JNI_CreateJavaVM");
        }
        return (FunctionPointerLogHandler) handler;
    }

    /**
     * Notifies that {@code JNI_CreateJavaVM} has finished parsing all {@code JavaVMOption}s.
     */
    public static void afterParsingJniVMOptions() {
        LogHandler handler = ImageSingletons.lookup(LogHandler.class);
        if (handler == null || !(handler instanceof FunctionPointerLogHandler)) {
            return;
        }

        FunctionPointerLogHandler fpHandler = (FunctionPointerLogHandler) handler;
        if (fpHandler.logFunctionPointer.isNonNull()) {
            if (fpHandler.flushFunctionPointer.isNull()) {
                throw new IllegalArgumentException("The _flush_log option cannot be null when _log is non-null");
            }
        } else if (fpHandler.flushFunctionPointer.isNonNull()) {
            throw new IllegalArgumentException("The _log option cannot be null when _flush_log is non-null");
        }
    }
}
