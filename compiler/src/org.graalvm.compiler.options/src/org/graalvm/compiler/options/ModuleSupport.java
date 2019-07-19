package org.graalvm.compiler.options;

import java.util.ServiceLoader;

public class ModuleSupport {

    static Iterable<OptionDescriptors> getOptionsLoader() {
        // On JDK 8, Graal and its extensions are loaded by the same class loader.
        return ServiceLoader.load(OptionDescriptors.class, OptionDescriptors.class.getClassLoader());
    }
}
