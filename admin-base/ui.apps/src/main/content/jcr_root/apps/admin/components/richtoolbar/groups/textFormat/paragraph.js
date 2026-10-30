import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  return {
    label: vm.$i18n('paragraph'),
    icon: 'paragraph',
    iconLib: IconLib.FONT_AWESOME,
    cmd: 'formatBlock',
    value: 'p',
    class: () => vm.rangeMatchesSelector('p') ? 'active' : null,
    click: () => window.dispatchEvent(new CustomEvent('richtoolbar:cmd', { detail: { cmd: 'formatBlock', value: '<p>' } }))
  }
}
