package com.oracle.truffle.api.boot;

/** Runtime support for the execution of Truffle interpreters. Contains
 * various method to obtain support for specific areas. These methods may
 * return <code>null</code> if particular feature of the runtime isn't
 * supported.
 * <p>
 * There are two known implementations of the runtime. The default, fallback
 * one, provided by the <em>Truffle</em> API itself. The <em>optimized one</em>
 * provided by the <em>Graal virtual machine</em>. When evolving this
 * interface it is essential to do so with care and respect to these two
 * implementations. Keep in mind, that different version of <em>Truffle</em>
 * API may be executed against different version of <em>Graal</em>.
 *
 * @since 0.12
 */
public abstract class TruffleServices {
    static TruffleServices DEFAULT;
    private final String name;

    /** Constructor for subclasses.
     * @param name simplified, programmatic name of this implementation
     *   to be returned from {@link #getName()}
     * @since 0.12 
     */
    protected TruffleServices(String name) {
        this.name = name;
        if (DEFAULT != null) {
            //throw new IllegalStateException();
        }
        DEFAULT = this;
    }

    /** Obtains programmatic name of this implementation.
     *
     * @return the name identifying this implementation
     */
    public final String getName() {
        return name;
    }

    /** Provides support for additional optimizations of loops.
     *
     * @return <code>null</code> by default, override to return something
     *   meaningful
     * @since 0.12
     */
    protected LoopCountSupport<?> loopCount() {
        return null;
    }

    /** Information about truffle API useful for runtime support.
     * @return instance of the info class, never <code>null</code>
     * @since 0.12
     */
    protected final TruffleInfo info() {
        return TruffleInfo.DEFAULT;
    }
}
