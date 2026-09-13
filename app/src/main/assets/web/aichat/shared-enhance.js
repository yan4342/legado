/**
 * shared-enhance.js — runtime, non-invasive enhancements for AI-chat HTML themes.
 *
 * Injected at load time (in memory, never written to disk) for user packs that carry their
 * own app.js / index.html. Provides:
 *   1. StreamingUpdate handling — live per-token message updates for shells that don't
 *      implement that effect type yet.
 *   2. Code-block copy buttons (`.code-copy-btn` inside `.code-block`).
 *   3. Fullscreen image viewer for `.msg-image img` clicks.
 *
 * Design notes:
 *   - Event delegation only; no dependency on the host shell's internal rendering.
 *   - Guarded by the `__sharedEnhanceLoaded` flag on window.LegadoAiChat, which the built-in
 *     shell sets when it already ships these features inline — avoids double binding.
 *   - Streaming bubble rendering is best-effort: creates a compatible `.msg.streaming`
 *     element if the host has not rendered one yet.
 */
(function () {
  "use strict";

  if (window.LegadoAiChat && window.LegadoAiChat.__sharedEnhanceLoaded) return;
  if (window.LegadoAiChat) window.LegadoAiChat.__sharedEnhanceLoaded = true;

  function escapeHtml(text) {
    return String(text == null ? "" : text)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
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

  function renderMd(text) {
    var raw = String(text == null ? "" : text);
    if (!raw) return "";
    return typeof window.formatMarkdown === "function"
      ? window.formatMarkdown(raw)
      : escapeHtml(raw);
  }

  function messageListEl() {
    return (
      document.querySelector(".message-list") ||
      document.getElementById("message-list") ||
      document.querySelector("[data-slot='message_area'] .message-list")
    );
  }

  function scrollListToBottom(list) {
    if (!list) return;
    if (list.scrollHeight - list.scrollTop - list.clientHeight < 240) {
      list.scrollTop = list.scrollHeight;
    }
  }

  // ---- 1. StreamingUpdate ----

  var streamUpdatePending = false;
  var pendingStreamMsg = null;

  function renderStreamingBody(el, msg) {
    var bodyEl = el.querySelector(".msg-body");
    if (!bodyEl) return;
    // Persist fold open state before clobbering the DOM.
    var openKeys = {};
    bodyEl.querySelectorAll("details.fold[data-fold-key]").forEach(function (d) {
      openKeys[d.getAttribute("data-fold-key")] = d.open;
    });
    var html = "";
    var reasoning = msg.reasoning || "";
    if (reasoning) {
      html +=
        '<details class="fold streaming" data-fold-key="se-reasoning" open>' +
        "<summary>💭 " +
        escapeHtml(msg.speakerName || "Reasoning") +
        "</summary>" +
        '<div class="reasoning">' +
        renderMd(reasoning) +
        "</div></details>";
    }
    html += '<div class="text">' + renderMd(msg.content || "") + "</div>";
    bodyEl.innerHTML = html;
    bodyEl.querySelectorAll("details.fold[data-fold-key]").forEach(function (d) {
      var key = d.getAttribute("data-fold-key");
      if (key && openKeys[key]) d.open = true;
    });
  }

  function findStreamingEl(msgId) {
    if (msgId) {
      var byId = document.querySelector('.msg.streaming[data-msg-id="' + msgId + '"]');
      if (byId) return byId;
    }
    return document.querySelector(".msg.streaming");
  }

  function ensureStreamingEl(msg) {
    var existing = findStreamingEl(msg.id);
    if (existing) return existing;
    var list = messageListEl();
    if (!list) return null;
    var el = document.createElement("article");
    el.className = "msg assistant streaming";
    if (msg.id) el.setAttribute("data-msg-id", msg.id);
    var role = document.createElement("div");
    role.className = "msg-role";
    role.textContent = msg.speakerName || "assistant";
    el.appendChild(role);
    var body = document.createElement("div");
    body.className = "msg-body";
    el.appendChild(body);
    list.appendChild(el);
    scrollListToBottom(list);
    return el;
  }

  function scheduleStreamingUpdate(msg) {
    pendingStreamMsg = msg;
    if (streamUpdatePending) return;
    streamUpdatePending = true;
    requestAnimationFrame(function () {
      streamUpdatePending = false;
      var m = pendingStreamMsg;
      pendingStreamMsg = null;
      if (!m) return;
      var el = ensureStreamingEl(m);
      if (el) renderStreamingBody(el, m);
    });
  }

  function applyStreamingUpdate(effect) {
    var msg = effect.message || {};
    scheduleStreamingUpdate(msg);
  }

  function tryWrap() {
    var api = window.LegadoAiChat;
    if (!api || typeof api.applyEffect !== "function") return false;
    var original = api.applyEffect;
    api.applyEffect = function (effect) {
      try {
        if (effect && effect.type === "StreamingUpdate") {
          applyStreamingUpdate(effect);
        }
      } catch (e) {
        // Never let enhancement failures break the host shell.
      }
      return original.apply(api, arguments);
    };
    return true;
  }

  // ---- 2 & 3. Code copy + image viewer (document-level delegation) ----

  var viewerEl = null;
  function openImageViewer(src) {
    if (!src) return;
    if (!viewerEl) {
      viewerEl = document.createElement("div");
      viewerEl.className = "image-viewer";
      viewerEl.innerHTML = '<img alt="preview" />';
      viewerEl.addEventListener("click", function () {
        viewerEl.classList.remove("open");
      });
      document.body.appendChild(viewerEl);
    }
    viewerEl.querySelector("img").setAttribute("src", src);
    viewerEl.classList.add("open");
  }

  document.addEventListener("click", function (e) {
    var copyBtn = e.target.closest(".code-copy-btn");
    if (copyBtn) {
      var block = copyBtn.closest(".code-block");
      var codeEl = block ? block.querySelector("pre code") : null;
      if (codeEl) copyText(codeEl.textContent);
      return;
    }
    var img = e.target.closest(".msg-image img");
    if (img) openImageViewer(img.getAttribute("src") || "");
  });

  // ---- Fallback styles (so the features work even if the theme CSS lacks them) ----

  function ensureFallbackStyles() {
    if (document.getElementById("shared-enhance-style")) return;
    var style = document.createElement("style");
    style.id = "shared-enhance-style";
    style.textContent = [
      ".image-viewer{position:fixed;inset:0;z-index:9999;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,.85);opacity:0;pointer-events:none;transition:opacity .18s ease}",
      ".image-viewer.open{opacity:1;pointer-events:auto}",
      ".image-viewer img{max-width:94vw;max-height:92vh;object-fit:contain;border-radius:8px}",
      ".code-block{position:relative}",
      ".code-copy-btn{position:absolute;top:6px;right:6px;z-index:2;font-size:11px;padding:2px 8px;border-radius:6px;border:1px solid rgba(128,128,128,.4);background:rgba(128,128,128,.15);color:inherit;cursor:pointer;opacity:.7}",
      ".code-copy-btn:hover{opacity:1}",
      ".msg.streaming .fold.streaming{opacity:.85}",
    ].join("\n");
    document.head.appendChild(style);
  }
  ensureFallbackStyles();

  // ---- Init: wrap the host's applyEffect once it exists ----

  function startRetry() {
    var attempts = 0;
    var timer = setInterval(function () {
      attempts++;
      if (tryWrap() || attempts > 50) clearInterval(timer);
    }, 120);
  }

  if (tryWrap()) {
    return;
  }
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () {
      if (!tryWrap()) startRetry();
    });
  } else {
    startRetry();
  }
})();
