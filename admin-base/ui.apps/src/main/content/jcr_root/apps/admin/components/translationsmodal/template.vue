<template>
  <admin-components-materializemodal
      ref="materializemodal"
      class="translations-modal"
      v-on:complete="$emit('complete', $event)"
      v-bind:modalTitle="computedTitle">

    <div class="translation-content">

      <div v-if="loading && nodes.length === 0" class="loading-message">
        Loading translations...
      </div>

      <div v-if="error" class="error">
        {{ error }}
      </div>

      <div v-if="!loading && nodes.length === 0 && !error" class="empty-message">
        No translatable nodes found for this path.
      </div>

      <div :class="{ 'is-reloading': loading && nodes.length > 0 }">

        <div class="bulk-actions" v-if="nodes.length > 0">
          <div class="bulk-buttons">
            <div
                v-for="lang in languages"
                :key="lang"
                class="bulk-dropdown-wrapper">

              <button
                  class="btn translate dropdown-trigger"
                  @click.stop="toggleDropdown(lang)"
                  :disabled="isBulkProcessing">
                Bulk Translate in {{ lang.toUpperCase() }}
                <i class="material-icons right">more_vert</i>
              </button>

              <ul v-if="openDropdown === lang" class="bulk-dropdown-menu">
                <li @click="selectBulkAction(lang, 'keep')">
                  <span class="action-title">Keep Existing</span>
                  <span class="action-desc">Translate only missing texts</span>
                </li>
                <li @click="selectBulkAction(lang, 'outdated')">
                  <span class="action-title">Override Outdated</span>
                  <span class="action-desc">Update missing and outdated texts</span>
                </li>
                <li class="danger" @click="selectBulkAction(lang, 'override')">
                  <span class="action-title">Override All</span>
                  <span class="action-desc">Re-translate everything</span>
                </li>
              </ul>
            </div>
          </div>

          <div v-if="translationQueue.total > 0" class="bulk-progress">
            {{ progressMessage }}
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: progressPercentage + '%' }"></div>
            </div>
          </div>
        </div>

        <div v-if="openDropdown" class="dropdown-backdrop" @click="openDropdown = null"></div>

        <table v-if="nodes.length > 0">
          <thead>
          <tr>
            <th>
              <div class="action-buttons">
                Original
                <a :href="getPreviewUrl('en')" target="_blank" class="btn btn-flat btn-icon" title="View Original Page">
                  <i class="material-icons">visibility</i>
                </a>
              </div>
            </th>
            <th v-for="lang in languages" :key="lang">
              <div class="action-buttons">
                {{ lang.toUpperCase() }}
                <a :href="getPreviewUrl(lang)" target="_blank" class="btn btn-flat btn-icon" :title="'View ' + lang.toUpperCase() + ' Page'">
                  <i class="material-icons">visibility</i>
                </a>
              </div>
            </th>
          </tr>
          </thead>
          <tbody>
          <template v-for="(node) in nodes">
            <tr v-for="(text, property) in getTranslatableProperties(node)" :key="node.path + ':' + property">

              <td class="col-original">
                <textarea class="value" rows="3" readonly :value="text"></textarea>
                <div v-if="pageLastModified" class="date">
                  <em>{{ pageLastModified }}</em>
                </div>
                <div class="meta-info">
                  <a :href="origin + node.path + '.json'" target="_blank">
                    <strong>{{ node.reference }}:{{ property }}</strong>
                  </a>
                </div>
              </td>

              <td v-for="lang in languages" :key="lang" class="col-lang">
                <div v-if="node.translations && node.translations[lang] && node.translations[lang][property] !== undefined" class="translation-edit">
                  <textarea
                      class="value"
                      rows="3"
                      v-model="node.translations[lang][property]">
                  </textarea>
                  <div class="date" :class="{ 'outdated': isOutdated(node.translations[lang], property) }">
                    <em>{{ formatDate(node.translations[lang], property) }}</em>
                  </div>

                  <div class="action-buttons">
                    <button
                        class="btn save"
                        @click="saveTranslation(node, lang, property)"
                        :disabled="isDisabled(node.path, lang, property)">
                      <span v-if="isSuccess(node.path, lang, property)">Saved</span>
                      <span v-else>{{ isProcessing(node.path, lang, property) ? 'Saving...' : 'Save' }}</span>
                    </button>

                    <button
                        class="btn btn-icon btn-flat delete"
                        @click="deleteTranslation(node, lang, property)"
                        :disabled="isDisabled(node.path, lang, property)"
                        title="Delete Translation">
                      <i class="icon material-icons">delete</i>
                    </button>
                  </div>
                </div>

                <div v-else class="translation-create">
                  <button
                      class="btn translate"
                      @click="translateNode(node, lang, property, text)"
                      :disabled="isDisabled(node.path, lang, property)">
                    {{ isProcessing(node.path, lang, property) ? 'Translating...' : `Translate in ${lang.toUpperCase()}` }}
                  </button>
                </div>
              </td>
            </tr>
          </template>
          </tbody>
        </table>
      </div>

    </div>

  </admin-components-materializemodal>
</template>

<script>
import {Toast} from "../../../../../../js/constants";

export default {
  props: [
    'path',
    'modalTitle',
  ],
  data() {
    return {
      loading: false,
      error: null,
      nodes: [],
      languages: ['de', 'fr', 'it'],
      translationModel: null,
      processingMap: {},
      saveSuccess: {},
      pageLastModified: null,
      rawPageLastModified: null,
      savedScrollTop: 0,
      origin: window.location.origin,
      // Bulk State
      isBulkProcessing: false,
      openDropdown: null,
      translationQueue: {
        total: 0,
        completed: 0,
        currentLang: ''
      }
    }
  },
  computed: {
    computedTitle() {
      return this.modalTitle || `Translations: ${this.path}`;
    },
    progressMessage() {
      if (this.translationQueue.total === 0) return '';
      return `Translating ${this.translationQueue.currentLang.toUpperCase()}: ${this.translationQueue.completed} / ${this.translationQueue.total}`;
    },
    progressPercentage() {
      if (this.translationQueue.total === 0) return 0;
      return (this.translationQueue.completed / this.translationQueue.total) * 100;
    }
  },
  methods: {
    open() {
      this.$refs.materializemodal.open();
      if (this.path) {
        this.listTranslations();
      } else {
        this.error = "No path provided.";
      }
    },
    close() {
      this.$refs.materializemodal.close();
      this.nodes = [];
      this.error = null;
      this.pageLastModified = null;
      this.rawPageLastModified = null;
      this.savedScrollTop = 0;
      this.processingMap = {};
      this.saveSuccess = {};
      this.translationModel = null;
      this.resetBulkState();
    },

    resetBulkState() {
      this.isBulkProcessing = false;
      this.openDropdown = null;
      this.translationQueue = { total: 0, completed: 0, currentLang: '' };
    },

    // --- Helper Methods ---

    getPreviewUrl(lang) {
      let url = this.path;
      if (url.startsWith('/content/') && url.includes('/objects/news/')) {
        url = url.replace('objects/news', 'pages/news-details') + '.html';
      } else {
        url = url + '.html';
      }

      if (lang) {
        url += `?lang=${lang}`;
      }

      return this.origin + url;
    },

    json2blob(data) {
      const content = JSON.stringify(data);
      return new Blob([content], {type: 'application/json; charset=utf-8'});
    },

    normalizeProperty(property) {
      return property.replaceAll(':', '_');
    },

    formatDate(node, property) {
      const dateString = node[`per:TranslatedAt_${this.normalizeProperty(property)}`] ?? node['per:TranslatedAt'];
      if (dateString === undefined || dateString === null || dateString === '') {
        return '';
      }
      return new Date(dateString).toLocaleString();
    },

    isOutdated(translationNode, property) {
      if (!this.rawPageLastModified) return false;
      const dateString = translationNode[`per:TranslatedAt_${this.normalizeProperty(property)}`] ?? translationNode['per:TranslatedAt'];
      if (!dateString) return false;
      const translationDate = new Date(dateString);
      return translationDate < this.rawPageLastModified;
    },

    isProcessing(path, lang, prop) {
      return !!this.processingMap[`${path}|${lang}|${prop}`];
    },

    isSuccess(path, lang, prop) {
      return !!this.saveSuccess[`${path}|${lang}|${prop}`];
    },

    isDisabled(path, lang, prop) {
      return this.isProcessing(path, lang, prop) || this.isSuccess(path, lang, prop) || this.isBulkProcessing;
    },

    setProcessing(path, lang, prop, status) {
      const key = `${path}|${lang}|${prop}`;
      if (status) {
        this.$set(this.processingMap, key, true);
      } else {
        this.$delete(this.processingMap, key);
      }
    },

    hasTranslatableText(htmlString) {
      if (!htmlString || !htmlString.trim()) return false;
      const parser = new DOMParser();
      const doc = parser.parseFromString(htmlString, 'text/html');
      const walk = (node) => {
        if (node.nodeType === Node.TEXT_NODE) {
          return node.textContent.trim().length > 0;
        }
        if (node.nodeType === Node.ELEMENT_NODE) {
          const tagName = node.tagName.toLowerCase();
          const ignoredTags = ['script', 'style', 'noscript', 'iframe', 'object'];
          if (ignoredTags.includes(tagName)) return false;
          for (const child of node.childNodes) {
            if (walk(child)) return true;
          }
        }
        return false;
      }
      return walk(doc.body);
    },

    getTranslatableProperties(node) {
      const result = {};
      Object.keys(node.original).forEach(key => {
        if (this.hasTranslatableText(node.original[key])) {
          result[key] = node.original[key];
        }
      });
      return result;
    },

    // --- Scroll Management ---

    getScrollContainer() {
      if (this.$el) {
        return this.$el.closest('.translations-modal');
      }
      return null;
    },

    saveScroll() {
      const container = this.getScrollContainer();
      if (container) {
        this.savedScrollTop = container.scrollTop;
      }
    },

    restoreScroll() {
      const container = this.getScrollContainer();
      if (container) {
        container.scrollTop = this.savedScrollTop;
      }
    },

    // --- Configuration Fetching ---

    async fetchTranslationModel() {
      if (this.translationModel) return;
      try {
        const tenantName = $perAdminApp.getView().state.tenant.name;
        if (!tenantName) throw new Error("Could not determine tenant name.");
        const tenantRes = await fetch(`/content/${tenantName}.json`);
        if (!tenantRes.ok) throw new Error(`Failed to fetch tenant configuration for ${tenantName}`);
        const tenantConfig = await tenantRes.json();
        const sourceSite = tenantConfig.sourceSite ?? tenantName;
        const modelRes = await fetch(`/apps/${sourceSite}/i18n/model.json`);
        if (!modelRes.ok) throw new Error(`Failed to fetch translation model from /apps/${sourceSite}/i18n/model.json`);
        this.translationModel = await modelRes.json();
      } catch (err) {
        console.error(err);
        throw new Error(`Configuration Error: ${err.message}`);
      }
    },

    // --- API Interactions ---

    async listTranslations() {
      this.saveScroll();
      this.loading = true;
      this.error = null;
      // Note: We DO NOT clear this.nodes = [] here to allow the table to persist during reload
      this.pageLastModified = null;
      this.rawPageLastModified = null;

      try {
        await this.fetchTranslationModel();
        try {
          const pageRes = await fetch(`${this.path}.json`);
          if(pageRes.ok) {
            const pageJson = await pageRes.json();
            if (pageJson['jcr:lastModified']) {
              this.rawPageLastModified = new Date(pageJson['jcr:lastModified']);
              this.pageLastModified = this.rawPageLastModified.toLocaleString();
            }
          }
        } catch(err) {
          console.warn("Could not fetch page metadata", err);
        }

        const formData = new FormData();
        formData.append('model', this.json2blob(this.translationModel));
        const response = await fetch(`/perapi/admin/listTranslations.json${this.path}`, {
          body: formData,
          method: 'POST'
        });
        if (!response.ok) throw new Error(`Error ${response.status}: ${response.statusText}`);
        const data = await response.json();
        this.nodes = data.nodes || [];
      } catch (e) {
        this.error = e.toString();
        // If error occurs, we might want to clear nodes so the error is visible and not confusing
        if (this.nodes.length === 0) this.nodes = [];
      } finally {
        this.loading = false;
        this.$nextTick(() => {
          this.restoreScroll();
        });
      }
    },

    async saveTranslation(node, lang, property) {
      this.setProcessing(node.path, lang, property, true);
      const value = node.translations[lang][property];
      const key = `${node.path}|${lang}|${property}`;

      try {
        const formData = new FormData();
        formData.append('_charset_', 'UTF-8');
        formData.append('lang', lang);
        formData.append('properties[]', property);
        formData.append('translations[]', value);
        formData.append('override', 'true');

        const response = await fetch(`/perapi/admin/translateNode.json${node.path}`, {
          body: formData,
          method: 'POST'
        });

        if (response.ok) {
          this.reloadExplorer();
          if (node.translations[lang]) {
            const date = new Date().toISOString();
            node.translations[lang][`per:TranslatedAt_${property.replaceAll(':', '_')}`] = date;
            node.translations[lang]['per:TranslatedAt'] = date;
          }
          this.$set(this.saveSuccess, key, true);
          setTimeout(() => {
            this.$delete(this.saveSuccess, key);
          }, 1500);
        } else {
          throw new Error(await response.text());
        }
      } catch (e) {
        $perAdminApp.toast(`Saving failed: ${e}`, Toast.Level.WARNING)
      } finally {
        this.setProcessing(node.path, lang, property, false);
      }
    },

    async deleteTranslation(node, lang, property) {
      try {
        $perAdminApp.askUser('Warning',
            (`This will delete the ${lang} translation. Would you like to continue ?`), {
              yesText: 'Yes',
              yes: async () => {
                const formData = new FormData();
                formData.append('_charset_', 'UTF-8');
                formData.append('lang', lang);
                formData.append('properties[]', property);
                formData.append('delete', 'true');

                const response = await fetch(`/perapi/admin/translateNode.json${node.path}`, {
                  body: formData,
                  method: 'POST'
                });

                if (!response.ok) {
                  throw new Error(await response.text());
                }

                this.reloadExplorer();
                await this.listTranslations();
              },
            });
      } catch (e) {
        $perAdminApp.toast(`Delete failed: ${e}`, Toast.Level.WARNING)
      }
    },

    reloadExplorer() {
      const explorerPath = this.path.split('/').slice(0, -1).join('/');
      $perAdminApp.getApi().populateNodesForBrowser(explorerPath);
    },

    async translateNode(node, lang, property, originalText) {
      this.setProcessing(node.path, lang, property, true);
      try {
        let formData = new FormData();
        formData.append('_charset_', 'UTF-8');
        formData.append('lang', lang);
        formData.append('properties[]', property);

        const response = await fetch(`/perapi/admin/translateNode.json${node.path}`, {
          body: formData,
          method: 'POST'
        });
        if (!response.ok) {
          throw new Error(await response.text());
        }
        const { translations } = await response.json();

        const siblings = [];
        this.nodes.forEach(otherNode => {
          const translatableProps = this.getTranslatableProperties(otherNode);
          Object.keys(translatableProps).forEach(otherProp => {
            const otherText = translatableProps[otherProp];
            if (otherText !== originalText) return;
            if (otherNode.path === node.path && otherProp === property) return;
            if (otherNode.translations && otherNode.translations[lang] && otherNode.translations[lang][otherProp]) return;
            siblings.push({
              path: otherNode.path,
              property: otherProp
            });
          });
        });

        if (siblings.length && translations) {
          const promises = siblings.map(sibling => {
            const siblingFormData = new FormData();
            siblingFormData.append('_charset_', 'UTF-8');
            siblingFormData.append('lang', lang);
            siblingFormData.append('properties[]', sibling.property);
            translations.forEach(t => siblingFormData.append('translations[]', t));
            return fetch(`/perapi/admin/translateNode.json${sibling.path}`, {
              body: siblingFormData,
              method: 'POST'
            });
          });
          await Promise.all(promises);
        }
        this.reloadExplorer();
        await this.listTranslations();
      } catch (e) {
        $perAdminApp.toast(`Translation failed: ${e}`, Toast.Level.WARNING)
      } finally {
        this.setProcessing(node.path, lang, property, false);
      }
    },

    // --- Bulk Translation Logic ---

    toggleDropdown(lang) {
      if (this.openDropdown === lang) {
        this.openDropdown = null;
      } else {
        this.openDropdown = lang;
      }
    },

    selectBulkAction(lang, mode) {
      this.openDropdown = null;
      this.executeBulk(lang, mode);
    },

    async executeBulk(lang, mode) {
      this.isBulkProcessing = true;
      this.translationQueue.currentLang = lang;
      this.translationQueue.total = 0;
      this.translationQueue.completed = 0;

      // 1. Identify items to translate based on mode
      const nodeBuckets = {};

      this.nodes.forEach(node => {
        const translatableProps = this.getTranslatableProperties(node);
        Object.keys(translatableProps).forEach(property => {

          const hasTranslation = node.translations &&
              node.translations[lang] &&
              node.translations[lang][property] !== undefined;

          let shouldTranslate = false;

          if (mode === 'override') {
            shouldTranslate = true;
          } else if (mode === 'outdated') {
            if (!hasTranslation) {
              shouldTranslate = true;
            } else {
              // CLIENT-SIDE CHECK: Only translate if it is actually outdated
              if (this.isOutdated(node.translations[lang], property)) {
                shouldTranslate = true;
              }
            }
          } else { // mode === 'keep'
            if (!hasTranslation) {
              shouldTranslate = true;
            }
          }

          if (shouldTranslate) {
            if (!nodeBuckets[node.path]) {
              nodeBuckets[node.path] = [];
            }
            nodeBuckets[node.path].push(property);
          }
        });
      });

      this.translationQueue.total = Object.keys(nodeBuckets).length;

      if (this.translationQueue.total === 0) {
        $perAdminApp.toast(`No items found for ${lang.toUpperCase()} translation with mode: ${mode}`, Toast.Level.INFO);
        this.resetBulkState();
        return;
      }

      // 2. Create parallel requests
      const requests = Object.entries(nodeBuckets).map(async ([path, properties]) => {
        const formData = new FormData();
        formData.append('_charset_', 'UTF-8');
        formData.append('lang', lang);

        // Append override if necessary
        if (mode === 'override' || mode === 'outdated') {
          formData.append('override', 'true');
        }

        properties.forEach(prop => {
          formData.append('properties[]', prop);
        });

        try {
          const response = await fetch(`/perapi/admin/translateNode.json${path}`, {
            body: formData,
            method: 'POST'
          });

          if (!response.ok) throw new Error(await response.text());

          this.translationQueue.completed++;
        } catch (e) {
          console.error(`Failed to bulk translate ${path}:`, e);
          this.translationQueue.completed++;
        }
      });

      // 3. Execute in parallel
      try {
        await Promise.all(requests);
        $perAdminApp.toast(`Bulk translation for ${lang.toUpperCase()} complete.`, Toast.Level.SUCCESS);
      } catch (e) {
        $perAdminApp.toast(`Some translations failed during bulk operation.`, Toast.Level.WARNING);
      } finally {
        this.reloadExplorer();
        await this.listTranslations();
        setTimeout(() => {
          this.resetBulkState();
        }, 1000);
      }
    }
  }
}
</script>

<style scoped>
/* Modal Resizing */
.translations-modal {
  --border-color: #cfd8dc;
  --text-color: #555;
  --text-color-light: #757575;
  --bg-color: #f5f5f5;
  --bg-color-hover: #ffebee;
  --text-color-error: #d32f2f;

  width: 80% !important;
  max-height: 80% !important;
}

/* Reloading State: Semi-transparent and disabled interactions */
.is-reloading {
  opacity: 0.5;
  pointer-events: none;
  user-select: none;
}

table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 10px;
}

table th {
  text-transform: uppercase;
  text-align: left;
  padding: 10px;
  border-bottom: 1px solid var(--border-color);
  color: var(--pcms-blue-grey);
}

table td {
  vertical-align: top;
  padding: 10px;
  line-height: 1.5rem;
  border-bottom: 1px solid var(--border-color);
  text-align: left;
  color: var(--pcms-blue-grey);
  font-size: 14px;
}

table tr:last-child td {
  border-bottom: none;
}

table .btn {
  text-transform: none;
  white-space: nowrap;
}

textarea.value {
  width: 100%;
  border: 1px solid var(--border-color);
  font-size: 1rem;
  padding: 0.5rem;
  transition: all 0.3s;
  line-height: 1.4;
  color: var(--text-color);
  box-sizing: border-box;
  resize: vertical;
}

textarea.value:focus {
  border-bottom: 1px solid var(--pcms-blue-grey);
  box-shadow: 0 1px 0 0 var(--pcms-blue-grey);
}

.meta-info, .date {
  margin-top: 5px;
  font-size: 0.85em;
}

.outdated {
  color: var(--pcms-orange);
  font-weight: bold;
}

a {
  color: var(--pcms-blue-grey);
  text-decoration: underline;
}

.error {
  color: var(--error-bg);
  padding: 16px;
}

.loading-message {
  font-weight: bold;
  font-size: 18px;
  padding: 16px;
}

.btn-icon {
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
}

.translation-edit, .translation-create {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-buttons {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

/* Bulk Actions Styling */
.bulk-actions {
  margin-bottom: 20px;
  padding: 10px;
  background-color: var(--bg-color);
  border-radius: 4px;
}

.bulk-buttons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

/* Dropdown styling */
.bulk-dropdown-wrapper {
  position: relative;
  display: inline-block;
}

.dropdown-trigger {
  display: flex;
  align-items: center;
  gap: 5px;
  padding-right: 12px;
}

.dropdown-trigger i {
  font-size: 18px;
}

.bulk-dropdown-menu {
  position: absolute;
  top: 100%;
  left: 0;
  z-index: 1000;
  min-width: 220px;
  background-color: white;
  box-shadow: 0 2px 10px rgba(0,0,0,0.15);
  border-radius: 4px;
  margin: 5px 0 0 0;
  padding: 5px 0;
  list-style: none;
}

.bulk-dropdown-menu li {
  padding: 10px 15px;
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 1px solid var(--border-color);
}

.bulk-dropdown-menu li:last-child {
  border-bottom: none;
}

.bulk-dropdown-menu li:hover {
  background-color: var(--bg-color);
}

.bulk-dropdown-menu li.danger {
  color: var(--text-color-error);
}

.bulk-dropdown-menu li.danger:hover {
  background-color: var(--bg-color-hover);
}

.action-title {
  display: block;
  font-weight: 600;
  font-size: 14px;
}

.action-desc {
  display: block;
  font-size: 12px;
  color: var(--text-color-light)
}

/* Backdrop for dropdown */
.dropdown-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 999;
  background: transparent;
}

.bulk-progress {
  margin-top: 10px;
  font-size: 0.9em;
  color: var(--pcms-blue-grey);
  font-weight: bold;
}

.progress-bar {
  width: 100%;
  height: 8px;
  background-color: var(--border-color);
  border-radius: 4px;
  margin-top: 5px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background-color: var(--pcms-blue-grey-darken-3);
  transition: width 0.3s ease;
}
</style>
