package com.oracle.svm.core.debug.jitdump;

import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CUnsigned;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.ProjectHeaderFile;
import com.oracle.svm.core.debug.SubstrateDebugInfoFeature;

/**
 * The jitdump entries are based on the <a href=
 * "https://github.com/torvalds/linux/blob/46a51f4f5edade43ba66b3c151f0e25ec8b69cb6/tools/perf/Documentation/jitdump-specification.txt">Jitdump
 * specification</a>. This defines structs that match the entry descriptions in the specification.
 */
@CContext(JitdumpEntry.JitdumpRecordDirective.class)
public class JitdumpEntry {
    public static class JitdumpRecordDirective implements CContext.Directives {
        @Override
        public boolean isInConfiguration() {
            return SubstrateOptions.RuntimeDebugInfo.getValue() && SubstrateDebugInfoFeature.Options.hasRuntimeDebugInfoFormatSupport(SubstrateDebugInfoFeature.DEBUG_INFO_JITDUMP_NAME);
        }

        @Override
        public List<String> getHeaderFiles() {
            return Collections.singletonList(ProjectHeaderFile.resolve("", "include/jitdump_entry.h"));
        }
    }

    /**
     * Each jitdump record has a record type. The following record types are defined:
     * <ul>
     * <li><code>Value 0 : JIT_CODE_LOAD ......... : record describing a jitted function</code>
     * <li><code>Value 1 : JIT_CODE_MOVE ......... : record describing an already jitted function which is moved</code>
     * <li><code>Value 2 : JIT_CODE_DEBUG_INFO ... : record describing the debug information for a jitted function</code>
     * <li><code>Value 3 : JIT_CODE_CLOSE ........ : record marking the end of the jit runtime (optional)</code>
     * <li><code>Value 4 : JIT_CODE_UNWINDING_INFO : record describing a function unwinding information</code>
     * </ul>
     */
    @CEnum("record_type")
    enum RecordType {
        JIT_CODE_LOAD,
        JIT_CODE_MOVE,
        JIT_CODE_DEBUG_INFO,
        JIT_CODE_CLOSE,
        JIT_CODE_UNWINDING_INFO;

        @CEnumValue
        public native int getCValue();
    }

    /**
     * Each jitdump file starts with a fixed size header containing the following fields in order:
     * <ul>
     * <li><code>uint32_t magic ...... : a magic number tagging the file type (see {@link JitdumpProvider#MAGIC})</code>
     * <li><code>uint32_t version .... : a 4-byte value representing the format version (see {@link JitdumpProvider#VERSION})</code>
     * <li><code>uint32_t total_size . : size in bytes of file header</code>
     * <li><code>uint32_t elf_mach ... : ELF architecture encoding (ELF e_machine value as specified in /usr/include/elf.h)</code>
     * <li><code>uint32_t pad1 ....... : padding, reserved for future use</code>
     * <li><code>uint32_t pid ........ : JIT runtime process id</code>
     * <li><code>uint64_t timestamp .. : timestamp of when the file was created (from {@code LinuxTime#CLOCK_MONOTONIC()})</code>
     * <li><code>uint64_t flags ...... : a bitmask of flags</code>
     * <ul>
     * <li><code>bit 0: JITDUMP_FLAGS_ARCH_TIMESTAMP : set if the jitdump file is using an architecture-specific timestamp clock source.</code>
     * </ul>
     * </ul>
     */
    @CStruct(value = "file_header", addStructKeyword = true)
    public interface FileHeader extends PointerBase {
        // uint32_t magic;
        @CField("magic")
        @CUnsigned
        int getMagic();

        @CField("magic")
        void setMagic(@CUnsigned int magic);

        // uint32_t version;
        @CField("version")
        @CUnsigned
        int getVersion();

        @CField("version")
        void setVersion(@CUnsigned int version);

        // uint32_t total_size;
        @CField("total_size")
        @CUnsigned
        int getTotalSize();

        @CField("total_size")
        void setTotalSize(@CUnsigned int totalSize);

        // uint32_t elf_mach;
        @CField("elf_mach")
        @CUnsigned
        int getElfMach();

        @CField("elf_mach")
        void setElfMach(@CUnsigned int elf_mach);

        // uint32_t pad1;
        @CField("pad1")
        @CUnsigned
        int getPad1();

        @CField("pad1")
        void setPad1(@CUnsigned int pad1);

        // uint32_t pid;
        @CField("pid")
        @CUnsigned
        int getPid();

        @CField("pid")
        void setPid(@CUnsigned int pid);

        // uint64_t timestamp;
        @CField("timestamp")
        @CUnsigned
        long getTimestamp();

        @CField("timestamp")
        void setTimestamp(@CUnsigned long timestamp);

        // uint64_t flags;
        @CField("flags")
        @CUnsigned
        long getFlags();

        @CField("flags")
        void setFlags(@CUnsigned long flags);
    }

    /**
     * The {@link FileHeader jitdump file header} is immediately followed by records. Each record
     * starts with a fixed size header describing the record that follows.
     * <p>
     * The record header is specified as follows:
     * <ul>
     * <li><code>uint32_t id ......... : a value identifying the {@link RecordType record type}</code>
     * <li><code>uint32_t total_size . : the size in bytes of the record including the header</code>
     * <li><code>uint64_t timestamp .. : a timestamp of when the record was created (from {@code LinuxTime#CLOCK_MONOTONIC()})</code>
     * </ul>
     * <p>
     * The payload of the record must immediately follow the record header without padding.
     */
    @CStruct(value = "record_header", addStructKeyword = true)
    public interface RecordHeader extends PointerBase {
        // uint32_t id;
        @CField("id")
        @CUnsigned
        int getId();

        @CField("id")
        void setId(@CUnsigned int id);

        // uint32_t total_size;
        @CField("total_size")
        @CUnsigned
        int getTotalSize();

        @CField("total_size")
        void setTotalSize(@CUnsigned int totalSize);

        // uint64_t timestamp;
        @CField("timestamp")
        @CUnsigned
        long getTimestamp();

        @CField("timestamp")
        void setTimestamp(@CUnsigned long timestamp);
    }

    /**
     * The code load record has the following fields following the {@link RecordHeader record
     * header}:
     * <ul>
     * <li><code>uint32_t pid ........ : OS process id of the runtime generating the jitted code</code>
     * <li><code>uint32_t tid ........ : OS thread id of the runtime thread generating the jitted code</code>
     * <li><code>uint64_t vma ........ : virtual address of jitted code start</code>
     * <li><code>uint64_t code_addr .. : code start address for the jitted code (by default vma = code_addr)</code>
     * <li><code>uint64_t code_size .. : size in bytes of the generated jitted code</code>
     * <li><code>uint64_t code_index . : unique identifier for the jitted code (see below)</code>
     * </ul>
     * A code load record is immediately followed by the function name in ASCII (including the null
     * termination) and the raw byte encoding of the jitted code. The format supports empty
     * functions with no native code.
     * <p>
     * The record header total_size field is inclusive of all components:
     * <ul>
     * <li><code>record header</code>
     * <li><code>fixed-sized fields</code>
     * <li><code>function name string, including termination</code>
     * <li><code>native code length</code>
     * </ul>
     * <p>
     * The code_index is used to uniquely identify each jitted function. The index can be a
     * monotonically increasing 64-bit value. Each time a function is jitted it gets a new number.
     * This value is used in case the code for a function is moved and avoids having to issue
     * another {@code JIT_CODE_LOAD} record.
     */
    @CStruct(value = "code_load_record", addStructKeyword = true)
    public interface CodeLoadRecord extends RecordHeader {
        // uint32_t pid;
        @CField("pid")
        @CUnsigned
        int getPid();

        @CField("pid")
        void setPid(@CUnsigned int pid);

        // uint32_t tid;
        @CField("tid")
        @CUnsigned
        int getTid();

        @CField("tid")
        void setTid(@CUnsigned int tid);

        // uint64_t vma;
        @CField("vma")
        @CUnsigned
        long getVma();

        @CField("vma")
        void setVma(@CUnsigned long vma);

        // uint64_t code_addr;
        @CField("code_addr")
        @CUnsigned
        long getCodeAddr();

        @CField("code_addr")
        void setCodeAddr(@CUnsigned long codeAddr);

        // uint64_t code_size;
        @CField("code_size")
        @CUnsigned
        long getCodeSize();

        @CField("code_size")
        void setCodeSize(@CUnsigned long codeSize);

        // uint64_t code_index;
        @CField("code_index")
        @CUnsigned
        long getCodeIndex();

        @CField("code_index")
        void setCodeIndex(@CUnsigned long codeIndex);
    }

    /**
     * The debug entry describes source line information and is part of a {@link DebugInfoRecord
     * debug info record}. It is defined as follows:
     * <ul>
     * <li><code>uint64_t code_addr : address of function (or inlined function) of this debug entry</code>
     * <li><code>uint32_t line .... : source file line number (starting at 1)</code>
     * <li><code>uint32_t discrim . : column discriminator, 0 is default</code>
     * </ul>
     * A debug entry is immediately followed by the corresponding source file name in ASCII
     * (including null termination)</code>
     */
    @CStruct(value = "debug_entry", addStructKeyword = true)
    public interface DebugEntry extends PointerBase {
        DebugEntry addressOf(int index);

        // uint64_t code_addr;
        @CField("code_addr")
        @CUnsigned
        long getCodeAddr();

        @CField("code_addr")
        void setCodeAddr(@CUnsigned long codeAddr);

        // uint32_t line;
        @CField("line")
        @CUnsigned
        int getLine();

        @CField("line")
        void setLine(@CUnsigned int line);

        // uint32_t discrim;
        @CField("discrim")
        @CUnsigned
        int getDiscrim();

        @CField("discrim")
        void setDiscrim(@CUnsigned int discrim);
    }

    /**
     * The debug info record contains source lines debug information, i.e., a way to map a code
     * address back to a source line. This information may be used by the performance tool.
     * <p>
     * The record has the following fields following the fixed-size record header in order:
     * <ul>
     * <li><code>uint64_t code_addr: address of function for which the debug information is generated</code>
     * <li><code>uint64_t nr_entry : number of debug entries for the function</code>
     * </ul>
     * A debug info record is immediately followed by an array of nr_entry {@link DebugEntry debug
     * entries}. The debug entries are saved in sequence but given that they have variable sizes due
     * to the file name string, they cannot be indexed directly. The next debug entry is found at
     * sizeof(debug_entry) + strlen(filename) + 1.
     * <p>
     * IMPORTANT: The debug info record for a given function must always be generated BEFORE the
     * code load record for the function. The parser only holds one debug info record in memory and
     * attaches it to the next code load record.
     */
    @CStruct(value = "debug_info_record", addStructKeyword = true)
    public interface DebugInfoRecord extends RecordHeader {
        // uint64_t code_addr;
        @CField("code_addr")
        @CUnsigned
        long getCodeAddr();

        @CField("code_addr")
        void setCodeAddr(@CUnsigned long codeAddr);

        // uint64_t nr_entry;
        @CField("nr_entry")
        @CUnsigned
        long getNrEntry();

        @CField("nr_entry")
        void setNrEntry(@CUnsigned long nrEntry);
    }
}
