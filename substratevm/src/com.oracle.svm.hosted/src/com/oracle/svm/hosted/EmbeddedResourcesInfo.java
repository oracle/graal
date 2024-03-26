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
        registeredResources.compute(key, (k, v) -> {
            if (v == null) {
                ArrayList<String> newValue = new ArrayList<>();
                newValue.add(source);
                return newValue;
            }

            /*
             * We have to avoid duplicated sources here. In case when declaring resource that comes
             * from module as registered, we don't have information whether the resource is already
             * registered or not. That check is performed later in {@link Resources.java#addEntry},
             * so we have to perform same check here, to avoid duplicates when collecting
             * information about resource.
             */
            if (!v.contains(source)) {
                v.add(source);
            }
            return v;
        });
    }

}
