/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.substitutions.standard;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.VersionFilter;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions(type = "Ljava/lang/StackStreamFactory$AbstractStackWalker;")
public final class Target_java_lang_StackStreamFactory_AbstractStackWalker {
    private Target_java_lang_StackStreamFactory_AbstractStackWalker() {
    }

    /*
     * As of JDK 19+, The native signature for these VM methods changed. This substitution bypasses
     * the native linking of these methods to their 'JVM_*' counterparts.
     */

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java19OrLater.class)
    public static @JavaType(Object.class) StaticObject callStackWalk(
                    @JavaType(internalName = "Ljava/lang/StackStreamFactory$AbstractStackWalker;") StaticObject stackStream,
                    long mode, int skipframes,
                    @JavaType(internalName = "Ljdk/internal/vm/ContinuationScope;") StaticObject contScope,
                    @JavaType(internalName = "Ljdk/internal/vm/Continuation;") StaticObject cont,
                    int batchSize, int startIndex,
                    @JavaType(Object[].class) StaticObject frames,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta) {
        return meta.getContext().getVM().JVM_CallStackWalk19(stackStream, mode, skipframes, contScope, cont, batchSize, startIndex, frames, language, meta);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java22OrLater.class)
    public static @JavaType(Object.class) StaticObject callStackWalk(
                    @JavaType(internalName = "Ljava/lang/StackStreamFactory$AbstractStackWalker;") StaticObject stackStream,
                    int mode, int skipframes,
                    @JavaType(internalName = "Ljdk/internal/vm/ContinuationScope;") StaticObject contScope,
                    @JavaType(internalName = "Ljdk/internal/vm/Continuation;") StaticObject cont,
                    int bufferSize, int startIndex,
                    @JavaType(Object[].class) StaticObject frames,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta) {
        return meta.getContext().getVM().JVM_CallStackWalk19(stackStream, mode, skipframes, contScope, cont,
                        bufferSize - startIndex, // Translate to batch size
                        startIndex, frames, language, meta);
    }

    @Substitution(hasReceiver = true, languageFilter = VersionFilter.Java22OrLater.class)
    public static int fetchStackFrames(
                    @JavaType(internalName = "Ljava/lang/StackStreamFactory;") StaticObject stream,
                    int mode, long anchor,
                    @SuppressWarnings("unused") int lastBatchFrameCount,
                    int bufferSize, int startIndex,
                    @JavaType(Object[].class) StaticObject frames,
                    @Inject EspressoLanguage lang,
                    @Inject Meta meta,
                    @Inject VM vm) {
        return vm.JVM_MoreStackWalk(stream, mode, anchor, bufferSize - startIndex, startIndex, frames, lang, meta);
    }
}
