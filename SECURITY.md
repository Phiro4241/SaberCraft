# Security policy

## Supported versions

SaberCraft has no published release yet. Security fixes go into the `main` branch.

| Version | Supported |
| --- | --- |
| `main` (0.1.0, Minecraft 26.2) | ✅ |
| older builds | ❌ |

## What counts as a security issue

SaberCraft runs on clients and servers and exchanges custom network packets, so please report privately for example:

- a client that can make a server crash, hang, use excessive memory or CPU, or act for another player through SaberCraft packets or commands
- a way around server-side checks (cooldowns, ownership of droids and ships, command permissions, duel consent)
- file access outside `config/sabercraft/` through custom profiles, skins or music files
- leaked credentials or personal data in the repository

Gameplay bugs and balance problems are not security issues; please use a normal [bug report](https://github.com/Phiro4241/SaberCraft/issues/new?template=bug_report.yml) for those.

## How to report

Please **do not open a public issue** for a security problem.

Use GitHub's private vulnerability reporting: open the repository's **Security** tab and click **Report a vulnerability**. If that button is not available, open a public issue that only says you have a security report and asks for a private contact, without any details.

Please include the SaberCraft version or commit, whether it affects the client, the server or both, and steps to reproduce.

This is a hobby project maintained in spare time. You can expect a first answer within about a week. Please give us reasonable time to fix the problem before you talk about it publicly.
