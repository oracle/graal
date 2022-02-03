/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.jniutils;

import static org.graalvm.jniutils.JNIUtil.DeleteGlobalRef;
import static org.graalvm.jniutils.JNIUtil.NewGlobalRef;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
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
     * The created {@link HSObject} does not allow duplicate JNI global handles. Use
     * {@link #HSObject(JNIEnv, JObject, boolean)} to create {@link HSObject} allowing duplicate JNI
     * global handles.
     */
    public HSObject(JNIEnv env, JObject handle) {
        this(env, handle, false);
    }

    /**
     * Creates an object encapsulating a {@code handle} whose lifetime is determined by this object.
     * The created {@link HSObject} possibly allows duplicate JNI global handles.
     */
    public HSObject(JNIEnv env, JObject handle, boolean allowGlobalDuplicates) {
        cleanHandles(env);
        if (checkingGlobalDuplicates(allowGlobalDuplicates)) {
            checkNonExistingGlobalReference(env, handle);
        }
        this.handle = NewGlobalRef(env, handle, this.getClass().getSimpleName());
        cleaner = new Cleaner(this, this.handle, allowGlobalDuplicates);
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

        Cleaner(HSObject referent, JObject handle, boolean allowGlobalDuplicates) {
            super(referent, CLEANERS_QUEUE);
            this.handle = handle;
            this.allowGlobalDuplicates = allowGlobalDuplicates;
        }

        void clean(JNIEnv env) {
            if (CLEANERS.remove(this)) {
                if (checkingGlobalDuplicates(allowGlobalDuplicates)) {
                    synchronized (this) {
                        DeleteGlobalRef(env, handle);
                        handle = WordFactory.nullPointer();
                    }
                } else {
                    DeleteGlobalRef(env, handle);
                }
            }
        }
    }
}
