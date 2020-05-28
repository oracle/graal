package org.graalvm.compiler.hotspot;

import org.graalvm.compiler.options.OptionValues;

public interface InstrumentationFactory {

    Instrumentation createInstrumentation(OptionValues options);
}
