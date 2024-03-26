package com.oracle.svm.hosted;

import static com.oracle.svm.core.jdk.Resources.createStorageKey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.Resources;

@Platforms(Platform.HOSTED_ONLY.class)
public class EmbeddedResourcesInfo {

    private final ConcurrentHashMap<Resources.ModuleResourceRecord, List<String>> registeredResources = new ConcurrentHashMap<>();

    public ConcurrentHashMap<Resources.ModuleResourceRecord, List<String>> getRegisteredResources() {
        return registeredResources;
    }

    public static EmbeddedResourcesInfo singleton() {
        return ImageSingletons.lookup(EmbeddedResourcesInfo.class);
    }

    public void declareResourceAsRegistered(Module module, String resource, String source) {
        Resources.ModuleResourceRecord key = createStorageKey(module, resource);
        synchronized (registeredResources) {
            registeredResources.computeIfAbsent(key, k -> new ArrayList<>());
            if (!registeredResources.get(key).contains(source)) {
                registeredResources.get(key).add(source);
            }
        }
    }

}
