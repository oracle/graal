package com.oracle.svm.hosted;

import java.util.function.Consumer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.SerializationConfigurationParser;
import com.oracle.svm.core.jdk.SerializationSupport;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;

@AutomaticFeature
public class SerializationFeature implements Feature {
    private int loadedConfigurations;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SerializationSupport.class, new SerializationSupport());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        SerializationSupport support = ImageSingletons.lookup(SerializationSupport.class);
        ImageClassLoader imageClassLoader = ((FeatureImpl.BeforeAnalysisAccessImpl) access).getImageClassLoader();
        Consumer<String> adapter = (targetClassName) -> {
            Class<?> targetClass = resolveClass(targetClassName, imageClassLoader);
            UserError.guarantee(targetClass != null,
                            "Cannot find serialization target class %s. The absence of serialization classes is fatal even with option " +
                                            SubstrateOptionsParser.commandArgument(NativeImageOptions.AllowIncompleteClasspath, "+") +
                                            ". Please make sure it is on the classpath.",
                            targetClassName);
            support.addClass(targetClass);
        };

        SerializationConfigurationParser parser = new SerializationConfigurationParser(adapter);
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationConfigurationFiles, ConfigurationFiles.Options.SerializationConfigurationResources,
                        ConfigurationFiles.SERIALIZATION_NAME);
    }

    private Class<?> resolveClass(String typeName, ImageClassLoader imageClassLoader) {
        Class<?> result = imageClassLoader.findClassByName(typeName, false);
        if (result == null) {
            handleError("Could not resolve " + typeName + " for serialization configuration.");
        }
        return result;
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (!ImageSingletons.contains(FallbackFeature.class)) {
            return;
        }
        FallbackFeature.FallbackImageRequest serializationFallback = ImageSingletons.lookup(FallbackFeature.class).serializationFallback;
        if (serializationFallback != null && loadedConfigurations == 0) {
            throw serializationFallback;
        }
    }

    private void handleError(String message) {
        // Checkstyle: stop
        boolean allowIncompleteClasspath = NativeImageOptions.AllowIncompleteClasspath.getValue();
        if (allowIncompleteClasspath) {
            System.out.println("WARNING: " + message);
        } else {
            throw UserError.abort(message + " To allow unresolvable reflection configuration, use option -H:+AllowIncompleteClasspath");
        }
        // Checkstyle: resume
    }
}
