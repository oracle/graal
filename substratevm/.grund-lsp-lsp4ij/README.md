# Grund in IntelliJ IDEA

This directory contains the LSP4IJ import template for the Native Image Grund project.
The template launches the locally installed `grund-lsp` and maps Markdown, Java, and shell files.
The template resolves `grund-lsp` through the IDE's `PATH`.
Set `GRUND_LSP_LSP4IJ_EXECUTABLE` in _template.json_ to an absolute path if the IDE cannot find your installation.

## Setup

1. Install LSP4IJ 0.20.2 or later and install a `grund-lsp` version with citation completion.
2. Open Settings > Languages & Frameworks > Language Servers.
3. Choose + > New Language Server > Import from custom template and select this directory.
   If Grund is already configured, update that definition instead of adding a second server.
4. In the Grund server's Configuration > Client tab, merge _client-config.json_ into the existing JSON.
5. Apply the settings and restart the Grund server.
6. Under Settings > Editor > General > Code Completion, enable "Show suggestions as you type".

Importing the template configures the server and file mappings.
The client settings must also be applied in step 4; keeping the JSON file in the repository does not activate them in IntelliJ.

## Typing Citations

In ordinary Markdown text or a Java comment, type `$$`.
Grund advertises `$` as a completion trigger and can offer the project's declaration IDs immediately after the second `$`.
Continue with `FS-` to narrow the suggestions, then accept a suggestion to insert the canonical citation.
If the popup does not appear, press Ctrl+Space to check that completion is connected.
Restart the server after upgrading `grund-lsp` so IntelliJ receives its current completion capabilities.

For automatic conversion without selecting a completion, type `$$FS-002` followed by Space in a file under _substratevm_.
With server-side on-type formatting enabled, the shorthand expands to the full security-provider specification citation.
Completion is unavailable in protected contexts such as fenced examples, inline code, and ordinary Java string literals.

## Verification and Troubleshooting

Check hover and navigation on an existing citation, then try completion and automatic conversion separately.
In the LSP console, typing `$$` should cause a `textDocument/completion` request.
Typing a citation should also cause `textDocument/onTypeFormatting` requests.
If a request is absent, check the client settings and file mappings.
If requests are present but no edits or suggestions return, inspect their responses and the Grund server log.

LSP4IJ releases before 0.20.2 had a bug that could send on-type formatting before the document change.
See the [LSP4IJ release notes](https://github.com/redhat-developer/lsp4ij/releases/tag/0.20.2) and
[on-type formatting documentation](https://github.com/redhat-developer/lsp4ij/blob/main/docs/LSPSupport.md#server-side-on-type-formatting).

Regenerating this directory with `grund-lsp integrations lsp4ij --write` produces the default import files.
Preserve these project instructions and _client-config.json_ when updating the generated template.
