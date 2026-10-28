function wrapSelectedRangeInBlock(range, editor, tagName) {
  if (range.collapsed) return false
  const doc = editor.ownerDocument
  // Handle a selection within a text node directly under contenteditable.
  const textNode = range.startContainer
  if (
    textNode.nodeType === Node.TEXT_NODE &&
    textNode === range.endContainer &&
    textNode.parentNode === editor
  ) {
    const text = textNode.nodeValue || ''
    const before = text.slice(0, range.startOffset)
    const selected = text.slice(range.startOffset, range.endOffset)
    const after = text.slice(range.endOffset)
    if (!selected) return false
    const wrapped = doc.createElement(tagName)
    wrapped.textContent = selected
    const fragment = doc.createDocumentFragment()
    if (before) fragment.appendChild(doc.createTextNode(before))
    fragment.appendChild(wrapped)
    if (after) fragment.appendChild(doc.createTextNode(after))
    textNode.replaceWith(fragment)
    const nextRange = doc.createRange()
    nextRange.selectNodeContents(wrapped)
    const selection = doc.defaultView?.getSelection?.()
    if (selection) {
      selection.removeAllRanges()
      selection.addRange(nextRange)
    }
    return true
  }
  const getBlock = (node) => {
    const element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement
    return element?.closest(blockSelector) || null
  }
  const block = getBlock(range.startContainer)
  if (!block || getBlock(range.endContainer) !== block || !editor.contains(block)) {
    return false
  }
  const beforeRange = doc.createRange()
  beforeRange.selectNodeContents(block)
  beforeRange.setEnd(range.startContainer, range.startOffset)
  const afterRange = doc.createRange()
  afterRange.selectNodeContents(block)
  afterRange.setStart(range.endContainer, range.endOffset)
  const before = beforeRange.cloneContents()
  const selected = range.cloneContents()
  const after = afterRange.cloneContents()
  const wrapped = doc.createElement(tagName)
  for (const attr of Array.from(block.attributes)) {
    wrapped.setAttribute(attr.name, attr.value)
  }
  wrapped.append(selected)
  const replacements = []
  if (before.hasChildNodes()) {
    const beforeBlock = block.cloneNode(false)
    beforeBlock.append(before)
    replacements.push(beforeBlock)
  }
  replacements.push(wrapped)
  if (after.hasChildNodes()) {
    const afterBlock = block.cloneNode(false)
    afterBlock.append(after)
    replacements.push(afterBlock)
  }
  const fragment = doc.createDocumentFragment()
  replacements.forEach((node) => fragment.appendChild(node))
  block.replaceWith(fragment)
  const nextRange = doc.createRange()
  nextRange.selectNodeContents(wrapped)
  const selection = doc.defaultView?.getSelection?.()
  if (selection) {
    selection.removeAllRanges()
    selection.addRange(nextRange)
  }
  return true
}

const blockSelector = 'p, h1, h2, h3, h4, h5, h6, div:not([contenteditable="true"]'
function getSelectedBlocksFromRange(range, editor) {
  if (!range || !editor) return []
  // A caret formats the block containing it.
  if (range.collapsed) {
    const container = range.startContainer
    const element = container.nodeType === Node.ELEMENT_NODE
      ? container
      : container.parentElement
    const block = element?.closest(blockSelector)
    return block && editor.contains(block) ? [block] : []
  }
  // A text selection formats every block it intersects.
  return Array.from(editor.querySelectorAll(blockSelector))
    .filter((block) => range.intersectsNode(block))
}


export function createButtonCommandMethods(vm) {
  return {

  formatBlock(tagName) {
    const normalizedTagName = String(tagName || '').replace(/[<>]/g, '').toUpperCase()
    if (!/^(P|H[1-6])$/.test(normalizedTagName)) return false
    const range = vm.lastInRteSelection;
    const textEditor = vm.lastSelectedRte;

    if (!range.collapsed) {
      if (!wrapSelectedRangeInBlock(range, textEditor, normalizedTagName)) return false
      vm.triggerInput()
      vm.$nextTick(() => {
	        vm.$nextTick(() => {
	           vm.reconstructSelectionRelativeToRte()
	        })
      })
      
      return true
    }

    const blocks = getSelectedBlocksFromRange(range, textEditor)
    if (!blocks.length) return false
    if (blocks.every((block) => block.tagName === normalizedTagName)) return true

    for (const block of blocks) {
      const replacement = textEditor.ownerDocument.createElement(normalizedTagName)

      for (const attr of Array.from(block.attributes)) {
        replacement.setAttribute(attr.name, attr.value)
      }

      while (block.firstChild) {
        replacement.appendChild(block.firstChild)
      }
    
      block.replaceWith(replacement)
    }

    vm.triggerInput()
    vm.$nextTick(() => {
      vm.$nextTick(() => {
        vm.reconstructSelectionRelativeToRte()
      })
    })
    return true
  },









  // subnav
  togglePreview() {
    const view = $perAdminApp.getView()
    const current = get(view, '/state/tools/workspace/preview', null)
    $perAdminApp.stateAction('editPreview', current ? null : 'preview')
  },

  // subnav
  previewInNewTab() {
    const view = $perAdminApp.getView()
    const page = get(view, '/pageView/path', null)
    window.open(page + '.html', 'viewer')
  },

  updateFontSize(newSize) {
    const fontSize = `${newSize}px`
    const range = vm.lastInRteSelection;
    console.log('updatefontsize range:', range.commonAncestorContainer, range.startOffset, range.endOffset, range)
    const textEditor = vm.lastSelectedRte;

    function setFontSizeOfEl(element, fontSizeStr) {
      element.querySelectorAll('*[style*="font-size"]').forEach(element => {
        if (element.tagName === 'SPAN') {
          element.replaceWith(...element.childNodes)
        } else {
          element.style.removeProperty('font-size')
        }
      })
      element.style.fontSize = fontSizeStr
    }

    const noHighlight = range.endContainer.isEqualNode(range.startContainer) && range.endOffset === range.startOffset
    if (noHighlight) {
    console.log('noHighlight', noHighlight)
      const parentQuery = 'p, ul, ol, h1, h2, h3, h4, h5, h6'
      const fontSizeParent = (range.startContainer.contentEditable === 'true' && range.startContainer?.children?.length > 0)
        ? range.startContainer.children[0]
        : typeof range.startContainer.closest === 'function'
          ? range.startContainer.closest(parentQuery)
          : range.startContainer.parentElement.closest(parentQuery)
      if (!textEditor.contains(fontSizeParent)) {
        console.warn('Attempting to change fontsize of paragraph outside of richtext editor', fontSizeParent, range.startContainer)
        return
      }
      setFontSizeOfEl(fontSizeParent, fontSize)
      fontSizeParent.style.fontSize = fontSize
      vm.triggerInput()
      vm.$nextTick(() => {
	        vm.$nextTick(() => {
	           vm.reconstructSelectionRelativeToRte()
	        })
      })
      return
    }

    if (range.startContainer.nodeType === Node.TEXT_NODE) {
      const newText = range.startContainer.splitText(range.startOffset)
      range.setStart(newText, 0)
    }
    if (range.endContainer.nodeType === Node.TEXT_NODE) {
      range.endContainer.splitText(range.endOffset)
      range.setEnd(range.endContainer, range.endContainer.length)
    }

    const onlySingleNode = range.startContainer.isEqualNode(range.endContainer)
    if (onlySingleNode) {
      const isOnlyChildTextNodeOfExistingFontSizeSpan = range.startContainer.parentElement.childNodes.length === 1 && range.startContainer.parentElement.nodeName === 'SPAN'
      console.log('isOnlyChildTextNodeOfExistingFontSizeSpan', isOnlyChildTextNodeOfExistingFontSizeSpan, range.cloneRange())
      if (range.startContainer.nodeType === Node.TEXT_NODE && !isOnlyChildTextNodeOfExistingFontSizeSpan) {
        const span = document.createElement('span')
        span.style.fontSize = fontSize
        range.surroundContents(span)
        vm.triggerInput()
        vm.selectNodes([span])
        vm.$nextTick(() => {
	        vm.$nextTick(() => {
	           vm.reconstructSelectionRelativeToRte()
	        })
        })
      } else {
        setFontSizeOfEl(range.startContainer, fontSize)
        vm.triggerInput()
      }
      return
    }

    const nodeRanges = vm.wrapTextNodesInRange(range, fontSize)

    textEditor.querySelectorAll('span[style*="font-size"]:has(> span[style*="font-size"])').forEach(span => {
      if (span.childNodes.length === 1) {
        span.replaceWith(span.childNodes[0])
      }
    })

    vm.selectNodes(nodeRanges)
    vm.$nextTick(() => {
      vm.$nextTick(() => {
          vm.reconstructSelectionRelativeToRte()
      })
    })
  },

  undo() {
    if (vm.historyIndex > 0) {
      vm.isUndoing = true
      vm.historyIndex--
      window.dispatchEvent(new CustomEvent('richtoolbar:history', {
        detail: { historyIndex: vm.historyIndex, historyLength: vm.historyStack.length },
      }))
      const content = vm.historyStack[vm.historyIndex]
      const container = vm.getInlineContainer()
      const doc = vm.getInlineDoc()
      const savedSel = container && doc ? saveSelection(container, doc) : null
      const textEditor = vm.$el.closest('.text-editor-wrapper')
        ? vm.$el.closest('.text-editor-wrapper').querySelector('.text-editor')
        : vm.$el.nextElementSibling
      textEditor.innerHTML = content
      $perAdminApp.action(this, 'textEditorWriteToModel')
      vm.$nextTick(() => {
        vm.isUndoing = false
        if (savedSel && container && doc) {
          // restoreSelection(container, savedSel, doc)
        }
        vm.pingRichToolbar()
      })
    }
  },

  redo() {
    if (vm.historyIndex < vm.historyStack.length - 1) {
      vm.isUndoing = true
      vm.historyIndex++
      window.dispatchEvent(new CustomEvent('richtoolbar:history', {
        detail: { historyIndex: vm.historyIndex, historyLength: vm.historyStack.length },
      }))
      const content = vm.historyStack[vm.historyIndex]
      const container = vm.getInlineContainer()
      const doc = vm.getInlineDoc()
      const savedSel = container && doc ? saveSelection(container, doc) : null
      const textEditor = vm.$el.closest('.text-editor-wrapper')
        ? vm.$el.closest('.text-editor-wrapper').querySelector('.text-editor')
        : vm.$el.nextElementSibling
      textEditor.innerHTML = content
      $perAdminApp.action(this, 'textEditorWriteToModel')
      vm.$nextTick(() => {
        vm.isUndoing = false
        if (savedSel && container && doc) {
          // restoreSelection(container, savedSel, doc)
        }
        vm.pingRichToolbar()
      })
    }
  },












  
  }
}
