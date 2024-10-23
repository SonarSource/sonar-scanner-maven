Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
  properties.load(it)
}

def projectBaseDir = 'sonar.projectBaseDir'
def sources = 'sonar.sources'
def sep = File.separator
// testing that sources with commas in the name are escaped as double-quoted strings
assert properties."$sources".contains(properties."$projectBaseDir" + "${sep}pom.xml")
assert properties."$sources".contains('"' + properties."$projectBaseDir" + "${sep}src${sep}main${sep}my,src,0" + '"')