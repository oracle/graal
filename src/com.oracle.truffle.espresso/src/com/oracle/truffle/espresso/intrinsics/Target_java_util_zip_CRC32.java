package com.oracle.truffle.espresso.intrinsics;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.zip.CRC32;

import com.oracle.truffle.espresso.meta.EspressoError;

@EspressoIntrinsics
public class Target_java_util_zip_CRC32 {
    @Intrinsic
    public static int update(int crc, int b) {
        Method m = null;
        try {
            m = CRC32.class.getDeclaredMethod("update", int.class, int.class);
            m.setAccessible(true);
            return (int) m.invoke(CRC32.class, crc, b);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Intrinsic
    public static int updateBytes(int crc, byte[] b, int off, int len) {
        Method m = null;
        try {
            m = CRC32.class.getDeclaredMethod("updateBytes", int.class, byte[].class, int.class, int.class);
            m.setAccessible(true);
            return (int) m.invoke(CRC32.class, crc, b, off, len);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Intrinsic
    public static int updateByteBuffer(int adler, long addr, int off, int len) {
        Method m = null;
        try {
            m = CRC32.class.getDeclaredMethod("updateByteBuffer", int.class, long.class, int.class, int.class);
            m.setAccessible(true);
            return (int) m.invoke(CRC32.class, addr, addr, off, len);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }
}
