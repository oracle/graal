/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.test.config;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.oracle.svm.configure.ConfigurationFile;
import com.oracle.svm.configure.ConfigurationParserOption;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedAccessCondition;
import com.oracle.svm.configure.command.ConfigurationGenerateCommand;
import com.oracle.svm.configure.command.ConfigurationGenerateConditionalsCommand;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.ForeignConfiguration;
import com.oracle.svm.configure.config.ForeignConfiguration.ConfigurationFunctionDescriptor;
import com.oracle.svm.configure.config.conditional.ConditionalConfigurationComputer;
import com.oracle.svm.configure.config.conditional.ConditionalConfigurationPredicate;
import com.oracle.svm.configure.config.conditional.MethodCallNode;
import com.oracle.svm.configure.config.conditional.MethodInfoRepository;
import com.oracle.svm.configure.config.conditional.PartialConfigurationWithOrigins;
import com.oracle.svm.configure.filters.ComplexFilter;
import com.oracle.svm.configure.filters.ConfigurationFilter.Inclusion;
import com.oracle.svm.configure.filters.HierarchyFilterNode;
import com.oracle.svm.configure.test.AddExports;

import jdk.graal.compiler.util.json.JsonParser;
import jdk.graal.compiler.util.json.JsonPrintable;
import jdk.graal.compiler.util.json.JsonWriter;

@AddExports({"org.graalvm.nativeimage/org.graalvm.nativeimage.impl", "jdk.graal.compiler/jdk.graal.compiler.util", "jdk.graal.compiler/jdk.graal.compiler.util.json",
                "jdk.graal.compiler/jdk.graal.compiler.phases.common"})
public class ForeignConfigurationTest {
    private static final ConfigurationFunctionDescriptor COMPARATOR = new ConfigurationFunctionDescriptor("jint", List.of("void*", "void*"));
    private static final Map<String, Object> OPTIONS = Map.of("captureCallState", true);
    private static final String CRITICAL_DOWNCALL_METADATA = """
                    {"foreign":{"downcalls":[{"returnType":"jlong","parameterTypes":["void*"],
                      "options":{"critical":{"allowHeapAccess":true}}}]}}
                    """;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static UnresolvedAccessCondition condition(String name) {
        return UnresolvedAccessCondition.create(NamedConfigurationTypeDescriptor.fromJSONName(name));
    }

    private static String metadata(String conditionType) {
        String conditionAttribute = conditionType == null ? "" : "\"condition\":{\"typeReached\":\"" + conditionType + "\"},";
        return """
                        {"foreign":{
                          "downcalls":[
                            {%1$s"returnType":"jlong","parameterTypes":["void*"],"options":{"captureCallState":true}},
                            {%1$s"returnType":"void","parameterTypes":["void*","jlong","jlong","void*"]}],
                          "upcalls":[{%1$s"returnType":"jint","parameterTypes":["void*","void*"]}],
                          "directUpcalls":[{%1$s"class":"lib.Native","method":"compareAscending","returnType":"jint","parameterTypes":["void*","void*"]}]
                        }}
                        """.formatted(conditionAttribute);
    }

    private static ForeignConfiguration parseForeign(String json) throws IOException {
        ForeignConfiguration result = new ForeignConfiguration();
        result.createParser(true, EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION)).parseAndRegister(new StringReader(json));
        return result;
    }

    private static String print(JsonPrintable value) throws IOException {
        StringWriter output = new StringWriter();
        try (JsonWriter writer = new JsonWriter(output)) {
            value.printJson(writer);
        }
        return output.toString();
    }

    /* Convert parser objects to Java collections so equality checks content, not map identity. */
    private static Object javaValue(Object value) {
        if (value instanceof EconomicMap<?, ?> map) {
            Map<Object, Object> result = new HashMap<>();
            var cursor = map.getEntries();
            while (cursor.advance()) {
                result.put(cursor.getKey(), javaValue(cursor.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(element -> result.add(javaValue(element)));
            return result;
        }
        return value;
    }

    private static Map<?, ?> jsonObject(String json) throws IOException {
        return (Map<?, ?>) javaValue(new JsonParser(json).parse());
    }

    private static void assertForeignJson(String expected, String actual) throws IOException {
        Map<?, ?> expectedForeign = jsonObject(expected);
        Map<?, ?> actualForeign = jsonObject(actual);
        Assert.assertEquals("Foreign categories", expectedForeign.keySet(), actualForeign.keySet());
        for (Object category : expectedForeign.keySet()) {
            List<?> expectedEntries = (List<?>) expectedForeign.get(category);
            List<?> actualEntries = (List<?>) actualForeign.get(category);
            Assert.assertEquals(category + " count", expectedEntries.size(), actualEntries.size());
            Assert.assertEquals(category + " registrations (including conditions/options)", new HashSet<>(expectedEntries), new HashSet<>(actualEntries));
        }
    }

    private static String foreignPart(String metadata) throws IOException {
        Object foreign = jsonObject(metadata).get("foreign");
        Assert.assertNotNull("Missing foreign section", foreign);
        return print(writer -> writer.print(foreign));
    }

    private static void assertEquivalent(ForeignConfiguration expected, ForeignConfiguration actual) {
        Assert.assertTrue("Missing foreign registrations", expected.copyAndSubtract(actual).isEmpty());
        Assert.assertTrue("Unexpected foreign registrations", actual.copyAndSubtract(expected).isEmpty());
    }

    private Path input(String name, String json) throws IOException {
        Path directory = temporaryFolder.newFolder(name).toPath();
        Files.writeString(directory.resolve(ConfigurationFile.REACHABILITY_METADATA.getFileName()), json);
        return directory;
    }

    private String generate(String... inputs) throws IOException {
        Path output = temporaryFolder.newFolder().toPath();
        List<String> arguments = new ArrayList<>();
        for (int i = 0; i < inputs.length; i++) {
            arguments.add("--input-dir=" + input("input" + i, inputs[i]));
        }
        arguments.add("--output-dir=" + output);
        new ConfigurationGenerateCommand().apply(arguments.iterator());
        return Files.readString(output.resolve(ConfigurationFile.REACHABILITY_METADATA.getFileName()));
    }

    @Test
    public void generatePreservesConditionsDescriptorsAndOptions() throws IOException {
        String reflection = "\"reflection\":[{\"condition\":{\"typeReached\":\"lib.Native\"},\"type\":\"lib.Target\"}],";
        String expected = "{" + reflection + metadata("lib.Native").substring(1);
        String actual = generate(expected);
        Assert.assertEquals("Reflection control", jsonObject(expected).get("reflection"), jsonObject(actual).get("reflection"));
        assertForeignJson(foreignPart(expected), foreignPart(actual));
    }

    @Test
    public void generateDoesNotEraseConditionalEntriesWhenMergingPlainInput() throws IOException {
        String actual = foreignPart(generate(metadata("lib.Native"), metadata(null)));
        Map<?, ?> foreign = jsonObject(actual);
        for (Object category : foreign.keySet()) {
            List<?> entries = (List<?>) foreign.get(category);
            Assert.assertEquals(category + " must not contain indistinguishable unconditional duplicates", entries.size(), new HashSet<>(entries).size());
        }
        ForeignConfiguration expected = parseForeign(metadata("lib.Native")).copyAndMerge(parseForeign(metadata(null)));
        assertEquivalent(expected, parseForeign("{\"foreign\":" + actual + "}"));
    }

    @Test
    public void nestedDowncallOptionsUseContentEquality() throws IOException {
        ForeignConfiguration first = parseForeign(CRITICAL_DOWNCALL_METADATA);
        ForeignConfiguration second = parseForeign(CRITICAL_DOWNCALL_METADATA);
        assertEquivalent(first, first.copyAndIntersect(second));
        Assert.assertTrue("Equivalent parsed registrations must subtract", first.copyAndSubtract(second).isEmpty());
        ForeignConfiguration agentConfiguration = new ForeignConfiguration();
        agentConfiguration.addDowncall("jlong", List.of("void*"), Map.of("critical", Map.of("allowHeapAccess", true)));
        assertEquivalent(agentConfiguration, first);
    }

    @Test
    public void generateMergesEquivalentNestedDowncallOptions() throws IOException {
        assertForeignJson(foreignPart(CRITICAL_DOWNCALL_METADATA), foreignPart(generate(CRITICAL_DOWNCALL_METADATA, CRITICAL_DOWNCALL_METADATA)));
    }

    @Test
    public void generateConditionalRetainsCommonNestedDowncallOptions() throws IOException {
        MethodCallNode root = MethodCallNode.createRoot();
        MethodInfoRepository methods = new MethodInfoRepository();
        for (String caller : List.of("app.First", "app.Second")) {
            MethodCallNode parent = child(root, methods, caller);
            MethodCallNode shared = child(parent, methods, "lib.Shared");
            shared.getConfiguration().getForeignConfiguration().addDowncall("jlong", List.of("void*"), Map.of("critical", Map.of("allowHeapAccess", true)));
        }
        Path partial = temporaryFolder.newFolder("partial").toPath();
        Files.writeString(partial.resolve(ConfigurationFile.PARTIAL_CONFIGURATION_WITH_ORIGINS), print(new PartialConfigurationWithOrigins(root, methods)));
        Path userCodeFilter = temporaryFolder.newFile("user-code-filter.json").toPath();
        Files.writeString(userCodeFilter, "{\"rules\":[{\"includeClasses\":\"**\"}]}");
        Path classNameFilter = temporaryFolder.newFile("class-name-filter.json").toPath();
        Files.writeString(classNameFilter, "{\"rules\":[{\"excludeClasses\":\"**\"},{\"includeClasses\":\"lib.**\"}]}");
        Path output = temporaryFolder.newFolder("conditional").toPath();
        new ConfigurationGenerateConditionalsCommand().apply(List.of("--input-dir=" + partial, "--output-dir=" + output,
                        "--user-code-filter=" + userCodeFilter, "--class-name-filter=" + classNameFilter).iterator());
        ForeignConfiguration expected = new ForeignConfiguration();
        expected.addDowncall(condition("lib.Shared"), new ConfigurationFunctionDescriptor("jlong", List.of("void*")), Map.of("critical", Map.of("allowHeapAccess", true)));
        String actual = Files.readString(output.resolve(ConfigurationFile.REACHABILITY_METADATA.getFileName()));
        assertEquivalent(expected, parseForeign(actual));
    }

    @Test
    public void generateKeepsDifferentConditionTypes() throws IOException {
        ForeignConfiguration expected = parseForeign(metadata("lib.First")).copyAndMerge(parseForeign(metadata("lib.Second")));
        assertEquivalent(expected, parseForeign(generate(metadata("lib.First"), metadata("lib.Second"))));
    }

    @Test
    public void generatePreservesUnconditionalControl() throws IOException {
        assertForeignJson(foreignPart(metadata(null)), foreignPart(generate(metadata(null))));
    }

    @Test
    public void inferredConditionAppliesToEveryForeignCategory() throws IOException {
        ForeignConfiguration actual = new ForeignConfiguration();
        actual.mergeConditional(condition("lib.Native"), parseForeign(metadata(null)));
        assertEquivalent(parseForeign(metadata("lib.Native")), actual);
    }

    private static ComplexFilter filter(String... included) {
        HierarchyFilterNode root = HierarchyFilterNode.createRoot();
        root.addOrGetChildren("**", Inclusion.Exclude);
        for (String name : included) {
            root.addOrGetChildren(name, Inclusion.Include);
        }
        return new ComplexFilter(root);
    }

    @Test
    public void filteringUsesConditionTypesForEveryForeignCategory() throws IOException {
        ForeignConfiguration included = parseForeign(metadata("lib.Native"));
        ForeignConfiguration all = included.copyAndMerge(parseForeign(metadata("excluded.Condition")));
        ForeignConfiguration actual = all.copyAndFilter(new ConditionalConfigurationPredicate(filter("lib.**")));
        assertEquivalent(included, actual);
    }

    @Test
    public void directUpcallFilteringAlsoUsesTargetClass() {
        ForeignConfiguration all = new ForeignConfiguration();
        all.addDirectUpcall(condition("lib.Native"), COMPARATOR, Map.of(), "lib.Target", "compare");
        all.addDirectUpcall(condition("lib.Native"), COMPARATOR, Map.of(), "excluded.Target", "compare");
        all.addDirectUpcall(condition("excluded.Condition"), COMPARATOR, Map.of(), "lib.Target", "compare");
        ForeignConfiguration expected = new ForeignConfiguration();
        expected.addDirectUpcall(condition("lib.Native"), COMPARATOR, Map.of(), "lib.Target", "compare");
        assertEquivalent(expected, all.copyAndFilter(new ConditionalConfigurationPredicate(filter("lib.**"))));
    }

    private static ForeignConfiguration direct(boolean descriptor, Map<String, Object> options) {
        ForeignConfiguration configuration = new ForeignConfiguration();
        if (descriptor) {
            configuration.addDirectUpcall(condition("lib.Native"), COMPARATOR, options, "lib.Native", "compare");
        } else {
            configuration.addDirectUpcall(condition("lib.Native"), options, "lib.Native", "compare");
        }
        return configuration;
    }

    private static void assertDirectRoundTrip(boolean descriptor, Map<String, Object> options) throws IOException {
        ForeignConfiguration expected = direct(descriptor, options);
        assertEquivalent(expected, parseForeign("{\"foreign\":" + print(expected) + "}"));
    }

    @Test
    public void descriptorfulDirectUpcallWithOptionsRoundTrips() throws IOException {
        assertDirectRoundTrip(true, OPTIONS);
    }

    @Test
    public void descriptorlessDirectUpcallWithOptionsRoundTrips() throws IOException {
        assertDirectRoundTrip(false, OPTIONS);
    }

    @Test
    public void descriptorlessDirectUpcallWithoutOptionsRoundTrips() throws IOException {
        assertDirectRoundTrip(false, Map.of());
    }

    @Test
    public void conditionalMergePreservesDescriptorlessDirectUpcall() {
        ForeignConfiguration input = new ForeignConfiguration();
        input.addDirectUpcall(UnresolvedAccessCondition.unconditional(), OPTIONS, "lib.Native", "compare");
        ForeignConfiguration actual = new ForeignConfiguration();
        actual.mergeConditional(condition("lib.Native"), input);
        assertEquivalent(direct(false, OPTIONS), actual);
    }

    @Test
    public void directUpcallSubsumptionIsScopedToConditionAndOptions() {
        ForeignConfiguration specific = direct(true, Map.of());
        specific.addDirectUpcall(condition("lib.Other"), COMPARATOR, Map.of(), "lib.Native", "compare");
        specific.addDirectUpcall(condition("lib.Native"), COMPARATOR, OPTIONS, "lib.Native", "compare");
        ForeignConfiguration generic = direct(false, Map.of());
        ForeignConfiguration expected = generic.copy();
        expected.addDirectUpcall(condition("lib.Other"), COMPARATOR, Map.of(), "lib.Native", "compare");
        expected.addDirectUpcall(condition("lib.Native"), COMPARATOR, OPTIONS, "lib.Native", "compare");
        assertEquivalent(expected, specific.copyAndMerge(generic));
        assertEquivalent(expected, generic.copyAndMerge(specific));
    }

    private static MethodCallNode child(MethodCallNode parent, MethodInfoRepository methods, String type) {
        return parent.getOrCreateChild(methods.getOrAdd(type, "call", "()V"));
    }

    private static ConfigurationSet infer(MethodCallNode root) {
        return new ConditionalConfigurationComputer(root, filter("lib.**"), new ConditionalConfigurationPredicate(filter("**"))).computeConditionalConfiguration();
    }

    @Test
    public void singleRunInferenceRetainsForeignAlongsideReflection() throws IOException {
        MethodCallNode root = MethodCallNode.createRoot();
        MethodCallNode library = child(root, new MethodInfoRepository(), "lib.Native");
        library.getConfiguration().getForeignConfiguration().mergeConditional(UnresolvedAccessCondition.unconditional(), parseForeign(metadata(null)));
        String reflectionEntry = """
                        "type":"lib.Target","unsafeAllocated":true,
                        "fields":[{"name":"value"}],"methods":[{"name":"hello","parameterTypes":[]}]
                        """;
        library.getConfiguration().getReflectionConfiguration().createParser(true, EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION)).parseAndRegister(
                        new StringReader("{\"reflection\":[{" + reflectionEntry + "}]}"));
        String originalReflection = print(library.getConfiguration().getReflectionConfiguration());
        ConfigurationSet expected = new ConfigurationSet();
        expected.getReflectionConfiguration().createParser(true, EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION)).parseAndRegister(
                        new StringReader("{\"reflection\":[{\"condition\":{\"typeReached\":\"lib.Native\"}," + reflectionEntry + "}]}"));
        ConfigurationSet actual = infer(root);
        Assert.assertEquals("Reflection condition, members, and flags", print(expected.getReflectionConfiguration()), print(actual.getReflectionConfiguration()));
        Assert.assertEquals("Source reflection is unchanged", originalReflection, print(library.getConfiguration().getReflectionConfiguration()));
        assertEquivalent(parseForeign(metadata("lib.Native")), actual.getForeignConfiguration());
    }

    @Test
    public void foreignOnlyPartialSerializationRetainsEachCategory() throws IOException {
        for (String category : List.of("downcalls", "upcalls", "directUpcalls")) {
            MethodCallNode root = MethodCallNode.createRoot();
            MethodInfoRepository methods = new MethodInfoRepository();
            MethodCallNode library = child(root, methods, "lib.Native");
            String entry = category.equals("directUpcalls") ? "\"class\":\"lib.Native\",\"method\":\"compare\"," : "";
            ForeignConfiguration expected = parseForeign("{\"foreign\":{\"" + category + "\":[{" + entry + "\"returnType\":\"jint\",\"parameterTypes\":[]}]}}");
            library.getConfiguration().getForeignConfiguration().mergeConditional(UnresolvedAccessCondition.unconditional(), expected);
            String partial = print(new PartialConfigurationWithOrigins(root, methods));
            MethodCallNode restored = MethodCallNode.createRoot();
            new PartialConfigurationWithOrigins(restored, methods).parseAndRegister(new StringReader(partial));
            Assert.assertFalse(category + " lost from foreign-only partial node", child(restored, methods, "lib.Native").getConfiguration().getForeignConfiguration().isEmpty());
            assertEquivalent(expected, child(restored, methods, "lib.Native").getConfiguration().getForeignConfiguration());
        }
    }

    @Test
    public void foreignOnlyDifferencesPropagateToCallingClasses() {
        MethodCallNode root = MethodCallNode.createRoot();
        MethodInfoRepository methods = new MethodInfoRepository();
        MethodCallNode parentA = child(root, methods, "lib.ParentA");
        MethodCallNode parentB = child(root, methods, "lib.ParentB");
        MethodCallNode first = child(parentA, methods, "lib.Shared");
        MethodCallNode second = child(parentB, methods, "lib.Shared");
        first.getConfiguration().getForeignConfiguration().addDowncall("jlong", List.of("void*"), Map.of());
        second.getConfiguration().getForeignConfiguration().addDowncall("void", List.of("void*"), Map.of());
        ForeignConfiguration firstExpected = first.getConfiguration().getForeignConfiguration().copy();
        ForeignConfiguration secondExpected = second.getConfiguration().getForeignConfiguration().copy();
        infer(root);
        assertEquivalent(firstExpected, parentA.getConfiguration().getForeignConfiguration());
        assertEquivalent(secondExpected, parentB.getConfiguration().getForeignConfiguration());
        Assert.assertTrue("No foreign-only difference may remain on the shared method", first.getConfiguration().getForeignConfiguration().isEmpty());
        Assert.assertTrue("No foreign-only difference may remain on the shared method", second.getConfiguration().getForeignConfiguration().isEmpty());
    }

    @Test
    public void commonForeignConfigurationRemainsOnDistinctCallNodes() {
        assertCommonForeignConfiguration(false);
    }

    @Test
    public void commonForeignConfigurationRemainsAfterDifferencesPropagate() {
        assertCommonForeignConfiguration(true);
    }

    private static void assertCommonForeignConfiguration(boolean includeDifferences) {
        MethodCallNode root = MethodCallNode.createRoot();
        MethodInfoRepository methods = new MethodInfoRepository();
        MethodCallNode parentA = child(root, methods, "lib.ParentA");
        MethodCallNode parentB = child(root, methods, "lib.ParentB");
        MethodCallNode first = child(parentA, methods, "lib.Shared");
        MethodCallNode second = child(parentB, methods, "lib.Shared");
        ForeignConfiguration common = new ForeignConfiguration();
        common.addDowncall("jlong", List.of("void*"), Map.of());
        first.getConfiguration().getForeignConfiguration().mergeConditional(UnresolvedAccessCondition.unconditional(), common);
        second.getConfiguration().getForeignConfiguration().mergeConditional(UnresolvedAccessCondition.unconditional(), common);
        ForeignConfiguration firstDifference = new ForeignConfiguration();
        ForeignConfiguration secondDifference = new ForeignConfiguration();
        if (includeDifferences) {
            firstDifference.addUpcall("jint", List.of("void*"), Map.of());
            secondDifference.addDirectUpcall("jint", List.of("void*"), Map.of(), "lib.Native", "compare");
            first.getConfiguration().getForeignConfiguration().mergeConditional(UnresolvedAccessCondition.unconditional(), firstDifference);
            second.getConfiguration().getForeignConfiguration().mergeConditional(UnresolvedAccessCondition.unconditional(), secondDifference);
        }
        ForeignConfiguration expected = new ForeignConfiguration();
        expected.mergeConditional(condition("lib.Shared"), common);
        expected.mergeConditional(condition("lib.ParentA"), firstDifference);
        expected.mergeConditional(condition("lib.ParentB"), secondDifference);
        ConfigurationSet actual = infer(root);
        Assert.assertNotSame("Call-node configuration copies are distinct", first.getConfiguration(), second.getConfiguration());
        assertEquivalent(common, first.getConfiguration().getForeignConfiguration());
        assertEquivalent(common, second.getConfiguration().getForeignConfiguration());
        assertEquivalent(firstDifference, parentA.getConfiguration().getForeignConfiguration());
        assertEquivalent(secondDifference, parentB.getConfiguration().getForeignConfiguration());
        assertEquivalent(expected, actual.getForeignConfiguration());
    }

    @Test
    public void generateConditionalRetainsForeignOnlyPartialData() throws IOException {
        MethodCallNode root = MethodCallNode.createRoot();
        MethodInfoRepository methods = new MethodInfoRepository();
        child(root, methods, "lib.Native").getConfiguration().getForeignConfiguration().mergeConditional(UnresolvedAccessCondition.unconditional(), parseForeign(metadata(null)));
        Path partial = temporaryFolder.newFolder("partial").toPath();
        Files.writeString(partial.resolve(ConfigurationFile.PARTIAL_CONFIGURATION_WITH_ORIGINS), print(new PartialConfigurationWithOrigins(root, methods)));
        Path filterFile = temporaryFolder.newFile("filter.json").toPath();
        Files.writeString(filterFile, "{\"rules\":[{\"excludeClasses\":\"**\"},{\"includeClasses\":\"lib.**\"}]}");
        Path output = temporaryFolder.newFolder("conditional").toPath();
        new ConfigurationGenerateConditionalsCommand().apply(List.of("--input-dir=" + partial, "--output-dir=" + output, "--user-code-filter=" + filterFile).iterator());
        String actual = Files.readString(output.resolve(ConfigurationFile.REACHABILITY_METADATA.getFileName()));
        assertEquivalent(parseForeign(metadata("lib.Native")), parseForeign(actual));
    }
}
