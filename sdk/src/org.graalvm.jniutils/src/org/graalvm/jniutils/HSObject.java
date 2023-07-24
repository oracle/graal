/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.jniutils;

import static org.graalvm.jniutils.JNIUtil.DeleteGlobalRef;
import static org.graalvm.jniutils.JNIUtil.DeleteWeakGlobalRef;
import static org.graalvm.jniutils.JNIUtil.NewGlobalRef;
import static org.graalvm.jniutils.JNIUtil.NewWeakGlobalRef;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JWeak;
import org.graalvm.word.WordFactory;

/**
 * Encapsulates a JNI handle to an object in the HotSpot heap. Depending on which constructor is
 * used, the handle is either local to a {@link JNIMethodScope} and thus invalid once the scope
 * exits or a global JNI handle that is only released sometime after the {@link HSObject} dies.
 */
public class HSObject {

    /**
     * JNI handle to the HotSpot object.
     */
    private final JObject handle;

    /**
     * Cleaner for Global Reference.
     */
    private final Cleaner cleaner;

    /**
     * Link to next the next scope local object. The head of the list is in
     * {@link JNIMethodScope#locals}. The handle of a scope local object is only valid for the
     * lifetime of a {@link JNIMethodScope}. A self-reference (i.e. {@code this.next == this})
     * denotes an object whose {@link JNIMethodScope} has closed.
     *
     * This field is {@code null} for a non-scope local object.
     */
    private HSObject next;

    /**
     * Creates an object encapsulating a {@code handle} whose lifetime is determined by this object.
     * The created {@link HSObject} uses a JNI global reference and does not allow duplicate JNI
     * references. Use {@link #HSObject(JNIEnv, JObject, boolean, boolean)} to create
     * {@link HSObject} using a JNI weak reference or allowing duplicate JNI references.
     */
    public HSObject(JNIEnv env, JObject handle) {
        this(env, handle, false, false);
    }

    /**
     * Creates an object encapsulating a {@code handle} whose lifetime is determined by this object.
     * The created {@link HSObject} possibly allows duplicate JNI global handles.
     */
    public HSObject(JNIEnv env, JObject handle, boolean allowGlobalDuplicates, boolean weak) {
        cleanHandles(env);
        if (checkingGlobalDuplicates(allowGlobalDuplicates)) {
            checkNonExistingGlobalReference(env, handle);
        }
        String name = this.getClass().getName();
        this.handle = weak ? NewWeakGlobalRef(env, handle, name) : NewGlobalRef(env, handle, name);
        cleaner = new Cleaner(this, this.handle, allowGlobalDuplicates, weak);
        CLEANERS.add(cleaner);
        next = null;
    }

    private static boolean checkingGlobalDuplicates(boolean allowGlobalDuplicates) {
        return !allowGlobalDuplicates && (assertionsEnabled() || JNIUtil.tracingAt(1));
    }

    /**
     * Creates an object encapsulating a {@code handle} whose lifetime is limited to {@code scope}.
     * Once {@code scope.close()} is called, any attempt to {@linkplain #getHandle() use} the handle
     * will result in an {@link IllegalArgumentException}.
     */
    public HSObject(JNIMethodScope scope, JObject handle) {
        this.handle = handle;
        next = scope.locals;
        scope.locals = this;
        cleaner = null;
    }

    /**
     * Invalidates the objects in the list starting at {@code head} such that subsequently calling
     * {@link #getHandle()} on any of the objects results in an {@link IllegalArgumentException}.
     *
     * @return the number of objects in the list
     */
    static int invalidate(HSObject head) {
        HSObject o = head;
        int count = 0;
        while (o != null) {
            HSObject next = o.next;
            // Makes the handle now invalid.
            o.next = o;
            o = next;
            count++;
        }
        return count;
    }

    public final JObject getHandle() {
        if (next == this) {
            throw new IllegalArgumentException("Reclaimed JNI reference: " + this);
        }
        return handle;
    }

    @Override
    public String toString() {
        return String.format("%s[0x%x]", getClass().getSimpleName(), handle.rawValue());
    }

    public final void release(JNIEnv env) {
        if (cleaner != null) {
            assert next == null || next == this;
            this.next = this;
            cleaner.clean(env);
        }
    }

    /**
     * Processes {@link #CLEANERS_QUEUE} to release any handles whose objects are now unreachable.
     */
    public static void cleanHandles(JNIEnv env) {
        Cleaner cleaner;
        while ((cleaner = (Cleaner) CLEANERS_QUEUE.poll()) != null) {
            cleaner.clean(env);
        }
    }

    private static void checkNonExistingGlobalReference(JNIEnv env, JObject handle) {
        for (Cleaner cleaner : CLEANERS) {
            synchronized (cleaner) {
                if (cleaner.handle.isNonNull() && JNIUtil.IsSameObject(env, handle, cleaner.handle)) {
                    throw new IllegalArgumentException("Global JNI handle already exists for object referenced by " + handle.rawValue());
                }
            }
        }
    }

    private static boolean assertionsEnabled() {
        boolean res = false;
        assert (res = true) == true;
        return res;
    }

    /**
     * Strong references to the {@link PhantomReference} objects.
     */
    private static final Set<Cleaner> CLEANERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Queue into which a {@link Cleaner} is enqueued when its {@link HSObject} referent becomes
     * unreachable.
     */
    private static final ReferenceQueue<HSObject> CLEANERS_QUEUE = new ReferenceQueue<>();

    private static final class Cleaner extends PhantomReference<HSObject> {

        private JObject handle;
        private final boolean allowGlobalDuplicates;
        private final boolean weak;

        Cleaner(HSObject referent, JObject handle, boolean allowGlobalDuplicates, boolean weak) {
            super(referent, CLEANERS_QUEUE);
            this.handle = handle;
            this.allowGlobalDuplicates = allowGlobalDuplicates;
            this.weak = weak;
        }

        void clean(JNIEnv env) {
            if (CLEANERS.remove(this)) {
                if (checkingGlobalDuplicates(allowGlobalDuplicates)) {
                    synchronized (this) {
                        delete(env);
                        handle = WordFactory.nullPointer();
                    }
                } else {
                    delete(env);
                }
            }
        }

        private void delete(JNIEnv env) {
            if (weak) {
                DeleteWeakGlobalRef(env, (JWeak) handle);
            } else {
                DeleteGlobalRef(env, handle);
            }
        }
    }
}
