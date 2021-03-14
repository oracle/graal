package com.oracle.svm.core.sampling;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

public class DumpSamplingData {

    private static final class CallFrame {
        final String name;
        final CallFrame tail;

        private CallFrame(String name, CallFrame tail) {
            this.name = name;
            this.tail = tail;
        }
    }

    public Runnable dumpSamplingProfilesToFile() {
        try {
            return DumpSamplingData::dumpSamplingData;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    public static void dumpSamplingData() {
        BufferedWriter profilesWriter = null;
        try {
            Path pathProfiles = Paths.get("sampling-java-stack.iprof").toAbsolutePath();
            profilesWriter = new BufferedWriter(new FileWriter(pathProfiles.toFile()));
            dumpFromTree(profilesWriter, DUMP_MODE.JAVA_STACK_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (profilesWriter != null) {
                try {
                    profilesWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        BufferedWriter flameWriter = null;
        try {
            Path path = Paths.get("sampling-compilation-stack.iprof").toAbsolutePath();
            flameWriter = new BufferedWriter(new FileWriter(path.toFile()));
            dumpFromTree(flameWriter, DUMP_MODE.COMPILATION_STACK_VIEW);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (flameWriter != null) {
                try {
                    flameWriter.close();
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

    static String decodeMethod(long address, DUMP_MODE mode) {
        CodePointer ip = WordFactory.pointer(address);
        CodeInfoQueryResult result = new AOTCodeInfoQueryResult(ip);
        CodeInfo codeInfo = codeInfo(ip);
        long relativeIP = CodeInfoAccess.relativeIP(codeInfo, ip);
        CodeInfoAccess.lookupCodeInfo(codeInfo, relativeIP, result);
        FrameInfoQueryResult frameInfo = result.getFrameInfo();
        return createDecodedMethodEntry(frameInfo, mode);
    }

    private static String createDecodedMethodEntry(FrameInfoQueryResult frameInfo, DUMP_MODE mode) {
        if (mode == DUMP_MODE.JAVA_STACK_VIEW) {
            List<String> frames = new ArrayList<>();
            frames.add(frameInfo.methodID + ":" + frameInfo.getBci());
            while (frameInfo.getCaller() != null) {
                frameInfo = frameInfo.getCaller();
                frames.add(frameInfo.methodID + ":" + frameInfo.getBci());
            }
            Collections.reverse(frames);
            return String.join(";", frames);
        } else {
            while (frameInfo.getCaller() != null) {
                frameInfo = frameInfo.getCaller();
            }
            int frameInfoMethodId = frameInfo.methodID;
            if (mode == DUMP_MODE.COMPILATION_STACK_VIEW) {
                return String.valueOf(frameInfoMethodId);
            } else {
                assert mode == DUMP_MODE.COMPILATION_WITH_BCI_STACK_VIEW;
                int bci = frameInfo.getBci();
                return frameInfoMethodId + ":" + bci;
            }
        }
    }

    static void dumpFromTree(BufferedWriter writer, DUMP_MODE mode) {
        PrefixTree prefixTree = ImageSingletons.lookup(ProfilingSampler.class).prefixTree();
        prefixTree.topDown(new CallFrame("<total>", null), (context, address) -> new CallFrame(decodeMethod(address, mode), context), (context, value) -> {
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

    enum DUMP_MODE {
        JAVA_STACK_VIEW,
        COMPILATION_STACK_VIEW,
        COMPILATION_WITH_BCI_STACK_VIEW;
    }
}
