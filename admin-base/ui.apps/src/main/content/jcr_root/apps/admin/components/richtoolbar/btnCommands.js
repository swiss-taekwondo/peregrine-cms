function wrapSelectedRangeInBlock(range, editor, tagName) {
  if (range.collapsed) return false;
  const doc = editor.ownerDocument;
  // Handle a selection within a text node directly under contenteditable.
  const textNode = range.startContainer;
  if (
    textNode.nodeType === Node.TEXT_NODE &&
    textNode === range.endContainer &&
    textNode.parentNode === editor
  ) {
    const text = textNode.nodeValue || "";
    const before = text.slice(0, range.startOffset);
    const selected = text.slice(range.startOffset, range.endOffset);
    const after = text.slice(range.endOffset);
    if (!selected) return false;
    const wrapped = doc.createElement(tagName);
    wrapped.textContent = selected;
    const fragment = doc.createDocumentFragment();
    if (before) fragment.appendChild(doc.createTextNode(before));
    fragment.appendChild(wrapped);
    if (after) fragment.appendChild(doc.createTextNode(after));
    textNode.replaceWith(fragment);
    const nextRange = doc.createRange();
    nextRange.selectNodeContents(wrapped);
    const selection = doc.defaultView?.getSelection?.();
    if (selection) {
      selection.removeAllRanges();
      selection.addRange(nextRange);
    }
    return true;
  }
  const getBlock = (node) => {
    const element =
      node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
    return element?.closest(blockSelector) || null;
  };
  const block = getBlock(range.startContainer);
  if (
    !block ||
    getBlock(range.endContainer) !== block ||
    !editor.contains(block)
  ) {
    return false;
  }
  const beforeRange = doc.createRange();
  beforeRange.selectNodeContents(block);
  beforeRange.setEnd(range.startContainer, range.startOffset);
  const afterRange = doc.createRange();
  afterRange.selectNodeContents(block);
  afterRange.setStart(range.endContainer, range.endOffset);
  const before = beforeRange.cloneContents();
  const selected = range.cloneContents();
  const after = afterRange.cloneContents();
  const wrapped = doc.createElement(tagName);
  for (const attr of Array.from(block.attributes)) {
    wrapped.setAttribute(attr.name, attr.value);
  }
  wrapped.append(selected);
  const replacements = [];
  if (before.hasChildNodes()) {
    const beforeBlock = block.cloneNode(false);
    beforeBlock.append(before);
    replacements.push(beforeBlock);
  }
  replacements.push(wrapped);
  if (after.hasChildNodes()) {
    const afterBlock = block.cloneNode(false);
    afterBlock.append(after);
    replacements.push(afterBlock);
  }
  const fragment = doc.createDocumentFragment();
  replacements.forEach((node) => fragment.appendChild(node));
  block.replaceWith(fragment);
  const nextRange = doc.createRange();
  nextRange.selectNodeContents(wrapped);
  const selection = doc.defaultView?.getSelection?.();
  if (selection) {
    selection.removeAllRanges();
    selection.addRange(nextRange);
  }
  return true;
}

const blockSelector =
  'p, h1, h2, h3, h4, h5, h6, div:not([contenteditable="true"]';
function getSelectedBlocksFromRange(range, editor) {
  if (!range || !editor) return [];
  // A caret formats the block containing it.
  if (range.collapsed) {
    const container = range.startContainer;
    const element =
      container.nodeType === Node.ELEMENT_NODE
        ? container
        : container.parentElement;
    const block = element?.closest(blockSelector);
    return block && editor.contains(block) ? [block] : [];
  }
  // A text selection formats every block it intersects.
  return Array.from(editor.querySelectorAll(blockSelector)).filter((block) =>
    range.intersectsNode(block),
  );
}

export function createButtonCommandMethods() {
  return {
    formatBlock(tagName) {
      const normalizedTagName = String(tagName || "")
        .replace(/[<>]/g, "")
        .toUpperCase();
      if (!/^(P|H[1-6])$/.test(normalizedTagName)) return false;
      const range = this.lastInRteSelection;
      const textEditor = this.lastSelectedRte;

      if (!range.collapsed) {
        if (!wrapSelectedRangeInBlock(range, textEditor, normalizedTagName))
          return false;
        this.triggerInput();
        this.$nextTick(() => {
          this.$nextTick(() => {
            this.reconstructSelectionRelativeToRte();
          });
        });

        return true;
      }

      const blocks = getSelectedBlocksFromRange(range, textEditor);
      if (!blocks.length) return false;
      if (blocks.every((block) => block.tagName === normalizedTagName))
        return true;

      for (const block of blocks) {
        const replacement =
          textEditor.ownerDocument.createElement(normalizedTagName);

        for (const attr of Array.from(block.attributes)) {
          replacement.setAttribute(attr.name, attr.value);
        }

        while (block.firstChild) {
          replacement.appendChild(block.firstChild);
        }

        block.replaceWith(replacement);
      }

      this.triggerInput();
      this.$nextTick(() => {
        this.$nextTick(() => {
          this.reconstructSelectionRelativeToRte();
        });
      });
      return true;
    },

    // subnav
    togglePreview() {
      const view = $perAdminApp.getView();
      const current = get(view, "/state/tools/workspace/preview", null);
      $perAdminApp.stateAction("editPreview", current ? null : "preview");
    },

    // subnav
    previewInNewTab() {
      const view = $perAdminApp.getView();
      const page = get(view, "/pageView/path", null);
      window.open(page + ".html", "viewer");
    },

    updateFontSize(newSize) {
      const fontSize = `${newSize}px`;
      const range = this.lastInRteSelection;
      const textEditor = this.lastSelectedRte;

      function setFontSizeOfEl(element, fontSizeStr) {
        element.querySelectorAll('*[style*="font-size"]').forEach((element) => {
          if (element.tagName === "SPAN") {
            element.replaceWith(...element.childNodes);
          } else {
            element.style.removeProperty("font-size");
          }
        });
        element.style.fontSize = fontSizeStr;
      }

      const noHighlight =
        range.endContainer.isEqualNode(range.startContainer) &&
        range.endOffset === range.startOffset;
      if (noHighlight) {
        const parentQuery = "p, ul, ol, h1, h2, h3, h4, h5, h6";
        const fontSizeParent =
          range.startContainer.contentEditable === "true" &&
          range.startContainer?.children?.length > 0
            ? range.startContainer.children[0]
            : typeof range.startContainer.closest === "function"
              ? range.startContainer.closest(parentQuery)
              : range.startContainer.parentElement.closest(parentQuery);
        if (!textEditor.contains(fontSizeParent)) {
          console.warn(
            "Attempting to change fontsize of paragraph outside of richtext editor",
            fontSizeParent,
            range.startContainer,
          );
          return;
        }
        setFontSizeOfEl(fontSizeParent, fontSize);
        fontSizeParent.style.fontSize = fontSize;
        this.triggerInput();
        this.$nextTick(() => {
          this.$nextTick(() => {
            this.reconstructSelectionRelativeToRte();
          });
        });
        return;
      }

      if (range.startContainer.nodeType === Node.TEXT_NODE) {
        const newText = range.startContainer.splitText(range.startOffset);
        range.setStart(newText, 0);
      }
      if (range.endContainer.nodeType === Node.TEXT_NODE) {
        range.endContainer.splitText(range.endOffset);
        range.setEnd(range.endContainer, range.endContainer.length);
      }

      const onlySingleNode = range.startContainer.isEqualNode(
        range.endContainer,
      );
      if (onlySingleNode) {
        const isOnlyChildTextNodeOfExistingFontSizeSpan =
          range.startContainer.parentElement.childNodes.length === 1 &&
          range.startContainer.parentElement.nodeName === "SPAN";
        if (
          range.startContainer.nodeType === Node.TEXT_NODE &&
          !isOnlyChildTextNodeOfExistingFontSizeSpan
        ) {
          const span = document.createElement("span");
          span.style.fontSize = fontSize;
          range.surroundContents(span);
          this.triggerInput();
          this.selectNodes([span]);
          this.$nextTick(() => {
            this.$nextTick(() => {
              this.reconstructSelectionRelativeToRte();
            });
          });
        } else {
          setFontSizeOfEl(range.startContainer, fontSize);
          this.triggerInput();
        }
        return;
      }

      const nodeRanges = this.wrapTextNodesInRange(range, fontSize);

      textEditor
        .querySelectorAll(
          'span[style*="font-size"]:has(> span[style*="font-size"])',
        )
        .forEach((span) => {
          if (span.childNodes.length === 1) {
            span.replaceWith(span.childNodes[0]);
          }
        });

      this.triggerInput();
      this.selectNodes(nodeRanges);
      this.$nextTick(() => {
        this.$nextTick(() => {
          this.reconstructSelectionRelativeToRte();
        });
      });
    },

    undo() {
      if (!this.performingUndoRedo && this.undoStack.length) {
        this.performingUndoRedo = true;
        this.redoStack.push(this.editorContent);
        const content = this.undoStack.pop();
        if (!content) {
          return console.warn("Failed to undo, no content");
        }
        this.setEditorContent(content);
        this.$nextTick(() => {
          this.performingUndoRedo = false;
          $perAdminApp.action(this, "textEditorWriteToModel");
        });

        return;
      }
    },

    redo() {
      if (!this.performingUndoRedo && this.redoStack.length) {
        this.performingUndoRedo = true;
        this.undoStack.push(this.editorContent);
        const content = this.redoStack.pop();
        if (!content) {
          return console.warn("Failed to redo, no content");
        }
        this.setEditorContent(content);
        this.$nextTick(() => {
          this.performingUndoRedo = false;
          $perAdminApp.action(this, "textEditorWriteToModel");
        });
      }
    },

    insertIcon(imgPath) {
      const range = this.lastInRteSelection;
      range.deleteContents();
      const fragment = range.createContextualFragment(
        `<img class="peregrine-icon" style="font-size: inherit; display: inline; width: auto; height: 1em; vertical-align: -0.125em;" src="${imgPath}"></img>`,
      );
      const lastChild = fragment.lastChild;
      range.insertNode(fragment);
      range.setStartAfter(lastChild);
      range.collapse(true);
      this.triggerInput();
    },

    insertSpecialCharacter(charCode) {
      const range = this.lastInRteSelection;
      range.deleteContents();
      const fragment = range.createContextualFragment(`&#${charCode};`);
      const lastChild = fragment.lastChild;
      range.insertNode(fragment);
      range.setStartAfter(lastChild);
      range.collapse(true);
      this.triggerInput();
    },

    wrapTextNodesInRange(range, fontSize) {
      const textNodes = [];
      const walker = document.createTreeWalker(
        range.commonAncestorContainer,
        NodeFilter.SHOW_TEXT,
        {
          acceptNode: (node) => {
            if (!node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
            const nodeRange = document.createRange();
            nodeRange.selectNodeContents(node);
            return range.intersectsNode(node)
              ? NodeFilter.FILTER_ACCEPT
              : NodeFilter.FILTER_REJECT;
          },
        },
      );
      let node;
      while ((node = walker.nextNode())) {
        textNodes.push(node);
      }
      const fontSizeNodes = [];
      for (let i = 0; i < textNodes.length; i++) {
        const textNode = textNodes[i];
        const nodeRange = document.createRange();
        nodeRange.selectNodeContents(textNode);
        if (
          textNode === range.startContainer ||
          textNode.contains(range.startContainer)
        ) {
          nodeRange.setStart(range.startContainer, range.startOffset);
        }
        if (
          textNode === range.endContainer ||
          textNode.contains(range.endContainer)
        ) {
          nodeRange.setEnd(range.endContainer, range.endOffset);
        }
        const existingSpan =
          textNode.parentElement.tagName === "SPAN" &&
          textNode.parentElement.childNodes.length === 1
            ? textNode.parentElement
            : null;
        if (existingSpan) {
          existingSpan.style.fontSize = fontSize;
          fontSizeNodes.push(existingSpan);
        } else {
          const span = document.createElement("span");
          span.style.fontSize = fontSize;
          nodeRange.surroundContents(span);
          fontSizeNodes.push(span);
        }
      }
      return fontSizeNodes;
    },
  };
}
