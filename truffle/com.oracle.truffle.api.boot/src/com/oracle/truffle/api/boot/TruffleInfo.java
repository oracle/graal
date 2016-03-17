package com.oracle.truffle.api.boot;

/**
 * Provides informations about Truffle runtime to implementation of {@link TruffleServices}. Obtain
 * via {@link TruffleServices#info()}.
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

    /**
     * Restricted constructor. Available only to selected subclasses.
     * 
     * @throws IllegalStateException usually throws an exception
     */
    protected TruffleInfo() {
        if (DEFAULT != null || !getClass().getName().equals("com.oracle.truffle.api.impl.TruffleInfoImpl")) {
            throw new IllegalStateException();
        }
        DEFAULT = this;
    }

    /**
     * Allows the Truffle API to access services of the runtime. For example
     * {@link LoopCountSupport}, etc.
     *
     * @param <T> the requested type
     * @param type class of the request service
     * @return instance of the requested type or <code>null</code> if none has been found
     */
    @SuppressWarnings({"unchecked", "static-method"})
    protected final <T> T lookup(Class<T> type) {
        if (type == LoopCountSupport.class) {
            return (T) TruffleServices.DEFAULT.loopCount();
        }
        return null;
    }

    public abstract Class<?> findLanguage(Object node);

    public abstract void initializeCallTarget(Object callTarget);
}
