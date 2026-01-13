<template>
  <admin-components-materializemodal
    ref="materializemodal"
    class="translations-modal"
    v-on:complete="$emit('complete', $event)"
    v-bind:modalTitle="computedTitle">

    <div class="translation-content">
      <div v-if="loading" class="loading-message">
        Loading translations...
      </div>

      <div v-if="error" class="error">
        {{ error }}
      </div>

      <div v-if="!loading && nodes.length === 0" class="empty-message">
        No translatable nodes found for this path.
      </div>

      <table v-if="nodes.length > 0">
        <thead>
        <tr>
          <th>Original</th>
          <th v-for="lang in languages" :key="lang">{{ lang.toUpperCase() }}</th>
        </tr>
        </thead>
        <tbody>
        <template v-for="(node, nIndex) in nodes">
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
                <button
                  class="button cta save"
                  @click="saveTranslation(node, lang, property)"
                  :disabled="isDisabled(node.path, lang, property)">
                  <span v-if="isSuccess(node.path, lang, property)">Saved</span>
                  <span v-else>{{ isProcessing(node.path, lang, property) ? 'Saving...' : 'Save' }}</span>
                </button>
              </div>

              <div v-else class="translation-create">
                <button
                  class="button cta translate"
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

  </admin-components-materializemodal>
</template>

<script>
const translationModel = {
  "breadcrumb": ["text"],
  "cards": ["text", "title", "subtitle", "tab", "category", "address", "buttontext"],
  "carousel": ["buttonlabel", "text"],
  "contactform": ["formtitle", "formsubtitle", "buttontext", "addresstitle", "contacttitle", "banktitle", "address"],
  "footer": ["description", "title", "text", "copyright"],
  "header": ["text", "buttontext", "title"],
  "listlinks": ["text", "title"],
  "maps": ["title", "address", "linklabel"],
  "mediablock": ["mediatitle"],
  "menubuttons": ["buttontext"],
  "pager": ["nextlabel", "prevlabel"],
  "richtext": ["text", "mediatitle"],
  "simpletext": ["text"],
  "teaserhorizontal": ["title", "subtitle", "text", "buttontext", "mediatitle"],
  "teaservertical": ["title", "subtitle", "text", "buttontext", "mediatitle"],
  "textlinks": ["text"],
  "viewlink": ["text"],
  "page": ["jcr:title", "description"],
  "news": ["text", "title"],
  "events": ["title", "description", "text", "location", "type", "organisation"]
};

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
      processingMap: {},
      saveSuccess: {},
      pageLastModified: null,
      rawPageLastModified: null, // Added to store Date object for comparison
      savedScrollTop: 0,
      origin: window.location.origin
    }
  },
  computed: {
    computedTitle() {
      return this.modalTitle || `Translations: ${this.path}`;
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
    },

    // --- Helper Methods ---

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

    // New method to check if translation is older than the original content
    isOutdated(translationNode, property) {
      if (!this.rawPageLastModified) return false;

      // Logic matches formatDate retrieval
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
      return this.isProcessing(path, lang, prop) || this.isSuccess(path, lang, prop);
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
        // Return the modal wrapper that contains the scroll
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

    // --- API Interactions ---

    async listTranslations() {
      this.saveScroll();

      this.loading = true;
      this.error = null;
      this.nodes = [];
      this.pageLastModified = null;
      this.rawPageLastModified = null;

      try {
        try {
          const pageRes = await fetch(`${this.path}.json`);
          if(pageRes.ok) {
            const pageJson = await pageRes.json();
            if (pageJson['jcr:lastModified']) {
              // Store Date object for comparison
              this.rawPageLastModified = new Date(pageJson['jcr:lastModified']);
              // Store string for display
              this.pageLastModified = this.rawPageLastModified.toLocaleString();
            }
          }
        } catch(err) {
          console.warn("Could not fetch page metadata", err);
        }

        const formData = new FormData();
        formData.append('model', this.json2blob(translationModel));

        const response = await fetch(`/perapi/admin/listTranslations.json${this.path}`, {
          body: formData,
          method: 'POST'
        });

        if (!response.ok) throw new Error(`Error ${response.status}: ${response.statusText}`);

        const data = await response.json();
        this.nodes = data.nodes || [];
      } catch (e) {
        this.error = e.toString();
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
        formData.append('lang', lang);
        formData.append('properties[]', property);
        formData.append('translations[]', value);
        formData.append('override', 'true');

        const response = await fetch(`/perapi/admin/translateNode.json${node.path}`, {
          body: formData,
          method: 'POST'
        });

        if (response.ok) {
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
        alert(`Saving failed: ${e}`);
      } finally {
        this.setProcessing(node.path, lang, property, false);
      }
    },

    async translateNode(node, lang, property, originalText) {
      this.setProcessing(node.path, lang, property, true);

      try {
        let formData = new FormData();
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

        // --- Sibling Logic ---
        // Find ALL nodes that have matching text, regardless of property name
        const siblings = [];

        this.nodes.forEach(otherNode => {
          const translatableProps = this.getTranslatableProperties(otherNode);
          Object.keys(translatableProps).forEach(otherProp => {
            const otherText = translatableProps[otherProp];

            // 1. Text must match exactly
            if (otherText !== originalText) return;

            // 2. Skip the specific item we just translated
            if (otherNode.path === node.path && otherProp === property) return;

            // 3. Skip if already translated
            if (otherNode.translations &&
              otherNode.translations[lang] &&
              otherNode.translations[lang][otherProp]) {
              return;
            }

            siblings.push({
              path: otherNode.path,
              property: otherProp
            });
          });
        });

        // Apply translations to siblings
        if (siblings.length && translations) {
          for (const sibling of siblings) {
            const siblingFormData = new FormData();
            siblingFormData.append('lang', lang);
            // Use the SIBLING'S property, not the original property
            siblingFormData.append('properties[]', sibling.property);

            translations.forEach(t => siblingFormData.append('translations[]', t));

            await fetch(`/perapi/admin/translateNode.json${sibling.path}`, {
              body: siblingFormData,
              method: 'POST'
            });
          }
        }

        await this.listTranslations();

      } catch (e) {
        alert(`Translation failed: ${e}`);
      } finally {
        this.setProcessing(node.path, lang, property, false);
      }
    }
  }
}
</script>

<style scoped>
table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 10px;
}

table th {
  text-transform: uppercase;
  text-align: left;
  padding: 10px;
  border-bottom: 1px solid #cfd8dc;
  color: #455a64;
}

table td {
  vertical-align: top;
  padding: 10px;
  line-height: 1.5rem;
  border-bottom: 1px solid #cfd8dc;
  text-align: left;
  color: #455a64;
  font-size: 14px;
}

table tr:last-child td {
  border-bottom: none;
}

textarea.value {
  width: 100%;
  border: 1px solid #cfd8dc;
  font-size: 1rem;
  padding: 0.5rem;
  transition: all 0.3s;
  line-height: 1.4;
  color: #555;
  box-sizing: border-box;
  resize: vertical;
}

textarea.value:focus {
  border-bottom: 1px solid #607d8b;
  box-shadow: 0 1px 0 0 #607d8b;
}

.meta-info, .date {
  margin-top: 5px;
  font-size: 0.85em;
}

/* New style for outdated translations */
.outdated {
  color: #ff9800; /* Orange highlight */
  font-weight: bold;
}

a {
  color: #455a64;
  text-decoration: underline;
}

.error {
  color: red;
  padding: 16px;
}

.loading-message {
  font-weight: bold;
  font-size: 18px;
  padding: 16px;
}

.button {
  box-shadow: none;
  background-color: transparent;
  color: #455a64;
  height: 36px;
  line-height: 36px;
  padding: 0 2rem;
  border: none;
  border-radius: 2px;
  transition: .3s ease-out;
  cursor: pointer;
  white-space: nowrap;
  display: inline-block;
  margin-top: 5px;
}

.button:hover {
  background-color: #eceff1;
}

.button.cta {
  background-color: #37474f;
  box-shadow: 0 2px 2px 0 rgba(0, 0, 0, 0.14), 0 1px 5px 0 rgba(0, 0, 0, 0.12), 0 3px 1px -2px rgba(0, 0, 0, 0.2);
  color: #fff;
}

.button.cta:hover {
  background: #ff9800;
  box-shadow: 0 3px 3px 0 rgba(0, 0, 0, 0.14), 0 1px 7px 0 rgba(0, 0, 0, 0.12), 0 3px 1px -1px rgba(0, 0, 0, 0.2);
}

.button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.translation-edit, .translation-create {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
