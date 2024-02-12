Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def projectBaseDir = 'sonar.projectBaseDir'
def sources = 'sonar.sources'
def module1Sources = "org.codehaus.sonar:sample-project-module1.$sources"

assert properties."$module1Sources" == properties."$projectBaseDir" + "/module1/pom.xml"
assert properties."$sources" == properties."$projectBaseDir" + "/pom.xml"