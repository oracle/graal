package com.oracle.svm.core.debug;

import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CUnsigned;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.ProjectHeaderFile;

@CContext(GDBJITInterface.GDBJITInterfaceDirectives.class)
public class GDBJITInterface {

    public static class GDBJITInterfaceDirectives implements CContext.Directives {
        @Override
        public boolean isInConfiguration() {
            return SubstrateOptions.RuntimeDebugInfo.getValue();
        }

        @Override
        public List<String> getHeaderFiles() {
            return Collections.singletonList(ProjectHeaderFile.resolve("com.oracle.svm.native.debug", "include/gdb_jit_compilation_interface.h"));
        }

        @Override
        public List<String> getLibraries() {
            return Collections.singletonList("debug");
        }
    }

    @CEnum(value = "jit_actions_t")
    public enum JITActions {
        JIT_NOACTION,
        JIT_REGISTER,
        JIT_UNREGISTER;

        @CEnumValue
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public native int getCValue();
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
        @CUnsigned
        long getSymfileSize();

        @CField("symfile_size")
        void setSymfileSize(@CUnsigned long symfileSize);
    }

    @CStruct(value = "jit_descriptor", addStructKeyword = true)
    public interface JITDescriptor extends PointerBase {
        // uint32_t version;
        @CField("version")
        @CUnsigned
        int getVersion();

        @CField("version")
        void setVersion(@CUnsigned int version);

        // uint32_t action_flag;
        @CField("action_flag")
        @CUnsigned
        int getActionFlag();

        @CField("action_flag")
        void setActionFlag(@CUnsigned int actionFlag);

        // struct jit_code_entry *relevant_entry;
        @CField("relevant_entry")
        JITCodeEntry getRelevantEntry();

        @CField("relevant_entry")
        void setRelevantEntry(JITCodeEntry jitCodeEntry);

        // struct jit_code_entry *first_entry;
        @CField("first_entry")
        JITCodeEntry getFirstEntry();

        @CField("first_entry")
        void setFirstEntry(JITCodeEntry jitCodeEntry);
    }

    @CFunction(value = "__jit_debug_register_code", transition = CFunction.Transition.NO_TRANSITION)
    private static native void jitDebugRegisterCode();

    private static final CGlobalData<JITDescriptor> jitDebugDescriptor = CGlobalDataFactory.forSymbol("__jit_debug_descriptor");

    public static void registerJITCode(CCharPointer addr, @CUnsigned long size, JITCodeEntry entry) {
        /* Create new jit_code_entry */
        entry.setSymfileAddr(addr);
        entry.setSymfileSize(size);

        /* Insert entry at head of the list. */
        JITCodeEntry nextEntry = jitDebugDescriptor.get().getFirstEntry();
        entry.setPrevEntry(WordFactory.nullPointer());
        entry.setNextEntry(nextEntry);

        if (nextEntry.isNonNull()) {
            nextEntry.setPrevEntry(entry);
        }

        /* Notify GDB. */
        jitDebugDescriptor.get().setActionFlag(JITActions.JIT_REGISTER.getCValue());
        jitDebugDescriptor.get().setFirstEntry(entry);
        jitDebugDescriptor.get().setRelevantEntry(entry);
        jitDebugRegisterCode();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void unregisterJITCode(JITCodeEntry entry) {
        JITCodeEntry prevEntry = entry.getPrevEntry();
        JITCodeEntry nextEntry = entry.getNextEntry();

        /* Fix prev and next in list */
        if (nextEntry.isNonNull()) {
            nextEntry.setPrevEntry(prevEntry);
        }

        if (prevEntry.isNonNull()) {
            prevEntry.setNextEntry(nextEntry);
        } else {
            assert (jitDebugDescriptor.get().getFirstEntry().equal(entry));
            jitDebugDescriptor.get().setFirstEntry(nextEntry);
        }

        /* Notify GDB. */
        jitDebugDescriptor.get().setActionFlag(JITActions.JIT_REGISTER.ordinal());
        jitDebugDescriptor.get().setRelevantEntry(entry);
        jitDebugRegisterCode();

        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(entry);
    }
}
