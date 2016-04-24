import re
import subprocess

gitLogOneLine = re.compile(r'^(?P<message>.*)$')
titleWithEndingPunct = re.compile(r'^(.*)[\.!?]$')
pastTenseWords = ['installed', 'implemented', 'fixed', 'merged', 'improved', 'simplified', 'enhanced', 'changed', 'removed', 'replaced', 'substituted', 'corrected', 'used', 'moved', 'refactored']

def checkCommitMessage(quotedCommitMessage):
    error = False
    message = ''
    commitMessage = quotedCommitMessage[1:-1]
    if commitMessage[0].islower():
        error = True
        message = quotedCommitMessage + ' starts with a lower case character'
    if titleWithEndingPunct.match(commitMessage):
        error = True
        message = quotedCommitMessage + ' ends with period, question mark, or exclamation mark'
    if commitMessage.lower().split()[0] in pastTenseWords:
        error = True
        print quotedCommitMessage, 'starts with past tense word "' + commitMessage.lower().split()[0] + '"'
    return (error, message)

def logCheck(args=None):
    output = subprocess.check_output(['git', 'log', '--pretty=format:"%s"', 'master@{u}..'])
    foundErrors = []
    for s in output.splitlines():
        match = gitLogOneLine.match(s)
        commitMessage = match.group('message')
        (isError, curMessage) = checkCommitMessage(commitMessage)
        if isError:
            foundErrors.append(curMessage)
    if foundErrors:
        for curMessage in foundErrors:
            print curMessage
        print "\nFound illegal git log messages! Please check CONTRIBUTING.md for commit message guidelines."
        exit(-1)
