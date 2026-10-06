# Shared integration workflow

- Unless the user explicitly directs otherwise, the canonical integration branch
  on both PCs is `user/asafmahlev/v2-beta-429-picker-complete`.
- Before each task, fetch that branch from `origin`, inspect the current
  worktree's changes and local/remote ahead-behind counts, and preserve unpushed
  work. Fast-forward only when safe; stop on divergence or concurrent changes.
- Start integration work in an isolated worktree from the shared tip. Never
  modify another live checkout or the main checkout.
- Commit completed changes as separate coherent commits. After a fresh fetch and
  revalidation, use a normal push explicitly targeting only the shared ref.
  Stop if its tip changed during the task; do not rebase or overwrite it silently.
  Never force-push or change `main` or `v2` without explicit user approval.
- Debug builds must use the existing local original signing key. Before running
  Gradle, verify its certificate against an actual previously signed APK or the
  installed app; stop if the key is missing, unreadable, or mismatched. Never
  generate, replace, upload, or commit signing keys or signing secrets, and never
  uninstall the app to bypass a signer mismatch.
- Before building a shared integration APK, verify the requested upstream
  baseline and picker features. Report the exact built commit and artifact path.

These defaults do not override separately authorized work on another branch or
an independent pull request.
