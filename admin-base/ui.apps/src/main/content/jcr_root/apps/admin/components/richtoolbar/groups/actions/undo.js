import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  return {
    label: 'undo',
    icon: 'undo',
    iconLib: IconLib.FONT_AWESOME,
    cmd: 'undo',
    isDisabled: () => vm.toolbarDisabled || vm.undoStackLength <= 0
  }
}
