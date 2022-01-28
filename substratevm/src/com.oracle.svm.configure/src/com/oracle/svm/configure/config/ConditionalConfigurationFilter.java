package com.oracle.svm.configure.config;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.oracle.svm.core.configure.ConditionalElement;

public class ConditionalConfigurationFilter
                implements TypeConfiguration.TypeConfigurationFilterPredicate, ProxyConfiguration.ProxyConfigurationFilterPredicate, ResourceConfiguration.ResourceConfigurationFilterPredicate,
                SerializationConfiguration.SerializationConfigurationFilter, PredefinedClassesConfiguration.PredefinedClassFilterPredicate {

    private final Set<Pattern> classNamePatterns;

    public ConditionalConfigurationFilter(Set<Pattern> classNamePatterns) {
        this.classNamePatterns = classNamePatterns;
    }

    private boolean classNameMatchesAny(String className) {
        return classNamePatterns.stream().anyMatch(p -> p.matcher(className).find());
    }

    @Override
    public boolean testIncludedType(ConditionalElement<String> conditionalElement, ConfigurationType type) {
        return classNameMatchesAny(conditionalElement.getCondition().getTypeName()) || classNameMatchesAny(type.getQualifiedJavaName());
    }

    @Override
    public boolean testProxyInterfaceList(ConditionalElement<List<String>> conditionalElement) {
        return classNameMatchesAny(conditionalElement.getCondition().getTypeName());
    }

    @Override
    public boolean testIncludedResource(ConditionalElement<String> condition, Pattern pattern) {
        return classNameMatchesAny(condition.getCondition().getTypeName());
    }

    @Override
    public boolean testIncludedBundle(ConditionalElement<String> condition, ResourceConfiguration.BundleConfiguration bundleConfiguration) {
        return classNameMatchesAny(condition.getCondition().getTypeName());
    }

    @Override
    public boolean testSerializationType(SerializationConfigurationType type) {
        return classNameMatchesAny(type.getCondition().getTypeName()) || classNameMatchesAny(type.getQualifiedJavaName());
    }

    @Override
    public boolean testLambdaSerializationType(SerializationConfigurationLambdaCapturingType type) {
        return classNameMatchesAny(type.getCondition().getTypeName()) || classNameMatchesAny(type.getQualifiedJavaName());
    }

    @Override
    public boolean testPredefinedClass(ConfigurationPredefinedClass clazz) {
        return clazz.getNameInfo() != null && classNameMatchesAny(clazz.getNameInfo());
    }
}
