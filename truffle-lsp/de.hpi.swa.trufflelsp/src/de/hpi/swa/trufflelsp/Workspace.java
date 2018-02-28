package de.hpi.swa.trufflelsp;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.services.WorkspaceService;

public class Workspace implements WorkspaceService {

	private String trace_server = "off";

	@Override
	public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
		List<? extends SymbolInformation> result = new ArrayList<>();
	    return CompletableFuture.completedFuture(result);
	}

	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params) {
		if (params.getSettings() instanceof Map<?, ?>) {
			Map<?, ?> settings = (Map<?, ?>) params.getSettings();
			if (settings.get("truffleLsp") instanceof Map<?, ?>) {
				Map<?, ?> truffleLsp = (Map<?, ?>) settings.get("truffleLsp");
				if (truffleLsp.get("trace") instanceof Map<?, ?>) {
					Map<?, ?> trace = (Map<?, ?>) truffleLsp.get("trace");
					if (trace.get("server") instanceof String) {
						trace_server  = (String) trace.get("server");
					};
				}
			}
		}
	}

	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
//		System.out.println(params);		
	}
	
	public boolean isVerbose() {
		return "verbose".equals(this.trace_server);
	}

}
