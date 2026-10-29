import specialCharacters from './specialCharacters'
import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  return {
    label: 'special-characters',
    icon: 'copyright',
    iconLib: IconLib.FONT_AWESOME,
    collapse: true,
    searchable: true,
    order: 90,
    priority: 110,
    isDisabled: () => vm.toolbarDisabled,
    rules: () => !vm.responsive || !vm.hiddenGroups['special-characters'],
    items: [
      ...specialCharacters(vm)
    ],
  }
}
