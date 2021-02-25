package com.oracle.svm.core.sampling;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.stack.ParameterizedStackFrameVisitor;

public class SamplingStackVisitor extends ParameterizedStackFrameVisitor<SamplingStackVisitor.SamplingStackTrace> {

    @Override
    protected boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame, SamplingStackTrace data) {
        CodeInfoQueryResult result = new AOTCodeInfoQueryResult(ip);
        CodeInfoAccess.lookupCodeInfo(codeInfo, CodeInfoAccess.relativeIP(codeInfo, ip), result);
        data.frameAddress[data.frameNum] = CodeInfoAccess.getCodeStart(codeInfo).rawValue();
        data.frameBCI[data.frameNum++] = result.getFrameInfo().getBci();
        return true;
    }

    @Override
    protected boolean unknownFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame, SamplingStackTrace data) {
        return false;
    }

    public static class SamplingStackTrace {
        static final int MAX_FRAME_NUM = 40;
        long[] frameAddress = new long[MAX_FRAME_NUM];
        int[] frameBCI = new int[MAX_FRAME_NUM];
        int frameNum = 0;
    }
}

class AOTCodeInfoQueryResult extends CodeInfoQueryResult {

    AOTCodeInfoQueryResult(CodePointer ip) {
        super();
        this.ip = ip;
    }
}
