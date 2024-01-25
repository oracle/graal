/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.util;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.ReportUtils;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaType;

@Platforms(Platform.HOSTED_ONLY.class)
@SuppressWarnings("serial")
public class AnalysisError extends Error {

    private static final long serialVersionUID = -4489048906003856416L;

    AnalysisError(String msg) {
        super(msg);
    }

    AnalysisError(Throwable ex) {
        super(ex);
    }

    AnalysisError(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * Thrown when the analysis is sealed if a type that was not discovered during the analysis is
     * requested.
     */
    public static class TypeNotFoundError extends AnalysisError {

        private static final long serialVersionUID = -7167507945764369928L;

        private final ResolvedJavaType type;

        TypeNotFoundError(ResolvedJavaType type) {
            super("Type not found during analysis: " + type);
            this.type = type;
        }

        public ResolvedJavaType getType() {
            return type;
        }
    }

    /**
     * Thrown when trying to snapshot new values after the shadow heap was sealed.
     */
    public static class SealedHeapError extends AnalysisError {

        private static final long serialVersionUID = 7057678828508165215L;

        SealedHeapError(String message) {
            super(message);
        }
    }

    /**
     * Thrown when the analysis parsing encounters an error.
     */
    public static class ParsingError extends AnalysisError {

        private static final long serialVersionUID = -7167507945764369928L;

        private final AnalysisMethod method;

        ParsingError(AnalysisMethod method, Throwable cause) {
            super(message(method), cause);
            this.method = method;
        }

        public AnalysisMethod getMethod() {
            return method;
        }

        public static String message(AnalysisMethod method) {
            String msg = String.format("Error encountered while parsing %s %n", method.asStackTraceElement(0));
            msg += "Parsing context:" + ReportUtils.parsingContext(method);
            return msg;
        }
    }

    /**
     * Thrown when the analysis is misused.
     */
    public static class UserError extends AnalysisError {

        private static final long serialVersionUID = -7167507945764369928L;

        UserError(String message) {
            super(message);
        }
    }

    public static class FieldNotPresentError extends AnalysisError {
        private static final long serialVersionUID = -7167507945764369928L;

        FieldNotPresentError(PointsToAnalysis bb, TypeFlow<?> objectFlow, BytecodePosition context, AnalysisField field, AnalysisType type) {
            super(message(bb, objectFlow, context, field, type));
        }

        private static String message(PointsToAnalysis bb, TypeFlow<?> objectFlow, BytecodePosition context, AnalysisField field, AnalysisType type) {
            String msg = String.format("Analysis is trying to access field %s on an object of type %s. ", field.format("%H.%n"), type.toJavaName());
            msg += String.format("This usually means that a type constraint is missing in the points-to graph. %n");
            if (objectFlow != null) {
                msg += ReportUtils.typePropagationTrace(bb, objectFlow, type);
            }
            if (context != null) {
                msg += String.format("The error was encountered while analyzing %s.%n", context.getMethod().format("%H.%n(%P)"));
                msg += "Parsing context:" + ReportUtils.parsingContext(context);
            }
            return msg;
        }
    }

    public static class InterruptAnalysis extends AnalysisError {
        private static final long serialVersionUID = 7126612141948542452L;

        InterruptAnalysis(String msg) {
            super(msg);
        }
    }

    public static TypeNotFoundError typeNotFound(ResolvedJavaType type) {
        throw new TypeNotFoundError(type);
    }

    public static SealedHeapError sealedHeapError(String message) {
        throw new SealedHeapError(message);
    }

    public static ParsingError parsingError(AnalysisMethod method, Throwable original) {
        throw new ParsingError(method, original);
    }

    public static UserError userError(String message) {
        throw new UserError(message);
    }

    public static FieldNotPresentError fieldNotPresentError(PointsToAnalysis bb, TypeFlow<?> objectFlow, BytecodePosition context, AnalysisField field, AnalysisType type) {
        throw new FieldNotPresentError(bb, objectFlow, context, field, type);
    }

    public static RuntimeException shouldNotReachHereUnexpectedInput(Object input) {
        throw new AnalysisError("Should not reach here: unexpected input: " + input);
    }

    public static RuntimeException shouldNotReachHere(String msg) {
        throw new AnalysisError("Should not reach here: " + msg);
    }

    public static RuntimeException shouldNotReachHere(Throwable cause) {
        throw new AnalysisError(cause);
    }

    public static RuntimeException shouldNotReachHere(String msg, Throwable cause) {
        throw new AnalysisError(msg, cause);
    }

    public static void guarantee(boolean condition) {
        if (!condition) {
            throw new AnalysisError("Guarantee failed");
        }
    }

    public static void guarantee(boolean condition, String format, Object... args) {
        if (!condition) {
            throw new AnalysisError(String.format(format, args));
        }
    }

    public static RuntimeException interruptAnalysis(String msg) {
        throw new InterruptAnalysis(msg);
    }
}
