//package com.oracle.truffle.espresso.intrinsics;
//
//import com.oracle.truffle.espresso.EspressoLanguage;
//
//import java.lang.reflect.InvocationTargetException;
//import java.lang.reflect.Method;
//import java.lang.reflect.Modifier;
//
//@EspressoIntrinsics
//public class Target_java_io_RandomAccessFile {
//
//    enum RandomAccessFileFunctions {
//        OPEN0("open0", String.class, int.class),
//        READ0("read0"),
//        READ_BYTES("readBytes", byte[].class, int.class, int.class),
//        WRITE0("write0", int.class),
//        WRITE_BYTES("writeBytes", byte[].class, int.class, int.class),
//        SEEK0("seek0", long.class),
//        CLOSE0("close0"),
//        INIT_IDS("initIDs");
//
//        RandomAccessFileFunctions(String name, Class<?>... parameterTypes) {
//            try {
//                this.method = RandomAccessFileFunctions.class.getDeclaredMethod(name, parameterTypes);
//                this.method.setAccessible(true);
//            } catch (NoSuchMethodException e) {
//                throw new RuntimeException(e);
//            }
//        }
//
//        private final Method method;
//
//        public Method getMethod() {
//            return method;
//        }
//
//        public Object invokeStatic(Object... args) {
//            try {
//                assert Modifier.isStatic(getMethod().getModifiers());
//                return getMethod().invoke(null, args);
//            } catch (IllegalAccessException e) {
//                throw new RuntimeException(e);
//            } catch (InvocationTargetException e) {
//                throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getTargetException().getClass());
//            }
//        }
//
//        public Object invoke(Object self, Object... args) {
//            try {
//                assert Modifier.isStatic(getMethod().getModifiers());
//                return getMethod().invoke(self, args);
//            } catch (IllegalAccessException e) {
//                throw new RuntimeException(e);
//            } catch (InvocationTargetException e) {
//                throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getTargetException().getClass());
//            }
//        }
//    }
//
//    // @Intrinsic(hasReceiver = true)
//    // public static void open0(StaticObject self, String name, int mode) { // FileNotFoundException
//    // RandomAccessFileFunctions.OPEN0.invoke(meta(self).getMeta().toGuest(name), mode);
//    // }
//    //
//    // @Intrinsic(hasReceiver = true)
//    // public static int read0(Object self) {
//    // RandomAccessFileFunctions.READ0.invoke();
//    // }
//    //
//    // @Intrinsic(hasReceiver = true)
//    // public static int readBytes(Object self, byte b[], int off, int len) {
//    // RandomAccessFileFunctions.READ_BYTES.invoke(b, )
//    // }
//    //
//    // @Intrinsic(hasReceiver = true)
//    // public static void write0(Object self, int b) throws IOException;
//    //
//    // @Intrinsic(hasReceiver = true)
//    // public static void writeBytes(Object self, byte b[], int off, int len) throws IOException;
//    //
//    // @Intrinsic(hasReceiver = true)
//    // public static void seek0(Object self, long pos) throws IOException;
//    //
//    // @Intrinsic(hasReceiver = true)
//    // public static void close0(Object self) throws IOException;
//    //
//    // @Intrinsic
//    // public static void initIDs() {
//    // /* nop */
//    // }
//    //
//    // @Substitute
//    // public int read() throws IOException {
//    // return PosixUtils.readSingle(fd);
//    // }
//    //
//    // @Substitute
//    // private int readBytes(byte[] b, int off, int len) throws IOException {
//    // return PosixUtils.readBytes(b, off, len, fd);
//    // }
//    //
//    // @Substitute
//    // public void write(int b) throws IOException {
//    // PosixUtils.writeSingle(fd, b, false);
//    // }
//    //
//    // @Substitute
//    // private void writeBytes(byte[] b, int off, int len) throws IOException {
//    // PosixUtils.writeBytes(fd, b, off, len, false);
//    // }
//    //
//    // @Substitute
//    // private void seek(long pos) throws IOException {
//    // int handle = PosixUtils.getFDHandle(fd);
//    // if (pos < 0L) {
//    // throw new IOException("Negative seek offset");
//    // } else if (lseek(handle, WordFactory.signed(pos), SEEK_SET()).equal(WordFactory.signed(-1)))
//    // {
//    // throw new IOException(PosixUtils.lastErrorString("Seek failed"));
//    // }
//    //
//    // }
//    //
//    // @Substitute
//    // public long getFilePointer() throws IOException {
//    // SignedWord ret;
//    // int handle = PosixUtils.getFDHandle(fd);
//    // if ((ret = lseek(handle, WordFactory.zero(), SEEK_CUR())).equal(WordFactory.signed(-1))) {
//    // throw new IOException(PosixUtils.lastErrorString("Seek failed"));
//    // }
//    // return ret.rawValue();
//    // }
//    //
//    // @Intrinsic(hasReceiver = true)
//    // private void open(Object self, String name, int mode) throws FileNotFoundException {
//    // new RandomAccessFile()
//    //
//    // }
//    //
//    // @Intrinsic(hasReceiver = true)
//    // private void close0(Object self) throws IOException {
//    //
//    // PosixUtils.fileClose(fd);
//    // }
//    //
//    // @Intrinsic(hasReceiver = true)
//    // public static long length() {
//    //
//    // }
//    //
//    // @Intrinsic(hasReceiver = true)
//    // public void setLength(long newLength) throws IOException {
//    // }
//
//}
