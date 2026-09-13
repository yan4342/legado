(function () {
  "use strict";

  var state = {
    conversations: [],
    messages: [],
    streamingMessage: null,
    pendingToolConfs: [],
    pendingToolBatchFeedback: "",
    currentConversationId: null,
    conversationType: "chat",
    writingSubMode: "roleplay",
    isSending: false,
    hasMoreHistory: false,
    isLoadingHistory: false,
    messagesReady: false,
    outputMode: "ASK",
    webSearchArmed: false,
    galgameEnabled: false,
    interCharacterChatEnabled: true,
    dialogueHighlightEnabled: true,
    roleplayDialogueBubbleEnabled: true,
    structuredAutoMaintainEnabled: true,
    outlineEnabled: false,
    workspaceId: null,
    workspaceName: "",
    selectedCharacterName: "",
    userName: "",
    userCardEnabled: false,
    reasoningLevel: "AUTO",
    suggestions: [],
    suggestionsLoading: false,
    galgameHudHtml: "",
    galgameHudLoading: false,
    pendingAttachments: [],
    isProcessingAttachments: false,
    postEditEnabled: false,
    contextTokensUsed: 0,
    contextInputBudget: 0,
    contextWindow: 0,
    isCompressing: false,
    cacheHitRatio: 0,
    selectedCharacterNames: [],
    selectedCharacterCount: 0,
    outlineBranchChoice: null,
    pendingUserQuestions: null,
    pendingHabitMemoryConfirm: null,
    pendingPlan: null,
    pendingTtsSecretFills: [],
  };

  var writingInputMode = "DIALOGUE";
  var writingSwitchState = {};
  var slashMenuItems = [];
  var mentionMenuItems = [];
  var planFullscreenOpen = false;

  var themeState = {
    themeId: "default",
    themes: [],
    cssUrl: "theme.css",
    fragments: {},
  };

  var editingMessageId = null;
  var els = {};
  var slotDefaults = {};

  function $(id) {
    return document.getElementById(id);
  }

  function postIntent(payload) {
    if (!window.LegadoBridge || typeof window.LegadoBridge.postIntent !== "function") {
      console.warn("LegadoBridge unavailable", payload);
      return;
    }
    window.LegadoBridge.postIntent(JSON.stringify(payload));
  }

  function openSheet(sheet, id) {
    if (!window.LegadoBridge || typeof window.LegadoBridge.openSheet !== "function") {
      console.warn("LegadoBridge.openSheet unavailable", sheet);
      return;
    }
    var payload = { sheet: sheet };
    if (id) payload.id = id;
    window.LegadoBridge.openSheet(JSON.stringify(payload));
  }

  function copyText(text) {
    if (window.LegadoBridge && typeof window.LegadoBridge.copyText === "function") {
      window.LegadoBridge.copyText(String(text == null ? "" : text));
      return;
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(String(text == null ? "" : text));
    }
  }

  function escapeHtml(text) {
    return String(text == null ? "" : text)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  /** Shallow array equality — compares length and each element loosely. */
  function arraysEqual(a, b) {
    if (a === b) return true;
    if (!a || !b) return false;
    if (a.length !== b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] !== b[i]) return false;
    }
    return true;
  }

  /** Render text through markdown if available, else plain escape. */
  function renderText(t) {
    var raw = String(t == null ? "" : t);
    if (!raw) return "";
    var md = window.formatMarkdown;
    return typeof md === "function" ? md(raw) : escapeHtml(raw);
  }

  /** Extract SVG blocks from text. Returns {parts, svgs} where parts is text with SVGs
   *  replaced by placeholders and svgs is an array of SVG source strings. */
  function extractSvgs(text) {
    var svgs = [];
    var parts = [];
    var idx = 0;
    console.log("extractSvgs: inputLen=" + text.length + " hasFence=" + /```/.test(text) + " hasSvgTag=" + /<svg\b/i.test(text) + " hasCloseSvg=" + /<\/svg>/i.test(text));
    // Match ```svg code blocks with optional language tag
    var fenceRe = /```(?:svg)?\s*\n([\s\S]*?)```/g;
    var match;
    while ((match = fenceRe.exec(text)) !== null) {
      var svgCode = match[1].trim();
      console.log("extractSvgs: fence match len=" + svgCode.length + " startsSvg=" + /^\s*<svg\b/.test(svgCode));
      if (/^\s*<svg\b/.test(svgCode)) {
        if (match.index > idx) parts.push({ kind: "text", text: text.slice(idx, match.index) });
        svgs.push(svgCode);
        parts.push({ kind: "svg", index: svgs.length - 1 });
        idx = match.index + match[0].length;
      }
    }
    // Also detect raw <svg> tags not in code fences
    var rawRe = /<svg\b[\s\S]*?<\/svg>/gi;
    while ((match = rawRe.exec(text)) !== null) {
      var pos = match.index;
      var svgRaw = match[0];
      // Skip if this SVG is inside an already-extracted fence match
      var insideFence = false;
      var fenceRe2 = /```(?:svg)?\s*\n[\s\S]*?```/g;
      var fm;
      while ((fm = fenceRe2.exec(text)) !== null) {
        if (pos >= fm.index && pos < fm.index + fm[0].length) {
          insideFence = true;
          break;
        }
      }
      if (!insideFence) {
        console.log("extractSvgs: raw svg found at pos=" + pos + " len=" + svgRaw.length);
        if (pos > idx) parts.push({ kind: "text", text: text.slice(idx, pos) });
        svgs.push(svgRaw);
        parts.push({ kind: "svg", index: svgs.length - 1 });
        idx = pos + match[0].length;
      }
    }
    if (idx < text.length) parts.push({ kind: "text", text: text.slice(idx) });
    console.log("extractSvgs: result svgsFound=" + svgs.length + " partsCount=" + parts.length);
    return { parts: parts, svgs: svgs };
  }

  /** Render an SVG source string as an inline SVG card with action buttons.
   *  [localPath] — if non-empty, the file path for saveImage; otherwise uses saveSvgCode. */
  function renderSvgCard(svgSource, localPath) {
    var sanitized = svgSource
      .replace(/<script[\s\S]*?<\/script>/gi, "")
      .replace(/<foreignObject[\s\S]*?<\/foreignObject>/gi, "")
      .replace(/\bon\w+\s*=\s*"[^"]*"|\bon\w+\s*=\s*'[^']*'/gi, "")
      .replace(/\bon\w+\s*=\s*\S+/gi, "")
      .replace(/javascript\s*:/gi, "");
    var copyId = "svgc" + (++svgCodeId);
    svgCodeStore[copyId] = sanitized;
    var hasPath = localPath && String(localPath).trim();
    var saveId = "svgs" + (++svgCodeId);
    if (hasPath) {
      svgCodeStore[saveId] = String(localPath);
    } else {
      svgCodeStore[saveId] = sanitized;
    }
    return '<div class="msg-image svg-container">' +
      sanitized +
      '<div class="svg-actions">' +
      '<button type="button" class="chip-btn svg-copy-btn" data-svg-copy-id="' + copyId + '">复制代码</button>' +
      '<button type="button" class="chip-btn svg-save-btn" data-svg-save-id="' + saveId + '" data-svg-save-type="' + (hasPath ? 'path' : 'code') + '">保存图片</button>' +
      '</div></div>';
  }
  var foldOpenByKey = {};
  var svgCodeStore = {};  // id → SVG source — avoids encoding issues in data attrs
  var svgCodeId = 0;
  var composerToolsExpanded = false;
  var composerMoreOpen = false;
  var thinkingMenuOpen = false;
  var EXECUTING_PLACEHOLDER = "[Executing...]";

  /** Cache of last-rendered message ids so we can update the streaming bubble in-place. */
  var lastRenderedMessageIds = "";
  var lastRenderedNearBottom = true;

  /** One-time guard for the document-level rich-text delegation. */
  var richTextBound = false;
  var imageViewerEl = null;

  /** Fullscreen image preview overlay for message images. */
  function openImageViewer(src) {
    if (!src) return;
    if (!imageViewerEl) {
      imageViewerEl = document.createElement("div");
      imageViewerEl.className = "image-viewer";
      imageViewerEl.innerHTML = '<img alt="preview" />';
      imageViewerEl.addEventListener("click", function () {
        imageViewerEl.classList.remove("open");
      });
      document.body.appendChild(imageViewerEl);
    }
    imageViewerEl.querySelector("img").setAttribute("src", src);
    imageViewerEl.classList.add("open");
  }

  /** Throttle streaming innerHTML updates to at most one per animation frame (~16ms). */
  var streamUpdatePending = false;
  var pendingStreamMsg = null;

  /** Rebuild a single streaming message bubble's body, preserving fold open state. */
  function renderStreamingBody(streamEl, msg) {
    var bodyEl = streamEl.querySelector('.msg-body');
    if (!bodyEl) return;
    // Persist fold open state BEFORE destroying DOM elements.
    bodyEl.querySelectorAll("details.fold[data-fold-key]").forEach(function (el) {
      foldOpenByKey[el.getAttribute("data-fold-key")] = el.open;
    });
    var streamingWithFlag = Object.assign({}, msg, { streaming: true });
    bodyEl.innerHTML = formatBody(streamingWithFlag);
    bindFoldToggles(bodyEl);
    // Restore fold open state on newly created elements.
    bodyEl.querySelectorAll("details.fold[data-fold-key]").forEach(function (el) {
      var key = el.getAttribute("data-fold-key");
      if (key && foldOpenByKey[key]) {
        el.open = true;
      }
    });
    bodyEl.querySelectorAll(".tool-undo").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var sid = btn.getAttribute("data-snapshot");
        if (sid) postIntent({ type: "RestoreSnapshot", snapshotId: sid });
      });
    });
    bodyEl.querySelectorAll(".svg-copy-btn").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var id = btn.getAttribute("data-svg-copy-id");
        var code = id ? svgCodeStore[id] : "";
        if (code) copyText(code);
      });
    });
    bodyEl.querySelectorAll(".svg-save-btn").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var id = btn.getAttribute("data-svg-save-id");
        var type = btn.getAttribute("data-svg-save-type");
        var val = id ? svgCodeStore[id] : "";
        if (type === "path" && val && window.LegadoBridge && window.LegadoBridge.saveImage) {
          window.LegadoBridge.saveImage(val);
        } else if (type === "code" && val && window.LegadoBridge && window.LegadoBridge.saveSvgCode) {
          window.LegadoBridge.saveSvgCode(val);
        }
      });
    });
  }

  /** Batch a streaming message update to at most one per animation frame. */
  function scheduleStreamingBubble(streamMsg) {
    pendingStreamMsg = streamMsg;
    if (streamUpdatePending) return;
    streamUpdatePending = true;
    requestAnimationFrame(function () {
      streamUpdatePending = false;
      var msg = pendingStreamMsg;
      pendingStreamMsg = null;
      if (!msg) return;
      var list = els.messageList;
      if (!list) return;
      var streamEl = list.querySelector('.msg.streaming[data-msg-id="' + (msg.id || "streaming_temp") + '"]');
      if (!streamEl) return;
      renderStreamingBody(streamEl, msg);
    });
  }

  /** Apply a lightweight streaming update pushed by the native side. */
  function applyStreamingUpdate(effect) {
    if (effect.flags) state = Object.assign({}, state, effect.flags);
    var msg = effect.message;
    if (!msg) return;
    state.streamingMessage = state.streamingMessage
      ? Object.assign({}, state.streamingMessage, msg)
      : Object.assign({}, msg, { role: "assistant" });
    var list = els.messageList;
    if (!list) return;
    var streamMsg = state.streamingMessage;
    var streamEl = list.querySelector('.msg.streaming[data-msg-id="' + (streamMsg.id || "streaming_temp") + '"]');
    if (streamEl) {
      scheduleStreamingBubble(streamMsg);
    } else {
      // Bubble not rendered yet — force a full rebuild so it gets created once.
      lastRenderedMessageIds = "";
      renderMessages();
    }
  }

  function collapsibleBlock(title, bodyHtml, open, opts) {
    opts = opts || {};
    var key = opts.key || "";
    var cls = "fold" + (opts.process ? " process-card" : "") + (opts.streaming ? " streaming" : "");
    var status = opts.status
      ? '<span class="fold-status">' + escapeHtml(opts.status) + "</span>"
      : "";
    var isOpen = key && foldOpenByKey[key] !== undefined ? !!foldOpenByKey[key] : !!open;
    return (
      '<details class="' +
      cls +
      '"' +
      (key ? ' data-fold-key="' + escapeHtml(key) + '"' : "") +
      (isOpen ? " open" : "") +
      "><summary><span class=\"fold-summary-main\">" +
      '<span class="fold-chevron" aria-hidden="true"></span>' +
      '<span class="fold-title">' +
      escapeHtml(title) +
      "</span>" +
      status +
      (opts.streaming ? '<span class="fold-pulse" aria-hidden="true"></span>' : "") +
      '</span></summary><div class="fold-body"><div class="fold-body-inner">' +
      bodyHtml +
      "</div></div></details>"
    );
  }

  function toolSnapshotId(part) {
    try {
      if (part && part.output) {
        var parsed = JSON.parse(part.output);
        if (parsed && parsed.snapshotId) return String(parsed.snapshotId);
      }
    } catch (e) {
      /* ignore */
    }
    return "";
  }

  function outIsExecuting(part) {
    return part && String(part.output || "") === EXECUTING_PLACEHOLDER;
  }

  function toolRunning(part, streaming) {
    if (!streaming || !part) return false;
    var out = part.output == null ? "" : String(part.output);
    return !out || out === EXECUTING_PLACEHOLDER;
  }

  function formatToolStep(part, streaming) {
    var name = part.toolName || "tool";
    var running = toolRunning(part, streaming);
    var statusLabel = running
      ? outIsExecuting(part)
        ? "Executing…"
        : "Running…"
      : part.approvalState
        ? String(part.approvalState)
        : "Done";
    var snapshotId = toolSnapshotId(part);
    var body =
      '<div class="tool-step' +
      (running ? " running" : "") +
      '"><div class="tool-step-head">' +
      '<span class="tool-step-name">' +
      escapeHtml(name) +
      '</span><span class="tool-step-status">' +
      escapeHtml(statusLabel) +
      "</span></div>";
    if (part.input) {
      body += "<pre class=\"tool-io\">" + escapeHtml(String(part.input).slice(0, 4000)) + "</pre>";
    }
    if (part.output && String(part.output) !== EXECUTING_PLACEHOLDER) {
      body += "<pre class=\"tool-io\">" + escapeHtml(String(part.output).slice(0, 4000)) + "</pre>";
    }
    if (snapshotId) {
      body +=
        '<button type="button" class="chip-btn tool-undo" data-snapshot="' +
        escapeHtml(snapshotId) +
        '">Undo</button>';
    }
    body += "</div>";
    return body;
  }

  function thinkingTitle(steps) {
    var hasReason = false;
    var hasTool = false;
    for (var i = 0; i < steps.length; i++) {
      if (steps[i].kind === "reasoning") hasReason = true;
      if (steps[i].kind === "tool") hasTool = true;
    }
    if (hasTool && hasReason) return "Thought & Tools";
    if (hasTool) return "Tools";
    return "Thinking";
  }

  function thinkingLiveStatus(steps, streaming) {
    if (!streaming) {
      var tools = 0;
      for (var i = 0; i < steps.length; i++) if (steps[i].kind === "tool") tools++;
      return tools > 0 ? tools + " tool" + (tools > 1 ? "s" : "") : "";
    }
    for (var j = steps.length - 1; j >= 0; j--) {
      var p = steps[j];
      if (p.kind === "tool" && toolRunning(p, true)) {
        return (p.toolName || "tool") + (outIsExecuting(p) ? " · executing" : " · running");
      }
    }
    for (var k = steps.length - 1; k >= 0; k--) {
      if (steps[k].kind === "reasoning") return "Thinking…";
    }
    return "Working…";
  }

  function renderThinkingGroup(msg, steps, groupIndex) {
    var key = String(msg.id || "stream") + ":think:" + groupIndex;
    var body = "";
    for (var i = 0; i < steps.length; i++) {
      var part = steps[i];
      if (part.kind === "reasoning" && part.text) {
        body += '<div class="reasoning">' + escapeHtml(part.text) + "</div>";
      } else if (part.kind === "tool") {
        body += formatToolStep(part, !!msg.streaming);
      }
    }
    if (!body) body = '<div class="muted">…</div>';
    return collapsibleBlock(thinkingTitle(steps), body, false, {
      key: key,
      process: true,
      streaming: !!msg.streaming,
      status: thinkingLiveStatus(steps, !!msg.streaming),
    });
  }

  function formatBody(msg) {
    var html = "";
    var parts = msg.parts || [];
    var hasReasoningPart = false;
    for (var r = 0; r < parts.length; r++) {
      if (parts[r].kind === "reasoning") {
        hasReasoningPart = true;
        break;
      }
    }
    // Legacy top-level reasoning (when not already in parts).
    if (msg.reasoning && !hasReasoningPart) {
      html += renderThinkingGroup(
        msg,
        [{ kind: "reasoning", text: msg.reasoning }],
        "legacy",
      );
    }

    var pending = [];
    var groupIndex = 0;
    var hasTextPart = false;

    function flushThinking() {
      if (!pending.length) return;
      html += renderThinkingGroup(msg, pending, groupIndex++);
      pending = [];
    }

    // Chat mode: keep thinking and tools in separate folds (Compose-like).
    // Writing mode: may merge consecutive think+tool into one process card.
    var separateThinkTool = (state.conversationType || "chat") !== "writing";

    for (var i = 0; i < parts.length; i++) {
      var part = parts[i];
      if (part.kind === "reasoning" || part.kind === "tool") {
        if (
          separateThinkTool &&
          pending.length &&
          pending[pending.length - 1].kind !== part.kind
        ) {
          flushThinking();
        }
        pending.push(part);
        continue;
      }
      flushThinking();
      if (part.kind === "text" && part.text) {
        hasTextPart = true;
        console.log("formatBody: text part len=" + part.text.length + " hasSvg=" + /<svg/i.test(part.text));
        var extracted = extractSvgs(part.text);
        if (extracted.svgs.length > 0) {
          console.log("formatBody: rendering " + extracted.svgs.length + " SVGs from text");
          extracted.parts.forEach(function (p) {
            if (p.kind === "svg") {
              html += renderSvgCard(extracted.svgs[p.index]);
            } else if (p.text) {
              html += '<div class="text">' + renderText(p.text) + "</div>";
            }
          });
        } else {
          html += '<div class="text">' + renderText(part.text) + "</div>";
        }
      } else if (part.kind === "attachment") {
        html +=
          '<div class="tool-chip">📎 ' +
          escapeHtml(part.displayName || "attachment") +
          "</div>";
      } else if (part.kind === "book_result") {
        html +=
          '<div class="tool-chip">📘 ' +
          escapeHtml(part.name || "") +
          (part.author ? " · " + escapeHtml(part.author) : "") +
          "</div>";
      } else if (part.kind === "image" && part.localPath) {
        console.log("formatBody: image part svgText=" + !!part.svgText + " localPath=" + (part.localPath || "").substring(0, 60));
        if (part.svgText) {
          html += renderSvgCard(part.svgText, part.localPath);
        } else {
          html += '<div class="msg-image"><img src="file://' +
            escapeHtml(part.localPath) +
            '" alt="image" loading="lazy" /></div>';
        }
      }
    }
    flushThinking();

    if (!hasTextPart && msg.content) {
      html += '<div class="text">' + renderText(msg.content) + "</div>";
    }
    if (!html) {
      html = '<div class="text muted">' + (msg.streaming ? "…" : "") + "</div>";
    }
    return html;
  }

  function bindFoldToggles(root) {
    if (!root) return;
    root.querySelectorAll("details.fold[data-fold-key]").forEach(function (el) {
      el.addEventListener("toggle", function () {
        var key = el.getAttribute("data-fold-key");
        if (key) foldOpenByKey[key] = el.open;
      });
    });
  }

  function currentConversation() {
    var id = state.currentConversationId;
    if (!id) return null;
    for (var i = 0; i < state.conversations.length; i++) {
      if (state.conversations[i].id === id) return state.conversations[i];
    }
    return null;
  }

  function renderConversations() {
    var list = els.conversationList;
    if (!list) return;
    list.innerHTML = "";
    state.conversations.forEach(function (c) {
      var row = document.createElement("div");
      row.className = "conversation-row" + (c.id === state.currentConversationId ? " active" : "");

      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "conversation-item";
      btn.innerHTML =
        '<div class="title">' +
        escapeHtml(c.title || "Untitled") +
        '</div><div class="meta">' +
        escapeHtml((c.type || "chat") + (c.modelName ? " · " + c.modelName : "")) +
        "</div>";
      btn.addEventListener("click", function () {
        postIntent({ type: "SelectConversation", id: c.id });
        closeSidebar();
      });
      btn.addEventListener("contextmenu", function (e) {
        e.preventDefault();
        renameConversation(c);
      });

      var actions = document.createElement("div");
      actions.className = "conversation-actions";

      var renameBtn = document.createElement("button");
      renameBtn.type = "button";
      renameBtn.className = "icon-btn tiny";
      renameBtn.title = "Rename";
      renameBtn.textContent = "✎";
      renameBtn.addEventListener("click", function (e) {
        e.stopPropagation();
        renameConversation(c);
      });

      var delBtn = document.createElement("button");
      delBtn.type = "button";
      delBtn.className = "icon-btn tiny danger-ghost";
      delBtn.title = "Delete";
      delBtn.textContent = "🗑";
      delBtn.addEventListener("click", function (e) {
        e.stopPropagation();
        openSheet("delete_conversation", c.id);
      });

      actions.appendChild(renameBtn);
      actions.appendChild(delBtn);
      row.appendChild(btn);
      row.appendChild(actions);
      list.appendChild(row);
    });
  }

  function renameConversation(c) {
    var title = window.prompt("Rename conversation", c.title || "");
    if (title != null && title.trim()) {
      postIntent({ type: "RenameConversation", id: c.id, title: title.trim() });
    }
  }

  function renderTopbar() {
    if (!els.currentTitle || !els.currentMeta) return;
    var c = currentConversation();
    els.currentTitle.textContent = c ? c.title || "AI Chat" : "AI Chat";
    els.currentMeta.textContent = c
      ? [c.type, c.providerName, c.modelName].filter(Boolean).join(" · ")
      : state.conversationType || "";
  }

  function renderMessages() {
    var list = els.messageList;
    if (!list) return;
    var nearBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 120;

    var messages = (state.messages || []).slice();
    var streamMsg = state.streamingMessage;
    var regenId = state.regeneratingMessageId;
    if (regenId) {
      messages = messages.filter(function (m) {
        return m.id !== regenId;
      });
    }
    var hasStreaming = false;
    if (streamMsg) {
      var last = messages.length ? messages[messages.length - 1] : null;
      // `arraysEqual` does shallow !== which never matches deserialized objects.
      // Use JSON.stringify for reliable deep comparison of parts arrays.
      var lastPartsKey = last && last.parts ? JSON.stringify(last.parts) : "";
      var streamPartsKey = streamMsg.parts && streamMsg.parts.length ? JSON.stringify(streamMsg.parts) : "";
      var alreadySaved =
        last &&
        last.role === "assistant" &&
        (last.content === streamMsg.content ||
          (streamPartsKey && lastPartsKey === streamPartsKey));
      // When generation has ended, the saved message is already in `messages`.
      // Skip the streaming temp so thinking cards enter final state (no pulse, tool counts).
      var generationEnded = !state.isSending && !state.regeneratingMessageId;
      if (!alreadySaved && !generationEnded) {
        var streamId = streamMsg.id;
        var dupById =
          streamId &&
          messages.some(function (m) {
            return m.id === streamId;
          });
        if (!dupById) {
          messages.push(Object.assign({}, streamMsg, { streaming: true }));
          hasStreaming = true;
        }
      }
    }

    // Build id fingerprint — if unchanged during streaming, update bubble in-place.
    var currentIds = messages.map(function (m) { return m.id; }).join(",");
    if (hasStreaming && currentIds === lastRenderedMessageIds && lastRenderedMessageIds) {
      var existingStreamEl = list.querySelector('.msg.streaming[data-msg-id="' + (streamMsg.id || "streaming_temp") + '"]');
      if (existingStreamEl) {
        scheduleStreamingBubble(streamMsg);
        lastRenderedNearBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 120;
        return;
      }
    }
    lastRenderedMessageIds = currentIds;

    // Full rebuild
    // Persist fold state before clearing; restore via foldOpenByKey after.
    list.querySelectorAll("details.fold[data-fold-key]").forEach(function (el) {
      foldOpenByKey[el.getAttribute("data-fold-key")] = el.open;
    });
    list.innerHTML = "";

    if (state.hasMoreHistory) {
      var more = document.createElement("button");
      more.type = "button";
      more.className = "ghost-btn load-more";
      more.textContent = state.isLoadingHistory ? "Loading…" : "Load earlier messages";
      more.disabled = !!state.isLoadingHistory;
      more.addEventListener("click", function () {
        postIntent({ type: "LoadMoreMessages" });
      });
      list.appendChild(more);
    }

    messages.forEach(function (msg) {
      var article = document.createElement("article");
      article.className =
        "msg " +
        (msg.role || "assistant") +
        (msg.streaming ? " streaming" : "");
      if (msg.id) article.setAttribute("data-msg-id", msg.id);
      // Do NOT set data-slot="message_item" on each bubble — applyFragments would
      // rewrite the first match and break the list.

      var role = document.createElement("div");
      role.className = "msg-role";
      role.textContent = msg.speakerName || msg.role || "assistant";
      if (msg.totalBranches > 1) {
        role.textContent +=
          " · " + ((msg.branchIndex || 0) + 1) + "/" + msg.totalBranches;
      }
      if (msg.excludeFromContext) {
        role.textContent += " · excluded";
      }
      if (msg.isPlanMessage) {
        role.textContent = "📋 计划" + (role.textContent ? " · " + role.textContent : "");
      }
      article.appendChild(role);

      var body = document.createElement("div");
      body.className = "msg-body";
      if (msg.isPlanMessage && msg.planFileId) {
        // 计划消息：链接卡，点击重开（pending 审批 / 结束只读详情）。
        var planCard = document.createElement("div");
        planCard.className = "plan-message-link";
        var planTitle = document.createElement("span");
        planTitle.textContent = "📋 计划文件";
        var planName = document.createElement("code");
        planName.textContent = msg.planFileId + ".md";
        var openBtn = document.createElement("button");
        openBtn.type = "button";
        openBtn.className = "ghost-btn";
        openBtn.textContent = "打开";
        openBtn.addEventListener("click", function () {
          postIntent({ type: "OpenPlan", planId: msg.planFileId });
        });
        planCard.appendChild(planTitle);
        planCard.appendChild(planName);
        planCard.appendChild(openBtn);
        body.appendChild(planCard);
      } else {
        body.innerHTML = formatBody(msg);
      }
      bindFoldToggles(body);
      // Restore fold open state on newly created elements (safety net).
      body.querySelectorAll("details.fold[data-fold-key]").forEach(function (el) {
        var key = el.getAttribute("data-fold-key");
        if (key && foldOpenByKey[key]) {
          el.open = true;
        }
      });
      body.querySelectorAll(".tool-undo").forEach(function (btn) {
        btn.addEventListener("click", function () {
          var sid = btn.getAttribute("data-snapshot");
          if (sid) postIntent({ type: "RestoreSnapshot", snapshotId: sid });
        });
      });
      body.querySelectorAll(".svg-copy-btn").forEach(function (btn) {
        btn.addEventListener("click", function () {
          var code = btn.getAttribute("data-svg-code");
          if (code) copyText(code);
        });
      });
      body.querySelectorAll(".svg-save-btn").forEach(function (btn) {
        btn.addEventListener("click", function () {
          var path = btn.getAttribute("data-svg-path");
          if (path && window.LegadoBridge && window.LegadoBridge.saveImage) {
            window.LegadoBridge.saveImage(path);
          }
        });
      });
      article.appendChild(body);

      if (!msg.streaming) {
        var actions = document.createElement("div");
        actions.className = "msg-actions";
        if (msg.totalBranches > 1) {
          var prev = document.createElement("button");
          prev.type = "button";
          prev.className = "chip-btn";
          prev.textContent = "‹";
          prev.addEventListener("click", function () {
            postIntent({ type: "SwitchBranch", messageId: msg.id, direction: -1 });
          });
          var next = document.createElement("button");
          next.type = "button";
          next.className = "chip-btn";
          next.textContent = "›";
          next.addEventListener("click", function () {
            postIntent({ type: "SwitchBranch", messageId: msg.id, direction: 1 });
          });
          actions.appendChild(prev);
          actions.appendChild(next);
        }
        if (msg.role === "assistant") {
          var regen = document.createElement("button");
          regen.type = "button";
          regen.className = "chip-btn";
          regen.textContent = "Regen";
          regen.addEventListener("click", function () {
            postIntent({ type: "RegenerateMessage", messageId: msg.id });
          });
          actions.appendChild(regen);
        }
        if (msg.role === "user") {
          var regenUser = document.createElement("button");
          regenUser.type = "button";
          regenUser.className = "chip-btn";
          regenUser.textContent = "Resend";
          regenUser.addEventListener("click", function () {
            postIntent({
              type: "RegenerateFromUserMessage",
              userMessageId: msg.id,
              messageId: msg.id,
            });
          });
          actions.appendChild(regenUser);
        }
        var copy = document.createElement("button");
        copy.type = "button";
        copy.className = "chip-btn";
        copy.textContent = "Copy";
        copy.addEventListener("click", function () {
          copyText(msg.content || "");
        });
        actions.appendChild(copy);
        var fork = document.createElement("button");
        fork.type = "button";
        fork.className = "chip-btn";
        fork.textContent = "Fork";
        fork.addEventListener("click", function () {
          postIntent({ type: "ForkConversation", messageId: msg.id });
        });
        actions.appendChild(fork);
        var edit = document.createElement("button");
        edit.type = "button";
        edit.className = "chip-btn";
        edit.textContent = "Edit";
        edit.addEventListener("click", function () {
          openEditDialog(msg);
        });
        actions.appendChild(edit);
        var del = document.createElement("button");
        del.type = "button";
        del.className = "chip-btn";
        del.textContent = "Delete";
        del.addEventListener("click", function () {
          if (window.confirm("Delete message?")) {
            postIntent({ type: "DeleteMessage", messageId: msg.id });
          }
        });
        actions.appendChild(del);
        article.appendChild(actions);
      }
      list.appendChild(article);
    });

    if (nearBottom) {
      list.scrollTop = list.scrollHeight;
    }
  }

  function renderTools() {
    var panel = els.toolPanel;
    if (!panel) return;
    var tools = state.pendingToolConfs || [];
    if (!tools.length) {
      panel.classList.add("hidden");
      panel.innerHTML = "";
      return;
    }
    panel.classList.remove("hidden");
    panel.innerHTML = "";

    var batch = document.createElement("textarea");
    batch.rows = 2;
    batch.placeholder = "Batch feedback…";
    batch.value = state.pendingToolBatchFeedback || "";
    batch.addEventListener("change", function () {
      postIntent({ type: "UpdateToolBatchFeedback", feedback: batch.value });
    });
    panel.appendChild(batch);

    tools.forEach(function (tool) {
      var card = document.createElement("div");
      card.className = "tool-card";
      card.innerHTML =
        "<h4>" +
        escapeHtml(tool.displayName || tool.toolName || "tool") +
        "</h4><p>" +
        escapeHtml(tool.summary || "") +
        "</p>";

      var check = document.createElement("label");
      var cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = !!tool.checked;
      cb.addEventListener("change", function () {
        postIntent({ type: "ToggleToolApproval", callId: tool.callId });
      });
      check.appendChild(cb);
      check.appendChild(document.createTextNode(" Approve"));
      card.appendChild(check);

      (tool.subItems || []).forEach(function (item) {
        var sub = document.createElement("label");
        var scb = document.createElement("input");
        scb.type = "checkbox";
        scb.checked = !!item.checked;
        scb.addEventListener("change", function () {
          postIntent({
            type: "ToggleToolSubItemApproval",
            callId: tool.callId,
            subItemId: item.id,
          });
        });
        sub.appendChild(scb);
        sub.appendChild(document.createTextNode(" " + (item.opLabel || item.id)));
        card.appendChild(sub);
      });

      var fb = document.createElement("textarea");
      fb.rows = 2;
      fb.placeholder = "Feedback…";
      fb.value = tool.feedback || "";
      fb.addEventListener("change", function () {
        postIntent({
          type: "UpdateToolFeedback",
          callId: tool.callId,
          feedback: fb.value,
        });
      });
      card.appendChild(fb);
      panel.appendChild(card);
    });

    var actions = document.createElement("div");
    actions.className = "tool-actions";
    var reject = document.createElement("button");
    reject.type = "button";
    reject.className = "ghost-btn";
    reject.textContent = "Reject";
    reject.addEventListener("click", function () {
      postIntent({ type: "RejectPendingTools" });
    });
    var confirm = document.createElement("button");
    confirm.type = "button";
    confirm.className = "primary-btn";
    confirm.textContent = "Confirm";
    confirm.addEventListener("click", function () {
      postIntent({ type: "ConfirmPendingTools" });
    });
    actions.appendChild(reject);
    actions.appendChild(confirm);
    panel.appendChild(actions);
  }

  function renderComposer() {
    if (!els.btnSend || !els.btnStop) return;
    els.btnSend.classList.toggle("hidden", !!state.isSending);
    els.btnStop.classList.toggle("hidden", !state.isSending);
    els.btnSend.disabled = !!state.isSending || !!state.isProcessingAttachments;
    if (els.btnComposerMore) {
      var showMore = state.conversationType === "writing";
      els.btnComposerMore.classList.toggle("hidden", !showMore);
      if (!showMore) { composerMoreOpen = false; renderComposerMoreMenu(); }
    }
    if (els.btnContinue) {
      var showContinue = !state.isSending && state.conversationType === "writing";
      els.btnContinue.classList.toggle("hidden", !showContinue);
    }
    if (els.btnAiHelp) {
      els.btnAiHelp.classList.toggle(
        "hidden",
        state.conversationType !== "writing" || !!state.isSending,
      );
    }
    if (els.btnAttach) {
      els.btnAttach.classList.toggle("hidden", state.conversationType === "writing");
      els.btnAttach.disabled = !!state.isSending || !!state.isProcessingAttachments;
    }
    if (els.btnAtMention) {
      var showAt = (state.selectedCharacterNames || []).length > 1 && state.conversationType === "writing";
      els.btnAtMention.classList.toggle("hidden", !showAt);
    }
    renderComposerToolbar();
    renderComposerMoreMenu();
    renderThinkingMenu();
    renderSuggestions();
    renderAttachments();
    renderGalgameHud();
    renderInteractivePanels();
    updateCompletionMenus();
  }

  function renderComposerMoreMenu() {
    var menu = els.composerMoreMenu || $("composer-more-menu");
    var toggle = els.btnComposerMore || $("btn-composer-more");
    if (!menu || !toggle) return;
    els.composerMoreMenu = menu;
    els.btnComposerMore = toggle;
    toggle.classList.toggle("open", !!composerMoreOpen);
    toggle.setAttribute("aria-expanded", composerMoreOpen ? "true" : "false");
    menu.classList.toggle("hidden", !composerMoreOpen);
  }

  function renderThinkingMenu() {
    var menu = els.thinkingMenu || $("thinking-menu");
    var btn = els.btnThinking || $("btn-thinking");
    if (!menu || !btn) return;
    els.thinkingMenu = menu;
    els.btnThinking = btn;

    var currentLevel = String(state.reasoningLevel).toUpperCase();
    var levelLabels = { OFF: "关", AUTO: "自动", LOW: "Low", MEDIUM: "Med", HIGH: "High", MAX: "MAX" };
    btn.textContent = "思考";
    btn.classList.toggle("open", !!thinkingMenuOpen);
    btn.setAttribute("aria-expanded", thinkingMenuOpen ? "true" : "false");

    if (!thinkingMenuOpen) {
      menu.classList.add("hidden");
      return;
    }
    menu.classList.remove("hidden");
    menu.innerHTML = "";
    ["OFF", "AUTO", "LOW", "MEDIUM", "HIGH", "MAX"].forEach(function (level) {
      var item = document.createElement("button");
      item.type = "button";
      item.className = "ghost-btn menu-item";
      if (currentLevel === level) item.classList.add("active");
      item.textContent = levelLabels[level] || level;
      item.addEventListener("click", function () {
        postIntent({ type: "UpdateReasoningLevel", level: level });
        thinkingMenuOpen = false;
        renderThinkingMenu();
        renderComposerToolbar();
      });
      menu.appendChild(item);
    });
  }

  /** 计划选区反馈：读取浏览器选中文本，在面板内弹出反馈输入区并提交修订。 */
  function openPlanFeedback(panel, plan, capturedSelection) {
    // 按钮点击可能清掉选区，优先用调用方预捕获的文本。
    var sel = (typeof capturedSelection === "string" && capturedSelection)
      ? capturedSelection
      : (window.getSelection ? window.getSelection().toString().trim() : "");
    var existing = panel.querySelector(".plan-feedback-box");
    if (existing) {
      existing.remove();
    }
    var box = document.createElement("div");
    box.className = "plan-feedback-box";
    var hint = document.createElement("div");
    hint.className = "muted";
    hint.textContent = sel
      ? "选中内容：" + sel.slice(0, 120) + (sel.length > 120 ? "…" : "")
      : "未选中文字，意见将作用于整篇计划。";
    box.appendChild(hint);
    var ta = document.createElement("textarea");
    ta.className = "plan-edit";
    ta.placeholder = "填写修改意见…";
    box.appendChild(ta);
    var row = document.createElement("div");
    row.className = "tool-actions";
    var cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "ghost-btn";
    cancel.textContent = "取消";
    cancel.addEventListener("click", function () { box.remove(); });
    var submit = document.createElement("button");
    submit.type = "button";
    submit.className = "primary-btn";
    submit.textContent = "暂存反馈";
    submit.addEventListener("click", function () {
      var fb = ta.value.trim();
      if (!fb) { ta.focus(); return; }
      postIntent({ type: "AddPlanFeedback", selectedText: sel, feedback: fb });
      box.remove();
    });
    row.appendChild(cancel);
    row.appendChild(submit);
    box.appendChild(row);
    panel.appendChild(box);
    ta.focus();
  }

  function renderPlanFullscreen() {
    var host = els.planFullscreenHost;
    if (!host) {
      host = document.createElement("div");
      host.className = "plan-fullscreen-host hidden";
      document.body.appendChild(host);
      els.planFullscreenHost = host;
    }
    var plan = state.pendingPlan;
    if (!planFullscreenOpen || !plan) {
      host.classList.add("hidden");
      host.innerHTML = "";
      return;
    }
    host.classList.remove("hidden");
    host.innerHTML = "";
    var panel = document.createElement("div");
    panel.className = "plan-fullscreen";

    var head = document.createElement("div");
    head.className = "plan-fullscreen-head";
    var title = document.createElement("h4");
    title.textContent = "计划审批（全屏）";
    var close = document.createElement("button");
    close.type = "button";
    close.className = "icon-btn";
    close.textContent = "✕";
    close.addEventListener("click", function () {
      planFullscreenOpen = false;
      renderPlanFullscreen();
    });
    head.appendChild(title);
    head.appendChild(close);
    panel.appendChild(head);

    var body = document.createElement("div");
    body.className = "plan-fullscreen-body";
    if (plan.isEditing) {
      var ta = document.createElement("textarea");
      ta.value = plan.editedPlanContent || plan.planContent || "";
      ta.className = "plan-edit plan-fullscreen-edit";
      ta.addEventListener("input", function () {
        postIntent({ type: "EditPlan", content: ta.value });
      });
      body.appendChild(ta);
    } else {
      var view = document.createElement("div");
      view.className = "plan-fullscreen-view";
      view.innerHTML = renderText(plan.planContent || "");
      body.appendChild(view);
    }
    panel.appendChild(body);

    // 已暂存反馈（全屏）
    if ((plan.stagedFeedback || []).length > 0) {
      var fbl = document.createElement("div");
      fbl.className = "plan-feedback-staged";
      var fbt = document.createElement("div");
      fbt.className = "muted";
      fbt.textContent = "已暂存 " + plan.stagedFeedback.length + " 条反馈";
      fbl.appendChild(fbt);
      plan.stagedFeedback.forEach(function (f) {
        var it = document.createElement("div");
        it.className = "plan-feedback-item";
        if (f.startLine != null) {
          var ln = document.createElement("span");
          ln.className = "plan-feedback-line";
          ln.textContent = "第 " + f.startLine + ((f.endLine != null && f.endLine != f.startLine) ? "-" + f.endLine : "") + " 行 · ";
          it.appendChild(ln);
        }
        var t = document.createElement("span");
        t.className = "muted";
        t.textContent = (f.selectedText ? f.selectedText.slice(0, 60) + " — " : "") + f.feedback;
        var d = document.createElement("button");
        d.type = "button";
        d.className = "icon-btn";
        d.textContent = "✕";
        d.addEventListener("click", function () {
          postIntent({ type: "RemovePlanFeedback", feedbackId: f.id });
        });
        it.appendChild(t);
        it.appendChild(d);
        fbl.appendChild(it);
      });
      panel.appendChild(fbl);
    }

    var actions = document.createElement("div");
    actions.className = "tool-actions";
    var reject = document.createElement("button");
    reject.type = "button";
    reject.className = "ghost-btn";
    reject.textContent = "拒绝";
    reject.addEventListener("click", function () {
      postIntent({ type: "RejectPlan" });
    });
    var editBtn = document.createElement("button");
    editBtn.type = "button";
    editBtn.className = "ghost-btn";
    editBtn.textContent = plan.isEditing ? "取消编辑" : "编辑";
    editBtn.addEventListener("click", function () {
      if (plan.isEditing) {
        postIntent({ type: "CancelPlanEdit" });
      } else {
        postIntent({ type: "EditPlan", content: plan.editedPlanContent || plan.planContent || "" });
      }
    });
    var accept = document.createElement("button");
    accept.type = "button";
    accept.className = "primary-btn";
    accept.textContent = "批准";
    accept.addEventListener("click", function () {
      postIntent({ type: "AcceptPlan" });
    });
    actions.appendChild(reject);
    actions.appendChild(editBtn);
    if ((plan.stagedFeedback || []).length > 0) {
      var submitAll = document.createElement("button");
      submitAll.type = "button";
      submitAll.className = "primary-btn";
      submitAll.textContent = "提交 " + plan.stagedFeedback.length + " 条反馈";
      submitAll.addEventListener("click", function () {
        postIntent({ type: "SubmitAllPlanFeedback" });
      });
      actions.appendChild(submitAll);
    }
    actions.appendChild(accept);
    panel.appendChild(actions);

    host.appendChild(panel);
  }

  /** 已结束计划只读详情：全屏 markdown 查看 + 关闭。 */
  function renderPlanDetail() {
    var detail = state.planDetailView;
    var host = els.planDetailHost;
    if (!detail) {
      if (host) host.classList.add("hidden");
      return;
    }
    if (!host) {
      host = document.createElement("div");
      host.className = "plan-detail-host hidden";
      document.body.appendChild(host);
      els.planDetailHost = host;
    }
    host.classList.remove("hidden");
    host.innerHTML = "";
    var panel = document.createElement("div");
    panel.className = "plan-detail";
    var head = document.createElement("div");
    head.className = "plan-detail-head";
    var title = document.createElement("h4");
    title.textContent = "计划 · " + detail.planId + ".md";
    var close = document.createElement("button");
    close.type = "button";
    close.className = "icon-btn";
    close.textContent = "✕";
    close.addEventListener("click", function () {
      postIntent({ type: "DismissPlanDetail" });
    });
    head.appendChild(title);
    head.appendChild(close);
    panel.appendChild(head);
    var meta = document.createElement("div");
    meta.className = "muted";
    meta.textContent = "状态：" + (detail.status || "") + " · 修订 #" + (detail.revision || 0);
    panel.appendChild(meta);
    var view = document.createElement("div");
    view.className = "plan-detail-body";
    view.innerHTML = renderText(detail.content || "");
    panel.appendChild(view);
    host.appendChild(panel);
  }

  function renderInteractivePanels() {
    renderPlanFullscreen();
    var host = els.interactivePanels;
    if (!host) return;
    host.innerHTML = "";

    if (state.outlineBranchChoice && state.outlineBranchChoice.options) {
      var branch = document.createElement("section");
      branch.className = "panel-card";
      branch.innerHTML =
        "<h4>" + escapeHtml(state.outlineBranchChoice.title || "Choose branch") + "</h4>";
      (state.outlineBranchChoice.options || []).forEach(function (opt) {
        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "ghost-btn block";
        btn.textContent = opt.label || opt.id;
        btn.addEventListener("click", function () {
          postIntent({ type: "SelectOutlineBranch", optionId: opt.id });
        });
        branch.appendChild(btn);
      });
      host.appendChild(branch);
    }

    if (state.pendingUserQuestions && state.pendingUserQuestions.questions) {
      var pq = state.pendingUserQuestions;
      var qPanel = document.createElement("section");
      qPanel.className = "panel-card";
      qPanel.innerHTML = "<h4>Questions</h4>";
      (pq.questions || []).forEach(function (q) {
        var block = document.createElement("div");
        block.className = "question-block";
        block.innerHTML = "<div class=\"q-prompt\">" + escapeHtml(q.prompt || "") + "</div>";
        (q.options || []).forEach(function (opt) {
          var label = document.createElement("label");
          label.className = "toggle-row";
          var input = document.createElement("input");
          input.type = q.allowMultiple ? "checkbox" : "radio";
          input.name = "q-" + q.id;
          input.checked = (q.selectedIds || []).indexOf(opt.id) >= 0;
          input.addEventListener("change", function () {
            postIntent({
              type: "ToggleQuestionOption",
              callId: pq.callId,
              questionId: q.id,
              optionId: opt.id,
            });
          });
          label.appendChild(document.createTextNode(opt.label || opt.id));
          label.appendChild(input);
          block.appendChild(label);
        });
        var custom = document.createElement("textarea");
        custom.rows = 2;
        custom.placeholder = "Custom answer…";
        custom.value = q.customText || "";
        custom.addEventListener("change", function () {
          postIntent({
            type: "UpdateQuestionCustomText",
            callId: pq.callId,
            questionId: q.id,
            text: custom.value,
          });
        });
        block.appendChild(custom);
        qPanel.appendChild(block);
      });
      var qActions = document.createElement("div");
      qActions.className = "tool-actions";
      var skip = document.createElement("button");
      skip.type = "button";
      skip.className = "ghost-btn";
      skip.textContent = "Skip";
      skip.addEventListener("click", function () {
        postIntent({ type: "DismissUserQuestions" });
      });
      var submit = document.createElement("button");
      submit.type = "button";
      submit.className = "primary-btn";
      submit.textContent = "Submit";
      submit.addEventListener("click", function () {
        postIntent({ type: "SubmitUserQuestions", callId: pq.callId });
      });
      qActions.appendChild(skip);
      qActions.appendChild(submit);
      qPanel.appendChild(qActions);
      host.appendChild(qPanel);
    }

    if (state.pendingHabitMemoryConfirm) {
      var habit = state.pendingHabitMemoryConfirm;
      var hPanel = document.createElement("section");
      hPanel.className = "panel-card";
      hPanel.innerHTML = "<h4>Remember habit?</h4>";
      (habit.lines || []).forEach(function (line) {
        var row = document.createElement("div");
        row.className = "muted";
        row.textContent =
          (line.op || "") + " " + (line.key || "") + " = " + (line.value || "");
        hPanel.appendChild(row);
      });
      var hActions = document.createElement("div");
      hActions.className = "tool-actions";
      var remember = document.createElement("button");
      remember.type = "button";
      remember.className = "primary-btn";
      remember.textContent = "Remember";
      remember.addEventListener("click", function () {
        postIntent({ type: "ConfirmHabitMemory" });
      });
      var hSkip = document.createElement("button");
      hSkip.type = "button";
      hSkip.className = "ghost-btn";
      hSkip.textContent = "Skip";
      hSkip.addEventListener("click", function () {
        postIntent({ type: "RejectHabitMemory" });
      });
      hActions.appendChild(hSkip);
      hActions.appendChild(remember);
      hPanel.appendChild(hActions);
      host.appendChild(hPanel);
    }

    if (state.pendingPlan) {
      var plan = state.pendingPlan;
      var pPanel = document.createElement("section");
      pPanel.className = "panel-card";
      var pHead = document.createElement("div");
      pHead.className = "plan-header";
      var pTitle = document.createElement("h4");
      pTitle.textContent = "计划审批";
      var fsBtn = document.createElement("button");
      fsBtn.type = "button";
      fsBtn.className = "ghost-btn";
      fsBtn.textContent = "全屏";
      fsBtn.addEventListener("click", function () {
        planFullscreenOpen = true;
        renderPlanFullscreen();
      });
      pHead.appendChild(pTitle);
      pHead.appendChild(fsBtn);
      pPanel.appendChild(pHead);
      var pMuted = document.createElement("div");
      pMuted.className = "muted";
      pMuted.textContent = "批准后将以自动模式生成最终结果；可先编辑计划再批准。";
      pPanel.appendChild(pMuted);
      var pBody = document.createElement("div");
      pBody.className = "plan-body";
      if (plan.isEditing) {
        var planText = document.createElement("textarea");
        planText.value = plan.editedPlanContent || plan.planContent || "";
        planText.className = "plan-edit";
        planText.addEventListener("input", function () {
          postIntent({ type: "EditPlan", content: planText.value });
        });
        pBody.appendChild(planText);
      } else {
        pBody.innerHTML = renderText(plan.planContent || "");
      }
      pPanel.appendChild(pBody);
      // 已暂存的反馈列表
      if ((plan.stagedFeedback || []).length > 0) {
        var fbList = document.createElement("div");
        fbList.className = "plan-feedback-staged";
        var fbTitle = document.createElement("div");
        fbTitle.className = "muted";
        fbTitle.textContent = "已暂存 " + plan.stagedFeedback.length + " 条反馈";
        fbList.appendChild(fbTitle);
        plan.stagedFeedback.forEach(function (f) {
          var item = document.createElement("div");
          item.className = "plan-feedback-item";
          if (f.startLine != null) {
            var ln = document.createElement("span");
            ln.className = "plan-feedback-line";
            ln.textContent = "第 " + f.startLine + ((f.endLine != null && f.endLine != f.startLine) ? "-" + f.endLine : "") + " 行 · ";
            item.appendChild(ln);
          }
          var text = document.createElement("span");
          text.className = "muted";
          text.textContent = (f.selectedText ? f.selectedText.slice(0, 60) + " — " : "") + f.feedback;
          var del = document.createElement("button");
          del.type = "button";
          del.className = "icon-btn";
          del.textContent = "✕";
          del.addEventListener("click", function () {
            postIntent({ type: "RemovePlanFeedback", feedbackId: f.id });
          });
          item.appendChild(text);
          item.appendChild(del);
          fbList.appendChild(item);
        });
        pPanel.appendChild(fbList);
      }
      var pActions = document.createElement("div");
      pActions.className = "tool-actions";
      var reject = document.createElement("button");
      reject.type = "button";
      reject.className = "ghost-btn";
      reject.textContent = "拒绝";
      reject.addEventListener("click", function () {
        postIntent({ type: "RejectPlan" });
      });
      var editBtn = document.createElement("button");
      editBtn.type = "button";
      editBtn.className = "ghost-btn";
      editBtn.textContent = plan.isEditing ? "取消编辑" : "编辑";
      editBtn.addEventListener("click", function () {
        if (plan.isEditing) {
          postIntent({ type: "CancelPlanEdit" });
        } else {
          postIntent({ type: "EditPlan", content: plan.editedPlanContent || plan.planContent || "" });
        }
      });
      var accept = document.createElement("button");
      accept.type = "button";
      accept.className = "primary-btn";
      accept.textContent = "批准";
      accept.addEventListener("click", function () {
        postIntent({ type: "AcceptPlan" });
      });
      pActions.appendChild(reject);
      pActions.appendChild(editBtn);
      if ((plan.stagedFeedback || []).length > 0) {
        var submitAll = document.createElement("button");
        submitAll.type = "button";
        submitAll.className = "primary-btn";
        submitAll.textContent = "提交 " + plan.stagedFeedback.length + " 条反馈";
        submitAll.addEventListener("click", function () {
          postIntent({ type: "SubmitAllPlanFeedback" });
        });
        pActions.appendChild(submitAll);
      }
      pActions.appendChild(accept);
      pPanel.appendChild(pActions);
      host.appendChild(pPanel);
    }

    (state.pendingTtsSecretFills || []).forEach(function (fill) {
      var tPanel = document.createElement("section");
      tPanel.className = "panel-card";
      tPanel.innerHTML =
        "<h4>TTS secret · " +
        escapeHtml(fill.engineLabel || fill.provider || fill.action || "") +
        "</h4>";
      if (fill.needsApiKey) {
        var api = document.createElement("input");
        api.type = "password";
        api.placeholder = fill.apiKeyLabel || "API Key";
        api.value = fill.apiKey || "";
        api.addEventListener("change", function () {
          postIntent({
            type: "UpdateTtsSecretFill",
            callId: fill.callId,
            apiKey: api.value,
          });
        });
        tPanel.appendChild(api);
      }
      if (fill.needsSecretKey) {
        var secret = document.createElement("input");
        secret.type = "password";
        secret.placeholder = fill.secretKeyLabel || "Secret Key";
        secret.value = fill.secretKey || "";
        secret.addEventListener("change", function () {
          postIntent({
            type: "UpdateTtsSecretFill",
            callId: fill.callId,
            secretKey: secret.value,
          });
        });
        tPanel.appendChild(secret);
      }
      host.appendChild(tPanel);
    });
  }

  function toolbarToggle(label, active, onClick) {
    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "chip-btn" + (active ? " active" : "");
    btn.textContent = label;
    btn.addEventListener("click", onClick);
    return btn;
  }

  function renderComposerToolbar() {
    var bar = els.composerToolbar;
    if (!bar) return;
    bar.innerHTML = "";

    var primary = document.createElement("div");
    primary.className = "composer-tools-row";

    // Mode toggle — always visible, compact
    var modes = document.createElement("div");
    modes.className = "chip-row";
    modes.appendChild(
      toolbarToggle("对话", state.conversationType === "chat", function () {
        if (state.conversationType !== "chat") postIntent({ type: "SwitchMode", mode: "chat" });
      }),
    );
    modes.appendChild(
      toolbarToggle("写作", state.conversationType === "writing", function () {
        if (state.conversationType !== "writing") postIntent({ type: "SwitchMode", mode: "writing" });
      }),
    );
    primary.appendChild(modes);

    // Quick chips: Search + CTX, always glanceable
    var quick = document.createElement("div");
    quick.className = "chip-row composer-quick";
    quick.appendChild(
      toolbarToggle("搜索", !!state.webSearchArmed, function () {
        postIntent({ type: "ToggleWebSearch" });
      }),
    );
    var ctxLabel = "CTX";
    if (state.contextWindow > 0 || state.contextInputBudget > 0) {
      var budget = state.contextInputBudget > 0 ? state.contextInputBudget : state.contextWindow;
      var pct = budget > 0 ? Math.min(100, Math.round((state.contextTokensUsed / budget) * 100)) : 0;
      ctxLabel = "CTX " + pct + "%" + (state.isCompressing ? "…" : "");
    }
    quick.appendChild(
      toolbarToggle(ctxLabel, false, function () {
        openSheet("context");
      }),
    );
    primary.appendChild(quick);

    var toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "ghost-btn tools-toggle" + (composerToolsExpanded ? " open" : "");
    toggle.setAttribute("aria-expanded", composerToolsExpanded ? "true" : "false");
    toggle.textContent = composerToolsExpanded ? "收起 ▴" : "工具 ▾";
    toggle.addEventListener("click", function () {
      composerToolsExpanded = !composerToolsExpanded;
      renderComposerToolbar();
    });
    primary.appendChild(toggle);
    bar.appendChild(primary);

    // Vertical tools panel — rendered into a separate element between toolbar and input.
    var panelEl = els.composerToolsPanel;
    if (panelEl) {
      if (!composerToolsExpanded) {
        panelEl.classList.add("hidden");
      } else {
        panelEl.classList.remove("hidden");
        panelEl.innerHTML = "";

        // Writing mode: input mode + sub-mode toggles
        if (state.conversationType === "writing") {
          var inputModes = document.createElement("div");
          inputModes.className = "chip-row";
          inputModes.appendChild(
            toolbarToggle("对话", writingInputMode === "DIALOGUE", function () {
              if (writingInputMode !== "DIALOGUE") switchWritingMode("DIALOGUE");
            }),
          );
          inputModes.appendChild(
            toolbarToggle("动作", writingInputMode === "ACTION", function () {
              if (writingInputMode !== "ACTION") switchWritingMode("ACTION");
            }),
          );
          panelEl.appendChild(inputModes);

          var subs = document.createElement("div");
          subs.className = "chip-row";
          subs.appendChild(
            toolbarToggle("角色扮演", state.writingSubMode === "roleplay", function () {
              postIntent({ type: "SetWritingSubMode", subMode: "roleplay" });
            }),
          );
          subs.appendChild(
            toolbarToggle("作者", state.writingSubMode === "author", function () {
              postIntent({ type: "SetWritingSubMode", subMode: "author" });
            }),
          );
          panelEl.appendChild(subs);
        }

        var toggles = document.createElement("div");
        toggles.className = "chip-row";
        if (state.conversationType === "chat") {
          // AI 输出审批模式：自动 / 询问 / 计划模式
          toggles.appendChild(
            toolbarToggle("自动", state.outputMode === "AUTO", function () {
              if (state.outputMode !== "AUTO") postIntent({ type: "SetOutputMode", mode: "AUTO" });
            }),
          );
          toggles.appendChild(
            toolbarToggle("询问", state.outputMode === "ASK", function () {
              if (state.outputMode !== "ASK") postIntent({ type: "SetOutputMode", mode: "ASK" });
            }),
          );
          toggles.appendChild(
            toolbarToggle("计划模式", state.outputMode === "PLAN", function () {
              if (state.outputMode !== "PLAN") postIntent({ type: "SetOutputMode", mode: "PLAN" });
            }),
          );
        }
        toggles.appendChild(
          toolbarToggle("Gal", !!state.galgameEnabled, function () {
            postIntent({ type: "ToggleGalgame" });
          }),
        );
        toggles.appendChild(
          toolbarToggle("大纲", !!state.outlineEnabled, function () {
            postIntent({ type: "ToggleOutlineEnabled" });
          }),
        );
        toggles.appendChild(
          toolbarToggle("润色", !!state.postEditEnabled, function () {
            postIntent({ type: "TogglePostEdit" });
          }),
        );
        if ((state.selectedCharacterCount || 0) > 1) {
          toggles.appendChild(
            toolbarToggle("角色互动", !!state.interCharacterChatEnabled, function () {
              postIntent({ type: "ToggleInterCharacterChat" });
            }),
          );
        }
        panelEl.appendChild(toggles);
      }
    }
  }

  function renderSuggestions() {
    var box = els.suggestions;
    if (!box) return;
    var items = state.suggestions || [];
    if (!items.length && !state.suggestionsLoading) {
      box.classList.add("hidden");
      box.innerHTML = "";
      return;
    }
    box.classList.remove("hidden");
    box.innerHTML = "";
    if (state.suggestionsLoading) {
      var loading = document.createElement("div");
      loading.className = "muted";
      loading.textContent = "Suggestions…";
      box.appendChild(loading);
    }
    items.forEach(function (text) {
      var chip = document.createElement("button");
      chip.type = "button";
      chip.className = "chip-btn";
      chip.textContent = text;
      chip.addEventListener("click", function () {
        postIntent({ type: "SelectSuggestion", text: text });
      });
      box.appendChild(chip);
    });
    if (items.length) {
      var dismiss = document.createElement("button");
      dismiss.type = "button";
      dismiss.className = "ghost-btn";
      dismiss.textContent = "❌";
      dismiss.addEventListener("click", function () {
        postIntent({ type: "DismissSuggestions" });
      });
      box.appendChild(dismiss);
    }
  }

  function renderAttachments() {
    var box = els.attachmentChips;
    if (!box) return;
    var items = state.pendingAttachments || [];
    if (!items.length) {
      box.classList.add("hidden");
      box.innerHTML = "";
      return;
    }
    box.classList.remove("hidden");
    box.innerHTML = "";
    items.forEach(function (att) {
      var chip = document.createElement("button");
      chip.type = "button";
      chip.className = "chip-btn";
      chip.textContent = "📎 " + (att.displayName || att.id) + " ×";
      chip.addEventListener("click", function () {
        postIntent({ type: "RemovePendingAttachment", id: att.id });
      });
      box.appendChild(chip);
    });
  }

  function renderGalgameHud() {
    var wrap = els.galgameHud;
    var body = els.galgameHudBody;
    if (!wrap || !body) return;
    var html = state.galgameHudHtml || "";
    if (!state.galgameEnabled || (!html && !state.galgameHudLoading)) {
      wrap.classList.add("hidden");
      body.innerHTML = "";
      return;
    }
    wrap.classList.remove("hidden");
    if (state.galgameHudLoading && !html) {
      body.textContent = "Loading HUD…";
    } else {
      body.innerHTML = html;
    }
  }

  function switchWritingMode(toMode) {
    if (!window.LegadoBridge || typeof window.LegadoBridge.switchWritingInputMode !== "function") {
      writingInputMode = toMode;
      renderComposerToolbar();
      return;
    }
    var payload = {
      text: els.input ? els.input.value : "",
      from: writingInputMode,
      to: toMode,
      textBeforeSwitch: writingSwitchState.textBeforeSwitch || "",
      textAfterSwitch: writingSwitchState.textAfterSwitch || "",
      modeBeforeSwitch: writingSwitchState.modeBeforeSwitch || null,
      modeAfterSwitch: writingSwitchState.modeAfterSwitch || null,
    };
    try {
      var result = JSON.parse(window.LegadoBridge.switchWritingInputMode(JSON.stringify(payload)) || "{}");
      writingInputMode = result.mode || toMode;
      writingSwitchState = {
        textBeforeSwitch: result.textBeforeSwitch || "",
        textAfterSwitch: result.textAfterSwitch || "",
        modeBeforeSwitch: result.modeBeforeSwitch || null,
        modeAfterSwitch: result.modeAfterSwitch || null,
      };
      if (els.input && typeof result.text === "string") {
        els.input.value = result.text;
        autosizeInput();
        postIntent({ type: "UpdateDraftInput", text: result.text });
      }
    } catch (e) {
      writingInputMode = toMode;
    }
    renderComposerToolbar();
  }

  function updateCompletionMenus() {
    var text = els.input ? els.input.value : "";
    updateSlashMenu(text);
    updateMentionMenu(text);
  }

  function updateSlashMenu(text) {
    var menu = els.slashMenu;
    if (!menu) return;
    var match = text.match(/(?:^|\s)\/([^\s]*)$/);
    if (!match || state.conversationType !== "chat" && state.conversationType !== "writing") {
      menu.classList.add("hidden");
      menu.innerHTML = "";
      slashMenuItems = [];
      return;
    }
    var partial = match[1] || "";
    var items = [];
    if (window.LegadoBridge && typeof window.LegadoBridge.matchSlashCommands === "function") {
      try {
        items = JSON.parse(window.LegadoBridge.matchSlashCommands(partial) || "[]");
      } catch (e) {
        items = [];
      }
    }
    slashMenuItems = items || [];
    if (!slashMenuItems.length) {
      menu.classList.add("hidden");
      menu.innerHTML = "";
      return;
    }
    menu.classList.remove("hidden");
    menu.innerHTML = "";
    slashMenuItems.forEach(function (cmd) {
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "ghost-btn block";
      btn.innerHTML =
        "<strong>" +
        escapeHtml(cmd.title || cmd.primary || "") +
        "</strong>" +
        (cmd.description
          ? '<div class="muted">' + escapeHtml(cmd.description) + "</div>"
          : "");
      btn.addEventListener("click", function () {
        var slashIdx = text.lastIndexOf("/");
        if (slashIdx >= 0 && els.input) {
          els.input.value = text.substring(0, slashIdx) + (cmd.insertText || "");
          autosizeInput();
          postIntent({ type: "UpdateDraftInput", text: els.input.value });
        }
        menu.classList.add("hidden");
      });
      menu.appendChild(btn);
    });
  }

  function updateMentionMenu(text) {
    var menu = els.mentionMenu;
    if (!menu) return;
    if (els.slashMenu && !els.slashMenu.classList.contains("hidden")) {
      menu.classList.add("hidden");
      menu.innerHTML = "";
      return;
    }
    var match = text.match(/@([^\s@]*)$/);
    var names = state.selectedCharacterNames || [];
    if (!match || names.length < 2) {
      menu.classList.add("hidden");
      menu.innerHTML = "";
      return;
    }
    var q = (match[1] || "").toLowerCase();
    var hits = names.filter(function (n) {
      return !q || String(n).toLowerCase().indexOf(q) >= 0;
    });
    if (!hits.length) {
      menu.classList.add("hidden");
      menu.innerHTML = "";
      return;
    }
    menu.classList.remove("hidden");
    menu.innerHTML = "";
    hits.forEach(function (name) {
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "ghost-btn block";
      btn.textContent = "@" + name;
      btn.addEventListener("click", function () {
        var atIdx = text.lastIndexOf("@");
        if (atIdx >= 0 && els.input) {
          els.input.value = text.substring(0, atIdx) + "@" + name + " ";
          autosizeInput();
          postIntent({ type: "UpdateDraftInput", text: els.input.value });
        }
        menu.classList.add("hidden");
      });
      menu.appendChild(btn);
    });
  }

  function toggleRow(label, checked, onToggle) {
    var row = document.createElement("label");
    row.className = "toggle-row";
    var text = document.createElement("span");
    text.textContent = label;
    var input = document.createElement("input");
    input.type = "checkbox";
    input.checked = !!checked;
    input.addEventListener("change", onToggle);
    row.appendChild(text);
    row.appendChild(input);
    return row;
  }

  function sheetButton(label, sheet) {
    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "ghost-btn block";
    btn.textContent = label;
    btn.addEventListener("click", function () {
      openSheet(sheet);
      closeRightDrawer();
    });
    return btn;
  }

  function modeButton(label, mode) {
    var btn = document.createElement("button");
    btn.type = "button";
    btn.className =
      "chip-btn" + (state.conversationType === mode ? " active" : "");
    btn.textContent = label;
    btn.addEventListener("click", function () {
      postIntent({ type: "SwitchMode", mode: mode });
    });
    return btn;
  }

  function renderRightDrawer() {
    var body = els.rightDrawerBody;
    if (!body) return;
    body.innerHTML = "";

    var modeSec = document.createElement("section");
    modeSec.className = "drawer-section";
    modeSec.innerHTML = "<h4>模式</h4>";
    var modes = document.createElement("div");
    modes.className = "chip-row";
    modes.appendChild(modeButton("对话", "chat"));
    modes.appendChild(modeButton("写作", "writing"));
    modeSec.appendChild(modes);
    body.appendChild(modeSec);

    if (state.conversationType === "writing") {
      var subSec = document.createElement("section");
      subSec.className = "drawer-section";
      subSec.innerHTML = "<h4>Writing</h4>";
      var subs = document.createElement("div");
      subs.className = "chip-row";
      ["roleplay", "author"].forEach(function (sub) {
        var btn = document.createElement("button");
        btn.type = "button";
        btn.className =
          "chip-btn" + (state.writingSubMode === sub ? " active" : "");
        btn.textContent = sub;
        btn.addEventListener("click", function () {
          postIntent({ type: "SetWritingSubMode", subMode: sub });
        });
        subs.appendChild(btn);
      });
      subSec.appendChild(subs);
      body.appendChild(subSec);
    }

    var toggleSec = document.createElement("section");
    toggleSec.className = "drawer-section";
    toggleSec.innerHTML = "<h4>Toggles</h4>";
    if (state.conversationType === "chat") {
      var modeRow = document.createElement("div");
      modeRow.className = "chip-row";
      ["AUTO", "ASK", "PLAN"].forEach(function (m) {
        var label = m === "AUTO" ? "Auto" : m === "ASK" ? "Ask before edit" : "Plan mode";
        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "chip-btn" + (state.outputMode === m ? " active" : "");
        btn.textContent = label;
        btn.addEventListener("click", function () {
          if (state.outputMode !== m) postIntent({ type: "SetOutputMode", mode: m });
        });
        modeRow.appendChild(btn);
      });
      toggleSec.appendChild(modeRow);
    }
    toggleSec.appendChild(
      toggleRow("Web search", state.webSearchArmed, function () {
        postIntent({ type: "ToggleWebSearch" });
      }),
    );
    toggleSec.appendChild(
      toggleRow("Galgame", state.galgameEnabled, function () {
        postIntent({ type: "ToggleGalgame" });
      }),
    );
    toggleSec.appendChild(
      toggleRow("Dialogue highlight", state.dialogueHighlightEnabled, function () {
        postIntent({ type: "ToggleDialogueHighlight" });
      }),
    );
    toggleSec.appendChild(
      toggleRow("Dialogue bubble", state.roleplayDialogueBubbleEnabled, function () {
        postIntent({ type: "ToggleRoleplayDialogueBubble" });
      }),
    );
    toggleSec.appendChild(
      toggleRow("Auto maintain", state.structuredAutoMaintainEnabled, function () {
        postIntent({
          type: "SetStructuredAutoMaintain",
          enabled: !state.structuredAutoMaintainEnabled,
        });
      }),
    );
    toggleSec.appendChild(
      toggleRow("Outline", state.outlineEnabled, function () {
        postIntent({ type: "ToggleOutlineEnabled" });
      }),
    );
    toggleSec.appendChild(
      toggleRow("Post-edit polish", state.postEditEnabled, function () {
        postIntent({ type: "TogglePostEdit" });
      }),
    );
    if ((state.selectedCharacterCount || 0) > 1) {
      toggleSec.appendChild(
        toggleRow("Inter-character chat", state.interCharacterChatEnabled, function () {
          postIntent({ type: "ToggleInterCharacterChat" });
        }),
      );
    }
    body.appendChild(toggleSec);

    var quickSec = document.createElement("section");
    quickSec.className = "drawer-section";
    quickSec.innerHTML = "<h4>Actions</h4>";
    var ctxBtn = document.createElement("button");
    ctxBtn.type = "button";
    ctxBtn.className = "ghost-btn block";
    ctxBtn.textContent = "Context usage";
    ctxBtn.addEventListener("click", function () {
      openSheet("context");
      closeRightDrawer();
    });
    quickSec.appendChild(ctxBtn);
    var compress = document.createElement("button");
    compress.type = "button";
    compress.className = "ghost-btn block";
    compress.textContent = "Compress context";
    compress.addEventListener("click", function () {
      openSheet("compress");
      closeRightDrawer();
    });
    quickSec.appendChild(compress);
    body.appendChild(quickSec);

    var sheetSec = document.createElement("section");
    sheetSec.className = "drawer-section";
    sheetSec.innerHTML = "<h4>Sheets</h4>";
    var charLabel = state.selectedCharacterName
      ? "Characters · " + state.selectedCharacterName
      : "Characters";
    sheetSec.appendChild(sheetButton(charLabel, "characters"));
    sheetSec.appendChild(
      sheetButton(
        state.userCardEnabled
          ? "User · " + (state.userName || "on")
          : "User card",
        "userCard",
      ),
    );
    sheetSec.appendChild(sheetButton("Prompts", "prompts"));
    sheetSec.appendChild(sheetButton("Skills", "skills"));
    sheetSec.appendChild(sheetButton("World book", "worldBook"));
    sheetSec.appendChild(
      sheetButton(
        state.workspaceName
          ? "Workspace · " + state.workspaceName
          : "Workspace",
        "workspace",
      ),
    );
    sheetSec.appendChild(sheetButton("Memory tables", "memoryTable"));
    sheetSec.appendChild(
      sheetButton(
        state.outlineEnabled ? "Outline · on" : "Outline",
        "outline",
      ),
    );
    sheetSec.appendChild(sheetButton("Execution history", "execution"));
    body.appendChild(sheetSec);

    var themeSec = document.createElement("section");
    themeSec.className = "drawer-section";
    themeSec.innerHTML = "<h4>Theme</h4>";
    var themeBtn = document.createElement("button");
    themeBtn.type = "button";
    themeBtn.className = "ghost-btn block";
    themeBtn.textContent = "Theme · " + (themeState.themeId || "default");
    themeBtn.addEventListener("click", openThemeDialog);
    themeSec.appendChild(themeBtn);
    body.appendChild(themeSec);
  }

  function renderAll() {
    renderConversations();
    renderTopbar();
    renderMessages();
    renderTools();
    renderComposer();
    renderRightDrawer();
    renderPlanDetail();
  }

  function openEditDialog(msg) {
    editingMessageId = msg.id;
    els.editText.value = msg.content || "";
    if (typeof els.editDialog.showModal === "function") {
      els.editDialog.showModal();
    } else {
      var next = window.prompt("Edit message", msg.content || "");
      if (next != null) {
        postIntent({
          type: "EditMessage",
          messageId: msg.id,
          newContent: next,
        });
      }
      editingMessageId = null;
    }
  }

  function openSidebar() {
    closeRightDrawer();
    els.sidebar.classList.add("open");
    els.backdrop.classList.remove("hidden");
  }

  function closeSidebar() {
    els.sidebar.classList.remove("open");
    if (!els.rightDrawer.classList.contains("open")) {
      els.backdrop.classList.add("hidden");
    }
  }

  function openRightDrawer() {
    closeSidebar();
    els.rightDrawer.classList.add("open");
    els.backdrop.classList.remove("hidden");
    renderRightDrawer();
  }

  function closeRightDrawer() {
    els.rightDrawer.classList.remove("open");
    if (!els.sidebar.classList.contains("open")) {
      els.backdrop.classList.add("hidden");
    }
  }

  function closeAllDrawers() {
    closeSidebar();
    closeRightDrawer();
    els.backdrop.classList.add("hidden");
  }

  function sendMessage() {
    var text = (els.input.value || "").trim();
    if (state.isSending || state.isProcessingAttachments) return;
    if (!text && !(state.pendingAttachments || []).length) {
      if (state.conversationType === "writing") {
        postIntent({ type: "ContinueWriting" });
      }
      return;
    }
    var toSend = text;
    if (state.conversationType === "writing" && text) {
      if (window.LegadoBridge && typeof window.LegadoBridge.normalizeWritingInput === "function") {
        toSend = window.LegadoBridge.normalizeWritingInput(text, writingInputMode) || text;
      }
    }
    postIntent({ type: "SendMessage", content: toSend });
    els.input.value = "";
    writingSwitchState = {};
    autosizeInput();
    updateCompletionMenus();
  }

  function autosizeInput() {
    var el = els.input;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 160) + "px";
  }

  var FRAGMENT_SLOTS = [
    "sidebar",
    "right_drawer",
    "composer",
    "topbar",
    "message_area",
    "galgame_hud",
    "suggestions",
    "tool_panel",
    "interactive_panels",
  ];

  function captureSlotDefaults() {
    document.querySelectorAll("[data-slot]").forEach(function (node) {
      var slot = node.getAttribute("data-slot");
      if (slot && !slotDefaults[slot]) {
        slotDefaults[slot] = node.innerHTML;
      }
    });
    FRAGMENT_SLOTS.forEach(function (slot) {
      var node = document.querySelector('[data-slot="' + slot + '"]');
      if (node && !slotDefaults[slot]) {
        slotDefaults[slot] = node.innerHTML;
      }
    });
  }

  function applyFragments(fragments, resetMissing) {
    var map = fragments || {};
    var hasAny = false;
    var keys = [];
    for (var key in map) {
      if (Object.prototype.hasOwnProperty.call(map, key)) {
        hasAny = true;
        keys.push(key);
      }
    }
    var slots = {};
    FRAGMENT_SLOTS.forEach(function (slot) {
      slots[slot] = true;
    });
    keys.forEach(function (slot) {
      slots[slot] = true;
    });
    document.querySelectorAll("[data-slot]").forEach(function (node) {
      var s = node.getAttribute("data-slot");
      if (s) slots[s] = true;
    });
    Object.keys(slots).forEach(function (slot) {
      var node = document.querySelector('[data-slot="' + slot + '"]');
      if (!node) return;
      if (Object.prototype.hasOwnProperty.call(map, slot)) {
        if (map[slot]) {
          node.innerHTML = map[slot];
        } else if (slotDefaults[slot]) {
          node.innerHTML = slotDefaults[slot];
        }
      } else if (resetMissing && hasAny && slotDefaults[slot]) {
        node.innerHTML = slotDefaults[slot];
      }
    });
    rebindAfterFragment();
  }

  function applyState(next) {
    if (!next || typeof next !== "object") return;
    state = Object.assign({}, state, next);
    els.conversationList = $("conversation-list") || els.conversationList;
    renderAll();
  }

  function applyEffect(effect) {
    if (!effect || typeof effect !== "object") return;
    if (effect.type === "SetInputText") {
      els.input.value = effect.text || "";
      writingSwitchState = {};
      autosizeInput();
      updateCompletionMenus();
    } else if (effect.type === "StreamingUpdate") {
      applyStreamingUpdate(effect);
    }
  }

  function applyTheme(next) {
    if (!next || typeof next !== "object") return;
    themeState = Object.assign({}, themeState, next);
    var link = document.getElementById("theme-css");
    if (link && themeState.cssUrl) {
      link.setAttribute("href", themeState.cssUrl);
    }
    var frags = themeState.fragments;
    if (frags == null && window.LegadoBridge && window.LegadoBridge.getFragments) {
      try {
        frags = JSON.parse(window.LegadoBridge.getFragments() || "{}");
        themeState.fragments = frags;
      } catch (e) {
        frags = {};
      }
    }
    applyFragments(frags || {}, true);
    renderThemeList();
    // Fragments may replace sidebar/composer DOM; always re-bind UI from current state.
    renderAll();
  }

  function rebindAfterFragment() {
    els.conversationList = $("conversation-list") || els.conversationList;
    els.messageList = $("message-list") || els.messageList;
    els.rightDrawerBody = $("right-drawer-body") || els.rightDrawerBody;
    els.input = $("input") || els.input;
    els.btnSend = $("btn-send") || els.btnSend;
    els.btnStop = $("btn-stop") || els.btnStop;
    els.btnContinue = $("btn-continue") || els.btnContinue;
    els.btnAttach = $("btn-attach") || els.btnAttach;
    els.btnAtMention = $("btn-at-mention") || els.btnAtMention;
    els.btnAiHelp = $("btn-ai-help") || els.btnAiHelp;
    els.btnComposerMore = $("btn-composer-more") || els.btnComposerMore;
    els.composerMoreMenu = $("composer-more-menu") || els.composerMoreMenu;
    els.btnThinking = $("btn-thinking") || els.btnThinking;
    els.thinkingMenu = $("thinking-menu") || els.thinkingMenu;
    els.composerToolbar = $("composer-toolbar") || els.composerToolbar;
    els.suggestions = $("suggestions") || els.suggestions;
    els.slashMenu = $("slash-menu") || els.slashMenu;
    els.mentionMenu = $("mention-menu") || els.mentionMenu;
    els.attachmentChips = $("attachment-chips") || els.attachmentChips;
    els.galgameHud = $("galgame-hud") || els.galgameHud;
    els.galgameHudBody = $("galgame-hud-body") || els.galgameHudBody;
    els.btnGalgameToggle = $("btn-galgame-toggle") || els.btnGalgameToggle;
    els.btnGalgameRegen = $("btn-galgame-regen") || els.btnGalgameRegen;
    els.btnGalgameRecreate = $("btn-galgame-recreate") || els.btnGalgameRecreate;
    els.interactivePanels = $("interactive-panels") || els.interactivePanels;
    els.composerToolsPanel = $("composer-tools-panel") || els.composerToolsPanel;
    els.btnNewChat = $("btn-new-chat") || els.btnNewChat;
    els.btnTheme = $("btn-theme") || els.btnTheme;
    els.btnBack = $("btn-back") || els.btnBack;
    els.btnSettings = $("btn-settings") || els.btnSettings;
    els.btnOpenRight = $("btn-open-right") || els.btnOpenRight;
    els.btnCloseRight = $("btn-close-right") || els.btnCloseRight;
    els.themeDialog = $("theme-dialog") || els.themeDialog;
    els.themeList = $("theme-list") || els.themeList;
    els.editDialog = $("edit-dialog") || els.editDialog;
    els.editForm = $("edit-form") || els.editForm;
    els.editText = $("edit-text") || els.editText;
    els.sidebar = $("sidebar") || els.sidebar;
    els.rightDrawer = $("right-drawer") || els.rightDrawer;
    els.backdrop = $("drawer-backdrop") || els.backdrop;
    els.toolPanel = $("tool-panel") || els.toolPanel;
    els.currentTitle = $("current-title") || els.currentTitle;
    els.currentMeta = $("current-meta") || els.currentMeta;
    bindStaticControls();
  }

  function renderThemeList() {
    if (!els.themeList) return;
    els.themeList.innerHTML = "";
    var themes = themeState.themes || [];
    if (!themes.length && window.LegadoBridge && window.LegadoBridge.listThemes) {
      try {
        themes = JSON.parse(window.LegadoBridge.listThemes() || "[]");
        themeState.themes = themes;
      } catch (e) {
        themes = [];
      }
    }
    themes.forEach(function (theme) {
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className =
        "theme-item" + (theme.id === themeState.themeId || theme.selected ? " active" : "");
      btn.innerHTML =
        '<div class="title">' +
        escapeHtml(theme.name || theme.id) +
        '</div><div class="meta">' +
        escapeHtml((theme.builtin ? "built-in" : "user") + " · " + (theme.id || "")) +
        "</div>";
      btn.addEventListener("click", function () {
        if (window.LegadoBridge && window.LegadoBridge.setThemeId) {
          window.LegadoBridge.setThemeId(theme.id);
          themeState.themeId = theme.id;
          renderThemeList();
        }
      });
      els.themeList.appendChild(btn);
    });
  }

  function openThemeDialog() {
    renderThemeList();
    if (els.themeDialog && typeof els.themeDialog.showModal === "function") {
      els.themeDialog.showModal();
    }
  }

  var staticBound = false;
  function bindStaticControls() {
    if (els.btnNewChat) {
      els.btnNewChat.onclick = function () {
        postIntent({ type: "NewConversation" });
        closeSidebar();
      };
    }
    if (els.btnTheme) {
      els.btnTheme.onclick = openThemeDialog;
    }
    if (els.btnSend) {
      els.btnSend.onclick = sendMessage;
    }
    if (els.btnStop) {
      els.btnStop.onclick = function () {
        postIntent({ type: "StopGenerating" });
      };
    }
    if (els.btnContinue) {
      els.btnContinue.onclick = function () {
        composerMoreOpen = false;
        renderComposerMoreMenu();
        postIntent({ type: "ContinueWriting" });
      };
    }
    if (els.btnAttach) {
      els.btnAttach.onclick = function () {
        if (window.LegadoBridge && window.LegadoBridge.pickAttachments) {
          window.LegadoBridge.pickAttachments();
        }
      };
    }
    if (els.btnComposerMore) {
      els.btnComposerMore.onclick = function (e) {
        e.stopPropagation();
        composerMoreOpen = !composerMoreOpen;
        thinkingMenuOpen = false;
        renderComposerMoreMenu();
        renderThinkingMenu();
      };
    }
    if (els.btnThinking) {
      els.btnThinking.onclick = function (e) {
        e.stopPropagation();
        thinkingMenuOpen = !thinkingMenuOpen;
        composerMoreOpen = false;
        renderComposerMoreMenu();
        renderThinkingMenu();
      };
    }
    if (els.btnAtMention) {
      els.btnAtMention.onclick = function () {
        if (!els.input) return;
        var text = els.input.value || "";
        var suffix = (!text || text.slice(-1) === " " || text.slice(-1) === "@") ? "@" : " @";
        els.input.value = text + suffix;
        autosizeInput();
        postIntent({ type: "UpdateDraftInput", text: els.input.value });
        els.input.focus();
      };
    }
    if (els.btnAiHelp) {
      els.btnAiHelp.onclick = function () {
        composerMoreOpen = false;
        renderComposerMoreMenu();
        var draft = els.input ? els.input.value : "";
        postIntent({
          type: "AiHelpReply",
          draftText: draft,
          inputMode: writingInputMode,
        });
      };
    }
    if (els.btnGalgameToggle) {
      els.btnGalgameToggle.onclick = function () {
        if (!els.galgameHudBody) return;
        els.galgameHudBody.classList.toggle("collapsed");
        var open = !els.galgameHudBody.classList.contains("collapsed");
        els.btnGalgameToggle.textContent = open ? "HUD ▴" : "HUD ▾";
      };
    }
    if (els.btnGalgameRegen) {
      els.btnGalgameRegen.onclick = function () {
        postIntent({ type: "RegenerateGalgameHud" });
      };
    }
    if (els.btnGalgameRecreate) {
      els.btnGalgameRecreate.onclick = function () {
        postIntent({ type: "RecreateGalgameHud" });
      };
    }
    if (els.btnBack) {
      els.btnBack.onclick = function () {
        if (window.LegadoBridge && window.LegadoBridge.goBack) window.LegadoBridge.goBack();
      };
    }
    if (els.btnSettings) {
      els.btnSettings.onclick = function () {
        if (window.LegadoBridge && window.LegadoBridge.openSettings) {
          window.LegadoBridge.openSettings();
        }
      };
    }
    if (els.btnOpenRight) {
      els.btnOpenRight.onclick = openRightDrawer;
    }
    if (els.btnCloseRight) {
      els.btnCloseRight.onclick = closeRightDrawer;
    }
    if (els.btnToggleSidebar) {
      els.btnToggleSidebar.onclick = openSidebar;
    }
    if (els.btnToggleRight) {
      els.btnToggleRight.onclick = openRightDrawer;
    }
    if (els.backdrop) {
      els.backdrop.onclick = closeAllDrawers;
    }
    document.addEventListener("click", function (e) {
      if (composerMoreOpen) {
        var wrap = document.querySelector(".composer-more-wrap");
        if (!wrap || !wrap.contains(e.target)) {
          composerMoreOpen = false;
          renderComposerMoreMenu();
        }
      }
      if (thinkingMenuOpen) {
        var twrap = document.querySelector(".composer-thinking-wrap");
        if (!twrap || !twrap.contains(e.target)) {
          thinkingMenuOpen = false;
          renderThinkingMenu();
        }
      }
    });
    if (!richTextBound) {
      richTextBound = true;
      document.addEventListener("click", function (e) {
        var copyBtn = e.target.closest(".code-copy-btn");
        if (copyBtn) {
          var block = copyBtn.closest(".code-block");
          var codeEl = block ? block.querySelector("pre code") : null;
          if (codeEl) copyText(codeEl.textContent);
          return;
        }
        var viewImg = e.target.closest(".msg-image img");
        if (viewImg) {
          openImageViewer(viewImg.getAttribute("src") || "");
        }
      });
    }
    if (els.input) {
      els.input.oninput = function () {
        autosizeInput();
        postIntent({ type: "UpdateDraftInput", text: els.input.value });
        updateCompletionMenus();
      };
      els.input.onkeydown = function (e) {
        if (e.key === "Enter" && !e.shiftKey) {
          e.preventDefault();
          sendMessage();
        }
      };
    }
  }

  function bind() {
    els = {
      conversationList: $("conversation-list"),
      messageList: $("message-list"),
      toolPanel: $("tool-panel"),
      input: $("input"),
      btnSend: $("btn-send"),
      btnStop: $("btn-stop"),
      btnContinue: $("btn-continue"),
      btnAttach: $("btn-attach"),
      btnAtMention: $("btn-at-mention"),
      btnAiHelp: $("btn-ai-help"),
      btnComposerMore: $("btn-composer-more"),
      composerMoreMenu: $("composer-more-menu"),
      btnThinking: $("btn-thinking"),
      thinkingMenu: $("thinking-menu"),
      btnNewChat: $("btn-new-chat"),
      btnTheme: $("btn-theme"),
      btnBack: $("btn-back"),
      btnSettings: $("btn-settings"),
      btnToggleSidebar: $("btn-toggle-sidebar"),
      btnToggleRight: $("btn-toggle-right"),
      btnOpenRight: $("btn-open-right"),
      btnCloseRight: $("btn-close-right"),
      btnGalgameToggle: $("btn-galgame-toggle"),
      btnGalgameRegen: $("btn-galgame-regen"),
      btnGalgameRecreate: $("btn-galgame-recreate"),
      currentTitle: $("current-title"),
      currentMeta: $("current-meta"),
      sidebar: $("sidebar"),
      rightDrawer: $("right-drawer"),
      rightDrawerBody: $("right-drawer-body"),
      backdrop: $("drawer-backdrop"),
      editDialog: $("edit-dialog"),
      editForm: $("edit-form"),
      editText: $("edit-text"),
      themeDialog: $("theme-dialog"),
      themeList: $("theme-list"),
      composerToolbar: $("composer-toolbar"),
      suggestions: $("suggestions"),
      slashMenu: $("slash-menu"),
      mentionMenu: $("mention-menu"),
      attachmentChips: $("attachment-chips"),
      galgameHud: $("galgame-hud"),
      galgameHudBody: $("galgame-hud-body"),
      interactivePanels: $("interactive-panels"),
      composerToolsPanel: $("composer-tools-panel"),
    };

    captureSlotDefaults();
    bindStaticControls();

    if (els.editForm && !staticBound) {
      els.editForm.addEventListener("submit", function (e) {
        var submitter = e.submitter;
        var value = submitter ? submitter.value : "cancel";
        if (value === "save" && editingMessageId) {
          postIntent({
            type: "EditMessage",
            messageId: editingMessageId,
            newContent: els.editText.value,
          });
        }
        editingMessageId = null;
      });
      staticBound = true;
    }
  }

  var planFeedbackFloat = null;
  /** 计划正文选中文字时，浮动一个「反馈这段」按钮（选择菜单的 web 等价物）。 */
  function showPlanFeedbackFloat(planBody, plan) {
    if (planFeedbackFloat) planFeedbackFloat.remove();
    var captured = window.getSelection ? window.getSelection().toString().trim() : "";
    var panel = planBody.closest(".panel-card") || planBody.closest(".plan-fullscreen") || planBody;
    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "primary-btn plan-feedback-float";
    btn.textContent = "反馈这段";
    btn.addEventListener("click", function () {
      btn.remove();
      planFeedbackFloat = null;
      openPlanFeedback(panel, plan, captured);
    });
    document.body.appendChild(btn);
    planFeedbackFloat = btn;
  }

  function hidePlanFeedbackFloat() {
    if (planFeedbackFloat) {
      planFeedbackFloat.remove();
      planFeedbackFloat = null;
    }
  }

  document.addEventListener("selectionchange", function () {
    var sel = window.getSelection();
    if (!sel || sel.isCollapsed || !sel.anchorNode) {
      hidePlanFeedbackFloat();
      return;
    }
    if (!sel.toString().trim() || !state.pendingPlan) {
      hidePlanFeedbackFloat();
      return;
    }
    var el = sel.anchorNode.nodeType === Node.TEXT_NODE
      ? sel.anchorNode.parentElement
      : sel.anchorNode;
    while (el && el !== document.body) {
      if (el.classList &&
          (el.classList.contains("plan-body") || el.classList.contains("plan-fullscreen-view"))) {
        showPlanFeedbackFloat(el, state.pendingPlan);
        return;
      }
      el = el.parentElement;
    }
    hidePlanFeedbackFloat();
  });

  window.LegadoAiChat = {
    applyState: applyState,
    applyEffect: applyEffect,
    applyTheme: applyTheme,
    openSheet: openSheet,
    __sharedEnhanceLoaded: true,
  };

  document.addEventListener("DOMContentLoaded", function () {
    bind();
    renderAll();
    if (window.LegadoBridge && typeof window.LegadoBridge.ready === "function") {
      window.LegadoBridge.ready();
    }
  });
})();
