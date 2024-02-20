Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def projectBaseDir = 'sonar.projectBaseDir'
def sources = 'sonar.sources'
def module1Sources = "org.codehaus.sonar:sample-project-module1.$sources"

assert properties."$module1Sources" == properties."$projectBaseDir" + "/module1/pom.xml"
def sourceDirs = properties."$sources".split(",")
assert sourceDirs.length == 3
assert properties."$sources".contains(properties."$projectBaseDir" + "/pom.xml")
assert properties."$sources".contains(properties."$projectBaseDir" + "/verify.groovy")
assert properties."$sources".contains(properties."$projectBaseDir" + "/invoker.properties")