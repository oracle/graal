package com.oracle.svm.core.graal.llvm;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.Arrays;

import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.log.Log;

public class GCCExceptionTable {

    enum Encoding {
        ULEB128((byte) 0x1),
        UDATA2((byte) 0x2),
        UDATA4((byte) 0x3),
        UDATA8((byte) 0x4),
        SLEB128((byte) 0x9),
        SDATA2((byte) 0xA),
        SDATA4((byte) 0xB),
        SDATA8((byte) 0xC);

        byte encoding;
        static Encoding[] lookupTable = new Encoding[16];

        static {
            Arrays.stream(values()).forEach(value -> lookupTable[value.encoding] = value);
        }

        Encoding(byte encoding) {
            this.encoding = encoding;
        }

        static Encoding parse(int encodingEncoding) {
            if (encodingEncoding < 0 || encodingEncoding >= lookupTable.length) {
                return null;
            }
            Encoding encoding = lookupTable[encodingEncoding];
            if (encoding == null) {
                return null;
            }
            return encoding;
        }
    }

    private static final FastThreadLocalInt offset = FastThreadLocalFactory.createInt();

    public static Long getHandlerOffset(Pointer buffer, long pcOffset) {
        Log log = Log.noopLog();
        offset.set(0);

        int header = Byte.toUnsignedInt(buffer.readByte(offset.get()));
        offset.set(offset.get() + Byte.BYTES);
        log.string("header: ").hex(header).newline();
        assert header == 255;

        int typeEncodingEncoding = Byte.toUnsignedInt(buffer.readByte(offset.get()));
        offset.set(offset.get() + Byte.BYTES);
        log.string("typeEncodingEncoding: ").hex(typeEncodingEncoding).newline();
        assert typeEncodingEncoding == 155;

        long typeBaseOffset = getULSB(buffer);
        long typeEnd = typeBaseOffset + offset.get();
        log.string("typeBaseOffset: ").unsigned(typeBaseOffset).string(", typeEnd: ").unsigned(typeEnd).newline();

        int siteEncodingEncoding = Byte.toUnsignedInt(buffer.readByte(offset.get()));
        offset.set(offset.get() + Byte.BYTES);
        log.string("siteEncodingEncoding: ").hex(siteEncodingEncoding).newline();
        Encoding siteEncoding = Encoding.parse(siteEncodingEncoding);

        long siteTableLength = getULSB(buffer);
        log.string("siteTableLength: ").signed(siteTableLength).newline();
        assert siteTableLength % 13 == 0;

        long siteTableEnd = offset.get() + siteTableLength;
        log.string("siteTableEnd: ").signed(siteTableEnd).newline();
        while (offset.get() < siteTableEnd) {

            long startOffset = get(buffer, siteEncoding);
            long size = get(buffer, siteEncoding);
            long handlerOffset = get(buffer, siteEncoding);
            log.string("start: ").unsigned(startOffset).string(", size: ").unsigned(size).string(", handlerOffset: ").unsigned(handlerOffset).newline();

            if (startOffset <= pcOffset && startOffset + size >= pcOffset) {
                return handlerOffset;
            }
            int action = Byte.toUnsignedInt(buffer.readByte(offset.get()));
            offset.set(offset.get() + Byte.BYTES);
            log.string("action: ").unsigned(action).newline();
            if (action != 0) {
                assert action == 1;
            }
        }

        return null;
    }

    private static long getULSB(Pointer buffer) {
        int read;
        long result = 0;
        int shift = 0;
        do {
            read = buffer.readByte(offset.get());
            offset.set(offset.get() + Byte.BYTES);
            result |= (read & Byte.MAX_VALUE) << shift;
            shift += 7;
        } while ((read & 0x80) != 0);

        return result;
    }

    private static long get(Pointer buffer, Encoding encoding) {
        switch (encoding) {
            case ULEB128:
                return getULSB(buffer);
            case UDATA4:
                int result = buffer.readInt(offset.get());
                offset.set(offset.get() + Integer.BYTES);
                return result;
            default:
                throw shouldNotReachHere();
        }
    }
}
