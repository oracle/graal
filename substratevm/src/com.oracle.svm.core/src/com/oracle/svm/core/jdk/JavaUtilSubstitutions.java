/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.Collection;
import java.util.Currency;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.LogManager;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

/*
 * Lazily initialized cache fields of collection classes need to be reset. They are not needed in
 * the image heap because they can always be recomputed. But more importantly, the fields can be
 * modified any time during image generation, in which case the static analysis and image heap
 * writing can report errors about new objects spuriously appearing.
 */

@TargetClass(java.util.AbstractMap.class)
final class Target_java_util_AbstractMap {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Set<?> keySet;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Collection<?> values;
}

@TargetClass(value = java.util.Collections.class, innerClass = "UnmodifiableMap")
final class Target_java_util_Collections_UnmodifiableMap {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Set<?> keySet;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Set<?> entrySet;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Collection<?> values;
}

@TargetClass(value = java.util.Collections.class, innerClass = "SynchronizedMap")
final class Target_java_util_Collections_SynchronizedMap {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Set<?> keySet;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Set<?> entrySet;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Collection<?> values;
}

@TargetClass(java.util.EnumMap.class)
final class Target_java_util_EnumMap {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Set<?> entrySet;
}

@TargetClass(java.util.IdentityHashMap.class)
final class Target_java_util_IdentityHashMap {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Set<?> entrySet;
}

@TargetClass(java.util.WeakHashMap.class)
final class Target_java_util_WeakHashMap {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Set<?> entrySet;
}

@TargetClass(java.util.Hashtable.class)
final class Target_java_util_Hashtable {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Set<?> keySet;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Set<?> entrySet;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Collection<?> values;
}

@TargetClass(java.util.TreeMap.class)
final class Target_java_util_TreeMap {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Target_java_util_TreeMap_EntrySet entrySet;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Target_java_util_TreeMap_KeySet navigableKeySet;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    NavigableMap<?, ?> descendingMap;
}

@TargetClass(value = java.util.TreeMap.class, innerClass = "EntrySet")
final class Target_java_util_TreeMap_EntrySet {
}

@TargetClass(value = java.util.TreeMap.class, innerClass = "KeySet")
final class Target_java_util_TreeMap_KeySet {
}

@TargetClass(java.util.HashMap.class)
final class Target_java_util_HashMap {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Set<?> entrySet;

    @Substitute
    private static Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) {
            /*
             * We cannot do all the generic interface checks that the original implementation is
             * doing, because we do not have the necessary metadata at run time.
             */
            return x.getClass();
        }
        return null;
    }
}

@TargetClass(java.util.concurrent.ConcurrentHashMap.class)
final class Target_java_util_concurrent_ConcurrentHashMap {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Target_java_util_concurrent_ConcurrentHashMap_KeySetView keySet;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Target_java_util_concurrent_ConcurrentHashMap_ValuesView values;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Target_java_util_concurrent_ConcurrentHashMap_EntrySetView entrySet;

    @Substitute
    private static Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) {
            /*
             * We cannot do all the generic interface checks that the original implementation is
             * doing, because we do not have the necessary metadata at run time.
             */
            return x.getClass();
        }
        return null;
    }
}

@TargetClass(value = java.util.concurrent.ConcurrentHashMap.class, innerClass = "KeySetView")
final class Target_java_util_concurrent_ConcurrentHashMap_KeySetView {
}

@TargetClass(value = java.util.concurrent.ConcurrentHashMap.class, innerClass = "ValuesView")
final class Target_java_util_concurrent_ConcurrentHashMap_ValuesView {
}

@TargetClass(value = java.util.concurrent.ConcurrentHashMap.class, innerClass = "EntrySetView")
final class Target_java_util_concurrent_ConcurrentHashMap_EntrySetView {
}

@TargetClass(java.util.concurrent.ConcurrentSkipListMap.class)
final class Target_java_util_concurrent_ConcurrentSkipListMap {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Target_java_util_concurrent_ConcurrentSkipListMap_KeySet keySet;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Target_java_util_concurrent_ConcurrentSkipListMap_EntrySet entrySet;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Target_java_util_concurrent_ConcurrentSkipListMap_Values values;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Target_java_util_concurrent_ConcurrentSkipListMap_SubMap descendingMap;
}

@TargetClass(value = java.util.concurrent.ConcurrentSkipListMap.class, innerClass = "KeySet")
final class Target_java_util_concurrent_ConcurrentSkipListMap_KeySet {
}

@TargetClass(value = java.util.concurrent.ConcurrentSkipListMap.class, innerClass = "EntrySet")
final class Target_java_util_concurrent_ConcurrentSkipListMap_EntrySet {
}

@TargetClass(value = java.util.concurrent.ConcurrentSkipListMap.class, innerClass = "SubMap")
final class Target_java_util_concurrent_ConcurrentSkipListMap_SubMap {
}

@TargetClass(value = java.util.concurrent.ConcurrentSkipListMap.class, innerClass = "Values")
final class Target_java_util_concurrent_ConcurrentSkipListMap_Values {
}

@TargetClass(java.util.Currency.class)
final class Target_java_util_Currency {
    @Alias//
    @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    private static ConcurrentMap<String, Currency> instances;
}

/**
 * During LogManager initialization a shutdown hook is added to close all handlers. However, this
 * shutdown hook is lost for native-image because (i) all hooks are reinitialized within the image
 * (see {@link Target_java_lang_Shutdown}) and (ii) the LogManager must be build-time initialized
 * (see LoggingFeature). As a workaround, extra logic is placed within (LogManager getLogManager())
 * so that during runtime the first time the log handler is accessed the equivalent shutdown hook is
 * added.
 */
@TargetClass(value = LogManager.class)
final class Target_java_util_logging_LogManager {

    @Inject @RecomputeFieldValue(kind = Kind.NewInstance, declClass = AtomicBoolean.class) private AtomicBoolean addedShutdownHook = new AtomicBoolean();

    @Alias static LogManager manager;

    @Alias
    native void ensureLogManagerInitialized();

    @Substitute
    public static LogManager getLogManager() {
        /* First performing logic originally in getLogManager. */
        if (manager == null) {
            return manager;
        }
        Target_java_util_logging_LogManager managerAlias = SubstrateUtil.cast(manager, Target_java_util_logging_LogManager.class);
        managerAlias.ensureLogManagerInitialized();

        /* Logic for adding shutdown hook. */
        if (!managerAlias.addedShutdownHook.getAndSet(true)) {
            /* Add a shutdown hook to close the global handlers. */
            try {
                Runtime.getRuntime().addShutdownHook(SubstrateUtil.cast(new Target_java_util_logging_LogManager_Cleaner(managerAlias), Thread.class));
            } catch (IllegalStateException e) {
                /* If the VM is already shutting down, we do not need to register shutdownHook. */
            }
        }

        return manager;
    }
}

@TargetClass(value = LogManager.class, innerClass = "Cleaner")
final class Target_java_util_logging_LogManager_Cleaner {

    @Alias
    @SuppressWarnings("unused")
    Target_java_util_logging_LogManager_Cleaner(Target_java_util_logging_LogManager outer) {
        throw VMError.shouldNotReachHere("This is an alias to the original constructor in the target class, so this code is unreachable");
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaUtilSubstitutions {
}
