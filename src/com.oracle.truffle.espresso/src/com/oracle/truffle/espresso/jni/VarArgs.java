package com.oracle.truffle.espresso.jni;

// TODO(peterssen): nespresso pop* bindings must be through the guest TruffleNFI not the host.
public abstract class VarArgs {
    native static boolean popBoolean(long nativePointer);
    native static byte popByte(long nativePointer);
    native static char popChar(long nativePointer);
    native static short popShort(long nativePointer);
    native static int popInt(long nativePointer);
    native static float popFloat(long nativePointer);
    native static double popDouble(long nativePointer);
    native static long popLong(long nativePointer);
    native static Object popObject(long nativePointer);

    public abstract boolean popBoolean();
    public abstract byte popByte();
    public abstract char popChar();
    public abstract short popShort();
    public abstract int popInt();
    public abstract float popFloat();
    public abstract double popDouble();
    public abstract long popLong();
    public abstract Object popObject();

    private static class Instance extends VarArgs {
        final long nativePointer;
        public Instance(long nativePointer) {
            this.nativePointer = nativePointer;
        }
        public boolean popBoolean() {
            return VarArgs.popBoolean(nativePointer);
        }
        public byte popByte() {
            return VarArgs.popByte(nativePointer);
        }
        public char popChar() {
            return VarArgs.popChar(nativePointer);
        }
        public short popShort() {
            return VarArgs.popShort(nativePointer);
        }
        public int popInt() {
            return VarArgs.popInt(nativePointer);
        }
        public float popFloat() {
            return VarArgs.popFloat(nativePointer);
        }
        public double popDouble() {
            return VarArgs.popDouble(nativePointer);
        }
        public long popLong() {
            return VarArgs.popLong(nativePointer);
        }
        public Object popObject() {
            return VarArgs.popObject(nativePointer);
        }
    }
    public static VarArgs init(long nativePointer) {
        return new VarArgs.Instance(nativePointer);
    }
}
