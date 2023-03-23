package com.oracle.svm.core.panama;

import java.lang.foreign.SegmentScope;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.util.function.BiFunction;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.loader.NativeLibrary;
import jdk.internal.loader.RawNativeLibraries;

@TargetClass(value = java.lang.foreign.SymbolLookup.class)
@SuppressWarnings("unused")
public final class Target_java_lang_foreign_SymbolLookup {
    // We currently cheat by melding all lookups into one global lookup
    @Substitute
    public static SymbolLookup loaderLookup() {
        return Target_jdk_internal_foreign_SystemLookup.getInstance();
    }

    @Substitute
    static SymbolLookup libraryLookup(String name, SegmentScope scope) {
        return loaderLookup();
    }

    @Substitute
    static SymbolLookup libraryLookup(Path path, SegmentScope scope) {
        return loaderLookup();
    }

    @Substitute
    private static <Z> SymbolLookup libraryLookup(Z libDesc, BiFunction<RawNativeLibraries, Z, NativeLibrary> loadLibraryFunc, SegmentScope libScope) {
        return loaderLookup();
    }
}
