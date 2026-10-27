const getOffset = (doc, rte, node, offset) => {
  const prefix = doc.createRange()
  prefix.selectNodeContents(rte)
  prefix.setEnd(node, offset)
  return prefix.toString().length
}

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
      this.lastInRteSelectionRelativeToRte = {
        start: getOffset(doc, rte, range.startContainer, range.startOffset),
        end: getOffset(doc, rte, range.endContainer, range.endOffset),
      }
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
      nodeArray[0].ownerDocument.dispatchEvent(new Event('selectionchange'))
    },

    // Updating inline content triggers a state update which causes inline RTE to re-render, deleting previous nodes and populating with new ones set by innerHTML
    // We store some selection data relative to the RTE parent so we can restore that if needed using this.
    reconstructSelectionRelativeToRte() {
	    const saved = this.lastInRteSelectionRelativeToRte
	    const rte = this.lastSelectedRte
	    const doc = rte.ownerDocument
	    
	    if (rte && saved) {
	      const pointAt = (targetOffset, isStart) => {
	        const walker = doc.createTreeWalker(rte, NodeFilter.SHOW_TEXT)
	        let remaining = targetOffset
	        let node
	    
	        while ((node = walker.nextNode())) {
						if (isStart) {
	            if (remaining < node.length) {
	              return { node, offset: remaining }
	            }
						} else {
              if (remaining <= node.length) {
                return { node, offset: remaining }
              }
						}
	          remaining -= node.length
	        }
	    
	        return { node: rte, offset: rte.childNodes.length }
	      }
	    
	      const start = pointAt(saved.start, true)
	      const end = pointAt(saved.end)
	      const restoredRange = doc.createRange()
	    
				const shouldSelectParentElement = start.node.isEqualNode(end.node) && start.node.nodeType === Node.TEXT_NODE && start.node.parentElement.childNodes.length === 1
				if (shouldSelectParentElement) {
          restoredRange.setStart(start.node.parentElement, 0)
          restoredRange.setEnd(end.node.parentElement, 1)
				} else {
	        restoredRange.setStart(start.node, start.offset)
	        restoredRange.setEnd(end.node, end.offset)
				}
	    
	      const selection = doc.getSelection()
	      selection.removeAllRanges()
	      selection.addRange(restoredRange)
        doc.dispatchEvent(new Event('selectionchange'))
        console.log('restored relative range')
	    }
    },

  }
}
