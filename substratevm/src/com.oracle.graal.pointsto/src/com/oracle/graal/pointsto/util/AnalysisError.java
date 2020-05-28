/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.ResolvedJavaType;

@Platforms(Platform.HOSTED_ONLY.class)
public class AnalysisError extends Error {

    private static final long serialVersionUID = -4489048906003856416L;

    AnalysisError() {
        super();
    }

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

        private static String message(AnalysisMethod method) {
            String msg = String.format("Error encountered while parsing %s %n", method.format("%H.%n(%P)"));
            msg += parsingContext(method);
            return msg;
        }

    }

    public static class FieldNotPresentError extends AnalysisError {
        private static final long serialVersionUID = -7167507945764369928L;

        FieldNotPresentError(AnalysisMethod context, AnalysisField field, AnalysisType type) {
            super(message(context, field, type));
        }

        private static String message(AnalysisMethod context, AnalysisField field, AnalysisType type) {
            String msg = String.format("Field %s is not present on type %s. ", field.format("%H.%n"), type.toJavaName());
            if (context != null) {
                msg += String.format("Error encountered while analysing %s %n", context.format("%H.%n(%P)"));
                msg += parsingContext(context);
            }
            return msg;
        }
    }

    private static String parsingContext(AnalysisMethod method) {
        StringBuilder msg = new StringBuilder("Parsing context:");
        if (method.getTypeFlow().getParsingContext().length > 0) {
            for (StackTraceElement e : method.getTypeFlow().getParsingContext()) {
                msg.append(String.format("%n\tparsing %s", e));
            }
            msg.append(String.format("%n"));
        } else {
            msg.append(String.format(" <no parsing context available> %n"));
        }
        return msg.toString();
    }

    public static TypeNotFoundError typeNotFound(ResolvedJavaType type) {
        throw new TypeNotFoundError(type);
    }

    public static ParsingError parsingError(AnalysisMethod method, Throwable original) {
        throw new ParsingError(method, original);
    }

    public static FieldNotPresentError fieldNotPresentError(AnalysisMethod context, AnalysisField field, AnalysisType type) {
        throw new FieldNotPresentError(context, field, type);
    }

    public static RuntimeException shouldNotReachHere() {
        throw new AnalysisError("should not reach here");
    }

    public static RuntimeException shouldNotReachHere(String msg) {
        throw new AnalysisError("should not reach here: " + msg);
    }

    public static RuntimeException shouldNotReachHere(Throwable cause) {
        throw new AnalysisError(cause);
    }

    public static void guarantee(boolean condition) {
        if (!condition) {
            throw new AnalysisError("guarantee failed");
        }
    }

    public static void guarantee(boolean condition, String format, Object... args) {
        if (!condition) {
            // Checkstyle: stop
            throw new AnalysisError(String.format(format, args));
            // Checkstyle: resume
        }
    }
}
