package com.oracle.truffle.espresso.intrinsics;

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoIntrinsics
public class Target_sun_nio_fs_UnixNativeDispatcher {

    @Surrogate("sun.nio.fs.UnixFileAttributes")
    private static class UnixFileAttributes {
        static Class<?> CLASS;
        static Constructor<?> CONSTRUCTOR;
        static {
            try {
                CLASS = Class.forName("sun.nio.fs.UnixFileAttributes");
                CONSTRUCTOR = CLASS.getDeclaredConstructor();
                CONSTRUCTOR.setAccessible(true);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        static Object newInstance() {
            try {
                return CONSTRUCTOR.newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        static void copyTo(Object host, StaticObject guest) {
            assert host.getClass() == CLASS;
            Meta.Klass.WithInstance target = meta(guest);
            for (Field f : CLASS.getDeclaredFields()) {
                // Copy only primitive fields.
                if (f.getType().isPrimitive()) {
                    try {
                        f.setAccessible(true);
                        target.field(f.getName()).set(f.get(host));
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Surrogate("sun.nio.fs.UnixFileStoreAttributes")
    private static class UnixFileStoreAttributes {
        static Class<?> CLASS;
        static Constructor<?> CONSTRUCTOR;
        static {
            try {
                CLASS = Class.forName("sun.nio.fs.UnixFileStoreAttributes");
                CONSTRUCTOR = CLASS.getDeclaredConstructor();
                CONSTRUCTOR.setAccessible(true);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        static Object newInstance() {
            try {
                return CONSTRUCTOR.newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        static void copyTo(Object host, StaticObject guest) {
            assert host.getClass() == CLASS;
            Meta.Klass.WithInstance target = meta(guest);
            for (Field f : CLASS.getDeclaredFields()) {
                assert f.getType().isPrimitive();
                try {
                    f.setAccessible(true);
                    target.field(f.getName()).set(f.get(host));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    enum UnixNativeDispatcherFunctions {

        GETCWD("getcwd"),
        DUP("dup", int.class),
        OPEN0("open0", long.class, int.class, int.class),
        OPENAT0("openat0", int.class, long.class, int.class, int.class),
        CLOSE("close", int.class),
        FOPEN0("fopen0", long.class, long.class),
        FCLOSE("fclose", long.class),
        LINK0("link0", long.class, long.class),
        UNLINK0("unlink0", long.class),
        UNLINKAT0("unlinkat0", int.class, long.class, int.class),
        MKNOD0("mknod0", long.class, int.class, long.class),
        RENAME0("rename0", long.class, long.class),
        RENAMEAT0("renameat0", int.class, long.class, int.class, long.class),
        MKDIR0("mkdir0", long.class, int.class),
        RMDIR0("rmdir0", long.class),
        READLINK0("readlink0", long.class),
        REALPATH0("realpath0", long.class),
        SYMLINK0("symlink0", long.class, long.class),
        STAT0("stat0", long.class, UnixFileAttributes.class),
        LSTAT0("lstat0", long.class, UnixFileAttributes.class),
        FSTAT("fstat", int.class, UnixFileAttributes.class),
        FSTATAT0("fstatat0", int.class, long.class, int.class, UnixFileAttributes.class),
        CHOWN0("chown0", long.class, int.class, int.class),
        LCHOWN0("lchown0", long.class, int.class, int.class),
        FCHOWN("fchown", int.class, int.class, int.class),
        CHMOD0("chmod0", long.class, int.class),
        FCHMOD("fchmod", int.class, int.class),
        UTIMES0("utimes0", long.class, long.class, long.class),
        FUTIMES("futimes", int.class, long.class, long.class),
        OPENDIR0("opendir0", long.class),
        FDOPENDIR("fdopendir", int.class),
        CLOSEDIR("closedir", long.class),
        READDIR("readdir", long.class),
        READ("read", int.class, long.class, int.class),
        WRITE("write", int.class, long.class, int.class),
        ACCESS0("access0", long.class, int.class),
        GETPWUID("getpwuid", int.class),
        GETGRGID("getgrgid", int.class),
        GETPWNAM0("getpwnam0", long.class),
        GETGRNAM0("getgrnam0", long.class),
        STATVFS0("statvfs0", long.class, UnixFileStoreAttributes.class),
        PATHCONF0("pathconf0", long.class, int.class),
        FPATHCONF("fpathconf", int.class, int.class),
        STRERROR("strerror", int.class),
        INIT("init");

        UnixNativeDispatcherFunctions(String name, Class<?>... parameterTypes) {
            try {
                Class<?> clazz = Class.forName("sun.nio.fs.UnixNativeDispatcher");
                Class<?>[] filteredParams = Stream.of(parameterTypes).map(c -> {
                            Surrogate surrogate = c.getAnnotation(Surrogate.class);
                            if (surrogate == null) {
                                return c;
                            }
                            try {
                                return Class.forName(surrogate.value());
                            } catch (ClassNotFoundException e) {
                                throw new RuntimeException(e);
                            }
                        }).toArray(Class<?>[]::new);
                this.method = clazz.getDeclaredMethod(name, filteredParams);
                this.method.setAccessible(true);
            } catch (NoSuchMethodException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        private final Method method;

        public Method getMethod() {
            return method;
        }

        public Object invokeStatic(Object... args) {
            try {
                return getMethod().invoke(null, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Intrinsic
    public static byte[] getcwd() {
        return (byte[]) UnixNativeDispatcherFunctions.GETCWD.invokeStatic();
    }

    @Intrinsic
    public static int dup(int var0) { // throws UnixException;
        return (int) UnixNativeDispatcherFunctions.DUP.invokeStatic(var0);
    }

    @Intrinsic
    public static int open0(long var0, int var2, int var3) { // throws UnixException;
        return (int) UnixNativeDispatcherFunctions.OPEN0.invokeStatic(var0, var2, var3);
    }

    @Intrinsic
    public static int openat0(int var0, long var1, int var3, int var4) { // throws UnixException;
        return (int) UnixNativeDispatcherFunctions.OPENAT0.invokeStatic(var0, var1, var3, var3);
    }

    @Intrinsic
    public static void close(int var0) {
        UnixNativeDispatcherFunctions.CLOSE.invokeStatic(var0);
    }

    @Intrinsic
    public static long fopen0(long var0, long var2) { // throws UnixException;
        return (long) UnixNativeDispatcherFunctions.FOPEN0.invokeStatic(var0, var2);
    }

    @Intrinsic
    public static void fclose(long var0) { // throws UnixException;
        UnixNativeDispatcherFunctions.FCLOSE.invokeStatic(var0);
    }

    @Intrinsic
    public static void link0(long var0, long var2) { // throws UnixException;
        UnixNativeDispatcherFunctions.LINK0.invokeStatic(var0, var2);
    }

    @Intrinsic
    public static void unlink0(long var0) { // throws UnixException;
        UnixNativeDispatcherFunctions.UNLINK0.invokeStatic(var0);
    }

    @Intrinsic
    public static void unlinkat0(int var0, long var1, int var3) { // throws UnixException;
        UnixNativeDispatcherFunctions.UNLINKAT0.invokeStatic(var0, var1, var3);
    }

    @Intrinsic
    public static void mknod0(long var0, int var2, long var3) { // throws UnixException;
        UnixNativeDispatcherFunctions.MKNOD0.invokeStatic(var0, var2, var3);
    }

    @Intrinsic
    public static void rename0(long var0, long var2) { // throws UnixException;
        UnixNativeDispatcherFunctions.RENAME0.invokeStatic(var0, var2);
    }

    @Intrinsic
    public static void renameat0(int var0, long var1, int var3, long var4) { // throws
                                                                             // UnixException;
        UnixNativeDispatcherFunctions.RENAMEAT0.invokeStatic(var0, var1, var3, var4);
    }

    @Intrinsic
    public static void mkdir0(long var0, int var2) { // throws UnixException;
        UnixNativeDispatcherFunctions.MKDIR0.invokeStatic(var0, var2);
    }

    @Intrinsic
    public static void rmdir0(long var0) { // throws UnixException;
        UnixNativeDispatcherFunctions.RMDIR0.invokeStatic(var0);
    }

    @Intrinsic
    public static byte[] readlink0(long var0) { // throws UnixException;
        return (byte[]) UnixNativeDispatcherFunctions.READLINK0.invokeStatic(var0);
    }

    @Intrinsic
    public static byte[] realpath0(long var0) { // throws UnixException;
        return (byte[]) UnixNativeDispatcherFunctions.REALPATH0.invokeStatic(var0);
    }

    @Intrinsic
    public static void symlink0(long var0, long var2) { // throws UnixException;
        UnixNativeDispatcherFunctions.SYMLINK0.invokeStatic(var0, var2);
    }

    @Intrinsic
    public static void stat0(long var0, @Type(UnixFileAttributes.class) StaticObject var2) { // throws
                                                                                             // UnixException;
        Object host = UnixFileAttributes.newInstance();
        UnixNativeDispatcherFunctions.STAT0.invokeStatic(var0, host);
        UnixFileAttributes.copyTo(host, var2);
    }

    @Intrinsic
    public static void lstat0(long var0, @Type(UnixFileAttributes.class) StaticObject var2) { // throws
                                                                                              // UnixException;
        Object host = UnixFileAttributes.newInstance();
        UnixNativeDispatcherFunctions.LSTAT0.invokeStatic(var0, host);
        UnixFileAttributes.copyTo(host, var2);
    }

    @Intrinsic
    public static void fstat(int var0, @Type(UnixFileAttributes.class) StaticObject var1) { // throws
                                                                                            // UnixException;
        Object host = UnixFileAttributes.newInstance();
        UnixNativeDispatcherFunctions.FSTAT.invokeStatic(var0, host);
        UnixFileAttributes.copyTo(host, var1);
    }

    @Intrinsic
    public static void fstatat0(int var0, long var1, int var3, @Type(UnixFileAttributes.class) StaticObject var4) { // throws
                                                                                                                    // UnixException;
        Object host = UnixFileAttributes.newInstance();
        UnixNativeDispatcherFunctions.FSTATAT0.invokeStatic(var0, var1, var3, host);
        UnixFileAttributes.copyTo(host, var4);

    }

    @Intrinsic
    public static void chown0(long var0, int var2, int var3) { // throws UnixException;
        UnixNativeDispatcherFunctions.CHOWN0.invokeStatic(var0, var2, var3);
    }

    @Intrinsic
    public static void lchown0(long var0, int var2, int var3) { // throws UnixException;
        UnixNativeDispatcherFunctions.LCHOWN0.invokeStatic(var0, var2, var3);
    }

    @Intrinsic
    public static void fchown(int var0, int var1, int var2) { // throws UnixException;
        UnixNativeDispatcherFunctions.FCHOWN.invokeStatic(var0, var1, var2);
    }

    @Intrinsic
    public static void chmod0(long var0, int var2) { // throws UnixException;
        UnixNativeDispatcherFunctions.CHMOD0.invokeStatic(var0, var2);
    }

    @Intrinsic
    public static void fchmod(int var0, int var1) { // throws UnixException;
        UnixNativeDispatcherFunctions.FCHMOD.invokeStatic(var0, var1);
    }

    @Intrinsic
    public static void utimes0(long var0, long var2, long var4) { // throws UnixException;
        UnixNativeDispatcherFunctions.UTIMES0.invokeStatic(var0, var2, var4);
    }

    @Intrinsic
    public static void futimes(int var0, long var1, long var3) { // throws UnixException;
        UnixNativeDispatcherFunctions.FUTIMES.invokeStatic(var0, var1, var3);
    }

    @Intrinsic
    public static long opendir0(long var0) { // throws UnixException;
        return (long) UnixNativeDispatcherFunctions.OPENDIR0.invokeStatic(var0);
    }

    @Intrinsic
    public static long fdopendir(int var0) { // throws UnixException;
        return (long) UnixNativeDispatcherFunctions.FDOPENDIR.invokeStatic(var0);
    }

    @Intrinsic
    public static void closedir(long var0) { // throws UnixException;
        UnixNativeDispatcherFunctions.CLOSEDIR.invokeStatic(var0);
    }

    @Intrinsic
    public static byte[] readdir(long var0) { // throws UnixException;
        return (byte[]) UnixNativeDispatcherFunctions.READDIR.invokeStatic(var0);
    }

    @Intrinsic
    public static int read(int var0, long var1, int var3) { // throws UnixException;
        return (int) UnixNativeDispatcherFunctions.READ.invokeStatic(var0, var1, var3);
    }

    @Intrinsic
    public static int write(int var0, long var1, int var3) { // throws UnixException;
        return (int) UnixNativeDispatcherFunctions.WRITE.invokeStatic(var0, var1, var3);
    }

    @Intrinsic
    public static void access0(long var0, int var2) { // throws UnixException;
        UnixNativeDispatcherFunctions.ACCESS0.invokeStatic(var0, var2);
    }

    @Intrinsic
    public static byte[] getpwuid(int var0) { // throws UnixException;
        return (byte[]) UnixNativeDispatcherFunctions.GETPWUID.invokeStatic(var0);
    }

    @Intrinsic
    public static byte[] getgrgid(int var0) { // throws UnixException;
        return (byte[]) UnixNativeDispatcherFunctions.GETGRGID.invokeStatic(var0);
    }

    @Intrinsic
    public static int getpwnam0(long var0) { // throws UnixException;
        return (int) UnixNativeDispatcherFunctions.GETPWNAM0.invokeStatic(var0);
    }

    @Intrinsic
    public static int getgrnam0(long var0) { // throws UnixException;
        return (int) UnixNativeDispatcherFunctions.GETGRNAM0.invokeStatic(var0);
    }

    @Intrinsic
    public static void statvfs0(long var0, @Type(UnixFileStoreAttributes.class) StaticObject var2) { // throws
                                                                                                     // UnixException;
        Object host = UnixFileStoreAttributes.newInstance();
        UnixNativeDispatcherFunctions.STATVFS0.invokeStatic(var0, host);
        UnixFileStoreAttributes.copyTo(host, var2);
    }

    @Intrinsic
    public static long pathconf0(long var0, int var2) { // throws UnixException;
        return (long) UnixNativeDispatcherFunctions.PATHCONF0.invokeStatic(var0, var2);
    }

    @Intrinsic
    public static long fpathconf(int var0, int var1) { // throws UnixException;
        return (long) UnixNativeDispatcherFunctions.FPATHCONF.invokeStatic(var0, var1);
    }

    @Intrinsic
    public static byte[] strerror(int var0) {
        return (byte[]) UnixNativeDispatcherFunctions.STRERROR.invokeStatic(var0);
    }

    @Intrinsic
    public static int init() {
        return (int) UnixNativeDispatcherFunctions.INIT.invokeStatic();
    }
}
