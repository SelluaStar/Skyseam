# vendor/ — read-only reference sources

## fealty-source/

A snapshot of the Fealty project, copied so that Skyseam's Fealty compat code can be checked against
the real API (`fealty-source/docs/API.md`, `src/api/`, and the GameTests).

**Read only. Never edit anything in this folder.** Fealty must not know about Skyseam. If Fealty
changes, re-copy the snapshot and update the provenance below. Do not patch it here.

| Field | Value |
|---|---|
| Copied from | `C:\Users\Sellu\Downloads\Fealty-git` (working tree) |
| Copied on | 2026-10-08 |
| Branch | `claude/eager-hopper-bov41a` |
| HEAD | `e057963b27ec6760bce4dbc08db49a93bbe79dc2` ("Rework the old crown chain: roles from the whole village, quest variants") |
| Working tree | **Dirty**: 745 changed or untracked paths. The API 1.3.0 work (rumours, conversations, flags, chain kinds, dialogue events) exists only as uncommitted changes in that checkout |
| Excluded | `.git/`, `build/`, `.gradle/`, `run/`, `build-log.txt` |
| Matching jars | `libs/fealty-0.1.0.jar` (SHA-256 `cd4ae67d39b6e85793a117df3df09e54cea965a5ac9128192e9c1c965b20b09c`) and `libs/fealty-0.1.0+api.1.3.0-api.jar` (SHA-256 `aca952e99d5ea81656d34998b9f727118af2e38ea8e8e16dd4f7c43207170d10`), built from this tree on 2026-10-08 09:06 local time |

The mod version is `0.1.0` and the public API version is `1.3.0` (`FealtyApi.API_VERSION`).
