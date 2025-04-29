/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.debug;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.SystemInOutErrSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmTrapNode;
import com.oracle.svm.hosted.webimage.wasm.phases.StackPointerVerificationPhase;
import com.oracle.svm.webimage.functionintrinsics.JSCallNode;
import com.oracle.svm.webimage.wasmgc.annotation.WasmExport;

public class WasmDebug {
    public static final SubstrateForeignCallDescriptor LOG_OBJECT = SnippetRuntime.findForeignCall(WasmDebug.class, "logObject", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor LOG_ERROR = SnippetRuntime.findForeignCall(WasmDebug.class, "logError", HAS_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor NULL_METHOD_POINTER = SnippetRuntime.findForeignCall(WasmDebug.class, "nullMethodPointer", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor READ_STACK_POINTER = SnippetRuntime.findForeignCall(WasmDebug.class, "readStackPointer", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CHECK_STACK_POINTER = SnippetRuntime.findForeignCall(WasmDebug.class, "checkStackPointer", HAS_SIDE_EFFECT);

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{
                    LOG_OBJECT, LOG_ERROR, NULL_METHOD_POINTER, READ_STACK_POINTER, CHECK_STACK_POINTER,
    };

    /**
     * Returns the original value of {@link System#err}.
     * <p>
     * {@link System#err} may be changed by user code. Internal error handling code should use this
     * method to get a stream that always refers to the
     * {@link com.oracle.svm.webimage.print.WebImagePrintStream} for {@code stderr} and can't be
     * intercepted by user code.
     */
    public static PrintStream getErrorStream() {
        return ImageSingletons.lookup(SystemInOutErrSupport.class).err();
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static void logObject(Object o) {
        Log.log().object(o).newline();
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void logError(String err) {
        getErrorStream().println(err);
        WasmTrapNode.trap();
    }

    /**
     * Foreign call target to read the stack pointer.
     *
     * @see StackPointerVerificationPhase
     */
    @NeverInline("Force a non-floating stack pointer read")
    @NoStackVerification("Part of the verification instrumentation")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static long readStackPointer() {
        return KnownIntrinsics.readCallerStackPointer().rawValue();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void printStackTrace() {
        JSCallNode.call(JSCallNode.PRINT_STACK_TRACE);
    }

    /**
     * Foreign call to verify that the given stack pointer matches the current stack pointer.
     *
     * @see StackPointerVerificationPhase
     */
    @NeverInline("Accesses stack pointer of caller")
    @NoStackVerification("Part of the verification instrumentation")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static void checkStackPointer(long oldSP) {
        long newSP = KnownIntrinsics.readCallerStackPointer().rawValue();

        if (newSP != oldSP) {
            Log.log().string("Method's stack pointer has changed during execution, was: 0x").hex(oldSP).string(" is: 0x").hex(newSP).newline();
            throw VMError.shouldNotReachHere("Stack verification failed");
        }
    }

    /**
     * Method that lives at index 0 of the function table and is called whenever a
     * {@link MethodPointer} with value {@code 0} is invoked.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static void nullMethodPointer() {
        throw VMError.shouldNotReachHere("Program tried to call null method pointer.");
    }

    @WasmExport(value = "debug.dumpObject", comment = "Prints debug info about Java object")
    private static void dumpObject(Object o) {
        String header;
        List<String> extraInfo = new ArrayList<>();
        if (o == null) {
            header = "This object is null";
        } else {
            Class<?> clazz = o.getClass();
            header = clazz + ": ";

            try {
                extraInfo.add("toString: " + o);
            } catch (Exception t) {
                extraInfo.add("toString threw an exception");
            }
        }

        System.err.println(header);
        for (String s : extraInfo) {
            System.err.println("\t" + s);
        }

        if (o instanceof Throwable throwable) {
            throwable.printStackTrace();
        }
    }
}
