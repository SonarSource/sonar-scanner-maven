Properties properties = new Properties()
File propertiesFile = new File(basedir, 'out.properties')
propertiesFile.withInputStream {
    properties.load(it)
}

def release = 'sonar.java.release'
def target = 'sonar.java.target'
def source = 'sonar.java.source'
def enablePreview = 'sonar.java.enablePreview'
assert properties."$release" == null
assert properties."$target" == '1.8'
assert properties."$source" == '1.8'
assert properties."$enablePreview" == 'false'
