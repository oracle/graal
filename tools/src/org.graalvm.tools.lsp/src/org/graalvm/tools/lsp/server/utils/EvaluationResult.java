/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.utils;

public final class EvaluationResult {
    private final Object result;
    private final boolean error;
    private final boolean evaluationDone;
    private final boolean unknownEcutionTarget;

    private EvaluationResult(Object result, boolean error, boolean evaluationDone, boolean unknownEcutionTarget) {
        this.result = result;
        this.error = error;
        this.evaluationDone = evaluationDone;
        this.unknownEcutionTarget = unknownEcutionTarget;
    }

    public Object getResult() {
        return result;
    }

    public boolean isError() {
        return error;
    }

    public boolean isEvaluationDone() {
        return evaluationDone;
    }

    public boolean isUnknownEcutionTarget() {
        return unknownEcutionTarget;
    }

    public static EvaluationResult createError(Object e) {
        return new EvaluationResult(e, true, true, false);
    }

    public static EvaluationResult createResult(Object result) {
        return new EvaluationResult(result, false, true, false);
    }

    public static EvaluationResult createEvaluationSectionNotReached() {
        return new EvaluationResult(null, false, false, false);
    }

    public static EvaluationResult createUnknownExecutionTarget() {
        return new EvaluationResult(null, false, false, true);
    }
}
