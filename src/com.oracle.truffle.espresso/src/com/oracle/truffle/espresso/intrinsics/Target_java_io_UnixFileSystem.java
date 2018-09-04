package com.oracle.truffle.espresso.intrinsics;

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoIntrinsics
public class Target_java_io_UnixFileSystem {

    enum UnixFileSystemFunctions {
        GET_BOOLEAN_ATTRIBUTES0("getBooleanAttributes0", File.class),
        GET_LAST_MODIFIED_TIME("getLastModifiedTime", File.class);

        UnixFileSystemFunctions(String name, Class<?>... parameterTypes) {
            try {
                this.method = getUnixFs().getClass().getMethod(name, parameterTypes);
                this.method.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        public Method getMethod() {
            return method;
        }

        // TODO(peterssen): Warning, the UnixFileSystem instance is cached.
        public Object invoke(Object... args) {
            try {
                return getMethod().invoke(getUnixFs(), args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        private static Object getUnixFs() {
            if (unixFs == null) {
                try {
                    Field fs = File.class.getDeclaredField("fs");
                    fs.setAccessible(true);
                    unixFs = fs.get(null);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            return unixFs;
        }

        private static Object unixFs;

        private Method method;
    }

    @Intrinsic
    public static void initIDs() {
        /* nop */
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(String.class) StaticObject canonicalize0(Object self, @Type(String.class) StaticObject path) {
        // TODO(peterssen): Implement path canonicalization.
        return path;
    }

    @Intrinsic(hasReceiver = true)
    public static int getBooleanAttributes0(Object self, @Type(File.class) StaticObject f) {
        return (int) UnixFileSystemFunctions.GET_BOOLEAN_ATTRIBUTES0.invoke(toHostFile(f));
    }

    @Intrinsic(hasReceiver = true)
    public static long getLastModifiedTime(Object self, @Type(File.class) StaticObject f) {
        return (long) UnixFileSystemFunctions.GET_LAST_MODIFIED_TIME.invoke(toHostFile(f));
    }

    private static File toHostFile(StaticObject f) {
        String path = Meta.toHost((StaticObject) meta(f).method("getPath", String.class).invokeDirect());
        assert path != null;
        return new File(path);
    }
}
