/*
 * Copyright (c) 2017, 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.thirdparty;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

/**
 * ICU4JFeature enables ICU4J library ({@link "http://site.icu-project.org/"} to be used in SVM.
 * <p>
 * The main obstacle in using the ICU4J library as is was that the library relies on class loader to
 * fetch localization data from resource files included in the ICU4J jar archive. This feature is
 * not supported by SVM, so the next option was to read the resource files from the file system. The
 * following code addresses several issues that occurred when specifying
 * <code>com.ibm.icu.impl.ICUBinary.dataPath</code> system property in runtime (standard ICU4J
 * feature).
 */
@AutomaticFeature
public final class ICU4JFeature implements Feature {

    static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(ICU4JFeature.class);
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return access.findClassByName("com.ibm.icu.impl.ClassLoaderUtil") != null;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        registerShimClass(access, "com.ibm.icu.text.NumberFormatServiceShim");
        registerShimClass(access, "com.ibm.icu.text.CollatorServiceShim");
        registerShimClass(access, "com.ibm.icu.text.BreakIteratorFactory");
    }

    private static void registerShimClass(BeforeAnalysisAccess access, String shimClassName) {
        Class<?> shimClass = access.findClassByName(shimClassName);
        if (shimClass != null) {
            RuntimeReflection.registerForReflectiveInstantiation(shimClass);
        } else {
            throw VMError.shouldNotReachHere(shimClassName + " not found");
        }
    }

    private Object checkImageHeapDoesNotIncludeDirectByteBuffers(Object obj) {
        if (obj instanceof ByteBuffer) {
            if (((ByteBuffer) obj).isDirect()) {
                throw new UnsupportedOperationException("Direct ByteBuffer found in heap: " + obj);
            }
        }
        return obj;
    }

    // TODO: this could be loaded dynamically from the JAR file class-loader.
    final String icu4jClassNames[] = {
                    "com.ibm.icu.impl.Assert", "com.ibm.icu.impl.BMPSet", "com.ibm.icu.impl.CSCharacterIterator", "com.ibm.icu.impl.CacheBase", "com.ibm.icu.impl.CacheValue$1",
                    "com.ibm.icu.impl.CacheValue$NullValue", "com.ibm.icu.impl.CacheValue$SoftValue", "com.ibm.icu.impl.CacheValue$Strength", "com.ibm.icu.impl.CacheValue$StrongValue",
                    "com.ibm.icu.impl.CacheValue", "com.ibm.icu.impl.CalendarAstronomer$1", "com.ibm.icu.impl.CalendarAstronomer$2", "com.ibm.icu.impl.CalendarAstronomer$3",
                    "com.ibm.icu.impl.CalendarAstronomer$4", "com.ibm.icu.impl.CalendarAstronomer$AngleFunc", "com.ibm.icu.impl.CalendarAstronomer$CoordFunc",
                    "com.ibm.icu.impl.CalendarAstronomer$Ecliptic", "com.ibm.icu.impl.CalendarAstronomer$Equatorial", "com.ibm.icu.impl.CalendarAstronomer$Horizon",
                    "com.ibm.icu.impl.CalendarAstronomer$MoonAge", "com.ibm.icu.impl.CalendarAstronomer$SolarLongitude", "com.ibm.icu.impl.CalendarAstronomer", "com.ibm.icu.impl.CalendarCache",
                    "com.ibm.icu.impl.CalendarUtil$CalendarPreferences", "com.ibm.icu.impl.CalendarUtil", "com.ibm.icu.impl.CaseMapImpl$1", "com.ibm.icu.impl.CaseMapImpl$GreekUpper",
                    "com.ibm.icu.impl.CaseMapImpl$StringContextIterator", "com.ibm.icu.impl.CaseMapImpl$WholeStringBreakIterator", "com.ibm.icu.impl.CaseMapImpl", "com.ibm.icu.impl.CharTrie",
                    "com.ibm.icu.impl.CharacterIteration", "com.ibm.icu.impl.CharacterIteratorWrapper", "com.ibm.icu.impl.ClassLoaderUtil$1", "com.ibm.icu.impl.ClassLoaderUtil$BootstrapClassLoader",
                    "com.ibm.icu.impl.ClassLoaderUtil", "com.ibm.icu.impl.CollectionSet", "com.ibm.icu.impl.CurrencyData$1", "com.ibm.icu.impl.CurrencyData$CurrencyDisplayInfo",
                    "com.ibm.icu.impl.CurrencyData$CurrencyDisplayInfoProvider", "com.ibm.icu.impl.CurrencyData$CurrencyFormatInfo", "com.ibm.icu.impl.CurrencyData$CurrencySpacingInfo$SpacingPattern",
                    "com.ibm.icu.impl.CurrencyData$CurrencySpacingInfo$SpacingType", "com.ibm.icu.impl.CurrencyData$CurrencySpacingInfo", "com.ibm.icu.impl.CurrencyData$DefaultInfo",
                    "com.ibm.icu.impl.CurrencyData", "com.ibm.icu.impl.DateNumberFormat", "com.ibm.icu.impl.DayPeriodRules$1", "com.ibm.icu.impl.DayPeriodRules$CutoffType",
                    "com.ibm.icu.impl.DayPeriodRules$DayPeriod", "com.ibm.icu.impl.DayPeriodRules$DayPeriodRulesCountSink", "com.ibm.icu.impl.DayPeriodRules$DayPeriodRulesData",
                    "com.ibm.icu.impl.DayPeriodRules$DayPeriodRulesDataSink", "com.ibm.icu.impl.DayPeriodRules", "com.ibm.icu.impl.DontCareFieldPosition", "com.ibm.icu.impl.Grego",
                    "com.ibm.icu.impl.ICUBinary$1", "com.ibm.icu.impl.ICUBinary$Authenticate", "com.ibm.icu.impl.ICUBinary$DatPackageReader$IsAcceptable",
                    "com.ibm.icu.impl.ICUBinary$DatPackageReader", "com.ibm.icu.impl.ICUBinary$DataFile", "com.ibm.icu.impl.ICUBinary$PackageDataFile", "com.ibm.icu.impl.ICUBinary$SingleDataFile",
                    "com.ibm.icu.impl.ICUBinary", "com.ibm.icu.impl.ICUCache", "com.ibm.icu.impl.ICUConfig$1", "com.ibm.icu.impl.ICUConfig", "com.ibm.icu.impl.ICUData$1", "com.ibm.icu.impl.ICUData$2",
                    "com.ibm.icu.impl.ICUData$3", "com.ibm.icu.impl.ICUData", "com.ibm.icu.impl.ICUDataVersion", "com.ibm.icu.impl.ICUDebug",
                    "com.ibm.icu.impl.ICULocaleService$ICUResourceBundleFactory", "com.ibm.icu.impl.ICULocaleService$LocaleKey", "com.ibm.icu.impl.ICULocaleService$LocaleKeyFactory",
                    "com.ibm.icu.impl.ICULocaleService$SimpleLocaleKeyFactory", "com.ibm.icu.impl.ICULocaleService", "com.ibm.icu.impl.ICUNotifier$NotifyThread", "com.ibm.icu.impl.ICUNotifier",
                    "com.ibm.icu.impl.ICURWLock$1", "com.ibm.icu.impl.ICURWLock$Stats", "com.ibm.icu.impl.ICURWLock", "com.ibm.icu.impl.ICUResourceBundle$1", "com.ibm.icu.impl.ICUResourceBundle$2$1",
                    "com.ibm.icu.impl.ICUResourceBundle$2", "com.ibm.icu.impl.ICUResourceBundle$3", "com.ibm.icu.impl.ICUResourceBundle$4", "com.ibm.icu.impl.ICUResourceBundle$AvailEntry",
                    "com.ibm.icu.impl.ICUResourceBundle$Loader", "com.ibm.icu.impl.ICUResourceBundle$OpenType", "com.ibm.icu.impl.ICUResourceBundle$WholeBundle", "com.ibm.icu.impl.ICUResourceBundle",
                    "com.ibm.icu.impl.ICUResourceBundleImpl$ResourceArray", "com.ibm.icu.impl.ICUResourceBundleImpl$ResourceBinary", "com.ibm.icu.impl.ICUResourceBundleImpl$ResourceContainer",
                    "com.ibm.icu.impl.ICUResourceBundleImpl$ResourceInt", "com.ibm.icu.impl.ICUResourceBundleImpl$ResourceIntVector", "com.ibm.icu.impl.ICUResourceBundleImpl$ResourceString",
                    "com.ibm.icu.impl.ICUResourceBundleImpl$ResourceTable", "com.ibm.icu.impl.ICUResourceBundleImpl", "com.ibm.icu.impl.ICUResourceBundleReader$1",
                    "com.ibm.icu.impl.ICUResourceBundleReader$Array", "com.ibm.icu.impl.ICUResourceBundleReader$Array16", "com.ibm.icu.impl.ICUResourceBundleReader$Array32",
                    "com.ibm.icu.impl.ICUResourceBundleReader$Container", "com.ibm.icu.impl.ICUResourceBundleReader$IsAcceptable", "com.ibm.icu.impl.ICUResourceBundleReader$ReaderCache",
                    "com.ibm.icu.impl.ICUResourceBundleReader$ReaderCacheKey", "com.ibm.icu.impl.ICUResourceBundleReader$ReaderValue", "com.ibm.icu.impl.ICUResourceBundleReader$ResourceCache$Level",
                    "com.ibm.icu.impl.ICUResourceBundleReader$ResourceCache", "com.ibm.icu.impl.ICUResourceBundleReader$Table", "com.ibm.icu.impl.ICUResourceBundleReader$Table16",
                    "com.ibm.icu.impl.ICUResourceBundleReader$Table1632", "com.ibm.icu.impl.ICUResourceBundleReader$Table32", "com.ibm.icu.impl.ICUResourceBundleReader",
                    "com.ibm.icu.impl.ICUResourceTableAccess", "com.ibm.icu.impl.ICUService$CacheEntry", "com.ibm.icu.impl.ICUService$Factory", "com.ibm.icu.impl.ICUService$Key",
                    "com.ibm.icu.impl.ICUService$LocaleRef", "com.ibm.icu.impl.ICUService$ServiceListener", "com.ibm.icu.impl.ICUService$SimpleFactory", "com.ibm.icu.impl.ICUService",
                    "com.ibm.icu.impl.IDNA2003", "com.ibm.icu.impl.IllegalIcuArgumentException", "com.ibm.icu.impl.IntTrie", "com.ibm.icu.impl.IntTrieBuilder",
                    "com.ibm.icu.impl.InvalidFormatException", "com.ibm.icu.impl.IterableComparator", "com.ibm.icu.impl.JavaTimeZone", "com.ibm.icu.impl.LocaleDisplayNamesImpl$1",
                    "com.ibm.icu.impl.LocaleDisplayNamesImpl$Cache", "com.ibm.icu.impl.LocaleDisplayNamesImpl$CapitalizationContextSink",
                    "com.ibm.icu.impl.LocaleDisplayNamesImpl$CapitalizationContextUsage", "com.ibm.icu.impl.LocaleDisplayNamesImpl$DataTable", "com.ibm.icu.impl.LocaleDisplayNamesImpl$DataTableType",
                    "com.ibm.icu.impl.LocaleDisplayNamesImpl$DataTables$1", "com.ibm.icu.impl.LocaleDisplayNamesImpl$DataTables", "com.ibm.icu.impl.LocaleDisplayNamesImpl$ICUDataTable",
                    "com.ibm.icu.impl.LocaleDisplayNamesImpl$ICUDataTables", "com.ibm.icu.impl.LocaleDisplayNamesImpl$LangDataTables", "com.ibm.icu.impl.LocaleDisplayNamesImpl$RegionDataTables",
                    "com.ibm.icu.impl.LocaleDisplayNamesImpl", "com.ibm.icu.impl.LocaleIDParser$1", "com.ibm.icu.impl.LocaleIDParser", "com.ibm.icu.impl.LocaleIDs", "com.ibm.icu.impl.LocaleUtility",
                    "com.ibm.icu.impl.Norm2AllModes$1", "com.ibm.icu.impl.Norm2AllModes$ComposeNormalizer2", "com.ibm.icu.impl.Norm2AllModes$DecomposeNormalizer2",
                    "com.ibm.icu.impl.Norm2AllModes$FCDNormalizer2",
                    "com.ibm.icu.impl.Norm2AllModes$NFCSingleton", "com.ibm.icu.impl.Norm2AllModes$NFKCSingleton", "com.ibm.icu.impl.Norm2AllModes$NFKC_CFSingleton",
                    "com.ibm.icu.impl.Norm2AllModes$NoopNormalizer2", "com.ibm.icu.impl.Norm2AllModes$Norm2AllModesSingleton", "com.ibm.icu.impl.Norm2AllModes$Normalizer2WithImpl",
                    "com.ibm.icu.impl.Norm2AllModes", "com.ibm.icu.impl.Normalizer2Impl$1", "com.ibm.icu.impl.Normalizer2Impl$Hangul", "com.ibm.icu.impl.Normalizer2Impl$IsAcceptable",
                    "com.ibm.icu.impl.Normalizer2Impl$ReorderingBuffer", "com.ibm.icu.impl.Normalizer2Impl$UTF16Plus", "com.ibm.icu.impl.Normalizer2Impl", "com.ibm.icu.impl.OlsonTimeZone",
                    "com.ibm.icu.impl.PVecToTrieCompactHandler", "com.ibm.icu.impl.Pair", "com.ibm.icu.impl.PatternProps", "com.ibm.icu.impl.PatternTokenizer", "com.ibm.icu.impl.PluralRulesLoader",
                    "com.ibm.icu.impl.PropsVectors$1", "com.ibm.icu.impl.PropsVectors$CompactHandler", "com.ibm.icu.impl.PropsVectors$DefaultGetFoldedValue",
                    "com.ibm.icu.impl.PropsVectors$DefaultGetFoldingOffset", "com.ibm.icu.impl.PropsVectors", "com.ibm.icu.impl.Punycode", "com.ibm.icu.impl.RBBIDataWrapper$1",
                    "com.ibm.icu.impl.RBBIDataWrapper$IsAcceptable", "com.ibm.icu.impl.RBBIDataWrapper$RBBIDataHeader", "com.ibm.icu.impl.RBBIDataWrapper$RBBIStateTable",
                    "com.ibm.icu.impl.RBBIDataWrapper", "com.ibm.icu.impl.Relation$SimpleEntry", "com.ibm.icu.impl.Relation", "com.ibm.icu.impl.RelativeDateFormat$1",
                    "com.ibm.icu.impl.RelativeDateFormat$RelDateFmtDataSink", "com.ibm.icu.impl.RelativeDateFormat$URelativeString", "com.ibm.icu.impl.RelativeDateFormat",
                    "com.ibm.icu.impl.ReplaceableUCharacterIterator", "com.ibm.icu.impl.ResourceBundleWrapper$1", "com.ibm.icu.impl.ResourceBundleWrapper$2$1",
                    "com.ibm.icu.impl.ResourceBundleWrapper$2", "com.ibm.icu.impl.ResourceBundleWrapper$Loader", "com.ibm.icu.impl.ResourceBundleWrapper", "com.ibm.icu.impl.Row$R2",
                    "com.ibm.icu.impl.Row$R3", "com.ibm.icu.impl.Row$R4", "com.ibm.icu.impl.Row$R5", "com.ibm.icu.impl.Row", "com.ibm.icu.impl.RuleCharacterIterator", "com.ibm.icu.impl.SimpleCache",
                    "com.ibm.icu.impl.SimpleFilteredSentenceBreakIterator$Builder", "com.ibm.icu.impl.SimpleFilteredSentenceBreakIterator", "com.ibm.icu.impl.SimpleFormatterImpl",
                    "com.ibm.icu.impl.SoftCache", "com.ibm.icu.impl.SortedSetRelation", "com.ibm.icu.impl.StandardPlural", "com.ibm.icu.impl.StaticUnicodeSets$Key",
                    "com.ibm.icu.impl.StaticUnicodeSets$ParseDataSink", "com.ibm.icu.impl.StaticUnicodeSets", "com.ibm.icu.impl.StringPrepDataReader", "com.ibm.icu.impl.StringRange$1",
                    "com.ibm.icu.impl.StringRange$Adder", "com.ibm.icu.impl.StringRange$Range", "com.ibm.icu.impl.StringRange$Ranges", "com.ibm.icu.impl.StringRange", "com.ibm.icu.impl.StringSegment",
                    "com.ibm.icu.impl.TZDBTimeZoneNames$1", "com.ibm.icu.impl.TZDBTimeZoneNames$TZDBNameInfo", "com.ibm.icu.impl.TZDBTimeZoneNames$TZDBNameSearchHandler",
                    "com.ibm.icu.impl.TZDBTimeZoneNames$TZDBNames", "com.ibm.icu.impl.TZDBTimeZoneNames", "com.ibm.icu.impl.TextTrieMap$1", "com.ibm.icu.impl.TextTrieMap$CharIterator",
                    "com.ibm.icu.impl.TextTrieMap$LongestMatchHandler", "com.ibm.icu.impl.TextTrieMap$Node", "com.ibm.icu.impl.TextTrieMap$Output", "com.ibm.icu.impl.TextTrieMap$ResultHandler",
                    "com.ibm.icu.impl.TextTrieMap", "com.ibm.icu.impl.TimeZoneAdapter", "com.ibm.icu.impl.TimeZoneGenericNames$1", "com.ibm.icu.impl.TimeZoneGenericNames$Cache",
                    "com.ibm.icu.impl.TimeZoneGenericNames$GenericMatchInfo", "com.ibm.icu.impl.TimeZoneGenericNames$GenericNameSearchHandler", "com.ibm.icu.impl.TimeZoneGenericNames$GenericNameType",
                    "com.ibm.icu.impl.TimeZoneGenericNames$NameInfo", "com.ibm.icu.impl.TimeZoneGenericNames$Pattern", "com.ibm.icu.impl.TimeZoneGenericNames",
                    "com.ibm.icu.impl.TimeZoneNamesFactoryImpl", "com.ibm.icu.impl.TimeZoneNamesImpl$1", "com.ibm.icu.impl.TimeZoneNamesImpl$MZ2TZsCache",
                    "com.ibm.icu.impl.TimeZoneNamesImpl$MZMapEntry", "com.ibm.icu.impl.TimeZoneNamesImpl$NameInfo", "com.ibm.icu.impl.TimeZoneNamesImpl$NameSearchHandler",
                    "com.ibm.icu.impl.TimeZoneNamesImpl$TZ2MZsCache", "com.ibm.icu.impl.TimeZoneNamesImpl$ZNames$NameTypeIndex", "com.ibm.icu.impl.TimeZoneNamesImpl$ZNames",
                    "com.ibm.icu.impl.TimeZoneNamesImpl$ZNamesLoader", "com.ibm.icu.impl.TimeZoneNamesImpl$ZoneStringsLoader", "com.ibm.icu.impl.TimeZoneNamesImpl", "com.ibm.icu.impl.Trie$1",
                    "com.ibm.icu.impl.Trie$DataManipulate", "com.ibm.icu.impl.Trie$DefaultGetFoldingOffset", "com.ibm.icu.impl.Trie", "com.ibm.icu.impl.Trie2$1", "com.ibm.icu.impl.Trie2$2",
                    "com.ibm.icu.impl.Trie2$CharSequenceIterator", "com.ibm.icu.impl.Trie2$CharSequenceValues", "com.ibm.icu.impl.Trie2$Range", "com.ibm.icu.impl.Trie2$Trie2Iterator",
                    "com.ibm.icu.impl.Trie2$UTrie2Header", "com.ibm.icu.impl.Trie2$ValueMapper", "com.ibm.icu.impl.Trie2$ValueWidth", "com.ibm.icu.impl.Trie2", "com.ibm.icu.impl.Trie2Writable$1",
                    "com.ibm.icu.impl.Trie2Writable", "com.ibm.icu.impl.Trie2_16", "com.ibm.icu.impl.Trie2_32", "com.ibm.icu.impl.TrieBuilder$DataManipulate", "com.ibm.icu.impl.TrieBuilder",
                    "com.ibm.icu.impl.TrieIterator", "com.ibm.icu.impl.UBiDiProps$1", "com.ibm.icu.impl.UBiDiProps$IsAcceptable", "com.ibm.icu.impl.UBiDiProps", "com.ibm.icu.impl.UCaseProps$1",
                    "com.ibm.icu.impl.UCaseProps$ContextIterator", "com.ibm.icu.impl.UCaseProps$IsAcceptable", "com.ibm.icu.impl.UCaseProps$LatinCase", "com.ibm.icu.impl.UCaseProps",
                    "com.ibm.icu.impl.UCharArrayIterator", "com.ibm.icu.impl.UCharacterIteratorWrapper", "com.ibm.icu.impl.UCharacterName$AlgorithmName", "com.ibm.icu.impl.UCharacterName",
                    "com.ibm.icu.impl.UCharacterNameChoice", "com.ibm.icu.impl.UCharacterNameReader", "com.ibm.icu.impl.UCharacterProperty$1", "com.ibm.icu.impl.UCharacterProperty$10",
                    "com.ibm.icu.impl.UCharacterProperty$11", "com.ibm.icu.impl.UCharacterProperty$12", "com.ibm.icu.impl.UCharacterProperty$13", "com.ibm.icu.impl.UCharacterProperty$14",
                    "com.ibm.icu.impl.UCharacterProperty$15", "com.ibm.icu.impl.UCharacterProperty$16", "com.ibm.icu.impl.UCharacterProperty$17", "com.ibm.icu.impl.UCharacterProperty$18",
                    "com.ibm.icu.impl.UCharacterProperty$19", "com.ibm.icu.impl.UCharacterProperty$2", "com.ibm.icu.impl.UCharacterProperty$20", "com.ibm.icu.impl.UCharacterProperty$21",
                    "com.ibm.icu.impl.UCharacterProperty$22", "com.ibm.icu.impl.UCharacterProperty$23", "com.ibm.icu.impl.UCharacterProperty$24", "com.ibm.icu.impl.UCharacterProperty$3",
                    "com.ibm.icu.impl.UCharacterProperty$4", "com.ibm.icu.impl.UCharacterProperty$5", "com.ibm.icu.impl.UCharacterProperty$6", "com.ibm.icu.impl.UCharacterProperty$7",
                    "com.ibm.icu.impl.UCharacterProperty$8", "com.ibm.icu.impl.UCharacterProperty$9", "com.ibm.icu.impl.UCharacterProperty$BiDiIntProperty",
                    "com.ibm.icu.impl.UCharacterProperty$BinaryProperty", "com.ibm.icu.impl.UCharacterProperty$CaseBinaryProperty", "com.ibm.icu.impl.UCharacterProperty$CombiningClassIntProperty",
                    "com.ibm.icu.impl.UCharacterProperty$IntProperty", "com.ibm.icu.impl.UCharacterProperty$IsAcceptable", "com.ibm.icu.impl.UCharacterProperty$NormInertBinaryProperty",
                    "com.ibm.icu.impl.UCharacterProperty$NormQuickCheckIntProperty", "com.ibm.icu.impl.UCharacterProperty", "com.ibm.icu.impl.UCharacterUtility", "com.ibm.icu.impl.UPropertyAliases$1",
                    "com.ibm.icu.impl.UPropertyAliases$IsAcceptable", "com.ibm.icu.impl.UPropertyAliases", "com.ibm.icu.impl.URLHandler$FileURLHandler", "com.ibm.icu.impl.URLHandler$JarURLHandler",
                    "com.ibm.icu.impl.URLHandler$URLVisitor", "com.ibm.icu.impl.URLHandler", "com.ibm.icu.impl.UResource$Array", "com.ibm.icu.impl.UResource$Key", "com.ibm.icu.impl.UResource$Sink",
                    "com.ibm.icu.impl.UResource$Table", "com.ibm.icu.impl.UResource$Value", "com.ibm.icu.impl.UResource", "com.ibm.icu.impl.USerializedSet", "com.ibm.icu.impl.UTS46",
                    "com.ibm.icu.impl.UnicodeRegex$1", "com.ibm.icu.impl.UnicodeRegex", "com.ibm.icu.impl.UnicodeSetStringSpan$OffsetList", "com.ibm.icu.impl.UnicodeSetStringSpan",
                    "com.ibm.icu.impl.Utility", "com.ibm.icu.impl.ValidIdentifiers$Datasubtype", "com.ibm.icu.impl.ValidIdentifiers$Datatype", "com.ibm.icu.impl.ValidIdentifiers$ValidityData",
                    "com.ibm.icu.impl.ValidIdentifiers$ValiditySet", "com.ibm.icu.impl.ValidIdentifiers", "com.ibm.icu.impl.ZoneMeta$1", "com.ibm.icu.impl.ZoneMeta$CustomTimeZoneCache",
                    "com.ibm.icu.impl.ZoneMeta$SystemTimeZoneCache", "com.ibm.icu.impl.ZoneMeta", "com.ibm.icu.impl.data.HolidayBundle", "com.ibm.icu.impl.data.HolidayBundle_da",
                    "com.ibm.icu.impl.data.HolidayBundle_da_DK", "com.ibm.icu.impl.data.HolidayBundle_de", "com.ibm.icu.impl.data.HolidayBundle_de_AT", "com.ibm.icu.impl.data.HolidayBundle_de_DE",
                    "com.ibm.icu.impl.data.HolidayBundle_el", "com.ibm.icu.impl.data.HolidayBundle_el_GR", "com.ibm.icu.impl.data.HolidayBundle_en", "com.ibm.icu.impl.data.HolidayBundle_en_CA",
                    "com.ibm.icu.impl.data.HolidayBundle_en_GB", "com.ibm.icu.impl.data.HolidayBundle_en_US", "com.ibm.icu.impl.data.HolidayBundle_es", "com.ibm.icu.impl.data.HolidayBundle_es_MX",
                    "com.ibm.icu.impl.data.HolidayBundle_fr", "com.ibm.icu.impl.data.HolidayBundle_fr_CA", "com.ibm.icu.impl.data.HolidayBundle_fr_FR", "com.ibm.icu.impl.data.HolidayBundle_it",
                    "com.ibm.icu.impl.data.HolidayBundle_it_IT", "com.ibm.icu.impl.data.HolidayBundle_iw", "com.ibm.icu.impl.data.HolidayBundle_iw_IL", "com.ibm.icu.impl.data.HolidayBundle_ja_JP",
                    "com.ibm.icu.impl.data.ResourceReader", "com.ibm.icu.impl.data.TokenIterator", "com.ibm.icu.impl.duration.BasicDurationFormat", "com.ibm.icu.impl.duration.BasicDurationFormatter",
                    "com.ibm.icu.impl.duration.BasicDurationFormatterFactory", "com.ibm.icu.impl.duration.BasicPeriodBuilderFactory$Settings", "com.ibm.icu.impl.duration.BasicPeriodBuilderFactory",
                    "com.ibm.icu.impl.duration.BasicPeriodFormatter", "com.ibm.icu.impl.duration.BasicPeriodFormatterFactory$Customizations", "com.ibm.icu.impl.duration.BasicPeriodFormatterFactory",
                    "com.ibm.icu.impl.duration.BasicPeriodFormatterService", "com.ibm.icu.impl.duration.DateFormatter", "com.ibm.icu.impl.duration.DurationFormatter",
                    "com.ibm.icu.impl.duration.DurationFormatterFactory", "com.ibm.icu.impl.duration.FixedUnitBuilder", "com.ibm.icu.impl.duration.MultiUnitBuilder",
                    "com.ibm.icu.impl.duration.OneOrTwoUnitBuilder", "com.ibm.icu.impl.duration.Period", "com.ibm.icu.impl.duration.PeriodBuilder", "com.ibm.icu.impl.duration.PeriodBuilderFactory",
                    "com.ibm.icu.impl.duration.PeriodBuilderImpl", "com.ibm.icu.impl.duration.PeriodFormatter", "com.ibm.icu.impl.duration.PeriodFormatterFactory",
                    "com.ibm.icu.impl.duration.PeriodFormatterService", "com.ibm.icu.impl.duration.SingleUnitBuilder", "com.ibm.icu.impl.duration.TimeUnit",
                    "com.ibm.icu.impl.duration.TimeUnitConstants", "com.ibm.icu.impl.duration.impl.DataRecord$ECountVariant", "com.ibm.icu.impl.duration.impl.DataRecord$EDecimalHandling",
                    "com.ibm.icu.impl.duration.impl.DataRecord$EFractionHandling", "com.ibm.icu.impl.duration.impl.DataRecord$EGender", "com.ibm.icu.impl.duration.impl.DataRecord$EHalfPlacement",
                    "com.ibm.icu.impl.duration.impl.DataRecord$EHalfSupport", "com.ibm.icu.impl.duration.impl.DataRecord$EMilliSupport", "com.ibm.icu.impl.duration.impl.DataRecord$ENumberSystem",
                    "com.ibm.icu.impl.duration.impl.DataRecord$EPluralization", "com.ibm.icu.impl.duration.impl.DataRecord$ESeparatorVariant",
                    "com.ibm.icu.impl.duration.impl.DataRecord$ETimeDirection", "com.ibm.icu.impl.duration.impl.DataRecord$ETimeLimit", "com.ibm.icu.impl.duration.impl.DataRecord$EUnitVariant",
                    "com.ibm.icu.impl.duration.impl.DataRecord$EZeroHandling", "com.ibm.icu.impl.duration.impl.DataRecord$ScopeData", "com.ibm.icu.impl.duration.impl.DataRecord",
                    "com.ibm.icu.impl.duration.impl.PeriodFormatterData", "com.ibm.icu.impl.duration.impl.PeriodFormatterDataService", "com.ibm.icu.impl.duration.impl.RecordReader",
                    "com.ibm.icu.impl.duration.impl.RecordWriter", "com.ibm.icu.impl.duration.impl.ResourceBasedPeriodFormatterDataService", "com.ibm.icu.impl.duration.impl.Utils$ChineseDigits",
                    "com.ibm.icu.impl.duration.impl.Utils", "com.ibm.icu.impl.duration.impl.XMLRecordReader", "com.ibm.icu.impl.duration.impl.XMLRecordWriter",
                    "com.ibm.icu.impl.locale.AsciiUtil$CaseInsensitiveKey", "com.ibm.icu.impl.locale.AsciiUtil", "com.ibm.icu.impl.locale.BaseLocale$1", "com.ibm.icu.impl.locale.BaseLocale$Cache",
                    "com.ibm.icu.impl.locale.BaseLocale$Key", "com.ibm.icu.impl.locale.BaseLocale", "com.ibm.icu.impl.locale.Extension",
                    "com.ibm.icu.impl.locale.InternalLocaleBuilder$CaseInsensitiveChar", "com.ibm.icu.impl.locale.InternalLocaleBuilder$CaseInsensitiveString",
                    "com.ibm.icu.impl.locale.InternalLocaleBuilder", "com.ibm.icu.impl.locale.KeyTypeData$1", "com.ibm.icu.impl.locale.KeyTypeData$CodepointsTypeHandler",
                    "com.ibm.icu.impl.locale.KeyTypeData$KeyData", "com.ibm.icu.impl.locale.KeyTypeData$KeyInfoType", "com.ibm.icu.impl.locale.KeyTypeData$PrivateUseKeyValueTypeHandler",
                    "com.ibm.icu.impl.locale.KeyTypeData$ReorderCodeTypeHandler", "com.ibm.icu.impl.locale.KeyTypeData$RgKeyValueTypeHandler", "com.ibm.icu.impl.locale.KeyTypeData$SpecialType",
                    "com.ibm.icu.impl.locale.KeyTypeData$SpecialTypeHandler", "com.ibm.icu.impl.locale.KeyTypeData$SubdivisionKeyValueTypeHandler", "com.ibm.icu.impl.locale.KeyTypeData$Type",
                    "com.ibm.icu.impl.locale.KeyTypeData$TypeInfoType", "com.ibm.icu.impl.locale.KeyTypeData$ValueType", "com.ibm.icu.impl.locale.KeyTypeData", "com.ibm.icu.impl.locale.LanguageTag",
                    "com.ibm.icu.impl.locale.LocaleExtensions", "com.ibm.icu.impl.locale.LocaleObjectCache$CacheEntry", "com.ibm.icu.impl.locale.LocaleObjectCache",
                    "com.ibm.icu.impl.locale.LocaleSyntaxException", "com.ibm.icu.impl.locale.LocaleValidityChecker$1", "com.ibm.icu.impl.locale.LocaleValidityChecker$SpecialCase",
                    "com.ibm.icu.impl.locale.LocaleValidityChecker$Where", "com.ibm.icu.impl.locale.LocaleValidityChecker", "com.ibm.icu.impl.locale.ParseStatus",
                    "com.ibm.icu.impl.locale.StringTokenIterator", "com.ibm.icu.impl.locale.UnicodeLocaleExtension", "com.ibm.icu.impl.locale.XCldrStub$1",
                    "com.ibm.icu.impl.locale.XCldrStub$CollectionUtilities", "com.ibm.icu.impl.locale.XCldrStub$FileUtilities", "com.ibm.icu.impl.locale.XCldrStub$HashMultimap",
                    "com.ibm.icu.impl.locale.XCldrStub$ImmutableMap", "com.ibm.icu.impl.locale.XCldrStub$ImmutableMultimap", "com.ibm.icu.impl.locale.XCldrStub$ImmutableSet",
                    "com.ibm.icu.impl.locale.XCldrStub$Joiner", "com.ibm.icu.impl.locale.XCldrStub$LinkedHashMultimap", "com.ibm.icu.impl.locale.XCldrStub$Multimap",
                    "com.ibm.icu.impl.locale.XCldrStub$MultimapIterator", "com.ibm.icu.impl.locale.XCldrStub$Multimaps", "com.ibm.icu.impl.locale.XCldrStub$Predicate",
                    "com.ibm.icu.impl.locale.XCldrStub$RegexUtilities", "com.ibm.icu.impl.locale.XCldrStub$ReusableEntry", "com.ibm.icu.impl.locale.XCldrStub$Splitter",
                    "com.ibm.icu.impl.locale.XCldrStub$TreeMultimap", "com.ibm.icu.impl.locale.XCldrStub", "com.ibm.icu.impl.locale.XLikelySubtags$Aliases",
                    "com.ibm.icu.impl.locale.XLikelySubtags$LSR", "com.ibm.icu.impl.locale.XLikelySubtags$Maker$1", "com.ibm.icu.impl.locale.XLikelySubtags$Maker$2",
                    "com.ibm.icu.impl.locale.XLikelySubtags$Maker", "com.ibm.icu.impl.locale.XLikelySubtags", "com.ibm.icu.impl.locale.XLocaleDistance$1",
                    "com.ibm.icu.impl.locale.XLocaleDistance$AddSub", "com.ibm.icu.impl.locale.XLocaleDistance$CompactAndImmutablizer", "com.ibm.icu.impl.locale.XLocaleDistance$CopyIfEmpty",
                    "com.ibm.icu.impl.locale.XLocaleDistance$DistanceNode", "com.ibm.icu.impl.locale.XLocaleDistance$DistanceOption", "com.ibm.icu.impl.locale.XLocaleDistance$DistanceTable",
                    "com.ibm.icu.impl.locale.XLocaleDistance$IdMakerFull", "com.ibm.icu.impl.locale.XLocaleDistance$IdMapper", "com.ibm.icu.impl.locale.XLocaleDistance$RegionMapper$Builder",
                    "com.ibm.icu.impl.locale.XLocaleDistance$RegionMapper", "com.ibm.icu.impl.locale.XLocaleDistance$RegionSet$Operation", "com.ibm.icu.impl.locale.XLocaleDistance$RegionSet",
                    "com.ibm.icu.impl.locale.XLocaleDistance$StringDistanceNode", "com.ibm.icu.impl.locale.XLocaleDistance$StringDistanceTable", "com.ibm.icu.impl.locale.XLocaleDistance",
                    "com.ibm.icu.impl.locale.XLocaleMatcher$1", "com.ibm.icu.impl.locale.XLocaleMatcher$Builder", "com.ibm.icu.impl.locale.XLocaleMatcher",
                    "com.ibm.icu.impl.number.AffixPatternProvider$Flags", "com.ibm.icu.impl.number.AffixPatternProvider", "com.ibm.icu.impl.number.AffixUtils$SymbolProvider",
                    "com.ibm.icu.impl.number.AffixUtils$TokenConsumer", "com.ibm.icu.impl.number.AffixUtils", "com.ibm.icu.impl.number.CompactData$CompactDataSink",
                    "com.ibm.icu.impl.number.CompactData$CompactType", "com.ibm.icu.impl.number.CompactData", "com.ibm.icu.impl.number.ConstantAffixModifier",
                    "com.ibm.icu.impl.number.ConstantMultiFieldModifier", "com.ibm.icu.impl.number.CurrencyPluralInfoAffixProvider", "com.ibm.icu.impl.number.CurrencySpacingEnabledModifier",
                    "com.ibm.icu.impl.number.CustomSymbolCurrency", "com.ibm.icu.impl.number.DecimalFormatProperties$ParseMode", "com.ibm.icu.impl.number.DecimalFormatProperties",
                    "com.ibm.icu.impl.number.DecimalQuantity", "com.ibm.icu.impl.number.DecimalQuantity_AbstractBCD$1", "com.ibm.icu.impl.number.DecimalQuantity_AbstractBCD",
                    "com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD", "com.ibm.icu.impl.number.Grouper$1", "com.ibm.icu.impl.number.Grouper",
                    "com.ibm.icu.impl.number.LocalizedNumberFormatterAsFormat$Proxy", "com.ibm.icu.impl.number.LocalizedNumberFormatterAsFormat",
                    "com.ibm.icu.impl.number.LongNameHandler$PluralTableSink", "com.ibm.icu.impl.number.LongNameHandler", "com.ibm.icu.impl.number.MacroProps", "com.ibm.icu.impl.number.MicroProps",
                    "com.ibm.icu.impl.number.MicroPropsGenerator", "com.ibm.icu.impl.number.MicroPropsMutator", "com.ibm.icu.impl.number.Modifier", "com.ibm.icu.impl.number.MultiplierFormatHandler",
                    "com.ibm.icu.impl.number.MultiplierProducer", "com.ibm.icu.impl.number.MutablePatternModifier$ImmutablePatternModifier", "com.ibm.icu.impl.number.MutablePatternModifier",
                    "com.ibm.icu.impl.number.NumberStringBuilder", "com.ibm.icu.impl.number.Padder$1", "com.ibm.icu.impl.number.Padder$PadPosition", "com.ibm.icu.impl.number.Padder",
                    "com.ibm.icu.impl.number.ParameterizedModifier", "com.ibm.icu.impl.number.PatternStringParser$1", "com.ibm.icu.impl.number.PatternStringParser$ParsedPatternInfo",
                    "com.ibm.icu.impl.number.PatternStringParser$ParsedSubpatternInfo", "com.ibm.icu.impl.number.PatternStringParser$ParserState", "com.ibm.icu.impl.number.PatternStringParser",
                    "com.ibm.icu.impl.number.PatternStringUtils$1", "com.ibm.icu.impl.number.PatternStringUtils", "com.ibm.icu.impl.number.Properties",
                    "com.ibm.icu.impl.number.PropertiesAffixPatternProvider", "com.ibm.icu.impl.number.RoundingUtils", "com.ibm.icu.impl.number.SimpleModifier",
                    "com.ibm.icu.impl.number.parse.AffixMatcher$1", "com.ibm.icu.impl.number.parse.AffixMatcher", "com.ibm.icu.impl.number.parse.AffixPatternMatcher",
                    "com.ibm.icu.impl.number.parse.AffixTokenMatcherFactory", "com.ibm.icu.impl.number.parse.CodePointMatcher", "com.ibm.icu.impl.number.parse.CombinedCurrencyMatcher",
                    "com.ibm.icu.impl.number.parse.DecimalMatcher", "com.ibm.icu.impl.number.parse.IgnorablesMatcher", "com.ibm.icu.impl.number.parse.InfinityMatcher",
                    "com.ibm.icu.impl.number.parse.MinusSignMatcher", "com.ibm.icu.impl.number.parse.MultiplierParseHandler", "com.ibm.icu.impl.number.parse.NanMatcher",
                    "com.ibm.icu.impl.number.parse.NumberParseMatcher$Flexible", "com.ibm.icu.impl.number.parse.NumberParseMatcher", "com.ibm.icu.impl.number.parse.NumberParserImpl",
                    "com.ibm.icu.impl.number.parse.PaddingMatcher", "com.ibm.icu.impl.number.parse.ParsedNumber$1", "com.ibm.icu.impl.number.parse.ParsedNumber",
                    "com.ibm.icu.impl.number.parse.ParsingUtils", "com.ibm.icu.impl.number.parse.PercentMatcher", "com.ibm.icu.impl.number.parse.PermilleMatcher",
                    "com.ibm.icu.impl.number.parse.PlusSignMatcher", "com.ibm.icu.impl.number.parse.RequireAffixValidator", "com.ibm.icu.impl.number.parse.RequireCurrencyValidator",
                    "com.ibm.icu.impl.number.parse.RequireDecimalSeparatorValidator", "com.ibm.icu.impl.number.parse.RequireNumberValidator", "com.ibm.icu.impl.number.parse.ScientificMatcher",
                    "com.ibm.icu.impl.number.parse.SeriesMatcher", "com.ibm.icu.impl.number.parse.SymbolMatcher", "com.ibm.icu.impl.number.parse.ValidationMatcher", "com.ibm.icu.lang.CharSequences",
                    "com.ibm.icu.lang.UCharacter$1", "com.ibm.icu.lang.UCharacter$BidiPairedBracketType", "com.ibm.icu.lang.UCharacter$DecompositionType",
                    "com.ibm.icu.lang.UCharacter$DummyValueIterator",
                    "com.ibm.icu.lang.UCharacter$EastAsianWidth", "com.ibm.icu.lang.UCharacter$GraphemeClusterBreak", "com.ibm.icu.lang.UCharacter$HangulSyllableType",
                    "com.ibm.icu.lang.UCharacter$JoiningGroup", "com.ibm.icu.lang.UCharacter$JoiningType", "com.ibm.icu.lang.UCharacter$LineBreak", "com.ibm.icu.lang.UCharacter$NumericType",
                    "com.ibm.icu.lang.UCharacter$SentenceBreak", "com.ibm.icu.lang.UCharacter$UCharacterTypeIterator$MaskType", "com.ibm.icu.lang.UCharacter$UCharacterTypeIterator",
                    "com.ibm.icu.lang.UCharacter$UnicodeBlock", "com.ibm.icu.lang.UCharacter$WordBreak", "com.ibm.icu.lang.UCharacter", "com.ibm.icu.lang.UCharacterCategory",
                    "com.ibm.icu.lang.UCharacterDirection", "com.ibm.icu.lang.UCharacterEnums$ECharacterCategory", "com.ibm.icu.lang.UCharacterEnums$ECharacterDirection",
                    "com.ibm.icu.lang.UCharacterEnums", "com.ibm.icu.lang.UCharacterNameIterator", "com.ibm.icu.lang.UProperty$NameChoice", "com.ibm.icu.lang.UProperty",
                    "com.ibm.icu.lang.UScript$ScriptMetadata", "com.ibm.icu.lang.UScript$ScriptUsage", "com.ibm.icu.lang.UScript", "com.ibm.icu.lang.UScriptRun$ParenStackEntry",
                    "com.ibm.icu.lang.UScriptRun", "com.ibm.icu.math.BigDecimal", "com.ibm.icu.math.MathContext", "com.ibm.icu.number.CompactNotation$1",
                    "com.ibm.icu.number.CompactNotation$CompactHandler", "com.ibm.icu.number.CompactNotation", "com.ibm.icu.number.CurrencyPrecision", "com.ibm.icu.number.CurrencyRounder",
                    "com.ibm.icu.number.FormattedNumber", "com.ibm.icu.number.FractionPrecision", "com.ibm.icu.number.FractionRounder", "com.ibm.icu.number.IntegerWidth",
                    "com.ibm.icu.number.LocalizedNumberFormatter", "com.ibm.icu.number.Notation", "com.ibm.icu.number.NumberFormatter$DecimalSeparatorDisplay",
                    "com.ibm.icu.number.NumberFormatter$GroupingStrategy", "com.ibm.icu.number.NumberFormatter$SignDisplay", "com.ibm.icu.number.NumberFormatter$UnitWidth",
                    "com.ibm.icu.number.NumberFormatter", "com.ibm.icu.number.NumberFormatterImpl", "com.ibm.icu.number.NumberFormatterSettings", "com.ibm.icu.number.NumberPropertyMapper",
                    "com.ibm.icu.number.NumberSkeletonImpl$1", "com.ibm.icu.number.NumberSkeletonImpl$2", "com.ibm.icu.number.NumberSkeletonImpl$BlueprintHelpers",
                    "com.ibm.icu.number.NumberSkeletonImpl$EnumToStemString", "com.ibm.icu.number.NumberSkeletonImpl$GeneratorHelpers", "com.ibm.icu.number.NumberSkeletonImpl$ParseState",
                    "com.ibm.icu.number.NumberSkeletonImpl$StemEnum", "com.ibm.icu.number.NumberSkeletonImpl$StemToObject", "com.ibm.icu.number.NumberSkeletonImpl",
                    "com.ibm.icu.number.Precision$CurrencyRounderImpl", "com.ibm.icu.number.Precision$FracSigRounderImpl", "com.ibm.icu.number.Precision$FractionRounderImpl",
                    "com.ibm.icu.number.Precision$IncrementRounderImpl", "com.ibm.icu.number.Precision$InfiniteRounderImpl", "com.ibm.icu.number.Precision$PassThroughRounderImpl",
                    "com.ibm.icu.number.Precision$SignificantRounderImpl", "com.ibm.icu.number.Precision", "com.ibm.icu.number.Rounder", "com.ibm.icu.number.Scale",
                    "com.ibm.icu.number.ScientificNotation$1", "com.ibm.icu.number.ScientificNotation$ScientificHandler", "com.ibm.icu.number.ScientificNotation$ScientificModifier",
                    "com.ibm.icu.number.ScientificNotation", "com.ibm.icu.number.SimpleNotation", "com.ibm.icu.number.SkeletonSyntaxException", "com.ibm.icu.number.UnlocalizedNumberFormatter",
                    "com.ibm.icu.text.AbsoluteValueSubstitution", "com.ibm.icu.text.ArabicShaping", "com.ibm.icu.text.ArabicShapingException", "com.ibm.icu.text.Bidi$1",
                    "com.ibm.icu.text.Bidi$BracketData", "com.ibm.icu.text.Bidi$ImpTabPair", "com.ibm.icu.text.Bidi$InsertPoints", "com.ibm.icu.text.Bidi$IsoRun", "com.ibm.icu.text.Bidi$Isolate",
                    "com.ibm.icu.text.Bidi$LevState", "com.ibm.icu.text.Bidi$Opening", "com.ibm.icu.text.Bidi$Point", "com.ibm.icu.text.Bidi", "com.ibm.icu.text.BidiClassifier",
                    "com.ibm.icu.text.BidiLine", "com.ibm.icu.text.BidiRun", "com.ibm.icu.text.BidiTransform$1", "com.ibm.icu.text.BidiTransform$Mirroring", "com.ibm.icu.text.BidiTransform$Order",
                    "com.ibm.icu.text.BidiTransform$ReorderingScheme$1", "com.ibm.icu.text.BidiTransform$ReorderingScheme$10", "com.ibm.icu.text.BidiTransform$ReorderingScheme$11",
                    "com.ibm.icu.text.BidiTransform$ReorderingScheme$12", "com.ibm.icu.text.BidiTransform$ReorderingScheme$13", "com.ibm.icu.text.BidiTransform$ReorderingScheme$14",
                    "com.ibm.icu.text.BidiTransform$ReorderingScheme$15", "com.ibm.icu.text.BidiTransform$ReorderingScheme$16", "com.ibm.icu.text.BidiTransform$ReorderingScheme$2",
                    "com.ibm.icu.text.BidiTransform$ReorderingScheme$3", "com.ibm.icu.text.BidiTransform$ReorderingScheme$4", "com.ibm.icu.text.BidiTransform$ReorderingScheme$5",
                    "com.ibm.icu.text.BidiTransform$ReorderingScheme$6", "com.ibm.icu.text.BidiTransform$ReorderingScheme$7", "com.ibm.icu.text.BidiTransform$ReorderingScheme$8",
                    "com.ibm.icu.text.BidiTransform$ReorderingScheme$9", "com.ibm.icu.text.BidiTransform$ReorderingScheme", "com.ibm.icu.text.BidiTransform", "com.ibm.icu.text.BidiWriter",
                    "com.ibm.icu.text.BreakIterator$BreakIteratorCache", "com.ibm.icu.text.BreakIterator$BreakIteratorServiceShim", "com.ibm.icu.text.BreakIterator",
                    "com.ibm.icu.text.BreakIteratorFactory$BFService$1RBBreakIteratorFactory", "com.ibm.icu.text.BreakIteratorFactory$BFService", "com.ibm.icu.text.BreakIteratorFactory",
                    "com.ibm.icu.text.BurmeseBreakEngine", "com.ibm.icu.text.BytesDictionaryMatcher", "com.ibm.icu.text.CanonicalIterator", "com.ibm.icu.text.CaseMap$1",
                    "com.ibm.icu.text.CaseMap$Fold", "com.ibm.icu.text.CaseMap$Lower", "com.ibm.icu.text.CaseMap$Title", "com.ibm.icu.text.CaseMap$Upper", "com.ibm.icu.text.CaseMap",
                    "com.ibm.icu.text.CharsDictionaryMatcher", "com.ibm.icu.text.CharsetDetector$CSRecognizerInfo", "com.ibm.icu.text.CharsetDetector", "com.ibm.icu.text.CharsetMatch",
                    "com.ibm.icu.text.CharsetRecog_2022$CharsetRecog_2022CN", "com.ibm.icu.text.CharsetRecog_2022$CharsetRecog_2022JP", "com.ibm.icu.text.CharsetRecog_2022$CharsetRecog_2022KR",
                    "com.ibm.icu.text.CharsetRecog_2022", "com.ibm.icu.text.CharsetRecog_UTF8", "com.ibm.icu.text.CharsetRecog_Unicode$CharsetRecog_UTF_16_BE",
                    "com.ibm.icu.text.CharsetRecog_Unicode$CharsetRecog_UTF_16_LE", "com.ibm.icu.text.CharsetRecog_Unicode$CharsetRecog_UTF_32",
                    "com.ibm.icu.text.CharsetRecog_Unicode$CharsetRecog_UTF_32_BE", "com.ibm.icu.text.CharsetRecog_Unicode$CharsetRecog_UTF_32_LE", "com.ibm.icu.text.CharsetRecog_Unicode",
                    "com.ibm.icu.text.CharsetRecog_mbcs$CharsetRecog_big5", "com.ibm.icu.text.CharsetRecog_mbcs$CharsetRecog_euc$CharsetRecog_euc_jp",
                    "com.ibm.icu.text.CharsetRecog_mbcs$CharsetRecog_euc$CharsetRecog_euc_kr", "com.ibm.icu.text.CharsetRecog_mbcs$CharsetRecog_euc",
                    "com.ibm.icu.text.CharsetRecog_mbcs$CharsetRecog_gb_18030", "com.ibm.icu.text.CharsetRecog_mbcs$CharsetRecog_sjis", "com.ibm.icu.text.CharsetRecog_mbcs$iteratedChar",
                    "com.ibm.icu.text.CharsetRecog_mbcs", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_1", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_2",
                    "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_5", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_5_ru", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_6",
                    "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_6_ar", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_7", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_7_el",
                    "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_8", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_8_I_he",
                    "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_8_he", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_9", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_8859_9_tr",
                    "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_IBM420_ar", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_IBM420_ar_ltr",
                    "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_IBM420_ar_rtl", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_IBM424_he",
                    "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_IBM424_he_ltr", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_IBM424_he_rtl",
                    "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_KOI8_R", "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_windows_1251",
                    "com.ibm.icu.text.CharsetRecog_sbcs$CharsetRecog_windows_1256", "com.ibm.icu.text.CharsetRecog_sbcs$NGramParser", "com.ibm.icu.text.CharsetRecog_sbcs$NGramParser_IBM420",
                    "com.ibm.icu.text.CharsetRecog_sbcs$NGramsPlusLang", "com.ibm.icu.text.CharsetRecog_sbcs", "com.ibm.icu.text.CharsetRecognizer", "com.ibm.icu.text.ChineseDateFormat$Field",
                    "com.ibm.icu.text.ChineseDateFormat", "com.ibm.icu.text.ChineseDateFormatSymbols", "com.ibm.icu.text.CjkBreakEngine", "com.ibm.icu.text.CompactDecimalFormat$CompactStyle",
                    "com.ibm.icu.text.CompactDecimalFormat", "com.ibm.icu.text.ComposedCharIter", "com.ibm.icu.text.CurrencyDisplayNames", "com.ibm.icu.text.CurrencyFormat",
                    "com.ibm.icu.text.CurrencyMetaInfo$CurrencyDigits", "com.ibm.icu.text.CurrencyMetaInfo$CurrencyFilter", "com.ibm.icu.text.CurrencyMetaInfo$CurrencyInfo",
                    "com.ibm.icu.text.CurrencyMetaInfo", "com.ibm.icu.text.CurrencyPluralInfo", "com.ibm.icu.text.DateFormat$BooleanAttribute", "com.ibm.icu.text.DateFormat$Field",
                    "com.ibm.icu.text.DateFormat", "com.ibm.icu.text.DateFormatSymbols$1", "com.ibm.icu.text.DateFormatSymbols$CalendarDataSink$AliasType",
                    "com.ibm.icu.text.DateFormatSymbols$CalendarDataSink", "com.ibm.icu.text.DateFormatSymbols$CapitalizationContextUsage", "com.ibm.icu.text.DateFormatSymbols",
                    "com.ibm.icu.text.DateIntervalFormat$BestMatchInfo", "com.ibm.icu.text.DateIntervalFormat$SkeletonAndItsBestMatch", "com.ibm.icu.text.DateIntervalFormat",
                    "com.ibm.icu.text.DateIntervalInfo$DateIntervalSink", "com.ibm.icu.text.DateIntervalInfo$PatternInfo", "com.ibm.icu.text.DateIntervalInfo",
                    "com.ibm.icu.text.DateTimePatternGenerator$1", "com.ibm.icu.text.DateTimePatternGenerator$AppendItemFormatsSink", "com.ibm.icu.text.DateTimePatternGenerator$AppendItemNamesSink",
                    "com.ibm.icu.text.DateTimePatternGenerator$AvailableFormatsSink", "com.ibm.icu.text.DateTimePatternGenerator$DTPGflags",
                    "com.ibm.icu.text.DateTimePatternGenerator$DateTimeMatcher", "com.ibm.icu.text.DateTimePatternGenerator$DayPeriodAllowedHoursSink",
                    "com.ibm.icu.text.DateTimePatternGenerator$DisplayWidth", "com.ibm.icu.text.DateTimePatternGenerator$DistanceInfo", "com.ibm.icu.text.DateTimePatternGenerator$FormatParser",
                    "com.ibm.icu.text.DateTimePatternGenerator$PatternInfo", "com.ibm.icu.text.DateTimePatternGenerator$PatternWithMatcher",
                    "com.ibm.icu.text.DateTimePatternGenerator$PatternWithSkeletonFlag", "com.ibm.icu.text.DateTimePatternGenerator$SkeletonFields",
                    "com.ibm.icu.text.DateTimePatternGenerator$VariableField", "com.ibm.icu.text.DateTimePatternGenerator", "com.ibm.icu.text.DecimalFormat$PropertySetter",
                    "com.ibm.icu.text.DecimalFormat", "com.ibm.icu.text.DecimalFormatSymbols$1", "com.ibm.icu.text.DecimalFormatSymbols$CacheData",
                    "com.ibm.icu.text.DecimalFormatSymbols$DecFmtDataSink", "com.ibm.icu.text.DecimalFormatSymbols", "com.ibm.icu.text.DictionaryBreakEngine$DequeI",
                    "com.ibm.icu.text.DictionaryBreakEngine$PossibleWord", "com.ibm.icu.text.DictionaryBreakEngine", "com.ibm.icu.text.DictionaryData", "com.ibm.icu.text.DictionaryMatcher",
                    "com.ibm.icu.text.DisplayContext$Type", "com.ibm.icu.text.DisplayContext", "com.ibm.icu.text.DurationFormat", "com.ibm.icu.text.Edits$1", "com.ibm.icu.text.Edits$Iterator",
                    "com.ibm.icu.text.Edits", "com.ibm.icu.text.FilteredBreakIteratorBuilder", "com.ibm.icu.text.FilteredNormalizer2", "com.ibm.icu.text.FractionalPartSubstitution",
                    "com.ibm.icu.text.IDNA$Error", "com.ibm.icu.text.IDNA$Info", "com.ibm.icu.text.IDNA", "com.ibm.icu.text.IntegralPartSubstitution", "com.ibm.icu.text.KhmerBreakEngine",
                    "com.ibm.icu.text.LanguageBreakEngine", "com.ibm.icu.text.LaoBreakEngine", "com.ibm.icu.text.ListFormatter$1", "com.ibm.icu.text.ListFormatter$Cache",
                    "com.ibm.icu.text.ListFormatter$FormattedListBuilder", "com.ibm.icu.text.ListFormatter$Style", "com.ibm.icu.text.ListFormatter", "com.ibm.icu.text.LocaleDisplayNames$1",
                    "com.ibm.icu.text.LocaleDisplayNames$DialectHandling", "com.ibm.icu.text.LocaleDisplayNames$LastResortLocaleDisplayNames",
                    "com.ibm.icu.text.LocaleDisplayNames$UiListItem$UiListItemComparator", "com.ibm.icu.text.LocaleDisplayNames$UiListItem", "com.ibm.icu.text.LocaleDisplayNames",
                    "com.ibm.icu.text.MeasureFormat$FormatWidth", "com.ibm.icu.text.MeasureFormat$MeasureProxy", "com.ibm.icu.text.MeasureFormat$NumberFormatterCacheEntry",
                    "com.ibm.icu.text.MeasureFormat$NumericFormatters", "com.ibm.icu.text.MeasureFormat", "com.ibm.icu.text.MessageFormat$1", "com.ibm.icu.text.MessageFormat$AppendableWrapper",
                    "com.ibm.icu.text.MessageFormat$AttributeAndPosition", "com.ibm.icu.text.MessageFormat$Field", "com.ibm.icu.text.MessageFormat$PluralSelectorContext",
                    "com.ibm.icu.text.MessageFormat$PluralSelectorProvider", "com.ibm.icu.text.MessageFormat", "com.ibm.icu.text.MessagePattern$1", "com.ibm.icu.text.MessagePattern$ApostropheMode",
                    "com.ibm.icu.text.MessagePattern$ArgType", "com.ibm.icu.text.MessagePattern$Part$Type", "com.ibm.icu.text.MessagePattern$Part", "com.ibm.icu.text.MessagePattern",
                    "com.ibm.icu.text.MessagePatternUtil$1", "com.ibm.icu.text.MessagePatternUtil$ArgNode", "com.ibm.icu.text.MessagePatternUtil$ComplexArgStyleNode",
                    "com.ibm.icu.text.MessagePatternUtil$MessageContentsNode$Type", "com.ibm.icu.text.MessagePatternUtil$MessageContentsNode", "com.ibm.icu.text.MessagePatternUtil$MessageNode",
                    "com.ibm.icu.text.MessagePatternUtil$Node", "com.ibm.icu.text.MessagePatternUtil$TextNode", "com.ibm.icu.text.MessagePatternUtil$VariantNode",
                    "com.ibm.icu.text.MessagePatternUtil", "com.ibm.icu.text.ModulusSubstitution", "com.ibm.icu.text.MultiplierSubstitution", "com.ibm.icu.text.NFRule", "com.ibm.icu.text.NFRuleSet",
                    "com.ibm.icu.text.NFSubstitution", "com.ibm.icu.text.Normalizer$1", "com.ibm.icu.text.Normalizer$CharsAppendable", "com.ibm.icu.text.Normalizer$CmpEquivLevel",
                    "com.ibm.icu.text.Normalizer$FCD32ModeImpl", "com.ibm.icu.text.Normalizer$FCDMode", "com.ibm.icu.text.Normalizer$FCDModeImpl", "com.ibm.icu.text.Normalizer$Mode",
                    "com.ibm.icu.text.Normalizer$ModeImpl", "com.ibm.icu.text.Normalizer$NFC32ModeImpl", "com.ibm.icu.text.Normalizer$NFCMode", "com.ibm.icu.text.Normalizer$NFCModeImpl",
                    "com.ibm.icu.text.Normalizer$NFD32ModeImpl", "com.ibm.icu.text.Normalizer$NFDMode", "com.ibm.icu.text.Normalizer$NFDModeImpl", "com.ibm.icu.text.Normalizer$NFKC32ModeImpl",
                    "com.ibm.icu.text.Normalizer$NFKCMode", "com.ibm.icu.text.Normalizer$NFKCModeImpl", "com.ibm.icu.text.Normalizer$NFKD32ModeImpl", "com.ibm.icu.text.Normalizer$NFKDMode",
                    "com.ibm.icu.text.Normalizer$NFKDModeImpl", "com.ibm.icu.text.Normalizer$NONEMode", "com.ibm.icu.text.Normalizer$QuickCheckResult", "com.ibm.icu.text.Normalizer$Unicode32",
                    "com.ibm.icu.text.Normalizer", "com.ibm.icu.text.Normalizer2$1", "com.ibm.icu.text.Normalizer2$Mode", "com.ibm.icu.text.Normalizer2",
                    "com.ibm.icu.text.NumberFormat$Field",
                    "com.ibm.icu.text.NumberFormat$NumberFormatFactory", "com.ibm.icu.text.NumberFormat$NumberFormatShim", "com.ibm.icu.text.NumberFormat$SimpleNumberFormatFactory",
                    "com.ibm.icu.text.NumberFormat", "com.ibm.icu.text.NumberFormatServiceShim$NFFactory", "com.ibm.icu.text.NumberFormatServiceShim$NFService$1RBNumberFormatFactory",
                    "com.ibm.icu.text.NumberFormatServiceShim$NFService", "com.ibm.icu.text.NumberFormatServiceShim", "com.ibm.icu.text.NumberingSystem$1", "com.ibm.icu.text.NumberingSystem$2",
                    "com.ibm.icu.text.NumberingSystem$LocaleLookupData", "com.ibm.icu.text.NumberingSystem", "com.ibm.icu.text.NumeratorSubstitution", "com.ibm.icu.text.PluralFormat$1",
                    "com.ibm.icu.text.PluralFormat$PluralSelector", "com.ibm.icu.text.PluralFormat$PluralSelectorAdapter", "com.ibm.icu.text.PluralFormat", "com.ibm.icu.text.PluralRanges$Matrix",
                    "com.ibm.icu.text.PluralRanges", "com.ibm.icu.text.PluralRules$1", "com.ibm.icu.text.PluralRules$2", "com.ibm.icu.text.PluralRules$AndConstraint",
                    "com.ibm.icu.text.PluralRules$BinaryConstraint", "com.ibm.icu.text.PluralRules$Constraint", "com.ibm.icu.text.PluralRules$Factory", "com.ibm.icu.text.PluralRules$FixedDecimal",
                    "com.ibm.icu.text.PluralRules$FixedDecimalRange", "com.ibm.icu.text.PluralRules$FixedDecimalSamples", "com.ibm.icu.text.PluralRules$IFixedDecimal",
                    "com.ibm.icu.text.PluralRules$KeywordStatus", "com.ibm.icu.text.PluralRules$Operand", "com.ibm.icu.text.PluralRules$OrConstraint", "com.ibm.icu.text.PluralRules$PluralType",
                    "com.ibm.icu.text.PluralRules$RangeConstraint", "com.ibm.icu.text.PluralRules$Rule", "com.ibm.icu.text.PluralRules$RuleList", "com.ibm.icu.text.PluralRules$SampleType",
                    "com.ibm.icu.text.PluralRules$SimpleTokenizer", "com.ibm.icu.text.PluralRules", "com.ibm.icu.text.PluralRulesSerialProxy", "com.ibm.icu.text.PluralSamples",
                    "com.ibm.icu.text.Quantifier", "com.ibm.icu.text.QuantityFormatter", "com.ibm.icu.text.RBBINode", "com.ibm.icu.text.RBBIRuleBuilder$IntPair", "com.ibm.icu.text.RBBIRuleBuilder",
                    "com.ibm.icu.text.RBBIRuleParseTable$RBBIRuleTableElement", "com.ibm.icu.text.RBBIRuleParseTable", "com.ibm.icu.text.RBBIRuleScanner$RBBIRuleChar",
                    "com.ibm.icu.text.RBBIRuleScanner$RBBISetTableEl", "com.ibm.icu.text.RBBIRuleScanner", "com.ibm.icu.text.RBBISetBuilder$RangeDescriptor", "com.ibm.icu.text.RBBISetBuilder",
                    "com.ibm.icu.text.RBBISymbolTable$RBBISymbolTableEntry", "com.ibm.icu.text.RBBISymbolTable", "com.ibm.icu.text.RBBITableBuilder$RBBIStateDescriptor",
                    "com.ibm.icu.text.RBBITableBuilder", "com.ibm.icu.text.RBNFChinesePostProcessor", "com.ibm.icu.text.RBNFPostProcessor", "com.ibm.icu.text.RbnfLenientScanner",
                    "com.ibm.icu.text.RbnfLenientScannerProvider", "com.ibm.icu.text.RelativeDateTimeFormatter$1", "com.ibm.icu.text.RelativeDateTimeFormatter$AbsoluteUnit",
                    "com.ibm.icu.text.RelativeDateTimeFormatter$Cache$1", "com.ibm.icu.text.RelativeDateTimeFormatter$Cache", "com.ibm.icu.text.RelativeDateTimeFormatter$Direction",
                    "com.ibm.icu.text.RelativeDateTimeFormatter$Loader", "com.ibm.icu.text.RelativeDateTimeFormatter$RelDateTimeDataSink$DateTimeUnit",
                    "com.ibm.icu.text.RelativeDateTimeFormatter$RelDateTimeDataSink", "com.ibm.icu.text.RelativeDateTimeFormatter$RelativeDateTimeFormatterData",
                    "com.ibm.icu.text.RelativeDateTimeFormatter$RelativeDateTimeUnit", "com.ibm.icu.text.RelativeDateTimeFormatter$RelativeUnit",
                    "com.ibm.icu.text.RelativeDateTimeFormatter$Style",
                    "com.ibm.icu.text.RelativeDateTimeFormatter", "com.ibm.icu.text.Replaceable", "com.ibm.icu.text.ReplaceableContextIterator", "com.ibm.icu.text.ReplaceableString",
                    "com.ibm.icu.text.RuleBasedBreakIterator$BreakCache", "com.ibm.icu.text.RuleBasedBreakIterator$DictionaryCache", "com.ibm.icu.text.RuleBasedBreakIterator$LookAheadResults",
                    "com.ibm.icu.text.RuleBasedBreakIterator", "com.ibm.icu.text.RuleBasedNumberFormat", "com.ibm.icu.text.SCSU", "com.ibm.icu.text.SameValueSubstitution",
                    "com.ibm.icu.text.ScientificNumberFormatter$1", "com.ibm.icu.text.ScientificNumberFormatter$MarkupStyle", "com.ibm.icu.text.ScientificNumberFormatter$Style",
                    "com.ibm.icu.text.ScientificNumberFormatter$SuperscriptStyle", "com.ibm.icu.text.ScientificNumberFormatter", "com.ibm.icu.text.SelectFormat", "com.ibm.icu.text.SimpleDateFormat$1",
                    "com.ibm.icu.text.SimpleDateFormat$ContextValue", "com.ibm.icu.text.SimpleDateFormat$PatternItem", "com.ibm.icu.text.SimpleDateFormat", "com.ibm.icu.text.SimpleFormatter",
                    "com.ibm.icu.text.SpoofChecker$1", "com.ibm.icu.text.SpoofChecker$Builder$ConfusabledataBuilder$SPUString",
                    "com.ibm.icu.text.SpoofChecker$Builder$ConfusabledataBuilder$SPUStringComparator", "com.ibm.icu.text.SpoofChecker$Builder$ConfusabledataBuilder$SPUStringPool",
                    "com.ibm.icu.text.SpoofChecker$Builder$ConfusabledataBuilder", "com.ibm.icu.text.SpoofChecker$Builder", "com.ibm.icu.text.SpoofChecker$CheckResult",
                    "com.ibm.icu.text.SpoofChecker$ConfusableDataUtils", "com.ibm.icu.text.SpoofChecker$RestrictionLevel", "com.ibm.icu.text.SpoofChecker$ScriptSet",
                    "com.ibm.icu.text.SpoofChecker$SpoofData$DefaultData", "com.ibm.icu.text.SpoofChecker$SpoofData$IsAcceptable", "com.ibm.icu.text.SpoofChecker$SpoofData",
                    "com.ibm.icu.text.SpoofChecker", "com.ibm.icu.text.StringCharacterIterator", "com.ibm.icu.text.StringPrep$1", "com.ibm.icu.text.StringPrep$Values", "com.ibm.icu.text.StringPrep",
                    "com.ibm.icu.text.StringPrepParseException", "com.ibm.icu.text.StringTransform", "com.ibm.icu.text.SymbolTable", "com.ibm.icu.text.ThaiBreakEngine",
                    "com.ibm.icu.text.TimeUnitFormat$TimeUnitFormatSetupSink", "com.ibm.icu.text.TimeUnitFormat", "com.ibm.icu.text.TimeZoneFormat$1", "com.ibm.icu.text.TimeZoneFormat$GMTOffsetField",
                    "com.ibm.icu.text.TimeZoneFormat$GMTOffsetPatternType", "com.ibm.icu.text.TimeZoneFormat$OffsetFields", "com.ibm.icu.text.TimeZoneFormat$ParseOption",
                    "com.ibm.icu.text.TimeZoneFormat$Style", "com.ibm.icu.text.TimeZoneFormat$TimeType", "com.ibm.icu.text.TimeZoneFormat$TimeZoneFormatCache", "com.ibm.icu.text.TimeZoneFormat",
                    "com.ibm.icu.text.TimeZoneNames$1", "com.ibm.icu.text.TimeZoneNames$Cache", "com.ibm.icu.text.TimeZoneNames$DefaultTimeZoneNames$FactoryImpl",
                    "com.ibm.icu.text.TimeZoneNames$DefaultTimeZoneNames", "com.ibm.icu.text.TimeZoneNames$Factory", "com.ibm.icu.text.TimeZoneNames$MatchInfo",
                    "com.ibm.icu.text.TimeZoneNames$NameType", "com.ibm.icu.text.TimeZoneNames", "com.ibm.icu.text.Transform", "com.ibm.icu.text.UCharacterIterator", "com.ibm.icu.text.UFieldPosition",
                    "com.ibm.icu.text.UFormat", "com.ibm.icu.text.UForwardCharacterIterator", "com.ibm.icu.text.UTF16$StringComparator", "com.ibm.icu.text.UTF16",
                    "com.ibm.icu.text.UnhandledBreakEngine", "com.ibm.icu.text.UnicodeCompressor", "com.ibm.icu.text.UnicodeDecompressor", "com.ibm.icu.text.UnicodeFilter",
                    "com.ibm.icu.text.UnicodeMatcher",
                    "com.ibm.icu.text.UnicodeReplacer", "com.ibm.icu.text.UnicodeSet$1", "com.ibm.icu.text.UnicodeSet$ComparisonStyle", "com.ibm.icu.text.UnicodeSet$EntryRange",
                    "com.ibm.icu.text.UnicodeSet$EntryRangeIterable", "com.ibm.icu.text.UnicodeSet$EntryRangeIterator", "com.ibm.icu.text.UnicodeSet$Filter",
                    "com.ibm.icu.text.UnicodeSet$GeneralCategoryMaskFilter", "com.ibm.icu.text.UnicodeSet$IntPropertyFilter", "com.ibm.icu.text.UnicodeSet$NumericValueFilter",
                    "com.ibm.icu.text.UnicodeSet$ScriptExtensionsFilter", "com.ibm.icu.text.UnicodeSet$SpanCondition", "com.ibm.icu.text.UnicodeSet$UnicodeSetIterator2",
                    "com.ibm.icu.text.UnicodeSet$VersionFilter", "com.ibm.icu.text.UnicodeSet$XSymbolTable", "com.ibm.icu.text.UnicodeSet", "com.ibm.icu.text.UnicodeSetIterator",
                    "com.ibm.icu.text.UnicodeSetSpanner$CountMethod", "com.ibm.icu.text.UnicodeSetSpanner$TrimOption", "com.ibm.icu.text.UnicodeSetSpanner", "com.ibm.icu.util.AnnualTimeZoneRule",
                    "com.ibm.icu.util.BasicTimeZone", "com.ibm.icu.util.BuddhistCalendar", "com.ibm.icu.util.ByteArrayWrapper", "com.ibm.icu.util.BytesTrie$1", "com.ibm.icu.util.BytesTrie$Entry",
                    "com.ibm.icu.util.BytesTrie$Iterator", "com.ibm.icu.util.BytesTrie$Result", "com.ibm.icu.util.BytesTrie$State", "com.ibm.icu.util.BytesTrie",
                    "com.ibm.icu.util.BytesTrieBuilder$BytesAsCharSequence", "com.ibm.icu.util.BytesTrieBuilder", "com.ibm.icu.util.CECalendar", "com.ibm.icu.util.Calendar$1",
                    "com.ibm.icu.util.Calendar$CalType", "com.ibm.icu.util.Calendar$FormatConfiguration", "com.ibm.icu.util.Calendar$PatternData", "com.ibm.icu.util.Calendar$WeekData",
                    "com.ibm.icu.util.Calendar$WeekDataCache", "com.ibm.icu.util.Calendar", "com.ibm.icu.util.CaseInsensitiveString", "com.ibm.icu.util.CharsTrie$1",
                    "com.ibm.icu.util.CharsTrie$Entry", "com.ibm.icu.util.CharsTrie$Iterator", "com.ibm.icu.util.CharsTrie$State", "com.ibm.icu.util.CharsTrie", "com.ibm.icu.util.CharsTrieBuilder",
                    "com.ibm.icu.util.ChineseCalendar", "com.ibm.icu.util.CompactByteArray", "com.ibm.icu.util.CompactCharArray", "com.ibm.icu.util.CopticCalendar", "com.ibm.icu.util.Currency$1",
                    "com.ibm.icu.util.Currency$CurrencyNameResultHandler", "com.ibm.icu.util.Currency$CurrencyStringInfo", "com.ibm.icu.util.Currency$CurrencyUsage",
                    "com.ibm.icu.util.Currency$ServiceShim", "com.ibm.icu.util.Currency", "com.ibm.icu.util.CurrencyAmount", "com.ibm.icu.util.CurrencyServiceShim$CFService$1CurrencyFactory",
                    "com.ibm.icu.util.CurrencyServiceShim$CFService", "com.ibm.icu.util.CurrencyServiceShim", "com.ibm.icu.util.DangiCalendar", "com.ibm.icu.util.DateInterval",
                    "com.ibm.icu.util.DateRule", "com.ibm.icu.util.DateTimeRule", "com.ibm.icu.util.EasterHoliday", "com.ibm.icu.util.EasterRule", "com.ibm.icu.util.EthiopicCalendar",
                    "com.ibm.icu.util.Freezable", "com.ibm.icu.util.GenderInfo$1", "com.ibm.icu.util.GenderInfo$Cache", "com.ibm.icu.util.GenderInfo$Gender",
                    "com.ibm.icu.util.GenderInfo$ListGenderStyle", "com.ibm.icu.util.GenderInfo", "com.ibm.icu.util.GregorianCalendar", "com.ibm.icu.util.HebrewCalendar",
                    "com.ibm.icu.util.HebrewHoliday", "com.ibm.icu.util.Holiday", "com.ibm.icu.util.ICUCloneNotSupportedException", "com.ibm.icu.util.ICUException",
                    "com.ibm.icu.util.ICUUncheckedIOException", "com.ibm.icu.util.IllformedLocaleException", "com.ibm.icu.util.IndianCalendar", "com.ibm.icu.util.InitialTimeZoneRule",
                    "com.ibm.icu.util.IslamicCalendar$CalculationType", "com.ibm.icu.util.IslamicCalendar", "com.ibm.icu.util.JapaneseCalendar", "com.ibm.icu.util.LocaleData$1",
                    "com.ibm.icu.util.LocaleData$MeasurementSystem", "com.ibm.icu.util.LocaleData$PaperSize", "com.ibm.icu.util.LocaleData", "com.ibm.icu.util.LocaleMatcher$1",
                    "com.ibm.icu.util.LocaleMatcher$LanguageMatcherData", "com.ibm.icu.util.LocaleMatcher$Level", "com.ibm.icu.util.LocaleMatcher$LocalePatternMatcher",
                    "com.ibm.icu.util.LocaleMatcher$OutputDouble", "com.ibm.icu.util.LocaleMatcher$ScoreData", "com.ibm.icu.util.LocaleMatcher", "com.ibm.icu.util.LocalePriorityList$1",
                    "com.ibm.icu.util.LocalePriorityList$Builder", "com.ibm.icu.util.LocalePriorityList", "com.ibm.icu.util.Measure", "com.ibm.icu.util.MeasureUnit$1",
                    "com.ibm.icu.util.MeasureUnit$2", "com.ibm.icu.util.MeasureUnit$3", "com.ibm.icu.util.MeasureUnit$4", "com.ibm.icu.util.MeasureUnit$CurrencyNumericCodeSink",
                    "com.ibm.icu.util.MeasureUnit$Factory", "com.ibm.icu.util.MeasureUnit$MeasureUnitProxy", "com.ibm.icu.util.MeasureUnit$MeasureUnitSink", "com.ibm.icu.util.MeasureUnit",
                    "com.ibm.icu.util.NoUnit", "com.ibm.icu.util.Output", "com.ibm.icu.util.OutputInt", "com.ibm.icu.util.PersianCalendar", "com.ibm.icu.util.Range", "com.ibm.icu.util.RangeDateRule",
                    "com.ibm.icu.util.RangeValueIterator$Element", "com.ibm.icu.util.RangeValueIterator", "com.ibm.icu.util.Region$RegionType", "com.ibm.icu.util.Region",
                    "com.ibm.icu.util.RuleBasedTimeZone", "com.ibm.icu.util.STZInfo", "com.ibm.icu.util.SimpleDateRule", "com.ibm.icu.util.SimpleHoliday", "com.ibm.icu.util.SimpleTimeZone",
                    "com.ibm.icu.util.StringTokenizer", "com.ibm.icu.util.StringTrieBuilder$1", "com.ibm.icu.util.StringTrieBuilder$BranchHeadNode", "com.ibm.icu.util.StringTrieBuilder$BranchNode",
                    "com.ibm.icu.util.StringTrieBuilder$DynamicBranchNode", "com.ibm.icu.util.StringTrieBuilder$IntermediateValueNode", "com.ibm.icu.util.StringTrieBuilder$LinearMatchNode",
                    "com.ibm.icu.util.StringTrieBuilder$ListBranchNode", "com.ibm.icu.util.StringTrieBuilder$Node", "com.ibm.icu.util.StringTrieBuilder$Option",
                    "com.ibm.icu.util.StringTrieBuilder$SplitBranchNode", "com.ibm.icu.util.StringTrieBuilder$State", "com.ibm.icu.util.StringTrieBuilder$ValueNode",
                    "com.ibm.icu.util.StringTrieBuilder", "com.ibm.icu.util.TaiwanCalendar", "com.ibm.icu.util.TimeArrayTimeZoneRule", "com.ibm.icu.util.TimeUnit", "com.ibm.icu.util.TimeUnitAmount",
                    "com.ibm.icu.util.TimeZone$1", "com.ibm.icu.util.TimeZone$ConstantZone", "com.ibm.icu.util.TimeZone$SystemTimeZoneType", "com.ibm.icu.util.TimeZone",
                    "com.ibm.icu.util.TimeZoneRule", "com.ibm.icu.util.TimeZoneTransition", "com.ibm.icu.util.ULocale$1", "com.ibm.icu.util.ULocale$1ULocaleAcceptLanguageQ",
                    "com.ibm.icu.util.ULocale$2", "com.ibm.icu.util.ULocale$3", "com.ibm.icu.util.ULocale$Builder", "com.ibm.icu.util.ULocale$Category", "com.ibm.icu.util.ULocale$JDKLocaleHelper$1",
                    "com.ibm.icu.util.ULocale$JDKLocaleHelper", "com.ibm.icu.util.ULocale$Minimize", "com.ibm.icu.util.ULocale$Type", "com.ibm.icu.util.ULocale", "com.ibm.icu.util.UResourceBundle$1",
                    "com.ibm.icu.util.UResourceBundle$RootType", "com.ibm.icu.util.UResourceBundle", "com.ibm.icu.util.UResourceBundleIterator", "com.ibm.icu.util.UResourceTypeMismatchException",
                    "com.ibm.icu.util.UniversalTimeScale$TimeScaleData", "com.ibm.icu.util.UniversalTimeScale", "com.ibm.icu.util.VTimeZone", "com.ibm.icu.util.ValueIterator$Element",
                    "com.ibm.icu.util.ValueIterator", "com.ibm.icu.util.VersionInfo", "com.ibm.icu.impl.coll.BOCSU", "com.ibm.icu.impl.coll.Collation", "com.ibm.icu.impl.coll.CollationBuilder$1",
                    "com.ibm.icu.impl.coll.CollationBuilder$BundleImporter", "com.ibm.icu.impl.coll.CollationBuilder$CEFinalizer", "com.ibm.icu.impl.coll.CollationBuilder",
                    "com.ibm.icu.impl.coll.CollationCompare", "com.ibm.icu.impl.coll.CollationData", "com.ibm.icu.impl.coll.CollationDataBuilder$CEModifier",
                    "com.ibm.icu.impl.coll.CollationDataBuilder$ConditionalCE32", "com.ibm.icu.impl.coll.CollationDataBuilder$CopyHelper",
                    "com.ibm.icu.impl.coll.CollationDataBuilder$DataBuilderCollationIterator", "com.ibm.icu.impl.coll.CollationDataBuilder", "com.ibm.icu.impl.coll.CollationDataReader$1",
                    "com.ibm.icu.impl.coll.CollationDataReader$IsAcceptable", "com.ibm.icu.impl.coll.CollationDataReader", "com.ibm.icu.impl.coll.CollationFCD",
                    "com.ibm.icu.impl.coll.CollationFastLatin", "com.ibm.icu.impl.coll.CollationFastLatinBuilder", "com.ibm.icu.impl.coll.CollationIterator$CEBuffer",
                    "com.ibm.icu.impl.coll.CollationIterator$SkippedState", "com.ibm.icu.impl.coll.CollationIterator", "com.ibm.icu.impl.coll.CollationKeys$LevelCallback",
                    "com.ibm.icu.impl.coll.CollationKeys$SortKeyByteSink", "com.ibm.icu.impl.coll.CollationKeys$SortKeyLevel", "com.ibm.icu.impl.coll.CollationKeys",
                    "com.ibm.icu.impl.coll.CollationLoader$ASCII", "com.ibm.icu.impl.coll.CollationLoader", "com.ibm.icu.impl.coll.CollationRoot", "com.ibm.icu.impl.coll.CollationRootElements",
                    "com.ibm.icu.impl.coll.CollationRuleParser$Importer", "com.ibm.icu.impl.coll.CollationRuleParser$Position", "com.ibm.icu.impl.coll.CollationRuleParser$Sink",
                    "com.ibm.icu.impl.coll.CollationRuleParser", "com.ibm.icu.impl.coll.CollationSettings", "com.ibm.icu.impl.coll.CollationTailoring", "com.ibm.icu.impl.coll.CollationWeights$1",
                    "com.ibm.icu.impl.coll.CollationWeights$WeightRange", "com.ibm.icu.impl.coll.CollationWeights", "com.ibm.icu.impl.coll.ContractionsAndExpansions$CESink",
                    "com.ibm.icu.impl.coll.ContractionsAndExpansions", "com.ibm.icu.impl.coll.FCDIterCollationIterator$State", "com.ibm.icu.impl.coll.FCDIterCollationIterator",
                    "com.ibm.icu.impl.coll.FCDUTF16CollationIterator", "com.ibm.icu.impl.coll.IterCollationIterator", "com.ibm.icu.impl.coll.SharedObject$Reference",
                    "com.ibm.icu.impl.coll.SharedObject", "com.ibm.icu.impl.coll.TailoredSet", "com.ibm.icu.impl.coll.UTF16CollationIterator", "com.ibm.icu.impl.coll.UVector32",
                    "com.ibm.icu.impl.coll.UVector64", "com.ibm.icu.impl.text.RbnfScannerProviderImpl$1", "com.ibm.icu.impl.text.RbnfScannerProviderImpl$RbnfLenientScannerImpl",
                    "com.ibm.icu.impl.text.RbnfScannerProviderImpl", "com.ibm.icu.text.AlphabeticIndex$1", "com.ibm.icu.text.AlphabeticIndex$Bucket$LabelType",
                    "com.ibm.icu.text.AlphabeticIndex$Bucket", "com.ibm.icu.text.AlphabeticIndex$BucketList", "com.ibm.icu.text.AlphabeticIndex$ImmutableIndex",
                    "com.ibm.icu.text.AlphabeticIndex$Record", "com.ibm.icu.text.AlphabeticIndex", "com.ibm.icu.text.CollationElementIterator$MaxExpSink", "com.ibm.icu.text.CollationElementIterator",
                    "com.ibm.icu.text.CollationKey$BoundMode", "com.ibm.icu.text.CollationKey", "com.ibm.icu.text.Collator$1", "com.ibm.icu.text.Collator$ASCII",
                    "com.ibm.icu.text.Collator$CollatorFactory", "com.ibm.icu.text.Collator$KeywordsSink", "com.ibm.icu.text.Collator$ReorderCodes", "com.ibm.icu.text.Collator$ServiceShim",
                    "com.ibm.icu.text.Collator", "com.ibm.icu.text.CollatorServiceShim$1CFactory", "com.ibm.icu.text.CollatorServiceShim$CService$1CollatorFactory",
                    "com.ibm.icu.text.CollatorServiceShim$CService", "com.ibm.icu.text.CollatorServiceShim", "com.ibm.icu.text.RawCollationKey", "com.ibm.icu.text.RuleBasedCollator$1",
                    "com.ibm.icu.text.RuleBasedCollator$CollationBuffer", "com.ibm.icu.text.RuleBasedCollator$CollationKeyByteSink", "com.ibm.icu.text.RuleBasedCollator$FCDUTF16NFDIterator",
                    "com.ibm.icu.text.RuleBasedCollator$NFDIterator", "com.ibm.icu.text.RuleBasedCollator$UTF16NFDIterator", "com.ibm.icu.text.RuleBasedCollator",
                    "com.ibm.icu.text.SearchIterator$ElementComparisonType", "com.ibm.icu.text.SearchIterator$Search", "com.ibm.icu.text.SearchIterator", "com.ibm.icu.text.StringSearch$1",
                    "com.ibm.icu.text.StringSearch$CEBuffer", "com.ibm.icu.text.StringSearch$CEI", "com.ibm.icu.text.StringSearch$CollationPCE$PCEBuffer",
                    "com.ibm.icu.text.StringSearch$CollationPCE$PCEI", "com.ibm.icu.text.StringSearch$CollationPCE$RCEBuffer", "com.ibm.icu.text.StringSearch$CollationPCE$RCEI",
                    "com.ibm.icu.text.StringSearch$CollationPCE$Range", "com.ibm.icu.text.StringSearch$CollationPCE", "com.ibm.icu.text.StringSearch$Match", "com.ibm.icu.text.StringSearch$Pattern",
                    "com.ibm.icu.text.StringSearch", "com.ibm.icu.util.GlobalizationPreferences", "com.ibm.icu.impl.ICUCurrencyDisplayInfoProvider$1",
                    "com.ibm.icu.impl.ICUCurrencyDisplayInfoProvider$ICUCurrencyDisplayInfo$CurrencySink$EntrypointTable",
                    "com.ibm.icu.impl.ICUCurrencyDisplayInfoProvider$ICUCurrencyDisplayInfo$CurrencySink", "com.ibm.icu.impl.ICUCurrencyDisplayInfoProvider$ICUCurrencyDisplayInfo$FormattingData",
                    "com.ibm.icu.impl.ICUCurrencyDisplayInfoProvider$ICUCurrencyDisplayInfo$NarrowSymbol", "com.ibm.icu.impl.ICUCurrencyDisplayInfoProvider$ICUCurrencyDisplayInfo$ParsingData",
                    "com.ibm.icu.impl.ICUCurrencyDisplayInfoProvider$ICUCurrencyDisplayInfo", "com.ibm.icu.impl.ICUCurrencyDisplayInfoProvider", "com.ibm.icu.impl.ICUCurrencyMetaInfo$1",
                    "com.ibm.icu.impl.ICUCurrencyMetaInfo$Collector", "com.ibm.icu.impl.ICUCurrencyMetaInfo$CurrencyCollector", "com.ibm.icu.impl.ICUCurrencyMetaInfo$InfoCollector",
                    "com.ibm.icu.impl.ICUCurrencyMetaInfo$RegionCollector", "com.ibm.icu.impl.ICUCurrencyMetaInfo$UniqueList", "com.ibm.icu.impl.ICUCurrencyMetaInfo",
                    "com.ibm.icu.impl.ICULangDataTables", "com.ibm.icu.impl.ICURegionDataTables", "com.ibm.icu.impl.UtilityExtensions", "com.ibm.icu.text.AnyTransliterator$ScriptRunIterator",
                    "com.ibm.icu.text.AnyTransliterator", "com.ibm.icu.text.BreakTransliterator$ReplaceableCharacterIterator", "com.ibm.icu.text.BreakTransliterator",
                    "com.ibm.icu.text.CaseFoldTransliterator$1", "com.ibm.icu.text.CaseFoldTransliterator$2", "com.ibm.icu.text.CaseFoldTransliterator", "com.ibm.icu.text.CompoundTransliterator",
                    "com.ibm.icu.text.EscapeTransliterator$1", "com.ibm.icu.text.EscapeTransliterator$2", "com.ibm.icu.text.EscapeTransliterator$3", "com.ibm.icu.text.EscapeTransliterator$4",
                    "com.ibm.icu.text.EscapeTransliterator$5", "com.ibm.icu.text.EscapeTransliterator$6", "com.ibm.icu.text.EscapeTransliterator$7", "com.ibm.icu.text.EscapeTransliterator$8",
                    "com.ibm.icu.text.EscapeTransliterator", "com.ibm.icu.text.FunctionReplacer", "com.ibm.icu.text.LowercaseTransliterator$1", "com.ibm.icu.text.LowercaseTransliterator$2",
                    "com.ibm.icu.text.LowercaseTransliterator", "com.ibm.icu.text.NameUnicodeTransliterator$1", "com.ibm.icu.text.NameUnicodeTransliterator",
                    "com.ibm.icu.text.NormalizationTransliterator$1", "com.ibm.icu.text.NormalizationTransliterator$2", "com.ibm.icu.text.NormalizationTransliterator$3",
                    "com.ibm.icu.text.NormalizationTransliterator$4", "com.ibm.icu.text.NormalizationTransliterator$5", "com.ibm.icu.text.NormalizationTransliterator$6",
                    "com.ibm.icu.text.NormalizationTransliterator$NormalizingTransform", "com.ibm.icu.text.NormalizationTransliterator", "com.ibm.icu.text.NullTransliterator",
                    "com.ibm.icu.text.RemoveTransliterator$1", "com.ibm.icu.text.RemoveTransliterator", "com.ibm.icu.text.RuleBasedTransliterator$Data", "com.ibm.icu.text.RuleBasedTransliterator",
                    "com.ibm.icu.text.SourceTargetUtility", "com.ibm.icu.text.StringMatcher", "com.ibm.icu.text.StringReplacer", "com.ibm.icu.text.TitlecaseTransliterator$1",
                    "com.ibm.icu.text.TitlecaseTransliterator$2", "com.ibm.icu.text.TitlecaseTransliterator", "com.ibm.icu.text.TransliterationRule", "com.ibm.icu.text.TransliterationRuleSet",
                    "com.ibm.icu.text.Transliterator$Factory", "com.ibm.icu.text.Transliterator$Position", "com.ibm.icu.text.Transliterator", "com.ibm.icu.text.TransliteratorIDParser$SingleID",
                    "com.ibm.icu.text.TransliteratorIDParser$Specs", "com.ibm.icu.text.TransliteratorIDParser", "com.ibm.icu.text.TransliteratorParser$1",
                    "com.ibm.icu.text.TransliteratorParser$ParseData", "com.ibm.icu.text.TransliteratorParser$RuleArray", "com.ibm.icu.text.TransliteratorParser$RuleBody",
                    "com.ibm.icu.text.TransliteratorParser$RuleHalf", "com.ibm.icu.text.TransliteratorParser", "com.ibm.icu.text.TransliteratorRegistry$AliasEntry",
                    "com.ibm.icu.text.TransliteratorRegistry$CompoundRBTEntry", "com.ibm.icu.text.TransliteratorRegistry$IDEnumeration", "com.ibm.icu.text.TransliteratorRegistry$LocaleEntry",
                    "com.ibm.icu.text.TransliteratorRegistry$ResourceEntry", "com.ibm.icu.text.TransliteratorRegistry$Spec", "com.ibm.icu.text.TransliteratorRegistry",
                    "com.ibm.icu.text.UnescapeTransliterator$1", "com.ibm.icu.text.UnescapeTransliterator$2", "com.ibm.icu.text.UnescapeTransliterator$3", "com.ibm.icu.text.UnescapeTransliterator$4",
                    "com.ibm.icu.text.UnescapeTransliterator$5", "com.ibm.icu.text.UnescapeTransliterator$6", "com.ibm.icu.text.UnescapeTransliterator$7", "com.ibm.icu.text.UnescapeTransliterator",
                    "com.ibm.icu.text.UnicodeNameTransliterator$1", "com.ibm.icu.text.UnicodeNameTransliterator", "com.ibm.icu.text.UppercaseTransliterator$1",
                    "com.ibm.icu.text.UppercaseTransliterator$2", "com.ibm.icu.text.UppercaseTransliterator"
    };

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(this::checkImageHeapDoesNotIncludeDirectByteBuffers);

        Class<?>[] icu4jClasses = new Class<?>[icu4jClassNames.length];
        for (int i = 0; i < icu4jClassNames.length; i++) {
            icu4jClasses[i] = access.findClassByName(icu4jClassNames[i]);
        }

        RuntimeClassInitialization.delayClassInitialization(icu4jClasses);
    }

    static class Helper {
        /** Dummy ClassLoader used only for resource loading. */
        // Checkstyle: stop
        static final ClassLoader DUMMY_LOADER = new ClassLoader(null) {
        };
        // CheckStyle: resume
    }
}

@TargetClass(className = "com.ibm.icu.impl.ClassLoaderUtil", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ClassLoaderUtil {
    @Substitute
    // Checkstyle: stop
    public static ClassLoader getClassLoader() {
        return ICU4JFeature.Helper.DUMMY_LOADER;
    }
    // Checkstyle: resume
}

@TargetClass(className = "com.ibm.icu.impl.ICUBinary", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUBinary {

    @Alias
    static native void addDataFilesFromPath(String dataPath, List<?> files);

    @Alias @InjectAccessors(IcuDataFilesAccessors.class) static List<?> icuDataFiles;

    static final class IcuDataFilesAccessors {

        private static final String ICU4J_DATA_PATH_SYS_PROP = "com.ibm.icu.impl.ICUBinary.dataPath";
        private static final String ICU4J_DATA_PATH_ENV_VAR = "ICU4J_DATA_PATH";

        private static final String NO_DATA_PATH_ERR_MSG = "No ICU4J data path was set or found. This will likely end up with a MissingResourceException. " +
                        "To take advantage of the ICU4J library, you should either set system property, " +
                        ICU4J_DATA_PATH_SYS_PROP +
                        ", or set environment variable, " +
                        ICU4J_DATA_PATH_ENV_VAR +
                        ", to contain path to your ICU4J icudt directory";

        private static volatile List<?> instance;

        static List<?> get() {

            if (instance == null) {
                // Checkstyle: allow synchronization
                synchronized (IcuDataFilesAccessors.class) {
                    if (instance == null) {

                        instance = new ArrayList<>();

                        String dataPath = System.getProperty(ICU4J_DATA_PATH_SYS_PROP);
                        if (dataPath == null || dataPath.isEmpty()) {
                            dataPath = System.getenv(ICU4J_DATA_PATH_ENV_VAR);
                        }
                        if (dataPath != null && !dataPath.isEmpty()) {
                            addDataFilesFromPath(dataPath, instance);
                        } else {
                            System.err.println(NO_DATA_PATH_ERR_MSG);
                        }
                    }
                }
                // Checkstyle: disallow synchronization
            }
            return instance;
        }

        static void set(@SuppressWarnings("unused") List<?> bummer) {
        }
    }
}

@TargetClass(className = "com.ibm.icu.impl.ICUResourceBundle", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUResourceBundle {
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias, isFinal = true)
    // Checkstyle: stop
    private static ClassLoader ICU_DATA_CLASS_LOADER = ICU4JFeature.Helper.DUMMY_LOADER;
    // Checkstyle: resume

    @SuppressWarnings("unused")
    @Substitute
    // Checkstyle: stop
    private static void addBundleBaseNamesFromClassLoader(final String bn, final ClassLoader root, final Set<String> names) {
    }
    // Checkstyle: resume
}

@TargetClass(className = "com.ibm.icu.impl.ICUResourceBundle$WholeBundle", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUResourceBundle_WholeBundle {
    @Alias @RecomputeFieldValue(kind = Kind.Reset)
    // Checkstyle: stop
    ClassLoader loader;
    // Checkstyle: resume
}

@TargetClass(className = "com.ibm.icu.impl.ICUResourceBundle$AvailEntry", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUResourceBundle_AvailEntry {
    @Alias @RecomputeFieldValue(kind = Kind.Reset)
    // Checkstyle: stop
    ClassLoader loader;
    // Checkstyle: resume
}
