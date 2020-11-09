/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from "vscode";
import * as path from 'path';
import * as fs from 'fs';
import { getGVMHome } from "./graalVMConfiguration";

export function random(low: number, high: number): number {
    return Math.floor(Math.random() * (high - low) + low);
}

export function findExecutable(program: string, graalVMHome?: string): string | undefined {
	graalVMHome = graalVMHome || getGVMHome();
    if (graalVMHome) {
        let executablePath = path.join(graalVMHome, 'bin', program);
        if (process.platform === 'win32') {
            if (fs.existsSync(executablePath + '.cmd')) {
                return executablePath + '.cmd';
            }
            if (fs.existsSync(executablePath + '.exe')) {
                return executablePath + '.exe';
            }
        } else if (fs.existsSync(executablePath)) {
            return executablePath;
        }
    }
    return undefined;
}

export async function ask(question: string, options: {option: string, fnc?: (() => any)}[], otherwise?: (() => any)): Promise<any> {
	const select = await vscode.window.showInformationMessage(question, ...options.map(o => o.option));
	if (!select) {
		if (!otherwise) {
			return;
		} else {
			return otherwise();
		}
	}
	const opt = options.find(o => o.option === select);
	if (opt && opt.fnc) {
		return opt.fnc();
	}
	return;
}

const YES: string = 'Yes';
const NO: string = 'No';
export async function askYesNo(question: string, ifYes: (() => any) | undefined, ifNo?: (() => any), otherwise?: (() => any)): Promise<any> {
	ask(question, [{option: YES, fnc: ifYes}, {option: NO, fnc: ifNo}], otherwise);
}

const INSTALL: string = 'Install';
async function askInstall(question: string, ifYes: (() => any) | undefined, otherwise?: (() => any)): Promise<any> {
	ask(question, [{option: INSTALL, fnc: ifYes}], otherwise);
}

export async function runInTerminal(command: string) {
    let terminal: vscode.Terminal | undefined = vscode.window.activeTerminal;
    if (!terminal) {
        terminal = vscode.window.createTerminal();
	}
    terminal.show();
	terminal.sendText(command);
}

export function checkRecommendedExtension(extensionName: string, display: string): boolean {
	const extension =  vscode.extensions.getExtension(extensionName);
	if (!extension) {
		askInstall(`Do you want to install the recommended extensions for ${display}?`, 
			() => runInTerminal(`code --install-extension ${extensionName}`));
	}
	return extension !== undefined;
}

export function isSymlinked(dirPath: string): Promise<boolean> {
    return new Promise((resolve, reject) => {
        fs.lstat(dirPath, (err, stats) => {
            if (err) {
                reject(err);
            }
            if (stats.isSymbolicLink()) {
                resolve(true);
            } else {
                const parent = path.dirname(dirPath);
                if (parent === dirPath) {
                    resolve(false);
                } else {
                    resolve(isSymlinked(parent));
                }
            }
        });
    });
}

class InputFlowAction {
	static back = new InputFlowAction();
	static cancel = new InputFlowAction();
	static resume = new InputFlowAction();
}

type InputStep = (input: MultiStepInput) => Thenable<InputStep | void>;

interface QuickPickParameters<T extends vscode.QuickPickItem> {
	title: string;
	step: number;
	totalSteps: number;
	items: T[];
	activeItem?: T;
	placeholder: string;
	postProcess?: (value: T) => Promise<void>;
	buttons?: vscode.QuickInputButton[];
	shouldResume: () => Thenable<boolean>;
}

interface InputBoxParameters {
	title: string;
	step: number;
	totalSteps: number;
	value: string;
	prompt: string;
	validate?: (value: string) => Promise<string | undefined>;
	buttons?: vscode.QuickInputButton[];
	shouldResume: () => Thenable<boolean>;
}

export class MultiStepInput {

	static async run(start: InputStep) {
		const input = new MultiStepInput();
		return input.stepThrough(start);
	}

	private current?: vscode.QuickInput;
	private steps: InputStep[] = [];

	private async stepThrough(start: InputStep) {
		let step: InputStep | void = start;
		while (step) {
			this.steps.push(step);
			if (this.current) {
				this.current.enabled = false;
				this.current.busy = true;
			}
			try {
				step = await step(this);
			} catch (err) {
				if (err === InputFlowAction.back) {
					this.steps.pop();
					step = this.steps.pop();
				} else if (err === InputFlowAction.resume) {
					step = this.steps.pop();
				} else if (err === InputFlowAction.cancel) {
					step = undefined;
				} else {
					throw err;
				}
			}
		}
		if (this.current) {
			this.current.dispose();
		}
	}

	async showQuickPick<T extends vscode.QuickPickItem, P extends QuickPickParameters<T>>({ title, step, totalSteps, items, activeItem, placeholder, postProcess, buttons, shouldResume }: P) {
		const disposables: vscode.Disposable[] = [];
		try {
			return await new Promise<T | (P extends { buttons: (infer I)[] } ? I : never)>((resolve, reject) => {
				const input = vscode.window.createQuickPick<T>();
				input.title = title;
				input.step = step;
				input.totalSteps = totalSteps;
				input.placeholder = placeholder;
				input.items = items;
				if (activeItem) {
					input.activeItems = [activeItem];
				}
				input.buttons = [
					...(this.steps.length > 1 ? [vscode.QuickInputButtons.Back] : []),
					...(buttons || [])
				];
				input.ignoreFocusOut = true;
				disposables.push(
					input.onDidTriggerButton(item => {
						if (item === vscode.QuickInputButtons.Back) {
							reject(InputFlowAction.back);
						} else {
							resolve(<any>item);
						}
					}),
					input.onDidAccept(async () => {
						const item = input.selectedItems[0];
						if (postProcess) {
							input.enabled = false;
							input.busy = true;
							try {
								await postProcess(item);
							} catch(e) {
								reject(InputFlowAction.cancel);
								vscode.window.showErrorMessage(e.message);
							}
							input.enabled = true;
							input.busy = false;
						}
						resolve(item);
					}),
					input.onDidHide(() => {
						(async () => {
							reject(shouldResume && await shouldResume() ? InputFlowAction.resume : InputFlowAction.cancel);
						})()
							.catch(reject);
					})
				);
				if (this.current) {
					this.current.dispose();
				}
				this.current = input;
				this.current.show();
			});
		} finally {
			disposables.forEach(d => d.dispose());
		}
	}

	async showInputBox<P extends InputBoxParameters>({ title, step, totalSteps, value, prompt, validate, buttons, shouldResume }: P) {
		const disposables: vscode.Disposable[] = [];
		try {
			return await new Promise<string | (P extends { buttons: (infer I)[] } ? I : never)>((resolve, reject) => {
				const input = vscode.window.createInputBox();
				input.title = title;
				input.step = step;
				input.totalSteps = totalSteps;
				input.value = value || '';
				input.prompt = prompt;
				input.buttons = [
					...(this.steps.length > 1 ? [vscode.QuickInputButtons.Back] : []),
					...(buttons || [])
				];
				input.ignoreFocusOut = true;
				disposables.push(
					input.onDidTriggerButton(item => {
						if (item === vscode.QuickInputButtons.Back) {
							reject(InputFlowAction.back);
						} else {
							resolve(<any>item);
						}
					}),
					input.onDidAccept(async () => {
						const value = input.value;
						input.enabled = false;
						input.busy = true;
						if (validate) {
							input.validationMessage = await validate(value);
						}
						if (!input.validationMessage) {
							resolve(value);
						}
						input.enabled = true;
						input.busy = false;
					}),
					input.onDidChangeValue(async () => {
						input.validationMessage = undefined;
					}),
					input.onDidHide(() => {
						(async () => {
							reject(shouldResume && await shouldResume() ? InputFlowAction.resume : InputFlowAction.cancel);
						})()
							.catch(reject);
					})
				);
				if (this.current) {
					this.current.dispose();
				}
				this.current = input;
				this.current.show();
			});
		} finally {
			disposables.forEach(d => d.dispose());
		}
	}
}
