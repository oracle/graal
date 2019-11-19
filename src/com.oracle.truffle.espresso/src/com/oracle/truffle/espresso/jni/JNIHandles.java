package com.oracle.truffle.espresso.jni;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.function.Supplier;

import com.oracle.truffle.espresso.runtime.StaticObject;

// TODO(peterssen): Make (global handles) thread-safe. Add mechanism to "validate" local handles are not consumed by different threads.
//  e.g. taint handle upper bits with thread id/hashcode.
/**
 * JNI handles are divided in two categories: local and global.
 * <ul>
 * <li>All handles fit in an int
 * <li>Positive ints represent local handles
 * <li>0 is always NULL
 * <li>Negative ints (except {@link Integer#MIN_VALUE}) represent global handles
 * <li>{@link Integer#MIN_VALUE} is an invalid/poisoned handle
 * </ul>
 */
public final class JNIHandles {

    JNIHandles() {
        globals = new GlobalHandles();
    }

    /**
     * Minimum available local handles according to specification: "Before it enters a native
     * method, the VM automatically ensures that at least 16 local references can be created".
     */
    static final int NATIVE_CALL_MIN_LOCAL_HANDLE_CAPACITY = 16;

    static class LocalHandles {
        private static final int INITIAL_NUMBER_OF_FRAMES = 4; // TODO(peterssen): Minimum to run
                                                               // HelloWorld?

        private StaticObject[] objects = new StaticObject[NATIVE_CALL_MIN_LOCAL_HANDLE_CAPACITY];
        private int top = 1;

        private int[] frameStack = new int[INITIAL_NUMBER_OF_FRAMES];
        private int frameCount = 0;

        public int pushFrame(int capacity) {
            if (frameCount == frameStack.length) {
                int[] oldArray = frameStack;
                frameStack = new int[oldArray.length * 2];
                System.arraycopy(oldArray, 0, frameStack, 0, oldArray.length);
            }
            frameStack[frameCount] = top;
            frameCount++;
            ensureCapacity(capacity);
            return frameCount;
        }

        public void popFrame() {
            popFramesIncluding(frameCount);
        }

        public void popFramesIncluding(int frame) {
            assert frame > 0 && frame <= frameCount;
            int previousTop = top;
            frameCount = frame - 1;
            top = frameStack[frameCount];
            for (int i = top; i < previousTop; i++) {
                objects[i] = null; // so objects can be garbage collected
            }
        }

        // Not to confuse with EnsureLocalCapacity.
        public void ensureCapacity(int capacity) {
            if (top + capacity >= objects.length) {
                Object[] oldArray = objects;
                int newLength = oldArray.length * 2;
                assert newLength >= top + capacity;
                objects = new StaticObject[newLength];
                System.arraycopy(oldArray, 0, objects, 0, oldArray.length);
            }
        }

        public StaticObject get(int index) {
            if (index == 0) {
                return StaticObject.NULL;
            }
            // TODO(peterssen): Check handle is valid for the current frame (handle frame <= current
            // frame)
            return objects[index];
        }

        public int create(StaticObject obj) {
            if (StaticObject.isNull(obj)) {
                return 0;
            }
            ensureCapacity(1);
            int index = top;
            objects[index] = obj;
            top++;
            return index;
        }

        public void deleteLocalRef(int handle) {
            assert handle > 0;
            objects[handle] = null;
        }
    }

    public int nativeCallPrologue() {
        return getLocals().pushFrame(NATIVE_CALL_MIN_LOCAL_HANDLE_CAPACITY);
    }

    public void nativeCallEpilogue(int handleFrame) {
        getLocals().popFramesIncluding(handleFrame);
    }

    // TODO(peterssen): Use a free list to reclaim global unused slots, instead of growing forever.
    static class GlobalHandles {
        Object[] objects = new Object[2];
        int top = 1;

        public boolean destroy(int index) {
            if (index == 0) { // NULL
                return false;
            }
            Object previous = objects[index];
            objects[index] = null;
            return previous != null;
        }

        private synchronized int create(Object obj) {
            assert obj != null;
            assert obj instanceof StaticObject || obj instanceof WeakReference;
            if (top >= objects.length) {
                objects = Arrays.copyOf(objects, 2 * objects.length);
            }
            assert top > 0;

            int handle = top++;
            objects[handle] = obj;
            return handle;
        }

        public int createWeakGlobal(StaticObject obj) {
            if (StaticObject.isNull(obj)) {
                return 0; // NULL
            }
            return create(new WeakReference<>(obj));
        }

        public int create(StaticObject obj) {
            if (StaticObject.isNull(obj)) {
                return 0; // NULL
            }
            return create((Object) obj);
        }

        StaticObject get(int index) {
            if (index == 0) {
                return StaticObject.NULL;
            }

            assert index != Integer.MIN_VALUE : "invalid handle";

            Object obj = objects[index];

            // TODO(peterssen): StaticObject check is cheaper here.
            if (obj instanceof WeakReference) {
                Object referent = ((WeakReference<?>) obj).get();
                if (referent == null) { // referent is gone
                    return StaticObject.NULL;
                }
                return (StaticObject) referent;
            }

            assert obj != null; // Can be StaticObject.NULL, at best.
            return (StaticObject) obj;
        }

        public int getObjectRefType(int handle) {
            return objects[handle] instanceof WeakReference
                            ? JniEnv.JNIWeakGlobalRefType
                            : JniEnv.JNIGlobalRefType;
        }
    }

    private final ThreadLocal<LocalHandles> locals = ThreadLocal.withInitial(new Supplier<LocalHandles>() {
        @Override
        public LocalHandles get() {
            return new LocalHandles();
        }
    });

    private final GlobalHandles globals;

    LocalHandles getLocals() {
        return locals.get();
    }

    GlobalHandles getGlobals() {
        return globals;
    }

    public StaticObject get(int handle) {
        if (handle == 0) {
            return StaticObject.NULL;
        }
        if (handle > 0) {
            return getLocals().get(handle);
        }
        assert handle != Integer.MIN_VALUE;
        return getGlobals().get(-handle);
    }

    /**
     * Creates a local handle in the top frame. If obj is `null`, slot 0 is returned.
     */
    public int createLocal(StaticObject obj) {
        if (StaticObject.isNull(obj)) {
            return 0;
        }
        int handle = getLocals().create(obj);
        assert handle > 0;
        return handle;
    }

    /**
     * Creates a global handle. If obj is `null`, slot 0 is returned.
     */
    public int createGlobal(StaticObject obj) {
        if (StaticObject.isNull(obj)) {
            return 0;
        }
        // Global handles are negated.
        int handle = -getGlobals().create(obj);
        assert handle != Integer.MIN_VALUE;
        return handle;
    }

    /**
     * Creates a global weak handle, allowing the obj to be garbage collected. If obj is `null`,
     * slot 0 is returned.
     */
    public int createWeakGlobal(StaticObject obj) {
        if (StaticObject.isNull(obj)) {
            return 0;
        }
        // Global handles are negated.
        int handle = -getGlobals().createWeakGlobal(obj);
        assert handle != Integer.MIN_VALUE;
        return handle;
    }

    public void deleteLocalRef(int handle) {
        if (handle == 0) { // cannot delete null
            return;
        }
        assert handle > 0;
        getLocals().deleteLocalRef(handle);
    }

    public void deleteGlobalRef(int handle) {
        if (handle == 0) {
            return;
        }
        assert handle < 0;
        getGlobals().destroy(-handle);
    }

    int getObjectRefType(int handle) {
        if (handle < 0) {
            return getGlobals().getObjectRefType(-handle);
        }
        if (handle > 0) {
            return JniEnv.JNILocalRefType;
        }
        // 0 (NULL) is invalid
        return JniEnv.JNIInvalidRefType;
    }

    public void popFramesIncluding(int frame) {
        getLocals().popFramesIncluding(frame);
    }

    public int pushFrame(int capacity) {
        return getLocals().pushFrame(capacity);
    }

    public int pushFrame() {
        return pushFrame(NATIVE_CALL_MIN_LOCAL_HANDLE_CAPACITY);
    }
}
