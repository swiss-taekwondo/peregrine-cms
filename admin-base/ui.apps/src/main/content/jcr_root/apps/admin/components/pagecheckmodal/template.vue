<!--
  #%L
  admin base - UI Apps
  %%
  Copyright (C) 2026 headwire inc.
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
  <div class="page-check-modal-root">
    <admin-components-materializemodal
        ref="materializemodal"
        class="page-check-modal"
        v-on:complete="$emit('complete',$event)"
        v-bind:modalTitle="modalTitle">

    <div class="page-check-summary" v-bind:class="{'page-check-summary-ok': !hasIssues}">
      <i class="material-icons">{{ hasIssues ? 'warning' : 'check_circle' }}</i>
      <span v-if="hasIssues">Fix {{ issueCount }} issue{{ issueCount === 1 ? '' : 's' }} before publishing.</span>
      <span v-else>All page checks passed.</span>
    </div>

    <ul class="page-check-list">
      <li v-for="check in checks" v-bind:key="check.id" class="page-check-row">
        <div class="page-check-row-title" v-on:click="toggleCheck(check.id)">
            <span class="left">
                <i class="material-icons" v-bind:class="{'page-check-icon-ok': checkHasIssues(check) === false}">
                    {{ checkHasIssues(check) ? 'error_outline' : 'check_circle' }}
                </i>
          <span>{{ checkLabel(check) }}</span>
            </span>
            <span class="right">
                <span v-if="check.hint" class="page-check-hint">
                  <i class="material-icons">info_outline</i>
                </span>
                <span v-if="checkIssueCount(check) && !isBinaryCheck(check)" class="page-check-count">{{ checkIssueCount(check) }}</span>
                <span v-if="check.id === 'valid-links' && warningTotal > 0" class="page-check-warning-count">{{ warningTotal }}</span>
                <i class="material-icons page-check-expand">{{ isCheckOpen(check.id) ? 'expand_less' : 'expand_more' }}</i>
          </span>
        </div>
        <div v-if="isCheckOpen(check.id) && hasTabs(check)" class="page-check-tabs">
          <button
              v-bind:class="{'active': activeTab(check.id) === 'incorrect'}"
              type="button"
              v-on:click.stop="setCheckTab(check.id, 'incorrect')">
            Incorrect ({{ incorrectCount(check) - redirectIncorrectCount }})
          </button>
          <button
              v-if="check.id === 'valid-links' && redirectIncorrectCount > 0"
              v-bind:class="{'active': activeTab(check.id) === 'redirects-incorrect'}"
              type="button"
              v-on:click.stop="setCheckTab(check.id, 'redirects-incorrect')">
            Redirects - Incorrect ({{ redirectIncorrectCount }})
          </button>
          <button
              v-if="check.id === 'valid-links' && loginRedirectCount > 0"
              v-bind:class="{'active': activeTab(check.id) === 'manual-test'}"
              type="button"
              v-on:click.stop="setCheckTab(check.id, 'manual-test')">
            Manual Test ({{ loginRedirectCount }})
          </button>
          <button
              v-if="check.id === 'valid-links' && manualApprovedCount > 0"
              v-bind:class="{'active': activeTab(check.id) === 'manual-approved'}"
              type="button"
              v-on:click.stop="setCheckTab(check.id, 'manual-approved')">
            Manual Test - Approved ({{ manualApprovedCount }})
          </button>
          <button
              v-if="check.id === 'valid-links' && manualDisapprovedCount > 0"
              v-bind:class="{'active': activeTab(check.id) === 'manual-disapproved'}"
              type="button"
              v-on:click.stop="setCheckTab(check.id, 'manual-disapproved')">
            Manual Test - Disapproved ({{ manualDisapprovedCount }})
          </button>
          <button
              v-if="check.id === 'valid-links' && redirectChangedCount > 0"
              v-bind:class="{'active': activeTab(check.id) === 'redirect-changed'}"
              type="button"
              v-on:click.stop="setCheckTab(check.id, 'redirect-changed')">
            Redirects Changed ({{ redirectChangedCount }})
          </button>
          <button
              v-if="check.id === 'valid-links' && redirectCorrectCount > 0"
              v-bind:class="{'active': activeTab(check.id) === 'redirects-correct'}"
              type="button"
              v-on:click.stop="setCheckTab(check.id, 'redirects-correct')">
            Redirects - Correct ({{ redirectCorrectCount }})
          </button>
          <button
              v-bind:class="{'active': activeTab(check.id) === 'correct'}"
              type="button"
              v-on:click.stop="setCheckTab(check.id, 'correct')">
            Correct ({{ correctCount(check) }})
          </button>
        </div>
        <div v-if="isCheckOpen(check.id) && check.hint" class="page-check-hint-message">
          <i class="material-icons">info_outline</i>
          <span>{{ check.hint }}</span>
        </div>
        <div v-if="isCheckOpen(check.id) && check.id === 'valid-links' && (redirectIncorrectCount > 0 || redirectCorrectCount > 0 || loginRedirectCount > 0) && (activeTab(check.id) === 'redirects-incorrect' || activeTab(check.id) === 'redirects-correct' || activeTab(check.id) === 'manual-test')" class="page-check-hint-message page-check-redirect-warning">
          <i class="material-icons">warning</i>
          <span>Redirects can change at any time without notice. A link that works today may break tomorrow.</span>
        </div>
        <div v-if="isCheckOpen(check.id) && check.id === 'valid-links' && (manualApprovedCount > 0 || manualDisapprovedCount > 0) && (activeTab(check.id) === 'manual-approved' || activeTab(check.id) === 'manual-disapproved')" class="page-check-hint-message page-check-manual-test-banner">
          <i class="material-icons">info_outline</i>
          <span>{{ check.hint || 'Manually verified links may break at any time. Re-check periodically.' }}</span>
        </div>
        <ul v-if="visibleIssues(check).length" class="page-check-issues">
          <li v-for="issue in visibleIssues(check)" v-bind:key="issue.id">
            <template v-if="issue.type === 'image-alt'">
              <img v-if="issue.imageValue" v-bind:src="imageSrc(issue.imageValue)" alt=""/>
              <div class="page-check-message">{{ issue.message }}</div>
              <div class="page-check-location">{{ issue.location }}</div>
              <div class="page-check-guidance">Alt text: {{ issue.altText || 'Missing' }}</div>
              <button
                  v-if="editingIssueId !== issue.id && issue.altKey"
                  class="waves-effect waves-green btn-flat page-check-edit"
                  type="button"
                  v-on:click="editImageAlt(issue)">
                <i class="material-icons">edit</i>
                Edit
              </button>
              <div v-if="editingIssueId === issue.id && issue.altKey" class="page-check-image-editor">
                <label>
                  Alt text
                  <input type="text" v-model="editingAltText"/>
                </label>
                <div class="page-check-image-actions">
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelImageAlt" v-bind:disabled="isSavingAltText">
                    Cancel
                  </button>
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveImageAlt(issue)" v-bind:disabled="isSavingAltText">
                    Save
                  </button>
                </div>
              </div>
            </template>
            <template v-else-if="issue.type === 'broken-link'">
              <div class="page-check-message">{{ issue.message }}</div>
              <div class="page-check-location">{{ issue.location }}</div>
              <div v-if="issue.linkText" class="page-check-link-text">Link text: {{ issue.linkText }}</div>
              <div class="page-check-guidance">
                URL: <a :href="issue.href" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ issue.href }}</a>
              </div>
              <div v-if="issue.status" class="page-check-guidance">Status: {{ issue.status }}</div>
              <template v-if="issue.isRedirect">
                <div class="page-check-guidance">
                  Redirects to: <a :href="issue.finalUrl || issue.redirectUrl" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ issue.finalUrl || issue.redirectUrl }}</a>
                </div>
                <div class="page-check-guidance" v-if="issue.finalStatus">Final status: {{ issue.finalStatus }}</div>
              </template>
              <button
                  v-if="isEditableLink(issue) && editingIssueId !== issue.id"
                  class="waves-effect waves-green btn-flat page-check-edit"
                  type="button"
                  v-on:click="editLink(issue)">
                <i class="material-icons">edit</i>
                Edit
              </button>
              <div v-if="editingIssueId === issue.id && isEditableLink(issue)" class="page-check-property-editor">
                <label>
                  Link URL
                  <input type="text" v-model="editingLinkValue"/>
                </label>
                <div class="page-check-image-actions">
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="openLinkBrowser(issue)" v-bind:disabled="isSavingLink">
                    <i class="material-icons">folder_open</i>
                    Browse
                  </button>
                  <button class="waves-effect waves-green btn-flat page-check-remove-link" type="button" v-on:click="removeLink(issue)" v-bind:disabled="isSavingLink">
                    <i class="fa fa-chain-broken"></i>
                    Remove
                  </button>
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelLink" v-bind:disabled="isSavingLink">
                    Cancel
                  </button>
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveLink(issue)" v-bind:disabled="isSavingLink">
                    Save
                  </button>
                </div>
              </div>
            </template>
            <template v-else>
            <div class="page-check-message">{{ issue.message }}</div>
            <div class="page-check-location">{{ issue.location }}</div>
            <div v-if="issue.linkText" class="page-check-link-text">Link text: {{ issue.linkText }}</div>
            <div v-if="issue.type === 'html-link' || issue.type === 'link-field'" class="page-check-guidance">
              Link URL: <a :href="issue.guidance" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ issue.guidance }}</a>
            </div>
            <div v-else class="page-check-guidance">Link URL: {{ issue.guidance }}</div>
            <button
                v-if="(issue.type === 'page-property' || issue.type === 'text-field') && editingIssueId !== issue.id"
                class="waves-effect waves-green btn-flat page-check-edit"
                type="button"
                v-on:click="editTextField(issue)">
              <i class="material-icons">edit</i>
              Edit
            </button>
            <div v-if="editingIssueId === issue.id && (issue.type === 'page-property' || issue.type === 'text-field')" class="page-check-property-editor">
              <label>
                {{ issue.propertyLabel }}
                <input v-if="issue.propertyKey === 'jcr:title'" type="text" v-model="editingPropertyValue"/>
                <textarea v-else v-model="editingPropertyValue"></textarea>
              </label>
              <div class="page-check-image-actions">
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelPageProperty" v-bind:disabled="isSavingPageProperty">
                  Cancel
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveTextField(issue)" v-bind:disabled="isSavingPageProperty">
                  Save
                </button>
              </div>
            </div>
            <button
                v-if="isEditableLink(issue) && editingIssueId !== issue.id"
                class="waves-effect waves-green btn-flat page-check-edit"
                type="button"
                v-on:click="editLink(issue)">
              <i class="material-icons">edit</i>
              Edit
            </button>
            <div v-if="editingIssueId === issue.id && isEditableLink(issue)" class="page-check-property-editor">
              <label>
                {{ issue.propertyLabel }}
                <input type="text" v-model="editingLinkValue"/>
              </label>
              <div class="page-check-image-actions">
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="openLinkBrowser(issue)" v-bind:disabled="isSavingLink">
                  <i class="material-icons">folder_open</i>
                  Browse
                </button>
                <button class="waves-effect waves-green btn-flat page-check-remove-link" type="button" v-on:click="removeLink(issue)" v-bind:disabled="isSavingLink">
                  <i class="fa fa-chain-broken"></i>
                  Remove
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelLink" v-bind:disabled="isSavingLink">
                  Cancel
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveLink(issue)" v-bind:disabled="isSavingLink">
                  Save
                </button>
              </div>
            </div>
            </template>
          </li>
        </ul>
        <ul v-if="isCheckOpen(check.id) && check.id === 'valid-links' && activeTab(check.id) === 'redirects-incorrect' && redirectsIncorrect.length" class="page-check-issues">
          <li v-for="item in redirectsIncorrect" v-bind:key="item.id">
            <div class="page-check-message">Redirects to: {{ item.finalUrl || item.redirectUrl }}</div>
            <div class="page-check-location">{{ item.location }}</div>
            <div v-if="item.linkText" class="page-check-link-text">Link text: {{ item.linkText }}</div>
            <div class="page-check-guidance">
              Original URL: <a :href="item.href" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ item.href }}</a>
            </div>
            <div class="page-check-guidance">Redirect status: {{ item.status }}</div>
            <div class="page-check-guidance">
              Final URL: <a :href="item.finalUrl || item.redirectUrl" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ item.finalUrl || item.redirectUrl }}</a>
            </div>
            <div class="page-check-guidance" v-if="item.finalStatus">
              Final status: {{ item.finalStatus }}
              <span class="page-check-status-error">(BROKEN)</span>
            </div>
            <button
                v-if="isEditableLink(item) && editingIssueId !== item.id"
                class="waves-effect waves-green btn-flat page-check-edit"
                type="button"
                v-on:click="editLink(item)">
              <i class="material-icons">edit</i>
              Edit
            </button>
            <div v-if="editingIssueId === item.id && isEditableLink(item)" class="page-check-property-editor">
              <label>
                Link URL
                <input type="text" v-model="editingLinkValue"/>
              </label>
              <div class="page-check-image-actions">
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="openLinkBrowser(item)" v-bind:disabled="isSavingLink">
                  <i class="material-icons">folder_open</i>
                  Browse
                </button>
                <button class="waves-effect waves-green btn-flat page-check-remove-link" type="button" v-on:click="removeLink(item)" v-bind:disabled="isSavingLink">
                  <i class="fa fa-chain-broken"></i>
                  Remove
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelLink" v-bind:disabled="isSavingLink">
                  Cancel
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveLink(item)" v-bind:disabled="isSavingLink">
                  Save
                </button>
              </div>
            </div>
          </li>
        </ul>
        <ul v-if="isCheckOpen(check.id) && check.id === 'valid-links' && activeTab(check.id) === 'redirects-correct' && redirectsCorrect.length" class="page-check-issues">
          <li v-for="item in redirectsCorrect" v-bind:key="item.id">
            <div class="page-check-message">Redirects to: {{ item.finalUrl || item.redirectUrl }}</div>
            <div class="page-check-location">{{ item.location }}</div>
            <div v-if="item.linkText" class="page-check-link-text">Link text: {{ item.linkText }}</div>
            <div class="page-check-guidance">
              Original URL: <a :href="item.href" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ item.href }}</a>
            </div>
            <div class="page-check-guidance">Redirect status: {{ item.status }}</div>
            <div class="page-check-guidance">
              Final URL: <a :href="item.finalUrl || item.redirectUrl" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ item.finalUrl || item.redirectUrl }}</a>
            </div>
            <div class="page-check-guidance" v-if="item.finalStatus">
              Final status: {{ item.finalStatus }}
              <span class="page-check-status-ok">(OK)</span>
            </div>
            <button
                v-if="isEditableLink(item) && editingIssueId !== item.id"
                class="waves-effect waves-green btn-flat page-check-edit"
                type="button"
                v-on:click="editLink(item)">
              <i class="material-icons">edit</i>
              Edit
            </button>
            <div v-if="editingIssueId === item.id && isEditableLink(item)" class="page-check-property-editor">
              <label>
                Link URL
                <input type="text" v-model="editingLinkValue"/>
              </label>
              <div class="page-check-image-actions">
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="openLinkBrowser(item)" v-bind:disabled="isSavingLink">
                  <i class="material-icons">folder_open</i>
                  Browse
                </button>
                <button class="waves-effect waves-green btn-flat page-check-remove-link" type="button" v-on:click="removeLink(item)" v-bind:disabled="isSavingLink">
                  <i class="fa fa-chain-broken"></i>
                  Remove
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelLink" v-bind:disabled="isSavingLink">
                  Cancel
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveLink(item)" v-bind:disabled="isSavingLink">
                  Save
                </button>
              </div>
            </div>
          </li>
        </ul>
        <ul v-if="isCheckOpen(check.id) && check.id === 'valid-links' && activeTab(check.id) === 'manual-test' && loginRedirects.length" class="page-check-issues">
          <li v-for="item in loginRedirects" v-bind:key="item.id">
            <div class="page-check-message">Login required</div>
            <div class="page-check-location">{{ item.location }}</div>
            <div v-if="item.linkText" class="page-check-link-text">Link text: {{ item.linkText }}</div>
            <div class="page-check-guidance">
              URL: <a :href="item.href" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ item.href }}</a>
            </div>
            <div class="page-check-guidance">Redirects to: {{ item.redirectUrl }}</div>
            <div class="page-check-guidance page-check-manual-test-hint">
              <i class="material-icons">info_outline</i>
              <span>This link requires authentication. Please verify manually.</span>
            </div>
            <div class="page-check-manual-actions">
              <button class="waves-effect waves-green btn-flat page-check-approve" type="button" v-on:click.stop="approveManualLink(item.href)" v-bind:disabled="isSavingManualCheck">
                <i class="material-icons">check</i>
                Approve
              </button>
              <button class="waves-effect waves-green btn-flat page-check-disapprove" type="button" v-on:click.stop="disapproveManualLink(item.href)" v-bind:disabled="isSavingManualCheck">
                <i class="material-icons">close</i>
                Disapprove
              </button>
            </div>
            <button
                v-if="isEditableLink(item) && editingIssueId !== item.id"
                class="waves-effect waves-green btn-flat page-check-edit"
                type="button"
                v-on:click="editLink(item)">
              <i class="material-icons">edit</i>
              Edit
            </button>
            <div v-if="editingIssueId === item.id && isEditableLink(item)" class="page-check-property-editor">
              <label>
                Link URL
                <input type="text" v-model="editingLinkValue"/>
              </label>
              <div class="page-check-image-actions">
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="openLinkBrowser(item)" v-bind:disabled="isSavingLink">
                  <i class="material-icons">folder_open</i>
                  Browse
                </button>
                <button class="waves-effect waves-green btn-flat page-check-remove-link" type="button" v-on:click="removeLink(item)" v-bind:disabled="isSavingLink">
                  <i class="fa fa-chain-broken"></i>
                  Remove
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelLink" v-bind:disabled="isSavingLink">
                  Cancel
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveLink(item)" v-bind:disabled="isSavingLink">
                  Save
                </button>
              </div>
            </div>
          </li>
        </ul>
        <ul v-if="isCheckOpen(check.id) && check.id === 'valid-links' && activeTab(check.id) === 'manual-approved' && manualApprovedLinks.length" class="page-check-issues">
          <li v-for="item in manualApprovedLinks" v-bind:key="item.id">
            <div class="page-check-message">
              <i class="material-icons page-check-approved-icon">check_circle</i>
              Manually approved
            </div>
            <div class="page-check-location">{{ item.location }}</div>
            <div v-if="item.linkText" class="page-check-link-text">Link text: {{ item.linkText }}</div>
            <div class="page-check-guidance">
              URL: <a :href="item.href" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ item.href }}</a>
            </div>
            <div class="page-check-guidance page-check-approved-timestamp">
              <i class="material-icons">schedule</i>
              Approved: {{ formatManualTimestamp(item.approvedAt) }}
            </div>
            <div class="page-check-manual-actions">
              <button class="waves-effect waves-green btn-flat page-check-disapprove" type="button" v-on:click.stop="disapproveManualLink(item.href)" v-bind:disabled="isSavingManualCheck">
                <i class="material-icons">close</i>
                Disapprove
              </button>
            </div>
            <button
                v-if="isEditableLink(item) && editingIssueId !== item.id"
                class="waves-effect waves-green btn-flat page-check-edit"
                type="button"
                v-on:click="editLink(item)">
              <i class="material-icons">edit</i>
              Edit
            </button>
            <div v-if="editingIssueId === item.id && isEditableLink(item)" class="page-check-property-editor">
              <label>
                Link URL
                <input type="text" v-model="editingLinkValue"/>
              </label>
              <div class="page-check-image-actions">
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="openLinkBrowser(item)" v-bind:disabled="isSavingLink">
                  <i class="material-icons">folder_open</i>
                  Browse
                </button>
                <button class="waves-effect waves-green btn-flat page-check-remove-link" type="button" v-on:click="removeLink(item)" v-bind:disabled="isSavingLink">
                  <i class="fa fa-chain-broken"></i>
                  Remove
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelLink" v-bind:disabled="isSavingLink">
                  Cancel
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveLink(item)" v-bind:disabled="isSavingLink">
                  Save
                </button>
              </div>
            </div>
          </li>
        </ul>
        <ul v-if="isCheckOpen(check.id) && check.id === 'valid-links' && activeTab(check.id) === 'manual-disapproved' && manualDisapprovedLinks.length" class="page-check-issues">
          <li v-for="item in manualDisapprovedLinks" v-bind:key="item.id">
            <div class="page-check-message">
              <i class="material-icons page-check-disapproved-icon">cancel</i>
              Manually disapproved
            </div>
            <div class="page-check-location">{{ item.location }}</div>
            <div v-if="item.linkText" class="page-check-link-text">Link text: {{ item.linkText }}</div>
            <div class="page-check-guidance">
              URL: <a :href="item.href" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ item.href }}</a>
            </div>
            <div class="page-check-guidance page-check-disapproved-timestamp">
              <i class="material-icons">schedule</i>
              Disapproved: {{ formatManualTimestamp(item.disapprovedAt) }}
            </div>
            <div class="page-check-manual-actions">
              <button class="waves-effect waves-green btn-flat page-check-approve" type="button" v-on:click.stop="approveManualLink(item.href)" v-bind:disabled="isSavingManualCheck">
                <i class="material-icons">check</i>
                Approve
              </button>
            </div>
            <button
                v-if="isEditableLink(item) && editingIssueId !== item.id"
                class="waves-effect waves-green btn-flat page-check-edit"
                type="button"
                v-on:click="editLink(item)">
              <i class="material-icons">edit</i>
              Edit
            </button>
            <div v-if="editingIssueId === item.id && isEditableLink(item)" class="page-check-property-editor">
              <label>
                Link URL
                <input type="text" v-model="editingLinkValue"/>
              </label>
              <div class="page-check-image-actions">
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="openLinkBrowser(item)" v-bind:disabled="isSavingLink">
                  <i class="material-icons">folder_open</i>
                  Browse
                </button>
                <button class="waves-effect waves-green btn-flat page-check-remove-link" type="button" v-on:click="removeLink(item)" v-bind:disabled="isSavingLink">
                  <i class="fa fa-chain-broken"></i>
                  Remove
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelLink" v-bind:disabled="isSavingLink">
                  Cancel
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveLink(item)" v-bind:disabled="isSavingLink">
                  Save
                </button>
              </div>
            </div>
          </li>
        </ul>
        <ul v-if="isCheckOpen(check.id) && check.id === 'valid-links' && activeTab(check.id) === 'redirect-changed' && redirectChangedLinks.length" class="page-check-issues">
          <li v-for="item in redirectChangedLinks" v-bind:key="item.id">
            <div class="page-check-message">
              <i class="material-icons page-check-redirect-changed-icon">warning</i>
              Redirect destination changed
            </div>
            <div class="page-check-location">{{ item.location }}</div>
            <div v-if="item.linkText" class="page-check-link-text">Link text: {{ item.linkText }}</div>
            <div class="page-check-guidance">
              Original URL: <a :href="item.href" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ item.href }}</a>
            </div>
            <div class="page-check-guidance page-check-redirect-old">
              <i class="material-icons">arrow_back</i>
              Old: {{ item.oldFinalUrl }}
            </div>
            <div class="page-check-guidance page-check-redirect-new">
              <i class="material-icons">arrow_forward</i>
              New: {{ item.newFinalUrl }}
            </div>
            <div class="page-check-manual-actions">
              <button class="waves-effect waves-green btn-flat page-check-approve" type="button" v-on:click.stop="approveRedirectChange(item.href)" v-bind:disabled="isSavingManualCheck">
                <i class="material-icons">check</i>
                Approve New Destination
              </button>
            </div>
            <button
                v-if="isEditableLink(item) && editingIssueId !== item.id"
                class="waves-effect waves-green btn-flat page-check-edit"
                type="button"
                v-on:click="editLink(item)">
              <i class="material-icons">edit</i>
              Edit
            </button>
            <div v-if="editingIssueId === item.id && isEditableLink(item)" class="page-check-property-editor">
              <label>
                Link URL
                <input type="text" v-model="editingLinkValue"/>
              </label>
              <div class="page-check-image-actions">
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="openLinkBrowser(item)" v-bind:disabled="isSavingLink">
                  <i class="material-icons">folder_open</i>
                  Browse
                </button>
                <button class="waves-effect waves-green btn-flat page-check-remove-link" type="button" v-on:click="removeLink(item)" v-bind:disabled="isSavingLink">
                  <i class="fa fa-chain-broken"></i>
                  Remove
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelLink" v-bind:disabled="isSavingLink">
                  Cancel
                </button>
                <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveLink(item)" v-bind:disabled="isSavingLink">
                  Save
                </button>
              </div>
            </div>
          </li>
        </ul>
        <ul v-if="visibleDetails(check).length" class="page-check-details">
          <li v-for="detail in visibleDetails(check)" v-bind:key="detail.id">
            <template v-if="detail.type === 'image-alt'">
              <img v-if="detail.imageValue" v-bind:src="imageSrc(detail.imageValue)" alt=""/>
              <div class="page-check-message">{{ detail.message }}</div>
              <div class="page-check-location">{{ detail.location }}</div>
              <div v-if="detail.linkText" class="page-check-link-text">Link text: {{ detail.linkText }}</div>
              <div class="page-check-guidance">Alt text: {{ detail.altText || 'Missing' }}</div>
              <button
                  v-if="editingIssueId !== detail.id"
                  class="waves-effect waves-green btn-flat page-check-edit"
                  type="button"
                  v-on:click="editImageAlt(detail)">
                <i class="material-icons">edit</i>
                Edit
              </button>
              <div v-if="editingIssueId === detail.id" class="page-check-image-editor">
                <label>
                  Alt text
                  <input type="text" v-model="editingAltText"/>
                </label>
                <div class="page-check-image-actions">
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelImageAlt" v-bind:disabled="isSavingAltText">
                    Cancel
                  </button>
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveImageAlt(detail)" v-bind:disabled="isSavingAltText">
                    Save
                  </button>
                </div>
              </div>
            </template>
            <template v-else-if="detail.type === 'verified-link'">
              <div class="page-check-message">{{ detail.message }}</div>
              <div class="page-check-location">{{ detail.location }}</div>
              <div v-if="detail.linkText" class="page-check-link-text">Link text: {{ detail.linkText }}</div>
              <div class="page-check-guidance">
                URL: <a :href="detail.href" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ detail.href }}</a>
              </div>
              <div class="page-check-guidance">Status: {{ detail.status }}</div>
              <button
                  v-if="isEditableLink(detail) && editingIssueId !== detail.id"
                  class="waves-effect waves-green btn-flat page-check-edit"
                  type="button"
                  v-on:click="editLink(detail)">
                <i class="material-icons">edit</i>
                Edit
              </button>
              <div v-if="editingIssueId === detail.id && isEditableLink(detail)" class="page-check-property-editor">
                <label>
                  Link URL
                  <input type="text" v-model="editingLinkValue"/>
                </label>
                <div class="page-check-image-actions">
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="openLinkBrowser(detail)" v-bind:disabled="isSavingLink">
                    <i class="material-icons">folder_open</i>
                    Browse
                  </button>
                  <button class="waves-effect waves-green btn-flat page-check-remove-link" type="button" v-on:click="removeLink(detail)" v-bind:disabled="isSavingLink">
                    <i class="fa fa-chain-broken"></i>
                    Remove
                  </button>
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelLink" v-bind:disabled="isSavingLink">
                    Cancel
                  </button>
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveLink(detail)" v-bind:disabled="isSavingLink">
                    Save
                  </button>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="page-check-message">{{ detail.message }}</div>
              <div class="page-check-location">{{ detail.location }}</div>
              <div v-if="detail.linkText" class="page-check-link-text">Link text: {{ detail.linkText }}</div>
              <div v-if="detail.type === 'html-link' || detail.type === 'link-field'" class="page-check-guidance">
                Link URL: <a :href="detail.guidance" target="_blank" rel="noopener noreferrer" class="page-check-link">{{ detail.guidance }}</a>
              </div>
              <div v-else class="page-check-guidance">Link URL: {{ detail.guidance }}</div>
              <button
                  v-if="(detail.type === 'page-property' || detail.type === 'text-field') && detail.editable && editingIssueId !== detail.id"
                  class="waves-effect waves-green btn-flat page-check-edit"
                  type="button"
                  v-on:click="editTextField(detail)">
                <i class="material-icons">edit</i>
                Edit
              </button>
              <div v-if="editingIssueId === detail.id && (detail.type === 'page-property' || detail.type === 'text-field')" class="page-check-property-editor">
                <label>
                  {{ detail.propertyLabel }}
                  <input v-if="detail.propertyKey === 'jcr:title' || detail.type === 'text-field'" type="text" v-model="editingPropertyValue"/>
                  <textarea v-else v-model="editingPropertyValue"></textarea>
                </label>
                <div class="page-check-image-actions">
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelPageProperty" v-bind:disabled="isSavingPageProperty">
                    Cancel
                  </button>
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveTextField(detail)" v-bind:disabled="isSavingPageProperty">
                    Save
                  </button>
                </div>
              </div>
              <button
                  v-if="isEditableLink(detail) && editingIssueId !== detail.id"
                  class="waves-effect waves-green btn-flat page-check-edit"
                  type="button"
                  v-on:click="editLink(detail)">
                <i class="material-icons">edit</i>
                Edit
              </button>
              <div v-if="editingIssueId === detail.id && isEditableLink(detail)" class="page-check-property-editor">
                <label>
                  {{ detail.propertyLabel }}
                  <input type="text" v-model="editingLinkValue"/>
                </label>
                <div class="page-check-image-actions">
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="openLinkBrowser(detail)" v-bind:disabled="isSavingLink">
                    <i class="material-icons">folder_open</i>
                    Browse
                  </button>
                  <button class="waves-effect waves-green btn-flat page-check-remove-link" type="button" v-on:click="removeLink(detail)" v-bind:disabled="isSavingLink">
                    <i class="fa fa-chain-broken"></i>
                    Remove
                  </button>
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="cancelLink" v-bind:disabled="isSavingLink">
                    Cancel
                  </button>
                  <button class="waves-effect waves-green btn-flat" type="button" v-on:click="saveLink(detail)" v-bind:disabled="isSavingLink">
                    Save
                  </button>
                </div>
              </div>
            </template>
          </li>
        </ul>
        <div v-if="isCheckOpen(check.id) && (!hasTabs(check) || activeTab(check.id) === 'incorrect') && !visibleIssues(check).length" class="page-check-empty-message">
          {{ emptyIncorrectMessage(check) }}
        </div>
        <div v-if="isCheckOpen(check.id) && hasTabs(check) && activeTab(check.id) === 'correct' && !visibleDetails(check).length" class="page-check-empty-message">
          {{ emptyCorrectMessage(check) }}
        </div>
      </li>
    </ul>

    <template slot="footer">
      <button class="modal-action modal-close waves-effect waves-green btn-flat" type="button" v-on:click="close">
        Close
      </button>
    </template>
    </admin-components-materializemodal>

    <path-browser
        v-if="isLinkBrowserOpen"
        :isOpen="isLinkBrowserOpen"
        :header="linkBrowserHeader"
        :browserRoot="linkBrowserRoot"
        :browserType="linkBrowserType"
        :currentPath="linkBrowserCurrentPath"
        :selectedPath="linkBrowserSelectedPath"
        :withLinkTab="true"
        :newWindow="false"
        :toggleNewWindow="noop"
        :setCurrentPath="setLinkBrowserCurrentPath"
        :setSelectedPath="setLinkBrowserSelectedPath"
        :setResourceType="noop"
        :linkTitle="''"
        :setLinkTitle="noop"
        :onCancel="closeLinkBrowser"
        @select="onLinkBrowserSelect">
    </path-browser>
  </div>
</template>

<script>
import MaterializeModal from '../materializemodal/template.vue'
import PathBrowser from '../pathbrowser/template.vue'

const CHECKS = [
  { id: 'page-title', label: 'Page title' },
  { id: 'page-description', label: 'Page description' },
  { id: 'h1-title', label: 'H1 title' },
  { id: 'image-alt', label: 'Image alt text' },
  { id: 'empty-links', label: 'No empty links' },
  { id: 'valid-links', label: 'Valid links' }
];

export default {
  components: {
    'admin-components-materializemodal': MaterializeModal,
    'path-browser': PathBrowser
  },
  props: [
    'path',
    'node',
    'modalTitle',
    'autoOpen',
    'linkVerificationResults'
  ],
  data() {
    return {
      checks: this.emptyChecks(),
      editingIssueId: '',
      editingAltText: '',
      isSavingAltText: false,
      openCheckIds: {},
      checkTabs: {},
      editingPropertyValue: '',
      isSavingPageProperty: false,
      editingLinkValue: '',
      isSavingLink: false,
      isLinkBrowserOpen: false,
      linkBrowserHeader: 'Select Link',
      linkBrowserRoot: '',
      linkBrowserType: 'Page',
      linkBrowserCurrentPath: '',
      linkBrowserSelectedPath: '',
      isSavingManualCheck: false,
      isReverifyingLinks: false,
      manualChecks: {},
      redirectChecks: {},
    };
  },
  computed: {
    issueCount() {
      return this.checks.reduce((count, check) => count + check.issues.length, 0);
    },
    hasIssues() {
      return this.issueCount > 0;
    },
    warningTotal() {
      return this.loginRedirectCount + this.redirectChangedCount;
    },
    redirectIncorrectCount() {
      return (this.linkVerificationResults || []).filter(r => r.redirect && !r.finalOk && !r.loginRedirect && !this.isRedirectResultChanged(r)).length;
    },
    redirectCorrectCount() {
      return (this.linkVerificationResults || []).filter(r => r.redirect && r.finalOk && !this.isRedirectResultChanged(r)).length;
    },
    loginRedirectCount() {
      return (this.linkVerificationResults || []).filter(r => r.redirect && r.loginRedirect && !this.manualChecks[r.href]).length;
    },
    redirectsIncorrect() {
      return (this.linkVerificationResults || []).filter(r => r.redirect && !r.finalOk && !r.loginRedirect && !this.isRedirectResultChanged(r)).map(r => ({
        id: `redirect-incorrect-${r.href}`,
        href: r.href,
        linkText: r.linkText,
        location: r.location,
        status: r.status,
        redirectUrl: r.redirectUrl,
        finalUrl: r.finalUrl,
        finalStatus: r.finalStatus,
        finalOk: r.finalOk,
        type: 'broken-link',
        owner: r.owner,
        saveOwner: r.saveOwner,
        propertyKey: r.propertyKey,
        htmlTag: r.htmlTag,
        htmlValue: r.htmlValue,
        linkType: r.linkType
      }));
    },
    loginRedirects() {
      return (this.linkVerificationResults || []).filter(r => r.redirect && r.loginRedirect && !this.manualChecks[r.href]).map(r => ({
        id: `login-redirect-${r.href}`,
        href: r.href,
        linkText: r.linkText,
        location: r.location,
        status: r.status,
        redirectUrl: r.redirectUrl,
        finalUrl: r.finalUrl,
        type: 'broken-link',
        owner: r.owner,
        saveOwner: r.saveOwner,
        propertyKey: r.propertyKey,
        htmlTag: r.htmlTag,
        htmlValue: r.htmlValue,
        linkType: r.linkType
      }));
    },
    manualApprovedCount() {
      return (this.linkVerificationResults || []).filter(r => r.redirect && r.loginRedirect && this.manualChecks[r.href] && this.manualChecks[r.href].approved).length;
    },
    manualDisapprovedCount() {
      return (this.linkVerificationResults || []).filter(r => r.redirect && r.loginRedirect && this.manualChecks[r.href] && !this.manualChecks[r.href].approved).length;
    },
    manualApprovedLinks() {
      return (this.linkVerificationResults || []).filter(r => r.redirect && r.loginRedirect && this.manualChecks[r.href] && this.manualChecks[r.href].approved).map(r => ({
        id: `manual-approved-${r.href}`,
        href: r.href,
        linkText: r.linkText,
        location: r.location,
        status: r.status,
        redirectUrl: r.redirectUrl,
        finalUrl: r.finalUrl,
        approvedAt: this.manualChecks[r.href].timestamp,
        type: 'broken-link',
        owner: r.owner,
        saveOwner: r.saveOwner,
        propertyKey: r.propertyKey,
        htmlTag: r.htmlTag,
        htmlValue: r.htmlValue,
        linkType: r.linkType
      }));
    },
    manualDisapprovedLinks() {
      return (this.linkVerificationResults || []).filter(r => r.redirect && r.loginRedirect && this.manualChecks[r.href] && !this.manualChecks[r.href].approved).map(r => ({
        id: `manual-disapproved-${r.href}`,
        href: r.href,
        linkText: r.linkText,
        location: r.location,
        disapprovedAt: this.manualChecks[r.href].timestamp,
        type: 'broken-link',
        owner: r.owner,
        saveOwner: r.saveOwner,
        propertyKey: r.propertyKey,
        htmlTag: r.htmlTag,
        htmlValue: r.htmlValue,
        linkType: r.linkType
      }));
    },
    redirectChangedCount() {
      const count = (this.linkVerificationResults || []).filter(r => {
        if (!r.redirect || !r.finalUrl) return false;
        const stored = this.redirectChecks[r.href];
        return stored && stored.finalUrl !== r.finalUrl;
      }).length;
      return count;
    },
    redirectChangedLinks() {
      return (this.linkVerificationResults || []).filter(r => this.isRedirectResultChanged(r)).map(r => ({
        id: `redirect-changed-${r.href}`,
        href: r.href,
        linkText: r.linkText,
        location: r.location,
        oldFinalUrl: this.redirectChecks[r.href]?.finalUrl || '',
        newFinalUrl: r.finalUrl,
        type: 'broken-link',
        owner: r.owner,
        saveOwner: r.saveOwner,
        propertyKey: r.propertyKey,
        htmlTag: r.htmlTag,
        htmlValue: r.htmlValue,
        linkType: r.linkType
      }));
    },
    redirectsCorrect() {
      return (this.linkVerificationResults || []).filter(r => r.redirect && r.finalOk && !this.isRedirectResultChanged(r)).map(r => ({
        id: `redirect-correct-${r.href}`,
        href: r.href,
        linkText: r.linkText,
        location: r.location,
        status: r.status,
        redirectUrl: r.redirectUrl,
        finalUrl: r.finalUrl,
        finalStatus: r.finalStatus,
        finalOk: r.finalOk,
        type: 'broken-link',
        owner: r.owner,
        saveOwner: r.saveOwner,
        propertyKey: r.propertyKey,
        htmlTag: r.htmlTag,
        htmlValue: r.htmlValue,
        linkType: r.linkType
      }));
    }
  },
  async mounted() {
    await this.loadManualChecks();
    this.runChecks();
    await this.populateLinkVerificationResults();
    if (this.autoOpen !== false) {
      this.open();
    }
  },
  watch: {
    linkVerificationResults: {
      async handler() {
        if (this.isReverifyingLinks) {
          this.isReverifyingLinks = false;
          this.runChecks();
        }
        await this.loadManualChecks();
        await this.populateLinkVerificationResults();
      },
      deep: true
    }
  },
  methods: {
    open() {
      this.$refs.materializemodal.open();
    },
    close() {
      this.$refs.materializemodal.close();
    },
    async loadManualChecks() {
      try {
        const page = this.node || {};
        const content = page['jcr:content'] || page;
        const manualStored = content.perManualLinkChecks;
        if (manualStored) {
          const parsed = typeof manualStored === 'string' ? JSON.parse(manualStored) : manualStored;
          Object.keys(parsed).forEach(k => Vue.set(this.manualChecks, k, parsed[k]));
        }
        const redirectStored = content.perRedirectChecks;
        if (redirectStored) {
          const parsed = typeof redirectStored === 'string' ? JSON.parse(redirectStored) : redirectStored;
          Object.keys(parsed).forEach(k => Vue.set(this.redirectChecks, k, parsed[k]));
        }
        const view = $perAdminApp.getView();
        const pageViewPage = view && view.pageView && view.pageView.page;
        if (pageViewPage) {
          const pvContent = pageViewPage['jcr:content'] || pageViewPage;
          const pvRedirect = pvContent.perRedirectChecks;
          if (pvRedirect) {
            const parsed = typeof pvRedirect === 'string' ? JSON.parse(pvRedirect) : pvRedirect;
            Object.keys(parsed).forEach(k => Vue.set(this.redirectChecks, k, parsed[k]));
          }
        }
        if (Object.keys(this.redirectChecks).length === 0 && view && view.pageView && view.pageView.path) {
          const pagePath = view.pageView.path;
          const self = this;
          try {
            const resp = await axios.get(pagePath + '/jcr:content.json');
            if (resp.data && resp.data.perRedirectChecks) {
              const parsed = typeof resp.data.perRedirectChecks === 'string' ? JSON.parse(resp.data.perRedirectChecks) : resp.data.perRedirectChecks;
              Object.keys(parsed).forEach(function(k) { Vue.set(self.redirectChecks, k, parsed[k]); });
            }
            if (resp.data && resp.data.perManualLinkChecks) {
              const manualParsed = typeof resp.data.perManualLinkChecks === 'string' ? JSON.parse(resp.data.perManualLinkChecks) : resp.data.perManualLinkChecks;
              Object.keys(manualParsed).forEach(function(k) { Vue.set(self.manualChecks, k, manualParsed[k]); });
            }
          } catch (e) {
            // fallback fetch failed, will proceed with empty checks
          }
        }
      } catch (e) {
        // error loading checks
      }
    },
    async saveManualChecks() {
      try {
        const view = $perAdminApp.getView();
        const pagePath = view && view.pageView ? view.pageView.path : this.path;
        const targetPath = pagePath + '/jcr:content';
        const payload = {
          perManualLinkChecks: JSON.stringify(this.manualChecks),
          perRedirectChecks: JSON.stringify(this.redirectChecks)
        };
        const formData = new FormData();
        formData.append('content', JSON.stringify(payload));
        const response = await axios.post('/perapi/admin/updateResource.json' + targetPath, formData, { withCredentials: true });
        if (response.status === 200) {
          if (view && view.pageView && view.pageView.page) {
            const pvContent = view.pageView.page['jcr:content'] || view.pageView.page;
            Vue.set(pvContent, 'perRedirectChecks', JSON.stringify(this.redirectChecks));
            Vue.set(pvContent, 'perManualLinkChecks', JSON.stringify(this.manualChecks));
          }
          if (this.node) {
            const nodeContent = this.node['jcr:content'] || this.node;
            Vue.set(nodeContent, 'perRedirectChecks', JSON.stringify(this.redirectChecks));
            Vue.set(nodeContent, 'perManualLinkChecks', JSON.stringify(this.manualChecks));
          }
        }
      } catch (e) {
        console.error('[saveManualChecks] failed:', e);
      }
    },
    approveManualLink(href) {
      this.isSavingManualCheck = true;
      Vue.set(this.manualChecks, href, { approved: true, timestamp: Date.now() });
      this.saveManualChecks().then(() => {
        this.isSavingManualCheck = false;
        this.populateLinkVerificationResults();
        this.$nextTick(() => {
          this.autoSwitchManualTab('approved');
        });
      });
    },
    disapproveManualLink(href) {
      this.isSavingManualCheck = true;
      Vue.set(this.manualChecks, href, { approved: false, timestamp: Date.now() });
      this.saveManualChecks().then(() => {
        this.isSavingManualCheck = false;
        this.populateLinkVerificationResults();
        this.$nextTick(() => {
          this.autoSwitchManualTab('disapproved');
        });
      });
    },
    approveRedirectChange(href) {
      Vue.set(this.redirectChecks, href, {
        finalUrl: this.linkVerificationResults.find(r => r.href === href)?.finalUrl || '',
        timestamp: Date.now()
      });
      this.saveManualChecks().then(() => {
        this.populateLinkVerificationResults();
        this.$nextTick(() => {
          this.autoSwitchRedirectChangedTab();
        });
      });
    },
    autoSwitchRedirectChangedTab() {
      if (this.activeTab('valid-links') === 'redirect-changed' && this.redirectChangedCount === 0) {
        if (this.redirectIncorrectCount > 0) {
          this.setCheckTab('valid-links', 'redirects-incorrect');
        } else if (this.loginRedirectCount > 0) {
          this.setCheckTab('valid-links', 'manual-test');
        } else if (this.manualDisapprovedCount > 0) {
          this.setCheckTab('valid-links', 'manual-disapproved');
        } else if (this.manualApprovedCount > 0) {
          this.setCheckTab('valid-links', 'manual-approved');
        } else if (this.redirectCorrectCount > 0) {
          this.setCheckTab('valid-links', 'redirects-correct');
        } else {
          this.setCheckTab('valid-links', 'correct');
        }
      }
    },
    autoSwitchManualTab(action) {
      const currentTab = this.activeTab('valid-links');
      if (currentTab === 'manual-test' && this.loginRedirectCount === 0) {
        this.setCheckTab('valid-links', action === 'approved' ? 'manual-approved' : 'manual-disapproved');
      } else if (currentTab === 'manual-disapproved' && this.manualDisapprovedCount === 0) {
        this.setCheckTab('valid-links', 'manual-approved');
      } else if (currentTab === 'manual-approved' && this.manualApprovedCount === 0) {
        this.setCheckTab('valid-links', 'manual-disapproved');
      }
    },
    formatManualTimestamp(ts) {
      if (!ts) return '';
      const d = new Date(ts);
      return d.toLocaleDateString() + ' ' + d.toLocaleTimeString();
    },
    isRedirectResultChanged(r) {
      if (!r.redirect || !r.finalUrl) return false;
      const stored = this.redirectChecks[r.href];
      return stored && stored.finalUrl !== r.finalUrl;
    },
    toggleCheck(checkId) {
      Vue.set(this.openCheckIds, checkId, !this.openCheckIds[checkId]);
    },
    isCheckOpen(checkId) {
      return this.openCheckIds[checkId] === true;
    },
    hasTabs(check) {
      return check.id === 'image-alt' || check.id === 'empty-links' || check.id === 'valid-links';
    },
    activeTab(checkId) {
      return this.checkTabs[checkId] || 'incorrect';
    },
    setCheckTab(checkId, tab) {
      Vue.set(this.checkTabs, checkId, tab);
    },
    correctCount(check) {
      if (this.isBinaryCheck(check)) {
        return check.issues.length ? 0 : 1;
      }
      return check.details.filter(detail => detail.correct !== false).length;
    },
    incorrectCount(check) {
      if (this.isBinaryCheck(check)) {
        return check.issues.length ? 1 : 0;
      }
      return check.issues.length;
    },
    isBinaryCheck(check) {
      return ['page-title', 'page-description', 'h1-title'].indexOf(check.id) > -1;
    },
    checkHasIssues(check) {
      return check.issues.length > 0;
    },
    checkIssueCount(check) {
      return check.issues.length;
    },
    checkLabel(check) {
      if (check.id === 'page-title' || check.id === 'page-description' || check.id === 'h1-title' || check.id === 'image-alt') {
        return check.issues.length > 0 ? `Missing ${check.label.toLowerCase()}` : check.label;
      }
      if (check.id === 'valid-links') {
        return check.issues.length > 0 ? `${check.issues.length} broken link${check.issues.length === 1 ? '' : 's'}` : check.label;
      }
      return check.label;
    },
    visibleIssues(check) {
      if (!this.isCheckOpen(check.id)) {
        return [];
      }
      if (this.hasTabs(check) && this.activeTab(check.id) !== 'incorrect') {
        return [];
      }
      return check.issues.filter(i => !i.isManualDisapprove);
    },
    visibleDetails(check) {
      if (!this.isCheckOpen(check.id)) {
        return [];
      }
      if (this.hasTabs(check)) {
        if (this.activeTab(check.id) !== 'correct') {
          return [];
        }
        return check.details.filter(detail => detail.correct !== false);
      }
      return check.details;
    },
    editTextField(issue) {
      this.editingIssueId = issue.id;
      this.editingPropertyValue = issue.value || '';
    },
    cancelPageProperty() {
      this.editingIssueId = '';
      this.editingPropertyValue = '';
    },
    saveTextField(issue) {
      if (this.isBlank(this.editingPropertyValue)) {
        $perAdminApp.toast(`Please enter ${issue.propertyLabel}.`, 'warn');
        return;
      }
      const page = this.node || {};
      const content = page['jcr:content'] || page;
      this.isSavingPageProperty = true;
      const owner = issue.owner || content;
      if (issue.htmlTag && issue.htmlValue) {
        Vue.set(owner, issue.propertyKey, this.replaceHtmlTagText(issue.htmlValue, issue.htmlTag, this.editingPropertyValue));
      } else {
        Vue.set(owner, issue.propertyKey, this.editingPropertyValue);
      }
      const view = $perAdminApp.getView();
      const pagePath = view && view.pageView ? view.pageView.path : this.path;
      const nodeToSave = issue.saveOwner || owner;
      $perAdminApp.getApi().savePageEdit(pagePath, nodeToSave)
        .then(() => {
          $perAdminApp.toast(`${issue.propertyLabel} saved.`, 'success');
          this.cancelPageProperty();
          this.runChecks();
          if ($perAdminApp.getApi().populatePageView) {
            $perAdminApp.getApi().populatePageView(pagePath);
          }
        }).catch(() => {
          $perAdminApp.toast(`Unable to save ${issue.propertyLabel}.`, 'error');
        }).then(() => {
          this.isSavingPageProperty = false;
        });
    },
    editImageAlt(issue) {
      this.editingIssueId = issue.id;
      this.editingAltText = issue.altText || '';
    },
    cancelImageAlt() {
      this.editingIssueId = '';
      this.editingAltText = '';
    },
    saveImageAlt(issue) {
      if (this.isBlank(this.editingAltText)) {
        $perAdminApp.toast('Please enter alt text for the image.', 'warn');
        return;
      }
      if (!issue.owner || !issue.altKey) {
        $perAdminApp.toast('Unable to save alt text for this image.', 'error');
        return;
      }

      this.isSavingAltText = true;
      Vue.set(issue.owner, issue.altKey, this.editingAltText);
      const view = $perAdminApp.getView();
      const pagePath = view && view.pageView ? view.pageView.path : this.path;
      const nodeToSave = issue.saveOwner || issue.owner;

      $perAdminApp.getApi().savePageEdit(pagePath, nodeToSave)
        .then(() => {
          $perAdminApp.toast('Alt text saved.', 'success');
          this.cancelImageAlt();
          this.runChecks();
          this.populateLinkVerificationResults();
          this.emitSummary();
          if ($perAdminApp.getApi().populatePageView) {
            $perAdminApp.getApi().populatePageView(pagePath);
          }
        }).catch(() => {
          $perAdminApp.toast('Unable to save alt text.', 'error');
        }).then(() => {
          this.isSavingAltText = false;
        });
    },
    editLink(issue) {
      this.editingIssueId = issue.id;
      this.editingLinkValue = issue.value || issue.href || '';
    },
    cancelLink() {
      this.editingIssueId = '';
      this.editingLinkValue = '';
      this.closeLinkBrowser();
    },
    openLinkBrowser(issue) {
      this.linkBrowserHeader = 'Select Link';
      this.linkBrowserRoot = this.getPagesRoot();
      this.linkBrowserType = this.linkBrowserTypeForIssue(issue);
      const linkValue = issue.value || issue.href || '';
      this.linkBrowserCurrentPath = this.linkBrowserPathForValue(linkValue);
      this.linkBrowserSelectedPath = this.isEmptyHref(linkValue) ? '' : linkValue;
      const browsePath = this.linkBrowserCurrentPath || this.linkBrowserRoot;
      $perAdminApp.getApi()
        .populateNodesForBrowser(browsePath, 'pathBrowser')
        .then(() => {
          this.isLinkBrowserOpen = true;
        })
        .catch(() => {
          $perAdminApp.getApi().populateNodesForBrowser(this.linkBrowserRoot || '/content', 'pathBrowser')
            .then(() => {
              this.isLinkBrowserOpen = true;
            });
        });
    },
    closeLinkBrowser() {
      this.isLinkBrowserOpen = false;
    },
    setLinkBrowserCurrentPath(path) {
      this.linkBrowserCurrentPath = path;
    },
    setLinkBrowserSelectedPath(path) {
      this.linkBrowserSelectedPath = path;
      this.editingLinkValue = path || '';
    },
    onLinkBrowserSelect(path) {
      this.editingLinkValue = path || '';
      this.closeLinkBrowser();
    },
    removeLink(issue) {
      if (!issue || !issue.owner || !issue.propertyKey) {
        $perAdminApp.toast('Unable to remove this link.', 'error');
        return;
      }
      this.editingIssueId = issue.id;
      this.isSavingLink = true;

      const isHtmlLink = issue.type === 'html-link' || issue.linkType === 'html-link';
      if (isHtmlLink) {
        const nextHtml = this.removeHtmlLink(issue.htmlValue, issue.htmlTag);
        Vue.set(issue.owner, issue.propertyKey, nextHtml);
      } else {
        Vue.set(issue.owner, issue.propertyKey, '');
      }

      const view = $perAdminApp.getView();
      const pagePath = view && view.pageView ? view.pageView.path : this.path;
      const nodeToSave = issue.saveOwner || issue.owner;

      $perAdminApp.getApi().savePageEdit(pagePath, nodeToSave)
        .then(() => {
          $perAdminApp.toast('Link removed.', 'success');
          this.cancelLink();
          if ($perAdminApp.getApi().populatePageView) {
            $perAdminApp.getApi().populatePageView(pagePath);
          }
          this.isReverifyingLinks = true;
          this.$emit('reverify-links');
        }).catch(() => {
          $perAdminApp.toast('Unable to remove this link.', 'error');
        }).then(() => {
          this.isSavingLink = false;
        });
    },
    saveLink(issue) {
      if (this.isEmptyHref(this.editingLinkValue)) {
        $perAdminApp.toast(`Please enter a valid URL.`, 'warn');
        return;
      }
      if (!issue.owner || !issue.propertyKey) {
        $perAdminApp.toast('Unable to save this link.', 'error');
        return;
      }

      this.isSavingLink = true;
      const isHtmlLink = issue.type === 'html-link' || issue.linkType === 'html-link';
      if (isHtmlLink) {
        const nextHtml = this.updateHtmlLinkHref(issue.htmlValue, issue.htmlTag, this.editingLinkValue);
        Vue.set(issue.owner, issue.propertyKey, nextHtml);
      } else {
        Vue.set(issue.owner, issue.propertyKey, this.editingLinkValue);
      }
      const view = $perAdminApp.getView();
      const pagePath = view && view.pageView ? view.pageView.path : this.path;
      const nodeToSave = issue.saveOwner || issue.owner;

      $perAdminApp.getApi().savePageEdit(pagePath, nodeToSave)
        .then(() => {
          $perAdminApp.toast('Link saved.', 'success');
          this.cancelLink();
          if ($perAdminApp.getApi().populatePageView) {
            $perAdminApp.getApi().populatePageView(pagePath);
          }
          this.isReverifyingLinks = true;
          this.$emit('reverify-links');
        }).catch(() => {
          $perAdminApp.toast('Unable to save this link.', 'error');
        }).then(() => {
          this.isSavingLink = false;
        });
    },
    isEditableLink(item) {
      if (!item || !item.owner || !item.propertyKey) {
        return false;
      }
      if (item.type === 'link-field' || item.type === 'html-link') {
        return true;
      }
      if (item.type === 'broken-link' || item.type === 'verified-link') {
        return item.linkType === 'link-field' || item.linkType === 'html-link';
      }
      return false;
    },
    linkBrowserTypeForIssue() {
      return 'Page';
    },
    getPagesRoot() {
      const view = $perAdminApp.getView() || {};
      const tenant = (view.state && view.state.tenant) || { name: 'example' };
      return `/content/${tenant.name}/pages`;
    },
    linkBrowserPathForValue(value) {
      const href = String(value || '');
      if (!href) {
        return this.getPagesRoot();
      }
      if (/^https?:\/\//i.test(href)) {
        return this.getPagesRoot();
      }
      let path = href.replace(/\.html$/, '');
      if (!path.startsWith('/content/')) {
        path = `${this.getPagesRoot()}${path.startsWith('/') ? '' : '/'}${path}`;
      }
      const lastSlash = path.lastIndexOf('/');
      return lastSlash > 0 ? path.substring(0, lastSlash) : this.getPagesRoot();
    },
    imageSrc(value) {
      if (!value) {
        return '';
      }
      return String(value);
    },
    emptyChecks() {
      return CHECKS.map(check => ({
        id: check.id,
        label: check.label,
        issues: [],
        details: [],
        hint: ''
      }));
    },
    addIssue(checkId, message, location, guidance, meta) {
      const check = this.checks.find(item => item.id === checkId);
      if (!check) {
        return;
      }
      const issue = {
        id: `${checkId}-${check.issues.length}`,
        message,
        location: location || this.path,
        guidance
      };
      if (meta) {
        Object.keys(meta).forEach(key => {
          issue[key] = meta[key];
        });
      }
      check.issues.push(issue);
    },
    addDetail(checkId, message, location, guidance, meta) {
      const check = this.checks.find(item => item.id === checkId);
      if (!check) {
        return;
      }
      const detail = {
        id: `${checkId}-detail-${check.details.length}`,
        message,
        location: location || this.path,
        guidance
      };
      if (meta) {
        Object.keys(meta).forEach(key => {
          detail[key] = meta[key];
        });
      }
      check.details.push(detail);
    },
    runChecks() {
      this.checks = this.emptyChecks();
      const page = this.node || {};
      const content = page['jcr:content'] || page;
      const pageTitle = this.firstValue([page, content], ['jcr:title', 'title']);
      const pageDescription = this.firstValue([page, content], ['description']);

      if (this.isBlank(pageTitle)) {
        this.addIssue(
            'page-title',
            'The page title is empty.',
            this.path,
            'The title is used in browser tabs, search results, and shared links. Use a short, clear title that identifies the page.',
            { type: 'page-property', owner: content, saveOwner: content, propertyKey: 'jcr:title', propertyLabel: 'Page title', value: pageTitle }
        );
      } else {
        this.addDetail(
            'page-title',
            'Page title',
            this.path,
            pageTitle,
            { type: 'page-property', editable: true, correct: true, owner: content, saveOwner: content, propertyKey: 'jcr:title', propertyLabel: 'Page title', value: pageTitle }
        );
      }

      if (this.isBlank(pageDescription)) {
        this.addIssue(
            'page-description',
            'The page description is empty.',
            this.path,
            'The description helps search engines and link previews explain the page. Summarize the page in one or two concise sentences.',
            { type: 'page-property', owner: content, saveOwner: content, propertyKey: 'description', propertyLabel: 'Page description', value: pageDescription }
        );
      } else {
        this.addDetail(
            'page-description',
            'Page description',
            this.path,
            pageDescription,
            { type: 'page-property', editable: true, correct: true, owner: content, saveOwner: content, propertyKey: 'description', propertyLabel: 'Page description', value: pageDescription }
        );
      }

      const records = [];
      this.collectRecords(content, this.path, records, [], content);
      this.checkH1(records);
      this.checkImages(records);
      this.checkLinks(records);
    },
    async populateLinkVerificationResults() {
      const validLinksCheck = this.checks.find(c => c.id === 'valid-links');
      if (validLinksCheck) {
        validLinksCheck.issues = [];
        validLinksCheck.details = [];
      }
      const results = this.linkVerificationResults || [];
      results.forEach(r => {
        if (r.redirect) {
          return;
        }
        if (r.spaRedirect) {
          this.addIssue(
              'valid-links',
              `SPA detail page redirects client-side to 404: ${r.href}`,
              r.location,
              'Page requires client-side routing to work',
              {
                type: 'broken-link',
                href: r.href,
                linkText: r.linkText,
                status: r.status,
                spaRedirect: true,
                owner: r.owner,
                saveOwner: r.saveOwner,
                propertyKey: r.propertyKey,
                htmlTag: r.htmlTag,
                htmlValue: r.htmlValue,
                linkType: r.linkType,
                correct: false
              }
          );
          return;
        }
        if (!r.ok) {
          this.addIssue(
              'valid-links',
              r.status === 401
                  ? `Link requires authentication: ${r.href}`
                  : r.error
                      ? `Link error: ${r.href}`
                      : `Broken link (${r.status || 'unknown'}): ${r.href}`,
              r.location,
              r.error || `Server returned status ${r.status}`,
              {
                type: 'broken-link',
                href: r.href,
                linkText: r.linkText,
                status: r.status,
                error: r.error,
                owner: r.owner,
                saveOwner: r.saveOwner,
                propertyKey: r.propertyKey,
                htmlTag: r.htmlTag,
                htmlValue: r.htmlValue,
                linkType: r.linkType,
                correct: false
              }
          );
        } else {
          this.addDetail(
              'valid-links',
              r.linkText ? `Link: ${r.linkText}` : `Link verified`,
              r.location,
              `Status: ${r.status}`,
              {
                type: 'verified-link',
                href: r.href,
                linkText: r.linkText,
                status: r.status,
                correct: true,
                owner: r.owner,
                saveOwner: r.saveOwner,
                propertyKey: r.propertyKey,
                htmlTag: r.htmlTag,
                htmlValue: r.htmlValue,
                linkType: r.linkType
              }
          );
        }
      });
      this.redirectsIncorrect.forEach(r => {
        this.addIssue(
            'valid-links',
            `Redirects to broken page: ${r.href}`,
            r.location,
            `Redirects to: ${r.finalUrl || r.redirectUrl}`,
            {
              type: 'broken-link',
              href: r.href,
              linkText: r.linkText,
              status: r.status,
              redirectUrl: r.redirectUrl,
              finalUrl: r.finalUrl,
              finalStatus: r.finalStatus,
              isRedirect: true,
              owner: r.owner,
              saveOwner: r.saveOwner,
              propertyKey: r.propertyKey,
              htmlTag: r.htmlTag,
              htmlValue: r.htmlValue,
              linkType: r.linkType,
              correct: false
            }
        );
      });
      this.manualDisapprovedLinks.forEach(r => {
        this.addIssue(
            'valid-links',
            `Manually disapproved: ${r.href}`,
            r.location,
            `Disapproved: ${this.formatManualTimestamp(r.disapprovedAt)}`,
            {
              type: 'broken-link',
              href: r.href,
              linkText: r.linkText,
              isManualDisapprove: true,
              owner: r.owner,
              saveOwner: r.saveOwner,
              propertyKey: r.propertyKey,
              htmlTag: r.htmlTag,
              htmlValue: r.htmlValue,
              linkType: r.linkType,
              correct: false
            }
        );
      });
      let hasNewRedirects = false;
      (this.linkVerificationResults || []).forEach(r => {
        if (r.redirect && r.finalUrl && !this.redirectChecks[r.href]) {
          Vue.set(this.redirectChecks, r.href, {
            finalUrl: r.finalUrl,
            timestamp: Date.now()
          });
          hasNewRedirects = true;
        }
      });
      this.emitSummary();
      if (hasNewRedirects) {
        await this.saveManualChecks();
      }
      const totalRedirects = this.redirectIncorrectCount + this.redirectCorrectCount + this.loginRedirectCount;
      const hints = [];
      if (totalRedirects > 0) {
        hints.push(`${totalRedirects} redirect${totalRedirects === 1 ? '' : 's'} found`);
        if (this.loginRedirectCount > 0) {
          hints.push(`${this.loginRedirectCount} require${this.loginRedirectCount === 1 ? 's' : '' } manual verification`);
        }
        hints.push('Redirects can change at any time and may break links.');
      }
      if (this.redirectChangedCount > 0) {
        hints.push(`${this.redirectChangedCount} redirect destination${this.redirectChangedCount === 1 ? ' has' : 's have'} changed. Review and approve new destinations.`);
      }
      if (this.manualApprovedCount > 0 || this.manualDisapprovedCount > 0) {
        const parts = [];
        if (this.manualApprovedCount > 0) parts.push(`${this.manualApprovedCount} approved`);
        if (this.manualDisapprovedCount > 0) parts.push(`${this.manualDisapprovedCount} disapproved`);
        hints.push(`Manual verification: ${parts.join(', ')}. Re-check periodically.`);
      }
      if (hints.length) {
        validLinksCheck.hint = hints.join(' ');
      }
      this.autoSwitchToCorrectIfEmpty();
    },
    autoSwitchToCorrectIfEmpty() {
      this.checks.forEach(check => {
        if (this.hasTabs(check) && this.activeTab(check.id) === 'incorrect') {
          const incorrect = this.incorrectCount(check);
          const redirectIncorrect = check.id === 'valid-links' ? this.redirectIncorrectCount : 0;
          const manualDisapproved = check.id === 'valid-links' ? this.manualDisapprovedCount : 0;
          if (incorrect - redirectIncorrect - manualDisapproved === 0) {
            this.setCheckTab(check.id, 'correct');
          }
        }
      });
    },
    emitSummary() {
      this.$emit('summary', {
        issueCount: this.issueCount,
        hasIssues: this.hasIssues,
        warningCount: this.loginRedirectCount + this.redirectChangedCount
      });
    },
    checkH1(records) {
      const h1Records = [];
      const seen = {};
      const addH1 = record => {
        const meta = this.h1Meta(record);
        const key = meta.location + '/' + meta.propertyKey + '/' + meta.value;
        if (seen[key] || this.isBlank(meta.value)) {
          return;
        }
        seen[key] = true;
        h1Records.push(meta);
      };

      records.forEach(record => {
        if (typeof record.value === 'string' && this.containsNonEmptyTag(record.value, 'h1')) {
          this.findHtmlTags(record.value, 'h1').forEach(tag => {
            const text = tag.replace(/<[^>]*>/g, '').trim();
            addH1({
              key: record.key,
              value: text,
              owner: record.owner,
              saveOwner: record.saveOwner,
              path: record.path,
              htmlSource: true,
              htmlTag: tag,
              htmlValue: record.value
            });
          });
        }
        if (record.key === 'htmlelement' && String(record.value).toLowerCase() === 'h1') {
          const node = record.owner || {};
          if (!this.isBlank(this.firstValue([node], ['title', 'headline', 'text', 'jcr:title']))) {
            addH1(record);
          }
        }
        if (record.key === 'element' && String(record.value).toLowerCase() === 'h1') {
          const node = record.owner || {};
          if (!this.isBlank(this.firstValue([node], ['title', 'headline', 'text', 'jcr:title']))) {
            addH1(record);
          }
        }
        if (this.isThemeTeaserH1(record.owner)) {
          addH1({
            key: 'title',
            value: record.owner.title,
            owner: record.owner,
            saveOwner: record.saveOwner,
            path: record.path
          });
        }
        if (this.isCarouselSlideH1(record)) {
          addH1(record);
        }
      });

      h1Records.forEach((h1, index) => {
        this.addDetail(
            'h1-title',
            `H1 title ${index + 1}`,
            h1.location,
            h1.value,
            {
              type: 'text-field',
              correct: true,
              editable: h1.editable,
              owner: h1.owner,
              saveOwner: h1.saveOwner,
              propertyKey: h1.propertyKey,
              propertyLabel: 'H1 title',
              value: h1.value,
              htmlTag: h1.htmlTag,
              htmlValue: h1.htmlValue
            }
        );
      });

      if (h1Records.length === 0) {
        this.addIssue(
            'h1-title',
            'No h1 title was found on the page.',
            this.path,
            'Add a visible h1 heading to the page, or set a heading component to render as h1.'
        );
      } else if (h1Records.length > 1) {
        const check = this.checks.find(item => item.id === 'h1-title');
        if (check) {
          check.hint = `${h1Records.length} Heading 1 (h1) titles were found. Avoid overusing h1 titles; one primary h1 is recommended.`;
        }
      }
    },
    checkImages(records) {
      const seen = {};
      records.forEach(record => {
        if (typeof record.value === 'string') {
          this.findHtmlTags(record.value, 'img').forEach(tag => {
            const alt = this.getHtmlAttribute(tag, 'alt');
            if (this.isBlank(alt)) {
              this.addIssue(
                  'image-alt',
                  'An image tag has no alt text.',
                  `${record.path}/${record.key}`,
                  'Edit the image or rich text content and provide meaningful alt text.'
              );
            }
          });
        }

        if (!this.looksLikeImageField(record)) {
          return;
        }

        const imageKey = `${record.path}/${record.key}`;
        if (seen[imageKey]) {
          return;
        }
        seen[imageKey] = true;

        const alt = this.findAltTextInfo(record.owner, record.key);
        const imageMeta = {
          type: 'image-alt',
          owner: record.owner,
          saveOwner: record.saveOwner,
          imageKey: record.key,
          imageValue: record.value,
          altKey: alt.key,
          altText: alt.value,
          correct: !this.isBlank(alt.value)
        };
        this.addDetail(
            'image-alt',
            `Image field "${record.key}"`,
            imageKey,
            alt.value ? `Alt text: ${alt.value}` : 'Alt text is missing.',
            imageMeta
        );
        if (this.isBlank(alt.value)) {
          this.addIssue(
              'image-alt',
              `Image field "${record.key}" has no alt text.`,
              imageKey,
              'Add alt text for this image.',
              imageMeta
          );
        }
      });
    },
    checkLinks(records) {
      const seen = {};
      records.forEach(record => {
        if (typeof record.value === 'string') {
            this.findHtmlTags(record.value, 'a').forEach(tag => {
              const href = this.getHtmlAttribute(tag, 'href');
              const location = `${record.path}/${record.key}`;
              const linkText = this.linkTextFromHtml(tag);
              if (this.isEmptyHref(href)) {
                this.addIssue(
                    'empty-links',
                    'A link has an empty href.',
                    location,
                    'Provide a valid destination, or remove the link.',
                    this.htmlLinkMeta(record, tag, href, linkText)
                );
              } else {
                this.addDetail(
                    'empty-links',
                    'Link found',
                    location,
                    href,
                    this.htmlLinkMeta(record, tag, href, linkText)
                );
              }
            });
        }

        if (record.key === 'htmlelement' && String(record.value).toLowerCase() === 'a') {
          const linkKey = this.findLinkKey(record.owner || {});
          const href = (record.owner || {})[linkKey];
          const linkMeta = this.linkFieldMeta(record, linkKey, href);
          if (this.isEmptyHref(href)) {
            this.addIssue(
                'empty-links',
                'A component is configured as a link without a destination.',
                record.path,
                'Provide a valid destination, or change the HTML element.',
                linkMeta
            );
          } else {
            this.addDetail(
                'empty-links',
                'Component link found',
                record.path,
                href,
                linkMeta
            );
          }
        }

        if (this.looksLikeRequiredLinkField(record)) {
          const key = `${record.path}/${record.key}`;
          if (seen[key]) {
            return;
          }
          seen[key] = true;
          const linkMeta = this.linkFieldMeta(record, record.key, record.value);
          if (this.isEmptyHref(record.value)) {
            this.addIssue(
                'empty-links',
                `Link field "${record.key}" is empty.`,
                key,
                'Provide a valid destination, or remove the linked item.',
                linkMeta
            );
          } else {
            this.addDetail(
                'empty-links',
                `Link field "${record.key}"`,
                key,
                String(record.value),
                linkMeta
            );
          }
        }
      });
    },
    collectRecords(value, path, records, ancestors, saveOwner) {
      if (value === null || value === undefined) {
        return;
      }
      if (ancestors.indexOf(value) > -1) {
        return;
      }
      if (Array.isArray(value)) {
        value.forEach((item, index) => {
          this.collectRecords(item, `${path}[${index}]`, records, ancestors, saveOwner);
        });
        return;
      }
      if (typeof value !== 'object') {
        return;
      }

      const nextAncestors = ancestors.slice();
      nextAncestors.push(value);
      const currentSaveOwner = value.path ? value : saveOwner;
      Object.keys(value).forEach(key => {
        const item = value[key];
        if (typeof item === 'string' || typeof item === 'number' || typeof item === 'boolean') {
          records.push({
            key,
            value: item,
            owner: value,
            saveOwner: currentSaveOwner,
            path
          });
        } else {
          const childPath = item && item.path ? item.path : `${path}/${key}`;
          this.collectRecords(item, childPath, records, nextAncestors, currentSaveOwner);
        }
      });
    },
    firstValue(objects, keys) {
      for (let i = 0; i < objects.length; i++) {
        const object = objects[i] || {};
        for (let j = 0; j < keys.length; j++) {
          if (object[keys[j]] !== undefined && object[keys[j]] !== null) {
            return object[keys[j]];
          }
        }
      }
      return '';
    },
    isBlank(value) {
      return value === undefined || value === null || String(value).replace(/<[^>]*>/g, '').trim() === '';
    },
    h1Meta(record) {
      const owner = record.owner || {};
      let propertyKey = record.key;
      let value = record.value;
      let editable = true;

      if (record.htmlSource) {
        propertyKey = record.key;
        editable = true;
      } else if (record.key === 'htmlelement' || record.key === 'element') {
        propertyKey = this.firstExistingKey(owner, ['title', 'headline', 'text', 'jcr:title']);
        value = propertyKey ? owner[propertyKey] : '';
      } else if (this.isThemeTeaserH1(owner)) {
        propertyKey = 'title';
        value = owner.title;
      }

      return {
        owner,
        saveOwner: record.saveOwner || owner,
        propertyKey,
        value: String(value || '').replace(/<[^>]*>/g, '').trim(),
        editable: editable && !!propertyKey,
        location: record.path || this.path,
        htmlTag: record.htmlTag,
        htmlValue: record.htmlValue
      };
    },
    replaceHtmlTagText(html, tag, text) {
      const source = String(html || '');
      const escapedText = this.escapeHtml(text);
      const nextTag = String(tag || '').replace(/>([\s\S]*)<\/([a-z0-9]+)>$/i, `>${escapedText}</$2>`);
      return nextTag ? source.replace(tag, nextTag) : source;
    },
    escapeHtml(value) {
      return String(value || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
    },
    firstExistingKey(object, keys) {
      for (let i = 0; i < keys.length; i++) {
        if (object && object[keys[i]] !== undefined && object[keys[i]] !== null) {
          return keys[i];
        }
      }
      return '';
    },
    containsNonEmptyTag(value, tagName) {
      const tags = this.findHtmlTags(value, tagName);
      for (let i = 0; i < tags.length; i++) {
        const text = tags[i].replace(/<[^>]*>/g, '').trim();
        if (text !== '') {
          return true;
        }
      }
      return false;
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
    looksLikeImageField(record) {
      if (this.isBlank(record.value)) {
        return false;
      }
      const key = record.key.toLowerCase();
      if (key.indexOf('alt') > -1 || key.indexOf('title') > -1) {
        return false;
      }
      const value = String(record.value);
      const imageKeys = ['image', 'img', 'logo', 'icon', 'src', 'filereference', 'imagesrc', 'imagepath'];
      const imageKey = imageKeys.indexOf(key) > -1;
      const imageValue = /\/content\/.*\/assets\//.test(value)
          || /^https?:\/\//i.test(value)
          || /\.(png|jpe?g|gif|svg|webp)(\?.*)?$/i.test(value);
      return imageKey && imageValue;
    },
    isThemeTeaserH1(owner) {
      if (!owner) {
        return false;
      }
      const component = this.getComponentName(owner);
      const isTeaser = component.indexOf('teaserhorizontal') > -1 || component.indexOf('teaservertical') > -1;
      return isTeaser && owner.showtitle === 'true' && !this.isBlank(owner.title);
    },
    isCarouselSlideH1(record) {
      return record.key === 'title'
          && record.path.indexOf('/slides[') > -1
          && !this.isBlank(record.value);
    },
    getComponentName(owner) {
      return String(
          owner.component
          || owner['sling:resourceType']
          || owner.resourceType
          || ''
      ).toLowerCase();
    },
    looksLikeRequiredLinkField(record) {
      const key = record.key.toLowerCase();
      const linkKeys = ['href', 'url', 'link', 'buttonlink', 'logourl', 'slidelink'];
      if (linkKeys.indexOf(key) === -1) {
        return false;
      }

      const owner = record.owner || {};
      const component = this.getComponentName(owner);
      const anchorComponents = ['viewlink', 'listlinks', 'socialicons', 'header', 'footer'];
      for (let i = 0; i < anchorComponents.length; i++) {
        if (component.indexOf(anchorComponents[i]) > -1) {
          return true;
        }
      }

      return owner.htmlelement && String(owner.htmlelement).toLowerCase() === 'a';
    },
    findLinkKey(owner) {
      return this.firstExistingKey(owner, ['link', 'url', 'href', 'buttonlink', 'logourl', 'slidelink']) || 'link';
    },
    linkFieldMeta(record, propertyKey, value) {
      return {
        type: 'link-field',
        owner: record.owner,
        saveOwner: record.saveOwner,
        propertyKey,
        propertyLabel: 'Link',
        value,
        linkText: this.linkTextFromStructuredLink(record.owner, propertyKey),
        correct: !this.isEmptyHref(value)
      };
    },
    htmlLinkMeta(record, tag, value, linkText) {
      return {
        type: 'html-link',
        owner: record.owner,
        saveOwner: record.saveOwner,
        propertyKey: record.key,
        propertyLabel: 'Link',
        value,
        linkText: linkText || this.linkTextFromHtml(tag),
        htmlTag: tag,
        htmlValue: record.value,
        correct: !this.isEmptyHref(value)
      };
    },
    removeHtmlLink(html, tag) {
      const source = String(html || '');
      const innerText = String(tag || '').replace(/^<a\b[^>]*>/i, '').replace(/<\/a>$/i, '');
      return source.replace(tag, innerText);
    },
    linkTextFromHtml(tag) {
      return String(tag || '').replace(/<[^>]*>/g, '').trim();
    },
    linkTextFromStructuredLink(owner, propertyKey) {
      const node = owner || {};
      const candidates = [
        'buttonlabel',
        'buttontext',
        'linktext',
        'label',
        'title',
        'text',
        propertyKey
      ];
      const linkText = this.firstValue([node], candidates);
      return String(linkText || '').replace(/<[^>]*>/g, '').trim();
    },
    updateHtmlLinkHref(html, tag, href) {
      const source = String(html || '');
      const nextTag = this.replaceHtmlAttribute(tag, 'href', href);
      return source.replace(tag, nextTag);
    },
    replaceHtmlAttribute(tag, attribute, value) {
      const escapedValue = String(value).replace(/"/g, '&quot;');
      const pattern = new RegExp(`${attribute}\\s*=\\s*("([^"]*)"|'([^']*)'|([^\\s>]+))`, 'i');
      if (pattern.test(tag)) {
        return tag.replace(pattern, `${attribute}="${escapedValue}"`);
      }
      return tag.replace(/>$/, ` ${attribute}="${escapedValue}">`);
    },
    findAltTextInfo(owner, key) {
      const candidates = this.altTextCandidates(key);
      for (let i = 0; i < candidates.length; i++) {
        if (owner && owner[candidates[i]] !== undefined) {
          return {
            key: candidates[i],
            value: owner[candidates[i]]
          };
        }
      }
      return {
        key: candidates[0],
        value: ''
      };
    },
    altTextCandidates(key) {
      const map = {
        image: ['imagealttext', 'alt'],
        logo: ['logoalttext', 'alt'],
        icon: ['iconalttext', 'alt'],
        imagesrc: ['mediatitle', 'alt'],
        imagepath: ['alt']
      };
      const lowerKey = String(key).toLowerCase();
      if (map[lowerKey]) {
        return map[lowerKey];
      }
      return ['alt', 'alttext', 'altText', `${key}alttext`, `${key}AltText`, `${key}Alt`, 'imagealttext', 'logoalttext', 'iconalttext', 'mediatitle'];
    },
    isEmptyHref(href) {
      if (href === undefined || href === null) {
        return true;
      }
      const value = String(href).trim();
      return value === '' || value === '#' || value.toLowerCase() === 'javascript:void(0)';
    },
    emptyIncorrectMessage(check) {
      if (check.id === 'image-alt') return 'All images have alt text';
      if (check.id === 'empty-links') return 'No empty links found';
      if (check.id === 'valid-links') return 'No broken links found';
      return;
    },
    emptyCorrectMessage(check) {
      if (check.id === 'image-alt') return 'No images with alt text found';
      if (check.id === 'empty-links') return 'No valid links found';
      if (check.id === 'valid-links') return 'No valid links found';
      return;
    },
    noop() {
    },
  }
}
</script>

<style>
.page-check-modal .modal {
  max-height: 80%;
}
.page-check-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding: 10px 12px;
  border-left: 4px solid #f9a825;
  background: #fff8e1;
  color: rgba(0, 0, 0, 0.78);
}
.page-check-summary-ok {
  border-left-color: #43a047;
  background: #edf7ed;
}
.page-check-list,
.page-check-issues {
  margin: 0;
  padding: 0;
  list-style: none;
}
.page-check-row {
  border-top: 1px solid rgba(0, 0, 0, 0.12);
  padding: 12px 0;
}
.page-check-row-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  cursor: pointer;
}
.page-check-row-title .left .material-icons {
  color: #c62828;
  margin-right: 4px;
}
.page-check-row-title .left .page-check-icon-ok {
  color: #2e7d32;
  margin-right: 4px;
}
.page-check-row-title .left,
.page-check-row-title .right {
  display: flex;
}
.page-check-count {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 22px;
  width: 22px;
  height: 22px;
  min-width: 22px;
  min-height: 22px;
  padding: 0;
  border-radius: 50%;
  background: #c62828;
  color: #fff;
  font-size: 12px;
  line-height: 22px;
  overflow: hidden;
  text-align: center;
}
.page-check-warning-count {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 22px;
  width: 22px;
  height: 22px;
  min-width: 22px;
  min-height: 22px;
  padding: 0;
  border-radius: 50%;
  background: #f57c00;
  color: #fff;
  font-size: 12px;
  line-height: 22px;
  overflow: hidden;
  text-align: center;
  margin-left: 4px;
}
.page-check-expand {
  color: rgba(0, 0, 0, 0.48) !important;
}
.page-check-hint {
  display: flex;
  align-items: center;
  margin-right: 4px;
  color: #607d8b;
}
.page-check-hint .material-icons {
  font-size: 18px;
}
.page-check-empty-message {
  margin-top: 10px;
  padding-left: 34px;
  color: rgba(0, 0, 0, 0.48);
  font-style: italic;
  font-size: 13px;
}
.page-check-hint-message {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 10px;
  padding: 8px 10px;
  background: rgba(96, 125, 139, 0.08);
  color: rgba(0, 0, 0, 0.68);
  font-size: 12px;
  line-height: 1.4;
}
.page-check-hint-message .material-icons {
  font-size: 18px;
  color: #607d8b;
}
.page-check-redirect-warning {
  background: rgba(255, 152, 0, 0.12);
  color: rgba(0, 0, 0, 0.78);
}
.page-check-redirect-warning .material-icons {
  color: #f57c00;
}
.page-check-manual-test-banner {
  background: rgba(33, 150, 243, 0.12);
  color: rgba(0, 0, 0, 0.78);
}
.page-check-manual-test-banner .material-icons {
  color: #1976d2;
}
.page-check-manual-test-hint {
  background: rgba(33, 150, 243, 0.12);
  border-radius: 4px;
  padding: 8px 12px;
  margin-top: 8px;
}
.page-check-manual-test-hint .material-icons {
  color: #1976d2;
  font-size: 18px;
  vertical-align: middle;
  margin-right: 6px;
}
.page-check-manual-test-hint span {
  vertical-align: middle;
}
.page-check-manual-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.page-check-approve {
  display: flex;
  color: #2e7d32;
}
.page-check-approve .material-icons {
  color: #2e7d32;
}
.page-check-disapprove {
  display: flex;
  color: #c62828;
}
.page-check-disapprove .material-icons {
  color: #c62828;
}
.page-check-approved-icon {
  color: #2e7d32;
  font-size: 18px;
  vertical-align: middle;
  margin-right: 4px;
}
.page-check-approved-timestamp {
  color: #2e7d32;
  font-size: 12px;
}
.page-check-approved-timestamp .material-icons {
  font-size: 16px;
  vertical-align: middle;
  margin-right: 4px;
}
.page-check-disapproved-icon {
  color: #c62828;
  font-size: 18px;
  vertical-align: middle;
  margin-right: 4px;
}
.page-check-disapproved-timestamp {
  color: #c62828;
  font-size: 12px;
}
.page-check-disapproved-timestamp .material-icons {
  font-size: 16px;
  vertical-align: middle;
  margin-right: 4px;
}
.page-check-redirect-changed-icon {
  color: #f57c00;
  font-size: 18px;
  vertical-align: middle;
  margin-right: 4px;
}
.page-check-redirect-old {
  color: #c62828;
  font-size: 12px;
}
.page-check-redirect-new {
  color: #2e7d32;
  font-size: 12px;
}
.page-check-redirect-old .material-icons,
.page-check-redirect-new .material-icons {
  font-size: 16px;
  vertical-align: middle;
  margin-right: 4px;
}
.page-check-status-ok {
  color: #2e7d32;
  font-weight: 600;
}
.page-check-status-error {
  color: #c62828;
  font-weight: 600;
}
.page-check-tabs {
  display: flex;
  gap: 0;
  margin-top: 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.16);
}
.page-check-tabs button {
  position: relative;
  border: 0;
  background: transparent;
  padding: 9px 14px 8px;
  cursor: pointer;
  color: rgba(0, 0, 0, 0.58);
  font-weight: 600;
  line-height: 1;
}
.page-check-tabs button.active {
  color: #455a64;
}
.page-check-tabs button.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 3px;
  background: #455a64;
}
.page-check-issues {
  margin-top: 10px;
  padding-left: 34px;
}
.page-check-details {
  margin: 10px 0 0;
  padding-left: 34px;
  list-style: none;
}
.page-check-issues li,
.page-check-details li {
  margin-bottom: 10px;
}
.page-check-message {
  color: rgba(0, 0, 0, 0.82);
}
.page-check-location {
  margin-top: 2px;
  font-family: monospace;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.58);
  word-break: break-word;
}
.page-check-guidance {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.68);
}
.page-check-link {
  color: #1565c0;
  text-decoration: underline;
  cursor: pointer;
}
.page-check-link:hover {
  color: #0d47a1;
}
.page-check-edit {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  padding-left: 0;
}
.page-check-edit .material-icons {
  font-size: 18px;
}
.page-check-issues > li > img,
.page-check-details > li > img {
  display: block;
  max-width: 100%;
  max-height: 180px;
  margin-bottom: 8px;
  object-fit: contain;
}
.page-check-property-editor {
  margin-top: 10px;
  padding: 10px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  background: rgba(0, 0, 0, 0.03);
}
.page-check-property-editor textarea {
  min-height: 90px;
  background-color: white;
}
.page-check-image-editor {
  margin-top: 10px;
  padding: 10px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  background: rgba(0, 0, 0, 0.03);
}
.page-check-image-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
}
.page-check-remove-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.page-check-remove-link .material-icons {
  margin: 0;
  line-height: 1;
}
.page-check-image-actions button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.page-check-image-actions button .material-icons {
  line-height: 1;
}
</style>
