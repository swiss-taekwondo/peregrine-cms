const wait = (timeout) => new Promise(res => setTimeout(res, timeout));

const getLanguages = (page) => {
  return Object.keys(page && page.translations ? page.translations : {}).filter(key => key === 'de' || key === 'fr');
}

/**
 * Traverses a JSON object to find all fields that are marked as translatable
 * based on a provided translation model. It handles nested children by applying
 * the parent component's translation rules to them.
 *
 * @param {object} page The page in JSON representation.
 * @param {object} translationModel The JSON data from translation-model.json.
 * @returns {Array<object>} An array of translatable field.
 */
const findTranslatableFields = (page, translationModel) => {
  const translatableFields = [];

  /**
   * Recursively traverses the JSON object.
   * @param {any} currentNode The current node (object, array, or primitive) being inspected.
   * @param {string} currentPath The dot-notation path to the current node.
   * @param {Array<string>|null} fieldsToSearch The list of translatable field names inherited from a parent component.
   */
  const traverse = (currentNode, currentPath, fieldsToSearch) => {
    if (typeof currentNode !== 'object' || currentNode === null) {
      return;
    }

    let currentTranslatableFields = fieldsToSearch;

    if (currentNode.hasOwnProperty('sling:resourceType') && typeof currentNode['sling:resourceType'] === 'string') {
      const resourceType = currentNode['sling:resourceType'];
      const componentName = resourceType.split('/').pop();

      if (translationModel.hasOwnProperty(componentName)) {
        // Ignore cards with referenced objects
        if (componentName === 'cards' && currentNode.objectsdata) {
          return;
        }
        currentTranslatableFields = translationModel[componentName];
      }
    }

    if (currentTranslatableFields) {
      currentTranslatableFields.forEach(fieldName => {
        if (currentNode.hasOwnProperty(fieldName) && typeof currentNode[fieldName] === 'string' && currentNode[fieldName].trim() !== '') {
          translatableFields.push({
            path: currentPath || 'root',
            field: fieldName,
            value: currentNode[fieldName],
          });
        }
      });
    }

    for (const key in currentNode) {
      if (currentNode.hasOwnProperty(key)) {
        const newPath = currentPath ? `${currentPath}/${key}` : key;
        traverse(currentNode[key], newPath, currentTranslatableFields);
      }
    }
  };

  traverse(page, '', null);
  return translatableFields;
};

async function generateTranslations(translatableFields, lang) {
  const LANG_MAP = {
    'de' : 'german',
    'fr' : 'french',
    'en': 'english'
  };

  const language = LANG_MAP[lang];

  const values = [];
  translatableFields.forEach((field) => {
    if (field.value && !values.includes(field.value)) {
      values.push(field.value);
    }
  });

  try {
    const req = await fetch('/translate', {
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        responseSchema: {
          type: "array",
          items: {
            type: "string",
          }
        },
        contents: [{
          role: "user",
          parts: [
            {
              text: `Translate the array of strings in ${language}: ${JSON.stringify(values)}\n\nReturn the translations as array of strings. If a translation is not possible, use empty string.`
            },
          ],
        }],
      }),
      method: "POST",
    });

    if (!req.ok) {
      throw new Error(req.statusText);
    }

    const res = await req.json();

    const translatedValues = JSON.parse(res.candidates[0].content.parts[0].text);

    return values.map((original, i) => ({
      original,
      translated: translatedValues[i]
    }));
  } catch (e) {
    return {
      error: true,
      reason: e.message,
    };
  }
}

export default async function TranslateWidget() {
  let [editPath, pagePath] = window.location.pathname.split(':');
  let page = {};
  let lang = 'en';

  if (!pagePath.startsWith('/content/stkd/pages/protected/test')) {
    return;
  }

  if (pagePath.endsWith('/translations/de')) {
    lang = 'de';
    pagePath = pagePath.replace('/translations/de', '');
  }
  else if (pagePath.endsWith('/translations/fr')) {
    lang = 'fr';
    pagePath = pagePath.replace('/translations/fr', '');
  }

  const notifyError = (action) => {
    $perAdminApp.notifyUser('Error', 'Something went wrong. Please try again later.')

    if (action) {
      action.classList.remove('disabled');
      action.closest('.language').querySelector('.progress').remove();
    }
  };

  try {
    page = await(await fetch(pagePath + '.2.json')).json();
    const translations = Object.keys(page && page.translations ? page.translations : {}).filter(key => key === 'de' || key === 'fr');
    translations.push('en');

    const languages = [{
      value: 'en',
      name: 'English (Original)'
    }, {
      value: 'fr',
      name: 'French'
    }, {
      value: 'de',
      name: 'German'
    }];

    const id = 'admin-translations-modal';

    const translateButtons = l => `
            ${location.hostname === 'author.taekwondo.ch' ? `<button class="btn translate" data-translate="ai" data-lang="${l}">Translate with AI</button>` : ''}
            <button class="btn translate" data-translate="manual" data-lang="${l}">Translate manually</button>
        `;

    document.querySelector('.page-tree').insertAdjacentHTML('beforeend', `
            <button id="translations" class="btn" style="height: 32px; padding: 0 8px; display: flex; align-items: center; gap: 8px; margin: 0 1rem;">
                <i class="icon material-icons">language</i>
                <span style="text-transform: uppercase">${lang}</span>
            </button>
            <div id="${id}" class="modal materialize-modal">
                <div class="modal-header">Translations</div>
                <div class="modal-content">
                    <div style="display: flex;flex-direction: column;gap: 16px;">
                        ${languages.map(({name, value}) => `
                                <div class="language" style="display: flex; align-items: flex-start; flex-direction: column; gap: 8px; width: 100%; background: #fff; border: 1px solid #cfd8dc; padding: 1rem;">
                                    <div>${name}</div>
                                    <div style="margin-top: 8px;display: flex;gap: 16px; flex-wrap: wrap;">
                                        ${translations.includes(value) ? `
                                            ${value !== lang ? `<a href="${value === 'en' ? `${editPath}:${pagePath}` : `${editPath}:${pagePath}/translations/${value}`}" class="btn">Edit</a>` : ''}
                                            ${value !== 'en' ? `<button class="btn translation-delete" data-lang="${value}">Delete</button>`: ''}
                                        ` : translateButtons(value)}
                                    </div>
                                </div>
                            `).join('')}
                    </div>
                </div>
            </div>
        `);

    let $modal = $(`#${id}`);
    $modal.modal({
      dismissible: true,
      opacity: .5,
      inDuration: 300,
      outDuration: 300,
      startingTop: '4%',
      endingTop: '10%'
    });

    $modal[0].querySelector('.modal-content').addEventListener('click', async (event) => {
      const action = event.target;

      if (action.matches('.translation-delete')) {
        action.classList.add('disabled');

        const req = await fetch(
          `/bin/cpm/nodes/node.json${pagePath}/translations/${action.dataset.lang}`, {
            method: 'DELETE'
          }
        );

        action.classList.remove('disabled');

        if (!req.ok) {
          $modal.modal('close');
          setTimeout(() => {
            notifyError(action);
          }, 500);
        } else {
          if (lang === action.dataset.lang) {
            $modal.modal('close');
            setTimeout(() => {
              $perAdminApp.notifyUser('Success', 'You are being redirected to the original page.')
              location.href = `${editPath}:${pagePath}`;
            }, 500);
          }
          else {
            action.parentElement.innerHTML = translateButtons(action.dataset.lang);
          }
        }
      } else if (action.matches('.translate')) {
        action.classList.add('disabled');
        action.closest('.language').insertAdjacentHTML('beforeend', `
                  <div class="progress"><div class="indeterminate"></div></div>
             `);

        // Create translations node if none
        let languages = [];
        page = await(await fetch(`${pagePath}.infinity.json`)).json();

        if (!page.translations) {
          const formData = new FormData();
          formData.append('_charset_', 'UTF-8');
          formData.append('path', pagePath);
          formData.append('type', `nt:unstructured`);
          formData.append('name', `translations`);
          const reqCreate = await fetch('/bin/cpm/nodes/node.create.json', {
            method: 'POST',
            body: formData
          });

          if (!reqCreate.ok) {
            notifyError(action);
            return;
          }
        }
        else {
          languages = getLanguages(page);
        }

        const existingLanguage = languages.find(lang => lang === action.dataset.lang);
        const translationPath = `${pagePath}/translations/${action.dataset.lang}`;

        if (existingLanguage) {
          console.log('Language exists already');
          $perAdminApp.notifyUser('Success', 'You are being redirected to the translated page.')
          location.href = `${editPath}:${translationPath}`;
          return;
        }

        // Create new translated page
        const formData = new FormData();
        formData.append('_charset_', 'UTF-8');
        formData.append('path', `${pagePath}/translations`);
        formData.append('type', `per:Page`);
        formData.append('name', action.dataset.lang);

        const reqCreate = await fetch('/bin/cpm/nodes/node.create.json', {
          method: 'POST',
          body: formData
        });
        if (!reqCreate.ok) {
          notifyError(action);
          return;
        }

        const reqCopy = await fetch(`/bin/cpm/nodes/node.copy.json${translationPath}`, {
          method: 'PUT',
          headers: {
            'content-type': 'application/json'
          },
          body: JSON.stringify({
            name: 'jcr:content',
            path: `${pagePath}/jcr:content`
          })
        });

        if (!reqCopy.ok) {
          notifyError(action);
          return;
        }

        await wait(1000);

        // Auto translate
        let successMessage = '';
        if (action.dataset.translate === 'ai') {
          const tenant = pagePath.split('/')[2];
          const reqTranslationModel = await fetch(`/apps/${tenant}/components/translation-model.json`);
          if (!reqTranslationModel.ok) {
            notifyError(action);
            return;
          }

          const translationModel = await reqTranslationModel.json();

          // Find fields to translate
          const translatableFields = findTranslatableFields(page, translationModel);

          if (!translatableFields.length) {
            console.log('No translatable fields found');
            $perAdminApp.notifyUser('Success', 'You are being redirected to the page for manual translation.')
            location.href = `${editPath}:${translationPath}`;
            return;
          }

          // Generate translations with LLM and update the page
          const translationsLLM = await generateTranslations(translatableFields, action.dataset.lang);

          // Apply translations sequentially
          // TODO Handle translations error
          for (const item of translatableFields) {
            const translation = translationsLLM.find(({original}) => original === item.value);
            if (translation && translation.translated) {
              const reqTranslate = await fetch(`/bin/cpm/nodes/property.json${translationPath}/${item.path.replace('jcr:content', '_jcr_content')}`, {
                method: 'PUT',
                headers: {
                  'content-type': 'application/json'
                },
                body: JSON.stringify({
                  name: item.field,
                  value: translation.translated
                })
              });

              if (!reqTranslate.ok) {
                console.log(`Error translating "${item.field}" with "${translation.translated}"`);
              }
            }
          }

          successMessage = 'You are being redirected to the translated page.'
        }
        else {
          successMessage = 'You are being redirected to the page for manual translation.'
        }

        // Remove translation node if any
        await fetch(`/bin/cpm/nodes/node.json${translationPath}/translations`, {
          method: 'DELETE'
        });

        // Remove replication fields if any
        await fetch(`/bin/cpm/nodes/property.remove.json${pagePath}/translations/${action.dataset.lang}/_jcr_content`, {
          method: 'DELETE',
          headers: {
            'content-type': 'application/json'
          },
          body: JSON.stringify({
            names: [
              'per:ReplicatedBy',
              'per:ReplicationLastAction',
              'per:ReplicationRef',
              'per:Replicated',
            ]
          })
        });

        $modal.modal('close');
        setTimeout(async () => {
          $perAdminApp.notifyUser('Success', successMessage)

          await wait(1000);

          location.href = `${editPath}:${translationPath}`;
        }, 500);
      }
    });

    document.getElementById('translations').onclick = () => {
      $modal.modal('open');
    };
  }
  catch (e) {
    console.log(e);
    notifyError();
  }
}
