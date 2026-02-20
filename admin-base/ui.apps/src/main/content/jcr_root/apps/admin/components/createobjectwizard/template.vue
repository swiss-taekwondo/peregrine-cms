<!--
  #%L
  admin base - UI Apps
  %%
  Copyright (C) 2017 headwire inc.
  %%
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at
  
  http://www.apache.org/licenses/LICENSE-2.0
  
  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
  #L%
  -->
<template>
<div class="container">
    <form-wizard v-bind:title="'create an object'" v-bind:subtitle="''" @on-complete="onComplete" color="#37474f">
        <tab-content title="select object type" :before-change="leaveTabOne">
            <ul class="collection">
                <li class="collection-item"
                    v-for="item in objects"
                    v-bind:key="item.path"
                    v-on:click.stop.prevent="selectItem(null, item.path)"
                    v-bind:class="isSelected(item.path) ? 'grey lighten-2' : ''">
                    <admin-components-action v-bind:model="{ command: 'selectItem', target: item.path, title: item.name }"></admin-components-action>
                </li>
            </ul>
            <div v-if="!formmodel.templatePath">please select an object</div>
        </tab-content>
        <tab-content title="choose name" :before-change="leaveTabTwo">
            <vue-form-generator :model="formmodel"
                                :schema="nameSchema"
                                :options="formOptions"
                                ref="nameTab">

            </vue-form-generator>
        </tab-content>
        <tab-content title="values">
            <div>Provide the values for this object</div>
            <vue-form-generator :model="formmodel"
                                :schema="objectSchema"
                                :options="formOptions"
                                ref="verifyTab">

            </vue-form-generator>
        </tab-content>
    </form-wizard>
</div>
</template>

<script>
    export default {
        props: ['model'],
        data:
            function() {
                return {
                    formmodel: {
                        path: $perAdminApp.getNodeFromView(this.model.dataFrom),
                        name: '',
                        objectPath: ''

                    },
                    formOptions: {
                        validationErrorClass: "has-error",
                        validationSuccessClass: "has-success",
                        validateAfterChanged: true,
                        focusFirstField: true
                    },
                    nameSchema: {
                        fields: [{
                            type: "input",
                            inputType: "text",
                            label: "Object Name",
                            model: "name",
                            required: true,
                            validator: [this.nameAvailable, this.validObjectName]
                        }
                        ]
                    }
                }

        }
        ,
        computed: {
            objectSchema: function() {
                if(this.formmodel.objectPath !== '') {
                    const path = this.formmodel.objectPath.split('/')
                    const componentName = path.slice(2).join('-')
                    const definitions = $perAdminApp.getNodeFromView('/admin/componentDefinitions')
                    if(definitions &&  definitions[componentName]) {
                        return definitions[componentName].model
                    }
                }
            },
            objects: function() {
                const path = $perAdminApp.getNodeFromView(this.model.dataFrom)
                const node = $perAdminApp.findNodeFromPath($perAdminApp.getView().admin.nodes, path)
                const objects = $perAdminApp.getNodeFromViewOrNull('/admin/objects/data')
                const allowedObjects = this.findAllowedObjects(path)
                const tenant = $perAdminApp.getView().state.tenant;
                const filteredObjects = (objectsToFilter) => {
                  return objectsToFilter.filter( object => { 
                      return object.path.startsWith('/apps/admin/') 
                          || (tenant && object.path.startsWith(`/apps/${tenant.name}/`)) 
                          || (tenant && object.path.startsWith(`/content/${tenant.name}/object-definitions/`))
                  })
                }
                if(allowedObjects) {
                    let ret = []
                    for(let i = 0; i < objects.length; i++) {
                        if(allowedObjects.indexOf(objects[i].name) >= 0) {
                            ret.push(objects[i])
                        }
                    }
                    return filteredObjects(ret)
                }
                return filteredObjects(objects)
            }
        },
        created: function() {
            //By default select the first item in the list;
            if(this.objects.length > 0) {
                this.selectItem(null, this.objects[0].path)
            }
        },
        updated: function() {
            this._setupLocationAutocomplete()
        },
        methods: {
            findAllowedObjects(path) {

                const pathSegments = path.split('/')
                while(pathSegments.length > 1) {
                    const node = $perAdminApp.findNodeFromPath($perAdminApp.getView().admin.nodes, pathSegments.join('/'))
                    if(node.allowedObjects) {
                        return node.allowedObjects
                    }
                    pathSegments.pop()
                }
                return undefined
            },
            selectItem: function(me, target){
                if(me === null) me = this
                me.formmodel.objectPath = target
            },
            isSelected: function(target) {
                return this.formmodel.objectPath === target
            },
            onComplete: function() {
                let objectPath = this.formmodel.objectPath
                objectPath = objectPath.split('/').slice(2).join('/')
                $perAdminApp.stateAction('createObject', { parent: this.formmodel.path, name: this.formmodel.name, template: objectPath, data: this.formmodel, returnTo: this.model.returnTo })
            },
            nameAvailable(value) {
                if(!value || value.length === 0) {
                    return ['name is required']
                } else {
                    const folder = $perAdminApp.findNodeFromPath($perAdminApp.getView().admin.nodes, this.formmodel.path)
                    for(let i = 0; i < folder.children.length; i++) {
                        if(folder.children[i].name === value) {
                            return ['name aready in use']
                        }
                    }
                    return []
                }
            },
            validObjectName(value) {
                if(!value || value.length === 0) {
                    return ['name is required']
                }
                if(value.match(/[^0-9a-zA-Z-]/)) {
                    return ['object names may only contain letters, numbers and dashes. Google dislikes underscores in URLs']
                }
                return [];
            },
            leaveTabOne: function() {
                if('' !== ''+this.formmodel.objectPath) {
                    $perAdminApp.getApi().populateComponentDefinitionFromNode(this.formmodel.objectPath)
                }
                return ! ('' === ''+this.formmodel.objectPath)
            },
            leaveTabTwo: function() {
                return this.$refs.nameTab.validate()
            },

            _setupLocationAutocomplete() {
                const locationSelectors = ['.vue-form-generator #event-location', '.vue-form-generator #club-address']
                locationSelectors.forEach((selector) => {
                    const input = this.$el.querySelector(selector)
                    if (!input || input._autocompleteAttached) return
                    this._attachAddressAutocomplete(input)
                })
            },

            _attachAddressAutocomplete(input) {
                input._autocompleteAttached = true

                const listboxId = `location-autocomplete-listbox-${Math.random().toString(36).slice(2)}`
                input.setAttribute('role', 'combobox')
                input.setAttribute('aria-autocomplete', 'list')
                input.setAttribute('aria-expanded', 'false')
                input.setAttribute('aria-controls', listboxId)
                input.setAttribute('aria-haspopup', 'listbox')

                const dropdown = document.createElement('ul')
                dropdown.id = listboxId
                dropdown.setAttribute('role', 'listbox')
                Object.assign(dropdown.style, {
                    background: '#fff',
                    border: '1px solid #ccc',
                    borderRadius: '4px',
                    boxShadow: '0 2px 6px rgba(0,0,0,0.15)',
                    display: 'none',
                    listStyle: 'none',
                    margin: '0',
                    padding: '0',
                    position: 'absolute',
                    zIndex: '9999',
                })
                input.parentElement.style.position = 'relative'
                input.insertAdjacentElement('afterend', dropdown)

                let activeIndex = -1
                let featureList = []

                const setActiveItem = (index) => {
                    const items = dropdown.querySelectorAll('[role="option"]')
                    items.forEach((item, i) => {
                        const isActive = i === index
                        item.setAttribute('aria-selected', String(isActive))
                        item.style.background = isActive ? '#f5f5f5' : ''
                    })
                    activeIndex = index
                    if (index >= 0 && items[index]) {
                        input.setAttribute('aria-activedescendant', items[index].id)
                    } else {
                        input.removeAttribute('aria-activedescendant')
                    }
                }

                const hideDropdown = () => {
                    dropdown.style.display = 'none'
                    dropdown.innerHTML = ''
                    activeIndex = -1
                    input.setAttribute('aria-expanded', 'false')
                    input.removeAttribute('aria-activedescendant')
                }

                const formatAddress = ({ street, housenumber, postcode, city }) => {
                    const streetPart = [street, housenumber].filter(Boolean).join(' ')
                    const cityPart = [postcode, city].filter(Boolean).join(' ')
                    return [streetPart, cityPart].filter(Boolean).join(', ')
                }

                const selectFeature = (feature) => {
                    input.value = formatAddress(feature.properties)
                    input.dispatchEvent(new Event('input', { bubbles: true }))
                    input.dispatchEvent(new Event('change', { bubbles: true }))
                    hideDropdown()
                    input.focus()
                }

                const showSuggestions = (features) => {
                    featureList = features.filter((f) => f.properties.street)
                    dropdown.innerHTML = ''
                    if (!featureList.length) { hideDropdown(); return }
                    featureList.forEach((feature, i) => {
                        const label = formatAddress(feature.properties)
                        const li = document.createElement('li')
                        li.textContent = label
                        li.id = `location-option-${i}`
                        li.setAttribute('role', 'option')
                        li.setAttribute('aria-selected', 'false')
                        Object.assign(li.style, {
                            borderBottom: '1px solid #eee',
                            cursor: 'pointer',
                            padding: '8px 12px',
                            whiteSpace: 'normal',
                            wordBreak: 'break-word',
                        })
                        li.addEventListener('mouseenter', () => setActiveItem(i))
                        li.addEventListener('mouseleave', () => setActiveItem(-1))
                        li.addEventListener('mousedown', (e) => { e.preventDefault(); selectFeature(feature) })
                        dropdown.append(li)
                    })
                    activeIndex = -1
                    dropdown.style.display = 'block'
                    dropdown.style.width = input.offsetWidth + 'px'
                    input.setAttribute('aria-expanded', 'true')
                }

                input.addEventListener('keydown', (e) => {
                    if (dropdown.style.display === 'none') return
                    const items = dropdown.querySelectorAll('[role="option"]')
                    if (e.key === 'ArrowDown') { e.preventDefault(); setActiveItem(Math.min(activeIndex + 1, items.length - 1)) }
                    else if (e.key === 'ArrowUp') { e.preventDefault(); setActiveItem(Math.max(activeIndex - 1, -1)) }
                    else if (e.key === 'Enter' && activeIndex >= 0) { e.preventDefault(); selectFeature(featureList[activeIndex]) }
                    else if (e.key === 'Escape') { hideDropdown() }
                })

                let debounceTimer
                input.addEventListener('input', () => {
                    clearTimeout(debounceTimer)
                    const val = input.value.trim()
                    if (val.length < 3) { hideDropdown(); return }
                    debounceTimer = setTimeout(async () => {
                        try {
                            const res = await fetch(`https://photon.komoot.io/api/?q=${encodeURIComponent(val)}&limit=6&lang=en&bbox=5.9,45.8,10.5,47.8`)
                            if (!res.ok) return
                            const data = await res.json()
                            showSuggestions(data.features || [])
                        } catch { /* Do nothing */ }
                    }, 300)
                })

                input.addEventListener('blur', hideDropdown)
            },

        }
    }
</script>
