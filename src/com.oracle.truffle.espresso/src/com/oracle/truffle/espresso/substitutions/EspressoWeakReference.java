package com.oracle.truffle.espresso.substitutions;

import java.lang.ref.PhantomReference;
import java.lang.ref.PublicFinalReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import com.oracle.truffle.espresso.runtime.StaticObject;

public final class EspressoWeakReference<T> extends WeakReference<T> implements ReferenceWrapper {

    private final StaticObject guestReference;

    public EspressoWeakReference(StaticObject guestReference, T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
        this.guestReference = guestReference;
    }

    @Override
    public StaticObject getGuestReference() {
        return guestReference;
    }
}

class EspressoSoftReference<T> extends SoftReference<T> implements ReferenceWrapper {

    private final StaticObject guestReference;

    public EspressoSoftReference(StaticObject guestReference, T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
        this.guestReference = guestReference;
    }

    @Override
    public StaticObject getGuestReference() {
        return guestReference;
    }
}

class EspressoPhantomReference<T> extends PhantomReference<T> implements ReferenceWrapper {

    private final StaticObject guestReference;

    public EspressoPhantomReference(StaticObject guestReference, T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
        this.guestReference = guestReference;
    }

    @Override
    public StaticObject getGuestReference() {
        return guestReference;
    }
}

/**
 * PublicFinalReference defined early via Unsafe, on the the boot class loader.
 */
class EspressoFinalReference<T> extends PublicFinalReference<T> implements ReferenceWrapper {

    private final StaticObject guestReference;

    public EspressoFinalReference(StaticObject guestReference, T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
        this.guestReference = guestReference;
    }

    @Override
    public StaticObject getGuestReference() {
        return guestReference;
    }
}
