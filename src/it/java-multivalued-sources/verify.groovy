Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
  properties.load(it)
}

def projectBaseDir = 'sonar.projectBaseDir'
def sources = 'sonar.sources'
// testing that sources with commas in the name are escaped as double-quoted strings
assert properties."$sources" ==  properties."$projectBaseDir" + '/pom.xml,' + '"' + properties."$projectBaseDir" + '/src/main/my,src,0"'