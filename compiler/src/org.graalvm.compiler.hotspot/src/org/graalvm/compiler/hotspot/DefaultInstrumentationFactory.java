package org.graalvm.compiler.hotspot;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

@ServiceProvider(InstrumentationFactory.class)
public class DefaultInstrumentationFactory implements InstrumentationFactory {
    @Override
    public Instrumentation createInstrumentation(OptionValues options) {
        return new DefaultInstrumentation();
    }
}
