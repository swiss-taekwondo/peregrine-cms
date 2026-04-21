import removeFormat from './removeFormat'
import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  return {
    label: 'remove format',
    icon: 'format_clear',
    iconLib: IconLib.MATERIAL_ICONS,
    order: 110,
    priority: 80,
    rules: () => !vm.responsive || !vm.hiddenGroups['remove format'],
    items: [
      removeFormat(vm)
    ]
  }
}
