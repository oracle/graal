package com.oracle.truffle.host;

import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

@GenerateLibrary(receiverType = HostInstance.class)
abstract class HostLibrary extends Library {
    private static final LibraryFactory<HostLibrary> FACTORY = HostLibraryGen.resolve(HostLibrary.class);
    private static final HostLibrary UNCACHED = FACTORY.getUncached();

    static HostLibrary getUncached() {
        return UNCACHED;
    }
    
    static LibraryFactory<HostLibrary> getFactory() {
        return FACTORY;
    }
    
    public HostInstance asHostInstance(Object obj) {
        return obj instanceof HostInstance ? (HostInstance) obj : null;
    }
}
