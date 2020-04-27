package com.oracle.svm.hosted;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.Packages;
import org.graalvm.nativeimage.hosted.Feature;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@AutomaticFeature
public class PackagesFeature implements Feature {
    private int loadedConfigurations;

    @Override
    public void duringSetup(DuringSetupAccess a) {
        FeatureImpl.DuringSetupAccessImpl access = (FeatureImpl.DuringSetupAccessImpl) a;
        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        ClassLoader nativeImageClassLoader = imageClassLoader.getClassLoader();
        /**
         * Invoke ClassLoader.getPackages() via reflection.
         * Obtain the package by given package name.
         *
         * Package.getPackages() finds packages in the class loader of the caller, i.e.,
         * the sun.misc.Launcher$AppClassLoader, since we are still in the Java world.
         * However, because classes are loaded by the NativeImageClassLoader, those packages
         * cannot be found in the Launcher$AppClassLoader. Therefore, packages should be found
         * in the NativeImageClassLoader.
         *
         */
        try {
            Method getPackagesMethod = ClassLoader.class.getDeclaredMethod("getPackages");
            getPackagesMethod.setAccessible(true);
            Package[] packages = (Package[]) getPackagesMethod.invoke(nativeImageClassLoader);
            for (Package pkg : packages) {
                Annotation[] annotations = pkg.getAnnotations();
                if (annotations != null && annotations.length != 0) {
                    Packages.registerAnnotations(pkg, annotations);
                }
                annotations = pkg.getDeclaredAnnotations();
                if (annotations != null && annotations.length != 0) {
                    Packages.registerDeclaredAnnotations(pkg, annotations);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
