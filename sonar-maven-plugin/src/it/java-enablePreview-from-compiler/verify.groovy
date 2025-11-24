Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def enablePreview = 'sonar.java.enablePreview'
assert properties."$enablePreview" == 'true'
