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
package com.oracle.svm.interpreter;

import com.oracle.svm.core.log.Log;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.metadata.MetadataUtil;

public class InterpreterUtil {

    /**
     * Alternative to {@link VMError#guarantee(boolean, String, Object)} that avoids
     * {@link String#format(String, Object...)} .
     */
    public static void guarantee(boolean condition, String simpleFormat, Object arg1) {
        if (!condition) {
            VMError.guarantee(condition, MetadataUtil.fmt(simpleFormat, arg1));
        }
    }

    /**
     * Build time logging.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void log(String msg) {
        if (InterpreterOptions.InterpreterBuildTimeLogging.getValue()) {
            System.out.println(msg);
        }
    }

    /**
     * Build time logging.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void log(String simpleFormat, Object arg1) {
        if (InterpreterOptions.InterpreterBuildTimeLogging.getValue()) {
            System.out.println(MetadataUtil.fmt(simpleFormat, arg1));
        }
    }

    /**
     * Build time logging.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void log(String simpleFormat, Object arg1, Object arg2) {
        if (InterpreterOptions.InterpreterBuildTimeLogging.getValue()) {
            System.out.println(MetadataUtil.fmt(simpleFormat, arg1, arg2));
        }
    }

    /**
     * Build time logging.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void log(String simpleFormat, Object arg1, Object arg2, Object arg3) {
        if (InterpreterOptions.InterpreterBuildTimeLogging.getValue()) {
            System.out.println(MetadataUtil.fmt(simpleFormat, arg1, arg2, arg3));
        }
    }

    /**
     * Build time logging.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void log(Throwable t) {
        if (InterpreterOptions.InterpreterBuildTimeLogging.getValue()) {
            t.printStackTrace(System.out);
        }
    }

    public static Log traceInterpreter(String msg) {
        if (InterpreterOptions.InterpreterTraceSupport.getValue()) {
            if (InterpreterOptions.InterpreterTrace.getValue()) {
                return Log.log().string(msg);
            }
        }
        return Log.noopLog();
    }
}
