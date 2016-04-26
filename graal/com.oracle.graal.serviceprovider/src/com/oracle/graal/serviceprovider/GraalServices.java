package com.oracle.graal.serviceprovider;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import jdk.vm.ci.services.Services;

/**
 * A mechanism for accessing service providers that abstracts over whether Graal is running on
 * JVMCI-8 or JVMCI-9.
 */
public class GraalServices {

    private GraalServices() {
    }

    private static final boolean JDK8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    /**
     * Gets an {@link Iterable} of the providers available for a given service.
     *
     * @throws SecurityException if on JDK8 or earlier and a security manager is present and it
     *             denies {@code RuntimePermission("jvmci")}
     */
    public static <S> Iterable<S> load(Class<S> service) {
        return JDK8OrEarlier ? Services.load(service) : ServiceLoader.load(service);
    }

    /**
     * Gets the provider for a given service for which at most one provider must be available.
     *
     * @param service the service whose provider is being requested
     * @param required specifies if an {@link InternalError} should be thrown if no provider of
     *            {@code service} is available
     */
    public static <S> S loadSingle(Class<S> service, boolean required) {
        if (JDK8OrEarlier) {
            return Services.loadSingle(service, required);
        }
        Iterable<S> providers = ServiceLoader.load(service);
        S singleProvider = null;
        try {
            for (Iterator<S> it = providers.iterator(); it.hasNext();) {
                singleProvider = it.next();
                if (it.hasNext()) {
                    throw new InternalError(String.format("Multiple %s providers found", service.getName()));
                }
            }
        } catch (ServiceConfigurationError e) {
            // If the service is required we will bail out below.
        }
        if (singleProvider == null && required) {
            throw new InternalError(String.format("No provider for %s found", service.getName()));
        }
        return singleProvider;
    }
}