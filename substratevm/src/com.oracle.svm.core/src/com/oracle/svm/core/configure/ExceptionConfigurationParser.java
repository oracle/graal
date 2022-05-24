package com.oracle.svm.core.configure;

import java.net.URI;

public class ExceptionConfigurationParser extends ConfigurationParser {
    public ExceptionConfigurationParser(boolean strictConfiguration) {
        super(strictConfiguration);
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
    }
}
