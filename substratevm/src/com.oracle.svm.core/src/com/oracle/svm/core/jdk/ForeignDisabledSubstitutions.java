/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;

/* Substitutions for when Foreign Function and Memory (FFM) API support is disabled. */

final class ForeignDisabled implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return !SubstrateOptions.ForeignAPISupport.getValue();
    }
}

@TargetClass(className = "jdk.internal.foreign.MemorySessionImpl", onlyWith = ForeignDisabled.class)
final class Target_jdk_internal_foreign_MemorySessionImpl {
    @Substitute
    @SuppressWarnings("static-method")
    Target_java_lang_foreign_Arena asArena() {
        throw ForeignDisabledSubstitutions.fail();
    }
}

@TargetClass(className = "java.lang.foreign.Arena", onlyWith = ForeignDisabled.class)
final class Target_java_lang_foreign_Arena {
}

@TargetClass(className = "java.lang.foreign.Linker", onlyWith = ForeignDisabled.class)
final class Target_java_lang_foreign_Linker {
}

@TargetClass(className = "java.lang.foreign.Linker", innerClass = "Option", onlyWith = ForeignDisabled.class)
final class Target_java_lang_foreign_Linker_Option {
}

@TargetClass(className = "jdk.internal.foreign.abi.AbstractLinker", onlyWith = ForeignDisabled.class)
final class Target_jdk_internal_foreign_abi_AbstractLinker {
    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    Target_java_lang_foreign_MemorySegment upcallStub(MethodHandle target, Target_java_lang_foreign_FunctionDescriptor function,
                    Target_java_lang_foreign_Arena arena, Target_java_lang_foreign_Linker_Option... options) {
        throw ForeignDisabledSubstitutions.fail();
    }
}

@TargetClass(className = "jdk.internal.foreign.abi.SharedUtils", onlyWith = ForeignDisabled.class)
final class Target_jdk_internal_foreign_abi_SharedUtils {
    @Substitute
    @SuppressWarnings("unused")
    static Target_java_lang_foreign_Arena newBoundedArena(long size) {
        throw ForeignDisabledSubstitutions.fail();
    }

    @Substitute
    static Target_java_lang_foreign_Arena newEmptyArena() {
        throw ForeignDisabledSubstitutions.fail();
    }

    @Substitute
    static Target_java_lang_foreign_Linker getSystemLinker() {
        throw ForeignDisabledSubstitutions.fail();
    }
}

@TargetClass(className = "java.lang.foreign.MemoryLayout", onlyWith = ForeignDisabled.class)
final class Target_java_lang_foreign_MemoryLayout {
}

@TargetClass(className = "jdk.internal.foreign.FunctionDescriptorImpl", onlyWith = ForeignDisabled.class)
final class Target_jdk_internal_foreign_FunctionDescriptorImpl {
    @Substitute
    @SuppressWarnings("unused")
    Target_jdk_internal_foreign_FunctionDescriptorImpl(Target_java_lang_foreign_MemoryLayout resLayout, List<Target_java_lang_foreign_MemoryLayout> argLayouts) {
        throw ForeignDisabledSubstitutions.fail();
    }
}

@TargetClass(className = "java.lang.foreign.FunctionDescriptor", onlyWith = ForeignDisabled.class)
final class Target_java_lang_foreign_FunctionDescriptor {
}

@TargetClass(className = "jdk.internal.foreign.SegmentFactories", onlyWith = {ForeignDisabled.class, JDK22OrLater.class})
final class Target_jdk_internal_foreign_SegmentFactories {
    @Substitute
    @AlwaysInline("Make remaining code in callers unreachable.")
    static void ensureInitialized() {
        throw ForeignDisabledSubstitutions.fail();
    }
}

@TargetClass(className = "sun.nio.ch.FileChannelImpl", onlyWith = ForeignDisabled.class)
final class Target_sun_nio_ch_FileChannelImpl {
    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    Target_java_lang_foreign_MemorySegment map(FileChannel.MapMode mode, long offset, long size, Target_java_lang_foreign_Arena arena) throws IOException {
        throw ForeignDisabledSubstitutions.fail();
    }
}

@TargetClass(className = "jdk.internal.foreign.LayoutPath", onlyWith = ForeignDisabled.class)
final class Target_jdk_internal_foreign_LayoutPath {
}

@TargetClass(className = "java.lang.foreign.MemoryLayout", innerClass = "PathElement", onlyWith = ForeignDisabled.class)
final class Target_java_lang_foreign_MemoryLayout_PathElement {
}

@TargetClass(className = "jdk.internal.foreign.layout.AbstractLayout", onlyWith = {ForeignDisabled.class, JDK22OrLater.class})
final class Target_jdk_internal_foreign_layout_AbstractLayout {
    @Substitute
    @AlwaysInline("Make remaining code in callers unreachable.")
    @SuppressWarnings({"unused", "static-method"})
    VarHandle varHandle(Target_java_lang_foreign_MemoryLayout_PathElement... elements) {
        throw ForeignDisabledSubstitutions.fail();
    }

    @Substitute
    @TargetElement(onlyWith = JDK23OrLater.class)
    @SuppressWarnings({"unused", "static-method"})
    VarHandle varHandleInternal(Target_java_lang_foreign_MemoryLayout_PathElement... elements) {
        throw ForeignDisabledSubstitutions.fail();
    }

    @Substitute
    @SuppressWarnings("unused")
    static <Z> Z computePathOp(Target_jdk_internal_foreign_LayoutPath path, Function<Target_jdk_internal_foreign_LayoutPath, Z> finalizer,
                    Set<?> badKinds, Target_java_lang_foreign_MemoryLayout_PathElement... elements) {
        throw ForeignDisabledSubstitutions.fail();
    }
}

final class ForeignDisabledSubstitutions {
    private static final String OPTION_NAME = SubstrateOptionsParser.commandArgument(SubstrateOptions.ForeignAPISupport, "+");

    static RuntimeException fail() {
        assert !SubstrateOptions.ForeignAPISupport.getValue();
        throw VMError.unsupportedFeature("Support for the Java Foreign Function and Memory API is not active: enable with " + OPTION_NAME);
    }
}
