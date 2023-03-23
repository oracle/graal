package com.oracle.svm.core.panama;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.NativeLibrarySupport;

import jdk.internal.loader.NativeLibrary;
import jdk.internal.loader.RawNativeLibraries;

@TargetClass(value = jdk.internal.foreign.SystemLookup.class)
@SuppressWarnings("unused")
public final class Target_jdk_internal_foreign_SystemLookup {

    @Alias
    private static jdk.internal.foreign.SystemLookup INSTANCE;

    @Delete
    private static SymbolLookup FALLBACK_LOOKUP;

    @Delete
    private static SymbolLookup SYSTEM_LOOKUP;

    @Delete
    private static SymbolLookup makeSystemLookup() { return null; }

    @Delete
    private static SymbolLookup makeWindowsLookup() { return null; }

    @Delete
    private static SymbolLookup libLookup(Function<RawNativeLibraries, NativeLibrary> loader) {return null;}

    @Delete
    private static Path jdkLibraryPath(String name) {return null; }

    @Alias
    public static jdk.internal.foreign.SystemLookup getInstance() {
        return INSTANCE;
    };

    @Substitute
    public Optional<MemorySegment> find(String name) {
        // Using maybeFindSymbol results in a runtime error about casting a non-word into a word when getting from option
        var maybeSymbol = NativeLibrarySupport.singleton().findSymbol(name);
        if (maybeSymbol.isNull()) {
            return Optional.empty();
        }
        else {
            long address = maybeSymbol.rawValue();
            return Optional.of(MemorySegment.ofAddress(address));
        }
    }

//    @Delete
//    private enum WindowsFallbackSymbols {}
}
