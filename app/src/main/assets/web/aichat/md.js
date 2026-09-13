/**
 * Lightweight markdown → HTML renderer.
 * Shared utility — included by the stock shell, reusable by custom themes.
 * Exposed as window.formatMarkdown(text) → HTML string.
 */
(function () {
  "use strict";

  function safeHtml(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  var EMOJI = {
    "smile": "😄", "laughing": "😆", "grin": "😁", "joy": "😂", "blush": "😊",
    "heart": "❤️", "broken_heart": "💔", "sparkles": "✨", "star": "⭐", "fire": "🔥",
    "thumbsup": "👍", "+1": "👍", "thumbsdown": "👎", "-1": "👎",
    "clap": "👏", "wave": "👋", "pray": "🙏", "muscle": "💪", "eyes": "👀",
    "thinking": "🤔", "shrug": "🤷", "100": "💯", "check": "✅", "xmark": "❌",
    "warning": "⚠️", "question": "❓", "exclamation": "❗", "bulb": "💡",
    "book": "📖", "memo": "📝", "rocket": "🚀", "tada": "🎉", "party": "🎉",
    "sob": "😭", "sweat_smile": "😅", "wink": "😉", "sunglasses": "😎",
    "robot": "🤖", "alien": "👽", "crown": "👑", "gem": "💎", "lock": "🔒",
    "key": "🔑", "link": "🔗", "pushpin": "📌", "paperclip": "📎",
    "hourglass": "⏳", "alarm_clock": "⏰", "calendar": "📅", "clock": "🕐",
    "up": "⬆️", "down": "⬇️", "left": "⬅️", "right": "➡️",
    "red_circle": "🔴", "green_circle": "🟢", "yellow_circle": "🟡", "blue_circle": "🔵",
    "rocket_ship": "🚀", "recycle": "♻️", "zzz": "💤", "coffee": "☕", "tea": "🍵"
  };

  function parseInline(s) {
    s = safeHtml(s);
    // Preserve <u> / <kbd> through inline parsing
    s = s.replace(/<u>/g, "%%U0%%").replace(/<\/u>/g, "%%U1%%")
         .replace(/<kbd>/g, "%%K0%%").replace(/<\/kbd>/g, "%%K1%%");
    s = s.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
    s = s.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, "<em>$1</em>");
    s = s.replace(/`([^`]+)`/g, "<code>$1</code>");
    s = s.replace(/~~(.+?)~~/g, "<del>$1</del>");
    s = s.replace(/\[(.+?)\]\((.+?)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');
    // Bare URL auto-linking — skip URLs already inside an href attribute or tag.
    s = s.replace(/(^|[^"'>=])((?:https?:\/\/|www\.)[^\s<>"'()]+[^\s<>"'.()])(?=[^<]*(?:<|$))/gi,
      function (_, pre, url) {
        var href = /^www\./i.test(url) ? "https://" + url : url;
        return pre + '<a href="' + href + '" target="_blank" rel="noopener">' + url + "</a>";
      });
    // Emoji shortcodes
    s = s.replace(/:([a-z0-9_+-]+):/gi, function (_, name) {
      var emoji = EMOJI[name.toLowerCase()];
      return emoji !== undefined ? emoji : ":" + name + ":";
    });
    s = s.replace(/%%U0%%/g, "<u>").replace(/%%U1%%/g, "</u>");
    s = s.replace(/%%K0%%/g, "<kbd>").replace(/%%K1%%/g, "</kbd>");
    return s;
  }

  function parseTableAlign(sep) {
    return sep.replace(/^\||\|$/g, "").split("|").map(function (c) {
      var s = c.trim();
      if (s.slice(0, 1) === ":" && s.slice(-1) === ":") return "center";
      if (s.slice(-1) === ":") return "right";
      return "left";
    });
  }

  function formatMarkdown(raw) {
    var text = String(raw == null ? "" : raw);
    if (!text) return "";

    // 1. Decode common HTML entities
    text = text.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&")
      .replace(/&quot;/g, '"').replace(/&#39;|&apos;|&#x27;/g, "'");

    // 2. Mixed HTML → markdown equivalents
    text = text.replace(/<br\s*\/?>/gi, "\n");
    text = text.replace(/<hr\s*\/?>/gi, "\n---\n");
    text = text.replace(/<h1[^>]*>([\s\S]*?)<\/h1>/gi, "\n# $1\n");
    text = text.replace(/<h2[^>]*>([\s\S]*?)<\/h2>/gi, "\n## $1\n");
    text = text.replace(/<h3[^>]*>([\s\S]*?)<\/h3>/gi, "\n### $1\n");
    text = text.replace(/<p[^>]*>([\s\S]*?)<\/p>/gi, "\n$1\n");
    text = text.replace(/<blockquote[^>]*>([\s\S]*?)<\/blockquote>/gi, function (_, c) {
      return "\n" + c.trim().split("\n").map(function (l) { return "> " + l; }).join("\n") + "\n";
    });
    text = text.replace(/<li[^>]*>([\s\S]*?)<\/li>/gi, "- $1\n");
    text = text.replace(/<a\s+[^>]*href\s*=\s*"([^"]*)"[^>]*>([\s\S]*?)<\/a>/gi, "[$2]($1)");
    text = text.replace(/<(b|strong)[^>]*>([\s\S]*?)<\/\1>/gi, "**$2**");
    text = text.replace(/<(i|em)[^>]*>([\s\S]*?)<\/\1>/gi, "*$2*");
    text = text.replace(/<code[^>]*>([\s\S]*?)<\/code>/gi, "`$1`");
    text = text.replace(/<(del|s|strike)[^>]*>([\s\S]*?)<\/\1>/gi, "~~$2~~");
    text = text.replace(/<pre[^>]*>\s*(?:<code[^>]*>)?([\s\S]*?)(?:<\/code>\s*)?<\/pre>/gi, function (_, code) {
      return "\n```\n" + code.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&").trim() + "\n```\n";
    });
    text = text.replace(/<details[^>]*>([\s\S]*?)<\/details>/gi, function (_, inner) {
      var sm = /<summary[^>]*>([\s\S]*?)<\/summary>/i.exec(inner);
      var summary = sm ? sm[1].trim() : "Details";
      var body = sm ? inner.replace(sm[0], "").trim() : inner.trim();
      return "\n:::details " + summary + "\n" + body + "\n:::\n";
    });

    // 3. Block-level parse
    var lines = text.split("\n");
    var out = "";
    var inFence = false;
    var fenceLang = "";
    var fenceBuf = "";
    var i = 0;

    while (i < lines.length) {
      var rawLine = lines[i];
      var line = rawLine.trimEnd();
      var trimmed = line.trimStart();
      var indent = line.length - trimmed.length;

      if (/^```/.test(trimmed)) {
        if (inFence) {
          out += codeBlock(fenceLang, safeHtml(fenceBuf.trimEnd()));
          fenceBuf = "";
          fenceLang = "";
          inFence = false;
        } else {
          var langMatch = /^```[ \t]*([a-zA-Z0-9_+#.-]*)/.exec(trimmed);
          fenceLang = langMatch && langMatch[1] ? langMatch[1] : "";
          inFence = true;
        }
        i++; continue;
      }
      if (inFence) {
        if (fenceBuf) fenceBuf += "\n";
        fenceBuf += line;
        i++; continue;
      }

      if (!trimmed) { i++; continue; }

      if (/^:::details\s/.test(trimmed)) {
        var summary = trimmed.replace(/^:::details\s+/, "").trim();
        var detHtml = "";
        i++;
        while (i < lines.length) {
          if (/^:::$/.test(lines[i].trim())) { i++; break; }
          if (detHtml) detHtml += "\n";
          detHtml += lines[i].trimEnd();
          i++;
        }
        out += "<details><summary>" + safeHtml(summary) + "</summary>" + formatMarkdown(detHtml) + "</details>";
        continue;
      }

      var h = /^(#{1,3})\s+(.+)$/.exec(line);
      if (h) {
        out += "<h" + h[1].length + ">" + parseInline(h[2]) + "</h" + h[1].length + ">";
        i++; continue;
      }

      if (/^(-{3,}|\*{3,})\s*$/.test(trimmed)) {
        out += "<hr>";
        i++; continue;
      }

      if (trimmed.slice(0, 2) === "> ") {
        var qLines = [];
        while (i < lines.length && lines[i].trimStart().slice(0, 2) === "> ") {
          qLines.push(lines[i].trimStart().slice(2));
          i++;
        }
        out += "<blockquote>" + qLines.map(parseInline).join("<br>") + "</blockquote>";
        continue;
      }

      if (/^(\s*)[-*]\s+/.test(line)) {
        out += "<ul>";
        var listIndent = indent;
        while (i < lines.length) {
          var liLine = lines[i].trimEnd();
          var liTrimmed = liLine.trimStart();
          var liIndent = liLine.length - liTrimmed.length;
          var ulMatch = /^(\s*)[-*]\s+(.+)$/.exec(liLine);
          if (!ulMatch || liIndent !== listIndent) break;
          var ulTask = /^\[([ xX])\]\s*(.*)$/.exec(ulMatch[2]);
          if (ulTask) {
            var ulChecked = ulTask[1].toLowerCase() === "x" ? ' checked="checked"' : "";
            out += '<li class="task"><input type="checkbox" disabled="disabled"' + ulChecked + "> " + parseInline(ulTask[2]) + "</li>";
          } else {
            out += "<li>" + parseInline(ulMatch[2]) + "</li>";
          }
          i++;
        }
        out += "</ul>";
        continue;
      }

      if (/^(\s*)\d+\.\s+/.test(line)) {
        out += "<ol>";
        var olIndent = indent;
        while (i < lines.length) {
          var oLine = lines[i].trimEnd();
          var oTrimmed = oLine.trimStart();
          var oIndent = oLine.length - oTrimmed.length;
          var olMatch = /^(\s*)(\d+)\.\s+(.+)$/.exec(oLine);
          if (!olMatch || oIndent !== olIndent) break;
          var olTask = /^\[([ xX])\]\s*(.*)$/.exec(olMatch[3]);
          if (olTask) {
            var olChecked = olTask[1].toLowerCase() === "x" ? ' checked="checked"' : "";
            out += '<li class="task"><input type="checkbox" disabled="disabled"' + olChecked + "> " + parseInline(olTask[2]) + "</li>";
          } else {
            out += "<li>" + parseInline(olMatch[3]) + "</li>";
          }
          i++;
        }
        out += "</ol>";
        continue;
      }

      if (/^\|.+\|$/.test(line) && i + 1 < lines.length && /^\|(\s*:?-{3,}:?\s*\|)+$/.test(lines[i + 1].trim())) {
        var hCells = line.replace(/^\||\|$/g, "").split("|").map(function (c) { return c.trim(); });
        var aligns = parseTableAlign(lines[i + 1].trim());
        i += 2;
        out += "<table><thead><tr>";
        hCells.forEach(function (cell, idx) {
          out += '<th style="text-align:' + (aligns[idx] || "left") + '">' + parseInline(cell) + "</th>";
        });
        out += "</tr></thead><tbody>";
        while (i < lines.length && /^\|.+\|$/.test(lines[i].trim())) {
          var rCells = lines[i].trim().replace(/^\||\|$/g, "").split("|").map(function (c) { return c.trim(); });
          out += "<tr>";
          rCells.forEach(function (cell, idx) {
            out += '<td style="text-align:' + (aligns[idx] || "left") + '">' + parseInline(cell) + "</td>";
          });
          out += "</tr>";
          i++;
        }
        out += "</tbody></table>";
        continue;
      }

      // Paragraph
      var pLines = [];
      while (i < lines.length) {
        var pLine = lines[i].trimEnd();
        var pTrimmed = pLine.trimStart();
        if (!pTrimmed) break;
        if (/^```/.test(pTrimmed) || /^:::details\s/.test(pTrimmed) || /^:::$/.test(pTrimmed) ||
            /^(#{1,3})\s+/.test(pLine) || /^(-{3,}|\*{3,})\s*$/.test(pTrimmed) ||
            pTrimmed.slice(0, 2) === "> " || /^(\s*)[-*]\s+/.test(pLine) ||
            /^(\s*)\d+\.\s+/.test(pLine) || /^\|.+\|$/.test(pLine)) break;
        pLines.push(pLine);
        i++;
      }
      if (pLines.length) {
        out += "<p>" + pLines.map(parseInline).join("<br>") + "</p>";
      } else {
        i++;
      }
    }

    if (inFence && fenceBuf) out += codeBlock(fenceLang, safeHtml(fenceBuf.trimEnd()));
    return out;
  }

  function codeBlock(lang, codeHtml) {
    var langAttr = lang ? ' data-lang="' + lang + '"' : "";
    return (
      '<div class="code-block"' + langAttr + ">" +
      '<button type="button" class="code-copy-btn" title="Copy code">Copy</button>' +
      "<pre><code>" + codeHtml + "</code></pre></div>"
    );
  }

  window.formatMarkdown = formatMarkdown;
})();
