import org.apache.maven.project.MavenProject

MavenProject project = project
def excludedGroupIds = ['org.bonitasoft.engine', 'org.bonitasoft.runtime', 'org.projectlombok', 'org.slf4j']

def sb = new StringBuilder()
sb.append('<jarDependencies>\n')

// Add the connector's own JAR first
sb.append("    <jarDependency>${project.artifactId}-${project.version}.jar</jarDependency>\n")

project.artifacts.each { artifact ->
    if (!excludedGroupIds.contains(artifact.groupId)
            && artifact.scope != 'test'
            && artifact.type == 'jar') {
        sb.append("    <jarDependency>${artifact.artifactId}-${artifact.version}.jar</jarDependency>\n")
    }
}

sb.append('</jarDependencies>')
project.properties['connector-dependencies'] = sb.toString()
