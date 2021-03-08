package com.oracle.svm.core.sampling;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.oracle.svm.core.code.FrameInfoQueryResult;
import org.graalvm.collections.PrefixTree;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.ProfilingSampler;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.UntetheredCodeInfo;

public class SamplingData {

    private static class CallFrame {
        final String name;
        final CallFrame tail;

        private CallFrame(String name, CallFrame tail) {
            this.name = name;
            this.tail = tail;
        }
    }

    public Runnable dumpToFile() {
        try {
            return SamplingData::dumpProfiles;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    public static void dumpProfiles() {
        BufferedWriter writer = null;
        try {
            Path path = Paths.get("sampling.iprof").toAbsolutePath();
            writer = new BufferedWriter(new FileWriter(path.toFile()));
            dumpFromTree(writer);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Uninterruptible(reason = "Called by uninterruptible code.")
    private static CodeInfo codeInfo(CodePointer ip) {
        UntetheredCodeInfo untetheredCodeInfo = CodeInfoTable.lookupCodeInfo(ip);
        assert untetheredCodeInfo.isNonNull();
        Object tether = CodeInfoAccess.acquireTether(untetheredCodeInfo);
        try {
            return CodeInfoAccess.convert(untetheredCodeInfo, tether);
        } finally {
            CodeInfoAccess.releaseTether(untetheredCodeInfo, tether);
        }
        // return CodeInfoAccess.convert(untetheredCodeInfo);
    }

    static String decodeMethod(long address, AOTSamplingData aotSamplingData) {
        CodePointer ip = WordFactory.pointer(address);
        CodeInfoQueryResult result = new AOTCodeInfoQueryResult(ip);
        CodeInfo codeInfo = codeInfo(ip);
        CodeInfoAccess.lookupCodeInfo(codeInfo, CodeInfoAccess.relativeIP(codeInfo, ip), result);
        FrameInfoQueryResult frameInfo = result.getFrameInfo();
        while (frameInfo.getCaller() != null) {
            frameInfo = frameInfo.getCaller();
        }
        int methodId = aotSamplingData.findMethod(address);
        int bci = frameInfo.getBci();
        return methodId + ":" + bci;
    }

    static void dumpFromTree(BufferedWriter writer) throws IOException {
        PrefixTree prefixTree = ImageSingletons.lookup(ProfilingSampler.class).prefixTree();
        AOTSamplingData aotSamplingData = ImageSingletons.lookup(AOTSamplingData.class);
        aotSamplingData.dump();

        prefixTree.topDown(new CallFrame("<total>", null), (context, address) -> new CallFrame(decodeMethod(address, aotSamplingData), context), (context, value) -> {
            try {
                StringBuilder contextChain = new StringBuilder(context.name);
                CallFrame elem = context.tail;
                if (value > 0) {
                    while (elem != null) {
                        contextChain.append(";").append(elem.name);
                        elem = elem.tail;
                    }
                    contextChain.append(" " + value);
                    String contextString = contextChain.toString();
                    System.out.println(contextString);
                    writer.write(contextString);
                    writer.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    static class AOTCodeInfoQueryResult extends CodeInfoQueryResult {

        AOTCodeInfoQueryResult(CodePointer ip) {
            super();
            this.ip = ip;
        }
    }
}
