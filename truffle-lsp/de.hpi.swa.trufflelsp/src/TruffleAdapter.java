

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.services.LanguageClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;

public class TruffleAdapter {
	private LanguageClient client;
	private Workspace workspace;

	public void connect(final LanguageClient client, Workspace workspace) {
		this.client = client;
		this.workspace = workspace;
	}

	public void reportDiagnostics(final List<Diagnostic> diagnostics, final String documentUri) {
		if (diagnostics != null) {
			PublishDiagnosticsParams result = new PublishDiagnosticsParams();
			result.setDiagnostics(diagnostics);
			result.setUri(documentUri);
			client.publishDiagnostics(result);
		}
	}

	public synchronized List<Diagnostic> parse(String text, String langId, String documentUri) {
		List<Diagnostic> diagnostics = new ArrayList<>();
		Source source;
		try {
			String lang = langId;
			if (lang.equals("truffle-python")) {
				lang = "python";
				source = Source.newBuilder(lang, text, documentUri).build();
				Context context = Context.create(lang);
				Instrument instrument = context.getEngine().getInstruments().get(GlobalsInstrument.ID);				
				System.out.println(instrument);
				
				instrument.lookup(Object.class);
				Value value = context.eval(source);
			}
		} catch (IOException | IllegalStateException e) {
			e.printStackTrace(ServerLauncher.errWriter());
		} catch (PolyglotException e) {
			if (this.workspace.isVerbose()) {
				e.printStackTrace(ServerLauncher.errWriter());
			}
			
			Range range = new Range(new Position(), new Position());
			
			if (e.isSyntaxError()) {
				Pattern pattern = Pattern.compile(", line: (\\d+), index: (\\d+),");
				Matcher matcher = pattern.matcher(e.getMessage());
				if (matcher.find() && matcher.groupCount() == 2) {
					String lineText = matcher.group(1);
					lineText = matcher.group(1); 
					String indexText = matcher.group(2);
					int line = Integer.parseInt(lineText);
					int index = Integer.parseInt(indexText);
					range = new Range(
							new Position(line-1, index),
							new Position(line-1, index));
				}
				
			}
			
			if (e.getSourceLocation() != null && e.getSourceLocation().isAvailable()) {
				SourceSection sl = e.getSourceLocation();
				range = new Range(
						new Position(sl.getStartLine()-1, sl.getStartColumn()-1),
						new Position(sl.getEndLine()-1, sl.getEndColumn()-1));
			}
			diagnostics.add(new Diagnostic(range, e.getMessage(), DiagnosticSeverity.Error, "Polyglot"));
		}
		return diagnostics;
	}

	public static String docUriToNormalizedPath(final String documentUri) throws URISyntaxException {
		URI uri = new URI(documentUri).normalize();
		return uri.getPath();
	}

}
