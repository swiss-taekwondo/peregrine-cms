export default (vm, groups) => {
  const computeHidden = () => vm.getResponsiveLayout(groups)
  const getHiddenGroups = () => computeHidden().hidden.filter(g => g.isFontSizeControl || vm.getGroupItems(g).length > 0)

  const getHiddenItems = () => vm.getResponsiveMenuItems(groups)

  const rules = () => {
    const hiddenGroups = getHiddenGroups()
    return vm.responsive && (hiddenGroups.length > 1 || hiddenGroups.some(g => g.isFontSizeControl))
  }

  return {
    label: 'responsive-menu',
    icon: 'bars',
    collapse: true,
    isActive: () => false,
    rules: rules,
    items: getHiddenItems
  }
}
