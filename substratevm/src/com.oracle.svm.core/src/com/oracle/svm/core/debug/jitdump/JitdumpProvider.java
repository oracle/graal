package com.oracle.svm.core.debug.jitdump;

import java.nio.ByteOrder;

import org.graalvm.nativeimage.ProcessProperties;

public class JitdumpProvider {

    public static final int JITDUMP_HEADER_SIZE = 40;
    public static final int RECORD_HEADER_SIZE = 16;

    public static final int MAGIC_NUMBER = 0x4A695444;  // = "JiTD"
    public static final int JITDUMP_VERSION = 1;

    /**
     * Generates the byte array of a jitdump header.
     * <p>
     * According to the <a href=
     * "https://raw.githubusercontent.com/torvalds/linux/master/tools/perf/Documentation/jitdump-specification.txt">jitdump
     * specification</a> each jitdump file starts with a fixed size header containing the following
     * fields in order:
     * <ul>
     * <li><code>uint32_t magic ...... : a magic number tagging the file type. The value is 4-byte long and represents the string "JiTD" in ASCII form. It written is as 0x4A695444. The reader will detect an endian mismatch when it reads 0x4454694a.</code>
     * <li><code>uint32_t version .... : a 4-byte value representing the format version. It is currently set to 1</code>
     * <li><code>uint32_t total_size . : size in bytes of file header</code>
     * <li><code>uint32_t elf_mach ... : ELF architecture encoding (ELF e_machine value as specified in /usr/include/elf.h)</code>
     * <li><code>uint32_t pad1 ....... : padding. Reserved for future use</code>
     * <li><code>uint32_t pid ........ : JIT runtime process identification (OS specific)</code>
     * <li><code>uint64_t timestamp .. : timestamp of when the file was created</code>
     * <li><code>uint64_t flags ...... : a bitmask of flags</code>
     * </ul>
     *
     * @return The content of the jitdump header
     */
    public static byte[] createHeader() {
        int pos = 0;
        byte[] content = new byte[JITDUMP_HEADER_SIZE];

        pos = writeInt(MAGIC_NUMBER, content, pos);
        pos = writeInt(JITDUMP_VERSION, content, pos);
        pos = writeInt(JITDUMP_HEADER_SIZE, content, pos);
        // TODO get elf_mach, maybe
        // ELFMachine.from(ImageSingletons.lookup(Platform.class).getArchitecture()).toShort()
        pos = writeInt(0x3E, content, pos);
        pos = writeInt(0, content, pos);
        pos = writeInt((int) ProcessProperties.getProcessID(), content, pos);
        pos = writeLong(System.currentTimeMillis(), content, pos);
        pos = writeLong(0, content, pos); // no flags

        assert pos == JITDUMP_HEADER_SIZE;
        return content;
    }

    public static byte[] createRecords() {
        return new byte[0];
    }

    /**
     * Write a record header to the content byte array.
     * <p>
     * Each record starts with a fixed size header describing the record that follows. The record
     * header is specified in order as follows:
     * <ul>
     * <li><code>uint32_t id ......... : a value identifying the record type (see below)</code>
     * <li><code>uint32_t total_size . : the size in bytes of the record including the header.</code>
     * <li><code>uint64_t timestamp .. : a timestamp of when the record was created.</code>
     * </ul>
     * The following record types are defined:
     * <ul>
     * <li><code>Value 0 : JIT_CODE_LOAD ........... : record describing a jitted function</code>
     * <li><code>Value 1 : JIT_CODE_MOVE ........... : record describing an already jitted function which is moved</code>
     * <li><code>Value 2 : JIT_CODE_DEBUG_INFO ..... : record describing the debug information for a jitted function</code>
     * <li><code>Value 3 : JIT_CODE_CLOSE .......... : record marking the end of the jit runtime (optional)</code>
     * <li><code>Value 4 : JIT_CODE_UNWINDING_INFO . : record describing a function unwinding information</code>
     * </ul>
     *
     * @param id the id of the records' type
     * @param recordSize the record size of this header's record
     * @param content the content byte array
     * @param p the position in the byte array before writing
     * @return the position in the byte array after writing
     */
    private static int writeRecordHeader(int id, int recordSize, byte[] content, int p) {
        int pos = p;

        pos = writeInt(id, content, pos);
        pos = writeInt(recordSize, content, pos);
        pos = writeLong(System.currentTimeMillis(), content, pos);

        return pos;
    }

    private static boolean bigEndian() {
        return ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    }

    private static int writeByte(byte b, byte[] content, int p) {
        int pos = p;
        content[pos++] = b;
        return pos;
    }

    private static int writeShort(short s, byte[] content, int p) {
        int pos = p;
        if (bigEndian()) {
            s = Short.reverseBytes(s);
        }
        pos = writeByte((byte) s, content, pos);
        pos = writeByte((byte) (s >> Byte.SIZE), content, pos);
        return pos;
    }

    private static int writeInt(int i, byte[] content, int p) {
        int pos = p;
        if (bigEndian()) {
            i = Integer.reverseBytes(i);
        }
        pos = writeShort((short) i, content, pos);
        pos = writeShort((short) (i >> Short.SIZE), content, pos);
        return pos;
    }

    private static int writeLong(long l, byte[] content, int p) {
        int pos = p;
        if (bigEndian()) {
            l = Long.reverseBytes(l);
        }
        pos = writeInt((int) l, content, pos);
        pos = writeInt((int) (l >> Integer.SIZE), content, pos);
        return pos;
    }
}
