package com.oracle.svm.configure.config;

import java.util.List;
import java.util.regex.Pattern;

import com.oracle.svm.configure.filters.ComplexFilter;
import com.oracle.svm.core.configure.ConditionalElement;

public class ConditionalConfigurationPredicate implements TypeConfiguration.Predicate, ProxyConfiguration.Predicate,
                ResourceConfiguration.Predicate, SerializationConfiguration.Predicate, PredefinedClassesConfiguration.Predicate {

    private final ComplexFilter filter;

    public ConditionalConfigurationPredicate(ComplexFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean testIncludedType(ConditionalElement<String> conditionalElement, ConfigurationType type) {
        return !filter.includes(conditionalElement.getCondition().getTypeName()) || !filter.includes(type.getQualifiedJavaName());
    }

    @Override
    public boolean testProxyInterfaceList(ConditionalElement<List<String>> conditionalElement) {
        return !filter.includes(conditionalElement.getCondition().getTypeName());
    }

    @Override
    public boolean testIncludedResource(ConditionalElement<String> condition, Pattern pattern) {
        return !filter.includes(condition.getCondition().getTypeName());
    }

    @Override
    public boolean testIncludedBundle(ConditionalElement<String> condition, ResourceConfiguration.BundleConfiguration bundleConfiguration) {
        return !filter.includes(condition.getCondition().getTypeName());
    }

    @Override
    public boolean testSerializationType(SerializationConfigurationType type) {
        return !(filter.includes(type.getCondition().getTypeName()) && filter.includes(type.getQualifiedJavaName()));
    }

    @Override
    public boolean testLambdaSerializationType(SerializationConfigurationLambdaCapturingType type) {
        return !(filter.includes(type.getCondition().getTypeName()) && filter.includes(type.getQualifiedJavaName()));
    }

    @Override
    public boolean testPredefinedClass(ConfigurationPredefinedClass clazz) {
        return clazz.getNameInfo() != null && !filter.includes(clazz.getNameInfo());
    }
}
