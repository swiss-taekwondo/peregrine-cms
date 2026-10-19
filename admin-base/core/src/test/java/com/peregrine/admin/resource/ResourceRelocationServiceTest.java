package com.peregrine.admin.resource;

import com.peregrine.reference.Reference;
import com.peregrine.reference.ReferenceLister;
import org.junit.Test;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.wrappers.ModifiableValueMapDecorator;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResourceRelocationServiceTest {
    private static final String OLD_PATH = "/content/site/assets/photo.jpg";
    private static final String NEW_PATH = "/content/site/assets/archive/photo.jpg";

    @Test
    public void rewritesDirectAndEmbeddedReferencesWithoutDiscardingText() {
        assertEquals(NEW_PATH, ResourceRelocationService.replaceReferenceValue(OLD_PATH, OLD_PATH, NEW_PATH));
        assertEquals("<a href=\"" + NEW_PATH + "\">Photo</a>",
                ResourceRelocationService.replaceReferenceValue(
                        "<a href=\"" + OLD_PATH + "\">Photo</a>", OLD_PATH, NEW_PATH));
        assertEquals(NEW_PATH + ".html and " + NEW_PATH,
                ResourceRelocationService.replaceReferenceValue(OLD_PATH + ".html and " + OLD_PATH,
                        OLD_PATH, NEW_PATH));
        assertEquals("<img src='" + NEW_PATH + "?width=400'>",
                ResourceRelocationService.replaceReferenceValue(
                        "<img src='" + OLD_PATH + "?width=400'>", OLD_PATH, NEW_PATH));
    }

    @Test
    public void preservesStringArrayShapeAndUnrelatedValues() {
        final String[] values = {OLD_PATH, "<img src=\"" + OLD_PATH + "\">", "/content/other.jpg"};
        assertArrayEquals(new String[]{NEW_PATH, "<img src=\"" + NEW_PATH + "\">", "/content/other.jpg"},
                (String[]) ResourceRelocationService.replaceReferenceValue(values, OLD_PATH, NEW_PATH));
        final String[] unrelated = {"/content/other.jpg"};
        assertSame(unrelated, ResourceRelocationService.replaceReferenceValue(unrelated, OLD_PATH, NEW_PATH));
        assertEquals("/content/site/assets/photo.jpg-extra",
                ResourceRelocationService.replaceReferenceValue("/content/site/assets/photo.jpg-extra", OLD_PATH, NEW_PATH));
        assertEquals("/content/site/assets/photoXjpg",
                ResourceRelocationService.replaceReferenceValue("/content/site/assets/photoXjpg", OLD_PATH, NEW_PATH));
    }

    @Test
    public void moveUpdatesPropertyOnComponentWithoutJcrContent() throws Exception {
        final ResourceRelocationService service = new ResourceRelocationService();
        final ReferenceLister lister = mock(ReferenceLister.class);
        injectLister(service, lister);
        final Resource from = mock(Resource.class);
        final Resource destination = mock(Resource.class);
        final Resource moved = mock(Resource.class);
        final Resource component = mock(Resource.class);
        final ResourceResolver resolver = mock(ResourceResolver.class);
        final ModifiableValueMap properties = properties("image", OLD_PATH);
        when(from.getPath()).thenReturn(OLD_PATH);
        when(from.getResourceResolver()).thenReturn(resolver);
        when(destination.getPath()).thenReturn("/content/site/assets/archive");
        when(resolver.move(OLD_PATH, destination.getPath())).thenReturn(moved);
        when(moved.getPath()).thenReturn(NEW_PATH);
        when(component.adaptTo(ModifiableValueMap.class)).thenReturn(properties);
        when(lister.getReferencedByList(from)).thenReturn(Collections.singletonList(new Reference(component, "image", component)));

        service.moveToNewParent(from, destination, true);

        assertEquals(NEW_PATH, properties.get("image"));
    }

    @Test
    public void renameUpdatesPropertyOnParentEvenWhenItHasJcrContent() throws Exception {
        final ResourceRelocationService service = new ResourceRelocationService();
        final ReferenceLister lister = mock(ReferenceLister.class);
        injectLister(service, lister);
        final Resource from = mock(Resource.class);
        final Resource parent = mock(Resource.class);
        final Resource renamed = mock(Resource.class);
        final Resource component = mock(Resource.class);
        final Resource childContent = mock(Resource.class);
        final Node fromNode = mock(Node.class);
        final Node parentNode = mock(Node.class);
        final NodeIterator nodes = mock(NodeIterator.class);
        final Session session = mock(Session.class);
        final ModifiableValueMap properties = properties("image", OLD_PATH);
        final ModifiableValueMap childProperties = properties("image", "unchanged");
        when(from.getPath()).thenReturn(OLD_PATH);
        when(from.getName()).thenReturn("photo.jpg");
        when(from.getParent()).thenReturn(parent);
        when(from.adaptTo(Node.class)).thenReturn(fromNode);
        when(fromNode.getSession()).thenReturn(session);
        when(parent.getPath()).thenReturn("/content/site/assets");
        when(parent.adaptTo(Node.class)).thenReturn(parentNode);
        when(parentNode.getNodes()).thenReturn(nodes);
        when(parent.getChild("renamed.jpg")).thenReturn(renamed);
        when(renamed.getPath()).thenReturn("/content/site/assets/renamed.jpg");
        when(component.getChild("jcr:content")).thenReturn(childContent);
        when(component.adaptTo(ModifiableValueMap.class)).thenReturn(properties);
        when(childContent.adaptTo(ModifiableValueMap.class)).thenReturn(childProperties);
        when(lister.getReferencedByList(from)).thenReturn(Collections.singletonList(new Reference(component, "image", component)));

        service.rename(from, "renamed.jpg", true);

        assertEquals("/content/site/assets/renamed.jpg", properties.get("image"));
        assertEquals("unchanged", childProperties.get("image"));
    }

    private static ModifiableValueMap properties(final String key, final String value) {
        final Map<String, Object> backing = new HashMap<>();
        backing.put(key, value);
        return new ModifiableValueMapDecorator(backing);
    }

    private static void injectLister(final ResourceRelocationService service, final ReferenceLister lister) throws Exception {
        final Field field = ResourceRelocationService.class.getDeclaredField("referenceLister");
        field.setAccessible(true);
        field.set(service, lister);
    }
}
