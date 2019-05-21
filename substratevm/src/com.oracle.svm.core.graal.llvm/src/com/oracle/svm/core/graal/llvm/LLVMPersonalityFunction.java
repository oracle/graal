package com.oracle.svm.core.graal.llvm;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.stack.StackOverflowCheck;

public class LLVMPersonalityFunction {
    /* _Unwind_Reason_Code */
    private static final int URC_NO_REASON = 0;
    private static final int URC_FOREIGN_EXCEPTION_CAUGHT = 1;
    private static final int URC_FATAL_PHASE2_ERROR = 2;
    private static final int URC_FATAL_PHASE1_ERROR = 3;
    private static final int URC_NORMAL_STOP = 4;
    private static final int URC_END_OF_STACK = 5;
    private static final int URC_HANDLER_FOUND = 6;
    private static final int URC_INSTALL_CONTEXT = 7;
    private static final int URC_CONTINUE_UNWIND = 8;

    /* _Unwind_Action */
    private static final int UA_SEARCH_PHASE = 1;
    private static final int UA_CLEANUP_PHASE = 2;
    private static final int UA_HANDLER_FRAME = 4;
    private static final int UA_FORCE_UNWIND = 8;
    private static final int UA_END_OF_STACK = 16;

    @CEntryPoint
    public static int personality(int version, int action, IsolateThread thread, Pointer unwindException, Pointer context) {
        Log log = Log.noopLog();

        log.string("In personality").newline();
        Pointer ip = _Unwind_GetIP(context);
        Pointer functionStart = _Unwind_GetRegionStart(context);
        int pcOffset = NumUtil.safeToInt(ip.rawValue() - functionStart.rawValue());
        log.string("functionStart: ").hex(functionStart).string(", ip: ").hex(ip).string(", offset: ").hex(pcOffset).newline();

        Pointer lsda = _Unwind_GetLanguageSpecificData(context);
        log.string("lsda: ").hex(lsda).newline();
        Long handlerOffset = GCCExceptionTable.getHandlerOffset(lsda, pcOffset);

        if (handlerOffset == null || handlerOffset == 0) {
            log.string("Unwinding, ip = ").hex(ip).newline();
            return URC_CONTINUE_UNWIND;
        }

        if ((action & UA_SEARCH_PHASE) != 0) {
            log.string("Found handler, ip = ").hex(ip).newline();
            return URC_HANDLER_FOUND;
        } else if ((action & UA_CLEANUP_PHASE) != 0) {
            Throwable exception = SnippetRuntime.currentException.get();
            SnippetRuntime.currentException.set(null);

            int exceptionRegister = 0; //__builtin_eh_return_data_regno(0);
            int typeRegister = 1; //__builtin_eh_return_data_regno(1);

            _Unwind_SetGR(context, exceptionRegister, Word.objectToTrackedPointer(exception).rawValue());
            _Unwind_SetGR(context, typeRegister, 1);
            _Unwind_SetIP(context, functionStart.add(handlerOffset.intValue()));

            log.string("About to jump to handler, ip = ").hex(ip).string(", handler = ").hex(functionStart.add(handlerOffset.intValue())).newline();

            StackOverflowCheck.singleton().protectYellowZone();
            return URC_INSTALL_CONTEXT;
        } else {
            return URC_FATAL_PHASE1_ERROR;
        }
    }

    public static void raiseException() {
        Pointer exceptionStructure = StackValue.get(4 * Long.BYTES);
        exceptionStructure.writeObject(0, CurrentIsolate.getCurrentThread());
        _Unwind_RaiseException(exceptionStructure);
    }

    @CFunction
    public static native int _Unwind_RaiseException(Pointer exception);

    @CFunction
    public static native Pointer _Unwind_GetIP(Pointer context);

    @CFunction
    public static native Pointer _Unwind_SetIP(Pointer context, Pointer ip);

    @CFunction
    public static native Pointer _Unwind_SetGR(Pointer context, int reg, long value);

    @CFunction
    public static native Pointer _Unwind_GetRegionStart(Pointer context);

    @CFunction
    public static native Pointer _Unwind_GetLanguageSpecificData(Pointer context);

    @CFunction
    public static native int __builtin_eh_return_data_regno(int id);
}
