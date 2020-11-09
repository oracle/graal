/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as mustache from 'mustache';

export class LicenseCheckPanel {

	public static readonly viewType: string = 'graalVMLicenseCheck';

	private static readonly webviewsFolder: string = 'webviews';
	private static readonly userEmail: string = 'userEmail';

	private readonly _panel: vscode.WebviewPanel;
	private _disposables: vscode.Disposable[] = [];
	private static accepted: string[] = [];

	public static show(context: vscode.ExtensionContext, licenseLabel: string, license: string): Promise<boolean> {
		if (this.accepted.includes(license)) {
			return Promise.resolve(true);
		}
		return new Promise<boolean>(resolve => {
			const lcp = new LicenseCheckPanel(context, licenseLabel, license, (message: any) => {
				if (message.command === 'accepted') {
					context.globalState.update(LicenseCheckPanel.userEmail, message.email);
					this.accepted.push(license);
					lcp.dispose();
					resolve(true);
				} else {
					lcp.dispose();
					resolve(false);
				}
			});
		});
	}

	private constructor(context: vscode.ExtensionContext, licenseLabel: string, license: string, messageHandler: (message: any) => any) {
		const extensionPath = context.extensionPath;
		this._panel = vscode.window.createWebviewPanel(LicenseCheckPanel.viewType, licenseLabel,
			{ viewColumn: vscode.ViewColumn.One, preserveFocus: true },
			{
				enableScripts: true,
				localResourceRoots: [vscode.Uri.file(path.join(extensionPath, LicenseCheckPanel.webviewsFolder))]
			}
		);
		this._panel.iconPath = {
			light: vscode.Uri.file(path.join(extensionPath, LicenseCheckPanel.webviewsFolder, 'icons', 'law_light.png')),
			dark: vscode.Uri.file(path.join(extensionPath, LicenseCheckPanel.webviewsFolder, 'icons', 'law_dark.png'))
		};

		// Set the webview's html content
		this.setHtml(extensionPath, license, context.globalState.get(LicenseCheckPanel.userEmail) || '');

		// Listen for when the panel is disposed
		// This happens when the user closes the panel or when the panel is closed programatically
		this._panel.onDidDispose(() => this.dispose(), null, this._disposables);

		// Update the content based on view changes
		this._panel.onDidChangeViewState(
			() => {
				if (this._panel.visible) {
					this.setHtml(extensionPath, license, context.globalState.get(LicenseCheckPanel.userEmail) || '');
				}
			},
			null,
			this._disposables
		);

		// Handle messages from the webview
		this._panel.webview.onDidReceiveMessage(
			messageHandler,
			undefined,
			this._disposables
		);
	}

	private setHtml(extensionPath: string, license: string, email: string) {
		const templatePath = path.join(extensionPath, LicenseCheckPanel.webviewsFolder, 'licenseCheck.html');
		this._panel.webview.html = mustache.render(fs.readFileSync(templatePath).toString(), {
			cspSource: this._panel.webview.cspSource,
			email,
			license: license,
			cssUri: this._panel.webview.asWebviewUri(vscode.Uri.file(path.join(extensionPath, LicenseCheckPanel.webviewsFolder, 'styles', 'licenseCheck.css'))),
			jsUri: this._panel.webview.asWebviewUri(vscode.Uri.file(path.join(extensionPath, LicenseCheckPanel.webviewsFolder, 'scripts', 'licenseCheck.js')))
		});
	}

	public dispose() {
		// Clean up our resources
		this._panel.dispose();
		while (this._disposables.length) {
			const x = this._disposables.pop();
			if (x) {
				x.dispose();
			}
		}
	}
}
