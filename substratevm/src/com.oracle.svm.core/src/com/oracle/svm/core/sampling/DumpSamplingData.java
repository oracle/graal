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
        final ModeFrameData frameInfo;
        final CallFrame tail;

        private CallFrame(ModeFrameData frameInfo, CallFrame tail) {
            this.tail = tail;
            this.frameInfo = frameInfo;
        }
    }

    public Runnable dumpSamplingProfilesToFile() {
        return DumpSamplingData::dumpSamplingData;
    }

    public static void dumpSamplingData() {
        BufferedWriter profilesWriter = null;
        try {
            Path pathProfiles = Paths.get("sampling-java-stack.iprof").toAbsolutePath();
            profilesWriter = new BufferedWriter(new FileWriter(pathProfiles.toFile()));
            dumpFromTree(profilesWriter, DUMP_MODE.JAVA_STACK_VIEW);
        } catch (Throwable e) {
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
        try {
            Path pathProfiles = Paths.get("sampling-compilation-stack.iprof").toAbsolutePath();
            profilesWriter = new BufferedWriter(new FileWriter(pathProfiles.toFile()));
            dumpFromTree(profilesWriter, DUMP_MODE.COMPILATION_STACK_VIEW);
        } catch (Throwable e) {
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

    static List<ModeFrameData> decodeMethod(long address, DUMP_MODE mode) {
        CodePointer ip = WordFactory.pointer(address);
        CodeInfoQueryResult result = new AOTCodeInfoQueryResult(ip);
        CodeInfo codeInfo = codeInfo(ip);
        long relativeIP = CodeInfoAccess.relativeIP(codeInfo, ip);
        CodeInfoAccess.lookupCodeInfo(codeInfo, relativeIP, result);
        FrameInfoQueryResult frameInfo = result.getFrameInfo();
        return createDecodedMethodEntry(frameInfo, mode);
    }

    private static List<ModeFrameData> createDecodedMethodEntry(FrameInfoQueryResult frameInfo, DUMP_MODE mode) {
        DebugCallStackFrameMethodData frameMethodData = ImageSingletons.lookup(DebugCallStackFrameMethodData.class);
        if (mode == DUMP_MODE.JAVA_STACK_VIEW) {
            List<ModeFrameData> frames = new ArrayList<>();
            int frameInfoMethodId = frameInfo.getMethodID();
            frames.add(new MethodIDBCI(frameInfoMethodId, frameInfo.getBci(), frameMethodData.methodInfo(frameInfoMethodId)));
            while (frameInfo.getCaller() != null) {
                frameInfo = frameInfo.getCaller();
                frameInfoMethodId = frameInfo.getMethodID();
                frames.add(new MethodIDBCI(frameInfoMethodId, frameInfo.getBci(), frameMethodData.methodInfo(frameInfoMethodId)));
            }
            return frames;
        } else {
            while (frameInfo.getCaller() != null) {
                frameInfo = frameInfo.getCaller();
            }
            int frameInfoMethodId = frameInfo.getMethodID();
            if (mode == DUMP_MODE.COMPILATION_STACK_VIEW) {
                return Collections.singletonList(new MethodID(frameInfoMethodId, frameMethodData.methodInfo(frameInfoMethodId)));

            } else {
                assert mode == DUMP_MODE.COMPILATION_WITH_BCI_STACK_VIEW;
                int bci = frameInfo.getBci();
                return Collections.singletonList(new MethodIDBCI(frameInfoMethodId, bci, frameMethodData.methodInfo(frameInfoMethodId)));
            }
        }
    }

    static void dumpFromTree(BufferedWriter writer, DUMP_MODE mode) {
        PrefixTree prefixTree = ImageSingletons.lookup(ProfilingSampler.class).prefixTree();
        prefixTree.topDown(new CallFrame(new MethodID(MethodID.TOTAL_ID, "<total>"), null), (context, address) -> {
            List<ModeFrameData> frameDatas = decodeMethod(address, mode);
            CallFrame head = context;
            for (ModeFrameData frameData : frameDatas) {
                head = new CallFrame(frameData, head);
            }
            return head;
        }, (context, value) -> {
            // TODO: remove total
            DebugCallStackFrameMethodData frameMethodData = ImageSingletons.lookup(DebugCallStackFrameMethodData.class);
            CallFrame current = context;

            if (value > 0) {
                boolean safepointFound = false;
                ModeFrameData frameData;
                while (!safepointFound) {
                    frameData = current.frameInfo;
                    if (frameMethodData.isSamplingCode(frameData.methodId)) {
                        safepointFound = true;
                    }
                    current = current.tail;
                }
                StringBuilder contextString = new StringBuilder(current.frameInfo.toString());
                current = current.tail;
                while (current != null) {
                    frameData = current.frameInfo;
                    contextString.append(",").append(frameData);
                    current = current.tail;
                }
                contextString.append(" ").append(value);
                System.out.println(contextString.toString());
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

    static class ModeFrameData {
        int methodId;
    }

    static class MethodIDBCI extends ModeFrameData {
        int bci;
        String methodName;

        MethodIDBCI(int methodId, int bci, String methodName) {
            this.methodId = methodId;
            this.bci = bci;
            this.methodName = methodName;
        }

        @Override
        public String toString() {
            return methodName + " (" + methodId + ") " + ":" + bci;
        }
    }

    static class MethodID extends ModeFrameData {
        static final int TOTAL_ID = -5;
        String methodName;

        MethodID(int methodId, String methodName) {
            this.methodId = methodId;
            this.methodName = methodName;
        }

        @Override
        public String toString() {
            return methodName + " (" + methodId + ")";
        }
    }

    enum DUMP_MODE {
        JAVA_STACK_VIEW,
        COMPILATION_STACK_VIEW,
        COMPILATION_WITH_BCI_STACK_VIEW;
    }
}
