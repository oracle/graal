/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub.registry;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.NameSymbols;
import com.oracle.svm.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Symbols;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Utf8Symbols;
import com.oracle.svm.shared.singletons.LayeredImageSingletonSupport;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = BuiltinTraits.NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class, other = PartiallyLayerAware.class)
public final class SymbolsSupport {
    @Platforms(Platform.HOSTED_ONLY.class) //
    private static final SymbolsSupport TEST_SINGLETON = ImageInfo.inImageCode() ? null : new SymbolsSupport();

    final Symbols symbols;
    final Utf8Symbols utf8;
    final NameSymbols names;
    final TypeSymbols types;
    final SignatureSymbols signatures;
    @UnknownObjectField(canBeNull = true, availability = BuildPhaseProvider.AfterCompilation.class) //
    ImageSymbolSet imageSymbols;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SymbolsSupport() {
        int initialSymbolTableCapacity = 4 * 1024;
        symbols = Symbols.fromExisting(SVMSymbols.SYMBOLS.freeze(), initialSymbolTableCapacity, 0);
        // let this resize when first used at runtime
        utf8 = new Utf8Symbols(symbols);
        names = new NameSymbols(symbols);
        types = new TypeSymbols(symbols);
        signatures = new SignatureSymbols(symbols, types);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static SymbolsSupport hostedLookup() {
        return LayeredImageSingletonSupport.singleton().lookup(SymbolsSupport.class, false, true);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void prepareSymbolsForImageHeap() {
        hostedLookup().prepareSymbolsForImageHeap0();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void prepareSymbolsForImageHeap0() {
        /*
         * Keep build-time symbols in one immutable image-heap lookup table and leave the normal
         * strong/weak maps empty for symbols created at runtime. The image table is only for
         * symbols deliberately embedded in the image heap; symbols created after image startup keep
         * using the runtime SymbolsImpl maps and their normal weak/strong semantics.
         */
        var imageSymbolList = symbols.drainSymbols();
        if (!imageSymbolList.isEmpty()) {
            imageSymbols = ImageSymbolSet.create(imageSymbolList);
        }
    }

    public static TypeSymbols getTypes() {
        return currentLayer().types;
    }

    public static SignatureSymbols getSignatures() {
        return currentLayer().signatures;
    }

    public static NameSymbols getNames() {
        return currentLayer().names;
    }

    public static Utf8Symbols getUtf8() {
        return currentLayer().utf8;
    }

    /*
     * Substituted at runtime to return the topmost layer. This ensures that symbols defined at
     * runtime end up in a single location.
     */
    public static SymbolsSupport currentLayer() {
        if (TEST_SINGLETON != null) {
            /*
             * Some unit tests use com.oracle.svm.interpreter.metadata outside the context of
             * native-image.
             */
            assert !ImageInfo.inImageCode();
            return TEST_SINGLETON;
        }
        return LayeredImageSingletonSupport.singleton().lookup(SymbolsSupport.class, false, true);
    }

    public static SymbolsSupport[] layeredSingletons() {
        return MultiLayeredImageSingleton.getAllLayers(SymbolsSupport.class);
    }
}

/**
 * Immutable open-addressed lookup table for symbols that were already present during image
 * building. The table preserves symbol identity by storing the original {@link Symbol} instances
 * directly in the image heap while avoiding the runtime footprint of the mutable strong/weak maps
 * used by {@link Symbols}.
 */
final class ImageSymbolSet {
    /**
     * Maximum table occupancy. Keeping at least one third of the slots empty bounds the expected
     * number of linear-probing steps while still using less image-heap memory than the original
     * maps.
     */
    private static final int MAX_LOAD_PERCENT = 66;

    @UnknownObjectField(availability = BuildPhaseProvider.AfterCompilation.class) //
    private final Symbol<?>[] table;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.AfterCompilation.class) //
    private final int mask;

    private ImageSymbolSet(Symbol<?>[] table) {
        this.table = table;
        this.mask = table.length - 1;
    }

    static ImageSymbolSet create(Collection<Symbol<?>> symbols) {
        int tableSize = 1;
        int symbolCount = symbols.size();
        int roundedUpPercent = symbolCount * 100 + MAX_LOAD_PERCENT - 1;
        int minimumTableSize = Math.max(1, roundedUpPercent / MAX_LOAD_PERCENT);
        while (tableSize < minimumTableSize) {
            tableSize <<= 1;
        }
        Symbol<?>[] table = new Symbol<?>[tableSize];
        ImageSymbolSet result = new ImageSymbolSet(table);
        for (Symbol<?> symbol : symbols) {
            result.insert(symbol);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    <T> Symbol<T> lookup(ByteSequence byteSequence) {
        if (byteSequence == null) {
            return null;
        }
        int index = hashIndex(byteSequence, mask);
        while (true) {
            Symbol<?> candidate = table[index];
            if (candidate == null) {
                return null;
            }
            if (candidate == byteSequence || (candidate.hashCode() == byteSequence.hashCode() && candidate.contentEquals(byteSequence))) {
                return (Symbol<T>) candidate;
            }
            index = (index + 1) & mask;
        }
    }

    private void insert(Symbol<?> symbol) {
        int index = hashIndex(symbol, mask);
        while (table[index] != null) {
            index = (index + 1) & mask;
        }
        table[index] = symbol;
    }

    private static int hashIndex(ByteSequence byteSequence, int mask) {
        return byteSequence.hashCode() & mask;
    }
}

/**
 * This substitution class is here to avoid a massive refactoring of the symbols support. The only
 * part of this code that needs to be layer-aware is the Symbols class (and its implementation). We
 * simply need getOrCreate to act on the current layer at build-time, and the lookup and getOrCreate
 * calls to be checking all layered singletons at run-time.
 */
@TargetClass(className = "com.oracle.svm.espresso.classfile.descriptors.SymbolsImpl")
final class Target_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl {
    @Alias//
    ConcurrentHashMap<ByteSequence, Symbol<?>> strongMap;

    @Alias//
    WeakHashMap<ByteSequence, WeakReference<Symbol<?>>> weakMap;

    @Alias//
    ReadWriteLock readWriteLock;

    @Substitute
    @SuppressWarnings({"static-method"})
    public <T> Symbol<T> lookup(ByteSequence byteSequence) {
        for (var singleton : SymbolsSupport.layeredSingletons()) {
            var symbols = SubstrateUtil.cast(singleton.symbols, Target_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.class);
            Symbol<T> symbol = Util_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.originalLookup(symbols, byteSequence);
            if (symbol == null) {
                symbol = Util_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.imageLookup(singleton, byteSequence);
            }
            if (symbol != null) {
                return symbol;
            }
        }
        return null;
    }

    @Substitute
    <T> Symbol<T> getOrCreate(ByteSequence byteSequence, boolean ensureStrongReference) {
        for (var singleton : SymbolsSupport.layeredSingletons()) {
            var symbols = SubstrateUtil.cast(singleton.symbols, Target_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.class);
            Symbol<T> symbol = Util_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.originalLookup(symbols, byteSequence);
            if (symbol == null) {
                symbol = Util_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.imageLookup(singleton, byteSequence);
                if (symbol != null) {
                    return symbol;
                }
            }
            if (symbol != null && !(ensureStrongReference && symbols.isWeak(symbol))) {
                return symbol;
            }
        }
        return Util_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.originalGetOrCreate(this, byteSequence, ensureStrongReference);
    }

    @Substitute
    public boolean isWeak(Symbol<?> symbol) {
        assert lookup(symbol) == symbol;
        /*
         * A symbol found in an immutable image table is not represented by a runtime weak-map entry,
         * so report it as non-weak. This is specific to symbols that were embedded in the image
         * heap. It does not imply that runtime-created symbols are generally strong: symbols
         * created after image startup keep the usual SymbolsImpl semantics, where strongMap
         * membership determines whether the symbol is weak. Check all layers because the receiver
         * may be a lower-layer symbol table while the symbol itself belongs to another layer.
         */
        for (var singleton : SymbolsSupport.layeredSingletons()) {
            var symbols = SubstrateUtil.cast(singleton.symbols, Target_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.class);
            if (Util_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.imageLookup(singleton, symbol) == symbol || symbols.strongMap.get(symbol) == symbol) {
                return false;
            }
        }
        return true;
    }
}

final class Util_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl {
    @SuppressWarnings("unchecked")
    static <T> Symbol<T> originalLookup(Target_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl symbols, ByteSequence byteSequence) {
        // Lock-free fast path, common symbols are usually strongly referenced e.g.
        // Ljava/lang/Object;
        Symbol<T> result = (Symbol<T>) symbols.strongMap.get(byteSequence);
        if (result != null) {
            return result;
        }
        symbols.readWriteLock.readLock().lock();
        try {
            result = (Symbol<T>) symbols.strongMap.get(byteSequence);
            if (result != null) {
                return result;
            }
            WeakReference<Symbol<?>> weakValue = symbols.weakMap.get(byteSequence);
            if (weakValue != null) {
                return (Symbol<T>) weakValue.get();
            } else {
                return null;
            }
        } finally {
            symbols.readWriteLock.readLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    static <T> Symbol<T> imageLookup(SymbolsSupport singleton, ByteSequence byteSequence) {
        if (singleton.imageSymbols == null) {
            return null;
        }
        return (Symbol<T>) singleton.imageSymbols.lookup(byteSequence);
    }

    @SuppressWarnings("unchecked")
    static <T> Symbol<T> originalGetOrCreate(Target_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl symbols, ByteSequence byteSequence, boolean ensureStrongReference) {
        // Lock-free fast path, common symbols are usually strongly referenced e.g.
        // Ljava/lang/Object;
        Symbol<T> symbol = (Symbol<T>) symbols.strongMap.get(byteSequence);
        if (symbol != null) {
            return symbol;
        }

        symbols.readWriteLock.writeLock().lock();
        try {
            // Must peek again within the lock because the symbol may have been promoted from weak
            // to strong by another thread; querying only the weak map wouldn't be correct.
            symbol = (Symbol<T>) symbols.strongMap.get(byteSequence);
            if (symbol != null) {
                return symbol;
            }

            if (ensureStrongReference) {
                WeakReference<Symbol<?>> weakValue = symbols.weakMap.remove(byteSequence);
                if (weakValue != null) {
                    // Promote weak symbol to strong.
                    symbol = (Symbol<T>) weakValue.get();
                    // The weak symbol may have been collected.
                    if (symbol != null) {
                        symbols.strongMap.put(symbol, symbol);
                        return symbol;
                    }
                }

                // Create new strong symbol.
                symbol = Target_com_oracle_svm_espresso_classfile_descriptors_Symbols.createSymbolInstanceUnsafe(byteSequence);
                symbols.strongMap.put(symbol, symbol);
                return symbol;
            } else {
                WeakReference<Symbol<?>> weakValue = symbols.weakMap.get(byteSequence);
                if (weakValue != null) {
                    symbol = (Symbol<T>) weakValue.get();
                    // The weak symbol may have been collected.
                    if (symbol != null) {
                        return symbol;
                    }
                }

                // Create new weak symbol.
                symbol = Target_com_oracle_svm_espresso_classfile_descriptors_Symbols.createSymbolInstanceUnsafe(byteSequence);
                symbols.weakMap.put(symbol, new WeakReference<>(symbol));
                return symbol;
            }
        } finally {
            symbols.readWriteLock.writeLock().unlock();
        }
    }
}

@TargetClass(Symbols.class)
final class Target_com_oracle_svm_espresso_classfile_descriptors_Symbols {
    @Alias
    static native <T> Symbol<T> createSymbolInstanceUnsafe(ByteSequence byteSequence);
}

@TargetClass(SymbolsSupport.class)
final class Target_com_oracle_svm_core_hub_registry_SymbolsSupport {
    @Substitute
    public static SymbolsSupport currentLayer() {
        SymbolsSupport[] allLayers = SymbolsSupport.layeredSingletons();
        return allLayers[allLayers.length - 1];
    }
}
