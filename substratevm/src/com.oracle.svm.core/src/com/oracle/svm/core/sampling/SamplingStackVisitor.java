package com.oracle.svm.core.sampling;

import org.graalvm.collections.PrefixTree;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.stack.ParameterizedStackFrameVisitor;

public class SamplingStackVisitor extends ParameterizedStackFrameVisitor<SamplingStackVisitor.SamplingStackTrace> {

    @Override
    protected boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame, SamplingStackVisitor.SamplingStackTrace data) {
        data.node = data.node.at(ip.rawValue());
        return true;
    }

    @Override
    protected boolean unknownFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame, SamplingStackTrace data) {
        return false;
    }

    public static class SamplingStackTrace {
        PrefixTree.Node node;

        SamplingStackTrace(PrefixTree.Node node) {
            this.node = node;
        }
    }
}
