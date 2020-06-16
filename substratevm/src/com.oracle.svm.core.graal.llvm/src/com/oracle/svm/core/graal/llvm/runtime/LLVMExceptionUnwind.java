/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.runtime;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.function.BooleanSupplier;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.graal.llvm.util.LLVMDirectives;
import com.oracle.svm.core.snippets.ExceptionUnwind;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@CContext(LLVMDirectives.class)
public class LLVMExceptionUnwind {

    /*
     * Exception handling using libunwind happens using the following steps:
     *
     * 1. The exception is raised using _Unwind_RaiseException
     *
     * 2. libunwind walks the stack twice, and for each Java call frame calls the personality
     * function below
     *
     * 3. During the first stack walk (UA_SEARCH_PHASE), the personality function tells whether it
     * has a registered handler able to handle the exception (URC_HANDLER_FOUND).
     *
     * 4. During the second stack walk (UA_CLEANUP_PHASE), the frame that accepted the exception
     * prepares the context to jump to the handler (URC_INSTALL_CONTEXT).
     *
     * In order for the personality function to function normally, it needs the context from the
     * thread which threw the exception to be restored when the function is called. This is done
     * through the thread argument which is passed to libunwind in place of the exception object
     * (see raiseException()). Libunwind then passes the value back as the third argument to the
     * personality function. The actual exception is then retrieved from the thread-local variable
     * SnippetRuntime.currentException.
     *
     * When preparing to jump to the handler, the exception object is placed in the return register,
     * from which it will get extracted after the landingpad instruction (see
     * NodeLLVMBuilder.emitReadExceptionObject).
     */
    @CEntryPoint
    @CEntryPointOptions(include = IncludeForLLVMOnly.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    @SuppressWarnings("unused")
    public static int personality(int version, int action, IsolateThread thread, _Unwind_Exception unwindException, _Unwind_Context context) {
        Pointer ip = getIP(context);
        Pointer functionStart = getRegionStart(context);
        int pcOffset = NumUtil.safeToInt(ip.rawValue() - functionStart.rawValue());

        Pointer lsda = getLanguageSpecificData(context);
        Long handlerOffset = GCCExceptionTable.getHandlerOffset(lsda, pcOffset);

        if (handlerOffset == null || handlerOffset == 0) {
            return _URC_CONTINUE_UNWIND();
        }

        if ((action & _UA_SEARCH_PHASE()) != 0) {
            return _URC_HANDLER_FOUND();
        } else if ((action & _UA_CLEANUP_PHASE()) != 0) {
            setIP(context, functionStart.add(handlerOffset.intValue()));

            ThreadingSupportImpl.resumeRecurringCallbackAtNextSafepoint();
            StackOverflowCheck.singleton().protectYellowZone();
            return _URC_INSTALL_CONTEXT();
        } else {
            return _URC_FATAL_PHASE1_ERROR();
        }
    }

    @Uninterruptible(reason = "Called before Java state is restored")
    public static Throwable retrieveException() {
        Throwable exception = ExceptionUnwind.currentException.get();
        ExceptionUnwind.currentException.set(null);
        return exception;
    }

    private static class IncludeForLLVMOnly implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.useLLVMBackend();
        }
    }

    public static ResolvedJavaMethod getPersonalityStub(MetaAccessProvider metaAccess) {
        try {
            return ((UniverseMetaAccess) metaAccess).getUniverse().lookup(CEntryPointCallStubSupport.singleton()
                            .getStubForMethod(LLVMExceptionUnwind.class.getMethod("personality", int.class, int.class, IsolateThread.class, _Unwind_Exception.class, _Unwind_Context.class)));
        } catch (NoSuchMethodException e) {
            throw shouldNotReachHere();
        }
    }

    public static ResolvedJavaMethod getRetrieveExceptionMethod(MetaAccessProvider metaAccess) {
        try {
            return metaAccess.lookupJavaMethod(LLVMExceptionUnwind.class.getMethod("retrieveException"));
        } catch (NoSuchMethodException e) {
            throw shouldNotReachHere();
        }
    }

    public static void raiseException() {
        _Unwind_Exception exceptionStructure = StackValue.get(_Unwind_Exception.class);
        exceptionStructure.set_exception_class(CurrentIsolate.getCurrentThread());
        exceptionStructure.set_exception_cleanup(WordFactory.nullPointer());
        raiseException(exceptionStructure);
    }

    // Allow methods with non-standard names: Checkstyle: stop

    /* _Unwind_Reason_Code */
    @CConstant
    private static native int _URC_NO_REASON();

    @CConstant
    private static native int _URC_FOREIGN_EXCEPTION_CAUGHT();

    @CConstant
    private static native int _URC_FATAL_PHASE2_ERROR();

    @CConstant
    private static native int _URC_FATAL_PHASE1_ERROR();

    @CConstant
    private static native int _URC_NORMAL_STOP();

    @CConstant
    private static native int _URC_END_OF_STACK();

    @CConstant
    private static native int _URC_HANDLER_FOUND();

    @CConstant
    private static native int _URC_INSTALL_CONTEXT();

    @CConstant
    private static native int _URC_CONTINUE_UNWIND();

    /* _Unwind_Action */
    @CConstant
    private static native int _UA_SEARCH_PHASE();

    @CConstant
    private static native int _UA_CLEANUP_PHASE();

    @CConstant
    private static native int _UA_HANDLER_FRAME();

    @CConstant
    private static native int _UA_FORCE_UNWIND();

    @CConstant
    private static native int _UA_END_OF_STACK();

    @CStruct(addStructKeyword = true)
    private interface _Unwind_Exception extends PointerBase {
        @CField
        PointerBase exception_class();

        @CField
        void set_exception_class(PointerBase value);

        @CField
        PointerBase exception_cleanup();

        @CField
        void set_exception_cleanup(PointerBase value);
    }

    @CStruct(addStructKeyword = true, isIncomplete = true)
    private interface _Unwind_Context extends PointerBase {
    }

    @CFunction(value = "_Unwind_RaiseException")
    public static native int raiseException(_Unwind_Exception exception);

    @CFunction(value = "_Unwind_GetIP")
    public static native Pointer getIP(_Unwind_Context context);

    @CFunction(value = "_Unwind_SetIP")
    public static native Pointer setIP(_Unwind_Context context, Pointer ip);

    @CFunction(value = "_Unwind_SetGR")
    public static native Pointer setGR(_Unwind_Context context, int reg, long value);

    @CFunction(value = "_Unwind_GetRegionStart")
    public static native Pointer getRegionStart(_Unwind_Context context);

    @CFunction(value = "_Unwind_GetLanguageSpecificData")
    public static native Pointer getLanguageSpecificData(_Unwind_Context context);

    @CFunction(value = "__builtin_eh_return_data_regno")
    public static native int builtinEHReturnDataRegno(int id);
}

// Checkstyle: resume
