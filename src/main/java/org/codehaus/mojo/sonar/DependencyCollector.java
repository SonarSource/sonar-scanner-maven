package org.codehaus.mojo.sonar;

import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;
import org.apache.maven.shared.dependency.tree.filter.AncestorOrSelfDependencyNodeFilter;
import org.apache.maven.shared.dependency.tree.filter.DependencyNodeFilter;
import org.apache.maven.shared.dependency.tree.filter.StateDependencyNodeFilter;
import org.apache.maven.shared.dependency.tree.traversal.BuildingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.tree.traversal.CollectingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.tree.traversal.DependencyNodeVisitor;
import org.apache.maven.shared.dependency.tree.traversal.FilteringDependencyNodeVisitor;

import java.util.*;

public class DependencyCollector
{

    private final DependencyTreeBuilder dependencyTreeBuilder;

    private final ArtifactFactory artifactFactory;

    private final ArtifactRepository localRepository;

    private final ArtifactMetadataSource artifactMetadataSource;

    private final ArtifactCollector artifactCollector;

    public DependencyCollector( DependencyTreeBuilder dependencyTreeBuilder, ArtifactFactory artifactFactory,
                                ArtifactRepository localRepository, ArtifactMetadataSource artifactMetadataSource,
                                ArtifactCollector artifactCollector )
    {
        this.dependencyTreeBuilder = dependencyTreeBuilder;
        this.artifactFactory = artifactFactory;
        this.localRepository = localRepository;
        this.artifactMetadataSource = artifactMetadataSource;
        this.artifactCollector = artifactCollector;
    }

    private static class Dependency
    {

        private final String key;

        private final String version;

        private String scope;

        List<Dependency> dependencies = new ArrayList<Dependency>();

        public Dependency( String key, String version )
        {
            this.key = key;
            this.version = version;
        }

        public String key()
        {
            return key;
        }

        public String version()
        {
            return version;
        }

        public String scope()
        {
            return scope;
        }

        public Dependency setScope( String scope )
        {
            this.scope = scope;
            return this;
        }

        public List<Dependency> dependencies()
        {
            return dependencies;
        }
    }

    private List<Dependency> collectProjectDependencies( MavenProject project )
    {
        final List<Dependency> result = new ArrayList<Dependency>();
        try
        {
            DependencyNode root =
                dependencyTreeBuilder.buildDependencyTree( project, localRepository, artifactFactory,
                                                           artifactMetadataSource, null, artifactCollector );

            DependencyNodeVisitor visitor = new BuildingDependencyNodeVisitor( new DependencyNodeVisitor()
            {

                private Deque<Dependency> stack = new ArrayDeque<Dependency>();

                @Override
                public boolean visit( DependencyNode node )
                {
                    if ( node.getParent() != null && node.getParent() != node )
                    {
                        Dependency dependency = toDependency( node );
                        if ( stack.isEmpty() )
                        {
                            result.add( dependency );
                        }
                        else
                        {
                            stack.peek().dependencies().add( dependency );
                        }
                        stack.push( dependency );
                    }
                    return true;
                }

                @Override
                public boolean endVisit( DependencyNode node )
                {
                    if ( !stack.isEmpty() )
                    {
                        stack.pop();
                    }
                    return true;
                }
            } );

            // mode verbose OFF : do not show the same lib many times
            DependencyNodeFilter filter = StateDependencyNodeFilter.INCLUDED;

            CollectingDependencyNodeVisitor collectingVisitor = new CollectingDependencyNodeVisitor();
            DependencyNodeVisitor firstPassVisitor = new FilteringDependencyNodeVisitor( collectingVisitor, filter );
            root.accept( firstPassVisitor );

            DependencyNodeFilter secondPassFilter =
                new AncestorOrSelfDependencyNodeFilter( collectingVisitor.getNodes() );
            visitor = new FilteringDependencyNodeVisitor( visitor, secondPassFilter );

            root.accept( visitor );

        }
        catch ( DependencyTreeBuilderException e )
        {
            throw new IllegalStateException( "Can not load the graph of dependencies of the project " + project, e );
        }
        return result;
    }

    private Dependency toDependency( DependencyNode node )
    {
        String key = String.format( "%s:%s", node.getArtifact().getGroupId(), node.getArtifact().getArtifactId() );
        String version = node.getArtifact().getBaseVersion();
        return new Dependency( key, version ).setScope( node.getArtifact().getScope() );
    }

    public String toJson( MavenProject project )
    {
        return dependenciesToJson( collectProjectDependencies( project ) );
    }

    private String dependenciesToJson( List<Dependency> deps )
    {
        StringBuilder json = new StringBuilder();
        json.append( '[' );
        serializeDeps( json, deps );
        json.append( ']' );
        return json.toString();
    }

    private void serializeDeps( StringBuilder json, List<Dependency> deps )
    {
        for ( Iterator<Dependency> dependencyIt = deps.iterator(); dependencyIt.hasNext(); )
        {
            serializeDep( json, dependencyIt.next() );
            if ( dependencyIt.hasNext() )
            {
                json.append( ',' );
            }
        }
    }

    private void serializeDep( StringBuilder json, Dependency dependency )
    {
        json.append( "{" );
        json.append( "\"k\":\"" );
        json.append( dependency.key() );
        json.append( "\",\"v\":\"" );
        json.append( dependency.version() );
        json.append( "\",\"s\":\"" );
        json.append( dependency.scope() );
        json.append( "\",\"d\":[" );
        serializeDeps( json, dependency.dependencies() );
        json.append( "]" );
        json.append( "}" );
    }
}
