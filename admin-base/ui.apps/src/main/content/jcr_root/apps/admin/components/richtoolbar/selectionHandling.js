const getOffset = (doc, rte, node, offset) => {
  const prefix = doc.createRange();
  prefix.selectNodeContents(rte);
  prefix.setEnd(node, offset);
  return prefix.toString().length;
};

export function createSelectionMethods() {
  return {
    // Sidebar rte handles selection/range storing and passes it to toolbar
    handleSelectionChange(event) {
      try {
        const doc = event.target.ownerDocument || event.target;
        const inIframe =
          doc.defaultView.parent.document !== doc &&
          !doc.defaultView.parent.Cypress;
        const selection = doc.getSelection();
        if (selection.rangeCount < 1) {
          // console.warn('debug no ranges', selection)
          return;
        }
        const range = doc.getSelection().getRangeAt(0);
        const commonAncestorEl = range.commonAncestorContainer.closest
          ? range.commonAncestorContainer
          : range.commonAncestorContainer.parentElement;
        const rte = commonAncestorEl.closest(".inline-edit");

        if (rte) {
          this.lastInRteSelection = range.cloneRange();
          this.lastInRteSelectionRelativeToRte = {
            start: getOffset(doc, rte, range.startContainer, range.startOffset),
            end: getOffset(doc, rte, range.endContainer, range.endOffset),
          };
          // for default fontsize, should always get set when clicking rte
          if (inIframe) this.lastRteSelectionInlineOnly = range.cloneRange();
          this.lastSelectedRte = rte;

          // fontsize
          const fontSizeSpan = commonAncestorEl.closest(
            '.inline-edit [style*="font-size"]',
          );
          if (fontSizeSpan) {
            this.currentFontSize = parseInt(fontSizeSpan.style.fontSize);
          } else {
            const getFontSizeFrom = this.lastRteSelectionInlineOnly
              ?.commonAncestorContainer
              ? (this.lastRteSelectionInlineOnly.commonAncestorContainer.closest
                  ? this.lastRteSelectionInlineOnly.commonAncestorContainer
                  : this.lastRteSelectionInlineOnly.commonAncestorContainer
                      .parentElement
                ).closest(".inline-edit")
              : document
                  .querySelector("#editview")
                  .contentDocument.querySelector(
                    '[data-editing-component] [contenteditable="true"][data-per-inline$=".text"]',
                  ) ||
                document
                  .querySelector("#editview")
                  .contentDocument.querySelector("[data-editing-component]"); // fallback if rte was only ever touched via sidebar
            this.currentFontSize = parseInt(
              window.getComputedStyle(getFontSizeFrom).fontSize,
            );
          }
        } else {
          // console.warn('handleselectionchange: no rte')
        }

        this.toolbarDisabled = !commonAncestorEl.closest(
          ".inline-edit, .richtoolbar",
        );
      } catch (error) {
        console.error("error handling selection", error);
      }
    },

    restoreEditorSelection() {
      const ownerDocument =
        this.lastInRteSelection.startContainer.ownerDocument;
      const selection = ownerDocument.getSelection();
      selection.removeAllRanges();
      selection.addRange(this.lastInRteSelection);
    },

    selectElementContents(element) {
      const doc = element.ownerDocument;
      const range = doc.createRange();
      range.selectNodeContents(element);
      const selection = doc.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
    },

    selectNodes(nodeArray) {
      const selection = nodeArray[0].ownerDocument.getSelection();
      const reselectRange = nodeArray[0].ownerDocument.createRange();
      reselectRange.setStart(nodeArray[0], 0);
      reselectRange.setEnd(
        nodeArray[nodeArray.length - 1],
        nodeArray[nodeArray.length - 1].childNodes.length,
      );
      selection.removeAllRanges();
      selection.addRange(reselectRange.cloneRange());
      nodeArray[0].ownerDocument.dispatchEvent(new Event("selectionchange"));
    },

    // Updating inline content triggers a state update which causes inline RTE to re-render, deleting previous nodes and populating with new ones set by innerHTML
    // We store some selection data relative to the RTE parent so we can restore that if needed using this.
    reconstructSelectionRelativeToRte() {
      const saved = this.lastInRteSelectionRelativeToRte;
      const rte = this.lastSelectedRte;
      const doc = rte.ownerDocument;
      const inIframe =
        doc.defaultView.parent.document !== doc &&
        !doc.defaultView.parent.Cypress;

      if (rte && saved) {
        const pointAt = (targetOffset, isStart) => {
          const walker = doc.createTreeWalker(rte, NodeFilter.SHOW_TEXT);
          let remaining = targetOffset;
          let node;

          while ((node = walker.nextNode())) {
            if (isStart) {
              if (remaining < node.length) {
                return { node, offset: remaining };
              }
            } else {
              if (remaining <= node.length) {
                return { node, offset: remaining };
              }
            }
            remaining -= node.length;
          }

          return { node: rte, offset: rte.childNodes.length };
        };

        const start = pointAt(saved.start, true);
        const end = pointAt(saved.end);
        const restoredRange = doc.createRange();

        restoredRange.setStart(start.node, start.offset);
        restoredRange.setEnd(end.node, end.offset);

        const selection = doc.getSelection();
        selection.removeAllRanges();
        selection.addRange(restoredRange);
        if (inIframe) document.querySelector("#editview").focus();
        doc.dispatchEvent(new Event("selectionchange"));
      }
    },

    rangeMatchesSelector(selector) {
      const range = this.lastInRteSelection;
      if (!range) return null;
      const doc = range.startContainer.ownerDocument;
      const walker = doc.createTreeWalker(
        range.commonAncestorContainer,
        NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT,
      );
      const findMatch = (node) => {
        let element = node.nodeType === 1 ? node : node.parentElement;
        while (element) {
          if (element.matches(selector)) return element;
          if (element.matches(".inline-edit")) break;
          element = element.parentElement;
        }
        return null;
      };
      if (range.collapsed) return findMatch(range.startContainer);
      let node = walker.currentNode;
      while (node) {
        if (range.intersectsNode(node)) {
          const match = findMatch(node);
          if (match) return match;
        }
        node = walker.nextNode();
      }
      return null;
    },
  };
}
