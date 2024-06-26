package com.oracle.svm.core.debug;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.ProjectHeaderFile;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CUnsigned;
import org.graalvm.word.PointerBase;

import java.util.Collections;
import java.util.List;

@CContext(GDBJITInterface.GDBJITInterfaceDirectives.class)
public class GDBJITInterface {

    public static class GDBJITInterfaceDirectives implements CContext.Directives {
        @Override
        public boolean isInConfiguration() {
            return SubstrateOptions.RuntimeDebugInfo.getValue();
        }

        @Override
        public List<String> getHeaderFiles() {
            return Collections.singletonList(ProjectHeaderFile.resolve("com.oracle.svm.native.debug", "include/gdbJITCompilationInterface.h"));
        }

        @Override
        public List<String> getLibraries() {
            return Collections.singletonList("debug");
        }
    }

    @CStruct(value = "jit_code_entry", addStructKeyword = true)
    public interface JITCodeEntry extends PointerBase {
        // struct jit_code_entry *next_entry;
        @CField("next_entry")
        JITCodeEntry getNextEntry();
        @CField("next_entry")
        void setNextEntry(JITCodeEntry jitCodeEntry);

        // struct jit_code_entry *prev_entry;
        @CField("prev_entry")
        JITCodeEntry getPrevEntry();
        @CField("prev_entry")
        void setPrevEntry(JITCodeEntry jitCodeEntry);

        // const char *symfile_addr;
        @CField("symfile_addr")
        CCharPointer getSymfileAddr();
        @CField("symfile_addr")
        void setSymfileAddr(CCharPointer symfileAddr);

        // uint64_t symfile_size;
        @CField("symfile_size")
        @CUnsigned long getSymfileSize();
        @CField("symfile_size")
        void setSymfileSize(@CUnsigned long symfileSize);
    }

    @CFunction(value = "register_jit_code", transition = CFunction.Transition.NO_TRANSITION)
    public static native JITCodeEntry registerJITCode(CCharPointer addr, @CUnsigned long size);

    @CFunction(value = "unregister_jit_code", transition = CFunction.Transition.NO_TRANSITION)
    public static native void unregisterJITCode(JITCodeEntry entry);
}
