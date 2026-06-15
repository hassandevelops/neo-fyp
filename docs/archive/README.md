<!-- DEPRECATED — see docs/CURRENT.md. This file describes an older architecture that has been superseded. -->
# Neo — Project Context Index

> This directory is the permanent project memory system for Neo.
> Start every new AI conversation with `compressed_context.md`.

---

## Quick Start for AI Chats

1. **New chat, need full context fast?** → Paste `compressed_context.md`
2. **Working on a specific area?** → Paste `compressed_context.md` + relevant topic file
3. **Debugging a tricky issue?** → Check `memory.md` (failed approaches + pitfalls)

---

## File Map

| File | Purpose | When to Read |
|---|---|---|
| `compressed_context.md` | Dense single-file project summary | **Every new chat — paste this first** |
| `memory.md` | Architecture decisions, pitfalls, failed approaches | Debugging, refactoring, before major changes |
| `project_overview.md` | What Neo is, vision, features list, status | Onboarding new collaborators |
| `architecture.md` | Full system architecture, data flow, patterns | Architecture changes, understanding how things connect |
| `tech_stack.md` | All libraries, versions, why each was chosen | Adding dependencies, updating versions |
| `database.md` | Full schema, relationships, queries, migrations | DB changes, querying, debugging data issues |
| `features.md` | Every feature: purpose, flow, implementation, bugs | Feature development, bug investigation |
| `ui_ux.md` | Design system, color palette, screen-by-screen UI | UI changes, new screens, design decisions |
| `api_reference.md` | Bluetooth message protocol (the internal "API") | Adding message types, protocol changes |
| `ai_system.md` | AI integration (none exists — documents why) | If AI features are ever added |
| `development_workflow.md` | Build, test, code standards, naming conventions | New developer onboarding, CI setup |
| `setup.md` | Installation, permissions, local dev guide | First-time setup, environment issues |
| `roadmap.md` | Current bugs, tech debt, planned features | Sprint planning, prioritization |
| `component_registry.md` | All Compose components + ViewModels documented | Component development, props reference |
| `screen_registry.md` | All screens, routes, UI elements, nav flow | Navigation changes, new screen implementation |

---

## Project At A Glance

```
Neo = Decentralized Android Social Network
    No internet. No servers. Bluetooth mesh only.
    
Stack: Kotlin · Jetpack Compose · Hilt · Room · BLE/RFCOMM
Arch:  Clean Architecture (MVVM + Use Cases + Gossip Protocol)
Sec:   Ed25519 signatures · EncryptedSharedPreferences
DB:    Room v8 (7 tables, 7 migrations)
UI:    Cyberpunk/Neon aesthetic · Pure black · Glassmorphism

Status: FYP-complete ✅  |  Build: Stable ✅  |  Tests: Unit + Integration ✅
```

---

## Last Updated

**May 2026** — Generated from actual codebase by AI documentation assistant.  
This directory reflects the state of the codebase as of the FYP submission milestone.

---

> 📝 `memory.md` should be updated manually as you make important decisions, hit bugs, or change direction.
