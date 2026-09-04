NEVER use IDE_syncFiles.

Minimize IDE_getDiagnostics because it opens files and disrupts the user's editor focus. Call it at most once per task, only after all edits are complete, and only for files modified by that task. Do not call it in parallel or while other work is ongoing. Prefer terminal-based compilation and tests for validation. Use additional IDE_getDiagnostics calls only when terminal diagnostics are insufficient to resolve a specific problem.

NEVER execute any git command without explicit user approival.  Before using any git command, explain explicitly in English why it is necessary,  what effect the git command will have, and what the risks are.

Modify code comments to match code changes, but NEVER delete them unless the accompanying code is also deleted.
