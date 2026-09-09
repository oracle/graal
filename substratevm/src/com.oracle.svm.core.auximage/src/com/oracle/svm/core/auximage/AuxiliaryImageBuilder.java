/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.core.thread.Safepoint;

/**
 * Permits creating, populating and persisting an auxiliary image, which captures a part of the
 * runtime state of a native image during its execution. Similar to {@link ImageSingletons}, this is
 * a key-value registry that associates a {@link Class} object with an instance of the class (or
 * interface) that it represents. A successfully persisted and loaded {@link AuxiliaryImage} will
 * contain the same associations.
 *
 * @see AuxiliaryImageLoader
 */
public final class AuxiliaryImageBuilder {
    private final Map<Class<?>, Object> map = new ConcurrentHashMap<>();
    private final List<AuxiliaryImageObjectReplacer> replacers = new ArrayList<>();
    private AuxiliaryImagePersistenceCallback callback;
    private long maximumAllowedAuxiliaryImageSize = -1;

    public AuxiliaryImageBuilder() {
    }

    /**
     * Add a singleton to the registry. The key must be unique, i.e., no value must have been
     * registered with the given class before.
     *
     * If this instance represents an auxiliary image that was loaded from a persisted state, the
     * call will throw an {@link UnsupportedOperationException}.
     */
    public <T> void add(Class<T> key, T value) {
        checkKey(key);
        checkValue(key, value);
        Object existing = map.putIfAbsent(key, value);
        if (existing != null) {
            throw new IllegalArgumentException("Must not overwrite existing key: " + key.getTypeName() +
                            System.lineSeparator() + "Existing value: " + existing +
                            System.lineSeparator() + "New value: " + value);
        }
    }

    /**
     * Look up the singleton of the provided class in the registry, which must already
     * {@linkplain #contains exist}.
     */
    public <T> T lookup(Class<T> key) {
        checkKey(key);
        Object result = map.get(key);
        if (result == null) {
            throw new RuntimeException("Does not contain key: " + key.getTypeName());
        }
        return key.cast(result);
    }

    /** Determines whether a singleton of the given class is present in the registry. */
    public boolean contains(Class<?> key) {
        checkKey(key);
        return map.containsKey(key);
    }

    /**
     * Registers an {@link AuxiliaryImageObjectReplacer}, which enables replacing or simply
     * observing object instances on the auxiliary image heap during construction.
     */
    public void registerObjectReplacer(AuxiliaryImageObjectReplacer replacer) {
        Objects.requireNonNull(replacer, "replacer");
        replacers.add(replacer);
    }

    /**
     * Enables indirect control of persisting while in {@link #persist} through a control word in
     * memory. This word at {@code address} is periodically read, although without guaranteed
     * frequency. Because Java execution in other threads is suspended, the control word must be
     * written from a thread that is in native code at the beginning of the {@link #persist} call.
     * <p>
     * A control-word value of zero is required throughout persisting in order for it to complete
     * successfully. If a non-zero control word is read at some point, the persist operation is
     * cancelled. See {@link #persist()} for details.
     * <p>
     * Cannot be used together with {@link #enableCallback}.
     */
    public void enableControlMemoryWord(WordPointer address) {
        if (callback != null) {
            throw new IllegalStateException("A control word or callback has already been registered.");
        }
        if (address.isNull()) {
            throw new IllegalArgumentException("Control word address must not be null.");
        }
        callback = new MemoryBasedAuxiliaryImagePersistenceCallback(address);
    }

    /**
     * Installs a callback that will be invoked repeatedly while persisting.
     * <p>
     * Persisting is done in a sensitive context in a VM operation. No objects may be allocated, and
     * no exceptions may be thrown by this callback.
     * <p>
     * Consider using {@link #enableControlMemoryWord} instead.
     */
    public void enableCallback(AuxiliaryImagePersistenceCallback cb) {
        if (callback != null) {
            throw new IllegalStateException("A callback or control word has already been registered.");
        }
        callback = Objects.requireNonNull(cb);
    }

    /**
     * Sets the maximum allowed size for the persisted auxiliary image in bytes. An instance of
     * {@link MaximumAuxiliaryImageSizeExceededException} is thrown if the given limit is exceeded
     * during the auxiliary image persist operation.
     */
    public void setMaximumAllowedAuxiliaryImageSize(long bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("Maximum allowed auxiliary image size must be positive.");
        }
        maximumAllowedAuxiliaryImageSize = bytes;
    }

    /**
     * Persists the associations in this builder in an image that is returned in a buffer. This
     * captures the current state of each of the objects, including their references to other
     * objects, and those objects, and so on, resulting in a transitive object graph. This happens
     * at a {@linkplain Safepoint safepoint,} an isolate-global state in which all threads are
     * paused in a known state.
     * <p>
     * <em>However, the safepoint mechanism does not guarantee that all threads that interact with
     * the objects have left the objects in a consistent state.</em> For example, another thread
     * might be inserting into a list, but has entered a safepoint after inserting the element and
     * before increasing the element count. The object observed and persisted by this method will
     * have an inconsistent state. Executing in a {@code synchronized} block or holding a lock will
     * not prevent threads from entering safepoints or delay it.
     * <p>
     * These problems can occur even with an immutable object when it has been newly allocated and
     * execution enters a safepoint before the constructor has completed (in particular, when a
     * constructor installs a reference to its object elsewhere), or when using {@code Unsafe} or
     * JNI to allocate an object and then call its constructor in a separate step.
     * <p>
     * When objects reachable through this registry can be accessed from threads other than the
     * current thread, it is recommended to manually ensure that these threads are paused in a
     * consistent, for example by using {@link CyclicBarrier}.
     * <p>
     * The auxiliary image persistence operation can be cancelled while in progress via
     * {@link #enableControlMemoryWord} or {@link #enableCallback}.
     * <p>
     * This method throws an instance of {@link AuxiliaryImagePersistenceCancelledException} if the
     * operation is intentionally cancelled, or a subclass of {@link RuntimeException} in case of an
     * unexpected failure.
     */
    public ByteBuffer persist() throws AuxiliaryImagePersistenceCancelledException {
        ImmutableAuxiliaryImage image = new ImmutableAuxiliaryImage(new IdentityHashMap<>(map));
        AuxiliaryImagePersistenceCallback cb = (callback != null) ? callback : new NonCancellableAuxiliaryImagePersistenceCallback();
        return AuxiliaryImagePersistence.persist(image, replacers, cb, maximumAllowedAuxiliaryImageSize);
    }

    private static <T> void checkKey(Class<T> key) {
        Objects.requireNonNull(key, "key");
    }

    private static <T> void checkValue(Class<T> key, T value) {
        if (value == null) {
            throw new NullPointerException("'null' value not allowed for key: " + key.getTypeName());
        }
        if (AuxiliaryImagePersistence.isInPrimaryImageHeap(value)) {
            throw new RuntimeException("Image heap object not allowed: key: " + key.getTypeName() + "\nValue: " + value);
        }
    }
}
