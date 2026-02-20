/*-
 * #%L
 * admin base - UI Apps
 * %%
 * Copyright (C) 2017 headwire inc.
 * %%
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
import { LoggerFactory } from './logger';
import { DATA_EXTENSION, SUFFIX_PARAM_SEPARATOR } from './constants.js';

let logger = LoggerFactory.logger('utils').setLevelDebug();

export function makePathInfo(path) {
  logger.fine('makePathInfo for path', path);
  var htmlPos = path.indexOf('.html');
  var pathPart = path;
  var suffixPath = '';
  if (htmlPos >= 0) {
    suffixPath = path.slice(htmlPos);
    pathPart = path.slice(0, htmlPos + 5);
  }

  var suffixParams = {};
  if (suffixPath.length > 0) {
    suffixPath = suffixPath.slice(6);
    var suffixParamList = suffixPath.split(SUFFIX_PARAM_SEPARATOR);
    for (var i = 0; i < suffixParamList.length; i += 2) {
      suffixParams[suffixParamList[i]] = suffixParamList[i + 1];
    }
  }

  var ret = {
    path: pathPart,
    suffix: suffixPath,
    suffixParams: suffixParams,
  };
  logger.fine('makePathInfo res:', ret);
  return ret;
}

export function pagePathToDataPath(path) {
  var res = null;
  var hasExtension = /\.[^\/\\]+$/.test(path);
  if (hasExtension) {
    if (path.endsWith('.html')) {
      // .html found replace with DATA_EXTENSION
      res = path.slice(0, -5) + DATA_EXTENSION;
    } else {
      // has another extension, don't modify
      res = path;
    }
  } else {
    // no extension found, add DATA_EXTENSION
    res = path + DATA_EXTENSION;
  }
  logger.fine('result', res);
  return res;
}

export function set(node, path, value) {
  var vue = $perAdminApp.getApp();
  path = path
    .slice(1)
    .split('/')
    .reverse();
  while (path.length > 1) {
    var segment = path.pop();
    if (!node[segment]) {
      if (vue) {
        Vue.set(node, segment, {});
      } else {
        node[segment] = {};
      }
    }
    node = node[segment];
  }
  if (vue) {
    Vue.set(node, path[0], value);
  } else {
    node[path[0]] = value;
  }
}

export function get(node, path, value) {
  var vue = $perAdminApp.getApp();
  path = path
    .slice(1)
    .split('/')
    .reverse();
  while (path.length > 1) {
    var segment = path.pop();
    if (!node[segment]) {
      if (vue) {
        Vue.set(node, segment, {});
      } else {
        node[segment] = {};
      }
    }
    node = node[segment];
  }
  if (value !== undefined && !node[path[0]]) {
    if (vue) {
      Vue.set(node, path[0], value);
    } else {
      node[path[0]] = value;
    }
  }
  return node[path[0]];
}

export function parentPath(path) {
  logger.fine('parentPath()', path);
  let segments = path.split('/');
  let name = segments.pop();
  let parentPath = segments.join('/');
  let ret = { parentPath: parentPath, name: name };
  logger.fine('parentPath() res:', ret);
  return ret;
}

export function stripNulls(data) {
  for (var key in data) {
    if (data[key] === null) delete data[key];
    if (typeof data[key] === 'object') {
      stripNulls(data[key]);
    }
  }
}

export function jsonEqualizer(name, value) {
  if (value === null || value === undefined || value.length === 0) {
    return undefined;
  }
  return value;
}

export const deepClone = (obj) => {
  return JSON.parse(JSON.stringify(obj));
};

export const capitalizeFirstLetter = (str) => {
  return str.charAt(0).toUpperCase() + str.slice(1);
};

export const getCaretCharacterOffsetWithin = (element) => {
  let caretOffset = 0;
  const doc = element.ownerDocument || element.document;
  const win = doc.defaultView || doc.parentWindow;
  let sel;
  if (typeof win.getSelection != 'undefined') {
    sel = win.getSelection();
    if (sel.rangeCount > 0) {
      const range = win.getSelection().getRangeAt(0);
      const preCaretRange = range.cloneRange();
      preCaretRange.selectNodeContents(element);
      preCaretRange.setEnd(range.endContainer, range.endOffset);
      caretOffset = preCaretRange.toString().length;
    }
  } else if ((sel = doc.selection) && sel.type !== 'Control') {
    const textRange = sel.createRange();
    const preCaretTextRange = doc.body.createTextRange();
    preCaretTextRange.moveToElementText(element);
    preCaretTextRange.setEndPoint('EndToEnd', textRange);
    caretOffset = preCaretTextRange.text.length;
  }
  return caretOffset;
};

export const saveSelection = (containerEl, document = document) => {
  const window = document.defaultView;
  if (window.getSelection && document.createRange) {
    const range = window.getSelection().getRangeAt(0);
    const preSelectionRange = range.cloneRange();
    preSelectionRange.selectNodeContents(containerEl);
    preSelectionRange.setEnd(range.startContainer, range.startOffset);
    const start = preSelectionRange.toString().length;

    return {
      start: start,
      end: start + range.toString().length,
    };
  } else if (document.selection) {
    const selectedTextRange = document.selection.createRange();
    const preSelectionTextRange = document.body.createTextRange();
    preSelectionTextRange.moveToElementText(containerEl);
    preSelectionTextRange.setEndPoint('EndToStart', selectedTextRange);
    const start = preSelectionTextRange.text.length;

    return {
      start: start,
      end: start + selectedTextRange.text.length,
    };
  }
};

export const restoreSelection = (containerEl, savedSel, doc = document) => {
  const win = doc.defaultView;

  if (win.getSelection && doc.createRange) {
    let charIndex = 0,
      range = doc.createRange();
    range.setStart(containerEl, 0);
    range.collapse(true);
    let nodeStack = [containerEl],
      node,
      foundStart = false,
      stop = false;

    while (!stop && (node = nodeStack.pop())) {
      if (node.nodeType === 3) {
        const nextCharIndex = charIndex + node.length;
        if (
          !foundStart &&
          savedSel.start >= charIndex &&
          savedSel.start <= nextCharIndex
        ) {
          range.setStart(node, savedSel.start - charIndex);
          foundStart = true;
        }
        if (
          foundStart &&
          savedSel.end >= charIndex &&
          savedSel.end <= nextCharIndex
        ) {
          range.setEnd(node, savedSel.end - charIndex);
          stop = true;
        }
        charIndex = nextCharIndex;
      } else {
        let i = node.childNodes.length;
        while (i--) {
          nodeStack.push(node.childNodes[i]);
        }
      }
    }

    const sel = win.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
  } else if (doc.selection) {
    const textRange = doc.body.createTextRange();
    textRange.moveToElementText(containerEl);
    textRange.collapse(true);
    textRange.moveEnd('character', savedSel.end);
    textRange.moveStart('character', savedSel.start);
    textRange.select();
  }
};

export const getCurrentDateTime = () => {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;
  const day = now.getDate();
  const hour = now.getHours();
  const minute = now.getMinutes();
  const second = now.getSeconds();
  const milli = now.getMilliseconds();

  return `${year}-${month}-${day}_${hour}-${minute}-${second}-${milli}`;
};

export function isChromeBrowser() {
  return (
    /Chrome/.test(navigator.userAgent) && /Google Inc/.test(navigator.vendor)
  );
}

export function focusElement(target, win = window) {
  const selection = win.getSelection();
  const range = win.document.createRange();
  range.setStart(target, 0);
  range.setEnd(target, 0);
  selection.removeAllRanges();
  selection.addRange(range);
}

export function createDebouncer() {
  let timeout;
  let oldReject;
  return {
    call: (func, wait) =>
      new Promise((resolve, reject) => {
        let context = this,
          args = arguments;
        let later = function() {
          resolve(func.apply(context, args));
        };

        if (timeout) {
          clearTimeout(timeout);
          oldReject();
        }

        oldReject = reject;
        timeout = setTimeout(later, wait);
      }),
  };
}

export function chainPromises(operation, first) {
  return new Promise(function(resolve, reject) {
    let last = first;
    let next;
    while (i < maxRequests) {
      next = last.then(operation);
      last = next;
      i++;
    }
    return next;
  });
}

export function objectToFormData(obj) {
  const formData = new FormData();

  Object.keys(obj).forEach((key) => {
    formData.append(key, obj[key]);
  });

  return formData;
}

export function asyncLoadJsScript(src) {
  return new Promise((resolve, reject) => {
    if (!document.querySelector(`script[src="${src}"]`)) {
      const script = document.createElement('script');

      script.setAttribute('src', src);
      document.body.appendChild(script);
      script.onload = () => {
        logger.fine(`successfully loaded script: ${src}`);
        resolve();
      };
      script.onerror = () => {
        logger.fine(`failed to load script: ${src}`);
        reject();
      };
    } else {
      logger.fine(`script already present: ${src}`);
      resolve();
    }
  });
}

export function isMac() {
  return window.navigator.platform.toUpperCase().indexOf('MAC') >= 0;
}

export function attachAddressAutocomplete(input) {
  if (!input || input._autocompleteAttached) return;

  input._autocompleteAttached = true;

  const listboxId = `location-autocomplete-listbox-${Math.random().toString(36).slice(2)}`;
  input.setAttribute('role', 'combobox');
  input.setAttribute('aria-autocomplete', 'list');
  input.setAttribute('aria-expanded', 'false');
  input.setAttribute('aria-controls', listboxId);
  input.setAttribute('aria-haspopup', 'listbox');

  const dropdown = document.createElement('ul');
  dropdown.id = listboxId;
  dropdown.setAttribute('role', 'listbox');
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
  });
  input.parentElement.style.position = 'relative';
  input.insertAdjacentElement('afterend', dropdown);

  let activeIndex = -1;
  let featureList = [];

  const setActiveItem = (index) => {
    const items = dropdown.querySelectorAll('[role="option"]');
    items.forEach((item, i) => {
      const isActive = i === index;
      item.setAttribute('aria-selected', String(isActive));
      item.style.background = isActive ? '#f5f5f5' : '';
    });
    activeIndex = index;
    if (index >= 0 && items[index]) {
      input.setAttribute('aria-activedescendant', items[index].id);
    } else {
      input.removeAttribute('aria-activedescendant');
    }
  };

  const hideDropdown = () => {
    dropdown.style.display = 'none';
    dropdown.innerHTML = '';
    activeIndex = -1;
    input.setAttribute('aria-expanded', 'false');
    input.removeAttribute('aria-activedescendant');
  };

  const formatAddress = ({ street, housenumber, postcode, city }) => {
    const streetPart = [street, housenumber].filter(Boolean).join(' ');
    const cityPart = [postcode, city].filter(Boolean).join(' ');
    return [streetPart, cityPart].filter(Boolean).join(', ');
  };

  const selectFeature = (feature) => {
    input.value = formatAddress(feature.properties);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    hideDropdown();
    input.focus();
  };

  const showSuggestions = (features) => {
    featureList = features.filter((f) => f.properties.street);
    dropdown.innerHTML = '';
    if (!featureList.length) { hideDropdown(); return }
    featureList.forEach((feature, i) => {
      const label = formatAddress(feature.properties);
      const li = document.createElement('li');
      li.textContent = label;
      li.id = `location-option-${i}`;
      li.setAttribute('role', 'option');
      li.setAttribute('aria-selected', 'false');
      Object.assign(li.style, {
        borderBottom: '1px solid #eee',
        cursor: 'pointer',
        padding: '8px 12px',
        whiteSpace: 'normal',
        wordBreak: 'break-word',
      });
      li.addEventListener('mouseenter', () => setActiveItem(i));
      li.addEventListener('mouseleave', () => setActiveItem(-1));
      li.addEventListener('mousedown', (e) => { e.preventDefault(); selectFeature(feature) });
      dropdown.append(li);
    });
    activeIndex = -1;
    dropdown.style.display = 'block';
    dropdown.style.width = input.offsetWidth + 'px';
    input.setAttribute('aria-expanded', 'true');
  };

  input.addEventListener('keydown', (e) => {
    if (dropdown.style.display === 'none') return;
    const items = dropdown.querySelectorAll('[role="option"]');
    if (e.key === 'ArrowDown') { e.preventDefault(); setActiveItem(Math.min(activeIndex + 1, items.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActiveItem(Math.max(activeIndex - 1, -1)); }
    else if (e.key === 'Enter' && activeIndex >= 0) { e.preventDefault(); selectFeature(featureList[activeIndex]); }
    else if (e.key === 'Escape') { hideDropdown(); }
  });

  let debounceTimer;
  input.addEventListener('input', () => {
    clearTimeout(debounceTimer);
    const val = input.value.trim();
    if (val.length < 3) { hideDropdown(); return; }
    debounceTimer = setTimeout(async () => {
      try {
        const res = await fetch(`https://photon.komoot.io/api/?q=${encodeURIComponent(val)}&limit=50&lang=en&bbox=5.9,45.8,10.5,47.8`);
        if (!res.ok) return;
        const data = await res.json();
        showSuggestions(data.features || []);
      } catch { /* Do nothing */ }
    }, 300);
  });

  input.addEventListener('blur', hideDropdown);
}

