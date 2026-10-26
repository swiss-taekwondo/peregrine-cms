export function createSelectionMethods() {
  return {
  // Sidebar rte handles selection/range storing and passes it to toolbar
  handleSelectionChange(event) {
    try{
    const doc = event.target.ownerDocument || event.target
    const inIframe = doc.defaultView.top.document !== doc;
    const selection = doc.getSelection()
    if (selection.rangeCount < 1) {
      console.warn('debug no ranges', selection)
      return
    }
    const range = doc.getSelection().getRangeAt(0)
    console.log('handleselectionchange', range.commonAncestorContainer, range.startOffset, range.endOffset, this.lastInRteSelection?.commonAncestorContainer, this.lastInRteSelection?.startOffset, this.lastInRteSelection?.endOffset)
    const commonAncestorEl = range.commonAncestorContainer.closest ? range.commonAncestorContainer : range.commonAncestorContainer.parentElement
    const rte = commonAncestorEl.closest('.inline-edit')

    if (rte) {
      this.lastInRteSelection = range.cloneRange()
      // for default fontsize, should always get set when clicking rte
      if (inIframe) this.lastRteSelectionInlineOnly = range.cloneRange()
      this.lastSelectedRte = rte;

      // fontsize
      const fontSizeSpan = commonAncestorEl.closest('.inline-edit [style*="font-size"]')
      if (fontSizeSpan) {
        this.currentFontSize = parseInt(fontSizeSpan.style.fontSize)
        console.log('fontsizespan:', this.currentFontSize)
      } else {
        const getFontSizeFrom =
          this.lastRteSelectionInlineOnly?.commonAncestorContainer ? (this.lastRteSelectionInlineOnly.commonAncestorContainer.closest ? this.lastRteSelectionInlineOnly.commonAncestorContainer : this.lastRteSelectionInlineOnly.commonAncestorContainer.parentElement).closest('.inline-edit') :
          document.querySelector('#editview').contentDocument.querySelector('[data-editing-component]') // fallback if rte was only ever touched via sidebar
        this.currentFontSize = parseInt(window.getComputedStyle(getFontSizeFrom).fontSize)
        console.log('fontsize base:', this.currentFontSize)
      }
    } else {
      console.warn('handleselectionchange: no rte')
    }

    this.toolbarDisabled = !commonAncestorEl.closest('.inline-edit, .richtoolbar')
    // this.pingRichToolbar()


    } catch (error) {
      console.error('error handling selection', error)
    }
  },

    restoreEditorSelection() {
      const ownerDocument = this.lastInRteSelection.startContainer.ownerDocument
      const selection = ownerDocument.getSelection()
      selection.removeAllRanges()
      selection.addRange(this.lastInRteSelection)
    },

    selectElementContents(element) {
      const doc = element.ownerDocument
      const range = doc.createRange();
      range.selectNodeContents(element);
      const selection = doc.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
      
    },

    saveCurrentSelection() {
      
    },

    selectNodes(nodeArray) {
      const selection = nodeArray[0].ownerDocument.getSelection()
      const reselectRange = nodeArray[0].ownerDocument.createRange()
      reselectRange.setStart(nodeArray[0], 0)
      reselectRange.setEnd(nodeArray[nodeArray.length - 1], nodeArray[nodeArray.length - 1].childNodes.length)
      selection.removeAllRanges()
      selection.addRange(reselectRange.cloneRange())
      // nodeArray[0].ownerDocument.dispatchEvent(new Event('selectionchange'))
      
      setTimeout(() => {
      
      nodeArray[0].ownerDocument.dispatchEvent(new Event('selectionchange'))
      console.log('reselectRange', reselectRange, reselectRange.commonAncestorContainer, reselectRange.startOffset, reselectRange.endOffset, nodeArray[0].ownerDocument.getSelection().getRangeAt(0).commonAncestorContainer)
      
      }, 0)
    },

  }
}
