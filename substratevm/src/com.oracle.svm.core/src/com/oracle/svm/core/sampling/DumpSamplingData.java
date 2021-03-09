package com.oracle.svm.core.sampling;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
            return DumpSamplingData::dumpProfiles;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    public Runnable dumpCompilationUnitsToFile() {
        try {
            return DumpSamplingData::dumpCompilationUnits;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    public static void dumpProfiles() {
        BufferedWriter profilesWriter = null;
        try {
            Path pathProfiles = Paths.get("sampling-profiles.iprof").toAbsolutePath();
            profilesWriter = new BufferedWriter(new FileWriter(pathProfiles.toFile()));
            dumpFromTree(profilesWriter, true);
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
    }

    public static void dumpCompilationUnits() {
        BufferedWriter flameWriter = null;
        try {
            Path path = Paths.get("sampling-flame.iprof").toAbsolutePath();
            flameWriter = new BufferedWriter(new FileWriter(path.toFile()));
            dumpFromTree(flameWriter, false);
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

    static String decodeMethod(long address, SamplingMethodData samplingMethodData, boolean dumpProfiles) {
        CodePointer ip = WordFactory.pointer(address);
        CodeInfoQueryResult result = new AOTCodeInfoQueryResult(ip);
        CodeInfo codeInfo = codeInfo(ip);
        long relativeIP = CodeInfoAccess.relativeIP(codeInfo, ip);
        CodeInfoAccess.lookupCodeInfo(codeInfo, relativeIP, result);
        FrameInfoQueryResult frameInfo = result.getFrameInfo();
        while (frameInfo.getCaller() != null) {
            frameInfo = frameInfo.getCaller();
        }
        int methodId = samplingMethodData.findMethod(relativeIP);
        String methodName = samplingMethodData.findMethodName(relativeIP);
        int bci = frameInfo.getBci();
        return methodName + ":" + bci;
    }

    private static String createDecodedMethodEntry(FrameInfoQueryResult frameInfo, long relativeIP, SamplingMethodData samplingMethodData, boolean dumpProfiles) {
        if (dumpProfiles) {
            List<FrameInfoQueryResult> frames = new ArrayList<>();
            frames.add(frameInfo);
            while (frameInfo.getCaller() != null) {
                frameInfo = frameInfo.getCaller();
                frames.add(frameInfo);
            }
            // TODO
            return "";
        } else {
            while (frameInfo.getCaller() != null) {
                frameInfo = frameInfo.getCaller();
            }
            String methodName = samplingMethodData.findMethodName(relativeIP);
            int bci = frameInfo.getBci();
            return methodName + ":" + bci;
        }
    }

    static void dumpFromTree(BufferedWriter writer, boolean dumpProfiles) throws IOException {
        PrefixTree prefixTree = ImageSingletons.lookup(ProfilingSampler.class).prefixTree();
        SamplingMethodData samplingMethodData = ImageSingletons.lookup(SamplingMethodData.class);

        prefixTree.topDown(new CallFrame("<total>", null), (context, address) -> new CallFrame(decodeMethod(address, samplingMethodData, dumpProfiles), context), (context, value) -> {
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
