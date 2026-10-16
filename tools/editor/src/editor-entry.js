// CodeMirror 6 编辑器入口：打包后暴露 window.YHEditor.create(parent, options)。
// 页面和 Kotlin 之间没有 JS 桥：Kotlin 通过 evaluateJavascript 调用 window.editorApi 的方法、读取返回值。
import {
  EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter,
  drawSelection, highlightSpecialChars,
} from '@codemirror/view';
import { EditorState, Compartment, Text } from '@codemirror/state';
import { defaultKeymap, history, historyKeymap, indentWithTab, undo, redo, indentMore } from '@codemirror/commands';
import { searchKeymap, openSearchPanel, highlightSelectionMatches } from '@codemirror/search';
import {
  HighlightStyle, syntaxHighlighting, indentOnInput, bracketMatching, StreamLanguage, indentUnit,
} from '@codemirror/language';
import { closeBrackets, closeBracketsKeymap } from '@codemirror/autocomplete';
import { tags as t } from '@lezer/highlight';

import { javascript } from '@codemirror/lang-javascript';
import { python } from '@codemirror/lang-python';
import { json } from '@codemirror/lang-json';
import { css } from '@codemirror/lang-css';
import { html } from '@codemirror/lang-html';
import { markdown } from '@codemirror/lang-markdown';
import { java } from '@codemirror/lang-java';
import { sql } from '@codemirror/lang-sql';
import { kotlin, c, cpp, csharp } from '@codemirror/legacy-modes/mode/clike';
import { go } from '@codemirror/legacy-modes/mode/go';
import { rust } from '@codemirror/legacy-modes/mode/rust';
import { shell } from '@codemirror/legacy-modes/mode/shell';
import { yaml } from '@codemirror/legacy-modes/mode/yaml';
import { toml } from '@codemirror/legacy-modes/mode/toml';
import { swift } from '@codemirror/legacy-modes/mode/swift';
import { ruby } from '@codemirror/legacy-modes/mode/ruby';
import { lua } from '@codemirror/legacy-modes/mode/lua';
import { diff } from '@codemirror/legacy-modes/mode/diff';

const legacy = (mode) => () => StreamLanguage.define(mode);

// 键名和 App 里 CodeLanguages / highlight.js 的语言 id 一致
const LANGS = {
  javascript: () => javascript({ jsx: true }),
  typescript: () => javascript({ typescript: true, jsx: true }),
  python: () => python(),
  json: () => json(),
  css: () => css(),
  scss: () => css(),
  less: () => css(),
  html: () => html(),
  xml: () => html(),
  markdown: () => markdown(),
  java: () => java(),
  sql: () => sql(),
  kotlin: legacy(kotlin),
  c: legacy(c),
  cpp: legacy(cpp),
  csharp: legacy(csharp),
  go: legacy(go),
  rust: legacy(rust),
  bash: legacy(shell),
  yaml: legacy(yaml),
  ini: legacy(toml),
  swift: legacy(swift),
  ruby: legacy(ruby),
  lua: legacy(lua),
  diff: legacy(diff),
};

// 配色全部引用 themes.css 的变量：切换主题时编辑器自动跟着变，不需要重建
const highlight = HighlightStyle.define([
  { tag: [t.keyword, t.modifier, t.operatorKeyword, t.controlKeyword, t.definitionKeyword, t.moduleKeyword], color: 'var(--hl-keyword)' },
  { tag: [t.string, t.special(t.string), t.regexp, t.character], color: 'var(--hl-string)' },
  { tag: [t.number, t.bool, t.null, t.atom], color: 'var(--hl-number)' },
  { tag: [t.comment, t.lineComment, t.blockComment, t.docComment], color: 'var(--hl-comment)', fontStyle: 'italic' },
  { tag: [t.function(t.variableName), t.function(t.propertyName), t.definition(t.function(t.variableName))], color: 'var(--hl-title)' },
  { tag: [t.typeName, t.className, t.namespace, t.standard(t.variableName)], color: 'var(--hl-type)' },
  { tag: [t.propertyName, t.attributeName], color: 'var(--hl-attr)' },
  { tag: [t.heading], color: 'var(--heading)', fontWeight: '700' },
  { tag: [t.link, t.url], color: 'var(--link)', textDecoration: 'underline' },
  { tag: [t.emphasis], fontStyle: 'italic' },
  { tag: [t.strong], fontWeight: '700' },
  { tag: t.inserted, color: 'var(--hl-string)' },
  { tag: t.deleted, color: 'var(--hl-keyword)' },
  { tag: [t.changed, t.meta], color: 'var(--hl-comment)' },
  { tag: [t.invalid], color: '#e5534b' },
]);

const theme = EditorView.theme({
  '&': { color: 'var(--code-fg)', backgroundColor: 'var(--bg)', height: '100%', fontSize: '14px' },
  '.cm-scroller': { fontFamily: 'var(--font-mono)', lineHeight: '1.6' },
  '.cm-content': { caretColor: 'var(--accent)', padding: '8px 0 40px' },
  '.cm-cursor, .cm-dropCursor': { borderLeftColor: 'var(--accent)', borderLeftWidth: '2px' },
  '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, ::selection': {
    backgroundColor: 'color-mix(in srgb, var(--accent) 28%, transparent)',
  },
  '.cm-gutters': { backgroundColor: 'var(--bg)', color: 'var(--muted)', border: 'none', borderRight: '1px solid var(--border)' },
  '.cm-activeLineGutter': { backgroundColor: 'transparent', color: 'var(--accent)' },
  '.cm-activeLine': { backgroundColor: 'color-mix(in srgb, var(--code-bg) 70%, transparent)' },
  '.cm-matchingBracket': { backgroundColor: 'color-mix(in srgb, var(--accent) 25%, transparent)', outline: 'none' },
  '.cm-searchMatch': { backgroundColor: 'color-mix(in srgb, var(--hl-type) 35%, transparent)' },
  '.cm-panels': { backgroundColor: 'var(--code-bg)', color: 'var(--fg)', borderTop: '1px solid var(--border)' },
  '.cm-panel input, .cm-panel button': { fontSize: '14px' },
});

const MAX_CHARS = 1_000_000;

function create(parent, options) {
  const text = typeof options.text === 'string' ? options.text : '';
  if (text.length > MAX_CHARS) throw new RangeError('文档超过 100 万字符，无法编辑。');
  const wrap = new Compartment();
  let savedDoc = null;

  const state = EditorState.create({
    doc: text,
    extensions: [
      // Split only on LF so CRLF and standalone CR roundtrip byte-for-byte.
      EditorState.lineSeparator.of('\n'),
      EditorState.transactionFilter.of(tr => tr.newDoc.length <= MAX_CHARS ? tr : []),
      lineNumbers(),
      highlightActiveLineGutter(),
      highlightSpecialChars(),
      history(),
      drawSelection(),
      indentOnInput(),
      bracketMatching(),
      closeBrackets(),
      highlightActiveLine(),
      highlightSelectionMatches(),
      indentUnit.of('    '),
      syntaxHighlighting(highlight),
      theme,
      wrap.of(options.wrap ? EditorView.lineWrapping : []),
      keymap.of([...closeBracketsKeymap, ...defaultKeymap, ...searchKeymap, ...historyKeymap, indentWithTab]),
      (LANGS[options.lang] || (() => []))(),
    ],
  });

  const view = new EditorView({ state, parent });
  savedDoc = view.state.doc;

  // 文本插在光标处；成对符号光标落在中间
  function insert(text, cursorBack) {
    const { from, to } = view.state.selection.main;
    view.dispatch({
      changes: { from, to, insert: text },
      selection: { anchor: from + text.length - (cursorBack || 0) },
      scrollIntoView: true,
    });
    view.focus();
  }

  return {
    getText: () => view.state.doc.toString(),
    // 文档对象不可变：和上次保存时的对象比较，比逐字比较便宜得多
    isDirty: () => !view.state.doc.eq(savedDoc),
    // Native saves asynchronously; retain edits made after the saved snapshot.
    markSaved: (expectedText) => {
      savedDoc = typeof expectedText === 'string' ? Text.of(expectedText.split('\n')) : view.state.doc;
    },
    undo: () => { undo(view); view.focus(); },
    redo: () => { redo(view); view.focus(); },
    search: () => openSearchPanel(view),
    indent: () => { indentMore(view); view.focus(); },
    insert,
    setWrap: (on) => view.dispatch({ effects: wrap.reconfigure(on ? EditorView.lineWrapping : []) }),
    focus: () => view.focus(),
  };
}

window.YHEditor = { create, languages: Object.keys(LANGS) };