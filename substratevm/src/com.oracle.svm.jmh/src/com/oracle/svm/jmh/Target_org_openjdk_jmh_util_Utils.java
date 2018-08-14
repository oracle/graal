package com.oracle.svm.jmh;

import java.util.Properties;

import org.openjdk.jmh.util.Utils;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Substitutes {@link Utils#getRecordedSystemProperties()} to set up properties with non-null
 * values, as Substrate currently returns null for some system properties.
 */
@TargetClass(Utils.class)
public final class Target_org_openjdk_jmh_util_Utils {

    @Substitute
    public static Properties getRecordedSystemProperties() {
        Properties properties = new Properties();
        properties.put("java.vm.name", System.getProperty("java.vm.name"));

        // set up custom values as Substrate returns null for these properties
        properties.put("java.version", "unknown");
        properties.put("java.vm.version", "unknown");

        return properties;
    }

}
