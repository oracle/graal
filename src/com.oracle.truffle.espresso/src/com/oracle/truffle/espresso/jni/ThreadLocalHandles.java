package com.oracle.truffle.espresso.jni;

/**
 * Implementation of local object handles, which are bound to a specific thread and can be created
 * and destroyed implicitly or explicitly. Local handles can be managed in frames and a frame can be
 * discarded in its entirety.
 */
public final class ThreadLocalHandles {
    private static final int INITIAL_NUMBER_OF_FRAMES = 4;

    public static final int MIN_VALUE = Math.toIntExact(1 + nullHandle());
    public static final int MAX_VALUE = Integer.MAX_VALUE;

    public static int nullHandle() {
        return 0;
    }

    public static boolean isInRange(int handle) {
        return handle >= MIN_VALUE && handle <= MAX_VALUE;
    }

    private Object[] objects;
    private int top = MIN_VALUE;

    private int[] frameStack = new int[INITIAL_NUMBER_OF_FRAMES];
    private int frameCount = 0;

    public ThreadLocalHandles(int initialNumberOfHandles) {
        objects = new Object[MIN_VALUE + initialNumberOfHandles];
    }

    private static int toIndex(int handle) {
        return (int) handle;
    }

    public int getHandleCount() {
        return top - MIN_VALUE;
    }

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

    @SuppressWarnings("unchecked")
    public int create(Object obj) {
        if (obj == null) {
            return (int) nullHandle();
        }
        ensureCapacity(1);
        int index = top;
        objects[index] = obj;
        top++;
        return index;
    }

    @SuppressWarnings("unchecked")
    public <U> U getObject(int handle) {
        return (U) objects[toIndex(handle)];
    }

    public boolean delete(int handle) {
        int index = toIndex(handle);
        Object previous = objects[index];
        objects[index] = null;
        return previous != null;
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

    public void ensureCapacity(int capacity) {
        while (top + capacity >= objects.length) {
            Object[] oldArray = objects;
            int newLength = oldArray.length * 2;
            assert newLength >= top + capacity;
            objects = new Object[newLength];
            System.arraycopy(oldArray, 0, objects, 0, oldArray.length);
        }
    }
}
