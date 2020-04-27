package com.oracle.svm.core.jdk;

import com.oracle.svm.core.annotate.AutomaticFeature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Packages {
    static class PackagesSupport {
        final Map<String, Annotation[]> pkg2Annotations = new HashMap<>();
        final Map<String, Annotation[]> pkg2DeclAnnotations = new HashMap<>();

        public Annotation[] getAnnotations(Package pkg) {
            return pkg2Annotations.get(pkg.getName());
        }

        public Annotation[] getDeclaredAnnotations(Package pkg) {
            return pkg2DeclAnnotations.get(pkg.getName());
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Annotations:\n");
            for (Map.Entry<String, Annotation[]> e : pkg2Annotations.entrySet()) {
                builder.append(e.getKey());
                builder.append(": ");
                builder.append(Arrays.toString(e.getValue()));
                builder.append("\n");
            }
            builder.append("\n");
            builder.append("Declared Annotations:\n");
            for (Map.Entry<String, Annotation[]> e : pkg2DeclAnnotations.entrySet()) {
                builder.append(e.getKey());
                builder.append(": ");
                builder.append(Arrays.toString(e.getValue()));
                builder.append("\n");
            }
            return builder.toString();
        }
    }

    @AutomaticFeature
    static class PackagesFeature implements Feature {
        public void afterRegistration(AfterRegistrationAccess access) {
            ImageSingletons.add(PackagesSupport.class, new PackagesSupport());
        }
    }

    private Packages() {}

    public static void registerAnnotations(Package pkg, Annotation[] annotations) {
        PackagesSupport packagesSupport = ImageSingletons.lookup(PackagesSupport.class);
        packagesSupport.pkg2Annotations.put(pkg.getName(), annotations);
    }

    public static void registerDeclaredAnnotations(Package pkg, Annotation[] annotations) {
        PackagesSupport packagesSupport = ImageSingletons.lookup(PackagesSupport.class);
        packagesSupport.pkg2DeclAnnotations.put(pkg.getName(), annotations);
    }
}
