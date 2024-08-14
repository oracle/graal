/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.deopt;

import java.util.Objects;

import com.oracle.svm.core.SubstrateOptions;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.DeoptimizationSourcePositionDecoder;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

public class DeoptimizationRuntime {

    public static final SubstrateForeignCallDescriptor DEOPTIMIZE = SnippetRuntime.findForeignCall(DeoptimizationRuntime.class, "deoptimize", true, LocationIdentity.any());

    /** Foreign call: {@link #DEOPTIMIZE}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    @NeverInline("Access of caller frame")
    private static void deoptimize(long actionAndReason, SpeculationReason speculation) {
        /*
         * In cases where we deoptimize because of a StackOverflowError, we do not immediately want
         * to create and throw another StackOverflowError. Therefore, we enable the yellow zone. The
         * actual deoptimization operation is a VMOperation and would enable the yellow zone anyway.
         */
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {

            Pointer sp = KnownIntrinsics.readCallerStackPointer();
            DeoptimizationAction action = Deoptimizer.decodeDeoptAction(actionAndReason);

            if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                CodePointer ip = KnownIntrinsics.readReturnAddress();
                traceDeoptimization(actionAndReason, speculation, action, sp, ip);
            }

            if (action.doesInvalidateCompilation()) {
                Deoptimizer.invalidateMethodOfFrame(sp, speculation);
            } else {
                Deoptimizer.deoptimizeFrame(sp, false, speculation);
            }

            if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                Log.log().string("]").newline();
            }

        } catch (Throwable t) {
            /*
             * If an error was thrown during this deoptimization stage we likely will be in an
             * inconsistent state from which execution cannot proceed.
             */
            throw VMError.shouldNotReachHere(t);
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    public static void traceDeoptimization(long actionAndReason, SpeculationReason speculation, DeoptimizationAction action, Pointer sp, CodePointer ip) {
        Log log = Log.log().string("[Deoptimization initiated").newline();

        SubstrateInstalledCode installedCode = CodeInfoTable.lookupInstalledCode(ip);
        if (installedCode != null) {
            log.string("    name: ").string(installedCode.getName()).newline();
        }
        log.string("    sp: ").hex(sp).string("  ip: ").hex(ip).newline();

        DeoptimizationReason reason = Deoptimizer.decodeDeoptReason(actionAndReason);
        log.string("    reason: ").string(reason.toString()).string("  action: ").string(action.toString()).newline();

        int debugId = Deoptimizer.decodeDebugId(actionAndReason);
        log.string("    debugId: ").signed(debugId).string("  speculation: ").string(Objects.toString(speculation)).newline();

        NodeSourcePosition sourcePosition = DeoptimizationSourcePositionDecoder.decode(debugId, ip);
        if (sourcePosition == null || !SubstrateOptions.IncludeNodeSourcePositions.getValue()) {
            log.string("    To see the stack trace that triggered deoptimization, build the native image with -H:+IncludeNodeSourcePositions and run with --engine.NodeSourcePositions");
            log.newline();
        } else {
            log.string("    stack trace that triggered deoptimization:").newline();
            NodeSourcePosition cur = sourcePosition;
            while (cur != null) {
                log.string("        at ");
                if (cur.getMethod() != null) {
                    StackTraceElement element = cur.getMethod().asStackTraceElement(cur.getBCI());
                    if (element.getFileName() != null && element.getLineNumber() >= 0) {
                        log.string(element.toString());
                    } else {
                        log.string(cur.getMethod().format("%H.%n(%p)")).string(" bci ").signed(cur.getBCI());
                    }
                } else {
                    log.string("[unknown method]");
                }
                log.newline();

                cur = cur.getCaller();
            }
        }
    }
}
