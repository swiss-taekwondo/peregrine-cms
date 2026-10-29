import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  const headlines = []
  
  for (let i = 1; i <= 6; i++) {
    headlines.push({
      label: `${vm.$i18n('headline')} ${i}`,
      icon: 'header',
      iconLib: IconLib.FONT_AWESOME,
      cmd: 'formatBlock',
      value: `h${i}`,
      isActive: () => vm.rangeMatchesSelector(`h${i}`),
      class: () => vm.rangeMatchesSelector(`h${i}`) ? 'active' : null,
      click: () => vm.exec('formatBlock', `h${i}`),
    })
  }

  return headlines
}
