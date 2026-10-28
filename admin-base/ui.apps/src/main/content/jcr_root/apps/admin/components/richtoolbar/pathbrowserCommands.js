import {IconLib, PathBrowser} from '../../../../../../js/constants'
import {fallbackMediaText, resolveMediaText} from '../../../js/mediaText'

// TODO: split onLink/ImageSelect functions, they are giant if blocks, should be different functions.

export function createPathbrowserCommandMethods() {
  return {

  onBrowserSelect() {
    this.browser.open = false

    this.$nextTick(() => {
      if (['editLink', 'insertLink'].includes(this.param.cmd)) {
        this.onLinkSelect()
        return
      } else if (['insertImage', 'editImage', 'editIcon'].includes(this.param.cmd)) {
        this.onImageSelect().catch((err) => console.error(err))
        return
      }

      this.execCmd(this.param.cmd, this.param.value)
      this.param.cmd = null
      this.param.value = null
      this.param.anchor = null
      this.browser.path.selected = null
      this.browser.linkTitle = null
      this.browser.img.width = null
      this.browser.img.height = null
    })
  },

    // ====================
    // Links
    // ====================

  removeLink(vm) {
    this.lastSelectedRte.ownerDocument.execCommand('unlink', false, null)
    this.triggerInput()
  },

  insertLink() {
    const startPath = this.roots.pages
    this.param.cmd = 'insertLink'
    this.param.anchor = null
    this.browser.header = this.$i18n('Insert Link')
    this.browser.path.current = startPath
    this.browser.path.selected = null
    this.browser.withLinkTab = true
    this.browser.newWindow = false
    this.browser.rel = true
    this.browser.linkTitle = ''
    this.browser.type = PathBrowser.Type.PAGE
    this.startBrowsing(startPath)
  },

  editLink() {
    const anchor = this.rangeMatchesSelector('a')
    const href = anchor.getAttribute('href') || ''
    const title = anchor.getAttribute('title') || ''
    const target = anchor.getAttribute('target') || ''
    const rel = anchor.getAttribute('rel') || ''

    let startPath = this.roots.pages
    this.param.cmd = 'editLink'
    this.param.anchor = anchor
    this.browser.header = this.$i18n('Edit Link')
    this.browser.withLinkTab = true
    this.browser.newWindow = target === '_blank'
    this.browser.rel = rel.includes('noopener')
    this.browser.linkTitle = title
    this.browser.type = PathBrowser.Type.PAGE

    let resolvedHref = href

    if (/^https?:\/\//.test(href)) {
      try {
        const url = new URL(href)
        if (url.hostname === window.location.hostname) {
          resolvedHref = url.pathname + url.search + url.hash
        }
      } catch (e) { /* Do nothing */ }
    }

    const isExternal = /^https?:\/\//.test(resolvedHref)
    if (isExternal) {
      this.browser.path.selected = resolvedHref
      startPath = this.roots.pages || '/'
    } else {
      // Normalize only the repository path; keep query strings and fragments verbatim.
      const suffixIndex = resolvedHref.search(/[?#]/)
      const pathname = suffixIndex < 0 ? resolvedHref : resolvedHref.slice(0, suffixIndex)
      const suffix = suffixIndex < 0 ? '' : resolvedHref.slice(suffixIndex)
      let hrefPath = pathname.replace(/\.html$/, '')
      if (!hrefPath.startsWith('/content/')) {
        hrefPath = (this.roots.pages || '') + hrefPath
      }
      const lastSlash = hrefPath.lastIndexOf('/')
      startPath = lastSlash > 0 ? hrefPath.substring(0, lastSlash) : (this.roots.pages || '/')
      this.browser.path.selected = hrefPath + suffix
    }
    this.browser.path.current = startPath
    this.startBrowsing(startPath)
    this.startBrowsing(startPath)
  },

  onLinkSelect() {
    const typeLC = this.browser.type?.toLowerCase()
    const isAssetOrFile = typeLC === PathBrowser.Type.ASSET || typeLC === 'file'
    let href = this.browser.path.selected || ''
    if (!href.trim()) {
      console.warn('no href specified in onLinkSelect')
      $perAdminApp.toast("Invalid link", "warn", 3000)
      return
    }
    try {
      new URL(href, window.location.origin)
    } catch (e) {
      console.warn('invalid href specified in onLinkSelect', href, e)
      $perAdminApp.toast("Invalid link", "warn", 3000)
      return
    }

    const isPageType = this.browser.type?.toLowerCase() === PathBrowser.Type.PAGE
    if (href.startsWith('/') && isPageType) {
      const url = new URL(href, "https://placeholder.invalid");
      if (!url.pathname.endsWith('.html')) {
        url.pathname += ".html";
      }
      href = url.pathname + url.search + url.hash;
    }

    const applyLinkAttributes = (link) => {
      link.setAttribute('href', href)
      if (this.browser.linkTitle) {
        link.setAttribute('title', this.browser.linkTitle)
      } else {
        link.removeAttribute('title')
      }
      if (isAssetOrFile) {
        link.setAttribute('download', '')
        link.removeAttribute('target')
        link.removeAttribute('rel')
      } else {
        link.removeAttribute('download')
        link.setAttribute('target', this.browser.newWindow ? '_blank' : '_self')
        link.setAttribute('rel', this.browser.rel ? 'noopener noreferrer' : '')
      }
    }

    if (this.param.cmd === 'insertLink') {
      const range = this.lastInRteSelection;
      const link = range.startContainer.ownerDocument.createElement('a')
      applyLinkAttributes(link)
      const textEditor = this.lastSelectedRte;

      let rangeIsInListItem = false
      if (!range.startContainer.isEqualNode(range.endContainer)) {
        const listItems = Array.from(textEditor.querySelectorAll('li')).reverse()
        for (const li of listItems) {
          if (range.intersectsNode(li)) {
            rangeIsInListItem = li
            break
          }
        }
      }

      if (rangeIsInListItem) {
        range.setStart(rangeIsInListItem, 0)
        if (rangeIsInListItem.isEqualNode(range.endContainer)) {
          range.setEnd(range.endContainer, range.endOffset)
        } else {
          range.setEnd(rangeIsInListItem, rangeIsInListItem.childNodes.length)
        }
      }

      link.append(range.extractContents())
      if (link.textContent.trim().length < 1 && link.children.length === 0) {
        link.textContent = href
      }
      range.insertNode(link)
      this.triggerInput()
    } else { // param.cmd === editLink
      let anchor = this.param.anchor
      applyLinkAttributes(anchor)
      this.param.anchor = null
      this.triggerInput()
    }
  },


  // ====================
  // Images
  // ====================

  insertImage() {
    this.param.cmd = 'insertImage'
    this.browser.header = this.$i18n('Insert Image')
    this.browser.path.current = this.roots.assets
    this.browser.withLinkTab = true
    this.browser.newWindow = undefined
    this.browser.type = PathBrowser.Type.ASSET
    this.browser.path.suffix = ''
    this.browser.img.width = null
    this.browser.img.height = null
    this.browser.img.objectFit = null
    this.browser.path.selected = '';
    // this.selection.doc = doc
    // this.selection.container = container
    this.startBrowsing()
  },

  async editImage(vm = this, target) {
    // vm handles inline double click
    const title = target.getAttribute('title')
    const src = target.getAttribute('src')
    const defaultText = title || target.getAttribute('alt') || await this.getDefaultMediaText(src)
    const fallbackText = fallbackMediaText(src)
    const srcArr = src.split('/')
    const img = {
      width: target.style.width ? parseInt(target.style.width) : null,
      height: target.style.height ? parseInt(target.style.height) : null,
      objectFit: target.style.objectFit || null,
    }
    vm.param.cmd = 'editImage'
    vm.browser.header = vm.$i18n('Edit Image')
    vm.browser.path.selected = srcArr.join('/')
    srcArr.pop()
    vm.browser.path.current = srcArr.join('/')
    vm.browser.withLinkTab = true
    vm.browser.newWindow = undefined
    vm.browser.type = PathBrowser.Type.ASSET
    vm.browser.linkTitle = defaultText
    vm.browser.mediaSourcePath = src
    vm.browser.mediaSourceText = defaultText || ''
    vm.browser.mediaSourceFallback = fallbackText
    vm.browser.element = target
    vm.browser.img.width = img.width
    vm.browser.img.height = img.height
    vm.browser.img.objectFit = img.objectFit
    vm.startBrowsing()
  },

  async editIcon(target) {
    const alt = target.getAttribute('alt') || await this.getDefaultMediaText(target.getAttribute('src') || '')
    const src = target.getAttribute('src') || ''
    const img = {
      width: target.style.width ? parseInt(target.style.width) : 20,
      height: target.style.height ? parseInt(target.style.height) : 20,
      objectFit: target.style.objectFit || null,
    }
    this.param.cmd = 'editIcon'
    this.browser.header = this.$i18n('Edit Icon')
    this.browser.withLinkTab = false
    this.browser.withImageTab = true
    this.browser.path.selected = src
    this.browser.path.current = src
    this.browser.type = PathBrowser.Type.ASSET
    this.browser.altText = alt
    this.browser.linkTitle = undefined
    this.browser.newWindow = undefined
    this.browser.element = target
    this.browser.img.width = img.width
    this.browser.img.height = img.height
    this.browser.img.objectFit = img.objectFit
    this.saveSelection()
    this.selection.restore = true
    this.startBrowsing(src.substring(0, src.lastIndexOf('/')))
  },


  async onImageSelect() {
    if (this.param.cmd === 'editIcon') {
      const imgEl = this.browser.element
      console.log('imgEl', imgEl)
      const styles = []
      if (this.browser.img.width) styles.push(`width: ${this.browser.img.width}px`)
      if (this.browser.img.height) styles.push(`height: ${this.browser.img.height}px`)
      if (this.browser.img.objectFit) styles.push(`object-fit: ${this.browser.img.objectFit}`)
      imgEl.setAttribute('alt', this.browser.altText || await this.getDefaultMediaText(this.browser.path.selected) || '')
      imgEl.setAttribute('style', styles.join(';'))
      const container = imgEl.closest('[data-per-inline]')
      if (container) $perAdminApp.action(this, 'writeElementToModel', container)
      this.browser.element = null
      this.browser.altText = undefined
      this.browser.withImageTab = false
      return
    }
    if (this.param.cmd === 'editImage') {
      const imgEl = this.browser.element
      const linkTitle = this.browser.linkTitle || this.browser.altText || await this.getDefaultMediaText(this.browser.path.selected)
      const styles = []
      if (this.browser.img.width) styles.push(`width: ${this.browser.img.width}px`)
      if (this.browser.img.height) styles.push(`height: ${this.browser.img.height}px`)
      if (this.browser.img.objectFit) styles.push(`object-fit: ${this.browser.img.objectFit}`)
      imgEl.setAttribute('src', this.browser.path.selected)
      imgEl.setAttribute('alt', linkTitle || '')
      imgEl.setAttribute('title', linkTitle || '')
      imgEl.setAttribute('style', styles.join(';'))
      if ($perAdminApp.getNodeFromViewOrNull('/state/contentview/editor/active')) {
        $perAdminApp.action(this, 'reWrapEditable')
      }
      const editor = imgEl.closest('[contenteditable="true"]')
      if (editor) editor.dispatchEvent(new Event('input'))
      this.browser.element = null
      this.browser.mediaSourcePath = ''
      this.browser.mediaSourceText = ''
      this.browser.mediaSourceFallback = ''
    } else {
      // insertImage
      const range = this.lastInRteSelection;

      const styles = []
      if (this.browser.img.width) styles.push(`width: ${this.browser.img.width}px`)
      if (this.browser.img.height) styles.push(`height: ${this.browser.img.height}px`)
      if (this.browser.img.objectFit) styles.push(`object-fit: ${this.browser.img.objectFit}`)
      const mediaText = this.browser.linkTitle || this.browser.altText || await this.getDefaultMediaText(this.browser.path.selected)

      const img = document.createElement('img')
      img.setAttribute('src', this.browser.path.selected)
      img.setAttribute('alt', mediaText || '')
      img.setAttribute('title', mediaText || '')
      if (styles.length) {
        img.setAttribute('style', styles.join(';'))
      }

      range.deleteContents()
      range.insertNode(img)
      range.setStartAfter(img)
      range.collapse(true)

      this.triggerInput()
    }
    this.browser.mediaSourcePath = ''
    this.browser.mediaSourceText = ''
    this.browser.mediaSourceFallback = ''
  },

  }
}
