<template>
  <div :class="['explorer-preview-content', `preview-${nodeType}`]">

    <template v-if="currentObject">
      <div class="explorer-preview-nav">
        <ul class="nav-left">
          <explorer-preview-nav-item
              v-if="!!($slots.default)"
              icon="view_list"
              title="component explorer"
              :class="{'active': isTab(Tab.COMPONENTS)}"
              @click="setActiveTab(Tab.COMPONENTS)"/>

          <explorer-preview-nav-item
              icon="settings"
              :title="`${nodeType}-info`"
              :class="{'active': isTab(Tab.INFO)}"
              @click="setActiveTab(Tab.INFO)"/>

          <explorer-preview-nav-item
              v-if="hasOgTags"
              icon="label"
              :title="'og-tags'"
              :class="{'active': isTab(Tab.OG_TAGS)}"
              @click="setActiveTab(Tab.OG_TAGS)"/>

          <explorer-preview-nav-item
              v-if="hasReferences"
              icon="list"
              :title="'references'"
              :class="{'active': isTab(Tab.REFERENCES)}"
              @click="setActiveTab(Tab.REFERENCES)"/>

          <explorer-preview-nav-item
              icon="restore_page"
              :title="`${nodeType}-versions`"
              :class="{'active': isTab(Tab.VERSIONS)}"
              @click="setActiveTab(Tab.VERSIONS)"/>

          <explorer-preview-nav-item
              v-if="allowWebPublish"
              icon="public"
              :title="'Web Publishing'"
              :class="{'active': isTab(Tab.PUBLISHING)}"
              @click="setActiveTab(Tab.PUBLISHING)" />

          <explorer-preview-nav-item
              icon="more_vert"
              :title="'actions'"
              :class="{'active': isTab(Tab.ACTIONS)}"
              @click="setActiveTab(Tab.ACTIONS)"/>
        </ul>

        <ul class="nav-right"></ul>
      </div>

      <template v-if="isTab([Tab.COMPONENTS])">
        <slot></slot>
      </template>

      <template v-else-if="isTab([Tab.INFO, Tab.OG_TAGS])">
        <span class="panel-title">{{getActiveTabName}}</span>
        <div v-if="hasInfoView && !edit"
             :class="`${nodeType}-info-view`">
          <img v-if="isImage"
               :src="currentObject"
               class="info-view-image"
               v-on:click="openModal"
               />
          <video v-else-if="isVideo"
              ref="videoPreview"
              :src="currentObject"
              class="info-view-video"
              controls>
          </video>
          <iframe
              v-else
              :src="currentObject"
              class="info-view-iframe">
          </iframe>
        </div>
        <vue-form-generator
            v-if="node && getSchemaByActiveTab()"
            ref="vfg"
            :class="{'vfg-preview': !edit}"
            :schema="getSchemaByActiveTab()"
            :model="node"
            :options="options"
            @validated="onValidated()"
            @model-updated="onModelUpdate">
        </vue-form-generator>
        <div v-if="nodeType !== NodeType.FILE" class="explorer-confirm-dialog">
          <template v-if="edit">
            <button
                class="btn btn-raised waves-effect waves-light right"
                type="button"
                :title="`cancel editing ${nodeType} properties`"
                @click.stop.prevent="onCancel()">
              <icon icon="close"/>
            </button>
            <button
                class="btn btn-raised waves-effect waves-light right"
                type="button"
                :title="`save ${nodeType} properties`"
                :disabled="!valid"
                @click.stop.prevent="save()">
              <icon icon="check_box"/>
            </button>
          </template>
          <template v-else>
            <span></span>
            <button
                class="btn btn-raised waves-effect waves-light right"
                type="button"
                :title="`edit ${nodeType} properties`"
                @click.stop.prevent="onEdit()">
              <icon icon="edit"/>
            </button>
          </template>
        </div>
      </template>

      <template v-else-if="isTab(Tab.REFERENCES)">
        <span class="panel-title">{{ getActiveTabName }}</span>
        <linear-preloader v-if="loading"/>
        <ul v-else :class="['collection', 'with-header', `explorer-${nodeType}-referenced-by`]">
          <li class="collection-header">
            referenced in {{ referencedBy.length }}
            location<span v-if="referencedBy.length !== 1 ">s</span>
          </li>
          <li v-for="item in referencedBy" :key="item.path" class="collection-item">
            <span>
              <span v-if="item.count" class="count">{{
                  item.count > 99 ? '99+' : item.count
                }}</span>
              <span class="right">
                <action
                    v-bind:model="{
                      target: item,
                      command: 'editReference',
                      tooltipTitle: `edit '${item.name}'`
                    }">
                    <bdo>{{ item.path }}</bdo>
                </action>
              </span>
              <span class="edit-icon">
                <action
                    v-bind:model="{
                      target: item,
                      command: 'editReference',
                      tooltipTitle: `edit '${item.name}'`
                    }">
                    <icon-edit-page/>
                </action>
              </span>
            </span>
          </li>
        </ul>
      </template>

      <template v-else-if="isTab(Tab.VERSIONS)">
        <span class="panel-title">{{getActiveTabName}}</span>
        <div v-if="allowOperations" class="action-list">
          <div class="action"
               v-on:click.stop.prevent="createVersion"
               v-bind:title="`create new ${nodeType} version`">
            <icon icon="create"/>
            Create new {{ nodeType }} version
          </div>

          <p v-if="!hasVersions"
             v-bind:title="`no versions created yet`">
            This {{nodeType}} has no versions
          </p>
          <template v-else>
            <div v-for="version in versions"
                 class="action"
                 v-bind:key="version.name"
                 v-on:click="checkoutVersion(version)"
                 v-bind:title="`Version ${version.name}`">
              <icon v-if="version.base" icon="check_box"/>
              <icon v-else-if="!version.base" icon="check_box_outline_blank"/> {{version.name}} {{version.created}}
              <div v-if="version.labels">
                <span v-for="label in version.labels" class="chip labelChip" v-bind:key="label">{{label}}</span>
              </div>
              <span v-if="!version.base" class="deleteVersionWrapper">
                          <action
                              v-bind:model="{
                                command: 'deleteVersion',
                                target: {version: version, path: currentObject},
                                tooltipTitle: 'delete version'}">
                              <icon icon="delete"/>
                          </action>
                      </span>
            </div>
          </template>

        </div>
      </template>

      <template v-else-if="isTab(Tab.PUBLISHING)">
        <span class="panel-title">{{getActiveTabName}}</span>
        <admin-components-publishinginfo v-bind:node="nodeFromPath" v-if="nodeFromPath"/>

        <div v-if="allowOperations && node" class="action-list">
          <div class="action" :title="`Open Web Publishing ${nodeType} Dialog`" @click="openPublishingModal()">
            <i class="material-icons">publish</i>
            Publish to Web ({{nodeType}})
          </div>
          <div v-if="nodeFromPath && nodeFromPath.activated" class="action" :title="`Deactivate ${nodeType}`">
            <admin-components-action :model="{
                    target: node && node.path,
                    command: 'unPublishResource',
                    tooltipTitle: `${$i18n('undo publish')} '${node && (node.title || node.name)}'`
                }">
              <i class="material-icons">remove_circle_outline</i>
              Unpublish ({{nodeType}})
            </admin-components-action>
          </div>
          <div v-else class="action operationDisabledOnActivatedItem" :title="`Deactivate ${nodeType}`">
            <span>
              <i class="material-icons">remove_circle_outline</i>
              <span>Unpublish ({{nodeType}})</span>
            </span>
          </div>
        </div>
      </template>

      <admin-components-publishingmodal
          v-if="isPublishDialogOpen"
          v-bind:isOpen="isPublishDialogOpen"
          v-bind:path="currentObject"
          v-on:complete="closePublishing"
          v-bind:modalTitle="`Web Publishing: ${nodeName}`">
      </admin-components-publishingmodal>

      <template v-else-if="isTab(Tab.ACTIONS)">
        <span class="panel-title">Actions</span>
        <div v-if="allowOperations" class="action-list">
          <div v-if="nodeType === NodeType.PAGE"
               class="action"
               title="open live version"
               @click="openLiveVersion">
            <icon icon="external-link" :lib="IconLib.FONT_AWESOME"/>
            Open live version
          </div>
          <button v-if="nodeType === NodeType.ASSET"
                  :class="['action', 'generate-alt-text-action', { operationDisabledOnActivatedItem: !canGenerateAltText || !altTextConfigured }]" type="button"
                  :disabled="!canGenerateAltText || !altTextConfigured || generatingAltText || savingAltText"
                  :title="!canGenerateAltText ? 'AI alt text supports PNG, JPEG, WebP, and GIF images' : (!altTextConfigured ? 'Alt text requires its Gemini API Key, Model, and Prompt' : 'Generate Alt Text')"
                  @click.stop.prevent="generateAltText">
            <svg xmlns="http://www.w3.org/2000/svg" width="1em" height="1em"
                 viewBox="0 0 48 48" aria-hidden="true">
              <path d="M0 0h48v48H0z" fill="none"/>
              <path fill="currentColor"
                    d="M34 6c-1.368 4.944-3.13 6.633-8 8c4.87 1.367 6.632 3.056 8 8c1.368-4.944 3.13-6.633 8-8c-4.87-1.367-6.632-3.056-8-8m-14 8c-2.395 8.651-5.476 11.608-14 14c8.524 2.392 11.605 5.349 14 14c2.395-8.651 5.476-11.608 14-14c-8.524-2.392-11.605-5.349-14-14"/>
            </svg>
            <span>{{ savingAltText ? 'Saving...' : (generatingAltText ? 'Generating...' : 'Generate Alt Text') }}</span>
          </button>
          <button v-if="nodeType === NodeType.ASSET" class="action generate-alt-text-action" type="button" @click.stop.prevent="openAltTextQueue">
            <icon icon="list" /> <span>Image Republish Queue{{ altTextQueueImageCount === null ? '' : ` (${altTextQueueImageCount})` }}</span>
          </button>
          <div v-if="allowTranslate"
               :class="translationConfigured ? 'action' : 'action operationDisabledOnActivatedItem'"
               :title="translationConfigured ? `translate ${nodeType}` : translationConfigurationMessage"
               @click="openTranslationsModal()">
            <icon icon="translate" />
            <span>Translate {{ nodeType }}</span>
          </div>
          <div v-if="allowRename" :class="classForActionDisabledOnActivatedResource" :title="`rename ${nodeType}`" @click="renameNode()">
            <icon :lib="IconLib.MATERIAL_ICONS" icon="text_format"/>
            <span>Rename {{ nodeType }}</span>
          </div>
          <div v-if="allowMove" :class="classForActionDisabledOnActivatedResource" :title="`move ${nodeType}`" @click="moveNode()">
            <icon icon="compare_arrows"/>
            <span>Move {{ nodeType }}</span>
          </div>
          <div v-if="allowCopy" class="action" :title="`copy ${nodeType}`" @click="copyNode()">
            <icon icon="content_copy"/>
            Copy {{ nodeType }}
          </div>
          <div v-if="allowDelete" :class="classForActionDisabledOnActivatedResource" :title="`delete ${nodeType}`" @click="deleteNode()">
            <icon :icon="selfOrAnyDescendantActivated ? 'delete_forever' : 'delete'" />
            <span>Delete {{ nodeType }}</span>
          </div>
        </div>
      </template>

      <admin-components-translationsmodal
        v-if="this.node && this.node.path"
        ref="translationsModal"
        v-bind:path="node && node.path"
        v-bind:modalTitle="`Translations: ${nodeName}`">
      </admin-components-translationsmodal>

      <materialize-modal
          ref="altTextCompareModal"
          modalTitle="Review Generated Alt Text"
          v-on:complete="pendingAltText = null">
        <div class="alt-text-comparison">
          <div>
            <div>Current alt text</div>
            <div class="alt-text-value">{{ pendingAltText && pendingAltText.current }}</div>
          </div>
          <div>
            <div>Generated alt text</div>
            <div class="alt-text-value">{{ pendingAltText && pendingAltText.generated }}</div>
          </div>
        </div>
        <template slot="footer">
          <button class="modal-action waves-effect waves-light btn-flat" type="button" @click="keepCurrentAltText">
            Keep Current
          </button>
          <button class="modal-action waves-effect waves-light btn" type="button" :disabled="savingAltText" @click="useGeneratedAltText">
            {{ savingAltText ? 'Saving...' : 'Use Generated' }}
          </button>
        </template>
      </materialize-modal>

      <materialize-modal ref="altTextDecisionModal" modalTitle="">
        <p>Image changes were saved. What would you like to do with this image?</p>
        <div v-if="currentAltTextEntry" class="alt-text-queue-preview">
          <img :src="currentAltTextEntry.assetPath" alt="" />
          <div>
            <div class="alt-text-queue-path">{{ currentAltTextEntry.assetPath }}</div>
            <div><strong>Alt text:</strong> {{ currentAltTextEntry.altText || '(empty)' }}</div>
          </div>
        </div>
        <div v-if="loadingAltTextQueue" role="status" aria-label="Loading affected pages">
          <admin-components-materializespinner/>
        </div>
        <p v-if="altTextQueueError">{{ altTextQueueError }}</p>
        <template v-if="currentAltTextEntry && !loadingAltTextQueue">
          <p>Pages that can be republished:</p>
          <ul v-if="currentAltTextEntry.eligiblePages.length">
            <li v-for="pagePath in currentAltTextEntry.eligiblePages" :key="pagePath">
              <a :href="pageEditorUrl(pagePath)" target="_blank" rel="noopener noreferrer">{{ pagePath }}</a>
            </li>
          </ul>
          <p v-else>None.</p>
          <p v-if="currentAltTextEntry.assetRepublishable">The image itself can be republished.</p>
          <p>Published pages with unpublished changes (manual review required):</p>
          <ul v-if="currentAltTextEntry.blockedPages.length">
            <li v-for="pagePath in currentAltTextEntry.blockedPages" :key="pagePath">
              <a :href="pageEditorUrl(pagePath)" target="_blank" rel="noopener noreferrer">{{ pagePath }}</a>
            </li>
          </ul>
          <p v-else>None.</p>
        </template>
        <p v-if="republishedAltTextPages.length">The image was republished on these pages:</p>
        <ul v-if="republishedAltTextPages.length">
          <li v-for="pagePath in republishedAltTextPages" :key="pagePath">
            <a :href="pageEditorUrl(pagePath)" target="_blank" rel="noopener noreferrer">{{ pagePath }}</a>
          </li>
        </ul>
        <p v-if="republishedAltTextAssets.length">The image asset was republished:</p>
        <ul v-if="republishedAltTextAssets.length">
          <li v-for="assetPath in republishedAltTextAssets" :key="assetPath">{{ assetPath }}</li>
        </ul>
        <div v-if="republishingAltTextPages && altTextRepublishRequested" role="status" aria-label="Checking republish status">
          <admin-components-materializespinner/>
        </div>
        <p v-if="altTextRepublishStatusUnavailable">{{ altTextRepublishStatusUnavailable }}</p>
        <template slot="footer">
          <button v-if="currentAltTextEntry && currentAltTextEntry.reportToken && (currentAltTextEntry.assetRepublishable || currentAltTextEntry.eligiblePages.length) && !altTextRepublishRequested" class="modal-action waves-effect waves-light btn-flat"
                  type="button" @click="$refs.altTextDecisionModal.close()">Add to Queue</button>
          <button v-if="currentAltTextEntry && currentAltTextEntry.reportToken && (currentAltTextEntry.assetRepublishable || currentAltTextEntry.eligiblePages.length) && !altTextRepublishRequested" class="waves-effect waves-light btn-flat"
                  type="button" :disabled="decidingAltTextQueue" @click="decideAltTextQueue(currentAltTextEntry, 'republish')">Republish</button>
          <button v-if="currentAltTextEntry && currentAltTextEntry.reportToken && (currentAltTextEntry.assetRepublishable || currentAltTextEntry.eligiblePages.length) && !altTextRepublishRequested" class="waves-effect waves-light btn-flat"
                  type="button" :disabled="decidingAltTextQueue" @click="decideAltTextQueue(currentAltTextEntry, 'discard')">Do not Republish</button>
          <button v-if="altTextRepublishRequested && !altTextRepublishComplete" class="waves-effect waves-light btn-flat"
                  type="button" :disabled="republishingAltTextPages" @click="checkAltTextRepublishStatusAgain">Check again</button>
          <button class="modal-action waves-effect waves-light btn" type="button" @click="$refs.altTextDecisionModal.close()">Close</button>
        </template>
      </materialize-modal>

      <materialize-modal
          ref="unpublishedAltTextPagesModal"
          modalTitle="Image Republish Queue">
        <div class="alt-text-queue-tabs" role="tablist" aria-label="Queue view">
          <button type="button" role="tab" :aria-selected="altTextQueueTab === 'images'"
                  :class="{'is-active': altTextQueueTab === 'images'}" @click="altTextQueueTab = 'images'">
            Images ({{ altTextQueueImageCount === null ? 0 : altTextQueueImageCount }})
          </button>
          <button type="button" role="tab" :aria-selected="altTextQueueTab === 'pages'"
                  :class="{'is-active': altTextQueueTab === 'pages'}" @click="altTextQueueTab = 'pages'">
            Pages ({{ altTextQueuePages.length }})
          </button>
          <button type="button" role="tab" :aria-selected="altTextQueueTab === 'manual'"
                  :class="{'is-active': altTextQueueTab === 'manual'}" @click="altTextQueueTab = 'manual'">
            Manual work ({{ manualAltTextQueuePages.length }})
          </button>
        </div>
        <div v-if="loadingAltTextQueue" role="status" aria-label="Loading image republish queue">
          <admin-components-materializespinner/>
        </div>
        <p v-if="altTextQueueError">{{ altTextQueueError }}</p>
        <p v-if="altTextQueueTab === 'images' && !loadingAltTextQueue && !republishableAltTextQueueEntries.length">No image changes are queued.</p>
        <div v-if="altTextQueueTab === 'images'">
        <div v-for="entry in republishableAltTextQueueEntries" :key="entry.reportToken" class="alt-text-queue-entry">
          <div class="alt-text-queue-preview">
            <img :src="entry.assetPath" alt="" />
            <span>
              <span class="alt-text-queue-path">{{ entry.assetPath }}</span>
              <span><strong>Alt text:</strong>
                {{ entry.altText || '(empty)' }}</span>
            </span>
          </div>
          <button type="button" class="waves-effect waves-light btn-flat"
                  :disabled="decidingAltTextQueue || republishingAltTextPages"
                  @click="removeImageFromAltTextQueue(entry.assetPath)">Remove from queue</button>
          <button type="button" class="waves-effect waves-light btn-flat"
                  :disabled="decidingAltTextQueue || republishingAltTextPages"
                  @click="republishQueuedImage(entry)">Republish</button>
          <p v-if="entry.manualReview">Manual review is still required for these pages:</p>
          <p v-else>Pages that can be republished:</p>
          <ul v-if="!entry.manualReview && entry.eligiblePages.length">
            <li v-for="pagePath in entry.eligiblePages" :key="pagePath">
              <a :href="pageEditorUrl(pagePath)" target="_blank" rel="noopener noreferrer">{{ pagePath }}</a>
            </li>
          </ul>
          <p v-else-if="!entry.manualReview">None.</p>
          <p v-if="entry.assetRepublishable">The image itself will be republished.</p>
          <p v-if="!entry.manualReview">Published pages with unpublished changes (manual review required):</p>
          <ul v-if="entry.blockedPages.length">
            <li v-for="pagePath in entry.blockedPages" :key="pagePath">
              <a :href="pageEditorUrl(pagePath)" target="_blank" rel="noopener noreferrer">{{ pagePath }}</a>
            </li>
          </ul>
          <p v-else>None.</p>
        </div>
        </div>
        <div v-else-if="altTextQueueTab === 'pages' && altTextQueueEntries.length">
          <div v-for="page in altTextQueuePages" :key="page.path" class="alt-text-queue-entry">
            <a :href="pageEditorUrl(page.path)" target="_blank" rel="noopener noreferrer">{{ page.path }}</a>
            <p>{{ page.needsManualReview ? 'Manual check required: unpublished changes' : 'Can be republished' }}</p>
            <p>Images ({{ page.images.length }}):
              <span v-for="(imagePath, index) in page.images" :key="imagePath" class="alt-text-queue-image-hover" tabindex="0">
                {{ index ? ', ' : '' }}{{ imagePath }}
                <span class="alt-text-queue-image-tooltip" aria-hidden="true"><img :src="imagePath" alt="" /></span>
              </span>
            </p>
          </div>
          <p v-if="!altTextQueuePages.length">No published pages are associated with the queued images.</p>
        </div>
        <p v-else-if="altTextQueueTab === 'pages' && !loadingAltTextQueue">No queued pages to display.</p>
        <div v-else-if="altTextQueueTab === 'manual'">
          <p v-if="!manualAltTextQueuePages.length">No manual work is needed.</p>
          <div v-for="entry in manualAltTextQueueEntries" :key="entry.reportToken" class="alt-text-queue-entry">
            <div class="alt-text-queue-preview">
              <img :src="entry.assetPath" alt="" />
              <span>
                <span class="alt-text-queue-path">{{ entry.assetPath }}</span>
                <span><strong>Alt text:</strong> {{ entry.altText || '(empty)' }}</span>
              </span>
            </div>
            <p>Manual review is needed on these pages:</p>
            <ul>
              <li v-for="pagePath in entry.blockedPages" :key="pagePath">
                <a :href="pageEditorUrl(pagePath)" target="_blank" rel="noopener noreferrer">{{ pagePath }}</a>
              </li>
            </ul>
            <button type="button" class="waves-effect waves-light btn-flat"
                    :disabled="decidingAltTextQueue || republishingAltTextPages"
                    @click="removeImageFromAltTextQueue(entry.assetPath)">Remove from queue</button>
          </div>
          <div v-for="page in manualAltTextQueuePages.filter(page => !page.images.length)" :key="page.path" class="alt-text-queue-entry">
            <a :href="pageEditorUrl(page.path)" target="_blank" rel="noopener noreferrer">{{ page.path }}</a>
            <p>Manual review is needed before the image can be republished.</p>
          </div>
        </div>
        <p v-if="republishedAltTextPages.length">The image was successfully republished on these pages:</p>
        <ul>
          <li v-for="pagePath in republishedAltTextPages" :key="pagePath">
            <a :href="pageEditorUrl(pagePath)" target="_blank" rel="noopener noreferrer">{{ pagePath }}</a>
          </li>
        </ul>
        <p v-if="republishedAltTextAssets.length">The image assets were republished:</p>
        <ul v-if="republishedAltTextAssets.length">
          <li v-for="assetPath in republishedAltTextAssets" :key="assetPath">{{ assetPath }}</li>
        </ul>
        <p v-if="altTextRepublishComplete && unpublishedAltTextPages.length">These published pages could not be updated automatically. Review and publish them manually:</p>
        <ul v-if="altTextRepublishComplete && unpublishedAltTextPages.length">
          <li v-for="pagePath in unpublishedAltTextPages" :key="pagePath">
            <a :href="pageEditorUrl(pagePath)" target="_blank" rel="noopener noreferrer">{{ pagePath }}</a>
          </li>
        </ul>
        <div v-if="republishingAltTextPages && altTextRepublishRequested" role="status" aria-label="Checking which pages were republished">
          <admin-components-materializespinner/>
        </div>
        <p v-if="altTextRepublishComplete && !republishedAltTextPages.length">No pages were republished.</p>
        <p v-if="altTextRepublishStatusUnavailable">{{ altTextRepublishStatusUnavailable }}</p>
        <template slot="footer">
          <button v-if="republishableAltTextQueueEntries.length"
                  class="waves-effect waves-light btn-flat" type="button" :disabled="decidingAltTextQueue || republishingAltTextPages"
                  @click="republishAltTextQueue">Republish all</button>
          <button v-if="altTextQueueEntries.length"
                  class="waves-effect waves-light btn-flat" type="button" :disabled="decidingAltTextQueue || republishingAltTextPages"
                  @click="clearAltTextQueue">Clear Queue</button>
          <button v-if="((currentAltTextReportToken && currentAltTextAssetPath) || unconfirmedAltTextReports.length) && altTextRepublishRequested && !altTextRepublishComplete"
                  class="waves-effect waves-light btn-flat" type="button" :disabled="republishingAltTextPages"
                  @click="checkAltTextRepublishStatusAgain">
            Check again
          </button>
          <button class="modal-action waves-effect waves-light btn" type="button" @click="$refs.unpublishedAltTextPagesModal.close()">
            Close
          </button>
        </template>
      </materialize-modal>

    </template>

    <template v-else>
      <div v-if="!currentObject" class="explorer-preview-empty">
        <span>{{ $i18n(`no${uNodeType}Selected`) }}</span>
        <i class="material-icons">info</i>
      </div>
    </template>

    <materialize-modal
        class="rename-modal"
        ref="renameModal"
        v-bind:modalTitle="modalTitle"
        v-on:ready="onReady">
      <vue-form-generator
          :model="formmodel"
          :schema="nameSchema"
          :options="formOptions"
          ref="renameForm">
      </vue-form-generator>
      <template slot="footer">
        <confirm-dialog
            submitText="submit"
            v-on:confirm-dialog="onConfirmDialog"/>
      </template>
    </materialize-modal>

    <path-browser
        v-if="isOpen"
        :isOpen="isOpen"
        :header="`Move ${nodeName}`"
        :browserRoot="browserRoot"
        :browserType="nodeType"
        :currentPath="path.current"
        :selectedPath="path.selected"
        :setCurrentPath="setCurrentPath"
        :setSelectedPath="setSelectedPath"
        :onCancel="onMoveCancel"
        @select="onMoveSelect">
    </path-browser>

    <path-browser
        v-if="isCopyOpen"
        :isOpen="isCopyOpen"
        :header="`Copy ${nodeName}`"
        :browserRoot="browserRoot"
        :browserType="nodeType"
        :currentPath="path.current"
        :selectedPath="path.selected"
        :setCurrentPath="setCurrentPath"
        :setSelectedPath="setSelectedPath"
        :onCancel="onCopyCancel"
        @select="onCopySelect">
    </path-browser>

      <dialog v-if="modalVisible" class="modal-overlay" ref="previewModal" @click.self="closeModal" @keydown.esc="closeModal" tabindex="-1">
        <div class="modal-content">
          <img :src="currentObject" alt="Modal Image" />
          <button @click="closeModal"><i class="material-icons">close</i></button>
        </div>
      </dialog>
  </div>
</template>

<script>
import {
	IconLib,
	MimeType,
	NodeType,
	SUFFIX_PARAM_SEPARATOR,
} from "../../../../../../js/constants";
import NodeNameValidation from "../../../../../../js/mixins/NodeNameValidation";
import ReferenceUtil from "../../../../../../js/mixins/ReferenceUtil";
import { deepClone, get, set } from "../../../../../../js/utils";
import { resolveMediaText } from "../../../js/mediaText";
import Action from "../action/template.vue";
import ConfirmDialog from "../confirmdialog/template.vue";
import ExplorerPreviewNavItem from "../explorerpreviewnavitem/template.vue";
import Icon from "../icon/template.vue";
import IconEditPage from "../iconeditpage/template.vue";
import LinearPreloader from "../linearpreloader/template.vue";
import MaterializeModal from "../materializemodal/template.vue";
import PathBrowser from "../pathbrowser/template.vue";

const Tab = {
  INFO: 'info',
  OG_TAGS: 'og-tags',
  REFERENCES: 'references',
  VERSIONS: 'versions',
  COMPONENTS: 'components',
  ACTIONS: 'actions',
  PUBLISHING: 'publishing'
};

const SchemaKey = {
  MODEL: 'model',
  OG_TAGS: 'ogTags'
};

export default {
  name: 'ExplorerPreviewContent',
  components: {
    LinearPreloader,
    Icon,
    PathBrowser,
    MaterializeModal,
    ConfirmDialog,
    Action,
    ExplorerPreviewNavItem,
    IconEditPage
  },
  props: {
    model: {
      type: Object,
      required: true
    },
    nodeType: {
      type: String,
      required: true
    },
    browserRoot: {
      type: String,
      required: true
    },
    currentPath: {
      type: String,
      required: true
    },
    tab: {
      type: String,
      default: Tab.INFO
    },
    isEdit: {
      type: Boolean,
      default: false
    },
    onDelete: {
      type: Function,
      default: (type, path) => new Promise()
    }
  },
  data() {
    return {
      Tab,
      SchemaKey,
      NodeType,
      IconLib,
      activeTab: null,
      activeTabName: "info",
      edit: false,
      valid: {
        state: true,
        errors: null
      },
      isOpen: false,
      isCopyOpen: false,
      isPublishDialogOpen: false,
      modalVisible: false,
      selectedPath: null,
      options: {
        validateAfterLoad: true,
        validateAfterChanged: true,
        focusFirstField: true
      },
      nodeTypeGroups: {
        ogTags: [NodeType.PAGE, NodeType.TEMPLATE],
        references: [NodeType.ASSET, NodeType.PAGE, NodeType.TEMPLATE, NodeType.OBJECT],
        selectStateAction: [NodeType.ASSET, NodeType.OBJECT],
        showProp: [NodeType.ASSET, NodeType.OBJECT, NodeType.FILE],
        allowMove: [NodeType.PAGE, NodeType.TEMPLATE, NodeType.ASSET, NodeType.FILE, NodeType.OBJECT],
        allowRename: [NodeType.PAGE, NodeType.TEMPLATE, NodeType.ASSET, NodeType.FILE, NodeType.OBJECT],
        allowCopy: [NodeType.PAGE, NodeType.TEMPLATE, NodeType.ASSET, NodeType.FILE, NodeType.OBJECT],
        allowDelete: [NodeType.PAGE, NodeType.TEMPLATE, NodeType.ASSET, NodeType.FILE, NodeType.OBJECT],
        allowTranslate: [NodeType.PAGE, NodeType.TEMPLATE, NodeType.OBJECT],
        allowWebPublish: [NodeType.PAGE, NodeType.FILE],
      },
      path: {
        current: null,
        selected: null
      },
      formGenerator: {
        changes: []
      },
      loading: false,
      generatingAltText: false,
      savingAltText: false,
      altTextRequestId: 0,
      altTextConfigured: false,
      pendingAltText: null,
      unpublishedAltTextPages: [],
      republishedAltTextPages: [],
      republishedAltTextAssets: [],
      altTextQueueEntries: [],
      unconfirmedAltTextReports: [],
      altTextQueueTab: 'images',
      altTextQueueImageCount: null,
      currentAltTextAssetRepublishable: false,
      loadingAltTextQueue: false,
      altTextQueueRequestId: 0,
      decidingAltTextQueue: false,
      altTextQueueError: '',
      altTextRepublishRequested: false,
      republishingAltTextPages: false,
      altTextRepublishComplete: false,
      currentAltTextReportToken: null,
      currentAltTextAssetPath: null,
      currentAltTextValue: '',
      currentAltTextSavedEntry: null,
      altTextRepublishStatusUnavailable: '',
      altTextStatusController: null,
      translationConfigured: false,
      translationConfigurationMessage: 'Checking translation configuration...',
      isReferencedInPublish: true
    }
  },
  mixins: [NodeNameValidation,ReferenceUtil],
  computed: {
    currentAltTextEntry() {
      if (!this.currentAltTextAssetPath) return null;
      return this.altTextQueueEntries.find(entry => this.currentAltTextReportToken
        ? entry.reportToken === this.currentAltTextReportToken
        : entry.assetPath === this.currentAltTextAssetPath)
        || (this.currentAltTextSavedEntry && this.currentAltTextSavedEntry.assetPath === this.currentAltTextAssetPath
          ? this.currentAltTextSavedEntry : null)
        || {reportToken: this.currentAltTextReportToken, assetPath: this.currentAltTextAssetPath,
          eligiblePages: [], blockedPages: this.unpublishedAltTextPages, altText: this.currentAltTextValue,
          assetRepublishable: this.currentAltTextAssetRepublishable};
    },
    republishableAltTextQueueEntries() {
      return this.altTextQueueEntries.filter(entry => !entry.manualReview
        && (entry.assetRepublishable || (entry.eligiblePages && entry.eligiblePages.length)));
    },
    manualAltTextQueueEntries() {
      return this.altTextQueueEntries.filter(entry => entry.manualReview || (entry.blockedPages && entry.blockedPages.length));
    },
    altTextQueuePages() {
      const pages = new Map();
      this.altTextQueueEntries.forEach(entry => {
        const addPage = (path, needsManualReview) => {
          if (!pages.has(path)) pages.set(path, {path, needsManualReview: false, images: []});
          const page = pages.get(path);
          page.needsManualReview = page.needsManualReview || needsManualReview;
          if (!page.images.includes(entry.assetPath)) page.images.push(entry.assetPath);
        };
        (entry.eligiblePages || []).forEach(path => addPage(path, false));
        (entry.blockedPages || []).forEach(path => addPage(path, true));
      });
      return Array.from(pages.values()).sort((a, b) => a.path.localeCompare(b.path));
    },
    manualAltTextQueuePages() {
      const pages = new Map();
      const addPage = (path, assetPath) => {
        if (!pages.has(path)) pages.set(path, {path, images: []});
        const page = pages.get(path);
        if (assetPath && !page.images.includes(assetPath)) page.images.push(assetPath);
      };
      this.altTextQueuePages.filter(page => page.needsManualReview)
        .forEach(page => page.images.forEach(assetPath => addPage(page.path, assetPath)));
      this.unpublishedAltTextPages.forEach(path => addPage(path, null));
      return Array.from(pages.values()).sort((a, b) => a.path.localeCompare(b.path));
    },
    uNodeType() {
      return this.capFirstLetter(this.nodeType);
    },
    modalTitle() {
      return `Rename ${this.uNodeType}`
    },
    rawCurrentObject() {
      return $perAdminApp.getNodeFromViewOrNull(`/state/tools/${this.nodeType}`);
    },
    currentObject() {
      const obj = this.rawCurrentObject;
      if (this.nodeTypeGroups.showProp.indexOf(this.nodeType) > -1) {
        if (this.nodeType === NodeType.FILE) {
          return obj;
        } else if (obj && obj.hasOwnProperty('show')) {
          return obj.show;
        } else {
          return null;
        }
      }
      return obj;
    },
    nodeFromPath() {
      return $perAdminApp.findNodeFromPath(this.$root.$data.admin.nodes, this.currentObject);
    },
    node() {
      if (this.nodeType === NodeType.OBJECT) {
        if (!this.rawCurrentObject || !this.rawCurrentObject.data) return null;
        return this.rawCurrentObject.data
      }
      return this.nodeFromPath;
    },
    allowOperations() {
      if (!this.currentObject) return false;
      return this.currentObject.split('/').length > 4;
    },
    allowMove() {
      return this.nodeTypeGroups.allowMove.indexOf(this.nodeType) > -1;
    },
    allowRename() {
      return this.nodeTypeGroups.allowRename.indexOf(this.nodeType) > -1;
    },
    allowCopy() {
      return this.nodeTypeGroups.allowCopy.indexOf(this.nodeType) > -1;
    },
    allowDelete() {
      return this.nodeTypeGroups.allowDelete.indexOf(this.nodeType) > -1;
    },
    allowTranslate() {
      if (this.nodeType === NodeType.ASSET) return this.isImage;
      return this.nodeTypeGroups.allowTranslate.indexOf(this.nodeType) > -1;
    },
    allowWebPublish() {
      return this.nodeTypeGroups.allowWebPublish.indexOf(this.nodeType) > -1;
    },
    hasOgTags() {
      return this.nodeTypeGroups.ogTags.indexOf(this.nodeType) > -1;
    },
    hasReferences() {
      return this.nodeTypeGroups.references.indexOf(this.nodeType) > -1;
    },
    referencedBy() {
      if ($perAdminApp.getView().state.referencedBy) {
        return this.trimReferences($perAdminApp.getView().state.referencedBy.referencedBy);
      }
      return []
    },
    versions() {
      return this.hasVersions ? $perAdminApp.getView().state.versions.versions : []
    },
    isImage() {
      const node = $perAdminApp.findNodeFromPath(
          $perAdminApp.getView().admin.nodes, this.currentObject);
      if (!node) {
        return false;
      }
      const mime = node.mimeType;
      return Object.values(MimeType.Image).indexOf(mime) >= 0
    },
    canGenerateAltText() {
      return typeof this.currentObject === 'string'
          && /\.(png|jpe?g|webp|gif)$/i.test(this.currentObject);
    },
    isVideo() {
      const node = $perAdminApp.findNodeFromPath(
          $perAdminApp.getView().admin.nodes, this.currentObject);
      if (!node) {
        return false;
      }
      const mime = node.mimeType;
      return Object.values(MimeType.Video).indexOf(mime) >= 0
    },
    hasInfoView() {
      return [NodeType.ASSET].indexOf(this.nodeType) > -1;
    },
    hasVersions() {
      return $perAdminApp.getView().state.versions ? $perAdminApp.getView().state.versions.has_versions : false
    },
    nodeName() {
      if (!this.node) return '';
      let nodeName = this.node.name;
      if (this.nodeType === NodeType.OBJECT) {
        nodeName = this.node.path.split('/').slice(-1).pop()
      }
      return nodeName
    },
    getActiveTabName(){
      switch(this.activeTabName) {
        case 'info':
          return "Properties & Information"
        case 'og-tags':
          return "Open Graph Tags"
        case 'versions':
          return "Versioning"
        case 'publishing':
          return "Web Publishing"
        case 'actions':
          return "Actions"
        case 'references':
          return "References"
      }
    },
    selfOrAnyDescendantActivated() {
      const node = this.nodeFromPath;
      if (!node) {
        console.warn('selfOrAnyDescendantActivated() failed')
        return
      }
      return node.activated || node.anyDescendantActivated;
    },
    classForActionDisabledOnActivatedResource() {
      return this.selfOrAnyDescendantActivated ? 'action operationDisabledOnActivatedItem' : 'action';
    },
    stateToolsEdit() {
      const stateTools = $perAdminApp.getNodeFromViewOrNull('/state/tools')
      if (stateTools) {
        return stateTools.edit
      } else {
        return false
      }
    },
  },
  watch: {
    edit(val) {
      $perAdminApp.getNodeFromViewOrNull('/state/tools').edit = val
    },
    activeTab : function(tab) {
      if (tab === 'versions') {
        this.showVersions()
      }
      if (tab === 'publishing') {
        this.updateIsReferencedInPublish()
      }
      if (tab === Tab.ACTIONS) {
        this.loadTranslationConfiguration()
        this.loadAltTextConfiguration()
        if (this.nodeType === NodeType.ASSET) this.refreshAltTextQueueCount()
      }
    },
    currentObject : function(path) {
      if (this.activeTab === Tab.ACTIONS && this.nodeType === NodeType.ASSET) this.refreshAltTextQueueCount()
      this.altTextRequestId++;
      if (this.pendingAltText && this.$refs.altTextCompareModal) this.$refs.altTextCompareModal.close();
      this.generatingAltText = false;
      this.savingAltText = false;
      this.pendingAltText = null;
      if (this.activeTab === 'versions') {
        this.showVersions()
      }
      if (this.activeTab === 'publishing') {
        this.updateIsReferencedInPublish()
      }
      if (this.stateToolsEdit) {
        this.onEdit()
      }
      this.$nextTick(() => {
        if (this.node && this.isImage && !this.node.alt && this.node.title) {
          this.node.alt = this.node.title
        }
      })
    },
    stateToolsEdit(edit) {
      this.edit = edit
    }
  },
  created() {
    this.activeTab = this.tab
  },
  mounted() {
    this.path.selected = this.selectedPath
    this.path.current = this.currentPath
    if (this.activeTab === Tab.ACTIONS) {
      this.loadTranslationConfiguration()
      this.loadAltTextConfiguration()
      if (this.nodeType === NodeType.ASSET) this.refreshAltTextQueueCount()
    }
  },
  beforeDestroy() {
    this.altTextRequestId++;
    if (this.altTextStatusController) this.altTextStatusController.abort();
  },
  methods: {
    async refreshAltTextQueueCount() {
      try {
        const response = await fetch('/perapi/admin/assetAltTextRepublishStatus.json?queue=true',
          {credentials: 'same-origin', cache: 'no-store'});
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data = await response.json();
        this.altTextQueueImageCount = new Set((Array.isArray(data.entries) ? data.entries : [])
          .filter(entry => !entry.manualReview && (entry.assetRepublishable || (entry.eligiblePages && entry.eligiblePages.length)))
          .map(entry => entry.assetPath).filter(Boolean)).size;
      } catch (error) {
        this.altTextQueueImageCount = null;
      }
    },
    async loadAltTextConfiguration() {
      this.altTextConfigured = false;
      try {
        const response = await fetch('/perapi/admin/generateAltText.json');
        if (!response.ok) return;
        const data = await response.json();
        this.altTextConfigured = data.altTextConfigured === true;
      } catch (error) {
        console.warn('Could not check alt text configuration', error);
      }
    },
    async loadTranslationConfiguration() {
      this.translationConfigured = false;
      try {
        const response = await fetch('/perapi/admin/translateNode.json');
        if (!response.ok) {
          this.translationConfigurationMessage = 'Could not check translation configuration. Verify that the admin core bundle is installed.';
          return;
        }
        const data = await response.json();
        if (typeof data.translationConfigured === 'boolean') {
          this.translationConfigured = data.translationConfigured;
        } else {
          this.translationConfigured = !!data.languageMap && Object.keys(data.languageMap).length > 0;
        }
        this.translationConfigurationMessage = this.translationConfigured
            ? ''
            : 'Translation requires a Language Map, Gemini Prompt, Gemini Model, and Gemini API Key in the Translate Node configuration.';
      } catch (error) {
        this.translationConfigurationMessage = 'Could not check translation configuration.';
      }
    },
    async generateAltText() {
      if (!this.canGenerateAltText || !this.altTextConfigured || this.generatingAltText || this.savingAltText) return;
      const path = this.currentObject;
      const asset = this.node;
      const requestId = ++this.altTextRequestId;
      const isCurrentRequest = () => requestId === this.altTextRequestId && this.currentObject === path;
      let phase = 'generate';
      this.generatingAltText = true;
      try {
        const response = await $perAdminApp.getApi().generateAltText(path);
        if (!isCurrentRequest()) return;
        if (!response.data || typeof response.data.altText !== 'string') {
          throw new Error('The alt text service returned an unexpected response.');
        }
        const altText = response.data.altText.trim();
        if (!altText) {
          $perAdminApp.notifyUser('info', 'No clear alt text could be generated for this image.');
          return;
        }
        if (/[<>]/.test(altText)) {
          throw new Error('Generated alt text contains markup and was not saved.');
        }
        const currentAltText = typeof asset.alt === 'string' ? asset.alt.trim() : '';
        if (currentAltText) {
          this.pendingAltText = {
            path,
            asset,
            requestId,
            current: currentAltText,
            generated: altText
          };
          this.$nextTick(() => {
            if (isCurrentRequest() && this.pendingAltText && this.pendingAltText.requestId === requestId) {
              if (this.$refs.altTextCompareModal) this.$refs.altTextCompareModal.open();
            }
          });
        } else {
          phase = 'save';
          await this.saveGeneratedAltText(path, asset, altText, requestId);
        }
      } catch (error) {
        if (!isCurrentRequest()) return;
        const responseData = error.response && error.response.data;
        const message = phase === 'generate' && error.response && error.response.status === 409
            ? 'Alt text service is unavailable. Update Peregrine CMS and try again.'
            : ((responseData && responseData.message) || (error.response && error.response.statusText) || error.message);
        $perAdminApp.notifyUser('error', `Could not ${phase === 'save' ? 'save' : 'generate'} alt text: ` + (message || 'An unexpected error occurred.'));
      } finally {
        if (isCurrentRequest()) {
          this.generatingAltText = false;
          this.savingAltText = false;
        }
      }
    },
    async saveGeneratedAltText(path, asset, altText, requestId = this.altTextRequestId) {
      const isCurrentRequest = () => requestId === this.altTextRequestId && this.currentObject === path;
      if (!isCurrentRequest()) return false;
      this.savingAltText = true;
      try {
        const response = await $perAdminApp.getApi().saveAssetProperties({ ...asset, alt: altText });
        if (isCurrentRequest() && this.node === asset) {
          this.$set(asset, 'alt', altText);
          this.showUnpublishedAltTextPages(response, true, path, altText);
          const preview = altText.length > 500 ? `${altText.substring(0, 500)}...` : altText;
          $perAdminApp.notifyUser('info', `Alt text saved: ${preview}`);
        }
        return isCurrentRequest();
      } catch (error) {
        if (!isCurrentRequest()) return false;
        const responseData = error.response && error.response.data;
        const message = (responseData && responseData.message) || (error.response && error.response.statusText) || error.message;
        $perAdminApp.notifyUser('error', 'Could not save alt text: ' + (message || 'An unexpected error occurred.'));
        return false;
      } finally {
        if (isCurrentRequest()) this.savingAltText = false;
      }
    },
    showUnpublishedAltTextPages(response, forceOpen = false, savedAssetPath = null, savedAltText = '') {
      const data = response && response.data ? response.data : response;
      const pages = data && data.unpublishedPages;
      this.unpublishedAltTextPages = Array.isArray(pages) ? pages : [];
      this.republishedAltTextPages = [];
      this.republishedAltTextAssets = [];
      this.altTextRepublishComplete = false;
      this.altTextRepublishRequested = false;
      this.republishingAltTextPages = false;
      this.altTextRepublishStatusUnavailable = '';
      this.currentAltTextReportToken = data && data.reportToken || null;
      this.currentAltTextAssetPath = data && data.assetPath || savedAssetPath || null;
      this.currentAltTextAssetRepublishable = !!(data && data.assetRepublishable);
      this.currentAltTextValue = savedAltText || this.node && this.node.alt || '';
      this.currentAltTextSavedEntry = null;
      this.altTextQueueEntries = [];
      this.unconfirmedAltTextReports = [];
      if (!data || !data.assetPath || !data.reportToken) {
        if (!forceOpen && !this.unpublishedAltTextPages.length && !(data && data.republishWarning)) return;
        this.republishingAltTextPages = false;
        this.altTextRepublishStatusUnavailable = data && data.republishWarning || '';
        this.openAltTextRepublishModal();
        if (this.currentAltTextAssetPath) {
          this.loadAltTextQueue(this.currentAltTextAssetPath);
          this.refreshAltTextQueueCount();
        }
        return;
      }
      this.republishingAltTextPages = false;
      this.openAltTextRepublishModal();
      this.loadAltTextQueue(data.assetPath);
      this.refreshAltTextQueueCount();
    },
    openAltTextQueue() {
      this.altTextQueueTab = 'images';
      this.currentAltTextReportToken = null;
      this.currentAltTextAssetPath = null;
      this.currentAltTextAssetRepublishable = false;
      this.currentAltTextSavedEntry = null;
      this.altTextQueueEntries = [];
      this.unconfirmedAltTextReports = [];
      this.republishedAltTextPages = [];
      this.republishedAltTextAssets = [];
      this.unpublishedAltTextPages = [];
      this.altTextRepublishComplete = false;
      this.altTextRepublishRequested = false;
      this.republishingAltTextPages = false;
      this.altTextRepublishStatusUnavailable = '';
      this.$nextTick(() => {
        if (this.$refs.unpublishedAltTextPagesModal) this.$refs.unpublishedAltTextPagesModal.open();
      });
      this.loadAltTextQueue();
    },
    async loadAltTextQueue(assetPath = null) {
      const requestId = ++this.altTextQueueRequestId;
      this.loadingAltTextQueue = true;
      this.altTextQueueError = '';
      try {
        const query = new URLSearchParams({queue: 'true'});
        if (assetPath) query.set('path', assetPath);
        const response = await fetch(`/perapi/admin/assetAltTextRepublishStatus.json?${query}`,
          {credentials: 'same-origin', cache: 'no-store'});
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data = await response.json();
        if (requestId !== this.altTextQueueRequestId) return;
        this.altTextQueueEntries = Array.isArray(data.entries) ? data.entries : [];
        if (!assetPath) this.altTextQueueImageCount = new Set(this.altTextQueueEntries
          .filter(entry => !entry.manualReview && (entry.assetRepublishable || (entry.eligiblePages && entry.eligiblePages.length)))
          .map(entry => entry.assetPath).filter(Boolean)).size;
        if (assetPath && this.currentAltTextReportToken) {
          this.currentAltTextSavedEntry = this.altTextQueueEntries.find(entry => this.currentAltTextReportToken
            ? entry.reportToken === this.currentAltTextReportToken
            : entry.assetPath === assetPath) || null;
        }
      } catch (error) {
        if (requestId === this.altTextQueueRequestId) {
          this.altTextQueueError = `Could not load alt text queue (${error.message}).`;
        }
      } finally {
        if (requestId === this.altTextQueueRequestId) this.loadingAltTextQueue = false;
      }
    },
    async sendAltTextQueueAction(entry, action) {
      const body = new URLSearchParams({queue: 'true', path: entry.assetPath,
        reportToken: entry.reportToken, action});
      const response = await fetch('/perapi/admin/assetAltTextRepublishStatus.json', {
        method: 'POST', credentials: 'same-origin', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body,
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
    },
    async removeImageFromAltTextQueue(assetPath) {
      if (this.decidingAltTextQueue || this.republishingAltTextPages) return;
      this.decidingAltTextQueue = true;
      this.altTextQueueError = '';
      let removalError = '';
      try {
        for (const entry of this.altTextQueueEntries.filter(item => item.assetPath === assetPath)) {
          await this.sendAltTextQueueAction(entry, 'discard');
          this.altTextQueueEntries = this.altTextQueueEntries.filter(item => item.reportToken !== entry.reportToken);
        }
      } catch (error) {
        removalError = `Could not remove this image from the queue (${error.message}).`;
      } finally {
        this.decidingAltTextQueue = false;
        await this.loadAltTextQueue();
        if (removalError) {
          this.altTextQueueError = this.altTextQueueError
            ? `${removalError} ${this.altTextQueueError}`
            : removalError;
        }
      }
    },
    async clearAltTextQueue() {
      if (this.decidingAltTextQueue || this.republishingAltTextPages) return;
      this.decidingAltTextQueue = true;
      this.altTextQueueError = '';
      const failures = [];
      try {
        for (const entry of this.altTextQueueEntries.slice()) {
          try {
            await this.sendAltTextQueueAction(entry, 'discard');
          } catch (error) {
            failures.push(`${entry.assetPath}: ${error.message}`);
          }
        }
        await this.loadAltTextQueue();
        if (failures.length) this.altTextQueueError = `Some queue entries could not be cleared: ${failures.join('; ')}`;
      } finally {
        this.decidingAltTextQueue = false;
      }
    },
    async republishAltTextQueue() {
      if (this.decidingAltTextQueue || this.republishingAltTextPages) return;
      const entries = this.republishableAltTextQueueEntries;
      if (!entries.length) return;
      this.decidingAltTextQueue = true;
      this.altTextQueueError = '';
      this.republishedAltTextPages = [];
      this.republishedAltTextAssets = [];
      this.unpublishedAltTextPages = [];
      this.altTextRepublishComplete = false;
      this.altTextRepublishRequested = true;
      this.republishingAltTextPages = true;
      this.unconfirmedAltTextReports = [];
      const accepted = [];
      const failures = [];
      try {
        for (const entry of entries) {
          try {
            await this.sendAltTextQueueAction(entry, 'republish');
            accepted.push(entry);
          } catch (error) {
            failures.push(`${entry.assetPath}: ${error.message}`);
          }
        }
        await this.loadAltTextQueue();
        const results = await Promise.all(accepted.map(async entry => {
          for (let attempt = 0; attempt < 30; attempt += 1) {
            try {
              const query = new URLSearchParams({path: entry.assetPath, reportToken: entry.reportToken});
              const response = await fetch(`/perapi/admin/assetAltTextRepublishStatus.json?${query}`,
                {credentials: 'same-origin', cache: 'no-store'});
              if (!response.ok) throw new Error(`HTTP ${response.status}`);
              const data = await response.json();
              if (data.completed) return data;
            } catch (error) {
              failures.push(`${entry.assetPath}: status check failed (${error.message})`);
              return null;
            }
            await new Promise(resolve => setTimeout(resolve, 1000));
          }
          failures.push(`${entry.assetPath}: republishing was not confirmed within 30 seconds`);
          return null;
        }));
        this.republishedAltTextPages = [...new Set(results.flatMap(result => result && result.republishedPages || []))];
        this.republishedAltTextAssets = [...new Set(results.flatMap(result => result && result.republishedAssets || []))];
        this.unpublishedAltTextPages = [...new Set(results.flatMap(result => result && result.unpublishedPages || []))];
        results.forEach((result, index) => {
          if (result && result.error) failures.push(`${accepted[index].assetPath}: ${result.error}`);
        });
        const unconfirmedCount = results.filter(result => !result).length;
        this.unconfirmedAltTextReports = accepted.filter((entry, index) => !results[index]);
        this.altTextRepublishComplete = unconfirmedCount === 0;
        this.altTextRepublishStatusUnavailable = unconfirmedCount
          ? `Republish status could not be confirmed for ${unconfirmedCount} image${unconfirmedCount === 1 ? '' : 's'}. Verify the published pages manually.`
          : '';
        await this.refreshRepublishedImageNodes(this.republishedAltTextAssets);
        await this.loadAltTextQueue();
        this.altTextQueueError = failures.join('; ');
      } finally {
        this.republishingAltTextPages = false;
        this.decidingAltTextQueue = false;
      }
    },
    async decideAltTextQueue(entry, action) {
      if (this.decidingAltTextQueue) return;
      this.decidingAltTextQueue = true;
      this.altTextQueueError = '';
      try {
        const body = new URLSearchParams({queue: 'true', path: entry.assetPath,
          reportToken: entry.reportToken, action});
        const response = await fetch('/perapi/admin/assetAltTextRepublishStatus.json', {
          method: 'POST', credentials: 'same-origin', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body,
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        this.altTextQueueEntries = this.altTextQueueEntries.filter(item => item.reportToken !== entry.reportToken);
        this.refreshAltTextQueueCount();
        if (action === 'republish') {
          this.currentAltTextAssetPath = entry.assetPath;
          this.currentAltTextReportToken = entry.reportToken;
          this.unpublishedAltTextPages = entry.blockedPages || [];
          this.altTextRepublishComplete = false;
          this.altTextRepublishRequested = true;
          this.republishingAltTextPages = true;
          this.pollAltTextRepublishStatus(entry.assetPath, entry.reportToken);
        } else if (this.currentAltTextReportToken === entry.reportToken) {
          if (this.$refs.altTextDecisionModal) this.$refs.altTextDecisionModal.close();
          this.currentAltTextReportToken = null;
          this.currentAltTextAssetPath = null;
        }
      } catch (error) {
        this.altTextQueueError = `Could not ${action === 'republish' ? 'republish' : 'discard'} this entry (${error.message}).`;
        await this.loadAltTextQueue(this.currentAltTextAssetPath || null);
      } finally {
        this.decidingAltTextQueue = false;
      }
    },
    checkAltTextRepublishStatusAgain() {
      if (!this.altTextRepublishRequested || this.republishingAltTextPages || this.altTextRepublishComplete) return;
      if (this.unconfirmedAltTextReports.length) {
        this.retryUnconfirmedAltTextReports();
        return;
      }
      if (!this.currentAltTextReportToken || !this.currentAltTextAssetPath) return;
      this.altTextRepublishStatusUnavailable = '';
      this.republishingAltTextPages = true;
      this.pollAltTextRepublishStatus(this.currentAltTextAssetPath, this.currentAltTextReportToken);
    },
    async retryUnconfirmedAltTextReports() {
      if (this.republishingAltTextPages || !this.unconfirmedAltTextReports.length) return;
      this.republishingAltTextPages = true;
      this.altTextRepublishStatusUnavailable = '';
      this.altTextQueueError = '';
      const reports = this.unconfirmedAltTextReports.slice();
      const statusErrors = [];
      try {
        const results = await Promise.all(reports.map(async entry => {
          let lastError = '';
          for (let attempt = 0; attempt < 30; attempt += 1) {
            try {
              const query = new URLSearchParams({path: entry.assetPath, reportToken: entry.reportToken});
              const response = await fetch(`/perapi/admin/assetAltTextRepublishStatus.json?${query}`,
                {credentials: 'same-origin', cache: 'no-store'});
              if (!response.ok) throw new Error(`HTTP ${response.status}`);
              const data = await response.json();
              if (data.completed) return data;
            } catch (error) {
              lastError = error.message;
            }
            await new Promise(resolve => setTimeout(resolve, 1000));
          }
          statusErrors.push(`${entry.assetPath}: ${lastError
            ? `status check failed (${lastError})`
            : 'republishing was not confirmed within 30 seconds'}`);
          return null;
        }));
        this.republishedAltTextPages = [...new Set([
          ...this.republishedAltTextPages,
          ...results.flatMap(result => result && result.republishedPages || []),
        ])];
        this.republishedAltTextAssets = [...new Set([
          ...this.republishedAltTextAssets,
          ...results.flatMap(result => result && result.republishedAssets || []),
        ])];
        this.unpublishedAltTextPages = [...new Set([
          ...this.unpublishedAltTextPages,
          ...results.flatMap(result => result && result.unpublishedPages || []),
        ])];
        results.forEach((result, index) => {
          if (result && result.error) statusErrors.push(`${reports[index].assetPath}: ${result.error}`);
        });
        this.unconfirmedAltTextReports = reports.filter((entry, index) => !results[index]);
        this.altTextRepublishComplete = this.unconfirmedAltTextReports.length === 0;
        this.altTextRepublishStatusUnavailable = this.unconfirmedAltTextReports.length
          ? `Republish status could not be confirmed for ${this.unconfirmedAltTextReports.length} image${this.unconfirmedAltTextReports.length === 1 ? '' : 's'}. Verify the published pages manually.`
          : '';
        await this.refreshRepublishedImageNodes(results.flatMap(result => result && result.republishedAssets || []));
        await this.loadAltTextQueue();
        this.altTextQueueError = statusErrors.join('; ');
      } finally {
        this.republishingAltTextPages = false;
      }
    },
    openAltTextRepublishModal() {
      this.$nextTick(() => {
        if (this.$refs.altTextDecisionModal) this.$refs.altTextDecisionModal.open();
      });
    },
    async pollAltTextRepublishStatus(assetPath, reportToken) {
      if (this.altTextStatusController) this.altTextStatusController.abort();
      const controller = new AbortController();
      this.altTextStatusController = controller;
      const isCurrentPoll = () => !controller.signal.aborted
        && this.altTextStatusController === controller
        && this.currentAltTextReportToken === reportToken;
      const maxAttempts = 30;
      let retryError = '';
      try {
        for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
          if (!isCurrentPoll()) return;
          try {
            const query = new URLSearchParams({path: assetPath, reportToken});
            const response = await fetch(`/perapi/admin/assetAltTextRepublishStatus.json?${query}`, {
              credentials: 'same-origin', signal: controller.signal,
            });
            const data = await response.json();
            if (!isCurrentPoll()) return;
            if (!response.ok) {
              const detail = data && data.message ? ` ${data.message}` : '';
              throw new Error(`Republish status request failed (HTTP ${response.status}).${detail}`);
            }
            if (data && data.completed) {
              this.republishedAltTextPages = Array.isArray(data.republishedPages) ? data.republishedPages : [];
              this.republishedAltTextAssets = Array.isArray(data.republishedAssets) ? data.republishedAssets : [];
              if (Array.isArray(data.unpublishedPages)) {
                this.unpublishedAltTextPages = [...new Set([...this.unpublishedAltTextPages, ...data.unpublishedPages])];
              }
              this.republishingAltTextPages = false;
              this.altTextRepublishComplete = true;
              this.altTextRepublishStatusUnavailable = data.error || '';
              await this.refreshRepublishedImageNodes(this.republishedAltTextAssets);
              return;
            }
            if (data && data.retrying) {
              retryError = data.error || '';
              this.altTextRepublishStatusUnavailable = retryError
                ? `Republishing is retrying: ${retryError}`
                : 'Republishing is retrying.';
            }
          } catch (error) {
            if (!isCurrentPoll()) return;
            this.republishingAltTextPages = false;
            this.altTextRepublishStatusUnavailable = `The image was saved, but ${error.message || 'republish results could not be loaded.'}`;
            return;
          }
          await new Promise(resolve => {
            let timer;
            const finish = () => {
              clearTimeout(timer);
              controller.signal.removeEventListener('abort', finish);
              resolve();
            };
            timer = setTimeout(finish, 1000);
            controller.signal.addEventListener('abort', finish, {once: true});
          });
        }
        if (!isCurrentPoll()) return;
        this.republishingAltTextPages = false;
        this.altTextRepublishStatusUnavailable = retryError
          ? `Republishing was not confirmed within 30 seconds. Last reported error: ${retryError} Check again or review the server log.`
          : 'Republishing was not confirmed within 30 seconds. Check again or review the server log.';
      } finally {
        if (this.altTextStatusController === controller) this.altTextStatusController = null;
      }
    },
    async refreshRepublishedImageNodes(assetPaths) {
      const paths = [...new Set((assetPaths || []).filter(path => typeof path === 'string' && path))];
      const parents = [...new Set(paths.map(path => path.substring(0, path.lastIndexOf('/'))))];
      await Promise.all(parents.map(parent => $perAdminApp.getApi().populateNodesForBrowser(parent)));
    },
    async republishQueuedImage(entry) {
      if (!entry || this.decidingAltTextQueue || this.republishingAltTextPages) return;
      this.decidingAltTextQueue = true;
      this.altTextQueueError = '';
      this.currentAltTextAssetPath = entry.assetPath;
      this.currentAltTextReportToken = entry.reportToken;
      this.unpublishedAltTextPages = entry.blockedPages || [];
      this.republishedAltTextPages = [];
      this.republishedAltTextAssets = [];
      this.altTextRepublishComplete = false;
      this.altTextRepublishRequested = true;
      this.altTextRepublishStatusUnavailable = '';
      try {
        await this.sendAltTextQueueAction(entry, 'republish');
        this.republishingAltTextPages = true;
        await this.pollAltTextRepublishStatus(entry.assetPath, entry.reportToken);
        if (this.altTextRepublishComplete && !this.altTextRepublishStatusUnavailable) {
          this.altTextQueueEntries = this.altTextQueueEntries.filter(item => item.reportToken !== entry.reportToken);
          this.currentAltTextSavedEntry = null;
          this.refreshAltTextQueueCount();
          await this.refreshRepublishedImageNodes(this.republishedAltTextAssets);
          await this.loadAltTextQueue();
        }
      } catch (error) {
        this.altTextQueueError = `Could not republish ${entry.assetPath} (${error.message}).`;
        await this.loadAltTextQueue();
      } finally {
        this.republishingAltTextPages = false;
        this.decidingAltTextQueue = false;
      }
    },
    pageEditorUrl(path) {
      return `/content/admin/pages/pages/edit.html/path:${path}`;
    },
    keepCurrentAltText() {
      this.$refs.altTextCompareModal.close();
    },
    async useGeneratedAltText() {
      const pending = this.pendingAltText;
      if (!pending || this.savingAltText || pending.requestId !== this.altTextRequestId) return;
      const saved = await this.saveGeneratedAltText(pending.path, pending.asset, pending.generated, pending.requestId);
      if (saved && pending.requestId === this.altTextRequestId && this.$refs.altTextCompareModal) {
        this.$refs.altTextCompareModal.close();
      }
    },
    itemToTarget(path) {
      const ret = { path, target: path }
      const tenant = $perAdminApp.getNodeFromViewOrNull('/state/tenant')
      if(path.startsWith(`/content/${tenant.name}/pages`)) {
        ret.path = `/content/admin/pages/pages/edit.html/path:${path}`
      } else if (path.startsWith(`/content/${tenant.name}/templates`)) {
        ret.path = `/content/admin/pages/templates/edit.html/path:${path}`
      } else {
        const segments = path.split('/')
        if(segments.length > 0) {
          segments.pop()
        }
        path = segments.join('/')
        ret.target = path
        if (path.startsWith(`/content/${tenant.name}/assets`)) {
          ret.load = ret.path = `/content/admin/pages/assets.html/path:${path}`
          ret.type = 'ASSET'
        } else if (path.startsWith(`/content/${tenant.name}/objects`)) {
          ret.load = ret.path = `/content/admin/pages/objects.html/path:${path}`
          ret.type = 'OBJECT'
        }
      }
      return ret
    },
    getSchema(schemaKey) {
      if (!this.node) {
        return null;
      }
      const view = $perAdminApp.getView();
      let component = this.node.component;
      if (this.nodeType === NodeType.ASSET) {
        component = 'admin-components-assetview';
      }
      if (this.nodeType === NodeType.OBJECT) {
        component = this.getObjectComponent();
      }
      const componentDefinitions = view.admin.componentDefinitions
      if (!componentDefinitions) {
        return {}
      }
      const cmpDefinition = view.admin.componentDefinitions[component]
      if (!cmpDefinition) {
        return {}
      }
      let schema = cmpDefinition[schemaKey];
      if (this.edit) {
        return schema;
      }
      if (!schema) {
        return {};
      }
      schema = deepClone(schema);
      schema.fields.forEach((field) => {
        field.preview = true;
        field.readonly = true;
        if (field.fields) {
          field.fields.forEach((field) => {
            field.readonly = true;
          });
        }
      });
      return schema;
    },
    getSchemaByActiveTab() {
      if (this.nodeType === NodeType.FILE) {
        return this.getGeneratedFileSchema();
      } else if (this.activeTab === Tab.INFO) {
        return this.getSchema(SchemaKey.MODEL);
      } else if (this.activeTab === Tab.OG_TAGS) {
        return this.getSchema(SchemaKey.OG_TAGS);
      } else {
        return {};
      }
    },
    getObjectComponent() {
      let resourceType = this.rawCurrentObject.data['component'];
      if (!resourceType) {
        resourceType = this.rawCurrentObject.data['sling:resourceType'];
      }
      return resourceType.split('/').join('-');
    },
    capFirstLetter(string) {
      return string.charAt(0).toUpperCase() + string.slice(1);
    },
    onEdit() {
      this.edit = true
      this.formGenerator.original = deepClone(this.node)

      if (this.nodeType === NodeType.OBJECT) {
        $perAdminApp.stateAction('editObject', {selected: this.currentObject, schema: this.getSchemaByActiveTab()})
      }
    },
    onCancel() {
      const payload = {selected: this.currentObject}
      this.edit = false
      let node = this.node
      this.formGenerator.changes.forEach((ch) => {
        node[ch.key] = ch.oldVal
      })
      this.formGenerator.changes = []
    },
    onModelUpdate(newVal, schemaKey) {
      if (this.edit && this.formGenerator.original) {
        this.formGenerator.changes.push({
          key: schemaKey,
          oldVal: this.formGenerator.original[schemaKey],
          newVal: newVal
        })
      }
      this.autoFillAltFromAsset(newVal, schemaKey)
    },
    onValidated(isValid, errors) {
      if (this.edit) {
        return;
      }
      this.valid.state = isValid;
      this.valid.errors = errors;
    },

    async autoFillAltFromAsset(newVal, schemaKey) {
      if (!newVal || typeof newVal !== 'string' || !newVal.startsWith('/content/')) return
      const schema = this.getSchema(SchemaKey.MODEL)
      if (!schema || !schema.fields) return
      const findField = (fields) => {
        if (!fields) return null
        for (const f of fields) {
          if (f.model === schemaKey) return f
          if (f.fields) {
            const found = findField(f.fields)
            if (found) return found
          }
        }
        return null
      }
      const field = findField(schema.fields)
      if (!field) return
      let altModelKey = null
      if (field.defaultAltFromAsset === true) {
        altModelKey = schemaKey + 'Alt'
        if (field.model !== altModelKey) {
          const byDefaultFrom = (fields) => {
            for (const f of fields) {
              if (f.defaultFrom === schemaKey) return f
              if (f.fields) {
                const found = byDefaultFrom(f.fields)
                if (found) return found
              }
            }
            return null
          }
          const targetField = byDefaultFrom(schema.fields)
          if (targetField) {
            altModelKey = targetField.model
          }
        }
      }
      if (!altModelKey) return
      if (this.node[altModelKey]) return
      const rootData = this.$root && this.$root.$data
      const adminNodes = rootData && rootData.admin && rootData.admin.nodes
      const text = await resolveMediaText(newVal, adminNodes)
      if (text && !this.node[altModelKey]) {
        this.node[altModelKey] = text
      }
    },

    onConfirmDialog (event) {
      if (event === 'confirm') {
        const isValid = this.$refs.renameForm.validate()
        if (isValid) {
          this.performRenameNode(this.formmodel.name, this.formmodel.title || "")
        } else {
          return
        }
      }
      this.nameChanged = false
      this.formmodel.name = ''
      this.formmodel.title = ''
      this.$refs.renameForm.clearValidationErrors()
      this.$refs.renameModal.close()
    },
    onReady (event) {
      this.formmodel.name = this.node.name
      this.formmodel.title = this.node.title
    },

    performRenameNode(newName) {
      const vm = this;
      $perAdminApp.stateAction(`rename${this.uNodeType}`, {
        path: this.currentObject,
        name: newName,
        edit: this.isEdit
      }).then((data) => {
        if (vm.nodeType === 'asset' || vm.nodeType === 'object') {
          const currNode = $perAdminApp.getNodeFromView(`/state/tools/${vm.nodeType}/show`)
          const currNodeArr = currNode.split('/');
          currNodeArr[currNodeArr.length - 1] = newName
          $perAdminApp.getNodeFromView(`/state/tools/${vm.nodeType}`).show = currNodeArr.join(
              '/')
        } else if (vm.nodeType === NodeType.FILE) {
          $perAdminApp.stateAction('selectFile', {path: data.destination, resourceType: 'nt:file'});
        } else { // page and template handling
          const currNode = $perAdminApp.getNodeFromView('/state/tools')[vm.nodeType]
          const currNodeArr = currNode.split('/');
          currNodeArr[currNodeArr.length - 1] = newName
          $perAdminApp.getNodeFromView('/state/tools')[vm.nodeType] = currNodeArr.join('/')
        }
        this.setActiveTab(Tab.INFO)
      })
    },

    openPublishingModal(){
      console.log("Open Publishing Modal")
      this.isPublishDialogOpen = true;
    },
    unPublishResource(me, path) {
      if (me.anyDescendantActivated) {
          $perAdminApp.toast("One of the children of this resource is still published. Please unpublish all of them first.", "warn", 5000)
      }
      else if (me.isReferencedInPublish) {
          $perAdminApp.askUser('Warning',
              ("Unpublishing may break references. Would you like to continue ?"), {
                  yesText: 'Yes',
                  yes: function yes() {
                      $perAdminApp.stateAction('unreplicate', path);
                  },
              });
      }
      else {
          $perAdminApp.stateAction('unreplicate', path);
      }
    },
    closePublishing(){
      console.log("Close Publishing Modal")
      this.isPublishDialogOpen = false;
    },

    checkActivationStatusAndPerform(action) {
      if (this.nodeFromPath.activated) {
        $perAdminApp.toast("The resource is still published. Please unpublish it first.", "warn", 5000);
      } else if (this.nodeFromPath.anyDescendantActivated) {
        $perAdminApp.toast("One of the children of this resource is still published. Please unpublish all of them first.", "warn", 5000);
      } else if (this.nodeFromPath.isReferenced) {
        $perAdminApp.askUser('Warning', "Deleting may break references. Would you like to continue ?", {
          yesText: 'Yes',
          yes: function yes() {
            action();
          }
        });
      }
      else {
        action();
      }
    },

    renameNode() {
      // initialize with existing values
      if (this.formmodel && !this.formmodel.title) this.formmodel.title = this.node.title
      if (this.formmodel && !this.formmodel.name) this.formmodel.name = this.node.name
      this.checkActivationStatusAndPerform(() => {
        this.$refs.renameModal.open();
        this.$nextTick(() => {
          this.$refs.renameForm.$el.querySelector('input').focus()
        })
      });
    },

    moveNode() {
      this.checkActivationStatusAndPerform(() => {
        $perAdminApp.getApi().populateNodesForBrowser(this.path.current, 'pathBrowser')
            .then(() => {
              this.path.selected = this.browserRoot
              this.isOpen = true;
            }).catch(() => {
          this.path.selected = this.browserRoot
          $perAdminApp.getApi().populateNodesForBrowser(`/content/${site.tenant}`, 'pathBrowser');
        });
      });
    },

    copyNode() {
      $perAdminApp.getApi().populateNodesForBrowser(this.path.current, 'pathBrowser')
          .then(() => {
            this.path.selected = this.browserRoot
            this.isCopyOpen = true;
          }).catch(() => {
        this.path.selected = this.browserRoot
        $perAdminApp.getApi().populateNodesForBrowser(`/content/${site.tenant}`, 'pathBrowser');
      });

    },

    deleteNode() {
      this.checkActivationStatusAndPerform(() => {
        const me = this
        me.onDelete(this.nodeType, this.node.path).then(() => {
          $perAdminApp.stateAction(`unselect${me.uNodeType}`, {})
        }).then(() => {
          const path = $perAdminApp.getNodeFromView('/state/tools/pages')
          if (path) {
            $perAdminApp.loadContent(
              '/content/admin/pages/pages.html/path' + SUFFIX_PARAM_SEPARATOR + path)
          }

          me.isOpen = false
        })
      });
    },

    openTranslationsModal(){
      if (!this.translationConfigured) return;
      this.$refs.translationsModal.open();
    },

    setCurrentPath(path) {
      this.path.current = path;
    },
    setSelectedPath(path) {
      this.path.selected = path;
    },
    showVersions() {
      $perAdminApp.getApi().populateVersions(this.currentObject);
    },
    deleteVersion(me, target) {
      $perAdminApp.stateAction('deleteVersion', { path: target.path, version: target.version.name });
    },
    createVersion(){
      $perAdminApp.stateAction('createVersion', this.currentObject);
    },
    checkoutVersion(version){
      if(version.base === true){
        $perAdminApp.notifyUser('Info', 'You cannot checkout the current version')
        return
      }
      let self = this;
      $perAdminApp.askUser('Restore Version',
          `Would you like to restore ${version.name}? You may lose work unless you create a new version saving the current state.`, {
            yesText: 'Yes',
            noText: 'No',
            yes() {
              $perAdminApp.stateAction('restoreVersion', {path: self.currentObject, versionName: version.name});
            },
            no() {
              console.log('no')
            }
          })
    },

    // after copy dialog
    onCopySelect() {
      // Assets are not a nt:file, they are per:Asset. Using copyFile() on an asset gives it resouceType of nt:file and I could not seem to prevent that.
      if (this.node.resourceType === 'nt:file') {
        let to = this.path.selected

        if (!to) {
          const split = this.currentObject.split('/');
          split.pop();
          to = split.join('/')
        }

        $perAdminApp.stateAction('copyFile', {
          from: this.currentObject,
          to,
          resourceType: this.node.resourceType,
          mimeType: this.node.mimeType,
        });
      } else {
        $perAdminApp.stateAction('copyPage', {
          srcPath: this.currentObject,
          targetPath: this.path.selected,
          resourceType: this.node.resourceType,
          mimeType: this.node.mimeType,
        }).then(() => {
          setTimeout(() => {
            $perAdminApp.loadContent(`/content/admin/pages/${this.nodeType}s.html/path${SUFFIX_PARAM_SEPARATOR}${this.path.selected}`, false);
          }, 100);
        });
      }
      this.isCopyOpen = false;
    },
    onCopyCancel() {
      this.isCopyOpen = false;
    },
    onMoveCancel() {
      this.isOpen = false;
    },
    onMoveSelect() {
      $perAdminApp.stateAction(`move${this.uNodeType}`, {
        path: this.node.path,
        to: this.path.selected,
        type: 'child'
      });
      $perAdminApp.stateAction(`unselect${this.uNodeType}`, {});
      this.isOpen = false;
    },
    async save() {
      // Validate telephone inputs using intlTelInput
      const telInputs = document.querySelectorAll('.iti__tel-input');
      for (const input of telInputs) {
        if (input.iti && !input.value.trim() && input.dataset.model) {
          const deleteProps = this.node._opDeleteProps || [];
          if (deleteProps.indexOf(input.dataset.model) === -1) {
            deleteProps.push(input.dataset.model);
          }
          this.node._opDeleteProps = deleteProps;
          set(this.node, input.dataset.model, '');
        }

        if (input.iti && input.value.trim() && !input.iti.isValidNumber()) {
          // Set error on VFG's fieldErrors to trigger UI feedback
          if (this.$refs.vfg && this.$refs.vfg.fieldErrors) {
            const telField = this.$refs.vfg.schema.fields.find(f => f.inputType === 'tel');
            if (telField) {
              this.$refs.vfg.fieldErrors[telField.model] = ['Invalid phone number'];
            }
          }
          $perAdminApp.notifyUser('error', 'Please fix validation errors before saving')
          return
        }
      }

      if (this.$refs.vfg && this.$refs.vfg.validate) {
        const isValid = await this.$refs.vfg.validate()
        if (!isValid) {
          $perAdminApp.notifyUser('error', 'Please fix validation errors before saving')
          return
        }
      }
      const savedPath = this.node.path;
      const previousAltText = this.nodeType === NodeType.ASSET
        ? (this.formGenerator.original ? this.formGenerator.original.alt : this.node.alt)
        : undefined;
      let promise
      if (this.nodeType === NodeType.OBJECT) {
        promise = this.saveObject();
      } else {
        promise = $perAdminApp.stateAction(`save${this.uNodeType}Properties`, this.node);
        this.edit = false;
      }

      promise.then((response) => {
        if (this.currentObject === savedPath) {
          const responseData = response && response.data ? response.data : response;
          const imageChanged = this.nodeType === NodeType.ASSET
            && ((responseData && responseData.imageChanged === true)
              || this.node.alt !== previousAltText
              || (responseData && responseData.republishWarning));
          this.showUnpublishedAltTextPages(response, imageChanged, savedPath, this.node.alt);
        }
        return $perAdminApp.getApi().populateNodesForBrowser(savedPath.split('/').slice(0, -1).join('/'));
      }).catch((error) => {
        const responseData = error && error.response && error.response.data;
        const serverException = responseData && Array.isArray(responseData.exception)
          ? responseData.exception.slice(0, 4).join(' ')
          : '';
        const detail = serverException || (responseData && responseData.message)
          || (error && error.message) || 'The server rejected the save without returning error details.';
        console.error(`Could not save ${savedPath}`, responseData || error || 'Request rejected without an error object');
        $perAdminApp.notifyUser('error', `Could not save ${savedPath}: ${detail}`);
      })
    },
    saveObject() {
      let data = this.node;
      let {show} = this.rawCurrentObject;
      let _deleted = $perAdminApp.getNodeFromViewWithDefault('/state/tools/_deleted', {});

      //Find child nodes with subchildren for our edited object
      for (const key in data) {
        if (!data.hasOwnProperty(key)) {
          continue;
        }
        //If node (or deleted node) is an array of objects then we have a child node
        if ((Array.isArray(data[key]) && data[key].length && typeof data[key][0] === 'object') ||
            (Array.isArray(_deleted[key]) && _deleted[key].length && typeof _deleted[key][0]
                === 'object')) {

          let node = data[key];

          //loop through children
          let targetNode = {};
          //Insert deleted children
          for (const j in _deleted[key]) {
            if (!_deleted[key].hasOwnProperty(j)) {
              continue;
            }
            const deleted = _deleted[key][j];
            targetNode[deleted.name] = deleted;
          }
          //Insert children
          for (const i in node) {
            if (!node.hasOwnProperty(i)) {
              continue;
            }
            const child = node[i];
            targetNode[child.name] = child;
          }
          data[key] = targetNode;
        }
      }
      set($perAdminApp.getView(), '/state/tools/save/confirmed', true)

      const result = $perAdminApp.stateAction('saveObjectEdit', {data: data, path: show, schema: this.getSchemaByActiveTab()}).then(() => {
        $perAdminApp.getNodeFromView('/state/tools')._deleted = {}
      });
      $perAdminApp.stateAction('selectObject', {selected: show})
      this.edit = false;
      return result
    },
    setActiveTab(clickedTab) {
      this.activeTab = clickedTab;
      $perAdminApp.action(this, 'setActiveTabName', {activeTab: this.activeTab})
    },
    setActiveTabName(me, target){
      me.activeTabName = target.activeTab
    },
    isTab(arg) {
      if (Array.isArray(arg)) {
        return arg.indexOf(this.activeTab) > -1;
      }
      return this.activeTab === arg;
    },
    openLiveVersion() {
      const view = $perAdminApp.getView()
      const page = get(view, '/pageView/page', null)

      if (!page) return;

      const {
        primaryDomain,
        pagePath
      } = page

      if (primaryDomain && pagePath) {
        const tenant = pagePath.split('/')[1]

        window.open(`${primaryDomain}${pagePath}.html`, `${tenant}-live-version`)
      }
    },
    updateIsReferencedInPublish() {
      this.isReferencedInPublish = true;
      const path = this.currentObject;
      if (!path) {
        return;
      }

      $perAdminApp.getApi().isReferencedInPublish(path)
        .then(data => {
          this.isReferencedInPublish = data.result;
        }).catch(() => {
          this.isReferencedInPublish = false;
        });
    },

    getGeneratedFileSchema() {
      return {
        fields: [
          {
            type: 'input',
            inputType: 'text',
            label: 'Name',
            model: 'name',
            readonly: true,
            preview: true
          },
          {
            type: 'material-datetime',
            inputType: 'text',
            label: 'Created',
            model: 'created',
            readonly: true,
            preview: true
          },
          {
            type: 'input',
            inputType: 'text',
            label: 'Created by',
            model: 'createdBy',
            readonly: true,
            preview: true
          },
        ]
      };
    },
    openModal() {
      this.modalVisible = true;
      this.$nextTick(() => {
        this.$refs.previewModal.focus();
      });
    },
    closeModal() {
      this.modalVisible = false;
    }
  }
}
</script>

<style>
.alt-text-queue-entry {
  border-bottom: 1px solid #ddd;
  padding: 12px 0;
}
.alt-text-queue-preview {
  align-items: center;
  display: flex;
  gap: 12px;
  text-align: left;
}
.alt-text-queue-preview img {
  height: 72px;
  object-fit: cover;
  width: 72px;
}
.alt-text-queue-preview span span, .alt-text-queue-path {
  display: block;
}
.alt-text-queue-path {
  overflow-wrap: anywhere;
}
.alt-text-queue-image-hover {
  position: relative;
  cursor: default;
  overflow-wrap: anywhere;
}
.alt-text-queue-image-tooltip {
  display: none;
  position: absolute;
  top: 100%;
  left: 0;
  z-index: 10;
  padding: 5px;
  background: white;
  border: 1px solid #ddd;
  box-shadow: 0 2px 8px rgba(0, 0, 0, .2);
}
.alt-text-queue-image-hover:hover .alt-text-queue-image-tooltip,
.alt-text-queue-image-hover:focus .alt-text-queue-image-tooltip {
  display: block;
}
.alt-text-queue-image-tooltip img {
  display: block;
  max-width: 180px;
  max-height: 140px;
  object-fit: contain;
}
.alt-text-queue-tabs {
  display: flex;
  border-bottom: 1px solid #ddd;
  margin-bottom: 12px;
}
.alt-text-queue-tabs button {
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  font: inherit;
  padding: 10px 16px;
}
.alt-text-queue-tabs button.is-active {
  border-bottom-color: #26a69a;
  color: #00796b;
}
.deleteVersionWrapper {
  margin-left: auto;
}
.labelChip {
  display: block;
  width: fit-content;
  white-space: nowrap;
}
.operationDisabledOnActivatedItem {
  opacity: 0.4;
  cursor: default!important;
}
</style>

<style scoped>
.alt-text-comparison {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
}

.alt-text-value {
    box-sizing: border-box;
    width: 100%;
    margin-top: 8px;
    padding: 8px;
    border: 1px solid #9e9e9e;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    color: #333;
}

.generate-alt-text-action {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    text-align: left;
    font: inherit;
    color: inherit;
    background: transparent;
    border: 0;
}

.generate-alt-text-action svg {
    width: 24px;
    height: 24px;
    flex: none;
}

.generate-alt-text-action:disabled {
    opacity: 0.6;
    cursor: wait;
}

.generate-alt-text-action.operationDisabledOnActivatedItem:disabled {
    opacity: 0.4;
    cursor: default;
}

@media (max-width: 600px) {
    .alt-text-comparison {
        grid-template-columns: 1fr;
    }
}

.info-view-image {
    cursor: pointer;
}

.info-view-video {
    width: 100%;
    height: 100%;
}

.explorer-preview .explorer-preview-content.preview-asset .asset-info-view img {
    max-height: 50vh;
    height: 100%;
    width: 100%;
    object-fit: contain;
}

.modal-overlay {
  position: fixed;
  top: 0; left: 0;
  width: 100vw; height: 100vh;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;

    .modal-content {
        img {
            width: auto;
            height: auto;
            object-fit: contain;
            min-width: 50vw;
            min-height: 50vh;
            max-width: 90vw;
            max-height: 90vh;
            display: block;
            margin: auto;
        }

        img {
            pointer-events: none;
        }

        button {
            position: absolute;
            top: 10px;
            right: 10px;
            display: flex;
            background-color: white;
            color: black;
            border: 2px solid black;
            border-radius: 100%;
            aspect-ratio: 1 / 1;
            align-items: center;

            &:hover, &:focus, &:active {
                background-color: black;
                color: white;
                border: 2px solid white;
            }
        }
    }
}
</style>
