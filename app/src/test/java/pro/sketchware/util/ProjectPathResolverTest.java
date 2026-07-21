package pro.sketchware.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProjectPathResolverTest {

    @Test
    public void readRootAliasesAreExplicitAndScoped() {
        assertTrue(ProjectPathResolver.isReadRootAlias(""));
        assertTrue(ProjectPathResolver.isReadRootAlias("."));
        assertTrue(ProjectPathResolver.isReadRootAlias("/"));
        assertTrue(ProjectPathResolver.isReadRootAlias("\\"));
        assertFalse(ProjectPathResolver.isReadRootAlias("/storage/emulated/0/project"));
    }

    @Test
    public void obviousModelPlaceholdersAreRejected() {
        assertTrue(ProjectPathResolver.isPlaceholderPath("<uri"));
        assertTrue(ProjectPathResolver.isPlaceholderPath("<uri>"));
        assertTrue(ProjectPathResolver.isPlaceholderPath("{{project_path}}"));
        assertTrue(ProjectPathResolver.isPlaceholderPath("${workspace}"));
        assertTrue(ProjectPathResolver.isPlaceholderPath("undefined"));
        assertFalse(ProjectPathResolver.isPlaceholderPath("data/123/file.xml"));
    }

    @Test
    public void parentTraversalSegmentsAreRejectedIncludingFinalSegment() {
        assertTrue(ProjectPathResolver.hasParentTraversal("sub/.."));
        assertTrue(ProjectPathResolver.hasParentTraversal("data/123/sub/../file.xml"));
        assertTrue(ProjectPathResolver.hasParentTraversal("sub\\.."));
        assertFalse(ProjectPathResolver.hasParentTraversal("data/123/file..xml"));
    }
}
