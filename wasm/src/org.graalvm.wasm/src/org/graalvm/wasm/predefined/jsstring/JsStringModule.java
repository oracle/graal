package org.graalvm.wasm.predefined.jsstring;

import static org.graalvm.wasm.WasmType.EXTERNREF_TYPE;
import static org.graalvm.wasm.WasmType.EXTERNREF_TYPE_ARRAY;
import static org.graalvm.wasm.WasmType.I32_TYPE;
import static org.graalvm.wasm.WasmType.ARRAYREF_TYPE;

import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.BuiltinModule;

public class JsStringModule extends BuiltinModule {
    @Override
    protected WasmModule createModule(WasmLanguage language, WasmContext context, String name) {

        WasmModule module = WasmModule.createBuiltin(language, name);
        defineFunction(context, module, "cast", types(EXTERNREF_TYPE), types(EXTERNREF_TYPE), new CastNode(language, module));
        defineFunction(context, module, "test", types(EXTERNREF_TYPE), types(I32_TYPE), new TestNode(language, module));
        defineFunction(context, module, "fromCharCodeArray", types(EXTERNREF_TYPE, I32_TYPE, I32_TYPE), types(EXTERNREF_TYPE), new FromCharCodeArrayNode(language, module));
        defineFunction(context, module, "intoCharCodeArray", types(EXTERNREF_TYPE, EXTERNREF_TYPE, I32_TYPE), types(I32_TYPE), new IntoCharCodeArrayNode(language, module));
        defineFunction(context, module, "fromCharCode", types(I32_TYPE), types(EXTERNREF_TYPE), new FromCharCodeNode(language, module));
        defineFunction(context, module, "fromCodePoint", types(I32_TYPE), types(EXTERNREF_TYPE), new FromCodePointNode(language, module));
        defineFunction(context, module, "charCodeAt", types(EXTERNREF_TYPE, I32_TYPE), types(I32_TYPE), new CharCodeAtNode(language, module));
        defineFunction(context, module, "codePointAt", types(EXTERNREF_TYPE, I32_TYPE), types(I32_TYPE), new CodePointAtNode(language, module));
        defineFunction(context, module, "length", types(EXTERNREF_TYPE), types(I32_TYPE), new LengthNode(language, module));
        defineFunction(context, module, "concat", types(EXTERNREF_TYPE, EXTERNREF_TYPE), types(EXTERNREF_TYPE), new ConcatNode(language, module));
        defineFunction(context, module, "substring", types(EXTERNREF_TYPE, I32_TYPE, I32_TYPE), types(EXTERNREF_TYPE), new SubstringNode(language, module));
        defineFunction(context, module, "equals", types(EXTERNREF_TYPE, EXTERNREF_TYPE), types(I32_TYPE), new EqualsNode(language, module));
        defineFunction(context, module, "compare", types(EXTERNREF_TYPE, EXTERNREF_TYPE), types(I32_TYPE), new CompareNode(language, module));

        return module;
    }
}