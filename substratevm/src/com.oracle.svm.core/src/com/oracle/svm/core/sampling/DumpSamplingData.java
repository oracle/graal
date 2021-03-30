package com.oracle.svm.core.sampling;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;

public class DumpSamplingData {
    private static final class CallFrame {
        final CallFrameInfo frameInfo;
        final CallFrame tail;

        private CallFrame(CallFrameInfo frameInfo, CallFrame tail) {
            this.tail = tail;
            this.frameInfo = frameInfo;
        }
    }

    public Runnable dumpSamplingProfilesToFile() {
        return DumpSamplingData::dumpSamplingData;
    }

    public static void dumpSamplingData() {
        dumpToFile("sampling-java-stack.iprof", DumpMode.JavaStackView);
        dumpToFile("sampling-compilation-stack.iprof", DumpMode.CompilationStackView);
    }

    private static void dumpToFile(String fileName, DumpMode dumpMode) {
        BufferedWriter profilesWriter = null;
        try {
            Path profilesPath = Paths.get(fileName).toAbsolutePath();
            profilesWriter = new BufferedWriter(new FileWriter(profilesPath.toFile()));
            dumpFromTree(profilesWriter, dumpMode);
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            if (profilesWriter != null) {
                try {
                    profilesWriter.close();
                } catch (Throwable e) {
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

    static List<CallFrameInfo> decodeMethod(long address, DumpMode mode) {
        // The resulting list is ordered from callees to their callers to align with the format of
        // the instrumentation profiles.
        CodePointer ip = WordFactory.pointer(address);
        CodeInfoQueryResult result = new AOTCodeInfoQueryResult(ip);
        CodeInfo codeInfo = codeInfo(ip);
        long relativeIP = CodeInfoAccess.relativeIP(codeInfo, ip);
        CodeInfoAccess.lookupCodeInfo(codeInfo, relativeIP, result);
        FrameInfoQueryResult frameInfo = result.getFrameInfo();
        return createDecodedMethodEntry(frameInfo, mode);
    }

    private static List<CallFrameInfo> createDecodedMethodEntry(FrameInfoQueryResult frameInfo, DumpMode mode) {
        CallStackFrameMethodInfo frameMethodData = ImageSingletons.lookup(CallStackFrameMethodInfo.class);
        if (mode == DumpMode.JavaStackView) {
            List<CallFrameInfo> frames = new ArrayList<>();
            int frameInfoMethodId = frameInfo.getMethodID();
            frames.add(new CallFrameBciInfo(frameInfoMethodId, frameInfo.getBci(), frameMethodData.methodFor(frameInfoMethodId)));
            while (frameInfo.getCaller() != null) {
                frameInfo = frameInfo.getCaller();
                frameInfoMethodId = frameInfo.getMethodID();
                frames.add(new CallFrameBciInfo(frameInfoMethodId, frameInfo.getBci(), frameMethodData.methodFor(frameInfoMethodId)));
            }
            return frames;
        } else if (mode == DumpMode.JavaStackWithoutBciView) {
            List<CallFrameInfo> frames = new ArrayList<>();
            int frameInfoMethodId = frameInfo.getMethodID();
            frames.add(new CallFrameBciInfo(frameInfoMethodId, frameInfo.getBci(), frameMethodData.methodFor(frameInfoMethodId)));
            while (frameInfo.getCaller() != null) {
                frameInfo = frameInfo.getCaller();
                frameInfoMethodId = frameInfo.getMethodID();
                frames.add(new CallFrameNoBciInfo(frameInfoMethodId, frameMethodData.methodFor(frameInfoMethodId)));
            }
            return frames;
        } else {
            assert mode == DumpMode.CompilationStackView;
            while (frameInfo.getCaller() != null) {
                frameInfo = frameInfo.getCaller();
            }
            int frameInfoMethodId = frameInfo.getMethodID();
            return Collections.singletonList(new CallFrameNoBciInfo(frameInfoMethodId, frameMethodData.methodFor(frameInfoMethodId)));

        }
    }

    static void dumpFromTree(BufferedWriter writer, DumpMode mode) {
        PrefixTree prefixTree = ImageSingletons.lookup(ProfilingSampler.class).prefixTree();
        // TODO: remove total
        prefixTree.topDown(new CallFrame(new CallFrameNoBciInfo(CallFrameNoBciInfo.TOTAL_ID, "<total>"), null), (context, address) -> {
            List<CallFrameInfo> frameInfos = decodeMethod(address, mode);
            CallFrame head = context;
            for (CallFrameInfo frameInfo : frameInfos) {
                head = new CallFrame(frameInfo, head);
            }
            return head;
        }, (context, value) -> {
            CallStackFrameMethodInfo frameMethodData = ImageSingletons.lookup(CallStackFrameMethodInfo.class);
            CallFrame current = context;

            if (value > 0) {
                boolean safepointFound = false;
                while (!safepointFound) {
                    if (frameMethodData.isSamplingCodeEntry(current.frameInfo.methodId)) {
                        safepointFound = true;
                    }
                    current = current.tail;
                }
                StringBuilder contextString = new StringBuilder(current.frameInfo.toString());
                current = current.tail;
                while (current != null) {
                    contextString.append(",").append(current.frameInfo);
                    current = current.tail;
                }
                contextString.append(" ").append(value);
                try {
                    writer.write(contextString.toString());
                    writer.newLine();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    static class AOTCodeInfoQueryResult extends CodeInfoQueryResult {

        AOTCodeInfoQueryResult(CodePointer ip) {
            super();
            this.ip = ip;
        }
    }

    static class CallFrameInfo {
        int methodId;
    }

    static class CallFrameBciInfo extends CallFrameInfo {
        int bci;
        String methodName;

        CallFrameBciInfo(int methodId, int bci, String methodName) {
            this.methodId = methodId;
            this.bci = bci;
            this.methodName = methodName;
        }

        @Override
        public String toString() {
            return methodName + " (" + methodId + ") " + ":" + bci;
        }
    }

    static class CallFrameNoBciInfo extends CallFrameInfo {
        static final int TOTAL_ID = -5;
        String methodName;

        CallFrameNoBciInfo(int methodId, String methodName) {
            this.methodId = methodId;
            this.methodName = methodName;
        }

        @Override
        public String toString() {
            return methodName + " (" + methodId + ")";
        }
    }

    enum DumpMode {
        JavaStackView,
        JavaStackWithoutBciView,
        CompilationStackView;
    }
}
