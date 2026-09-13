---
name: mimo-tts
description: Configure Xiaomi MiMo cloud TTS (preset / voicedesign / voiceclone models, voices, style tags). Use when the user asks about MiMo TTS, 小米语音合成, voice design/clone, or cloud TTS setup/troubleshooting.
mode: chat
resources: mimoTtsHelp
---

# MiMo TTS

## Goal

Help the user create or fix a **MiMo** cloud TTS engine, then verify with `test_tts`.

## Tools (Ability Management must enable them)

1. `read_tts_config` — current engines / default
2. `read_cloud_tts_engine` — one engine detail
3. `patch_cloud_tts_engine` — create/update (`provider=mimo`); **never put real apiKey in args** (omit or `***`; app shows local secret panel)
4. `list_cloud_tts_voices` — sync/list voices after save
5. `test_tts` — speak a short sample
6. `set_default_tts_engine` — optional default switch
7. `search_rule_help` — load docs (see below)

## Progressive docs

- Core workflow: this SKILL.md (already loaded when you opened the skill)
- Full MiMo API reference (models, voices, style tags, samples):  
  `search_rule_help` with `scope=skill:mimo-tts` and `doc=mimoTtsHelp`  
  (optional keyword in `query` to excerpt sections)

Do **not** paste the full reference into user-facing replies.

## Workflow

1. Call `read_tts_config`. Ask for API Key via the app secret panel — do not request the user paste keys into chat if the panel can collect them.
2. Choose model:
   - `mimo-v2.5-tts` — preset voices (`冰糖`/`茉莉`/`苏打`/`白桦`/`Mia`/`Chloe`/`Milo`/`Dean`/`mimo_default`); supports `(唱歌)` tags
   - `mimo-v2.5-tts-voicedesign` — text voice design; no preset/clone/sing
   - `mimo-v2.5-tts-voiceclone` — clone from audio sample; no preset/design/sing
3. `patch_cloud_tts_engine` with `provider=mimo`, model, voice (if preset), and other fields the tool schema allows. Confirm with the user before mutating.
4. After save: `list_cloud_tts_voices` if needed, then `test_tts`.
5. For style / tag / API edge cases → load `doc=mimoTtsHelp` on demand.

## Style control (short)

- Natural-language style → user message (voicedesign: required)
- Speak text → assistant message (never put speak text only in user)
- Inline tags on speak text, e.g. `(开心)你好` or `[叹气]` mid-sentence
- Singing on preset model: start with `(唱歌)` / `(sing)` / `(singing)`

## Defaults

- Base URL: `https://api.xiaomimimo.com/v1`
- Streaming audio format in-app: `pcm16` (24 kHz)
