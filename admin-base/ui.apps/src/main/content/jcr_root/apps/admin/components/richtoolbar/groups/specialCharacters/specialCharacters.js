import {simpleCharMap} from '../../../../../../../../js/utils/charMap'
import HtmlEncoder from '../../../../../../../../js/utils/htmlEncoder'

export default (vm) => {
  const specialChars = []

  simpleCharMap.forEach((char) => {
    specialChars.push({
      label: HtmlEncoder.htmlDecode(`&#${char.code};`),
      title: char.name,
      name: char.name,
      click: () => {
        const container = vm.selection.container
        const doc = vm.selection.doc
        if (container && doc) {
          // Bring focus back to the editor (needed if the filter <input> stole it).
          // preventScroll avoids jumping the page to the top of the editor on refocus.
          container.focus({ preventScroll: true })
          // Restore the exact cursor position using the live Range captured at
          // toggle-click time. This is precise across <br> boundaries, unlike the
          // character-offset fallback which can land on the wrong line.
          const range = vm._specialCharRange
          if (range) {
            const sel = doc.defaultView.getSelection()
            if (sel) {
              sel.removeAllRanges()
              sel.addRange(range.cloneRange())
            }
            vm._specialCharRange = null
          }
        } else {
          // Fallback: character-offset based restore (covers edge cases where the
          // live Range wasn't captured, e.g. if toggleClick was skipped).
          vm.restoreSelection()
        }
        vm.execCmd('insertHTML', `&#${char.code};`)
      }
    },)
  })

  return specialChars
}