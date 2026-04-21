import bold from './bold'
import italic from './italic'
import underline from './underline'
import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  return {
    label: 'bold-italic',
    icon: 'bold',
    iconLib: IconLib.FONT_AWESOME,
    order: 30,
    priority: 10,
    rules: () => !vm.responsive || !vm.hiddenGroups['bold-italic'],
    items: [
      bold(vm),
      italic(vm),
      underline(vm)
    ]
  }
}
