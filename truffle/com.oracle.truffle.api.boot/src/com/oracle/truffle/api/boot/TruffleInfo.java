package com.oracle.truffle.api.boot;

/** Provides informations about Truffle runtime to
 * implementation of {@link TruffleServices}. Obtain via {@link TruffleServices#info()}.
 */
public abstract class TruffleInfo {
    static TruffleInfo DEFAULT;
    static {
        try {
            Class.forName("com.oracle.truffle.api.impl.Accessor", true, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Restricted constructor. Available only to selected subclasses.
     * @throws IllegalStateException usually throws an exception
     */
    protected TruffleInfo() {
        if (DEFAULT != null) {
            throw new IllegalStateException();
        }
        DEFAULT = this;
    }

    protected final <T> T lookup(Class<T> type) {
        if (type == LoopCountSupport.class) {
            return (T) TruffleServices.DEFAULT.loopCount();
        }
        return null;
    }

    public abstract Class<?> findLanguage(Object node);
    public abstract void initializeCallTarget(Object callTarget);
}
