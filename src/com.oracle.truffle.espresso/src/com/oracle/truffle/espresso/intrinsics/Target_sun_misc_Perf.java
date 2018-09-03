package com.oracle.truffle.espresso.intrinsics;

import java.nio.ByteBuffer;

import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

import sun.misc.Perf;

@EspressoIntrinsics
public class Target_sun_misc_Perf {

    static class ByteUtils {
        private static ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);

        public static byte[] longToBytes(long x) {
            buffer.putLong(0, x);
            return buffer.array();
        }

        public static long bytesToLong(byte[] bytes) {
            buffer.put(bytes, 0, bytes.length);
            buffer.flip();// need flip
            return buffer.getLong();
        }
    }

    private final static Perf hostPerf = Perf.getPerf();

    @Intrinsic(hasReceiver = true)
    public static long highResCounter(Object self) {
        return Perf.getPerf().highResCounter();
    }

    @Intrinsic(hasReceiver = true)
    public static long highResFrequency(Object self) {
        return Perf.getPerf().highResFrequency();
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(ByteBuffer.class) StaticObject createLong(Object self, @Type(String.class) StaticObject var1, int var2, int var3, long var4) {
        EspressoContext context = Utils.getContext();
        MethodInfo wrap = context.getRegistries().resolve(context.getTypeDescriptors().make("Ljava/nio/ByteBuffer;"), null).findDeclaredMethod("wrap", ByteBuffer.class, byte[].class);
        return (StaticObject) wrap.getCallTarget().call((Object) ByteUtils.longToBytes(var4));
    }

    @Intrinsic
    public static void registerNatives() {
        // nop
    }
}
