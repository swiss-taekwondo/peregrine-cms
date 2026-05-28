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
                      target: path,
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
          <div v-if="!isCheckingPublishability && !canPublishToWeb && nodeType !== NodeType.FILE" class="publishability-note">
            <i class="material-icons">warning</i>
            <span>{{ publishabilityReason || 'Publishing not possible as there are some errors' }}</span>
          </div>
          <div v-if="nodeType === NodeType.PAGE || nodeType === NodeType.TEMPLATE" class="action" v-bind:class="{'operationDisabledOnActivatedItem': isPageCheckSummaryLoading || isVerifyingLinks}" title="Check the page before publishing" @click="isPageCheckSummaryLoading || isVerifyingLinks ? null : openPageCheckModal()">
            <i class="material-icons">playlist_add_check</i>
            <span>Page Check</span>
            <admin-components-materializespinner v-if="isPageCheckSummaryLoading || isVerifyingLinks" class="page-check-spinner"/>
            <span v-else class="page-check-action-icons">
              <template v-if="pageCheckHasRun">
              <i v-if="currentIssueCount === 0" class="material-icons page-check-action-ok">check_circle</i>
              <span v-else-if="currentIssueCount > 0" class="page-check-action-error">
                <i class="material-icons">error_outline</i>
                <span class="page-check-action-count">{{ currentIssueCount }}</span>
              </span>
              </template>
              <span v-else><i class="material-icons page-check-action-pending">pending</i></span>
            </span>
          </div>
          <div v-if="isCheckingPublishability" class="action publishability-loading" title="Checking if this page can be published">
            <admin-components-materializespinner/>
            Checking publish status
          </div>
          <div v-else-if="canPublishToWeb" class="action" :title="publishabilityTitle" @click="openPublishingModal()">
            <i class="material-icons">publish</i>
            Publish to Web ({{nodeType}})
          </div>
          <div v-else class="action operationDisabledOnActivatedItem" :title="'Publishing not possible'">
            <span>
              <i class="material-icons">warning</i>
              Publish to Web ({{nodeType}})
            </span>
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

      <admin-components-pagecheckmodal
          v-if="isPageCheckDialogOpen"
          v-bind:isOpen="isPageCheckDialogOpen"
          v-bind:path="currentObject"
          v-bind:node="pageCheckNode"
          v-bind:linkVerificationResults="linkVerificationResults"
          v-bind:verifying="isVerifyingLinks"
          v-on:complete="closePageCheck"
          v-on:summary="onPageCheckSummary"
          v-on:edit-page-properties="editPagePropertiesFromCheck"
          v-on:reverify-links="verifyLinks"
          v-on:purge-and-reverify="purgeAndReverify"
          modalTitle="Page Check">
      </admin-components-pagecheckmodal>

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
          <div v-if="allowTranslate" class="action" :title="`translate ${nodeType}`" @click="openTranslationsModal()">
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
import {IconLib, MimeType, NodeType, SUFFIX_PARAM_SEPARATOR} from '../../../../../../js/constants'
import {deepClone, get, set} from '../../../../../../js/utils'
import NodeNameValidation from '../../../../../../js/mixins/NodeNameValidation'
import ReferenceUtil from '../../../../../../js/mixins/ReferenceUtil'
import Icon from '../icon/template.vue'
import PathBrowser from '../pathbrowser/template.vue'
import MaterializeModal from '../materializemodal/template.vue'
import ConfirmDialog from '../confirmdialog/template.vue'
import Action from '../action/template.vue'
import ExplorerPreviewNavItem from '../explorerpreviewnavitem/template.vue'
import IconEditPage from '../iconeditpage/template.vue'
import LinearPreloader from '../linearpreloader/template.vue'
import PageCheckModal from '../pagecheckmodal/template.vue'
import MaterializeSpinner from '../materializespinner/template.vue'

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
    IconEditPage,
    'admin-components-pagecheckmodal': PageCheckModal,
    'admin-components-materializespinner': MaterializeSpinner
  },
  props: {
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
      isPageCheckDialogOpen: false,
      modalVisible: false,
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
        allowWebPublish: [NodeType.PAGE, NodeType.TEMPLATE, NodeType.FILE],
      },
      path: {
        current: null,
        selected: null
      },
      formGenerator: {
        changes: []
      },
      loading: false,
      isReferencedInPublish: true,
      isPublishable: false,
      isPublishabilityChecked: false,
      isPublishabilityLoading: false,
      publishabilityReason: '',
      pageCheckSummary: null,
      isPageCheckSummaryLoading: false,
      linkVerificationResults: [],
      pageCheckHasRun: false,
      isPurgeRecheck: false,
      isVerifyingLinks: false,
    }
  },
  mixins: [NodeNameValidation,ReferenceUtil],
  computed: {
    brokenLinkCount() {
      return (this.linkVerificationResults || []).filter(r => !r.ok && !r.redirect).length;
    },
    redirectIncorrectCount() {
      return (this.linkVerificationResults || []).filter(r => r.redirect && !r.finalOk && !r.loginRedirect).length;
    },
    currentIssueCount() {
      if (this.pageCheckSummary) return this.pageCheckSummary.issueCount;
      return this.brokenLinkCount + this.redirectIncorrectCount;
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
    pageCheckNode() {
      const page = get($perAdminApp.getView(), '/pageView/page', null);
      if (page) {
        return page;
      }
      return this.nodeFromPath;
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
    canPublishToWeb() {
      if (this.nodeType === NodeType.FILE) {
        return true;
      }
      return this.isPublishabilityChecked && this.isPublishable;
    },
    isCheckingPublishability() {
      return this.nodeType !== NodeType.FILE
          && (!this.isPublishabilityChecked || this.isPublishabilityLoading);
    },
    publishabilityTitle() {
      if (this.nodeType === NodeType.FILE) {
        return `Open Web Publishing ${this.nodeType} Dialog`;
      }
      if (this.canPublishToWeb) {
        return `Open Web Publishing ${this.nodeType} Dialog`;
      }
      if (this.isPublishabilityLoading) {
        return 'Checking if this page can be published';
      }
      return this.publishabilityReason || 'Publishing not possible as there are some errors';
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
        this.updateIsPublishable()
      }
    },
    currentObject : function(path) {
      this.pageCheckSummary = null;
      this.isPageCheckSummaryLoading = false;
      this.linkVerificationResults = [];
      if (this.activeTab === 'versions') {
        this.showVersions()
      }
      if (this.activeTab === 'publishing') {
        this.updateIsReferencedInPublish()
        this.updateIsPublishable()
      }
      if (this.stateToolsEdit) {
        this.onEdit()
      }
    },
    stateToolsEdit(edit) {
      this.edit = edit
    }
  },
  created() {
    this.activeTab = this.tab
  },
  mounted() {
    this.path.current = this.currentPath
    if (this.activeTab === Tab.PUBLISHING) {
      this.updateIsReferencedInPublish()
      this.updateIsPublishable()
    }
  },
  methods: {
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
    },
    onValidated(isValid, errors) {
      if (this.edit) {
        return;
      }
      this.valid.state = isValid;
      this.valid.errors = errors;
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
      if (!this.canPublishToWeb) {
        return
      }
      this.isPublishDialogOpen = true;
    },
    openPageCheckModal(){
      this.isPageCheckDialogOpen = true;
      this.startPageCheckSummaryLoad();
    },
    purgeAndReverify() {
      this.isPurgeRecheck = true;
      this.startPageCheckSummaryLoad();
    },
    startPageCheckSummaryLoad() {
      if (this.nodeType !== NodeType.PAGE && this.nodeType !== NodeType.TEMPLATE) {
        return;
      }
      this.isPageCheckSummaryLoading = true;
      this.linkVerificationResults = [];
      this.verifyLinks();
    },
    isExternalLink(href) {
      return /^https?:\/\//i.test(String(href || ''));
    },
    isInternalLink(href) {
      const value = String(href || '').trim();
      if (!value || value === '#' || value.toLowerCase().startsWith('javascript:')) {
        return false;
      }
      return value.startsWith('/') || (!value.startsWith('http://') && !value.startsWith('https://'));
    },
    isEmptyHref(href) {
      if (href === undefined || href === null) {
        return true;
      }
      const value = String(href).trim();
      return value === '' || value === '#' || value.toLowerCase() === 'javascript:void(0)';
    },
    findHtmlTags(value, tagName) {
      if (typeof value !== 'string' || value.toLowerCase().indexOf(`<${tagName}`) === -1) {
        return [];
      }
      const flags = 'gi';
      const pattern = tagName === 'img'
          ? new RegExp(`<${tagName}\\b[^>]*>`, flags)
          : new RegExp(`<${tagName}\\b[^>]*>[\\s\\S]*?<\\/${tagName}>`, flags);
      return value.match(pattern) || [];
    },
    getHtmlAttribute(tag, attribute) {
      const pattern = new RegExp(`${attribute}\\s*=\\s*("([^"]*)"|'([^']*)'|([^\\s>]+))`, 'i');
      const match = tag.match(pattern);
      if (!match) {
        return '';
      }
      return match[2] || match[3] || match[4] || '';
    },
    linkTextFromHtml(tag) {
      return String(tag || '').replace(/<[^>]*>/g, '').trim();
    },
    findLinkKey(owner) {
      for (const key of Object.keys(owner || {})) {
        const lower = key.toLowerCase();
        if ((lower.endsWith('link') || lower.endsWith('url') || lower === 'href')
            && typeof owner[key] === 'string') {
          return key;
        }
      }
      return 'link';
    },
    linkTextFromStructuredLink(owner, propertyKey) {
      const node = owner || {};
      for (const key of Object.keys(node)) {
        const lower = key.toLowerCase();
        if ((lower.endsWith('label') || lower.endsWith('text') || lower === 'title')
            && node[key] !== undefined && node[key] !== null) {
          return String(node[key]).replace(/<[^>]*>/g, '').trim();
        }
      }
      const fallback = node[propertyKey];
      return String(fallback || '').replace(/<[^>]*>/g, '').trim();
    },
    looksLikeRequiredLinkField(record) {
      const key = record.key.toLowerCase();
      const isLinkKey = (key.endsWith('link') || key.endsWith('url') || key === 'href') && key !== 'canonicalurl';
      if (!isLinkKey) return false;
      const value = record.value;
      if (typeof value !== 'string') return false;
      return value.startsWith('/') || value.startsWith('http://') || value.startsWith('https://');
    },
    collectRecords(value, path, records, ancestors) {
      if (value === null || value === undefined) return;
      if (ancestors.indexOf(value) > -1) return;
      if (Array.isArray(value)) {
        value.forEach((item, index) => {
          this.collectRecords(item, `${path}[${index}]`, records, ancestors);
        });
        return;
      }
      if (typeof value !== 'object') return;
      const nextAncestors = ancestors.slice();
      nextAncestors.push(value);
      Object.keys(value).forEach(key => {
        const item = value[key];
        if (typeof item === 'string' || typeof item === 'number' || typeof item === 'boolean') {
          records.push({ key, value: item, owner: value, path });
        } else {
          // Always use the accumulated path, not item.path.
          // item.path is a relative JCR path that breaks the accumulated
          // hierarchy and causes path inconsistency in records.
          this.collectRecords(item, `${path}/${key}`, records, nextAncestors);
        }
      });
    },
    async verifyLink(urlToCheck) {
      try {
        const purgeParam = this.isPurgeRecheck ? '&purge=true' : '';
        const apiUrl = `/extension/check-link?url=${encodeURIComponent(urlToCheck)}${purgeParam}`;
        const response = await fetch(apiUrl, { method: 'GET', credentials: 'same-origin' });
        if (response.status === 401) return { ok: false, status: 401, error: 'Unauthorized' };
        const data = await response.json();
        if (data.checkerError) return { ok: false, checkerError: true, error: data.error || 'External link checker unavailable' };
        if (data.error) return { ok: false, status: data.status || 0, error: data.error };
        if (data.redirect) {
          return { ok: data.finalOk !== false, status: data.status, redirect: true, redirectUrl: data.redirectUrl, finalUrl: data.finalUrl, finalStatus: data.finalStatus, finalOk: data.finalOk, loginRedirect: data.loginRedirect || false };
        }
        return { ok: data.ok, status: data.status };
      } catch (e) {
        return { ok: false, status: 0, error: 'Failed to verify link' };
      }
    },
    async verifyInternalLink(path) {
      const checkUrl = (p) => {
        const purgeParam = this.isPurgeRecheck ? '&purge=true' : '';
        const url = `/extension/check-link?url=${encodeURIComponent(window.location.origin + p)}${purgeParam}`;
        return fetch(url, { method: 'GET', credentials: 'same-origin' });
      };
      const parseResponse = async (resp) => {
        if (resp.status === 401) return { ok: false, status: 401, error: 'Unauthorized' };
        const data = await resp.json();
        if (data.error) return { ok: false, status: data.status || 0, error: data.error };
        if (data.redirect) {
          return { ok: data.finalOk !== false, status: data.status, redirect: true, redirectUrl: data.redirectUrl, finalUrl: data.finalUrl, finalStatus: data.finalStatus, finalOk: data.finalOk, loginRedirect: data.loginRedirect || false };
        }
        return { ok: data.ok, status: data.status };
      };
      try {
        const response = await checkUrl(path);
        const result = await parseResponse(response);
        // Sling serves pages with .html extension. If a bare path returns 403
        // (login required), 404 (not found), or redirects to login, try
        // appending .html — the page may still be accessible with the extension.
        if (!result.ok && (result.status === 403 || result.status === 404 || result.loginRedirect) && !path.endsWith('.html')) {
          const htmlResponse = await checkUrl(path + '.html');
          const htmlResult = await parseResponse(htmlResponse);
          if (htmlResult.ok) return htmlResult;
        }
        return result;
      } catch (e) {
        return { ok: false, status: 0, error: 'Failed to verify internal link' };
      }
    },
    resolveInternalLink(href) {
      const value = String(href || '').trim();
      if (value.startsWith('/')) return value;
      const pagePath = this.currentObject || '';
      const basePath = pagePath.substring(0, pagePath.lastIndexOf('/') + 1);
      return `${basePath}${value}`;
    },
    async verifyLinks() {
      if (this.nodeType !== NodeType.PAGE && this.nodeType !== NodeType.TEMPLATE) {
        this.isPurgeRecheck = false;
        this.isVerifyingLinks = false;
        this.linkVerificationResults = [];
        return;
      }
      this.isVerifyingLinks = true;

      // Always re-fetch the page data so newly added content is included.
      // pageView.page may contain stale data from a previous editor session.
      let pageData = null;
      try {
        await $perAdminApp.getApi().populatePageView(this.currentObject);
        pageData = get($perAdminApp.getView(), '/pageView/page', null);
      } catch (e) {
        this.isPurgeRecheck = false;
        this.isVerifyingLinks = false;
        return;
      }
      if (!pageData) {
        this.isPurgeRecheck = false;
        this.isVerifyingLinks = false;
        return;
      }

      const content = pageData['jcr:content'] || pageData;
      const records = [];
      this.collectRecords(content, this.currentObject, records, []);
      const allLinks = [];
      const occurrenceMap = {};
      const addLink = (href, location, linkText, meta) => {
        if (this.isEmptyHref(href)) return;
        const entry = { href, location, linkText, ...meta };
        if (!occurrenceMap[href]) occurrenceMap[href] = [];
        occurrenceMap[href].push(entry);
        allLinks.push(entry);
      };
      records.forEach(record => {
        if (typeof record.value === 'string') {
          this.findHtmlTags(record.value, 'a').forEach(tag => {
            const href = this.getHtmlAttribute(tag, 'href');
            addLink(href, `${record.path}/${record.key}`, this.linkTextFromHtml(tag), {
              owner: record.owner,
              saveOwner: record.saveOwner,
              htmlTag: tag,
              htmlValue: record.value,
              propertyKey: 'text',
              type: 'html-link'
            });
          });
        }
        if (record.key === 'htmlelement' && String(record.value).toLowerCase() === 'a') {
          const linkKey = this.findLinkKey(record.owner || {});
          const href = (record.owner || {})[linkKey];
          addLink(href, record.path, this.linkTextFromStructuredLink(record.owner, linkKey), {
            owner: record.owner,
            saveOwner: record.saveOwner,
            propertyKey: linkKey,
            type: 'link-field'
          });
        }
        if (this.looksLikeRequiredLinkField(record)) {
          addLink(record.value, `${record.path}/${record.key}`, '', {
            owner: record.owner,
            saveOwner: record.saveOwner,
            propertyKey: record.key,
            type: 'link-field'
          });
        }
      });
      if (allLinks.length === 0) {
        this.isPurgeRecheck = false;
        this.isVerifyingLinks = false;
        this.linkVerificationResults = [];
        return;
      }
      const uniqueHrefs = [...new Set(allLinks.map(l => l.href))];
      const batchSize = 5;
      const newResults = [];
      for (let i = 0; i < uniqueHrefs.length; i += batchSize) {
        const batch = uniqueHrefs.slice(i, i + batchSize);
        const results = await Promise.all(batch.map(async (href) => {
          let result;
          if (this.isExternalLink(href)) {
            result = await this.verifyLink(href);
          } else if (this.isInternalLink(href)) {
            result = await this.verifyInternalLink(this.resolveInternalLink(href));
          } else {
            result = { ok: true, status: 0, error: null };
          }
          return { href, result };
        }));
        results.forEach(({ href, result }) => {
          const occurrences = occurrenceMap[href] || [];
          // Multiple records from the same component instance should count as a
          // single occurrence. In Peregrine, a component instance is the node
          // at the deepest array-indexed segment that is NOT a structural wrapper
          // (children[N] or experiences[N]). Everything after that segment is
          // either a property or a nested child of the same component.
          const componentLocation = (loc) => {
            if (!loc) return loc;
            // Strip translation-variant segments first
            let path = loc.replace(/\/experiences(?:\/lang_[^\/]+|\[\d+\])/g, '');
            // Find all array-indexed segments like /children[2], /columns[1]
            const segments = path.match(/\/\w+\[\d+\]/g) || [];
            // Find the last segment that is NOT a structural wrapper
            let cutoff = -1;
            for (let i = segments.length - 1; i >= 0; i--) {
              const seg = segments[i];
              if (!seg.startsWith('/children[')) {
                cutoff = path.indexOf(seg) + seg.length;
                break;
              }
            }
            if (cutoff >= 0) {
              return path.substring(0, cutoff);
            }
            // Fallback: deepest array-indexed segment
            if (segments.length > 0) {
              const lastSeg = segments[segments.length - 1];
              const idx = path.indexOf(lastSeg);
              return path.substring(0, idx + lastSeg.length);
            }
            return path;
          };
          const baseLocs = new Set(occurrences.map((link, index) => {
            // HTML links in the same text field are distinct occurrences
            if (link.htmlTag) {
              return link.location + '#' + index;
            }
            return componentLocation(link.location);
          }));
          const totalCount = baseLocs.size;
          occurrences.forEach(link => {
            newResults.push({
              href: this.isInternalLink(link.href) ? this.resolveInternalLink(link.href) : link.href,
              linkText: link.linkText,
              location: link.location,
              ok: result.ok,
              status: result.status,
              error: result.error,
              checkerError: result.checkerError || false,
              redirect: result.redirect || false,
              redirectUrl: result.redirectUrl || null,
              finalUrl: result.finalUrl || null,
              finalStatus: result.finalStatus || null,
              finalOk: result.finalOk !== undefined ? result.finalOk : null,
              loginRedirect: result.loginRedirect || false,
              owner: link.owner || null,
              saveOwner: link.saveOwner || null,
              propertyKey: link.propertyKey || null,
              htmlTag: link.htmlTag || null,
              htmlValue: link.htmlValue || null,
              linkType: link.type || null,
              _totalOccurrences: totalCount
            });
          });
        });
      }
      this.linkVerificationResults = newResults;
      this.pageCheckHasRun = true;
      this.isPurgeRecheck = false;
      this.isVerifyingLinks = false;
      this.$nextTick(() => {
        this.isPageCheckSummaryLoading = false;
      });
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
      this.isPublishDialogOpen = false;
    },
    closePageCheck(){
      this.isPageCheckDialogOpen = false;
    },
    onPageCheckSummary(summary) {
      this.pageCheckSummary = summary;
    },
    editPagePropertiesFromCheck(){
      this.isPageCheckDialogOpen = false;
      this.setActiveTab(Tab.INFO);
      this.$nextTick(() => {
        this.onEdit();
      });
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
              this.isOpen = true;
            }).catch(() => {
          $perAdminApp.getApi().populateNodesForBrowser(`/content/${site.tenant}`, 'pathBrowser');
        });
      });
    },

    copyNode() {
      $perAdminApp.getApi().populateNodesForBrowser(this.path.current, 'pathBrowser')
          .then(() => {
            this.isCopyOpen = true;
          }).catch(() => {
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
    save() {
      let promise
      if (this.nodeType === NodeType.OBJECT) {
        promise = this.saveObject();
      } else {
        promise = $perAdminApp.stateAction(`save${this.uNodeType}Properties`, this.node);
        this.edit = false;
      }

      promise.then(() => $perAdminApp.getApi().populateNodesForBrowser(this.node.path.split('/').slice(0, -1).join('/')))
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
    updateIsPublishable() {
      if (this.nodeType === NodeType.FILE) {
        this.isPublishable = true;
        this.isPublishabilityChecked = true;
        this.isPublishabilityLoading = false;
        this.publishabilityReason = '';
        return;
      }

      const path = this.currentObject;
      if (!path) {
        this.isPublishable = false;
        this.isPublishabilityChecked = false;
        this.isPublishabilityLoading = false;
        this.publishabilityReason = '';
        return;
      }

      this.isPublishable = false;
      this.isPublishabilityChecked = false;
      this.isPublishabilityLoading = true;
      this.publishabilityReason = '';

      $perAdminApp.getApi().isPublishable(path)
        .then(data => {
          this.isPublishable = data.result === true;
          this.publishabilityReason = data.reason || '';
        }).catch(error => {
          this.isPublishable = false;
          this.publishabilityReason = (error && error.response && error.response.data
              && (error.response.data.reason || error.response.data.message))
              || (error && error.message)
              || 'Publishing not possible as there are some errors';
        }).then(() => {
          this.isPublishabilityLoading = false;
          this.isPublishabilityChecked = true;
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
.deleteVersionWrapper {
  margin-left: auto;
}
.publishability-note {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  font-size: 12px;
  line-height: 1.4;
  color: rgba(0, 0, 0, 0.65);
}
.publishability-note .material-icons {
  font-size: 18px;
  width: 18px;
  height: 18px;
  line-height: 18px;
  color: rgba(0, 0, 0, 0.45);
}
.publishability-loading {
  cursor: default;
  color: rgba(0, 0, 0, 0.58);
}
.publishability-loading .preloader-wrapper {
  width: 22px;
  height: 22px;
  margin-right: 10px;
}
.publishability-loading .spinner-layer {
  border-color: rgba(69, 90, 100, 0.72);
}
.page-check-spinner {
  width: 16px !important;
  height: 16px !important;
  display: inline-block;
  vertical-align: middle;
  margin-left: auto;
}
.action-list .action {
  display: flex;
  align-items: center;
  gap: 8px;
}
.page-check-spinner .spinner-layer {
  border-width: 2px !important;
}
.page-check-spinner .circle-clip {
  width: 8px !important;
  height: 8px !important;
}
.page-check-action-icons {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.page-check-action-ok {
  color: #2e7d32;
}
.page-check-action-pending {
  font-size: 18px;
  color: rgba(0, 0, 0, 0.38);
}
.page-check-action-error {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 0;
}
.page-check-action-error .material-icons {
  font-size: 18px;
  line-height: 1;
  color: #c62828;
}
.page-check-action-count {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 50%;
  background: #c62828;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
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
.operationDisabledOnActivatedItem > span {
  display: flex;
  align-items: center;
}
</style>

<style scoped>
.info-view-image {
    cursor: pointer;
}

.info-view-video {
    width: 100%;
    height: 100%;
}
</style>
