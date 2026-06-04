# Documentation Terminology and Style Guide

Use this guide when editing, reviewing, or creating Markdown documentation in the Graal repo.
Ask your agent to consult it before drafting, rewriting, or reviewing documentation so it can follow GraalVM documentation conventions for terminology, tone, spelling, and Markdown structure.
Apply these rules to new or modified content only; do not refactor documentation in bulk just to align older pages with this guide.

## Writing Style

- Write in a conversational but professional tone.
- Address the reader as **you**.
- Prefer **you** for user actions and guidance.
- Avoid **we** in documentation; rewrite the sentence in a neutral, direct way, for example *The recommended action is to enable this option*, or *For most use cases, enable this option*.
- Avoid contractions, for example use *do not* instead of *don't*.
- Prefer active voice over passive voice, for example “Use the `native-image` builder” instead of “The `native-image` builder should be used.”
- Avoid marketing language and phrases such as "Let's ...", "It's easy", or "It's simple", for example prefer *To build the executable, run:* over *Let's easily build the executable*.
- Keep sentences reasonably short; split long or winding sentences, for example prefer two sentences over one sentence that spans several lines.
- When documenting a UI or API, match the product terminology.
- For button names, capitalize the label as it appears on screen, for example “Click Abort.”
- For action names, use quotation marks, for example *Invoke the "Set as Default Java" action.*

## Terminology

- Always write *GraalVM*, never *Graal VM*.
- Do not use *Graal* as shorthand for the product; reserve Graal for the compiler, as in *the Graal compiler* or *the Graal JIT compiler*.
- Prefer *GraalVM Community Edition* on first use in the document. After the first mention, *GraalVM CE* is acceptable when the reference is clear.
- For shared documentation, do not distinguish between GraalVM Community Edition and Oracle GraalVM unless the distinction is necessary.
- When referring specifically to the JDK distribution, use *the GraalVM JDK*.
- Use proper feature names exactly as written, for example *Espresso*, *Web Image*.
- Use *Polyglot Isolates* in a title or heading, and *a polyglot isolate* or *polyglot isolates* in running prose.
- In embedding documentation, prefer *polyglot context*, *guest language*, or *context builder* over *language context* unless you are documenting an API term.
- Use *Native Image* as the user-facing technology name; do not use internal names such as *Substrate VM* or *svm* in user documentation.
- When referring to the build result, say *a native executable*, *a native image* lowercase, or *a native binary*.
- When referring to the tool, use *the `native-image` tool*, *the `native-image` generator*, *the `native-image` builder*, or just `native-image` hyphenated.
- When referring to the build process, use *build time*, *building an executable*, or *generating an executable*.
- If you need to distinguish Native Image editions, use *Oracle GraalVM Native Image* and *GraalVM Community Native Image*.
- On first use, spell out most acronyms in the form *full term (ACRONYM)*, then use the acronym in later references, for example *Profile-Guided Optimization (PGO)*, then *PGO*.
- On first mention, prefer the full tool or feature name; use a shorter form only after the reference is clear, for example *the Truffle Language Implementation Framework* then *Truffle*, or *Oracle GraalVM Native Image* then *Native Image*.
- On first mention, use *Tracing Agent*; after that, *the agent* is acceptable when the reference is clear.
- Use Graal language implementation names such as *GraalPy*, *GraalJS*, and *GraalWasm*.
- Use *Graal Languages* for languages implemented with Truffle.
- Use *the Truffle Language Implementation Framework*, *the Truffle framework*, or *Truffle*.
- Use *Truffle API*, *Truffle Runtime API*, and *Instrument API* or *Truffle Instrument API*.
- Use *GraalVM Polyglot API*, not *Graal Polyglot API*.

## Spelling and Grammar

- Prefer American English spelling, for example *behavior*, *organize*, and *specialize*.
- Use the Oxford comma, for example *red, green, and blue*.
- Prefer inclusive language and avoid insensitive terms such as *blacklist*, *whitelist*, *master*, *slave*, *dummy*, and *sanity-check*.
- Use *runtime* as a noun, but use *build time* and *run time* for timing, and *build-time* and *run-time* as adjectives.
- Avoid Latin abbreviations such as *e.g.* and *i.e.* when a plain-English phrase works better. Prefer *for example*, *that is*, or *in other words* in prose.
- Avoid *etc.* when a plain-English phrase works better. Prefer a precise list, *and other ...*, or no catch-all.
- Prefer precise verbs over vague uses of *get*, for example use *retrieve*, *produce*, or *initialize*.

## Markdown

- Use `#` for the page title, `##` for major sections, and `###` for subsections; do not skip directly from `#` to `###`.
- Use concise, descriptive titles and headings in Title Case.
- Leave one blank line after headings and between paragraphs.
- Start each sentence on a new line.
- For notes, start the paragraph with Markdown blockquote syntax (`>`), for example `> Note: This feature is not available in GraalVM Community Edition.`
- Add cross-links with standard Markdown link syntax, for example `[Native Image Overview](../reference-manual/native-image/Overview.md)`, and prefer descriptive link text over raw URLs.
- Use backticks for inline code, commands, options, environment variables, system properties, API names, and tool names, for example `--verbose`.
- Italicize filenames, directory names, and paths in prose, for example _Example.java_ or _META-INF/native-image_.
- Preserve exact spelling and capitalization for filenames, paths, API names, command output, generated content, and quoted text.
- Introduce command blocks with a short lead-in sentence and a colon.
- Use fenced code blocks with an explicit language such as `shell`, `java`, or `json`.
