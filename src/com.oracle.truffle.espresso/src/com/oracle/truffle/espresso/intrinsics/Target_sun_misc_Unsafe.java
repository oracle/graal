package com.oracle.truffle.espresso.intrinsics;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.runtime.Utils;

import sun.misc.Unsafe;

import static com.oracle.truffle.espresso.meta.Meta.meta;

@EspressoIntrinsics
public class Target_sun_misc_Unsafe {

    private static final long SAFETY_FIELD_OFFSET = 123456789L;

    private static Unsafe hostUnsafe;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            hostUnsafe = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Intrinsic(hasReceiver = true)
    public static int arrayBaseOffset(Object self, @Type(Class.class) StaticObjectClass clazz) {
        assert clazz.getMirror().isArray();
        if (clazz.getMirror().getComponentType().isPrimitive()) {
            Class<?> hostPrimitive = clazz.getMirror().getComponentType().getJavaKind().toJavaClass();
            return hostUnsafe.arrayBaseOffset(Array.newInstance(hostPrimitive, 0).getClass());
        } else {
            // Just a reference type.
            return hostUnsafe.arrayBaseOffset(Object[].class);
        }
    }

    @Intrinsic(hasReceiver = true)
    public static int arrayIndexScale(Object self, @Type(Class.class) StaticObjectClass clazz) {
        assert clazz.getMirror().isArray();
        if (clazz.getMirror().getComponentType().isPrimitive()) {
            Class<?> hostPrimitive = clazz.getMirror().getComponentType().getJavaKind().toJavaClass();
            return hostUnsafe.arrayIndexScale(Array.newInstance(hostPrimitive, 0).getClass());
        } else {
            // Just a reference type.
            return hostUnsafe.arrayIndexScale(Object[].class);
        }
    }

    @Intrinsic(hasReceiver = true)
    public static int addressSize(Object self) {
        // TODO(peterssen): Use host address size.
        return 4;
    }

    @Intrinsic(hasReceiver = true)
    public static long objectFieldOffset(Object self, @Type(Field.class) StaticObjectImpl field) {
        return SAFETY_FIELD_OFFSET + (int) meta(field).field("slot").get();
    }

    @Intrinsic(hasReceiver = true)
    public static final boolean compareAndSwapObject(Object self, @Type(Object.class) StaticObject holder, long offset, Object before, Object after) {
        if (holder instanceof StaticObjectArray) {
            return hostUnsafe.compareAndSwapObject(((StaticObjectArray) holder).getWrapped(), offset, before, after);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, offset <-> field index.
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        FieldInfo[] fields = holder.getKlass().getDeclaredFields();
        FieldInfo f = fields[(int) (offset - SAFETY_FIELD_OFFSET)];

        Object inTheField = meta(f).get(holder);
        if (inTheField == before) {
            meta(f).set(holder, after);
            return true;
        } else {
            return false;
        }
    }

    @Intrinsic(hasReceiver = true)
    public static int getIntVolatile(Object unsafe, @Type(Object.class) StaticObject holder, long offset) {
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        FieldInfo[] fields = holder.getKlass().getDeclaredFields();
        FieldInfo f = fields[(int) (offset - SAFETY_FIELD_OFFSET)];
        return (int) meta(f).get(holder);
    }

    @Intrinsic(hasReceiver = true)
    public static boolean compareAndSwapInt(Object self, @Type(Object.class) StaticObject holder, long offset, int before, int after) {
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        FieldInfo[] fields = holder.getKlass().getDeclaredFields();
        FieldInfo f = fields[(int) (offset - SAFETY_FIELD_OFFSET)];
        int inTheField = (int) meta(f).get(holder);
        if (inTheField == before) {
            meta(f).set(holder, after);
            return true;
        } else {
            return false;
        }
    }

    @Intrinsic(hasReceiver = true)
    public static boolean compareAndSwapLong(Object self, @Type(Object.class) StaticObject holder, long offset, long before, long after) {
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        FieldInfo[] fields = holder.getKlass().getDeclaredFields();
        FieldInfo f = fields[(int) (offset - SAFETY_FIELD_OFFSET)];
        long inTheField = (long) meta(f).get(holder);
        if (inTheField == before) {
            meta(f).set(holder, after);
            return true;
        } else {
            return false;
        }
    }

    @Intrinsic
    public static void registerNatives() {
        /* nop */
    }

    @Intrinsic(hasReceiver = true)
    public static long allocateMemory(Object self, long length) {
        return hostUnsafe.allocateMemory(length);
    }

    @Intrinsic(hasReceiver = true)
    public static void freeMemory(Object self, long address) {
        hostUnsafe.freeMemory(address);
    }

    @Intrinsic(hasReceiver = true)
    public static void putLong(Object self, Object holder, long offset, long x) {
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        FieldInfo[] fields = ((StaticObject) holder).getKlass().getDeclaredFields();
        FieldInfo f = fields[(int) (offset - SAFETY_FIELD_OFFSET)];
        Utils.getVm().setFieldLong(x, (StaticObject) holder, f);
    }

    @Intrinsic(hasReceiver = true)
    public static void putLong(Object self, long offset, long x) {
        hostUnsafe.putLong(offset, x);
    }

    @Intrinsic(hasReceiver = true)
    public static byte getByte(Object self, Object holder, long offset) {
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        FieldInfo[] fields = ((StaticObject) holder).getKlass().getDeclaredFields();
        FieldInfo f = fields[(int) (offset - SAFETY_FIELD_OFFSET)];
        return Utils.getVm().getFieldByte((StaticObject) holder, f);
    }

    @Intrinsic(hasReceiver = true)
    public static byte getByte(Object self, long offset) {
        return hostUnsafe.getByte(offset);
    }

    @Intrinsic(hasReceiver = true)
    public static Object getObjectVolatile(Object self, Object holder, long offset) {
        if (holder instanceof StaticObjectArray) {
            return hostUnsafe.getObjectVolatile(((StaticObjectArray) holder).getWrapped(), offset);
        }
        // TODO(peterssen): Current workaround assumes it's a field access, encoding is offset <->
        // field index.
        // TODO(peterssen): Use holder.getKlass().findInstanceFieldWithOffset
        FieldInfo[] fields = ((StaticObject) holder).getKlass().getDeclaredFields();
        FieldInfo f = fields[(int) (offset - SAFETY_FIELD_OFFSET)];
        return Utils.getVm().getFieldObject((StaticObject) holder, f);
    }

    @Intrinsic(hasReceiver = true)
    public static void ensureClassInitialized(Object self, @Type(Class.class) StaticObjectClass clazz) {
        clazz.getMirror().initialize();
    }
}
