package com.oracle.svm.core.sampling;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.stack.ParameterizedStackFrameVisitor;

public class SamplingStackVisitor extends ParameterizedStackFrameVisitor<SamplingStackVisitor.StackTrace> {

    @Override
    protected boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame, SamplingStackVisitor.StackTrace data) {
        GraalError.guarantee(data.num < StackTrace.MAX_STACK_DEPTH,
                        "The call stack depth of the thread " + Thread.currentThread() + " exceeds the maximal set value.");
        data.data[data.num++] = ip.rawValue();
        return true;
    }

    @Override
    protected boolean unknownFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame, StackTrace data) {
        return false;
    }

    public static class StackTrace {

        static final int MAX_STACK_DEPTH = 2048;
        long[] data = new long[MAX_STACK_DEPTH];
        int num = 0;
    }
}
