package com.peregrine.reference.impl;

import com.peregrine.SlingResourcesTest;
import com.peregrine.mock.PageContentMock;
import com.peregrine.mock.PageMock;
import com.peregrine.mock.ResourceMock;
import com.peregrine.reference.Reference;
import org.apache.sling.api.resource.Resource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.stream.Collectors;
import java.util.Set;

import static com.peregrine.commons.util.PerConstants.SLASH;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public final class ReferenceListerServiceTest extends SlingResourcesTest {

	@Mock
	private ReferenceListerService.Configuration configuration;

	private final ReferenceListerService model = new ReferenceListerService();
	private final PageMock siblingPage = new PageMock();
	private final ResourceMock siblingPageResource = setParentAndGetNewChild(siblingPage, parent);
	private final PageMock childPage = new PageMock();
	private final ResourceMock childPageResource = setParentAndGetNewChild(childPage, page);

	private static ResourceMock setParentAndGetNewChild(final PageMock page, final ResourceMock parent) {
		page.setPath(parent.getPath() + SLASH + "sibling");
		parent.addChild(page);
		final PageContentMock siblingPageContent = page.getContent();
		final String name = "resource";
		final ResourceMock resource = new ResourceMock();
		resource.setPath(siblingPageContent.getPath() + SLASH + name);
		siblingPageContent.addChild(name, resource);
		return resource;
	}

	@Before
	public void setUp() {
		when(configuration.referencedByRoot()).thenReturn(new String[] { "/content/", null, EMPTY });
		when(configuration.referencePrefix()).thenReturn(new String[] { "/content", null });
		model.activate(configuration);
	}

	@Test
	public void modified() {
		model.modified(configuration);
	}

	@Test
	public void getReferenceList() {
		resource.putProperty("reference", parent.getPath());
		siblingPageResource.putProperty("reference", page.getPath());
		childPageResource.putProperty("reference", siblingPage.getPath());
		var references = model.getReferenceList(true, page, true);
		assertTrue(references.contains(parent));
		assertTrue(references.contains(siblingPage));
		assertTrue(references.contains(page));
		references = model.getReferenceList(true, page, false);
		assertTrue(references.contains(parent));
		assertFalse(references.contains(siblingPage));
		assertFalse(references.contains(page));
		references = model.getReferenceList(false, page, true);
		assertTrue(references.contains(parent));
		assertTrue(references.contains(siblingPage));
		assertFalse(references.contains(page));
		references = model.getReferenceList(false, page, false);
		assertTrue(references.contains(parent));
		assertFalse(references.contains(siblingPage));
		assertFalse(references.contains(page));
	}

	@Test
	public void getReferencedByList() {
		assertTrue(model.getReferencedByList(null).isEmpty());
		when(resourceResolver.findResources(anyString(), anyString()))
				.thenReturn(
						resources.stream()
								.map(Resource.class::cast)
								.collect(Collectors.toList())
								.iterator()
				);
		parent.putProperty("reference", resource.getPath());
		assertFalse(model.getReferencedByList(resource).isEmpty());
	}

	@Test
	public void returnsEveryPropertyWhenOneComponentUsesTheAssetTwice() {
		when(resourceResolver.findResources(anyString(), anyString()))
				.thenReturn(java.util.Collections.<Resource>singletonList(parent).iterator());
		parent.putProperty("image", resource.getPath());
		parent.putProperty("logo", resource.getPath());

		final var references = model.getReferencedByList(resource);
		final Set<String> propertyNames = references.stream().map(Reference::getPropertyName)
				.collect(Collectors.toSet());
		assertEquals(Set.of("image", "logo"), propertyNames);
		assertEquals(2, references.size());
	}

	@Test
	public void findsSingleQuotedReferencesWithoutMatchingSimilarPaths() {
		when(resourceResolver.findResources(anyString(), anyString()))
				.thenReturn(java.util.Collections.<Resource>singletonList(parent).iterator());
		parent.putProperty("html", "<img src='" + resource.getPath() + "?width=400'>");
		parent.putProperty("other", resource.getPath().replace("/resource", "/resource-extra"));

		final var references = model.getReferencedByList(resource);
		assertEquals(1, references.size());
		assertEquals("html", references.get(0).getPropertyName());
	}

}
