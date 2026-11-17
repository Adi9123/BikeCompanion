# CLAUDE.md

@AGENTS.md

## Notes for Claude

- Start every UI task by reading `docs/design/USER_STORIES.md` and
  `docs/design/DESIGN_SYSTEM.md`. Name the story a change serves in its
  commit message or KDoc when it is not obvious.
- When a design decision is not covered by the design system, decide it, then
  add it to `DESIGN_SYSTEM.md` in the same change, so the next person does not
  decide it differently.
- Builds happen only in CI. After pushing, wait for the run and read the
  failing log lines; do not start a local Gradle build to "check quickly".
- Never add `Co-Authored-By` or any other attribution trailer to commits.
- Keep private details (real places, the bike's MAC address, the owner's email)
  out of everything that is committed or published.
